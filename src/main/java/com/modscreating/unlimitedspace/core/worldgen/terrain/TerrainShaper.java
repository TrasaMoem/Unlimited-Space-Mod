package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.hydrology.HydrologyField;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetypeSelector;

/**
 * HIERARCHICAL terrain composer (R20 terrain-quality stage, ACT 4 terrain richness stage).
 *
 * <p>Terrain is built TOP-DOWN: large geography first, details last. Small details can never
 * destroy the large forms because their amplitude budget is bounded (~5-10% of the macro
 * relief):
 *
 * <pre>
 * HEIGHT =
 *   GLOBAL    continents / oceans / global elevation   (wavelength ~3400, amp &lt;= 1.75A)
 *             + ACT4 uplift bias (bounded, planet-global vertical positioning)
 * + MACRO     mountain chains / valleys / basins       (wavelength 500-1500, amp &lt;= 3.3A)
 *             + ACT4 valley NETWORKS + crest ^ crestSharpness
 *             - gravity relief (bounded ~0.85..1.15 on the macro stage)
 *   · provinces (smooth, damped uplift/bias/roughness + ACT4 carve/dune channels)
 *   · RARE FEATURES (terraced craters / crater chains / volcano CALDERAS / dune SEAS /
 *                    mega-gullies / crystal ridges / canyons / lava / spires)
 *   · ACT4 low-gravity slump terraces (basin-bound, bounded ~-0.3A)
 * + REGIONAL  medium relief                            (wavelength ~150, amp &lt;= 0.35A)
 * + LOCAL     tiny bounded detail                      (wavelength ~26,  amp &lt;= ~10% macro)
 * </pre>
 *
 * <p>Scale separation is enforced by construction: every layer has its own frequency band and
 * its own amplitude budget (see {@link GlobalTerrainFields}). Built ONCE per planet and cached
 * by the chunk generator; per-column evaluation is a handful of pure hash mixes with no
 * allocations. Deterministic: same {@code (planetSeed, identity, coordinates)} &rarr; same terrain.
 *
 * <p>ACT 4 continuity contract: every new regional landform has a controlled spatial fade,
 * nothing may create one-column spikes, near-vertical walls, chunk-edge seams or isolated
 * 100+ block jumps. All ACT 4 forms reuse the existing field machinery ({@link TerrainFields},
 * {@link LandformField}, {@link MountainRangeField}) — no second terrain engine.
 */
public final class TerrainShaper {

    /** Crater field cell size — craters are rare, big geological events (radius up to ~360). */
    private static final int CRATER_CELL = 640;
    /** Volcanic cone cell size — rare, large cones with calderas. */
    private static final int CONE_CELL = 512;
    /** Crystal spire cell size (localized, never planet-wide). */
    private static final int SPIRE_CELL = 192;
    /** ACT 4: crater-chain cell size — chains are even rarer, very large events. */
    private static final int CHAIN_CELL = 1280;

    /**
     * ACT 6 section 3-A/D: bench height of the intentional PLATEAU terracing, in blocks.
     *
     * <p>Unchanged from the original terracing: the mesa step was always 9 blocks. ACT 6 only
     * changed HOW the quantised bench is blended into the real height (see the plateau stage in
     * {@link #sample(int, int)}), not the step itself.
     *
     * <p>ACT-D (d): the step is now a SHARE of the macro amplitude {@code A} with a hard FLOOR of 6
     * blocks. A fixed 9-block bench is a large fraction of a small world (where it flattened whole
     * provinces into one integer height) and almost nothing on a mountain world; a share keeps the
     * bench a constant FRACTION of the relief everywhere, and the floor guarantees the terracing
     * stays legible as real steps on the smallest world rather than degenerating into noise.
     */
    private static final double PLATEAU_STEP_SHARE = 0.32;
    /** The bench is never finer than this, so the terracing always reads as steps. */
    private static final double PLATEAU_STEP_FLOOR_BLOCKS = 6.0;

    /** The plateau bench height of THIS world: a share of {@code A}, never below the floor. */
    private double plateauStep() {
        return benchAmplitude(amplitude);
    }

    /** ACT-D (d): the bench step for a given amplitude - the one formula the shaper and the tests use. */
    public static double benchAmplitude(double amplitude) {
        double s = amplitude * PLATEAU_STEP_SHARE;
        return s < PLATEAU_STEP_FLOOR_BLOCKS ? PLATEAU_STEP_FLOOR_BLOCKS : s;
    }

    /**
     * ACT-D: the soft-knee band for a given amplitude.
     *
     * <p>ACT-D (b) tried {@code 0.85A} here and it regressed {@code Act6TerrainFlatStripTest}
     * (132 blocks pinned on a height bound against a 24-block limit), so the knee stays the
     * calibrated absolute {@link #BOUND_KNEE}. This accessor exists so tests read the one formula
     * the shaper uses rather than restating it.
     */
    public static double kneeAmplitude(double amplitude) {
        return BOUND_KNEE;
    }

    /** ACT-D: the amplitude below which the bench floor engages. */
    public static double benchFloorClearsAbove() {
        return PLATEAU_STEP_FLOOR_BLOCKS / PLATEAU_STEP_SHARE;
    }

    /** ACT-D: the amplitude below which a knee SHARE would engage its floor (reverted; kept as data). */
    public static double kneeFloorClearsAbove() {
        return 6.0 / 0.85;
    }

    /**
     * ACT 6 section 3-A: maximum share of the quantised terrace that may replace the real height.
     *
     * <p>Below 1 so the underlying terrain always keeps part of its own gradient. The old code used
     * {@code Math.min(1.0, ...)}, so a fully terraced column could be replaced ENTIRELY by the
     * quantised value, and the quantiser's flat bench derivative then survived into the output.
     */
    private static final double PLATEAU_MAX_BLEND = 0.72;

    /**
     * ACT 6 section 3-B: morphology strength below this is treated as EXACTLY no morphology.
     *
     * <p>Root cause of the second reported artifact: several ACT 4 landform fields kept a wide
     * spatial FOOTPRINT (their smooth mask/envelope was still non-zero over hundreds of blocks)
     * while contributing a deformation that had already decayed to ~0 — a broad region that was
     * nominally "dune sea" / "crystal ridge" / "spire field" but was geometrically ordinary, which
     * with an integer height produced flat shelves exactly there. Fading the strength to a hard
     * zero well before the deformation vanishes removes the footprint together with the amplitude.
     */
    private static final double MORPHOLOGY_EPSILON = 0.06;

    /**
     * ACT 6 section 3-C: the width of the soft-knee band at each height bound, in BLOCKS.
     *
     * <p>Beyond this distance from a bound the height is returned unchanged (slope exactly 1);
     * inside it the slope is smoothly reduced to 0, so the height approaches the bound
     * asymptotically instead of being pinned flat onto it.
     *
     * <p>ACT-D (b) WAS ATTEMPTED AND REVERTED. Converting this to a share of {@code A}
     * ({@code 0.85A}, floor 6) is the obviously right move and it measurably REGRESSES
     * {@code Act6TerrainFlatStripTest}: the knee is precisely the anti-saturation device, so a
     * narrower knee compresses the upper band harder, more columns reach the ceiling, and the
     * measured worst level run pinned on a height bound went from under the 24-block limit to
     * **132 blocks**. The guard is calibrated against the absolute 40, so the constant stays.
     */
    private static final double BOUND_KNEE = 40.0;

