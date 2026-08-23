package com.modscreating.unlimitedspace.core.nav;

/**
 * R15: the ten logical zoom levels of the galaxy map with smooth interpolation.
 *
 * <pre>
 *   1 Entire Galaxy · 2 Galactic · 3 Regional · 4 Dense Star Field · 5 Local Region
 *   6 System Neighborhood · 7 System Approach · 8 System Overview · 9 System Detail
 *   10 Celestial Detail
 * </pre>
 *
 * Pure logic (no Minecraft types): wheel up / '+' zoom in, wheel down / '-' zoom out,
 * always clamped to [1, 10]. {@link #currentZoom()} eases towards {@link #targetZoom()}
 * so the map never snaps.
 */
public final class MapZoomState {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    private static final double EASE = 0.22;
    private static final double SNAP_EPSILON = 0.002;

    private double targetZoom;
    private double currentZoom;

    public MapZoomState() {
        this(MIN_LEVEL);
    }

    public MapZoomState(double initial) {
        this.currentZoom = clamp(initial);
        this.targetZoom = this.currentZoom;
    }

    /** Logical LOD level (1..10), the rounded current zoom. */
    public int level() {
        return (int) Math.round(currentZoom);
    }

    public int targetLevel() {
        return (int) Math.round(targetZoom);
    }

    public double currentZoom() {
        return currentZoom;
    }

    public double targetZoom() {
        return targetZoom;
    }

    /** Mouse wheel: positive delta = zoom in. */
    public boolean onWheel(double scrollDelta) {
        if (scrollDelta > 0) {
            zoomIn();
            return true;
        }
        if (scrollDelta < 0) {
            zoomOut();
            return true;
        }
        return false;
    }

    /** '+' key. */
    public void zoomIn() {
        setTargetLevel(targetLevel() + 1);
    }

    /** '-' key. */
    public void zoomOut() {
        setTargetLevel(targetLevel() - 1);
    }

    /** Direct level request (search jump, tab switch). Clamped to [1, 10]. */
    public void setTargetLevel(int level) {
        this.targetZoom = clamp(level);
    }

    /** Advance the smooth interpolation; call once per frame/tick. */
    public void update() {
        if (Math.abs(targetZoom - currentZoom) <= SNAP_EPSILON) {
            currentZoom = targetZoom;
            return;
        }
        currentZoom += (targetZoom - currentZoom) * EASE;
    }

    public boolean isAnimating() {
        return Math.abs(targetZoom - currentZoom) > SNAP_EPSILON;
    }

    private static double clamp(double v) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, v));
    }
}
