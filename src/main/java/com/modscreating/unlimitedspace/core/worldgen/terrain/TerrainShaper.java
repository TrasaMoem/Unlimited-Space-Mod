package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;
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
 *   В· provinces (smooth, damped uplift/bias/roughness + ACT4 carve/dune channels)
 *   В· RARE FEATURES (terraced craters / crater chains / volcano CALDERAS / dune SEAS /
 *                    mega-gullies / crystal ridges / canyons / lava / spires)
 *   В· ACT4 low-gravity slump terraces (basin-bound, bounded ~-0.3A)
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
     */
    private static final double PLATEAU_STEP = 9.0;

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
     * ACT 6 section 3-C: the width of the soft-knee band at each height bound, in blocks.
     *
     * <p>Beyond this distance from a bound the height is returned unchanged (slope exactly 1);
     * inside it the slope is smoothly reduced to 0, so the height approaches the bound
     * asymptotically instead of being pinned flat onto it.
     */
    private static final double BOUND_KNEE = 40.0;

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
    private final BiomeRegionMap regions;
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

    private TerrainShaper(TerrainGenerator base, GeologicalProvinceMap provinces,
                          ProvinceTerrainModifier provinceModifiers, TerrainSignature signature,
                          PlanetPhysicalProfile profile,
                          TerrainArchetypeSelector.ArchetypePair archetype,
                          PlanetReliefProfile relief, BiomeRegionMap regions,
                          LandformField landforms, PlanetClimateProfile climate,
                          long fieldSeed, double amplitude, double baseHeight) {
        this.base = base;
        this.provinces = provinces;
        this.provinceModifiers = provinceModifiers;
        this.signature = signature;
        this.profile = profile;
        this.archetype = archetype;
        this.grammar = archetype.blended();
        this.relief = relief;
        this.regions = regions;
        this.landforms = landforms;
        this.climate = climate;
        this.fieldSeed = fieldSeed;
        this.amplitude = Math.max(6.0, amplitude);
        this.baseHeight = baseHeight;
        // ACT 6 section 3-C: the band is widened from (-3A .. +4A) to (-4A .. +6A) so that ordinary
        // relief — in particular a whole mountain top — no longer saturates against the legal range
        // at all. Combined with the asymptotic knee in {@link #softBound(double)} below, this is what
        // removed the reported artificial flat strip on mountainous worlds.
        this.minHeight = baseHeight - 4.0 * this.amplitude;
        this.maxHeight = baseHeight + 6.0 * this.amplitude;
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

    /**
     * R21 factory with explicit planet-level geography. {@code relief} / {@code regions} may be
     * {@code null}, in which case they are derived deterministically from the planet seed so
     * the shaper always agrees with {@code PlanetGeologyProfile}.
     */
    public static TerrainShaper create(TerrainGenerator base, long planetSeed,
                                       PlanetPhysicalProfile profile,
                                       GeologicalProvinceMap provinces,
                                       TerrainSignature signature,
                                       double baseHeight, double terrainAmplitude,
                                       PlanetReliefProfile relief, BiomeRegionMap regions) {
        return create(base, planetSeed, profile, provinces, signature, baseHeight,
                terrainAmplitude, relief, regions, null);
    }

    /**
     * ACT 4 factory: adds the (optional) climate subsystem. When {@code climate} is present the
     * shaper's regional context becomes climate-aware — the SAME macro-region authority the
     * chunk generator uses, so terrain and material systems can never disagree near region
     * boundaries. When {@code null}, the legacy non-climate-aware region path is used.
     */
    public static TerrainShaper create(TerrainGenerator base, long planetSeed,
                                       PlanetPhysicalProfile profile,
                                       GeologicalProvinceMap provinces,
                                       TerrainSignature signature,
                                       double baseHeight, double terrainAmplitude,
                                       PlanetReliefProfile relief, BiomeRegionMap regions,
                                       PlanetClimateProfile climate) {
        long fieldSeed = Seeds.derive(planetSeed, "us.terrain.fields");
        ProvinceTerrainModifier mods = ProvinceTerrainModifier.create(planetSeed, provinces, signature);
        double amp = Math.max(6.0, terrainAmplitude * (signature == null ? 1.0 : signature.amplitudeMul()));
        TerrainArchetypeSelector.ArchetypePair arch =
                TerrainArchetypeSelector.create(planetSeed, profile);
        PlanetReliefProfile r = relief != null ? relief
                : ReliefArchetypeSelector.create(planetSeed, profile);
        BiomeRegionMap map = regions;
        if (map == null) {
            // Same derivation as PlanetGeologyProfile.create — both paths agree exactly.
            PlanetPhysicalProfile p = profile;
            map = BiomeRegionMap.create(Seeds.derive(planetSeed, "us.biome.regions"),
                    p == null ? 0.5 : p.temperature(),
                    p == null ? 0.5 : p.humidity(),
                    p == null ? 0.0 : p.crystalAbundance(),
                    p == null ? 0.0 : p.volcanicActivity(),
                    p == null ? 0.0 : p.impactFrequency(),
                    p == null ? 0.5 : p.tectonicActivity());
        }
        return new TerrainShaper(base, provinces, mods, signature, profile, arch,
                r, map, LandformField.create(planetSeed, profile), climate, fieldSeed, amp, baseHeight);
    }

    /** R21: the planet's relief identity (diagnostics / debug screen). */
    public PlanetReliefProfile relief() {
        return relief;
    }

    /** R21: the planet's large biome-region map (diagnostics / debug screen). */
    public BiomeRegionMap regions() {
        return regions;
    }

    /** PHASE 6: the planet's budgeted landform field (diagnostics / surface strata). */
    public LandformField landforms() {
        return landforms;
    }

    /** ACT 4: the climate subsystem this shaper was built with (diagnostics / debug screen). */
    public PlanetClimateProfile climate() {
        return climate;
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
        // R21: mountains come from the PLANET'S relief identity + the local BIOME REGION,
        // never from a product of masks. ACT 4: the regional context is CLIMATE-AWARE — the
        // same authority the chunk generator uses, so terrain and materials always agree.
        BiomeRegionMap.Context regionCtx = regions != null
                ? (hasClimate ? regions.contextAt(x, z, localTemp) : regions.contextAt(x, z))
                : BiomeRegionMap.Context.single(null, 1.0);
        double localCoverage = regionCtx == null ? relief.mountainCoverage()
                : regionCtx.localMountainCoverage(relief.mountainCoverage());
        double widthMul = relief.archetype().rangeWidthMul();
        double sigRidge = signature == null ? 0.5 : signature.effectiveRidgeStrength();
        double tectGate = 0.95 + 0.05 * (profile == null ? 0.5 : profile.tectonicActivity());
        double coverage = GlobalTerrainFields.clamp01(localCoverage
                * (0.95 + 0.05 * sigRidge) * tectGate);

        double core = MountainRangeField.rangeEnvelope(fieldSeed, coverage, widthMul, x, z);
        double wide = MountainRangeField.foothillBand(fieldSeed, coverage, widthMul, x, z);
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
        // ACT 3 (P1.3): the major shaping authority is the MEDIUM (900) region-aware province
        // layer; the 192 layer contributes only continuous micro roughness texture.
        // ACT 4: the previously unused province carve / dune channels are now consumed.
        ProvinceTerrainModifier.Sample s = provinceModifiers != null
                ? provinceModifiers.sample(x, z, regionCtx == null ? null : regionCtx.region())
                : ProvinceTerrainModifier.Sample.NEUTRAL;
        double rough = s.isNeutral() ? 1.0 : s.roughness();
        // ACT 4: province uplift range widened from 0.85..1.25 to the SMALLEST safe expansion
        // (0.80..1.38) so provincial macro-relief stays visible without breaking continuity.
        double up = s.isNeutral() ? 1.0 : clamp(s.uplift(), 0.80, 1.38);
        double carveAffinity = s.isNeutral() ? 1.0 : clamp(0.80 + 0.20 * s.carve(), 0.80, 1.20);
        double duneAffinity = s.isNeutral() ? 1.0 : clamp(0.5 + 0.5 * s.dune(), 0.5, 1.0);

        // ACT 4: bounded gravity response — applied ONCE on the macro/regional relief stage
        // (never 10 times on different landforms). Low gravity widens relief, high damps it.
        double gravityMul = gravityReliefMul();
        // ACT 4: activate the (previously unused) upliftBias channel — deterministic, bounded,
        // planet-global so spatially smooth, and subordinate to the macro terrain.
        double upliftBias = signature == null ? 0.5 : signature.upliftBias();

        double y = baseHeight + global + s.bias() * 0.5 + macro * up * gravityMul
                + (upliftBias - 0.40) * A * 0.8;

        // R18 unified province context for this column (single source of truth).
        double elevation01 = GlobalTerrainFields.clamp01((y - minHeight) / (maxHeight - minHeight));
        GeologicalProvinceContext ctx = provinces != null
                ? provinces.contextAt(x, z, elevation01)
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
        // ACT 4: DUNE SEAS — broad regional dunes gated by dryness + province dune affinity.
        // ACT 6 section 3-B: the strength is gated like every other morphology.
        // ACT 6 section 5: the amplitude budget share rises 0.42*A -> 0.72*A. The dune morphology
        // previously had only 42% of the amplitude budget, which is why even a well-shaped crest
        // stayed a barely visible swell. 0.72 is the PRIMARY share; TerrainFields.duneSeaField adds
        // its secondary detail and micro octaves INSIDE the same supplied budget, so the total
        // relief stays bounded and continuous.
        double duneStrength = morphologyGate(signature == null ? 0.0 : signature.effectiveDuneStrength());
        if (duneStrength > 0.0) {
            double duneAmp = A * 0.72 * aridAffinity * duneAffinity;
            y += TerrainFields.duneSeaField(fieldSeed + 0x4C, x, z, duneStrength, duneAmp);
        }
        // ACT 4: CRYSTAL RIDGES — broad secondary crest on crystal-prone provinces (never a
        // giant spike, never planet-wide). ACT 6 section 3-B: gated like every other morphology.
        double cryst = morphologyGate(ctx.crystalIntensity());
        if (cryst > 0.02) {
            y += TerrainFields.crystalRidgeField(fieldSeed + 0x5EL, x, z, 0.65 * cryst, A * 0.9);
        }
        // Crystal spires: keep the existing per-column activation (bounded by territory).
        y += TerrainFields.spireField(fieldSeed + 0x5D, x, z, SPIRE_CELL,
                0.6 * cryst, A);
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
                // R21: CONTINUOUS terracing (floor + smoothstep, never hard 9-block risers).
                double yy = (y - baseHeight) / PLATEAU_STEP;
                double fr = yy - Math.floor(yy);
                double terrace = (Math.floor(yy) + smoothstep01(fr)) * PLATEAU_STEP;
                // Continuous bench tilt: a ~470-block profile, so a bench always keeps a slow
                // rise/fall instead of being a perfectly level shelf.
                double tilt = TerrainFields.warped(fieldSeed + 0xB5L, x, z, 0.0021, 3, 0.6) - 0.5;
                terrace += tilt * PLATEAU_STEP * 0.85;
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

        // ---------------------------------------------------------------- 5. REGIONAL relief
        // R21: ROLLING HILLS as a proper mid-frequency FIELD (wavelength ~220 blocks), never
        // local noise. Erosion damps it; regions modulate the amplitude.
        double hills = GlobalTerrainFields.fbm2(fieldSeed + 0xEL, x, z,
                1.0 / PlanetReliefProfile.HILL_WAVELENGTH) - 0.5;
        double hillAmp = relief.hillAmplitudeBlocks(ero)
                * (regionCtx == null ? 1.0 : regionCtx.hillMultiplier());
        double hills01 = GlobalTerrainFields.clamp01((hillAmp - 2.0) / 36.0); // flat-world gate
        y += 2.0 * hillAmp * (0.15 + 0.85 * hills01) * hills;

        double medium = GlobalTerrainFields.fbm2(fieldSeed + 0x88L, x, z, 1.0 / 150.0) - 0.5;
        y += rough * A * 0.35 * medium;

        // ---------------------------------------------------------------- 6. LOCAL detail
        // TINY by design: bounded to ~5-12% of the macro amplitude budget.
        double localAmp = A * 0.9 * rough * grammar.localDetailFactor();
        double local = GlobalTerrainFields.fbm2(fieldSeed + 0x99L, x, z, 1.0 / 26.0) - 0.5;
        y += 2.0 * localAmp * local;

        return new TerrainSample(clamp(y), cont, ero, ridge, macro, localAmp,
                core, wide, coverage);
    }

    /** Multi-scale terrain height for a world column (world Y of the surface heightmap). */
    public int surfaceHeight(int x, int z) {
        return sample(x, z).height();
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
        if (y > lo + BOUND_KNEE && y < hi - BOUND_KNEE) return y;
        if (y <= lo + BOUND_KNEE) return lo + BOUND_KNEE * softKnee((y - lo) / BOUND_KNEE);
        return hi - BOUND_KNEE * softKnee((hi - y) / BOUND_KNEE);
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
