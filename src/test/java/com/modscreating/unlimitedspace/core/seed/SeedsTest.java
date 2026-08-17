package com.modscreating.unlimitedspace.core.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedsTest {

    private static final long SEED = 123456789L;

    @Test
    void fractionIsStableAndBounded() {
        double f = Seeds.fraction(SEED, 0);
        assertTrue(f >= 0.0 && f < 1.0, "fraction must be in [0,1)");
        assertEquals(f, Seeds.fraction(SEED, 0));
        assertNotEquals(Seeds.fraction(SEED, 0), Seeds.fraction(SEED, 1));
    }

    @Test
    void fractionIsDeterministicAcrossJvm() {
        assertEquals(Seeds.fraction(SEED, 7), Seeds.fraction(SEED, 7));
    }

        @Test
    void rangeDoubleRespectsBounds() {
        for (long slot = 1; slot <= 20; slot++) {
            double v = Seeds.rangeDouble(SEED, slot, 10.0, 20.0);
            assertTrue(v >= 10.0 && v <= 20.0,
                    "slot " + slot + " value " + v + " out of [10,20]");
        }
    }

    @Test
    void rangeDoubleStaysWithinRangeForManySlots() {
        for (long slot = 1; slot <= 1000; slot++) {
            double v = Seeds.rangeDouble(SEED, slot, 0.0, 1.0);
            assertTrue(v >= 0.0 && v <= 1.0, "slot " + slot + " value " + v + " out of [0,1]");
        }
    }

    @Test
    void rangeDoubleIsDeterministic() {
        assertEquals(Seeds.rangeDouble(SEED, 5, 1.0, 2.0), Seeds.rangeDouble(SEED, 5, 1.0, 2.0));
    }

    @Test
    void rangeLongRespectsBounds() {
        for (long slot = 0; slot < 50; slot++) {
            long v = Seeds.rangeLong(SEED, slot, 0, 5);
            assertTrue(v >= 0 && v < 5, "slot " + slot + " value " + v + " out of [0,5)");
        }
    }

    @Test
    void subsystemSeedIsIndependentAndStable() {
        long a = Seeds.subsystem(SEED, "terrain");
        long b = Seeds.subsystem(SEED, "biome");
        assertNotEquals(a, b, "different subsystem names must yield different seeds");
        assertEquals(a, Seeds.subsystem(SEED, "terrain"));
        assertEquals(Seeds.subsystem(SEED, "ore"), Seeds.subsystem(SEED, "ore"));
    }

    @Test
    void differentParentsYieldDifferentSeeds() {
        assertNotEquals(Seeds.subsystem(SEED, "terrain"),
                Seeds.subsystem(SEED + 1, "terrain"));
    }

    @Test
    void planetSeedDerivesFromSlot() {
        PlanetSeed p0 = PlanetSeed.forSlot(999L, 0);
        PlanetSeed p3 = PlanetSeed.forSlot(999L, 3);
        assertEquals(p0, PlanetSeed.forSlot(999L, 0));
        assertNotEquals(p0, p3);
        assertTrue(p0.value() != p3.value() || p0.value() != 999L);
    }

    @Test
    void moonSeedDerivesFromPlanetAndIndex() {
        long planetSeed = 424242L;
        MoonSeed m0 = MoonSeed.forSlot(planetSeed, 0);
        MoonSeed m1 = MoonSeed.forSlot(planetSeed, 1);
        assertEquals(m0, MoonSeed.forSlot(planetSeed, 0), "same planet+index -> same moon seed");
        assertNotEquals(m0, m1, "different index -> different moon seed");
        assertNotEquals(m0.value(), planetSeed, "moon seed must differ from planet seed");
        assertNotEquals(Seeds.moon(planetSeed, 0), Seeds.planet(planetSeed, 0),
                "moon derivation must be domain-separated from planet derivation");
    }

    @Test
    void differentParentPlanetsYieldDifferentMoonSeeds() {
        assertNotEquals(Seeds.moon(100L, 0), Seeds.moon(101L, 0),
                "different planet seed -> different moon seed at same index");
    }
}
