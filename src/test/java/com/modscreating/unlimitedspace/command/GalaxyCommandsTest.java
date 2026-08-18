package com.modscreating.unlimitedspace.command;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.SystemPathIndex;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SystemPathIndex} current-system resolution and for the
 * {@link StarSystem#counts()} aggregate used by {@code /unlimitedspace system}.
 * Pure domain tests only — they never claim client rendering works.
 */
class GalaxyCommandsTest {

    private static final long WORLD_SEED = 1337L;

    @Test
    void currentSystemIndexParsesPlanetMoonAsteroidPaths() {
        assertEquals(0, SystemPathIndex.fromDimensionPath("planet/system_0000_planet_01/orbit"));
        assertEquals(0, SystemPathIndex.fromDimensionPath("moon/system_0000_planet_00_moon_00/orbit"));
        assertEquals(0, SystemPathIndex.fromDimensionPath("asteroid/system_0000_asteroid_00"));
        assertEquals(7, SystemPathIndex.fromDimensionPath("planet/system_0007_planet_02/surface"));
    }

    @Test
    void currentSystemIndexFallsBackToZeroWhenNoSystemMapped() {
        assertEquals(0, SystemPathIndex.fromDimensionPath("overworld"));
        assertEquals(0, SystemPathIndex.fromDimensionPath("space"));
        assertEquals(0, SystemPathIndex.fromDimensionPath(null));
        assertEquals(0, SystemPathIndex.fromDimensionPath(""));
    }

    @Test
    void countsStarsMatchDomainList() {
        for (int s = 0; s < 4; s++) {
            StarSystem system = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(s));
            StarSystem.SystemCounts c = system.counts();
            assertEquals(system.stars().size(), c.stars(), "stars() must match domain");
            assertTrue(system.stars().size() >= 1 && system.stars().size() <= 3,
                    "star multiplicity 1..3");
        }
    }

    @Test
    void countsPlanetsAreBoundedAndMoonsAreThePlanetSum() {
        for (int s = 0; s < 4; s++) {
            StarSystem system = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(s));
            StarSystem.SystemCounts c = system.counts();
            assertTrue(c.planets() >= 1 && c.planets() <= 6, "planets in [1,6]");
            assertTrue(c.asteroidClusters() >= 1 && c.asteroidClusters() <= 6,
                    "asteroid clusters in [1,6]");
            int expectedMoons = 0;
            for (int o = 0; o < c.planets(); o++) {
                expectedMoons += system.getPlanet(o).moonCount();
            }
            assertEquals(expectedMoons, c.moons(), "moons must equal sum of planet moons");
        }
    }

    @Test
    void sameWorldSeedProducesIdenticalCounts() {
        for (int s = 0; s < 4; s++) {
            Galaxy g = Galaxy.from(WORLD_SEED);
            StarSystem a = g.getStarSystem(StarSystemId.of(s));
            StarSystem b = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(s));
            assertEquals(a.counts(), b.counts(), "counts must be deterministic per seed");
        }
    }

    @Test
    void differentSystemIdsResolveDeterministically() {
        // Different system ids yield distinct seeds; recomputation must be stable even if
        // the exact counts coincidentally coincide, and stars() must match the domain list.
        for (int s = 0; s < 3; s++) {
            Galaxy g = Galaxy.from(WORLD_SEED);
            StarSystem system = g.getStarSystem(StarSystemId.of(s));
            StarSystem.SystemCounts c = system.counts();
            assertEquals(system.stars().size(), c.stars());
            assertEquals(c, system.counts(), "repeated count must be identical");
        }
    }
}