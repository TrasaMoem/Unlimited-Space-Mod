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
        long materialSeed) {

    /** Convenience: is this planet a gas giant (no surface terrain)? */
    public boolean isGasGiant() {
        return surface == PlanetSurface.GASEOUS;
    }

    /** Convenience: does this planet plausibly support surface life? */
    public boolean isHabitable() {
        return !isGasGiant()
                && temperature >= 240.0 && temperature <= 350.0
                && waterCoverage > 0.1 && atmosphericDensity > 0.15;
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
