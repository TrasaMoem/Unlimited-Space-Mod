package com.modscreating.unlimitedspace.core.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R15: zoom state tests — exactly ten logical levels, clamped 1..10, driven by
 * mouse wheel and '+'/'-' keys, with smooth interpolation towards the target.
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
        assertTrue(z.onWheel(+1)); // wheel up -> zoom in
        assertEquals(2, z.targetLevel());
        assertTrue(z.onWheel(-1)); // wheel down -> zoom out
        assertEquals(1, z.targetLevel());
        assertFalse(z.onWheel(0));
    }

    @Test
    void plusAndMinusKeysStepByOne() {
        MapZoomState z = new MapZoomState();
        for (int i = 0; i < 20; i++) z.zoomIn(); // '+' spam must clamp at 10
        assertEquals(10, z.targetLevel());
        for (int i = 0; i < 20; i++) z.zoomOut(); // '-' spam must clamp at 1
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
    void smoothInterpolationEasesTowardsTarget() {
        MapZoomState z = new MapZoomState(1);
        z.setTargetLevel(10);
        double before = z.currentZoom();
        assertTrue(before < 10.0);
        boolean monotonicUp = true;
        double prev = before;
        for (int i = 0; i < 200 && z.isAnimating(); i++) {
            z.update();
            if (z.currentZoom() < prev - 1e-12) monotonicUp = false;
            prev = z.currentZoom();
        }
        assertTrue(monotonicUp);
        assertFalse(z.isAnimating());
        assertEquals(z.targetZoom(), z.currentZoom(), 1e-2);
        assertEquals(10, z.level());
    }
}