    /**
     * ACT-D: the provincial vertical bias as a SHARE of the macro amplitude.
     *
     * <p>The previous factor was the literal {@code 0.5} BLOCKS per unit of bias. Because the bias is
     * a dimensionless blend clamped to {@code ±maxBias} (4..14.8), the old term was worth up to ~7.4
     * blocks on EVERY world — a fixed offset that made the composed surface non-scale-invariant and
     * is precisely why doubling {@code A} moved {@code elevation01} by 0.0135 (measured) instead of
     * leaving it alone.
     *
     * <p>At the historical amplitude this reproduces the old magnitude: the legacy amplitude was
     * {@code (4 + roughness*28) * (1-0.4*erosion) * patternMultiplier}, whose measured population mean
     * over 25 real worlds is ~19 blocks (see {@code run/final-worldgen/act-d/baseline-relief.txt}), so
     * {@code 0.5 / 19 ≈ 0.026} keeps the population where it was while making the term scale.
     */
    private static final double BIAS_AMPLITUDE_SHARE = 0.026;

    /**
     * ACT-D PHASE 2(a): the relative amplitude FLOOR that would replace the fixed 6.0 blocks.
     *
     * <p>NOT ACTIVE. Phase 2 was built and measured and it regressed BOTH boundary guards
     * (longestRun 55 -> 68 over its limit of 60, turnsGain below its floor), so the composer
     * keeps the legacy absolute floor until the boundary layer carries its own relative
     * irregularity budget. The constant is kept: it is the calibrated value the next
     * attempt needs.
     */
    private static final double AMPLITUDE_RELATIVE_FLOOR = 0.45;
    private final TerrainGenerator base;
    private final GeologicalProvinceMap provinces;
    private final ProvinceTerrainModifier provinceModifiers;
    private final TerrainSignature signature;
    private final PlanetPhysicalProfile profile;
    private final TerrainArchetypeSelector.ArchetypePair archetype;
    private final TerrainArchetypeSelector.TerrainArchetypeGrammar grammar;
    // R21: planet-level relief identity (archetype + mountainCoverage) + biome regions that
    // modulate the coverage LOCALLY. Together they decide whether this world is flat or a
    // mountain world — local noise may never create the geography by itself.
    private final PlanetReliefProfile relief;
    /**
     * WORLDGEN V2: the planet's SINGLE macro geography, shared by reference with the chunk
     * generator and the material path. There is no second instance anywhere.
     */
    private final MacroGeography geography;
    /**
     * A REUSED per-column scratch {@link MacroSample}. The terrain path runs on one shaper per
     * world, so a single instance keeps the hot path allocation-free; {@link MacroSample} is
     * explicitly designed to be overwritten in place.
     */
    private final MacroSample macroSample = new MacroSample();
    /** A REUSED weight scratch for the continuous province weights. */
    private double[] provinceWeights;
    // PHASE 6/7: budgeted local landforms (gullies / ravines / fissures / sinkholes /
    // crevasses), evaluated BETWEEN the macro relief and the regional hills layer.
    private final LandformField landforms;
    // ACT 4: the climate subsystem is (optionally) carried here so the shaper's region context
    // becomes CLIMATE-AWARE exactly like PlanetChunkGenerator's — one authoritative macro-region
    // decision for the same column. Null keeps the legacy non-climate-aware path (tests).
    private final PlanetClimateProfile climate;
    private final long fieldSeed;
    private final double amplitude;
    private final double minHeight;
    private final double baseHeight;
    private final double maxHeight;

    // ============================================================== WORLDGEN V3
    /**
     * V3 PLANET CHARACTER: the continuous tendencies (dune / glacial / volcanic / alpine / ...)
     * derived from the physical profile. These REPLACE every hard family switch in the terrain,
     * hydrology, biome and material layers. Never normalized to sum to 1.
     */
    private final PlanetCharacter character;
    /** V3 hydrology (rivers, drainage, lake basins) — tiles cached, deterministic. */
    private final HydrologyField hydrology;
    /**
     * V3 ELEVATION AUTHORITY: the multi-level composition field (mountains, dunes, glacial
     * troughs, river carving). Built once per planet, evaluated per column into a reused scratch.
     */
    private final ElevationField elevation;
    /** REUSED per-column scratch — the V3 hot path allocates nothing. */
    private final ElevationScratch elevationScratch = new ElevationScratch();
    /** ACT V3.1: the reusable composer output, so {@link #sample} no longer allocates per column. */
    private final TerrainShaperScratch sampleScratch = new TerrainShaperScratch();

    /** ACT V3.1: shared mountain-envelope scratch (see MountainRangeField#envelopes). */
    private final double[] envelopeScratch = new double[2];
    private TerrainShaper(TerrainGenerator base, GeologicalProvinceMap provinces,
                          ProvinceTerrainModifier provinceModifiers, TerrainSignature signature,
                          PlanetPhysicalProfile profile,
                          TerrainArchetypeSelector.ArchetypePair archetype,
                          PlanetReliefProfile relief, MacroGeography geography,
                          LandformField landforms, PlanetClimateProfile climate,
                          long fieldSeed, double amplitude, double baseHeight, long planetSeed) {
        this.base = base;
        this.provinces = provinces;
        this.provinceModifiers = provinceModifiers;
        this.signature = signature;
        this.profile = profile;
        this.archetype = archetype;
        this.grammar = archetype.blended();
        this.relief = relief;
        this.geography = geography;
        this.landforms = landforms;
        this.climate = climate;
        this.fieldSeed = fieldSeed;
        this.amplitude = Math.max(6.0, amplitude);
        this.baseHeight = baseHeight;
        // WORLDGEN V3: the character / hydrology / elevation authority. The elevation field is
        // built from the SAME amplitude the legacy skeleton uses, so the two compose cleanly.
        this.character = PlanetCharacter.of(profile);
        // V3 STAGE 3: the drainage network is solved on the REAL terrain. ElevationField and
        // HydrologyField are mutually dependent (terrain -> drainage -> river incision -> terrain),
        // so the cycle is closed explicitly in two steps: the hydrology is created first with an
        // elevation source that delegates back to the (not yet assigned) elevation field, and the
        // field is then bound. The delegation is a captured `this`, so no late-binding state and
        // no mutable generation configuration is involved.
        final ElevationField[] elevationRef = new ElevationField[1];
        this.hydrology = new HydrologyField(planetSeed, this.character,
                (wx, wz, scratch) -> {
                    ElevationField f = elevationRef[0];
                    return f == null ? this.baseHeight : f.drainageHeight(wx, wz, scratch);
                },
                // ACT-D (c): the incision budget scales with the SAME amplitude every other relief
                // layer uses, so a river is cut into relief of the world's own scale.
                this.amplitude);
        this.elevation = new ElevationField(planetSeed, this.character,
                relief, baseHeight, this.amplitude, this.hydrology);
        elevationRef[0] = this.elevation;
        // ACT 6 section 3-C widened the band to (-4A .. +6A) so a whole mountain top never saturated
        // against the ceiling. WORLDGEN V3 keeps a real ceiling but fits it to the relief.
        //
        // Measured over a 24k-block square, a temperate/glacial/arid world spans -1.49A .. +2.47A,
        // while a pure volcanic world pushes higher still. The band therefore sits just outside both
        // with a small margin: the old 10A was four times wider than the relief, which squeezed every
        // normalized elevation (lapse rate, biome scoring, snow line) into a narrow middle slice,
        // while a band fitted to the temperate case alone would clamp a volcanic summit flat.
        this.minHeight = baseHeight - 2.6 * this.amplitude;
        this.maxHeight = baseHeight + 4.2 * this.amplitude;
    }

