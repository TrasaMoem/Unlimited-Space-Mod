package com.modscreating.unlimitedspace.core.worldgen.profile;

/**
 * Coarse thermal band of a planet (R16 planet-diversity foundation).
 *
 * <p>A normalized temperature in {@code [0,1]} (cold &rarr; hot) is quantified into a small,
 * stable band so material/fluid/effect rules can express "cold-only" or "hot-only"
 * constraints without repeating raw thresholds everywhere. Pure domain: no Minecraft types.
 */
public enum TemperatureBand {

    FROZEN(0.00, 0.15),
    COLD(0.15, 0.32),
    TEMPERATE(0.32, 0.55),
    WARM(0.55, 0.75),
    HOT(0.75, 0.90),
    INFERNO(0.90, 1.01);

    public static final TemperatureBand[] VALUES = values();

    private final double minNormalized;
    private final double maxNormalized;

    TemperatureBand(double minNormalized, double maxNormalized) {
        this.minNormalized = minNormalized;
        this.maxNormalized = maxNormalized;
    }

    public double minNormalized() {
        return minNormalized;
    }

    public double maxNormalized() {
        return maxNormalized;
    }

    /** True when the band is at or below {@link #TEMPERATE} (cold half of the spectrum). */
    public boolean isCold() {
        return this == FROZEN || this == COLD;
    }

    /** True when the band is at or above {@link #HOT} (hot half of the spectrum). */
    public boolean isHot() {
        return this == HOT || this == INFERNO;
    }

    /** Deterministic band lookup for a normalized temperature (clamped to {@code [0,1]}). */
    public static TemperatureBand of(double normalized) {
        double v = normalized;
        if (v < 0.0) v = 0.0;
        if (v > 1.0) v = 1.0;
        for (TemperatureBand band : VALUES) {
            if (v < band.maxNormalized) return band;
        }
        return INFERNO;
    }
}
