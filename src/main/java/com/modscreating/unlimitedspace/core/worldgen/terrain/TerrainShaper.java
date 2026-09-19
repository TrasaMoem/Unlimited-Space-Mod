package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetypeSelector;

/**
 * HIERARCHICAL terrain composer (R20 terrain-quality stage).
 *
 * <p>Terrain is built TOP-DOWN: large geography first, details last. Small details can never
 * destroy the large forms because their amplitude budget is bounded (~5вЂ“10% of the macro
 * relief):
 *
 * <pre>
 * HEIGHT =
 *   GLOBAL    continents / oceans / global elevation   (wavelength ~3400, amp в‰¤ 1.75A)
 * + MACRO     mountain chains / valleys / basins       (wavelength 500вЂ“1500, amp в‰¤ 3.3A)
 *   В· provinces (smooth, damped uplift/bias/roughness)
 *   В· RARE FEATURES (craters / volcanoes / canyons / dunes / spires вЂ” rare and LARGE)
 * + REGIONAL  medium relief                            (wavelength ~150, amp в‰¤ 0.35A)
 * + LOCAL     tiny bounded detail                      (wavelength ~26,  amp в‰¤ ~10% macro)
 * </pre>
 *
 * <p>Scale separation is enforced by construction: every layer has its own frequency band and
 * its own amplitude budget (see {@link GlobalTerrainFields}). Built ONCE per planet and cached
 * by the chunk generator; per-column evaluation is a handful of pure hash mixes with no
 * allocations. Deterministic: same {@code (planetSeed, identity, coordinates)} в†’ same terrain.
 */
public final class TerrainShaper {

    /** Crater field cell size вЂ” craters are rare, big geological events (radius up to ~360). */
    private static final int CRATER_CELL = 640;
    /** Volcanic cone cell size вЂ” rare, large cones with calderas. */
    private static final int CONE_CELL = 512;
    /** Crystal spire cell size (localized, never planet-wide). */
    private static final int SPIRE_CELL = 192;

