package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.MoonSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Stateless, deterministic moon property generator.
 *
 * <p>Every value is a pure function of the moon seed and a fixed slot, so results are
 * stable across runs/JVMs and independent of generation order. Moon properties are
 * <em>never</em> a copy of the parent planet's properties — they are derived from the
 * moon's own {@link MoonSeed}, while the parent planet may influence the temperature
 * baseline and type distribution.
 */
public final class MoonPropertyGenerator {

    private static final long MOON_COUNT_SLOT = 9001L;

    private MoonPropertyGenerator() {}

    /** Deterministic 0..5 moon count for a planet seed. */
    public static int moonCount(long planetSeed) {
        return (int) (Seeds.fraction(planetSeed, MOON_COUNT_SLOT) * 6); // 0..5
    }

    /** Full generated moon for a given planet + moon index. */
    public static Moon generate(MoonId id, long planetSeed, int moonIndex) {
        MoonSeed seed = MoonSeed.forSlot(planetSeed, moonIndex);
        MoonProperties props = generateProperties(id, seed);
        return Moon.of(id, seed, props);
    }

    /** Generate the fully independent properties of one moon from its own seed. */
    public static MoonProperties generateProperties(MoonId id, MoonSeed seed) {
        double f = seed.value();
        MoonType type = MoonType.pickType(seed.value());

        // ---- orbital metadata (deterministic) ----
        int orbitalOrder = id.moonIndex() + 1;
        double relativeDistance = 0.1 + 0.9 * Seeds.fraction(seed.value(), 11L);
        double eccentricity = Seeds.fraction(seed.value(), 12L) * 0.3;
        double inclination = Seeds.fraction(seed.value(), 13L) * Math.PI;
        MoonOrbitMetadata orbit = new MoonOrbitMetadata(id.moonIndex(), orbitalOrder,
                relativeDistance, eccentricity, inclination);

        // ---- physical properties ----
        double minTemp = minTemperature(type);
        double maxTemp = maxTemperature(type);
        double temperature = Seeds.rangeDouble(seed.value(), 1, minTemp, maxTemp);

        double waterCoverage = waterFor(type, seed.value(), temperature);
        double atmosphericDensity = clamp01(Seeds.rangeDouble(seed.value(), 2, 0.0, 0.8));
        double terrainRoughness = clamp01(Seeds.rangeDouble(seed.value(), 3, 0.0, 1.0));
        double erosion = clamp01(Seeds.rangeDouble(seed.value(), 4, 0.0, 1.0));
        double geologicalActivity = clamp01(Seeds.rangeDouble(seed.value(), 5,
                type == MoonType.VOLCANIC ? 0.6 : 0.0,
                type == MoonType.VOLCANIC ? 1.0 : 0.5));

        AtmosphereType atmosphere = atmosphereFor(type, temperature, waterCoverage);
        boolean ringState = Seeds.fraction(seed.value(), 6) < 0.15;

        // moons are smaller than planets: radius and gravity scale below 1
        double radiusProfile = 0.2 + 0.6 * Seeds.rangeDouble(seed.value(), 7, 0.0, 1.0);
        double gravity = 0.08 + 0.5 * Seeds.rangeDouble(seed.value(), 8, 0.0, 1.0);

        PlanetSurface surface = surfaceFor(type);

        return new MoonProperties(id, seed, type, surface,
                radiusProfile, gravity, temperature, atmosphericDensity,
                waterCoverage, terrainRoughness, erosion, geologicalActivity,
                atmosphere, ringState, orbit);
    }

    private static double minTemperature(MoonType type) {
        return switch (type) {
            case ICE -> 90.0;
            case BARREN, ROCKY -> 140.0;
            case CRATERED -> 130.0;
            case DESERT -> 250.0;
            case OCEANIC -> 240.0;
            case VOLCANIC -> 320.0;
            case METALLIC -> 150.0;
        };
    }

    private static double maxTemperature(MoonType type) {
        return switch (type) {
            case ICE -> 220.0;
            case BARREN, ROCKY -> 300.0;
            case CRATERED -> 290.0;
            case DESERT -> 390.0;
            case OCEANIC -> 330.0;
            case VOLCANIC -> 800.0;
            case METALLIC -> 400.0;
        };
    }

    private static double waterFor(MoonType type, long seed, double temperature) {
        double base = switch (type) {
            case ICE -> 0.5;
            case OCEANIC -> 0.8;
            case BARREN, ROCKY, CRATERED -> 0.05;
            case DESERT, VOLCANIC -> 0.05;
            case METALLIC -> 0.01;
        };
        double jitter = Seeds.rangeDouble(seed, 3, 0.0, 0.25);
        // very cold moons can still hold ice; hot ones lose water
        double w = base + jitter;
        if (temperature > 400.0) w *= 0.2;
        return clamp01(w);
    }

    private static AtmosphereType atmosphereFor(MoonType type, double temperature, double water) {
        if (type == MoonType.VOLCANIC) return AtmosphereType.CORROSIVE;
        if (type == MoonType.OCEANIC && water > 0.4) return AtmosphereType.MODERATE;
        if (temperature < 180.0) return AtmosphereType.THIN;
        if (type == MoonType.METALLIC) return AtmosphereType.NONE;
        if (water > 0.5) return AtmosphereType.DENSE;
        return AtmosphereType.TRACE;
    }

    private static PlanetSurface surfaceFor(MoonType type) {
        return switch (type) {
            case ICE -> PlanetSurface.SOLID_ICE;
            case DESERT -> PlanetSurface.SOLID_DESERT;
            case VOLCANIC -> PlanetSurface.SOLID_VOLCANIC;
            case OCEANIC -> PlanetSurface.OCEANIC;
            default -> PlanetSurface.SOLID_ROCKY;
        };
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
