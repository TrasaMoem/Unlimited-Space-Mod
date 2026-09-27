package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.PlanetSeed;

/**
 * Fully generated, immutable properties of a planet. All values depend
 * deterministically on the planet seed (and its derived subsystem seeds), with
 * sensible cross-constraints. Designed so future worldgen can consume these values
 * (and the independent subsystem seeds) for climate, terrain, biomes, ores,
 * vegetation and structures.
 *
 * @param seed                 the provoking planet seed
 * @param type                 planet archetype
 * @param surface              semantic surface category
 * @param radiusProfile        radius relative to a reference world
 * @param gravity              surface gravity in Earth g
 * @param temperature          temperature in Kelvin (within type range)
 * @param humidity             humidity in [0,1]
 * @param atmosphere           atmosphere archetype
 * @param atmosphericDensity   relative density in [0,1]
 * @param waterCoverage        surface water fraction in [0,1]
 * @param terrainRoughness     relief amplitude in [0,1]
 * @param erosion              erosion factor in [0,1]
 * @param vegetationDensity    vegetation density in [0,1]
 * @param lifeLevel            life abundance in [0,1]
 * @param geologicalActivity   tectonic/volcanic activity in [0,1]
 * @param resources            resource profile
 * @param biomeParameters      placeholder biome noise parameters
 * @param generationParameters placeholder terrain generation parameters
 * @param terrainSeed          derived subsystem seed
 * @param biomeSeed            derived subsystem seed
 * @param oreSeed              derived subsystem seed
 * @param structureSeed        derived subsystem seed
 * @param vegetationSeed       derived subsystem seed
 * @param materialSeed         derived subsystem seed
 */
public record PlanetProperties(
        PlanetSeed seed,
        PlanetType type,
        PlanetSurface surface,
        double radiusProfile,
        double gravity,
        double temperature,
        double humidity,
        AtmosphereType atmosphere,
        double atmosphericDensity,
        double waterCoverage,
        double terrainRoughness,
        double erosion,
        double vegetationDensity,
        double lifeLevel,
        double geologicalActivity,
        ResourceProfile resources,
        BiomeParameters biomeParameters,
        GenerationParameters generationParameters,
        long terrainSeed,
        long biomeSeed,
        long oreSeed,
        long structureSeed,
        long vegetationSeed,
        long materialSeed,
        PlanetThermal thermal) {

    /**
     * PHASE 1 back-compat constructor: legacy call sites (and worldgen-only unit tests) that
     * build properties without a stellar context get {@link PlanetThermal#none(double)}
     * derived from their own temperature, so {@code thermal()} is never null.
     */
    public PlanetProperties(
            PlanetSeed seed,
            PlanetType type,
            PlanetSurface surface,
            double radiusProfile,
            double gravity,
            double temperature,
            double humidity,
            AtmosphereType atmosphere,
            double atmosphericDensity,
            double waterCoverage,
            double terrainRoughness,
            double erosion,
            double vegetationDensity,
            double lifeLevel,
            double geologicalActivity,
            ResourceProfile resources,
            BiomeParameters biomeParameters,
            GenerationParameters generationParameters,
            long terrainSeed,
            long biomeSeed,
            long oreSeed,
            long structureSeed,
            long vegetationSeed,
            long materialSeed) {
        this(seed, type, surface, radiusProfile, gravity, temperature, humidity, atmosphere,
                atmosphericDensity, waterCoverage, terrainRoughness, erosion, vegetationDensity,
                lifeLevel, geologicalActivity, resources, biomeParameters, generationParameters,
                terrainSeed, biomeSeed, oreSeed, structureSeed, vegetationSeed, materialSeed,
                PlanetThermal.none(temperature));
    }

    /** PHASE 1: the derived stellar environment of this planet (never null). */
    public PlanetThermal thermal() {
        return thermal == null ? PlanetThermal.none(temperature) : thermal;
    }

    /** Convenience: is this planet a gas giant (no surface terrain)? */
    public boolean isGasGiant() {
        return surface == PlanetSurface.GASEOUS;
    }

    /**
     * ACT 2 — the canonical PHYSICAL habitability verdict (concept B: Earth-like physics,
 * regardless of the system pattern). This is a pure delegate to
 * {@code HabitabilityValidator} — the thresholds live in ONE place.
     *
     * <p>NOT the final world state: a physically habitable planet that the system pattern did
     * NOT select hosts NO life, vegetation, mobs or structures. The system-aware verdict
     * (concept C) is {@code SystemHabitability.Result.isActuallyHabitable(orbitIndex)}.
     */
    public boolean isHabitable() {
        return com.modscreating.unlimitedspace.core.habitability.HabitabilityValidator
                .isPhysicallyHabitable(
                        com.modscreating.unlimitedspace.core.habitability.HabitabilityProfile
                                .ofPlanet(this));
    }

    /**
     * Resource richness metadata. A pure-data record; future ore/material profile
     * generation will derive specifics from {@link PlanetProperties#oreSeed()}.
     */
    public record ResourceProfile(double mineralRichness, boolean rareMaterials, double fuelAbundance) {
        public static ResourceProfile of(double mineralRichness, boolean rareMaterials, double fuelAbundance) {
            return new ResourceProfile(mineralRichness, rareMaterials, fuelAbundance);
        }
    }

    /**
     * Placeholder biome noise parameters; later consumed by a BiomeSource.
     */
    public record BiomeParameters(double temperatureNoiseScale, double humidityNoiseScale) {
    }

    /**
     * Placeholder terrain generation parameters; later consumed by a ChunkGenerator.
     */
    public record GenerationParameters(double seaLevelOffset, double baseHeight, double terrainFrequency) {
    }
}