    private final TerrainGenerator base;
    private final GeologicalProvinceMap provinces;
    private final ProvinceTerrainModifier provinceModifiers;
    private final TerrainSignature signature;
    private final PlanetPhysicalProfile profile;
    private final TerrainArchetypeSelector.ArchetypePair archetype;
    private final TerrainArchetypeSelector.TerrainArchetypeGrammar grammar;
    // R21: planet-level relief identity (archetype + mountainCoverage) + biome regions that
    // modulate the coverage LOCALLY. Together they decide whether this world is flat or a
    // mountain world вЂ” local noise may never create the geography by itself.
    private final PlanetReliefProfile relief;
    private final BiomeRegionMap regions;
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
        this.fieldSeed = fieldSeed;
        this.amplitude = Math.max(6.0, amplitude);
        this.baseHeight = baseHeight;
        this.minHeight = baseHeight - 3.0 * this.amplitude;
        this.maxHeight = baseHeight + 4.0 * this.amplitude;
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
        long fieldSeed = Seeds.derive(planetSeed, "us.terrain.fields");
        ProvinceTerrainModifier mods = ProvinceTerrainModifier.create(planetSeed, provinces, signature);
        double amp = Math.max(6.0, terrainAmplitude * (signature == null ? 1.0 : signature.amplitudeMul()));
        TerrainArchetypeSelector.ArchetypePair arch =
                TerrainArchetypeSelector.create(planetSeed, profile);
        PlanetReliefProfile r = relief != null ? relief
                : ReliefArchetypeSelector.create(planetSeed, profile);
        BiomeRegionMap map = regions;
        if (map == null) {
            // Same derivation as PlanetGeologyProfile.create в†’ both paths agree exactly.
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
                r, map, fieldSeed, amp, baseHeight);
    }

    /** R21: the planet's relief identity (diagnostics / debug screen). */
    public PlanetReliefProfile relief() {
        return relief;
    }

    /** R21: the planet's large biome-region map (diagnostics / debug screen). */
    public BiomeRegionMap regions() {
        return regions;
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

        /**
     * Hierarchical terrain sample for a world column: the final height PLUS the global fields
     * (continentalness / erosion / ridge) that produced it. Single evaluation, many consumers
     * (chunk fill, debug screen, headless diagnostics).
     */
    public TerrainSample sample(int x, int z) {
        double A = amplitude;

        // ---------------------------------------------------------------- 1. GLOBAL geography
        // Continentalness decides WHAT PART OF THE PLANET IS LAND before any height exists.
        double cont = GlobalTerrainFields.continentalness(fieldSeed, x, z);
        cont = GlobalTerrainFields.clamp01(cont + (grammar.continentalBias() - 0.5) * 0.7);
        double gShape = (cont - 0.5) * 2.0;                                   // -1..1
        // R21: the global dome is deliberately NOT the main relief carrier anymore вЂ” it only
        // decides what part of the planet is land and gives the broad ocean/land tilt. The
        // R20 1.75A dome was what made every planet read as "one huge smooth uplift".
        double global = A * 1.30 * Math.signum(gShape) * Math.pow(Math.abs(gShape), 1.4);
        // Ocean-heavy archetypes: a genuine global sea-level drop (whole map sits lower),
        // so oceans read as geography, not as random puddles.
        global -= A * 1.1 * Math.max(0.0, 0.5 - grammar.continentalBias());

        // Effective erosion regime: profile identity + spatial erosion field + archetype bias.
        double eroField = GlobalTerrainFields.erosionField(fieldSeed + 0x7L, x, z);
        double eroProfile = profile == null ? 0.5 : profile.erosion();
        double ero = clamp01(0.45 * eroProfile + 0.40 * eroField
                + 0.35 * (grammar.erosionBias() - 0.5));

        // ---------------------------------------------------------------- 2. MACRO relief
        // R21: mountains come from the PLANET'S relief identity + the local BIOME REGION,
        // never from a product of masks. PLAIN в†’ LOW HILLS в†’ FOOTHILLS в†’ MOUNTAIN в†’ PEAKS.
        BiomeRegionMap.Context regionCtx = regions != null
                ? regions.contextAt(x, z) : BiomeRegionMap.Context.single(null, 1.0);
        double localCoverage = regionCtx == null ? relief.mountainCoverage()
                : regionCtx.localMountainCoverage(relief.mountainCoverage());
        double widthMul = relief.archetype().rangeWidthMul();
        double sigRidge = signature == null ? 0.5 : signature.effectiveRidgeStrength();
        // Tectonics still gate WHERE belts may exist, but never by 5 chained factors вЂ” the
        // R20 product-of-masks was what flattened every mountain system into a gentle dome.
        double tectGate = 0.95 + 0.05 * (profile == null ? 0.5 : profile.tectonicActivity());
        double coverage = GlobalTerrainFields.clamp01(localCoverage
                * (0.95 + 0.05 * sigRidge) * tectGate);

        double core = MountainRangeField.rangeEnvelope(fieldSeed, coverage, widthMul, x, z);
        double wide = MountainRangeField.foothillBand(fieldSeed, coverage, widthMul, x, z);
        double band = GlobalTerrainFields.clamp01(wide - core);          // foothills zone
        double ridge = MountainRangeField.crest(fieldSeed, widthMul, x, z);

        double macro = A * 2.45 * core * (0.45 + 0.55 * Math.pow(ridge, 1.4))
                + A * 0.95 * band * (0.50 + 0.50 * ridge);

        // Valleys: wide smooth corridors BETWEEN the range systems (never random holes).
        double valleyField = MountainRangeField.valley(fieldSeed, widthMul, x, z);
        double lowland = 1.0 - 0.8 * core;
        macro -= A * 0.85 * relief.archetype().valleyStrength() * valleyField
                * (0.4 + 0.6 * lowland);

        // Basins: large smooth continental depressions (never a hole, always a region).
        double basin = GlobalTerrainFields.basinField(fieldSeed + 0xDL, x, z);
        double basinT = smoothstep01((basin - 0.58) / 0.34);
        macro -= A * 1.25 * grammar.basinStrength() * basinT;

        // ---------------------------------------------------------------- 3. PROVINCE shaping
        ProvinceTerrainModifier.Sample s = provinceModifiers != null
                ? provinceModifiers.sample(x, z)
                : ProvinceTerrainModifier.Sample.NEUTRAL;
        double rough = s.isNeutral() ? 1.0 : s.roughness();
        double up = s.isNeutral() ? 1.0 : clamp(s.uplift(), 0.85, 1.25);
        double y = baseHeight + global + s.bias() * 0.5 + macro * up;

        // R18 unified province context for this column (single source of truth).
        double elevation01 = GlobalTerrainFields.clamp01((y - minHeight) / (maxHeight - minHeight));
        GeologicalProvinceContext ctx = provinces != null
                ? provinces.contextAt(x, z, elevation01)
                : GeologicalProvinceContext.neutral(null);

        // ---------------------------------------------------------------- 4. RARE features
        // Craters: rare, LARGE, tiered (MICRO..MEGA) вЂ” density driven by impactFrequency.
        double craterDensity = clamp01((signature == null ? 0.0 : signature.effectiveCraterDensity())
                * grammar.craterFrequencyMul());
        double craterDeform = TerrainFields.craterDeform(fieldSeed + 0x2A, x, z, CRATER_CELL, craterDensity, A * 1.4);
        y += craterDeform;
        // Volcanic cones strengthened inside volcanic / geothermal provinces (R20: gated by
        // CONTINUOUS province intensity вЂ” boolean gates produced 1-block walls at borders).
        double volcStrength = clamp01((signature == null ? 0.0 : signature.effectiveVolcanicStrength())
                * grammar.volcanicStrengthMul());
        double volcIntensity = ctx.volcanicIntensity();
        volcStrength = clamp01(volcStrength + (0.5 - 0.5 * volcStrength) * volcIntensity);
        double coneDeform = TerrainFields.volcanicDeform(fieldSeed + 0x3B, x, z, CONE_CELL, volcStrength, A * 1.6);
        y += coneDeform;
        double duneDeform = TerrainFields.duneField(fieldSeed + 0x4C, x, z,
                signature == null ? 0.0 : signature.effectiveDuneStrength(), A);
        y += duneDeform;
        // Crystal spires: PER-COLUMN activation scaled by continuous CRYSTAL/GEOTHERMAL intensity.
        double spireDeform = TerrainFields.spireField(fieldSeed + 0x5D, x, z, SPIRE_CELL,
                0.6 * ctx.crystalIntensity(), A);
        y += spireDeform;
        // Lava channels follow volcanic provinces; they never punch terrain bounds.
        if (ctx.lavaIntensity() > 0.0) {
            double lavaDeform = ctx.lavaIntensity()
                    * TerrainFields.lavaChannel(fieldSeed + 0x0EA, x, z,
                            ContextUtils.volcanicChannelStrength(ctx, signature), A);
            y += lavaDeform;
        } else {
        }
        // Canyons: RARE, very large, one impressive cut per region (not many little cracks).
        double canyonStrength = clamp01((signature == null ? 0.0 : signature.effectiveCanyonStrength())
                * (0.4 + 0.6 * ero))
                * (1.0 - ctx.lavaIntensity());   // R20: continuous, no boolean gate
        if (canyonStrength > 0.0) {
            double ridgeLine = GlobalTerrainFields.ridgeField(fieldSeed + 0x9FL, x, z, 1.0 / 900.0, 400.0);
            if (ridgeLine > 0.972) {
                double channel = Math.pow((ridgeLine - 0.972) / 0.028, 1.5);
                double carve = canyonStrength * A * 1.1 * channel;
                y -= carve;
            }
        }

        // Plateau terracing (mesas): only where the archetype wants plateaus.
        if (grammar.plateauTendency() > 0.30
                && signature != null && signature.primary() == TerrainMorphology.PLATEAU) {
            double plateauNoise = TerrainFields.warped(fieldSeed + 0xB3L, x, z, 0.006, 5, 1.0);
            if (plateauNoise > 0.58) {
                double blend = Math.min(1.0, (plateauNoise - 0.58) / 0.18) * grammar.plateauTendency();
                // R21: CONTINUOUS terracing. The R20 round() ladder made each riser a hard
                // 9-block step (a hidden single-column wall); floor+smoothstep keeps the same
                // staircase silhouette but with smooth risers.
                double yy = (y - baseHeight) / 9.0;
                double terrace = (Math.floor(yy) + smoothstep01(yy - Math.floor(yy))) * 9.0;
                y = baseHeight + (y - baseHeight) * (1.0 - blend) + terrace * blend;
            }
        }

        // ---------------------------------------------------------------- 5. REGIONAL relief
        // R21: ROLLING HILLS are back вЂ” but as a proper mid-frequency FIELD (wavelength
        // ~220 blocks, amplitude 10вЂ“40 blocks), NOT as local noise. On a FLAT planet the
        // layer is nearly off; on a ROLLING planet it dominates; on a MOUNTAINOUS planet it
        // becomes the foothill texture between the belts. Erosion damps it.
        double hills = GlobalTerrainFields.fbm2(fieldSeed + 0xEL, x, z,
                1.0 / PlanetReliefProfile.HILL_WAVELENGTH) - 0.5;
        double hillAmp = relief.hillAmplitudeBlocks(ero)
                * (regionCtx == null ? 1.0 : regionCtx.hillMultiplier());
        double hills01 = GlobalTerrainFields.clamp01((hillAmp - 2.0) / 36.0); // flat-world gate
        y += 2.0 * hillAmp * (0.15 + 0.85 * hills01) * hills;

        double medium = GlobalTerrainFields.fbm2(fieldSeed + 0x88L, x, z, 1.0 / 150.0) - 0.5;
        y += rough * A * 0.35 * medium;

        // ---------------------------------------------------------------- 6. LOCAL detail
        // TINY by design: bounded to ~5вЂ“12% of the macro amplitude budget. Removing it must
        // leave a beautiful landscape; it only adds surface texture.
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

    private int clamp(double y) {
        int v = (int) Math.round(y);
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
