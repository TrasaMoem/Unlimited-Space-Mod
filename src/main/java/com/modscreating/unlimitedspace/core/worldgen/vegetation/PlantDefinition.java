package com.modscreating.unlimitedspace.core.worldgen.vegetation;

import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;

/**
 * Phase 9: a single reusable vegetation definition. Pure data — it references an
 * existing Minecraft block id (registry key, not a display name) plus the biome
 * archetype it belongs to. No Minecraft imports here; the adapter resolves the
 * block id to a {@code BlockState} at worldgen time.
 *
 * @param id      stable semantic id
 * @param blockId Minecraft block registry id to place as the plant
 * @param biome   biome archetype this plant belongs to
 */
public record PlantDefinition(String id, String blockId, PlanetBiome biome) {

    public static PlantDefinition of(String id, String blockId, PlanetBiome biome) {
        return new PlantDefinition(id, blockId, biome);
    }
}