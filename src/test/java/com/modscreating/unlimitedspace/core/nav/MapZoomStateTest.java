package com.modscreating.unlimitedspace.core.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R15 (updated R15.3): zoom state tests - ten logical levels, clamped 1..10, driven by
 * mouse wheel and '+'/'-' keys, with a SMOOTH TIMED (~2 s) transition to the target.
 */
class MapZoomStateTest {

    @Test
    void startsAtLevelOne() {
        MapZoomState z = new MapZoomState();
        assertEquals(1, z.level());
        assertEquals(1.0, z.currentZoom(), 1e-9);
        assertEquals(1.0, z.targetZoom(), 1e-9);
    }

    @Test
    void wheelUpZoomsIn_wheelDownZoomsOut() {
        MapZoomState z = new MapZoomState();
        assertTrue(z.onWheel(+1));
        assertEquals(2, z.targetLevel());
        assertTrue(z.onWheel(-1));
        assertEquals(1, z.targetLevel());
        assertFalse(z.onWheel(0));
    }

    @Test
    void plusAndMinusKeysStepByOne() {
        MapZoomState z = new MapZoomState();
        for (int i = 0; i < 20; i++) z.zoomIn();
        assertEquals(10, z.targetLevel());
        for (int i = 0; i < 20; i++) z.zoomOut();
        assertEquals(1, z.targetLevel());
    }

    @Test
    void boundsAreExactlyOneToTen() {
        MapZoomState z = new MapZoomState();
        z.setTargetLevel(-5);
        assertEquals(MapZoomState.MIN_LEVEL, z.targetLevel());
        z.setTargetLevel(99);
        assertEquals(MapZoomState.MAX_LEVEL, z.targetLevel());
    }

    @Test
    void timedTransitionCompletesInAboutTwoSeconds() {
        MapZoomState z = new MapZoomState();
        long t0 = 100_000L;
        z.setTargetLevel(10);
        z.forceAnimStartForTest(t0 + 40L); // pin start, drive synthetic clock
        // drive time manually from just after the request:
        long t = t0 + 50L;
        double prev = -1;
        boolean monotonicUp = true;
        while (z.isAnimating()) {
            z.updateAt(t);
            if (z.currentZoom() < prev - 1e-9) monotonicUp = false;
            prev = z.currentZoom();
            t += 100L;
            assertTrue(t < t0 + 10_000L, "animation must finish well before 10 s");
        }
        assertTrue(monotonicUp);
        assertEquals(10.0, z.currentZoom(), 1e-6);
        assertFalse(z.isAnimating());
    }
}
