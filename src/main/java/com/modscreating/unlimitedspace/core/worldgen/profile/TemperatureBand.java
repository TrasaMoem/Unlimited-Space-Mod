package com.modscreating.unlimitedspace.core.worldgen.profile;

/**
 * Coarse thermal band of a planet (R16 planet-diversity foundation).
 *
 * <p>A normalized temperature in {@code [0,1]} (cold &rarr; hot) is quantified into a small,
 * stable band so material/fluid/effect rules can express "cold-only" or "hot-only"
 * constraints without repeating raw thresholds everywhere. Pure domain: no Minecraft types.
 */
public enum TemperatureBand {

    // PHASE 1: boundaries are defined in real KELVIN and mapped onto the LOG-normalized
    // temperature axis (T01 = ln(K/30)/ln(4600/30)). A planet with T01 below a boundary is
    // in the band whose Kelvin range contains its real temperature. Design targets:
    //   FROZEN 30-150 K, COLD 150-240, TEMPERATE 240-320, WARM 320-400,
    //   HOT 400-800, INFERNO 800-4600+.
    FROZEN(0.00, norm(150.0)),
    COLD(norm(150.0), norm(240.0)),
    TEMPERATE(norm(240.0), norm(320.0)),
    WARM(norm(320.0), norm(400.0)),
    HOT(norm(400.0), norm(800.0)),
    INFERNO(norm(800.0), 1.01);

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

    /** Kelvin → log-normalized axis used by the band boundaries. */
    private static double norm(double kelvin) {
        return com.modscreating.unlimitedspace.core.physics.StellarThermalModel.normalizeKelvin(kelvin);
    }

    /** PHASE 1: the band of a REAL Kelvin temperature (canonical Kelvin entry point). */
    public static TemperatureBand ofKelvin(double kelvin) {
        return of(com.modscreating.unlimitedspace.core.physics.StellarThermalModel.normalizeKelvin(kelvin));
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
