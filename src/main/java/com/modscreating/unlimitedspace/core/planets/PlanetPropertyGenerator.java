package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Stateless, deterministic planet property generator. Every value is a pure
 * function of the planet seed and a fixed slot, so results are stable across
 * runs/JVMs and independent of generation order. Properties are cross-constrained
 * (e.g. a gas giant never gets a rocky terrain profile; a very cold planet never
 * gets an extreme temperature).
 */
public final class PlanetPropertyGenerator {

    private PlanetPropertyGenerator() {}

    /* ---------------- definition (identity + archetype) ---------------- */

    /**
     * Build the canonical definition for a planet slot. The type is chosen
     * deterministically from the seed, so the same seed always yields the same type.
     */
    public static PlanetDefinition define(PlanetSeed seed, StarSystemId systemId, int orbitIndex) {
        PlanetType type = pickType(seed.value());
        return PlanetDefinition.of(seed, systemId, orbitIndex, type);
    }

    /** Full generated data for a definition. */
    public static Planet generate(PlanetDefinition def) {
        return new Planet(def, generateProperties(def));
    }

    /* ---------------- property generation ---------------- */

    public static PlanetProperties generateProperties(PlanetDefinition def) {
        long p = def.seed().value();
        PlanetType type = def.type();

        long terrainSeed    = Seeds.subsystem(p, "terrain");
        long biomeSeed      = Seeds.subsystem(p, "biome");
        long oreSeed        = Seeds.subsystem(p, "ore");
        long structureSeed  = Seeds.subsystem(p, "structures");
        long vegetationSeed = Seeds.subsystem(p, "vegetation");
        long materialSeed   = Seeds.subsystem(p, "materials");

        PlanetSurface surface = surfaceFor(type);

        // temperature within the type-allowed range
        double temperature = Seeds.rangeDouble(p, 1, type.temperatureMinK(), type.temperatureMaxK());

        // humidity, pushed down on very cold worlds
        double humidity = clamp01(Seeds.rangeDouble(p, 2,
                        Math.max(0.0, type.humidityBase() - 0.25),
                        Math.min(1.0, type.humidityBase() + 0.25))
                * (1.0 - 0.3 * coldFactor(temperature)));

        // water coverage: from humidity + type base; gas giants keep 0 solid water
        double water = gasGiant(type)
                ? 0.0
                : clamp01(Seeds.rangeDouble(p, 3,
                        Math.max(0.0, type.waterBase() + (humidity - type.humidityBase()) * 0.5 - 0.3),
                        Math.min(1.0, type.waterBase() + (humidity - type.humidityBase()) * 0.5 + 0.3)));

        // atmosphere & density
        AtmosphereType atmosphere = atmosphereFor(type, temperature, water);
        double density = clamp01(Seeds.rangeDouble(p, 4,
                Math.max(0.0, atmosphere.densityBase() - 0.2),
                Math.min(1.0, atmosphere.densityBase() + 0.2)));

        // radius & gravity (independent, within type ranges)
        double radius = Seeds.rangeDouble(p, 5, type.radiusMin(), type.radiusMax());
        // Gravity is in Earth-g; floor it at the playable minimum so no surface world can
        // ever generate a zero / unusably-small gravity (R12.2 Bug #1). Orbit gravity is
        // governed separately and stays at the CS-intended zero.
        double gravity = Gravity.playableEarthG(
                Seeds.rangeDouble(p, 6, type.gravityMin(), type.gravityMax()));

        // terrain roughness/erosion: none for gas giants
        double roughness = gasGiant(type)
                ? 0.0
                : Seeds.rangeDouble(p, 7, type.roughnessMin(), type.roughnessMax());
        double erosion = gasGiant(type) ? 0.0 : Seeds.rangeDouble(p, 8, 0.0, 1.0);

        // life & vegetation depend on habitability
        double lifeFactor = habitability(temperature, water, humidity, type);
        double lifeLevel = clamp01(lifeFactor * Seeds.rangeDouble(p, 9, 0.3, 1.0));
        double vegetation = clamp01((type == PlanetType.OCEAN ? 0.4 : 0.0)
                + lifeLevel * type.vegetationFactor() * Seeds.rangeDouble(p, 10, 0.2, 1.0));

        // geological activity around type base
        double geo = clamp01(Seeds.rangeDouble(p, 11,
                Math.max(0.0, type.geologicalActivityBase() - 0.2),
                Math.min(1.0, type.geologicalActivityBase() + 0.2)));

        // resource profile (mineral richness correlates with tectonic activity)
        double mineral = clamp01(0.1 + 0.9 * geo * Seeds.rangeDouble(p, 12, 0.2, 1.0));
        boolean rare = Seeds.fraction(p, 13) < 0.15 + 0.4 * geo;
        double fuel = clamp01(Seeds.rangeDouble(p, 14, 0.0, 1.0));
        PlanetProperties.ResourceProfile resources =
                PlanetProperties.ResourceProfile.of(mineral, rare, fuel);

        PlanetProperties.BiomeParameters biomeParams =
                new PlanetProperties.BiomeParameters(
                        Seeds.rangeDouble(p, 15, 0.5, 2.0),
                        Seeds.rangeDouble(p, 16, 0.5, 2.0));

        PlanetProperties.GenerationParameters genParams =
                new PlanetProperties.GenerationParameters(
                        Seeds.rangeDouble(p, 17, 0.0, 0.2),
                        Seeds.rangeDouble(p, 18, -1.0, 1.0),
                        Seeds.rangeDouble(p, 19, 0.5, 2.0));

        return new PlanetProperties(
                def.seed(), type, surface,
                radius, gravity, temperature, humidity,
                atmosphere, density, water,
                roughness, erosion, vegetation, lifeLevel, geo,
                resources, biomeParams, genParams,
                terrainSeed, biomeSeed, oreSeed, structureSeed, vegetationSeed, materialSeed);
    }