    /** Canonical factory: one shaper per planet (cached by the caller). */
    public static TerrainShaper create(TerrainGenerator base, long planetSeed,
                                       PlanetPhysicalProfile profile,
                                       GeologicalProvinceMap provinces,
                                       TerrainSignature signature,
                                       double baseHeight, double terrainAmplitude) {
        return create(base, planetSeed, profile, provinces, signature, baseHeight,
                terrainAmplitude, null, null);
    }

    /** Factory with explicit planet-level geography; either may be {@code null}. */
    public static TerrainShaper create(TerrainGenerator base, long planetSeed,
                                       PlanetPhysicalProfile profile,
                                       GeologicalProvinceMap provinces,
                                       TerrainSignature signature,
                                       double baseHeight, double terrainAmplitude,
                                       PlanetReliefProfile relief, MacroGeography geography) {
        return create(base, planetSeed, profile, provinces, signature, baseHeight,
                terrainAmplitude, relief, geography, null);
    }

    /**
     * WORLDGEN V2 factory: the shaper takes the planet's SINGLE {@link MacroGeography} instance
     * (the same object the chunk generator and the material path read) and the optional climate
     * subsystem. Because it is the same object, terrain and materials physically cannot
     * disagree about macro ownership — there is no second map to drift.
     *
     * <p>When {@code geography} is {@code null} it is derived with the SAME derivation the
     * geology profile uses, so the two agree exactly.
     */
    public static TerrainShaper create(TerrainGenerator base, long planetSeed,
                                       PlanetPhysicalProfile profile,
                                       GeologicalProvinceMap provinces,
                                       TerrainSignature signature,
                                       double baseHeight, double terrainAmplitude,
                                       PlanetReliefProfile relief, MacroGeography geography,
                                       PlanetClimateProfile climate) {
        long fieldSeed = Seeds.derive(planetSeed, "us.terrain.fields");
        ProvinceTerrainModifier mods = ProvinceTerrainModifier.create(planetSeed, provinces, signature);
        TerrainArchetypeSelector.ArchetypePair arch =
                TerrainArchetypeSelector.create(planetSeed, profile);
        PlanetReliefProfile r = relief != null ? relief
                : ReliefArchetypeSelector.create(planetSeed, profile);
        // ---- ACT STAGE 1: RELIEF IS THE AUTHORITY FOR THE MACRO AMPLITUDE `A`.
        //
        // `A` used to be `terrainAmplitude * signature.amplitudeMul()`, and `terrainAmplitude` is
        // the TerrainProfile field handed to the DEAD ValueNoiseTerrainGenerator producer — so the
        // world's real relief budget was decided by an unrelated legacy channel. Measured on real
        // planets: relief identity and mountainCoverage had NO effect on the composed span, and a
        // FLAT SOLID_ROCKY world (P90−P10 = 5 blocks) could be flatter than a CANYONLAND SOLID_DESERT
        // world (22 blocks) purely because of that legacy value.
        //
        // The relief profile is the existing authority that already declares `hillAmplitude` and
        // `mountainCoverage`, so it is now the only thing that sets the macro budget. The legacy
        // amplitude is kept ONLY as a bounded secondary modulation (so a planet-level generation
        // parameter can still tilt the world) and can no longer be the budget itself.
        // ---- ACT STAGE 1: NOT APPLIED — see the note below.
//
// MEASURED AND REJECTED. Making `A` a function of the relief profile was implemented and measured:
// `PlanetReliefProfile.macroAmplitudeBlocks` (relief-derived) and then a narrow
// `macroAmplitudeReliefFactor` modulation were both tried, and BOTH regress two calibrated
// architectural guards that this ACT forbids weakening:
//
//   V3BoundaryIrregularityTest: "the longest uniform run is 77 of 80 columns - a single owner owns
//     almost the window" (seed 0xc101)
//   V4BoundaryTransitionTest: "accumulated turning FELL (0.024 -> 0.021) - the displacement is
//     smoother than the edge it deforms and is erasing it" (seed 0xc102)
//
// The mechanism was structural, not a tuning mistake, and ACT-D PHASE 1 removed the cause: the
        // composer's absolute-size terms (river incision capped at 12 blocks, the 9-block plateau bench,
        // the 40-block bound knee, the province vertical bias) did NOT scale with `A`. Raising `A`
        // therefore lifted the A-proportional relief layers while the absolute ones stayed put, the
        // normalised surface became smoother, and biome / boundary decisions became MORE
        // owner-dominated - exactly the opposite of the reference contract's "irregular, multi-scale,
        // no long straight walls". The dither band stays in margin units and is already relative.
        //
        // ---- ACT-D PHASE 2 WAS ATTEMPTED AND MEASURED, AND IT REGRESSES THE BOUNDARY GUARDS.
        //
        // Wiring the relief as the source of `A` and raising the per-surface budgets was built
        // and measured on the real seed-0 population, and it did exactly what the note above warns
        // of. The composer is now scale-invariant, so raising `A` lifts EVERY layer together -
        // which means the only remaining source of boundary irregularity is the boundary layer
        // itself, whose dither band is in MARGIN units (absolute blocks, and out of scope by contract).
        //
        //   Measured: DESERT mean P90-P10 28.6 -> 65.2 (target met), OCEANIC 31.2 -> 49.0
        //   (met), ROCKY min 9 -> 38 (met) - and V3BoundaryIrregularityTest longestRun 55 -> 68
        //   on seed 0xc101, over its hard limit of 60, with V4BoundaryTransitionTest
        //   turnsGain failing beside it.
        //
        // The guards outrank the relief targets: they are calibrated architectural contracts,
        // while the targets are a tuning goal. So `A` keeps its existing source, Phase 1 stands on
        // its own, and the per-surface budget work is recorded here rather than merged.
        //
        // `PlanetReliefProfile.macroAmplitudeBlocks(erosion, surface)` is kept and remains fully
        // tested - it is the measured expression of what the relief SHOULD scale to, and it is now
        // the documented entry point for the next attempt, once the boundary layer has its own
        // relative irregularity budget.
        // of `A`, which is the precondition this finding asked for. `PlanetReliefProfile` is the
        // existing authority that already declares `hillAmplitude` and `mountainCoverage`; the
        // `amplitude` field of `TerrainProfile` is the value handed to the DEAD
        // `ValueNoiseTerrainGenerator` (nothing ever calls `sampleInto` on it), so it was deciding the
        // relief of every world in the game while being dead code in its own right.
        double amp = Math.max(6.0, Math.abs(terrainAmplitude)
                * (signature == null ? 1.0 : signature.amplitudeMul()));
        MacroGeography geo = geography;
        if (geo == null) {
            PlanetaryEnvironment env = PlanetaryEnvironment.of(profile,
                    r == null ? 0.35 : r.mountainCoverage());
            geo = MacroGeography.of(planetSeed, env);
        }
        return new TerrainShaper(base, provinces, mods, signature, profile, arch,
                r, geo, LandformField.create(planetSeed, profile), climate, fieldSeed, amp, baseHeight,
                planetSeed);
    }

