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

    /** Nearly featureless surface. */
    FLAT(0.55, 0.30, 1.00, 0.10, 0.00, 0.00, 0.00, 0.20, 0.00, 0.00),
    /** Gentle temperate plains. */
    PLAINS(0.75, 0.55, 1.00, 0.55, 0.10, 0.05, 0.10, 0.30, 0.05, 0.00),
    /** Soft rolling hills. */
    ROLLING(0.90, 0.75, 1.00, 0.60, 0.20, 0.10, 0.15, 0.25, 0.10, 0.00),
    /** Connected mountain ranges and valleys. */
    MOUNTAINOUS(1.45, 1.35, 1.05, 0.20, 2.60, 0.30, 0.10, 0.20, 0.10, 0.00),
    /** Extreme high ranges with sharp peaks. */
    HIGH_MOUNTAINS(1.80, 1.60, 1.15, 0.10, 3.40, 0.25, 0.05, 0.10, 0.05, 0.00),
    /** Deep eroded canyon networks. */
    CANYON(1.05, 1.25, 1.10, 0.00, 0.80, 2.60, 0.10, 0.15, 0.05, 0.00),
    /** Broken, heavily eroded badlands. */
    BADLANDS(1.10, 1.45, 1.20, 0.00, 0.70, 2.20, 0.15, 0.15, 0.10, 0.00),
    /** Wind-formed dune seas. */
    DUNES(0.70, 0.60, 1.60, 0.00, 0.00, 0.10, 0.05, 0.20, 0.60, 0.00),
    /** Impact-dominated crater fields. */
    CRATERED(0.95, 1.05, 1.00, 0.00, 0.20, 0.10, 0.10, 0.30, 0.10, 3.20),
    /** Volcanic cones, calderas and lava channels. */
    VOLCANIC(1.35, 1.40, 1.10, 0.00, 1.10, 0.30, 0.30, 0.25, 0.60, 0.20),
    /** Glacial ridges and smoothed frozen terrain. */
    GLACIAL(1.05, 0.85, 0.95, 0.00, 0.60, 0.15, 0.05, 0.70, 0.05, 0.10),
    /** Eroded plateaus and mesas. */
    PLATEAU(1.00, 1.00, 0.80, 0.00, 0.50, 1.40, 0.10, 0.15, 0.05, 0.00),
    /** Low basins and dry lakebeds. */
    BASIN(0.80, 0.60, 0.90, 0.00, 0.10, 0.10, 0.20, 0.30, 0.00, 0.00),
    /** Sharp ridged terrain (fractured / tectonically shattered). */
    RIDGED(1.30, 1.50, 1.15, 0.00, 2.00, 0.60, 0.20, 0.20, 0.10, 0.30);

    public static final TerrainMorphology[] VALUES = values();

    private final double amplitudeMul;
    private final double roughnessMul;
    private final double frequencyMul;

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
                      double impactAffinity) {
        this.amplitudeMul = amplitudeMul;
        this.roughnessMul = roughnessMul;
        this.frequencyMul = frequencyMul;
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
}
