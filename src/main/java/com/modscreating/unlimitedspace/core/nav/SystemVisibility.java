package com.modscreating.unlimitedspace.core.nav;

import java.util.Collection;

/**
 * R22: fog-of-war visibility for the galaxy map. A system is KNOWN when the player
 * is in it right now, has VISITED it at least once (its index appears in the
 * visited set), or it lies within the current VISIBILITY RADIUS (light-years)
 * of the player's system. Everything else is displayed as "???".
 *
 * <p>The radius is deliberately mutable state: future "visibility booster"
 * upgrades / server config can {@link #setRadiusLy(double)} it at runtime.
 * Deliberately Minecraft-free so the decision logic is unit-testable.</p>
 */
public final class SystemVisibility {

    /** Default discovery radius in light-years. */
    public static final double DEFAULT_RADIUS_LY = 1600.0;

    private double radiusLy;

    public SystemVisibility() {
        this(DEFAULT_RADIUS_LY);
    }

    public SystemVisibility(double radiusLy) {
        setRadiusLy(radiusLy);
    }

    /** Current visibility radius in light-years (always finite, >= 0). */
    public double radiusLy() {
        return radiusLy;
    }

    /**
     * Set the visibility radius (e.g. a booster upgrade). Non-finite or negative
     * values are clamped: negative -> 0 (only current/visited systems known).
     */
    public void setRadiusLy(double radiusLy) {
        if (Double.isNaN(radiusLy) || Double.isInfinite(radiusLy) || radiusLy < 0) {
            radiusLy = 0;
        }
        this.radiusLy = radiusLy;
    }

    /**
     * R22b: whether a direct flight to this system is allowed - only when the
     * player is already there OR it lies within the visibility radius. A merely
     * KNOWN (visited, but distant) system cannot be flown to directly.
     */
    public boolean canTravelTo(int systemIndex, int currentSystemIndex, double distanceLy) {
        if (systemIndex == currentSystemIndex) return true;
        return withinRadius(distanceLy);
    }

    /** True when {@code distanceLy} is within the current visibility radius. */
    public boolean withinRadius(double distanceLy) {
        return Double.isFinite(distanceLy) && distanceLy <= radiusLy;
    }

    /**
     * The single source of truth for "is this system known".
     *
     * @param systemIndex         system being queried (any value; callers may pass
     *                            sentinels like Sol's -2 - handled by identity checks only)
     * @param currentSystemIndex  the system the player is currently in (-1 = none/deep space)
     * @param visitedSystemIndices indices of systems visited at least once (recents)
     * @param distanceLy          light-year distance from the player's current system
     *                            (or its Sol-anchor fallback); {@code NaN/+inf} = unknown distance
     */
    public boolean isKnown(int systemIndex, int currentSystemIndex,
                           Collection<Integer> visitedSystemIndices, double distanceLy) {
        if (systemIndex == currentSystemIndex) return true;      // you are standing there
        if (visitedSystemIndices != null
                && visitedSystemIndices.contains(systemIndex)) return true; // visited once -> known forever
        return withinRadius(distanceLy);                          // inside the discovery bubble
    }
}
