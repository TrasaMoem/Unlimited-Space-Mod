package com.modscreating.unlimitedspace.core.nav;

/**
 * R15: ten logical zoom levels of the galaxy map with SMOOTH TIMED interpolation.
 *
 * 1 Entire Galaxy / 2 Galactic / 3 Regional / 4 Dense Star Field / 5 Local Region /
 * 6 System Neighborhood / 7 System Approach / 8 System Overview / 9 System Detail /
 * 10 Celestial Detail
 *
 * R15.3: a zoom-level change now ANIMATES over DURATION_MS (1 second) with a
 * smoothstep curve, instead of the old fast per-frame easing. Wheel / '+' / '-'
 * semantics and the [1,10] clamp are unchanged.
 */
public final class MapZoomState {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    /** R15.3: duration of a zoom-level transition (ms). */
    public static final long DURATION_MS = 1000L;

    private double targetZoom;
    private double currentZoom;
    private double animFromZoom;
    private long animStartMs;

    public MapZoomState() {
        this(MIN_LEVEL);
    }

    public MapZoomState(double initial) {
        this.currentZoom = clamp(initial);
        this.targetZoom = this.currentZoom;
        this.animFromZoom = this.currentZoom;
        this.animStartMs = 0L;
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
        double newTarget = clamp(level);
        if (newTarget != targetZoom) {
            animFromZoom = currentZoom;
            targetZoom = newTarget;
            animStartMs = System.currentTimeMillis();
        }
    }

    /** Advance the animation using the wall clock; call once per frame. */
    public void update() {
        updateAt(System.currentTimeMillis());
    }

    /** Test hook: pin the animation start so tests can drive time deterministically. */
    void forceAnimStartForTest(long ms) { this.animStartMs = ms; }

    /** Time-injectable advance (used by tests). */
    public void updateAt(long nowMs) {
        long elapsed = nowMs - animStartMs;
        if (elapsed >= DURATION_MS) {
            currentZoom = targetZoom;
            return;
        }
        double t = Math.min(1.0, Math.max(0.0, elapsed / (double) DURATION_MS));
        double eased = t * t * (3.0 - 2.0 * t); // smoothstep
        currentZoom = animFromZoom + (targetZoom - animFromZoom) * eased;
    }

    public boolean isAnimating() {
        return Math.abs(targetZoom - currentZoom) > 1e-4;
    }

    private static double clamp(double v) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, v));
    }
}
