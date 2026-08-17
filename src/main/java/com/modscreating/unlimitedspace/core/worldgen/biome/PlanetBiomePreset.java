package com.modscreating.unlimitedspace.core.worldgen.biome;

/**
 * Immutable, Minecraft-free biome preset descriptor (R8).
 *
 * <p>Each {@link PlanetBiome} maps to one preset carrying the metadata used by
 * the deterministic {@link PlanetBiomeProfile} selector (climate windows,
 * preferred terrain, compatible material families, vegetation tendency).
 *
 * <p>This record is the reusable data carrier; it never imports Minecraft
 * types and is stable across restarts/JVMs because it is derived from
 * {@code PlanetSeed}.
 */
public record PlanetBiomePreset(
        PlanetBiome biome,
        int minTemperature,
        int maxTemperature,
        double minHumidity,
        double maxHumidity,
        boolean prefersWater,
        double vegetationTendency,
        PlanetTerrainAffinity terrain
) {
    public PlanetBiomePreset(PlanetBiome biome) {
        this(biome,
                biome.minTemperature(),
                biome.maxTemperature(),
                biome.minHumidity(),
                biome.maxHumidity(),
                biome.prefersWater(),
                0.5,
                PlanetTerrainAffinity.ANY);
    }

    public boolean climateMatches(double planetTemp, double planetHumidity, boolean hasWater) {
        return biome.climateMatches(planetTemp, planetHumidity, hasWater);
    }
}