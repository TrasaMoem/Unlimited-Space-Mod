package com.modscreating.unlimitedspace.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12.5: deterministic scale model for the celestial visual layers (pure math only).
 *
 * <p>The current-orbit-body numbers are transcribed one-to-one from the decompiled Creating Space
 * 1.7.18 bytecode (EarthOrbitEffects / GenericCelestialOrbitEffect.renderAstralBody):
 * half-size 150, plane 60 + 40*(alt+64)/448, orientation YP(-90) then XP(180). These tests pin those
 * CS reference values, assert dominance over system stars / sibling bodies, and verify determinism.
 * Rendering itself is not unit-tested (it needs a live client).
 */
class CelestialVisualScaleTest {

    @Test
    void currentBodyHalfMatchesCSReference() {
        // CS Earth quad half-size literal is 150.0f.
        assertEquals(150.0f, CelestialVisualScale.CS_BODY_HALF, 1e-3f);
        assertEquals(CelestialVisualScale.CS_BODY_HALF, CelestialVisualScale.currentBodyHalf(), 1e-3f);
    }

    @Test
    void currentBodyOrientationMatchesCSReference() {
        // CS renderAstralBody alpha branch: YP(-90) then XP(180).
        assertEquals(-90.0f, CelestialVisualScale.currentBodyRotY(), 1e-3f);
        assertEquals(180.0f, CelestialVisualScale.currentBodyRotX(), 1e-3f);
    }

    @Test
    void currentBodyPlaneMatchesCSFormulaAndIsMonotone() {
        // CS: 60 + 40*(alt - (-64)) / (384 - (-64)); at alt=-64 -> 60, at alt=384 -> 100.
        assertEquals(CelestialVisualScale.CS_BODY_Y_BASE,
                CelestialVisualScale.currentBodyPlaneY(CelestialVisualScale.CS_BODY_ALT_BASE), 1e-3f);
        assertEquals(CelestialVisualScale.CS_BODY_Y_BASE + CelestialVisualScale.CS_BODY_Y_ALTITUDE_STEP,
                CelestialVisualScale.currentBodyPlaneY(CelestialVisualScale.CS_BODY_ALT_MAX), 1e-3f);
        // Mid-altitude sits strictly between the two.
        float lo = CelestialVisualScale.currentBodyPlaneY(CelestialVisualScale.CS_BODY_ALT_BASE);
        float mid = CelestialVisualScale.currentBodyPlaneY(160.0);
        float hi = CelestialVisualScale.currentBodyPlaneY(CelestialVisualScale.CS_BODY_ALT_MAX);
        assertTrue(lo < mid && mid < hi, "plane height must increase monotonically with altitude");
        // Out-of-window altitudes are clamped, so the value is always finite and in [60, 100].
        assertTrue(CelestialVisualScale.currentBodyPlaneY(-100000) >= 60f);
        assertTrue(CelestialVisualScale.currentBodyPlaneY(100000) <= 100f);
    }

    @Test
    void currentBodyIsDominantOverSystemStar() {
        for (float apparent = 0f; apparent <= 40f; apparent += 0.5f) {
            assertTrue(CelestialVisualScale.currentBodyHalf()
                            > CelestialVisualScale.systemStarRadius(apparent) * 3f,
                    "current body must dwarf the system star (apparent=" + apparent + ")");
        }
    }

    @Test
    void currentBodyScaleIsDeterministic() {
        assertEquals(CelestialVisualScale.currentBodyHalf(), CelestialVisualScale.currentBodyHalf());
        assertEquals(CelestialVisualScale.currentBodyPlaneY(123.0),
                CelestialVisualScale.currentBodyPlaneY(123.0));
    }

    @Test
    void systemStarsStayCompactAndDifferentiated() {
        // compact: never exceeds the fixed cap, stays well under the current body.
        assertTrue(CelestialVisualScale.systemStarRadius(1000f)
                < CelestialVisualScale.currentBodyHalf(), "system star must stay compact");
        // binary / trinary: distinct apparent-luminosity -> distinct core radius (deterministic).
        assertTrue(CelestialVisualScale.systemStarRadius(5f)
                < CelestialVisualScale.systemStarRadius(20f), "brighter star must be bigger");
        assertEquals(CelestialVisualScale.systemStarRadius(7f),
                CelestialVisualScale.systemStarRadius(7f), "deterministic");
    }

    @Test
    void currentBodyDwarfsAnySiblingApparentSize() {
        // sibling apparent sizes are small (planets ~14*rp/depth, moons ~6*rp).
        for (float s = 0f; s <= 60f; s += 1f) {
            assertTrue(CelestialVisualScale.currentBodyHalf()
                    > CelestialVisualScale.siblingHalfSize(s) * 2f,
                    "current body must dominate sibling of apparent " + s);
        }
    }

    // ---------------------------------------------------------------- R12.6 multi-body scene

    @Test
    void parentBodyIsApproximatelyOneThirdOfCurrentBody() {
        assertEquals(CelestialVisualScale.currentBodyHalf() / 3.0f,
                CelestialVisualScale.parentBodyHalf(), 1e-3f);
        // clearly sub-dominant but non-zero
        assertTrue(CelestialVisualScale.parentBodyHalf() < CelestialVisualScale.currentBodyHalf());
        assertTrue(CelestialVisualScale.parentBodyHalf() > 0f);
    }

    @Test
    void currentBodyDominatesParentDominatesSiblingPlanets() {
        float current = CelestialVisualScale.currentBodyHalf();
        float parent = CelestialVisualScale.parentBodyHalf();
        // worst-case sibling planet (nearest, biggest radius) must stay below the parent.
        float nearestSibling = CelestialVisualScale.siblingPlanetHalf(5f, 0);
        assertTrue(current > parent, "current body must be the anchor (largest)");
        assertTrue(parent > nearestSibling, "parent planet must out-scale any sibling planet");
    }

    @Test
    void fartherPlanetIsSmaller() {
        // deterministic distance rule: the further the orbit, the smaller the sibling planet.
        assertTrue(CelestialVisualScale.siblingPlanetHalf(1f, 0)
                > CelestialVisualScale.siblingPlanetHalf(1f, 2));
        assertTrue(CelestialVisualScale.siblingPlanetHalf(1f, 2)
                > CelestialVisualScale.siblingPlanetHalf(1f, 5));
    }

    @Test
    void roleScalesAreDeterministic() {
        assertEquals(CelestialVisualScale.parentBodyHalf(), CelestialVisualScale.parentBodyHalf());
        assertEquals(CelestialVisualScale.siblingPlanetHalf(1.4f, 3),
                CelestialVisualScale.siblingPlanetHalf(1.4f, 3));
        assertEquals(CelestialVisualScale.siblingMoonHalf(0.8f), CelestialVisualScale.siblingMoonHalf(0.8f));
        // bodies are never zero-sized
        assertTrue(CelestialVisualScale.parentBodyHalf() > 0f);
        for (int oi = 0; oi <= 10; oi++) {
            assertTrue(CelestialVisualScale.siblingPlanetHalf(1f, oi) > 0f);
        }
    }
}
