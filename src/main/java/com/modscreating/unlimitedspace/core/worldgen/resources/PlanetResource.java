package com.modscreating.unlimitedspace.core.worldgen.resources;

/**
 * Data-driven resource definition (Phase 8). Identified by a stable id and a target
 * block key (registry id, not a display name). Distribution parameters feed a later
 * Minecraft {@code OreConfiguration}-style placement; here they are pure data.
 */
public record PlanetResource(
        String id,
        String targetBlock,
        boolean rare,
        int minY,
        int maxY,
        int veinSize,
        double spawnFrequency) {

    public static PlanetResource common(String id, String block, int minY, int maxY, int vein, double freq) {
        return new PlanetResource(id, block, false, minY, maxY, vein, freq);
    }

    public static PlanetResource rare(String id, String block, int minY, int maxY, int vein, double freq) {
        return new PlanetResource(id, block, true, minY, maxY, vein, freq);
    }
}