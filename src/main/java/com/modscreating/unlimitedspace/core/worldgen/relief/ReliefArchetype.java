package com.modscreating.unlimitedspace.core.worldgen.relief;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PLANET RELIEF ARCHETYPE (R21) — how mountainous ONE PLANET is as a whole.
 *
 * <p>Separate from climate: a planet is FLAT or VERY_MOUNTAINOUS as a property of the world,
 * not of a biome. This is what lets one planet be almost endless plains while another is a
 * genuine mountain world with ranges, foothills and valleys.
 *
 * <p>Each constant carries the relief grammar consumed by the hierarchical terrain compositor:
 * <ul>
 *   <li>{@code mountainCoverage} — planet-level share of mountain systems in [0,1];</li>
 *   <li>{@code hillAmplitude} — rolling-hills amplitude multiplier in [0,1] (10–40 blocks
 *       worth of relief at amplitude scale 1.0);</li>
 *   <li>{@code valleyStrength} / {@code rangeWidthMul} — valley carving and belt width;</li>
 *   <li>affinity weights for scoring against the physical profile.</li>
 * </ul>
 *
 * <p>Pure domain: no Minecraft types. Deterministic selection via {@link ReliefArchetypeSelector}.
 */
public enum ReliefArchetype {

    /** Almost endless smooth plains; hills nearly off. */
    FLAT(0.02, 0.10, 0.20, 0.8, 0.05, 0.90, 0.05, 0.30),
    /** Soft, beautiful rolling country. */
    ROLLING(0.05, 0.65, 0.45, 1.0, 0.25, 0.60, 0.10, 0.50),
    /** Clearly hilly regions without real mountain belts. */
    HILLY(0.14, 0.95, 0.60, 1.2, 0.45, 0.35, 0.15, 0.50),
    /** Mountain chains with foothills and inter-range valleys. */
    MOUNTAINOUS(0.42, 0.75, 0.85, 1.6, 0.95, 0.15, 0.20, 0.50),
    /** Dominant mountain world: most of the planet is ranges, basins remain. */
    VERY_MOUNTAINOUS(0.68, 0.70, 1.00, 2.0, 1.00, 0.05, 0.15, 0.35),
    /** Plateau country cut by huge canyons. */
    CANYONLAND(0.28, 0.60, 0.70, 1.2, 0.40, 1.00, 0.15, 0.10),
    /** Eroded mesas and terraced plateaus. */
    PLATEAU(0.24, 0.55, 0.55, 1.0, 0.45, 0.85, 0.30, 0.20),
    /** Volcanic cones and ash fields dominate the silhouette. */
    VOLCANIC(0.32, 0.50, 0.75, 1.3, 0.55, 0.20, 0.10, 0.20),
    /** Rare gigantic craters instead of ranges. */
    CRATERED(0.10, 0.35, 0.35, 0.8, 0.15, 0.35, 1.00, 0.20),
    /** Glacial: smoothed ice valleys and ridge fields. */
    GLACIAL(0.18, 0.45, 0.50, 1.1, 0.30, 0.40, 0.10, 0.90),
    /** Continental depressions: huge basins with gentle rims. */
    BASIN_RICH(0.08, 0.40, 0.35, 0.9, 0.10, 0.70, 0.15, 1.00),
    /** Balanced mixture of all relief families. */
    MIXED(0.26, 0.70, 0.65, 1.3, 0.50, 0.45, 0.30, 0.60);

    public static final ReliefArchetype[] VALUES = values();

    private final double mountainCoverage;
    private final double hillAmplitude;
    private final double valleyStrength;
    private final double rangeWidthMul;
    // affinity weights (how strongly a physical profile favours this relief)
    private final double tectonicAffinity;
    private final double erosionAffinity;
    private final double impactAffinity;
    private final double waterAffinity;

    ReliefArchetype(double mountainCoverage, double hillAmplitude, double valleyStrength,
                    double rangeWidthMul,
                    double tectonicAffinity, double erosionAffinity,
                    double impactAffinity, double waterAffinity) {
        this.mountainCoverage = mountainCoverage;
        this.hillAmplitude = hillAmplitude;
        this.valleyStrength = valleyStrength;
        this.rangeWidthMul = rangeWidthMul;
        this.tectonicAffinity = tectonicAffinity;
        this.erosionAffinity = erosionAffinity;
        this.impactAffinity = impactAffinity;
        this.waterAffinity = waterAffinity;
    }

    public double mountainCoverage() { return mountainCoverage; }
    public double hillAmplitude()    { return hillAmplitude; }
    public double valleyStrength()   { return valleyStrength; }
    public double rangeWidthMul()    { return rangeWidthMul; }

    /** Weighted compatibility score against a physical profile (pure, deterministic). */
    public double score(PlanetPhysicalProfile p) {
        if (p == null) return 0.0;
        return tectonicAffinity * p.tectonicActivity() * 1.4
                + erosionAffinity * p.erosion() * 0.9
                + impactAffinity * p.impactFrequency() * 0.8
                + waterAffinity * p.waterAbundance() * 0.5;
    }

    /** True for relief archetypes with real mountain systems. */
    public boolean isMountainous() {
        return this == MOUNTAINOUS || this == VERY_MOUNTAINOUS;
    }

    /** True for the calm end of the relief spectrum. */
    public boolean isCalm() {
        return this == FLAT || this == ROLLING;
    }
}
