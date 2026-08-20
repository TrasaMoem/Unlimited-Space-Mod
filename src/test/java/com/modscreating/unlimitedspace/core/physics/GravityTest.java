package com.modscreating.unlimitedspace.core.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12.2 Bug #1 + R14.5.1 — gravity unit/scale invariants (pure domain).
 *
 * <p>Verifies the Earth-g ↔ m/s² contract and, for R14.5.1, the single source of truth for orbit
 * gravity: {@link Gravity#CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ} is EXACTLY the value Creating Space's
 * own 1.7.18 datapack uses (orbit gravity 0 / arrival 64; surface arrival 200). Surface/moon worlds
 * must always carry a positive, playable value, while every orbit AND every weightless asteroid field
 * uses the fixed CS-zero value independently of the body's surface gravity.
 */
class GravityTest {

    @Test
    void earthGToMetersPerSecondSquaredMatchesCS() {
        // CS sample data: overworld 9.81, the_moon 1.6, mars 3.71, venus 1.6 (all in m/s²).
        assertEquals(9.81, Gravity.toMetersPerSecondSq(1.0), 1e-9);
        assertEquals(1.57, Gravity.toMetersPerSecondSq(0.16), 0.01);   // moon
        assertEquals(9.71, Gravity.toMetersPerSecondSq(0.99), 0.01);   // earth-like planet
    }

    @Test
    void csReferenceConstantsMatchInstalledCreatingSpaceDatapack() {
        // earth_orbit / mars_orbit / moon_orbit (CS 1.7.18): gravity 0, arrival 64.
        assertEquals(0.0, Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, 1e-9);
        assertEquals(64, Gravity.CS_ORBIT_ARRIVAL_HEIGHT);
        // venus / mars / the_moon / overworld (CS 1.7.18): arrival 200 (sky-descent landing).
        assertEquals(200, Gravity.CS_SURFACE_ARRIVAL_HEIGHT);
        assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                Gravity.MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, 1e-12);
    }

    @Test
    void orbitGravityIsSingleFixedConstantIndependentOfBodySurface() {
        double planetSurface = Gravity.toMetersPerSecondSq(0.99); // 9.71 m/s²
        double moonSurface = Gravity.toMetersPerSecondSq(0.16);   // ~1.57 m/s²
        // The orbit gravity must NOT be derived from any body's surface gravity.
        assertNotEquals(planetSurface, Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ);
        assertNotEquals(moonSurface, Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ);
        assertTrue(Gravity.isOrbitCompatibleGravity(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ));
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
        assertEquals(0.05, Gravity.playableEarthG(0.05), 1e-12);
        assertEquals(0.30, Gravity.playableEarthG(0.30), 1e-12);
        assertEquals(0.80, Gravity.playableEarthG(0.80), 1e-12);
        assertEquals(1.20, Gravity.playableEarthG(1.20), 1e-12);
        assertEquals(2.00, Gravity.playableEarthG(2.00), 1e-12);
        assertEquals(4.00, Gravity.playableEarthG(4.00), 1e-12);
    }

    @Test
    void orbitGravityMatchesCreatingSpaceEarthOrbit() {
        assertEquals(0.0, Gravity.MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ, 1e-9);
        assertTrue(Gravity.isOrbitCompatibleGravity(0.0));   // CS earth_orbit = 0
        assertTrue(Gravity.isOrbitCompatibleGravity(1.62));  // any non-negative accepted
        assertFalse(Gravity.isOrbitCompatibleGravity(-1.0)); // negative = invalid
    }

    @Test
    void playableMetersPerSecondSqAcceptsConvertedSurfaceGravities() {
        assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(0.16))); // moon
        assertTrue(Gravity.isPlayableMetersPerSecondSq(Gravity.toMetersPerSecondSq(0.99))); // planet
        assertFalse(Gravity.isPlayableMetersPerSecondSq(0.0));   // zero = orbit-only
        assertFalse(Gravity.isPlayableMetersPerSecondSq(-1.0));  // negative = unusable
    }

    @Test
    void asteroidGravityIsZeroGWeightlessField() {
        // R14.5.1 REQ 4/9 (reverses R14.5 BUG 6A): asteroid fields are WEIGHTLESS — exactly the CS
        // orbit gravity (0), independent of seed. A zero-g asteroid is a space field, NOT a walkable
        // surface, so it must NOT satisfy the playable-surface predicate.
        assertEquals(0.0, Gravity.asteroidGravityMetersPerSecondSq(), 1e-12);
        assertEquals(Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                Gravity.asteroidGravityMetersPerSecondSq(), 1e-12);
        assertFalse(Gravity.isPlayableMetersPerSecondSq(Gravity.asteroidGravityMetersPerSecondSq()),
                "weightless asteroid field must not be classified as a walkable surface");
    }
}