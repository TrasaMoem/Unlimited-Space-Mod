package com.modscreating.unlimitedspace.core.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12.2 Bug #1 — gravity unit/scale invariants (pure domain).
 *
 * <p>Verifies the Earth-g ↔ m/s² contract that fixes the reported "zero gravity / unplayably
 * small gravity" complaint: CS {@code RocketAccessibleDimension.gravity()} is a physical
 * acceleration in m/s² (Earth = 9.81), and surface/moon/asteroid worlds must always carry a
 * playable, positive value while orbit worlds remain at the CS-intended zero.
 */
class GravityTest {

    @Test
    void earthGToMetersPerSecondSquaredMatchesCS() {
        // CS sample data: overworld 9.81, the_moon 1.6, mars 3.71, venus 1.6 (all in m/s²).
        assertEquals(9.81, Gravity.toMetersPerSecondSq(1.0), 1e-9);
        assertEquals(1.57, Gravity.toMetersPerSecondSq(0.16), 0.01);   // moon
        assertEquals(0.49, Gravity.toMetersPerSecondSq(0.05), 0.01);   // asteroid
        assertEquals(9.71, Gravity.toMetersPerSecondSq(0.99), 0.01);   // earth-like planet
    }

    @Test
    void minPlayableFloorNeverZeroForSurfaces() {
        assertTrue(Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G > 0.0);
        assertTrue(Gravity.toMetersPerSecondSq(Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G) > 0.0);
    }

    @Test
    void playableEarthGClampsNonPositiveToFloor() {
        assertEquals(Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G, Gravity.playableEarthG(0.0), 1e-12);
        assertEquals(Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G, Gravity.playableEarthG(-3.0), 1e-12);
        assertEquals(Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G, Gravity.playableEarthG(Double.NaN), 1e-12);
    }

    @Test
    void playableEarthGPreservesNaturalVariation() {
        // Variation is NOT collapsed to Earth gravity.
        assertEquals(0.05, Gravity.playableEarthG(0.05), 1e-12);
        assertEquals(0.30, Gravity.playableEarthG(0.30), 1e-12);
        assertEquals(0.80, Gravity.playableEarthG(0.80), 1e-12);
        assertEquals(1.20, Gravity.playableEarthG(1.20), 1e-12);
        assertEquals(2.00, Gravity.playableEarthG(2.00), 1e-12);
        assertEquals(4.00, Gravity.playableEarthG(4.00), 1e-12);
    }

    @Test
    void orbitGravityMatchesCreatingSpaceEarthOrbit() {
        // R12.3 Bug: orbit/asteroid gravity is EXACTLY Creating Space's Earth-orbit value — zero-g
        // orbital flight ({@code creatingspace:earth_orbit} carries 0). The player floats in the
        // middle and manoeuvres with the rocket thrusters.
        assertEquals(0.0, Gravity.MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, 1e-9);
        assertTrue(Gravity.isOrbitCompatibleGravity(0.0));   // CS earth_orbit = 0
        assertTrue(Gravity.isOrbitCompatibleGravity(0.49));  // any non-negative accepted
        assertTrue(Gravity.isOrbitCompatibleGravity(1.62));  // any non-negative accepted
        assertFalse(Gravity.isOrbitCompatibleGravity(-1.0)); // negative = invalid
    }

    @Test
    void playableMetersPerSecondSqAcceptsConvertedSurfaceGravities() {
        assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(0.05))); // asteroid
        assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(0.16))); // moon
        assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(0.99))); // planet
        assertFalse(Gravity.isPlayableMetersPerSecondSq(0.0));   // zero = orbit-only
        assertFalse(Gravity.isPlayableMetersPerSecondSq(-1.0));  // negative = unusable
    }
}
