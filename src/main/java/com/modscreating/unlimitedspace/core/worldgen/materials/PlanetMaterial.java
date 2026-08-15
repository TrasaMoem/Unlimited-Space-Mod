package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * A single planet material: a stable semantic id plus the Minecraft block id
 * string it maps to. The block id is a registry key, NOT a display name — the
 * Minecraft-side mapper later resolves it (still pure-data, no Minecraft imports
 * here).
 */
public record PlanetMaterial(String id, MaterialFamily family, String blockId) {

    public static PlanetMaterial of(String id, MaterialFamily family, String blockId) {
        return new PlanetMaterial(id, family, blockId);
    }
}