package com.modscreating.unlimitedspace.core.galaxy.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R42: intra-system geometry - deterministic, small-scale (engine progression),
 * body-to-body distances usable by the fuel planner.
 */
class SystemGeometryTest {

    private static final long SEED = 123456789L;

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dz = a[1] - b[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Test
    void deterministicForSameInputs() {
        var a = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_01/surface");
        var b = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_01/surface");
        assertEquals(a[0], b[0], 1e-12);
        assertEquals(a[1], b[1], 1e-12);
    }

    @Test
    void orbitSharesTheBodyPosition() {
        var surface = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_01/surface");
        var orbit = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_01/orbit");
        assertEquals(0.0, dist(surface, orbit), 1e-9);
    }

    @Test
    void planetsStayInsideTheSystemAndAreDistinct() {
        var p0 = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_00/surface");
        var p5 = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_05/surface");
        assertTrue(Math.hypot(p0[0], p0[1]) <= SystemGeometry.SYSTEM_RADIUS_LY);
        assertTrue(Math.hypot(p5[0], p5[1]) <= SystemGeometry.SYSTEM_RADIUS_LY);
        double d = dist(p0, p5);
        assertTrue(d > 0.05, "distinct planets must not overlap");
        assertTrue(d <= 2 * SystemGeometry.SYSTEM_RADIUS_LY, "planet-planet hop must stay intra-system small");
    }

    @Test
    void moonIsCloseToItsParentPlanet() {
        var planet = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_01/surface");
        var moon = SystemGeometry.bodyOffsetLy(SEED, 42, "moon/system_0042_planet_01_moon_00/surface");
        double d = dist(planet, moon);
        assertTrue(d > 0.0 && d < 0.2, "moon must hug its planet, was " + d);
    }

    @Test
    void moonsOfOnePlanetAreSpacedApart() {
        var m0 = SystemGeometry.bodyOffsetLy(SEED, 42, "moon/system_0042_planet_01_moon_00/surface");
        var m2 = SystemGeometry.bodyOffsetLy(SEED, 42, "moon/system_0042_planet_01_moon_02/surface");
        assertTrue(dist(m0, m2) > 0.01, "different moons must not share a position");
    }

    @Test
    void starIsAtTheCentre() {
        var star = SystemGeometry.bodyOffsetLy(SEED, 42, "star/system_0042/surface");
        assertEquals(0.0, star[0], 1e-9);
        assertEquals(0.0, star[1], 1e-9);
        var planet = SystemGeometry.bodyOffsetLy(SEED, 42, "planet/system_0042_planet_00/surface");
        assertTrue(dist(star, planet) > 0.0);
    }

    @Test
    void solBodiesUseTheFixedLayout() {
        var earth = SystemGeometry.bodyOffsetLy(SEED, -2, "overworld");
        var moon = SystemGeometry.bodyOffsetLy(SEED, -2, "the_moon");
        var mars = SystemGeometry.bodyOffsetLy(SEED, -2, "mars");
        assertEquals(0.03, dist(earth, moon), 1e-9);
        assertTrue(dist(earth, mars) > 0.1, "Earth and Mars must be apart");
    }

    @Test
    void asteroidFieldsHaveTheirOwnPositions() {
        var a0 = SystemGeometry.bodyOffsetLy(SEED, 42, "asteroid/system_0042_asteroid_00");
        var a1 = SystemGeometry.bodyOffsetLy(SEED, 42, "asteroid/system_0042_asteroid_01");
        assertTrue(dist(a0, a1) > 0.0);
    }
}