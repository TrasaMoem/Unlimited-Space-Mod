package com.modscreating.unlimitedspace.core.worldgen.profile;

/**
 * Coarse surface-gravity classification (R16 planet-diversity foundation).
 *
 * <p>Reuses the existing planet {@code gravity} value (in Earth g) and quantifies it into a
 * stable class so material/erosion/vegetation rules can key off "low vs high gravity"
 * without hardcoding numeric thresholds repeatedly. Pure domain: no Minecraft types.
 */
public enum GravityClass {

    MICRO(0.00, 0.25),
    LOW(0.25, 0.60),
    STANDARD(0.60, 1.40),
    HIGH(1.40, 2.50),
    CRUSHING(2.50, Double.MAX_VALUE);

    public static final GravityClass[] VALUES = values();

    private final double minEarthG;
    private final double maxEarthG;

    GravityClass(double minEarthG, double maxEarthG) {
        this.minEarthG = minEarthG;
        this.maxEarthG = maxEarthG;
    }

    public double minEarthG() {
        return minEarthG;
    }

    public double maxEarthG() {
        return maxEarthG;
    }

    /** True for low-gravity bodies (relief preserved, sparse atmosphere retention). */
    public boolean isLow() {
        return this == MICRO || this == LOW;
    }

    /** True for high-gravity bodies (strong erosion/flattening, dense retention). */
    public boolean isHigh() {
        return this == HIGH || this == CRUSHING;
    }

    /** Deterministic class lookup for a gravity value in Earth g. */
    public static GravityClass of(double earthG) {
        double v = earthG;
        if (v < 0.0) v = 0.0;
        for (GravityClass c : VALUES) {
            if (v < c.maxEarthG) return c;
        }
        return CRUSHING;
    }
}
