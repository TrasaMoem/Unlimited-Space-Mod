package com.modscreating.unlimitedspace.core.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 1 (orbit model): the procedural physical orbit that feeds the thermal model.
 *
 * <p>The physical orbit is a DESIGN-PRIOR quantity separate from the galaxy layout radius
 * ({@code PlanetPosition.radius}); these tests pin the contract the thermal model relies on:
 * deterministic, log-space distributed, bounded, slot-ordered and gently eccentric.
 */
class OrbitProfileTest {

    private static final long SEED = 0x5EEDCAFE0L;

    @Test
    void sameSeedAndSlotAlwaysYieldTheSameOrbit() {
        for (int o = 0; o < 8; o++) {
            OrbitProfile a = OrbitProfile.forSlot(SEED, o);
            OrbitProfile b = OrbitProfile.forSlot(SEED, o);
            assertEquals(a, b, "orbit must be a pure function of (seed, slot)");
        }
    }

    @Test
    void orbitDistanceStaysInsideTheDesignRange() {
        for (long s = 0; s < 400; s++) {
            for (int o = 0; o < 8; o++) {
                OrbitProfile orbit = OrbitProfile.forSlot(s * 31 + 7, o);
                assertTrue(orbit.orbitAU() >= OrbitProfile.AU_MIN && orbit.orbitAU() <= OrbitProfile.AU_MAX,
                        "orbitAU out of range: " + orbit.orbitAU());
            }
        }
    }

    @Test
    void innerSlotsAreOnAverageCloserThanOuterSlots() {
        // The ordering anchor must survive the jitter: slot 0 is on average well inside slot 6.
        double inner = 0.0;
        double outer = 0.0;
        int samples = 500;
        for (int i = 0; i < samples; i++) {
            long s = 1000L + i;
            inner += OrbitProfile.forSlot(s, 0).orbitAU();
            outer += OrbitProfile.forSlot(s, 6).orbitAU();
        }
        inner /= samples;
        outer /= samples;
        assertTrue(inner < outer * 0.5,
                "slot 0 must be clearly inner: inner=" + inner + " outer=" + outer);
    }

    @Test
    void orbitFanIsLogSpaceAndSystemScaled() {
        // Log-space (not uniform AU) sampling: for a fixed slot, min·max ≈ median² in log space.
        double min = Double.MAX_VALUE;
        double max = 0.0;
        double sumLog = 0.0;
        int n = 0;
        for (long s = 0; s < 2000; s++) {
            double au = OrbitProfile.forSlot(s * 7 + 3, 3).orbitAU();
            min = Math.min(min, au);
            max = Math.max(max, au);
            sumLog += Math.log(au);
            n++;
        }
        double geoMean = Math.exp(sumLog / n);
        double geoCenter = Math.sqrt(min * max);
        assertEquals(geoCenter, geoMean, geoCenter * 0.15,
                "a log-space draw is symmetric in log space: min=" + min + " max=" + max
                        + " geometric mean=" + geoMean);
        assertTrue(max / min < 3.0, "the per-slot jitter must stay bounded: " + (max / min));
    }

    @Test
    void brighterSystemsPushTheirFanOutwardButNotProportionally() {
        long seed = 4242L;
        double dim = OrbitProfile.forSlot(seed, 2, 0.05).orbitAU();
        double sun = OrbitProfile.forSlot(seed, 2, 1.0).orbitAU();
        double bright = OrbitProfile.forSlot(seed, 2, 100.0).orbitAU();
        assertTrue(dim < sun && sun < bright, "brighter systems keep their planets further out");
        // Partial scaling only: a 20x fainter star must NOT push the planet 20x inward (r ∝ L^0.35),
        // so stellar class stays visible in the planet temperature without erasing it.
        assertEquals(Math.pow(0.05, OrbitProfile.LUM_SCALE_EXP), dim / sun, 1e-9);
        assertEquals(Math.pow(100.0, OrbitProfile.LUM_SCALE_EXP), bright / sun, 1e-9);
    }

    @Test
    void eccentricityIsBoundedAndMostlyCircularButBimodal() {
        int eccentric = 0;
        int total = 0;
        for (long s = 0; s < 600; s++) {
            for (int o = 0; o < 4; o++) {
                OrbitProfile orbit = OrbitProfile.forSlot(s * 17 + o, o);
                assertTrue(orbit.eccentricity() >= 0.0 && orbit.eccentricity() <= 0.25,
                        "eccentricity out of range: " + orbit.eccentricity());
                total++;
                if (orbit.eccentricity() > 0.06) eccentric++;
            }
        }
        double share = eccentric / (double) total;
        assertTrue(share > 0.10 && share < 0.60,
                "eccentric orbits must stay a minority but be clearly present: " + share);
    }

    @Test
    void fluxAveragingFactorIsBoundedBelowTwo() {
        for (double e = 0.0; e <= 0.25; e += 0.05) {
            OrbitProfile orbit = new OrbitProfile(1.0, e);
            double f = orbit.fluxAveragingFactor();
            assertTrue(f >= 1.0 && f < 2.0, "flux averaging factor must stay modest: " + f);
        }
    }

    @Test
    void orbitAndLayoutRadiusAreIndependentConcepts() {
        // The layout radius is galaxy units; the orbit is AU. They must not be conflated, so the
        // thermal model must only ever consume orbitAU (never PlanetPosition.radius).
        OrbitProfile orbit = OrbitProfile.forSlot(SEED, 3);
        assertTrue(orbit.orbitAU() < 100.0,
                "orbitAU must be an AU-scale distance, not a galaxy-unit layout radius");
    }
}
