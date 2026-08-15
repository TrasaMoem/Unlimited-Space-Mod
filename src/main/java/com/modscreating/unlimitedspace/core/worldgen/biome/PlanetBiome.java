package com.modscreating.unlimitedspace.core.worldgen.biome;

/**
 * Minimal set of domain biome archetypes for Phase 7. Purely semantic: the
 * Minecraft-side adapter maps each to a concrete {@code Holder<Biome>}. No
 * display-name dependency: selection is driven by a deterministic sample value.
 */
public enum PlanetBiome {
    HOT_DRY,
    COLD_DRY,
    WARM_WET,
    OCEAN
}