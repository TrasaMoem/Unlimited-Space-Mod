package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R13.13 — finite test-scope statistics. Pure domain tests: same WorldSeed + same configured
 * scope must yield identical statistics, sums must reconcile, and the scope must stay finite
 * (never materializing the whole potential galaxy).
 */
class TestGalaxyStatisticsTest {

    private static final long SEED_A = 0x5EEDCAFEL;
    private static final long SEED_B = 4242L;

    private TestGalaxyScope scope(int count) {
        return new TestGalaxyScope(count);
    }

    @Test
    void sameSeedSameScopeProducesIdenticalStatistics() {
        for (int n : new int[]{1, 8, 32}) {
            TestGalaxyStatistics a = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(n));
            TestGalaxyStatistics b = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(n));
            assertEquals(a, b, "statistics must be deterministic for same seed+scope, n=" + n);
        }
    }

    @Test
    void scopeControlsTheNumberOfSystemsResolved() {
        TestGalaxyStatistics s = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(16));
        assertEquals(16, s.systems(), "systems must equal the configured finite scope count");
    }

    @Test
    void differentScopeSizesAreFiniteAndMonotonicInSystems() {
        TestGalaxyStatistics small = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(4));
        TestGalaxyStatistics large = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(64));
        assertEquals(4, small.systems());
        assertEquals(64, large.systems());
        assertTrue(large.planets() >= small.planets(),
                "larger finite scope covers at least as many planets");
    }

    @Test
    void differentWorldSeedMayProduceDifferentStatistics() {
        // Different seeds may differ; we only require both resolve and stay finite,
        // and that determinism per seed holds.
        TestGalaxyStatistics a = TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(48));
        TestGalaxyStatistics b = TestGalaxyStatistics.of(Galaxy.from(SEED_B), scope(48));
        assertEquals(a, TestGalaxyStatistics.of(Galaxy.from(SEED_A), scope(48)));
        assertEquals(b, TestGalaxyStatistics.of(Galaxy.from(SEED_B), scope(48)));
        // At least across a spread of seeds the totals are not all identical, proving seed input
        // actually flows into the aggregate rather than returning a constant.
        boolean allSame = true;
        TestGalaxyStatistics first = TestGalaxyStatistics.of(Galaxy.from(1L), scope(48));
        for (long seed = 2L; seed <= 40L; seed++) {
            TestGalaxyStatistics s = TestGalaxyStatistics.of(Galaxy.from(seed), scope(48));
            allSame &= s.stars() == first.stars()
                    && s.planets() == first.planets()
                    && s.moons() == first.moons()
                    && s.asteroidClusters() == first.asteroidClusters();
        }
        assertTrue(!allSame, "different WorldSeeds should produce different aggregates");
    }

    @Test
    void sumsAndBoundsHold() {
        TestGalaxyScope scope = scope(24);
        Galaxy galaxy = Galaxy.from(SEED_A);
        TestGalaxyStatistics s = TestGalaxyStatistics.of(galaxy, scope);

        assertTrue(s.systems() >= 0);
        assertTrue(s.stars() >= s.systems(), "stars >= systems (each system has >=1 star)");
        assertTrue(s.planets() >= 0);
        assertTrue(s.moons() >= 0);
        assertTrue(s.asteroidClusters() >= 0);

        // Recompute per-system and verify the sums reconcile exactly.
        int starSum = 0, planetSum = 0, moonSum = 0, asteroidSum = 0;
        for (int i = 0; i < scope.systemCount(); i++) {
            var c = galaxy.getStarSystem(galaxy.systemId(i)).counts();
            starSum += c.stars();
            planetSum += c.planets();
            moonSum += c.moons();
            asteroidSum += c.asteroidClusters();
        }
        assertEquals(starSum, s.stars(), "sum(starCount) == totalStars");
        assertEquals(planetSum, s.planets(), "sum(planetCount) == totalPlanets");
        assertEquals(moonSum, s.moons(), "sum(moonCount) == totalMoons");
        assertEquals(asteroidSum, s.asteroidClusters(), "sum(asteroidFieldCount) == totalAsteroidClusters");
    }

    @Test
    void scopeIsFiniteAndBounded() {
        // A finite scope never pretends to be the unbounded potential galaxy.
        TestGalaxyScope scope = TestGalaxyScope.defaults();
        assertEquals(TestGalaxyScope.DEFAULT_SYSTEM_COUNT, scope.systemCount());
        assertTrue(scope.contains(0));
        assertTrue(scope.contains(scope.systemCount() - 1));
        assertTrue(!scope.contains(scope.systemCount()));
        assertTrue(!scope.contains(-1));
    }
}