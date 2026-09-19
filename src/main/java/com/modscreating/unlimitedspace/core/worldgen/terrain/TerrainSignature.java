package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * The planet-wide terrain signature (R17 terrain-diversity stage).
 *
 * <pre>
 * PHYSICAL PROFILE -&gt; TERRAIN MORPHOLOGY -&gt; TERRAIN SIGNATURE (this)
 * </pre>
 *
 * <p>A signature is the planet's morphology identity: a primary morphology blended with a
 * secondary one, plus the <em>feature strengths</em> that drive the shaping fields (ridged
 * mountain ranges, canyon carving, crater fields, volcanic cones, dune seas). Two planets with
 * the same material palette can still look entirely different because of this signature.
 *
 * <p>Pure domain: no Minecraft types. Deterministic — a pure function of
 * {@code (planetSeed, physical profile)} via {@link TerrainSignatureSelector}.
 *
 * @param primary          dominant morphology
 * @param secondary        blended-in morphology
 * @param blend            secondary influence in [0,1] (0 = pure primary)
 * @param ridgeStrength    mountain/ridge uplift strength in [0,1]
 * @param canyonStrength   canyon carving strength in [0,1]
 * @param craterDensity    crater field density in [0,1]
 * @param volcanicStrength volcanic cone/channel strength in [0,1]
 * @param duneStrength     dune field strength in [0,1]
 * @param upliftBias       global vertical bias in [0,1] (0 = neutral, 0.5 = balanced)
 */
public record TerrainSignature(
        TerrainMorphology primary,
        TerrainMorphology secondary,
        double blend,
        double ridgeStrength,
        double canyonStrength,
        double craterDensity,
        double volcanicStrength,
        double duneStrength,
        double upliftBias
) {

    public TerrainSignature {
        if (primary == null) primary = TerrainMorphology.PLAINS;
        if (secondary == null) secondary = primary;
        blend = clamp01(blend);
        ridgeStrength = clamp01(ridgeStrength);
        canyonStrength = clamp01(canyonStrength);
        craterDensity = clamp01(craterDensity);
        volcanicStrength = clamp01(volcanicStrength);
        duneStrength = clamp01(duneStrength);
        upliftBias = clamp01(upliftBias);
    }

    /** Blended amplitude multiplier of the two morphologies. */
    public double amplitudeMul() {
        return lerp(primary.amplitudeMul(), secondary.amplitudeMul(), blend);
    }

    /** Blended roughness multiplier of the two morphologies. */
    public double roughnessMul() {
        return lerp(primary.roughnessMul(), secondary.roughnessMul(), blend);
    }

    /** Blended frequency multiplier of the two morphologies. */
    public double frequencyMul() {
        return lerp(primary.frequencyMul(), secondary.frequencyMul(), blend);
    }

    /** Effective crater density actually used by the crater field. */
    public double effectiveCraterDensity() {
        return craterDensity * (primary == TerrainMorphology.CRATERED ? 1.0 : 0.45)
                + (secondary == TerrainMorphology.CRATERED ? 0.25 * blend : 0.0);
    }

    /** Effective canyon strength actually used by the canyon carver. */
    public double effectiveCanyonStrength() {
        return canyonStrength * (primary == TerrainMorphology.CANYON || primary == TerrainMorphology.BADLANDS ? 1.0 : 0.5)
                + (secondary == TerrainMorphology.CANYON || secondary == TerrainMorphology.BADLANDS ? 0.35 * blend : 0.0);
    }

    /** Effective volcanic strength actually used by the volcanic shaper. */
    public double effectiveVolcanicStrength() {
        return volcanicStrength * (primary == TerrainMorphology.VOLCANIC ? 1.0 : 0.5)
                + (secondary == TerrainMorphology.VOLCANIC ? 0.35 * blend : 0.0);
    }

    /** Effective dune strength actually used by the dune field. */
    public double effectiveDuneStrength() {
        return duneStrength * (primary == TerrainMorphology.DUNES ? 1.0 : 0.5);
    }

    /** Effective ridge strength actually used by the mountain shaper. */
    public double effectiveRidgeStrength() {
        double primaryScale = primary.isReliefDominated() ? 1.0 : 0.45;
        double secondaryScale = secondary.isReliefDominated() ? 1.0 : 0.45;
        return ridgeStrength * lerp(primaryScale, secondaryScale, blend);
    }

    /** Short summary for diagnostics. */
    public String summary() {
        return String.format(java.util.Locale.ROOT,
                "%s/%s(%.2f) ridge=%.2f canyon=%.2f crater=%.2f volc=%.2f dune=%.2f",
                primary, secondary, blend,
                effectiveRidgeStrength(), effectiveCanyonStrength(),
                effectiveCraterDensity(), effectiveVolcanicStrength(), effectiveDuneStrength());
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