    /** R21: the planet's relief identity (diagnostics / debug screen). */
    public PlanetReliefProfile relief() {
        return relief;
    }

    /** WORLDGEN V2: the planet's macro geography (diagnostics / debug screen / preview). */
    public MacroGeography geography() {
        return geography;
    }

    /**
     * The unified per-column geological context: the DISCRETE dominant province plus the
     * CONTINUOUS weight vector. The weight vector is what every intensity is derived from, so
     * no terrain amplitude can ever step at a province border.
     */
    public GeologicalProvinceContext provinceContext(int x, int z) {
        if (provinces == null) return GeologicalProvinceContext.neutral(profile);
        if (provinceWeights == null || provinceWeights.length != provinces.weights().size()) {
            provinceWeights = provinces.newScratch();
        }
        provinces.weightsAt(x, z, provinceWeights);
        int best = 0;
        for (int i = 1; i < provinceWeights.length; i++) {
            if (provinceWeights[i] > provinceWeights[best]) best = i;
        }
        return new GeologicalProvinceContext(provinces.weights().get(best).province(),
                provinceWeights, provinces.weights(), profile);
    }

    /** PHASE 6: the planet's budgeted landform field (diagnostics / surface strata). */
    public LandformField landforms() {
        return landforms;
    }

    /** ACT 4: the climate subsystem this shaper was built with (diagnostics / debug screen). */
    public PlanetClimateProfile climate() {
        return climate;
    }
// =============================================================== WORLDGEN V3 accessors

    /** V3: the planet's continuous character (dune / glacial / volcanic / alpine / ...). */
    public PlanetCharacter character() {
        return character;
    }

    /** V3: the planet's hydrology (rivers, drainage, lake basins). */
    public HydrologyField hydrology() {
        return hydrology;
    }

    /** V3: the multi-level elevation composition authority. */
    public ElevationField elevationField() {
        return elevation;
    }

