package com.modscreating.unlimitedspace.core.galaxy;

/**
 * Immutable position of a star system within the galaxy (abstract galaxy units).
 * Plain domain value; no Minecraft coupling.
 *
 * @param x coordinate
 * @param y coordinate (small vertical spread)
 * @param z coordinate
 */
public record GalacticPosition(double x, double y, double z) {

    public static GalacticPosition of(double x, double y, double z) {
        return new GalacticPosition(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + trim(x) + ", " + trim(y) + ", " + trim(z) + ")";
    }

    private static double trim(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
