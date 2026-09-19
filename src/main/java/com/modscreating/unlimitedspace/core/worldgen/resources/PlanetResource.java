package com.modscreating.unlimitedspace.core.worldgen.resources;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

/**
 * Data-driven resource definition (Phase 8, extended R18). Identified by a stable id and a
 * target block key (registry id, not a display name). Distribution parameters feed a later
 * Minecraft {@code OreConfiguration}-style placement; here they are pure data.
 *
 * <p>R18 adds a {@link RarityTier} (so per-province exotic ores stay rare) and an optional
 * {@link GeologicalProvince} preference (so a Volcanic province tends to host volcanic-compatible
 * ores while a Glacial province tends to host crystal/mineral ones — coherence over randomness).
 *
 * @param id           stable semantic id
 * @param targetBlock  Minecraft block registry id
 * @param rare         legacy flag (kept for back-compat; {@link #tier()} is authoritative)
 * @param minY         minimum spawn Y
 * @param maxY         maximum spawn Y
 * @param veinSize     ore vein size
 * @param spawnFrequency base per-cell spawn frequency (overridden by {@link RarityTier#baseFrequency()})
 * @param tier         rarity tier
 * @param province     optional preferred geological province
 */
public record PlanetResource(
        String id,
        String targetBlock,
        boolean rare,
        int minY,
        int maxY,
        int veinSize,
        double spawnFrequency,
        RarityTier tier,
        GeologicalProvince province) {

    public PlanetResource {
        if (tier == null) tier = rare ? RarityTier.RARE : RarityTier.COMMON;
    }

    public static PlanetResource common(String id, String block, int minY, int maxY, int vein, double freq) {
        return new PlanetResource(id, block, false, minY, maxY, vein, freq, RarityTier.COMMON, null);
    }

    public static PlanetResource rare(String id, String block, int minY, int maxY, int vein, double freq) {
        return new PlanetResource(id, block, true, minY, maxY, vein, freq, RarityTier.RARE, null);
    }

    public static PlanetResource of(String id, String block, int minY, int maxY, int vein,
                                    RarityTier tier, GeologicalProvince province) {
        return new PlanetResource(id, block, tier == RarityTier.COMMON || tier == RarityTier.UNCOMMON,
                minY, maxY, vein, 0.0, tier, province);
    }

    /** Effective per-cell frequency (tier-based unless an explicit frequency was provided). */
    public double effectiveFrequency() {
        return spawnFrequency > 0.0 ? spawnFrequency : tier.baseFrequency();
    }
}