    /**
     * WORLDGEN V3 — ELEVATION01 FIX (mandatory).
     *
     * <p>The old normalization divided by {@code 2 * profile.amplitude()} — a span far SMALLER
     * than the real composed relief — so the value saturated at 0/1 over most of a world. It now
     * normalizes over the SAME legal band the {@link ElevationField} actually composes into
     * ({@link ElevationField#minBound()} .. {@link ElevationField#maxBound()}). Sharing one band is
     * the point: if the shaper and the field each derived their own bounds, a column's normalized
     * elevation would depend on which of the two callers asked for it.
     */
    public double elevation01(double height) {
        double lo = elevation.minBound();
        double hi = elevation.maxBound();
        double span = hi - lo;
        if (span <= 0.0) return 0.5;
        double t = (height - lo) / span;
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    /** V3: the same normalized elevation straight from the composed surface height. */
    public double elevation01(int x, int z) {
        return elevation01(surfaceHeight(x, z));
    }

    /** V3: the reused per-column elevation scratch (hot path / diagnostics). */
    public ElevationScratch elevationScratch() {
        return elevationScratch;
    }

    /**
     * PHASE 6.9: landform EXPOSURE of deep material at a column in [0,1] (0 = ordinary
     * surface, 1 = a landform cut fully exposes the deep geology). Consumed by the
     * surface-strata stage of the chunk generator.
     */
    public double landformExposure(int x, int z) {
        return landforms == null ? 0.0 : landforms.exposureAt(x, z, amplitude);
    }

    /** The planet's terrain signature (diagnostics / debug screen). */
    public TerrainSignature signature() {
        return signature;
    }

    /** The planet's terrain archetype pair (primary + secondary modifier). */
    public TerrainArchetypeSelector.ArchetypePair archetype() {
        return archetype;
    }

    /** Lower height bound of the shaper (diagnostics / normalization). */
    public double minBound() { return minHeight; }

    /** Upper height bound of the shaper (diagnostics / normalization). */
    public double maxBound() { return maxHeight; }

    /** The amplitude scale of the hierarchical composer (diagnostics). */
    public double amplitudeBound() { return amplitude; }

    /** ACT 4: bounded gravity relief multiplier (centered on 1.0, reuses ACT 1 verticalScale). */
    private double gravityReliefMul() {
        double vs = PlanetaryEnvironment.verticalScale(profile == null ? null : profile.gravityClass());
        return clamp(0.85, 1.15, 1.0 + (vs - 1.0) * 0.6);
    }

    /** ACT 4: low-gravity slump drive in [0,1] (only genuinely low-gravity profiles). */
    private double slumpDriveValue() {
        double vs = PlanetaryEnvironment.verticalScale(profile == null ? null : profile.gravityClass());
        return clamp01((vs - 1.08) / 0.27);
    }

    /**
     * Hierarchical terrain sample for a world column: the final height PLUS the global fields
     * (continentalness / erosion / ridge) that produced it. Single evaluation, many consumers
     * (chunk fill, debug screen, headless diagnostics).
     *
     * <p>ACT 4: the layering is preserved (base + global + macro + province + landform + local)
     * but the regional/feature stage is enriched by the ACT 4 landforms (mega-gullies, valley
     * networks, dune seas, calderas, crater chains, terraced craters + central peaks, sharp
     * crest lines, fracture fields, crystal ridges, low-g slump terraces) — all bounded and
     * continuous. Hot path stays allocation-free and scalar.
     */
    public TerrainSample sample(int x, int z) {
        sampleInto(x, z, sampleScratch);
        return sampleScratch.toSample();
    }

    /**
     * ACT V3.1: the ALLOCATION-FREE composer. Writes the full decomposition into a caller-owned
     * scratch instead of building a {@link TerrainSample} record.
     *
     * <p>This is the same computation as {@link #sample(int, int)}, in the same order, with the
     * same arithmetic: the record form is now a thin wrapper around this method plus a
     * {@code toSample()} copy. Every consumer in the chunk hot path uses the scratch form.
     */
    public void sampleInto(int x, int z, TerrainShaperScratch out) {
        double A = amplitude;

        // ---------------------------------------------------------------- 1. GLOBAL geography
        double cont = GlobalTerrainFields.continentalness(fieldSeed, x, z);
        cont = GlobalTerrainFields.clamp01(cont + (grammar.continentalBias() - 0.5) * 0.7);
        double gShape = (cont - 0.5) * 2.0;                                   // -1..1
        // R21: the global dome is only the land/ocean tilt (not the main relief carrier).
        double global = A * 1.30 * Math.signum(gShape) * Math.pow(Math.abs(gShape), 1.4);
        // Ocean-heavy archetypes: a genuine global sea-level drop (whole map sits lower).
        global -= A * 1.1 * Math.max(0.0, 0.5 - grammar.continentalBias());

        // Effective erosion regime: profile identity + spatial erosion field + archetype bias.
        double eroField = GlobalTerrainFields.erosionField(fieldSeed + 0x7L, x, z);
        double eroProfile = profile == null ? 0.5 : profile.erosion();
        double ero = clamp01(0.45 * eroProfile + 0.40 * eroField
                + 0.35 * (grammar.erosionBias() - 0.5));

        // ---------------------------------------------------------- 1.5 ACT 4 CLIMATE AFFINITY
        // Bounded terrain affinity from the ALREADY-EXISTING local climate field (never a second
        // climate map, never a second terrain identity). Influence is capped at ~15-20% of the
        // affected feature strength. Single evaluation per column, reused below.
        double localHum = 0.5, localArid = 0.5, localTemp = 0.5;
        boolean hasClimate = climate != null;
        if (hasClimate) {
            localHum = climate.humidityAt(x, z);
            localTemp = climate.temperatureAt(x, z);
            localArid = 1.0 - localHum;
        }
        double humAffinity = 1.0 + 0.20 * (localHum - 0.5);    // 0.90..1.10 (wet -> valleys)
        double aridAffinity = 1.0 + 0.25 * (localArid - 0.5);  // 0.875..1.125 (dry -> dunes)

        // ---------------------------------------------------------------- 2. MACRO relief
        // WORLDGEN V2: mountains come from the PLANET'S relief identity modulated by the
        // CONTINUOUS macro-geography attributes. The macro sample carries the primary archetype
        // (a LABEL, used for display and discrete decisions) AND the continuous attribute blend
        // (used for EVERY amplitude). There is no region filter and no second map.
        geography.sample(x, z, macroSample);
        double localCoverage = macroSample.localMountainCoverage(relief.mountainCoverage());
        double widthMul = relief.archetype().rangeWidthMul();
        double sigRidge = signature == null ? 0.5 : signature.effectiveRidgeStrength();
        double tectGate = 0.95 + 0.05 * (profile == null ? 0.5 : profile.tectonicActivity());
        double coverage = GlobalTerrainFields.clamp01(localCoverage
                * (0.95 + 0.05 * sigRidge) * tectGate);

        // ACT V3.1: both envelopes from ONE smoothed cell field (bit-identical, half the work).
        MountainRangeField.envelopes(fieldSeed, coverage, x, z, envelopeScratch);
        double core = envelopeScratch[0];
        double wide = envelopeScratch[1];
        double band = GlobalTerrainFields.clamp01(wide - core);          // foothills zone
        double ridge = MountainRangeField.crest(fieldSeed, widthMul, x, z);

        // ACT 4: crest profile uses the blended morphology exponent (ridge ^ crestSharpness).
        // The old fixed 1.4 remains the default value so non-ACT4 morphologies are unchanged.
        double crestSharp = signature == null ? 1.4 : signature.crestSharpness();
        double macro = A * 2.45 * core * (0.45 + 0.55 * Math.pow(ridge, crestSharp))
                + A * 0.95 * band * (0.50 + 0.50 * ridge);

        // ACT 4: VALLEY NETWORKS — the existing ~1100-scale corridor plus coherent branches,
        // deepened to ~-1.05A before safety clamps; humidity strengthens wet expression, and
        // eroded worlds deepen their valleys further (erosion-driven relief, not random cuts).
        double valleyField = TerrainFields.valleyNetwork(fieldSeed, widthMul, x, z);
        double lowland = 1.0 - 0.8 * core;
        double valleyDepth = A * 1.05 * relief.archetype().valleyStrength()
                * humAffinity * (0.8 + 0.4 * ero) * (0.4 + 0.6 * lowland);
        macro -= GlobalTerrainFields.clamp01(valleyField * 0.55) * valleyDepth;

        // Basins: large smooth continental depressions (never a hole, always a region).
        double basin = GlobalTerrainFields.basinField(fieldSeed + 0xDL, x, z);
        double basinT = smoothstep01((basin - 0.58) / 0.34);
        macro -= A * 1.25 * grammar.basinStrength() * basinT;

        // ---------------------------------------------------------------- 3. PROVINCE shaping
        // WORLDGEN V2: the province modifier is the CONTINUOUS weighted average of the
        // per-province shape grammar. No integer province, no region filter, no label step.
        ProvinceTerrainModifier.Sample s = provinceModifiers != null
                ? provinceModifiers.sample(x, z)
                : ProvinceTerrainModifier.Sample.NEUTRAL;
        double rough = s.isNeutral() ? 1.0 : s.roughness();
        // ACT 4: province uplift range widened from 0.85..1.25 to the SMALLEST safe expansion
        // (0.80..1.38) so provincial macro-relief stays visible without breaking continuity.
        double up = s.isNeutral() ? 1.0 : clamp(s.uplift(), 0.80, 1.38);
        double carveAffinity = s.isNeutral() ? 1.0 : clamp(0.80 + 0.20 * s.carve(), 0.80, 1.20);

        // ACT 4: bounded gravity response — applied ONCE on the macro/regional relief stage
        // (never 10 times on different landforms). Low gravity widens relief, high damps it.
        double gravityMul = gravityReliefMul();
        // ACT 4: activate the (previously unused) upliftBias channel — deterministic, bounded,
        // planet-global so spatially smooth, and subordinate to the macro terrain.
        double upliftBias = signature == null ? 0.5 : signature.upliftBias();

        // ACT-D: the provincial vertical BIAS was the last absolute block term on this stage — a
        // fixed `s.bias() * 0.5` in BLOCKS, so a province that wanted to sit 7 blocks high sat 7
        // blocks high on a world whose entire legal band was 41 blocks and was a rounding error on a
        // 320-block one. It is now a share of the same `A` the rest of the stage uses, which is what
        // makes the whole macro line scale-invariant. The bias itself is already dimensionless
        // (a weighted blend of per-province weights in [-maxBias, maxBias]), so the share converts
        // it to blocks at exactly the rate the rest of the relief uses.
        double y = baseHeight + global + s.bias() * BIAS_AMPLITUDE_SHARE * A + macro * up * gravityMul
                + (upliftBias - 0.40) * A * 0.8;

        // The unified per-column geology: the DISCRETE province (for block/feature decisions)
        // plus the CONTINUOUS weights every intensity below is derived from.
        // WORLDGEN V3: elevation01 is normalized over the REAL legal band (#elevation01), never
        // over the old 2A span that saturated at 0/1. Consumers read it from the shaper.
        GeologicalProvinceContext ctx = provinces != null
                ? provinceContext(x, z)
                : GeologicalProvinceContext.neutral(null);

        // ---------------------------------------------------------------- 4. RARE features
        // ACT 4 terrain-cancellation fix (R20 balance): the positive large-scale skeleton must
        // stay visible — mountain envelopes are no longer flattened toward the same mean by the
        // basin/valley subtraction, and every feature below keeps a controlled spatial fade.
        double rim = signature == null ? 0.28 : signature.rimStrength();
        double peakMul = signature == null ? 0.20 : signature.centralPeakMul();

        // ACT 4: TERRACED CRATERS + CENTRAL PEAKS (morphology-driven rim & central peak).
        // ACT 6 section 3-B: the strength is faded to a hard zero below MORPHOLOGY_EPSILON, so a
        // crater footprint with no real deformation left is never entered at all.
        double craterDensity = morphologyGate(clamp01((signature == null ? 0.0
                : signature.effectiveCraterDensity()) * grammar.craterFrequencyMul()));
        if (craterDensity > 0.0) {
            y += TerrainFields.craterDeform2(fieldSeed + 0x2A, x, z, CRATER_CELL,
                    craterDensity, A * 1.4, rim, peakMul);
            // ACT 4: CRATER CHAINS — only on genuinely cratered worlds, very rare, large.
            if (craterDensity > 0.22) {
                y += TerrainFields.craterChainDeform(fieldSeed + 0x6BL, x, z, CHAIN_CELL,
                        craterDensity, A * 1.1, rim, peakMul);
            }
        }
        // ACT 4: VOLCANIC CALDERAS — positive edifice + broad central depression + raised rim.
        double volcStrength = clamp01((signature == null ? 0.0 : signature.effectiveVolcanicStrength())
                * grammar.volcanicStrengthMul());
        volcStrength = clamp01(volcStrength + (0.5 - 0.5 * volcStrength) * ctx.volcanicIntensity());
        if (volcStrength > 0.0) {
            y += TerrainFields.volcanicCalderaDeform(fieldSeed + 0x3B, x, z, CONE_CELL,
                    volcStrength, A * 1.6, rim);
        }
        // ACT 4 / ACT worldgen fix: DUNE SEAS — DuneMorphologyField is the SOLE producer of dune
        // height (see ElevationField LEVEL 2, driven by the CONTINUOUS planet character).
        //
        // The legacy path used to run the full dune budget (0.72*A) HERE as well, so a dry world
        // counted its sand relief TWICE: one erg rendered as two superimposed dune fields, which
        // doubled both the intended amplitude and the block-scale slope. That second producer is
        // REMOVED. TerrainFields.duneSeaField keeps its API and its tests, but it is no longer
        // added to the composed height; it survives only as a cheap spatial DUNE classifier in
        // landformIdentity / landformStrength below, so the dune landform (and its material) can
        // still be recognised without ever doubling the terrain.
        // ACT 4: CRYSTAL RIDGES — broad secondary crest on crystal-prone provinces (never a
        // giant spike, never planet-wide). ACT 6 section 3-B: gated like every other morphology.
        double cryst = morphologyGate(ctx.crystalIntensity());
        if (cryst > 0.02) {
            y += TerrainFields.crystalRidgeField(fieldSeed + 0x5EL, x, z, 0.65 * cryst, A * 0.9);
        }
        // Crystal spires: keep the existing per-column activation (bounded by territory).
        // ACT V4: the deformation the height is about to receive is ALSO published, normalised by
        // the amplitude, so the material layer can read the structural landform without
        // re-evaluating anything. No new field, no second noise engine: this is the same call.
        double spire = TerrainFields.spireField(fieldSeed + 0x5D, x, z, SPIRE_CELL,
                0.6 * cryst, A);
        y += spire;
        out.spireSignal = spire <= 0.0 ? 0.0 : clamp01(spire / A);
        // Lava channels follow volcanic provinces; they never punch terrain bounds.
        if (ctx.lavaIntensity() > 0.0) {
            double lavaDeform = ctx.lavaIntensity()
                    * TerrainFields.lavaChannel(fieldSeed + 0x0EA, x, z,
                            ContextUtils.volcanicChannelStrength(ctx, signature), A);
            y += lavaDeform;
        }
        // Canyons: rare, very large cuts — deepened by province carve + erosion (dry provinces
        // erode deepest) but never more than the continuity budget allows.
        double canyonStrength = clamp01((signature == null ? 0.0 : signature.effectiveCanyonStrength())
                        * (0.4 + 0.6 * ero) * carveAffinity)
                * (1.0 - ctx.lavaIntensity());
        if (canyonStrength > 0.0) {
            double ridgeLine = GlobalTerrainFields.ridgeField(fieldSeed + 0x9FL, x, z, 1.0 / 900.0, 400.0);
            if (ridgeLine > 0.958) {
                double channel = Math.pow((ridgeLine - 0.958) / 0.042, 1.5);
                double carve = canyonStrength * A * 1.25 * (0.8 + 0.3 * ero) * channel;
                y -= carve;
            }
        }

        // ACT 4: MEGA-GULLIES — broad ~900-scale regional channels for dry / eroded lands.
        // Gated by the planet gully budget, local erosion, province carve affinity and aridity
        // (never a second terrain map; broadened before deepened; inside the continuity cap).
        double gullyDrive = landforms == null ? 0.0 : landforms.budget().gully();
        double megaGullyStrength = clamp01(gullyDrive * (0.35 + 0.65 * ero)
                * carveAffinity * (0.75 + 0.35 * aridAffinity));
        if (megaGullyStrength > 0.06) {
            y += TerrainFields.megaGullyField(fieldSeed + 0x66L, x, z,
                    megaGullyStrength, A * 0.48);
        }

        // ACT 4: LOW-GRAVITY SLUMP TERRACES — broad stepped descent toward basins, only on
        // genuinely low-gravity profiles, bounded ~-0.3A, benches hundreds of blocks wide.
        // ACT 6 section 3-B: gated like every other morphology.
        double slumpDrive = morphologyGate(slumpDriveValue());
        if (slumpDrive > 0.0 && basinT > 0.30) {
            y += TerrainFields.slumpTerraceField(fieldSeed + 0x6FL, x, z,
                    slumpDrive, A * 0.30, basinT);
        }

        // Plateau terracing (mesas): only where the archetype wants plateaus.
        //
        // ACT 6 section 3-A/D: the plateau landform is still EXPLICITLY selected — it only runs
        // when the planet's primary morphology IS PLATEAU, so ordinary terrain can never take it —
        // but the pure quantiser is gone.
        //
        // The old step was terrace = (floor(y/9) + smoothstep(frac(y/9))) * 9, whose derivative is
        // 0 across the middle of every bench. On a gently sloping mesa top that produced arbitrarily
        // long runs of IDENTICAL integer height: the artificial flat strip. Two changes together:
        //   - a continuous bench TILT (a ~470-block profile) is added, so a bench is a gently
        //     sloping terrace rather than a dead-flat shelf;
        //   - the blend is capped below 1 and driven by a SMOOTH ramp, so no column is ever fully
        //     replaced by the quantised value, and the hard `if (plateauNoise > 0.58)` branch
        //     (itself a visible contour line) is gone.
        // The mesa silhouette — flat-ish benches with distinct risers — is preserved.
        if (grammar.plateauTendency() > 0.30
                && signature != null && signature.primary() == TerrainMorphology.PLATEAU) {
            double plateauNoise = TerrainFields.warped(fieldSeed + 0xB3L, x, z, 0.006, 5, 1.0);
            double blend = Math.min(PLATEAU_MAX_BLEND, smoothstep01((plateauNoise - 0.52) / 0.22)
                    * grammar.plateauTendency());
            if (blend > 1.0e-4) {
                // R21: CONTINUOUS terracing (floor + smoothstep, never hard risers).
                double step = plateauStep();
                double yy = (y - baseHeight) / step;
                double fr = yy - Math.floor(yy);
                double terrace = (Math.floor(yy) + smoothstep01(fr)) * step;
                // Continuous bench tilt: a ~470-block profile, so a bench always keeps a slow
                // rise/fall instead of being a perfectly level shelf.
                double tilt = TerrainFields.warped(fieldSeed + 0xB5L, x, z, 0.0021, 3, 0.6) - 0.5;
                terrace += tilt * step * 0.85;
                y = baseHeight + (y - baseHeight) * (1.0 - blend) + terrace * blend;
            }
        }

        // ---------------------------------------------------------------- 4.6 LANDFORMS (PHASE 6/7)
        // Budgeted local geomorphology sits BETWEEN the macro relief and the regional
        // hills: gullies/ravines/fissures/sinkholes/crevasses/depressions. Continuous,
        // bounded to ~55% of the amplitude budget, gravity-damped (7.4).
        if (landforms != null) {
            y += landforms.deltaAt(x, z, A);
        }

        // ---------------------------------------------------------------- 4.7 WORLDGEN V3 LAYERS
        // The V3 elevation authority contributes its LEVEL 1-3 terms on top of the legacy
        // continental skeleton (which IS the LEVEL 0 implementation):
        //   LEVEL 1 mountain belts + major valleys (negative relief)
        //   LEVEL 2 dune morphology (erg / barchan / ripple) + glacial troughs + calderas
        //   LEVEL 3 hydrology (terrain-following river carving)
        // Every term is a CONTINUOUS function of the planet character — no family switch, no
        // province ID touching the height, and the whole stage is bounded by construction
        // (each level has its own budget, see ElevationField). Zero allocation: one reused
        // scratch per shaper.
        elevation.sampleInto(x, z, elevationScratch);
        y += elevationScratch.delta();
        // ACT V3.6: publish the per-term decomposition, not just its sum. `delta()` alone already
        // folded these terms into the height, but the material layer had no way to read them, so
        // the glacial / dune / geothermal surface branches were dead everywhere.
        out.duneRelief = elevationScratch.duneRelief;
        out.glacialRelief = elevationScratch.glacialRelief;
        out.volcanicRelief = elevationScratch.volcanicRelief;
        out.valleyEnvelope = elevationScratch.valleyEnvelope;
        out.basinEnvelope = elevationScratch.basinEnvelope;

        // ---------------------------------------------------------------- 5. REGIONAL relief
        // R21: ROLLING HILLS as a proper mid-frequency FIELD (wavelength ~220 blocks), never
        // local noise. Erosion damps it; regions modulate the amplitude.
        double hills = GlobalTerrainFields.fbm2(fieldSeed + 0xEL, x, z,
                1.0 / PlanetReliefProfile.HILL_WAVELENGTH) - 0.5;
        // ACT-D (a): the hills amplitude is a SHARE of the macro budget `A`, not an absolute
        // 2..40-block band. The region multiplier stays a multiplier (it is a dimensionless local
        // modulation of the share, not a second absolute budget).
        double hillShare = relief.hillAmplitudeShare(ero) * macroSample.hillMultiplier;
        if (hillShare < PlanetReliefProfile.HILL_SHARE_MIN) {
            hillShare = PlanetReliefProfile.HILL_SHARE_MIN;
        } else if (hillShare > PlanetReliefProfile.HILL_SHARE_MAX) {
            hillShare = PlanetReliefProfile.HILL_SHARE_MAX;
        }
        double hillAmp = A * hillShare;
        // The flat-world gate is now RELATIVE too: it reads the same share the amplitude does, so it
        // can no longer compare an A-proportional amplitude against a fixed block threshold (which
        // is exactly how a large world ended up with its hills layer switched off).
        double hills01 = GlobalTerrainFields.clamp01(
                (hillShare - PlanetReliefProfile.HILL_SHARE_MIN)
                        / (PlanetReliefProfile.HILL_SHARE_MAX - PlanetReliefProfile.HILL_SHARE_MIN));
        y += 2.0 * hillAmp * (0.15 + 0.85 * hills01) * hills;

        double medium = GlobalTerrainFields.fbm2(fieldSeed + 0x88L, x, z, 1.0 / 150.0) - 0.5;
        y += rough * A * 0.35 * medium;

        // ---------------------------------------------------------------- 6. LOCAL detail
        // TINY by design: bounded to ~5-12% of the macro amplitude budget.
        double localAmp = A * 0.9 * rough * grammar.localDetailFactor();
        double local = GlobalTerrainFields.fbm2(fieldSeed + 0x99L, x, z, 1.0 / 26.0) - 0.5;
        y += 2.0 * localAmp * local;

        out.height = clamp(y);
        out.continentalness = cont;
        out.erosion = ero;
        out.ridge = ridge;
        out.macroElevation = macro;
        out.localDetailAmplitude = localAmp;
        out.mountainEnvelope = core;
        out.foothillEnvelope = wide;
        out.localMountainCoverage = coverage;
    }

    /**
     * Multi-scale terrain height for a world column (world Y of the surface heightmap).
     *
     * <p>ACT V3.1: this writes into the shaper's own reusable scratch instead of building a
     * {@link TerrainSample} record, so the value is identical but the per-column allocation is
     * gone. Callers on a hot path that already own a scratch should prefer
     * {@link #surfaceHeightInto(int, int, TerrainShaperScratch)} and avoid the shared scratch.
     */
    public int surfaceHeight(int x, int z) {
        sampleInto(x, z, sampleScratch);
        return sampleScratch.height;
    }

    /**
     * ACT V3.1: the ZERO-ALLOCATION surface height.
     *
     * <p>{@link #surfaceHeight(int, int)} is a convenience wrapper that calls {@link #sample},
     * and {@code sample} returns an immutable {@link TerrainSample} RECORD — so the convenience
     * wrapper allocated one record per generated column. At 200k column queries that is 200k
     * short-lived objects for a value that is a single {@code int}.
     *
     * <p>This method is the hot-path form: it writes the composed height straight into a
     * caller-owned {@code double[]} scratch and allocates nothing. It returns exactly the same
     * value as {@code surfaceHeight(x, z)} for the same column — the composition itself is
     * untouched — and it reuses this shaper's own {@code macroSample} scratch, exactly as
     * {@code sample} already does.
     *
     * <p>Thread-safety: the shared {@code macroSample} scratch is per-shaper, so callers that
     * evaluate columns concurrently must give each thread its own {@code TerrainShaper} — which
     * is already the case for the chunk generator (one shaper per world).
     */
    public int surfaceHeightInto(int x, int z, TerrainShaperScratch out) {
        sampleInto(x, z, out);
        return out.height;
    }

    /**
     * ACT 4: LANDFORM IDENTITY — the dominant implemented terrain form at a column, as a
     * lightweight, allocation-free signal used by the surface classifier (per-column, hot path)
     * and by diagnostics. It is derived from the SAME continuous shaping fields the composer
     * uses (never new noise) and only resolves forms that are cheap to evaluate per column;
     * cell-scanned forms (crater chains / calderas) keep their regional identity through the
     * material/geology layers instead of being resolved per column here.
     */
    public LandformIdentity landformIdentity(int x, int z) {
        double A = amplitude;
        double best = 0.0;
        LandformIdentity bestId = LandformIdentity.NONE;
        double widthMul = relief.archetype().rangeWidthMul();

        // Dune seas (positive, dry-gated by the caller-agnostic signature).
        // ACT 6 section 5: same 0.72 amplitude share as the composer, so the reported identity and
        // the actual relief stay consistent.
        double duneS = signature == null ? 0.0 : signature.effectiveDuneStrength();
        if (duneS > 0.0) {
            double v = TerrainFields.duneSeaField(fieldSeed + 0x4C, x, z, duneS, A * 0.72);
            if (v > best) { best = v; bestId = LandformIdentity.DUNE; }
        }
        // Mountain systems dominate when the belt envelope is high (planet-coverage proxy —
        // deliberately not the full local region lookup, to keep the hot path cheap).
        double env = MountainRangeField.rangeEnvelope(fieldSeed,
                relief.mountainCoverage(), widthMul, x, z);
        if (env > 0.5) {
            best = env;
            bestId = LandformIdentity.MOUNTAIN;
        } else {
            double vf = MountainRangeField.valley(fieldSeed, widthMul, x, z);
            if (vf > 0.55 && vf > best) { best = vf; bestId = LandformIdentity.VALLEY; }
        }
        // Canyons (rare, very large cuts).
        double canyonS = signature == null ? 0.0 : signature.effectiveCanyonStrength();
        if (canyonS > 0.0) {
            double ridgeLine = GlobalTerrainFields.ridgeField(fieldSeed + 0x9FL, x, z,
                    1.0 / 900.0, 400.0);
            if (ridgeLine > 0.972) {
                double v = clamp01((ridgeLine - 0.972) / 0.028);
                if (v > best) { best = v; bestId = LandformIdentity.CANYON; }
            }
        }
        // Crystal ridges on crystal-rich worlds.
        if (profile != null && profile.crystalAbundance() > 0.6) {
            double v = TerrainFields.crystalRidgeField(fieldSeed + 0x5EL, x, z, 0.65, A * 0.9);
            if (v > best) { best = v; bestId = LandformIdentity.CRYSTAL_RIDGE; }
        }
        // Local cuts (gullies / fissures) exposed at the surface.
        double exposure = landformExposure(x, z);
        if (exposure > 0.25 && exposure > best) {
            best = exposure;
            bestId = LandformIdentity.GULLY;
        }
        return bestId;
    }

    /** ACT 4: LANDFORM STRENGTH in [0,1] at a column (0 = ordinary terrain, 1 = dominant form).
     *  Cheap companion of {@link #landformIdentity}; allocation-free, deterministic. */
    public double landformStrength(int x, int z) {
        double A = amplitude;
        double best = 0.0;
        double widthMul = relief.archetype().rangeWidthMul();
        if (MountainRangeField.rangeEnvelope(fieldSeed,
                relief.mountainCoverage(), widthMul, x, z) > 0.5) return 1.0;
        double duneS = signature == null ? 0.0 : signature.effectiveDuneStrength();
        if (duneS > 0.0) {
            // ACT 6 section 5: same 0.72 amplitude share as the composer (was 0.42).
            best = Math.max(best, TerrainFields.duneSeaField(
                    fieldSeed + 0x4C, x, z, duneS, A * 0.72));
        }
        best = Math.max(best, MountainRangeField.valley(fieldSeed, widthMul, x, z));
        best = Math.max(best, landformExposure(x, z));
        if (profile != null && profile.crystalAbundance() > 0.6) {
            best = Math.max(best, TerrainFields.crystalRidgeField(
                    fieldSeed + 0x5EL, x, z, 0.65, A * 0.9));
        }
        return clamp01(best);
    }

    /**
     * ACT 6 section 3-C: the FINAL height bound.
     *
     * <p>The previous implementation was a HARD min/max clip: any column whose raw height left the
     * legal band was snapped flat onto the bound. On a MOUNTAINOUS world the macro relief routinely
     * over-shot the ceiling by more than a hundred blocks, so a whole mountain top — measured at 632
     * consecutive columns of IDENTICAL height — was pinned exactly onto {@code maxHeight}. That is
     * precisely the reported artificial flat strip, and it is why merely rounding the transition did
     * not remove it: the transition was not the problem, the SATURATION was.
     *
     * <p>Two changes together fix it (see the constructor for the widened band):
     * <ul>
     *   <li>the band is wider (-4A .. +6A instead of -3A .. +4A), so ordinary relief no longer
     *       saturates against it at all — a mountain keeps its own shape;</li>
     *   <li>what does still reach a bound approaches it ASYMPTOTICALLY (see {@link #softKnee}), so
     *       the last few blocks keep a small but non-zero slope instead of a dead-flat shelf.</li>
     * </ul>
     * The final height is still guaranteed to stay inside the legal range by the integer clamp in
     * {@link #clamp(double)}.
     */
    private double softBound(double y) {
        double lo = minHeight;
        double hi = maxHeight;
        double knee = boundKnee();
        if (y > lo + knee && y < hi - knee) return y;
        if (y <= lo + knee) return lo + knee * softKnee((y - lo) / knee);
        return hi - knee * softKnee((hi - y) / knee);
    }

    /** ACT-D (b): this world's soft-knee band - a share of {@code A}, floored in blocks. */
    private double boundKnee() {
        return BOUND_KNEE;
    }

    /** The knee profile: 0 at t = 0, 1 at t = 1, slope 0 -> 1, no overshoot (smooth Hermite ramp). */
    private static double softKnee(double t) {
        if (t >= 1.0) return t;                    // identity through the whole interior
        if (t <= -40.0) return 0.0;                // numerically saturated (far past the bound)
        double s = 1.0 - t;                        // >= 0: how far past the knee we are
        // h(s) = 1 - (1 + 2s) * exp(-3s) satisfies h(0) = 0, h'(0) = 1 and h(inf) = 1, and it is
        // strictly increasing. So the knee is C1-continuous against the identity region AND keeps a
        // non-zero slope all the way out to the bound: the height approaches the limit ASYMPTOTICALLY
        // instead of being pinned flat onto it. (A plain smoothstep clamps to 0 for every t <= 0,
        // which pinned any column that over-shot the bound by more than one knee width onto the bound
        // EXACTLY — that was the long level shelf measured at the top of a mountain range.)
        return 1.0 - (1.0 - (1.0 + 2.0 * s) * Math.exp(-3.0 * s));
    }

    /**
     * ACT 6 section 3-B: fade a morphology strength to a hard zero. Below
     * {@link #MORPHOLOGY_EPSILON} the result is exactly 0, so the field is never entered at all.
     */
    private static double morphologyGate(double strength) {
        return strength <= MORPHOLOGY_EPSILON ? 0.0 : strength;
    }

    /**
     * The final integer height. The soft bound above keeps essentially every column strictly inside
     * the range; this hard clamp is the last-resort guarantee and is now reached only by rounding a
     * fraction of a block, so it can no longer form a wide flat plateau.
     */
    private int clamp(double y) {
        int v = (int) Math.round(softBound(y));
        return Math.max((int) minHeight, Math.min((int) maxHeight, v));
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Clamped smoothstep transition of a normalized value (may be outside [0,1]). */
    private static double smoothstep01(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
