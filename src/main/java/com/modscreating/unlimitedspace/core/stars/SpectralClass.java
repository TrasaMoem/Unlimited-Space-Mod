package com.modscreating.unlimitedspace.core.stars;

import java.util.List;

/**
 * The seven standard main-sequence spectral classes, ordered hottest to coolest:
 *
 * <pre>
 *   O (hottest) → B → A → F → G → K → M (coolest)
 * </pre>
 *
 * <p>R14.9: the spectral class is DERIVED from the star's actual procedural temperature — it is
 * never the authoritative source (color is derived FROM the class, not the other way round). This
 * enum is pure data and is shared by the domain, the visual profile and the tests, so the whole
 * pipeline agrees on one classification. There is deliberately one {@link #fromTemperature} rule,
 * mirrored from the {@link StarType} temperature windows, so a star re-derived from its temperature
 * always lands on the class that generated it.
 */
public enum SpectralClass {

    /** Hottest; blue/blue-white. */
    O,
    /** Blue-white. */
    B,
    /** White. */
    A,
    /** Yellow-white. */
    F,
    /** Yellow (Sun-like). */
    G,
    /** Orange. */
    K,
    /** Coolest; red / deep orange-red. */
    M;

    /** All classes in hottest-to-coolest order (O first). */
    public static List<SpectralClass> hottestToCoolest() {
        return List.of(O, B, A, F, G, K, M);
    }

    /**
     * Derive the spectral class from the star's procedural temperature in Kelvin.
     *
     * <p>The boundaries mirror the {@link StarType} temperature windows so the derived class always
     * agrees with the class the generator chose for the same temperature:
     * <pre>
     *   O: 30000+   B: 10000–30000   A: 7500–10000   F: 6000–7500
     *   G: 5200–6000    K: 3700–5200   M: 2500–3700
     * </pre>
     * Temperatures below the M window (e.g. black holes) clamp to M; temperatures above the O window
     * clamp to O. This never returns {@code null}.
     */
    public static SpectralClass fromTemperature(double kelvin) {
        if (Double.isNaN(kelvin)) return M;
        if (kelvin >= 30000.0) return O;
        if (kelvin >= 10000.0) return B;
        if (kelvin >= 7500.0) return A;
        if (kelvin >= 6000.0) return F;
        if (kelvin >= 5200.0) return G;
        if (kelvin >= 3700.0) return K;
        return M;
    }

    /** 0 = hottest (O), 6 = coolest (M). Used for ordering / comparison. */
    public int heatIndex() {
        return ordinal();
    }

    /** True if {@code this} is hotter (closer to O) than {@code other}. */
    public boolean isHotterThan(SpectralClass other) {
        return ordinal() < other.ordinal();
    }

    /** Representative midpoint temperature in Kelvin for this class (visual interpolation anchors). */
    public double midpointKelvin() {
        return switch (this) {
            case O -> 40000.0;
            case B -> 20000.0;
            case A -> 8800.0;
            case F -> 6800.0;
            case G -> 5600.0;
            case K -> 4400.0;
            case M -> 3100.0;
        };
    }
}
