package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Abstract terrain morphology of a planet or province (R17 terrain-diversity stage).
 *
 * <p>Morphology describes the <em>shape</em> of the landscape (mountain ranges, canyon
 * networks, crater fields, dune seas, ...) — deliberately independent of any Minecraft block.
 * The same morphology can be realised with many material palettes, and the same palette can
 * appear under several morphologies.
 *
 * <p>Each constant carries:
 * <ul>
 *   <li>shape multipliers (amplitude / roughness / frequency) applied to the base terrain;</li>
 *   <li>affinity weights used by {@link TerrainSignatureSelector} to score how well this
 *       morphology fits a planet's {@link PlanetPhysicalProfile}.</li>
 * </ul>
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum TerrainMorphology {

    // ACT 4 morphological parameters (bounded, default values preserve the pre-ACT4 shapes):
    //   crestSharpness  exponent of the mountain crest profile (ridge^crestSharpness); the old
    //                   fixed 1.4 is the default so existing behavior stays representable
    //                   (recommended range 1.2-2.4).
    //   centralPeakMul  strength of the crater/caldera central peak as a share of the feature
    //                   depth (0..~0.6; 0 = no central peak).
    //   rimStrength     crater/caldera rim lift as a share of the feature depth (0.15..0.45).
    //   slopeSteepness  bounded multiplier of regional/feature slope (0.8..1.4, 1.0 = neutral).
    //   erosionDepth    bounded multiplier of the vertical cut of erosion features
    //                   (0.7..1.4, 1.0 = neutral).

    /** Nearly featureless surface. */
    FLAT(0.55, 0.30, 1.00, 0.10, 0.00, 0.00, 0.00, 0.20, 0.00, 0.00,
            1.4, 0.20, 0.25, 1.0, 1.0),
    /** Gentle temperate plains. */
    PLAINS(0.75, 0.55, 1.00, 0.55, 0.10, 0.05, 0.10, 0.30, 0.05, 0.00,
            1.4, 0.25, 0.28, 1.0, 1.0),
    /** Soft rolling hills. */
    ROLLING(0.90, 0.75, 1.00, 0.60, 0.20, 0.10, 0.15, 0.25, 0.10, 0.00,
            1.4, 0.25, 0.28, 1.0, 1.0),
    /** Connected mountain ranges and valleys. */
    MOUNTAINOUS(1.45, 1.35, 1.05, 0.20, 2.60, 0.30, 0.10, 0.20, 0.10, 0.00,
            1.8, 0.30, 0.32, 1.15, 1.05),
    /** Extreme high ranges with sharp peaks. */
    HIGH_MOUNTAINS(1.80, 1.60, 1.15, 0.10, 3.40, 0.25, 0.05, 0.10, 0.05, 0.00,
            2.0, 0.30, 0.30, 1.25, 1.10),
    /** Deep eroded canyon networks. */
    CANYON(1.05, 1.25, 1.10, 0.00, 0.80, 2.60, 0.10, 0.15, 0.05, 0.00,
            1.5, 0.28, 0.30, 1.05, 1.25),
    /** Broken, heavily eroded badlands. */
    BADLANDS(1.10, 1.45, 1.20, 0.00, 0.70, 2.20, 0.15, 0.15, 0.10, 0.00,
            1.5, 0.25, 0.30, 1.10, 1.35),
    /** Wind-formed dune seas. */
    DUNES(0.70, 0.60, 1.60, 0.00, 0.00, 0.10, 0.05, 0.20, 0.60, 0.00,
            1.3, 0.15, 0.22, 0.95, 0.90),
    /** Impact-dominated crater fields. */
    CRATERED(0.95, 1.05, 1.00, 0.00, 0.20, 0.10, 0.10, 0.30, 0.10, 3.20,
            1.5, 0.42, 0.38, 1.05, 1.05),
    /** Volcanic cones, calderas and lava channels. */
    VOLCANIC(1.35, 1.40, 1.10, 0.00, 1.10, 0.30, 0.30, 0.25, 0.60, 0.20,
            1.6, 0.48, 0.40, 1.10, 1.10),
    /** Glacial ridges and smoothed frozen terrain. */
    GLACIAL(1.05, 0.85, 0.95, 0.00, 0.60, 0.15, 0.05, 0.70, 0.05, 0.10,
            1.4, 0.30, 0.30, 0.95, 1.15),
    /** Eroded plateaus and mesas. */
    PLATEAU(1.00, 1.00, 0.80, 0.00, 0.50, 1.40, 0.10, 0.15, 0.05, 0.00,
            1.4, 0.30, 0.30, 1.0, 1.05),
    /** Low basins and dry lakebeds. */
    BASIN(0.80, 0.60, 0.90, 0.00, 0.10, 0.10, 0.20, 0.30, 0.00, 0.00,
            1.4, 0.30, 0.30, 0.95, 1.15),
    /** Sharp ridged terrain (fractured / tectonically shattered). */
    RIDGED(1.30, 1.50, 1.15, 0.00, 2.00, 0.60, 0.20, 0.20, 0.10, 0.30,
            1.9, 0.30, 0.32, 1.20, 1.05);

    public static final TerrainMorphology[] VALUES = values();

    private final double amplitudeMul;
    private final double roughnessMul;
    private final double frequencyMul;

    // ACT 4 morphological shape parameters (bounded; see class comment above).
    private final double crestSharpness;
    private final double centralPeakMul;
    private final double rimStrength;
    private final double slopeSteepness;
    private final double erosionDepth;

    // affinity weights (how strongly a profile feature favours this morphology)
    private final double volcanicImplication;
    private final double tectonicAffinity;
    private final double erosionAffinity;
    private final double aridityAffinity;
    private final double waterAffinity;
    private final double coldAffinity;
    private final double impactAffinity;

    TerrainMorphology(double amplitudeMul, double roughnessMul, double frequencyMul,
                      double tectonicAffinity, double erosionAffinity, double aridityAffinity,
                      double waterAffinity, double coldAffinity, double volcanicImplication,
                      double impactAffinity,
                      double crestSharpness, double centralPeakMul, double rimStrength,
                      double slopeSteepness, double erosionDepth) {
        this.amplitudeMul = amplitudeMul;
        this.roughnessMul = roughnessMul;
        this.frequencyMul = frequencyMul;
        this.crestSharpness = clampRange(crestSharpness, 1.2, 2.4);
        this.centralPeakMul = clampRange(centralPeakMul, 0.0, 0.6);
        this.rimStrength = clampRange(rimStrength, 0.10, 0.45);
        this.slopeSteepness = clampRange(slopeSteepness, 0.80, 1.40);
        this.erosionDepth = clampRange(erosionDepth, 0.70, 1.40);
        this.volcanicImplication = volcanicImplication;
        this.tectonicAffinity = tectonicAffinity;
        this.erosionAffinity = erosionAffinity;
        this.aridityAffinity = aridityAffinity;
        this.waterAffinity = waterAffinity;
        this.coldAffinity = coldAffinity;
        this.impactAffinity = impactAffinity;
    }

    public double amplitudeMul() {
        return amplitudeMul;
    }

    public double roughnessMul() {
        return roughnessMul;
    }

    public double frequencyMul() {
        return frequencyMul;
    }

    /** ACT 4: crest profile exponent {@code ridge^crestSharpness} (1.2-2.4). */
    public double crestSharpness() {
        return crestSharpness;
    }

    /** ACT 4: central crater/caldera peak strength as a share of the feature depth. */
    public double centralPeakMul() {
        return centralPeakMul;
    }

    /** ACT 4: crater/caldera rim lift as a share of the feature depth. */
    public double rimStrength() {
        return rimStrength;
    }

    /** ACT 4: bounded slope multiplier (1.0 = neutral). */
    public double slopeSteepness() {
        return slopeSteepness;
    }

    /** ACT 4: bounded erosion-cut multiplier (1.0 = neutral). */
    public double erosionDepth() {
        return erosionDepth;
    }

    public double tectonicAffinity() {
        return tectonicAffinity;
    }

    public double erosionAffinity() {
        return erosionAffinity;
    }

    public double aridityAffinity() {
        return aridityAffinity;
    }

    public double waterAffinity() {
        return waterAffinity;
    }

    public double coldAffinity() {
        return coldAffinity;
    }

    /** Volcanic worlds favour this morphology (extra weight for {@link #VOLCANIC}). */
    public double volcanicImplication() {
        return volcanicImplication;
    }

    public double impactAffinity() {
        return impactAffinity;
    }

    /** True for morphologies dominated by relief (mountain / ridge families). */
    public boolean isReliefDominated() {
        return tectonicAffinity >= 1.0;
    }

    /** Weighted compatibility score against a physical profile (pure, deterministic). */
    public double score(PlanetPhysicalProfile p) {
        if (p == null) return 0.0;
        double dryness = 1.0 - p.humidity();
        double cold = 1.0 - p.temperature();
        return tectonicAffinity * p.tectonicActivity()
                + erosionAffinity * p.erosion()
                + aridityAffinity * dryness
                + waterAffinity * p.waterAbundance()
                + coldAffinity * cold
                + impactAffinity * p.impactFrequency()
                + volcanicImplication() * p.volcanicActivity();
    }

    private static double clampRange(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