    /* ---------------- helpers ---------------- */

    /** Deterministic archetype selection weighted by {@link PlanetType#occurrenceWeight()}. */
    public static PlanetType pickType(long seed) {
        double f = Seeds.fraction(seed, 0);
        double acc = 0.0;
        PlanetType[] types = PlanetType.values();
        for (PlanetType type : types) {
            acc += type.occurrenceWeight();
            if (f < acc) return type;
        }
        return types[types.length - 1];
    }

    private static PlanetSurface surfaceFor(PlanetType type) {
        return switch (type) {
            case ICE -> PlanetSurface.SOLID_ICE;
            case DESERT -> PlanetSurface.SOLID_DESERT;
            case VOLCANIC -> PlanetSurface.SOLID_VOLCANIC;
            case OCEAN -> PlanetSurface.OCEANIC;
            case GAS_GIANT -> PlanetSurface.GASEOUS;
            case ROCKY, FOREST, BARREN -> PlanetSurface.SOLID_ROCKY;
        };
    }

    private static boolean gasGiant(PlanetType type) {
        return type == PlanetType.GAS_GIANT;
    }

    private static AtmosphereType atmosphereFor(PlanetType type, double temperature, double water) {
        if (type == PlanetType.GAS_GIANT) return AtmosphereType.GASEOUS;
        if (type == PlanetType.OCEAN && water > 0.4) return AtmosphereType.MODERATE;
        if (water > 0.5) return AtmosphereType.DENSE;
        if (temperature < 180.0) return AtmosphereType.THIN;
        if (type == PlanetType.VOLCANIC) return AtmosphereType.CORROSIVE;
        return AtmosphereType.TRACE;
    }

    private static double coldFactor(double temperature) {
        if (temperature <= 200.0) return 1.0;
        if (temperature >= 280.0) return 0.0;
        return (280.0 - temperature) / 80.0;
    }

    private static double habitability(double temperature, double water, double humidity, PlanetType type) {
        if (type == PlanetType.GAS_GIANT || type == PlanetType.VOLCANIC) return 0.0;
        if (water < 0.05 || humidity < 0.05) return 0.0;
        if (temperature < 230.0 || temperature > 350.0) return 0.0;
        // smooth band around a comfortable range
        double tScore = 1.0 - Math.min(1.0, Math.abs(temperature - 290.0) / 80.0);
        double wScore = Math.min(1.0, water / 0.3);
        return clamp01(tScore * wScore);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}

