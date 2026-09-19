package com.modscreating.unlimitedspace.core.worldgen.resources;

/**
 * Rarity tier of a planetary resource (R18 provincial-coherence stage).
 *
 * <p>Used to keep resources from saturating the ground: common materials stay abundant enough
 * to mine, while province-specific exotic ores stay rare. The tier only drives density — the
 * actual spawn is still a pure seed function, so it never changes identity determinism.
 */
public enum RarityTier {
    COMMON,
    UNCOMMON,
    RARE,
    VERY_RARE;

    /** Baseline per-cell frequency in [0,1] for a tier. */
    public double baseFrequency() {
        return switch (this) {
            case COMMON -> 0.05;
            case UNCOMMON -> 0.02;
            case RARE -> 0.008;
            case VERY_RARE -> 0.003;
        };
    }
}