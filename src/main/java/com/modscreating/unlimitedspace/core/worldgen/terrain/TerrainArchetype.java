package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * Planet Terrain Archetype (R20 hierarchical-terrain stage).
 *
 * <pre>
 * PLANET IDENTITY -&gt; PHYSICAL PROFILE -&gt; TERRAIN ARCHETYPE -&gt; GLOBAL -&gt; MACRO -&gt; REGIONAL -&gt; LOCAL
 * </pre>
 *
 * <p>An archetype is a <b>terrain grammar</b>: a bundle of large-scale shaping biases a planet
 * inherits from its physical profile (never hardcoded per planet id). The grammar drives the
 * hierarchical terrain composer — which part of the planet is land, where mountain systems may
 * form, how strong erosion / valleys / basins / craters / volcanism are, and how much tiny
 * detail is allowed.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum TerrainArchetype {

    /** Balanced land/ocean mix with plains and gentle inland relief. */
    CONTINENTAL(0.58, 0.45, 0.50, 0.45, 0.40, 0.60, 0.70, 0.20, 0.10,
            0.8, 0.4, 0.4, 0.6, 0.3, 0.4),
    /** Many islands / fragmented land. */
    ARCHIPELAGO(0.38, 0.50, 0.45, 0.40, 0.30, 0.70, 0.80, 0.10, 0.12,
            0.9, 0.9, 0.4, 0.3, 0.3, 0.5),
    /** Dominated by mountain chains, foothills and valleys. */
    MOUNTAIN_WORLD(0.55, 0.95, 0.40, 0.75, 0.30, 0.40, 0.60, 0.20, 0.12,
            1.0, 0.7, 0.2, 0.3, 0.2, 0.5),
    /** Volcanic cones, calderas, lava channels and ash plains. */
    VOLCANIC_WORLD(0.55, 0.70, 0.45, 0.40, 0.45, 0.40, 1.60, 0.25, 0.14,
            0.7, 0.5, 0.9, 0.3, 0.4, 0.4),
    /** Impact-dominated: large craters, ejecta zones, basins. */
    CRATER_WORLD(0.45, 0.30, 0.55, 0.25, 0.60, 2.20, 0.40, 0.20, 0.12,
            0.8, 0.5, 0.1, 0.3, 0.3, 0.3),
    /** Dry dune seas, mesas and canyon country. */
    DESERT_WORLD(0.55, 0.35, 0.75, 0.35, 0.45, 0.30, 0.50, 0.55, 0.10,
            0.9, 0.9, 0.1, 0.6, 0.2, 0.4),
    /** Frozen rolling terrain, glacial valleys, ice plains. */
    GLACIAL_WORLD(0.52, 0.35, 0.40, 0.55, 0.50, 0.35, 0.45, 0.15, 0.08,
            0.8, 0.4, 0.2, 0.3, 0.2, 1.6),
    /** Ocean-dominated with shelves and island chains. */
    OCEAN_WORLD(0.18, 0.35, 0.40, 0.45, 0.60, 0.50, 0.50, 0.10, 0.10,
            1.4, 1.0, 0.2, 0.2, 0.2, 0.4),
    /** Heavily eroded badlands: wide valleys + canyon networks. */
    BADLANDS_WORLD(0.55, 0.45, 0.95, 0.85, 0.40, 0.35, 0.50, 0.45, 0.13,
            0.9, 0.8, 0.1, 0.7, 0.3, 0.4),
    /** Vast smooth plains; the calm archetype. */
    PLAINS_WORLD(0.58, 0.15, 0.50, 0.20, 0.30, 0.30, 0.35, 0.10, 0.07,
            0.7, 0.3, 0.1, 0.4, 0.3, 0.4),
    /** Fractured, tectonically shattered ridged terrain. */
    RIDGED_WORLD(0.55, 0.90, 0.35, 0.45, 0.30, 0.60, 0.60, 0.30, 0.14,
            1.0, 0.8, 0.3, 0.3, 0.2, 0.5),
    /** Continental depressions: descending slopes into large basins. */
    BASIN_WORLD(0.60, 0.20, 0.55, 0.35, 0.90, 0.40, 0.40, 0.20, 0.08,
            0.8, 0.5, 0.1, 0.5, 0.9, 0.3),
    /** Exotic: crystal fields, spires, unusual macro shapes. */
    STRANGE_WORLD(0.50, 0.50, 0.60, 0.50, 0.50, 0.70, 0.70, 0.40, 0.15,
            0.8, 0.5, 0.5, 0.3, 0.5, 0.4);

    public static final TerrainArchetype[] VALUES = values();

    // ------------------------------------------------------------------ grammar
    private final double continentalBias;
    private final double mountainStrength;
    private final double erosionBias;
    private final double valleyStrength;
    private final double basinStrength;
    private final double craterFrequencyMul;
    private final double volcanicStrengthMul;
    private final double plateauTendency;
    private final double localDetailFactor;

    // affinities (how strongly a physical profile favours this archetype)
    private final double waterAffinity;
    private final double aridityAffinity;
    private final double volcanicAffinity;
    private final double erosionAffinity;
    private final double basinAffinity;
    private final double coldAffinity;

    TerrainArchetype(double continentalBias, double mountainStrength, double erosionBias,
                     double valleyStrength, double basinStrength, double craterFrequencyMul,
                     double volcanicStrengthMul, double plateauTendency, double localDetailFactor,
                     double waterAffinity, double aridityAffinity, double volcanicAffinity,
                     double erosionAffinity, double basinAffinity, double coldAffinity) {
        this.continentalBias = continentalBias;
        this.mountainStrength = mountainStrength;
        this.erosionBias = erosionBias;
        this.valleyStrength = valleyStrength;
        this.basinStrength = basinStrength;
        this.craterFrequencyMul = craterFrequencyMul;
        this.volcanicStrengthMul = volcanicStrengthMul;
        this.plateauTendency = plateauTendency;
        this.localDetailFactor = localDetailFactor;
        this.waterAffinity = waterAffinity;
        this.aridityAffinity = aridityAffinity;
        this.volcanicAffinity = volcanicAffinity;
        this.erosionAffinity = erosionAffinity;
        this.basinAffinity = basinAffinity;
        this.coldAffinity = coldAffinity;
    }

    public double continentalBias()    { return continentalBias; }
    public double mountainStrength()   { return mountainStrength; }
    public double erosionBias()        { return erosionBias; }
    public double valleyStrength()     { return valleyStrength; }
    public double basinStrength()      { return basinStrength; }
    public double craterFrequencyMul() { return craterFrequencyMul; }
    public double volcanicStrengthMul(){ return volcanicStrengthMul; }
    public double plateauTendency()    { return plateauTendency; }
    public double localDetailFactor()  { return localDetailFactor; }
    public double waterAffinity()      { return waterAffinity; }
    public double aridityAffinity()    { return aridityAffinity; }
    public double volcanicAffinity()   { return volcanicAffinity; }
    public double erosionAffinity()    { return erosionAffinity; }
    public double basinAffinity()      { return basinAffinity; }
    public double coldAffinity()       { return coldAffinity; }

    /** Weighted compatibility score against a physical profile (pure, deterministic). */
    public double score(com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile p) {
        if (p == null) return 0.0;
        double dryness = 1.0 - p.humidity();
        double cold = 1.0 - p.temperature();
        return waterAffinity * (p.waterAbundance() * 0.6 + p.oceanCoverage() * 0.8)
                + aridityAffinity * dryness
                + volcanicAffinity * p.volcanicActivity()
                + erosionAffinity * p.erosion()
                + basinAffinity * (1.0 - p.tectonicActivity())
                + coldAffinity * cold
                + craterFrequencyMul * 0.4 * p.impactFrequency()
                + mountainStrength * p.tectonicActivity() * 0.6;
    }
}

