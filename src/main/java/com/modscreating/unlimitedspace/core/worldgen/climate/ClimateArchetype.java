package com.modscreating.unlimitedspace.core.worldgen.climate;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PLANETARY CLIMATE ARCHETYPE (R21 planetary-geography stage).
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; CLIMATE ARCHETYPE -&gt; PlanetClimateField -&gt; BIOME REGIONS
 * </pre>
 *
 * <p>This is NOT a Minecraft biome: it is the planet's core climatic identity. Every later
 * subsystem (biome regions, surface categories, color theme, fluids, vegetation) derives its
 * global character from this archetype. Selection is a pure function of the physical profile
 * via {@link ClimateArchetypeSelector}.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum ClimateArchetype {

    /** End-to-end cryogenic world: ice crusts, frozen plains, sublimating atmosphere. */
    FROZEN(0.06, 0.45, 0.20, ClimatePattern.DUAL_POLE, 0.0, 0.9, 0.4, 0.2),
    /** Cold but not lethal: tundra-like baselines with cold seas. */
    COLD(0.20, 0.50, 0.30, ClimatePattern.BANDS, 0.1, 0.8, 0.3, 0.3),
    /** Balanced temperate world — the "earthlike" archetype. */
    TEMPERATE(0.48, 0.55, 0.35, ClimatePattern.BANDS, 0.5, 0.4, 0.4, 0.5),
    /** Dry hot world: dust, regolith, evaporites, almost no surface liquid. */
    ARID(0.62, 0.15, 0.35, ClimatePattern.PATCHY, 0.4, 0.2, 0.9, 0.6),
    /** Hot world with residual humidity: hot seas / brines / oxidized rock. */
    HOT(0.74, 0.35, 0.30, ClimatePattern.ONE_SIDED, 0.3, 0.3, 0.7, 0.7),
    /** Wet and warm: dense humidity, storms, organic-friendly baselines. */
    TROPICAL(0.68, 0.80, 0.30, ClimatePattern.EQUATORIAL, 0.3, 0.2, 0.3, 0.8),
    /** Runaway greenhouse: molten-adjacent surface conditions. */
    EXTREME_HOT(0.92, 0.10, 0.18, ClimatePattern.ONE_SIDED, 0.1, 0.1, 1.0, 0.4),
    /** Deep-freeze exoplanet: barely above the sublimation floor. */
    EXTREME_COLD(0.02, 0.20, 0.12, ClimatePattern.DUAL_POLE, 0.0, 1.0, 0.5, 0.1),
    /** Strong regional swings: cold sectors and warm sectors on the same world. */
    VARIABLE(0.45, 0.45, 0.75, ClimatePattern.PATCHY, 0.5, 0.5, 0.5, 0.5),
    /** Violent atmosphere: storms dominate, humidity pulses across regions. */
    STORMY(0.50, 0.75, 0.60, ClimatePattern.EQUATORIAL, 0.4, 0.3, 0.4, 0.7),
    /** Almost no volatiles: dust world with hard-vacuum chemistry. */
    HYPERARID(0.55, 0.03, 0.15, ClimatePattern.PATCHY, 0.3, 0.1, 1.0, 0.5),
    /** Ocean-dominated: water world with narrow island climate zones. */
    OCEANIC(0.45, 0.90, 0.25, ClimatePattern.EQUATORIAL, 0.5, 0.3, 0.2, 0.9);

    public static final ClimateArchetype[] VALUES = values();

    /** The shape of the planetary temperature/humidity distribution. */
    public enum ClimatePattern {
        /** Warped latitude-like belts (cold poles, warm equator). */
        BANDS,
        /** One dominant thermal hemisphere (a single-sided gradient). */
        ONE_SIDED,
        /** Two cold poles with a warm middle (or inverted). */
        DUAL_POLE,
        /** Warm equatorial band with cooler flanks. */
        EQUATORIAL,
        /** Coherent but irregular low-frequency patches (no belts at all). */
        PATCHY
    }

    private final double baseTemperature;
    private final double baseHumidity;
    private final double regionalVariance;
    private final ClimatePattern pattern;
    private final double temperateAffinity;
    private final double coldAffinity;
    private final double aridAffinity;
    private final double waterAffinity;

    ClimateArchetype(double baseTemperature, double baseHumidity, double regionalVariance,
                     ClimatePattern pattern,
                     double temperateAffinity, double coldAffinity,
                     double aridAffinity, double waterAffinity) {
        this.baseTemperature = baseTemperature;
        this.baseHumidity = baseHumidity;
        this.regionalVariance = regionalVariance;
        this.pattern = pattern;
        this.temperateAffinity = temperateAffinity;
        this.coldAffinity = coldAffinity;
        this.aridAffinity = aridAffinity;
        this.waterAffinity = waterAffinity;
    }

    public double baseTemperature() { return baseTemperature; }
    public double baseHumidity()    { return baseHumidity; }
    public double regionalVariance(){ return regionalVariance; }
    public ClimatePattern pattern() { return pattern; }

    /** Weighted compatibility score against a physical profile (pure, deterministic). */
    public double score(PlanetPhysicalProfile p) {
        if (p == null) return 0.0;
        double tempDiff = 1.0 - Math.min(1.0, Math.abs(p.temperature() - baseTemperature) * 2.4);
        double humDiff = 1.0 - Math.min(1.0, Math.abs(p.humidity() - baseHumidity) * 2.4);
        return Math.max(0.0, tempDiff) * 2.2
                + Math.max(0.0, humDiff) * 1.6
                + temperateAffinity * 0.5
                + coldAffinity * (1.0 - p.temperature()) * 0.6
                + aridAffinity * (1.0 - p.humidity()) * 0.6
                + waterAffinity * p.waterAbundance() * 0.8;
    }

    /** True when the archetype's baseline is hostile to liquid water. */
    public boolean isDryDominant() {
        return this == ARID || this == HYPERARID || this == EXTREME_HOT;
    }

    /** True when the archetype's baseline is cryogenic. */
    public boolean isFrozenDominant() {
        return this == FROZEN || this == EXTREME_COLD;
    }

    /** Short debug label, e.g. {@code FROZEN(DUAL_POLE)}. */
    public String label() {
        return name() + "(" + pattern + ")";
    }
}
