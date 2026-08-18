package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12 test sets 3-7: star visual metadata — colour, black-hole distinction,
 * multi-star support and deterministic distance-implied size.
 */
class StarVisualTest {

    private static final long SYSTEM_SEED = 12345L;
    private static final StarSystemId SYSTEM = StarSystemId.of(3);

    private static Star star(StarType type, double size, double luminosity) {
        return Star.of(new StarId(SYSTEM), 999L, type, 5000.0, size, luminosity, type.colorRgb());
    }

    @Test
    void sameStarSeedAlwaysProducesSameVisual() {
        Star red = star(StarType.RED_DWARF, 0.5, 0.01);
        StarVisual a = StarVisual.create(SYSTEM_SEED, red, 0);
        StarVisual b = StarVisual.create(SYSTEM_SEED, red, 0);
        assertEquals(a, b);
    }

    @Test
    void redStarYieldsRedVisualMetadata() {
        StarVisual v = StarVisual.create(SYSTEM_SEED, star(StarType.RED_DWARF, 0.5, 0.01), 0);
        assertTrue(v.red() > v.blue(), "red star R should exceed B");
    }

    @Test
    void blueStarYieldsBlueVisualMetadata() {
        StarVisual v = StarVisual.create(SYSTEM_SEED, star(StarType.BLUE, 12.0, 500.0), 0);
        assertTrue(v.blue() > v.red(), "blue star B should exceed R");
    }

    @Test
    void multiStarSystemContainsDistinctVisualsForEachIndex() {
        Star a = star(StarType.G, 1.0, 1.0);
        Star b = star(StarType.M, 0.3, 0.01);
        StarVisual va = StarVisual.create(SYSTEM_SEED, a, 0);
        StarVisual vb = StarVisual.create(SYSTEM_SEED, b, 1);
        assertNotEquals(va.index(), vb.index());
        // second star is a smaller/redder companion
        assertTrue(vb.blue() < va.blue());
    }

    @Test
    void blackHoleIsNotRenderedAsNormalSun() {
        StarVisual v = StarVisual.create(SYSTEM_SEED, star(StarType.BLACK_HOLE, 1.0, 0.0), 0);
        assertTrue(v.blackHole());
        assertEquals(0xFF111111, v.colorRgb());
        // compact, not a giant glowing disc
        assertTrue(v.apparentRadius() < 10.0f);
    }

    @Test
    void apparentSizeScalesWithLuminosityDeterministically() {
        Star faint = star(StarType.K, 0.5, 0.05);
        Star bright = star(StarType.O, 20.0, 50000.0);
        StarVisual vf = StarVisual.create(SYSTEM_SEED, faint, 0);
        StarVisual vb = StarVisual.create(SYSTEM_SEED, bright, 0);
        assertTrue(vb.apparentRadius() > vf.apparentRadius(),
                "brighter star should have larger apparent radius");
        // determinism: recompute exactly equal
        assertEquals(vb, StarVisual.create(SYSTEM_SEED, bright, 0));
    }
}