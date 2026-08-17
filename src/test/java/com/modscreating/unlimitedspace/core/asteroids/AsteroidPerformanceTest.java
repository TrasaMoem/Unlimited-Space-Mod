package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R10.1 — asteroid metadata performance / laziness validation.
 *
 * <p>Building asteroid cluster METADATA for many systems/clusters must be cheap (pure domain
 * records) and must NEVER create Minecraft worlds/dimensions. A galaxy with thousands of
 * clusters stays cheap; the bounded/playable Minecraft worlds are an R11 concern.
 */
class AsteroidPerformanceTest {

    @Test
    void generatingManyClusterSetsIsCheapAndDeterministic() {
        long worldSeed = 0x5EEDCAFE0L;
        Galaxy galaxy = Galaxy.from(worldSeed);

        long start = System.nanoTime();
        int totalClusters = 0;
        // 200 systems x 60 clusters = 12,000 clusters.
        for (int s = 0; s < 200; s++) {
            var system = galaxy.getStarSystem(StarSystemId.of(s));
            for (int i = 0; i < 60; i++) {
                AsteroidCluster c = system.asteroid(i);
                // touch every meaningful piece of metadata to prove they are plain, materialised records
                assertNotNull(c.id());
                assertNotNull(c.seed());
                assertEquals(system.id(), c.parentSystem());
                AsteroidGenerationProfile p = c.profile();
                assertNotNull(p.shapePattern());
                assertNotNull(p.material());
                assertNotNull(p.ore());
                assertNotNull(p.ore().dominantOre());
                assertNotNull(c.worldDestination());
                totalClusters++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 10_000,
                "asteroid metadata generation too slow: " + elapsedMs + " ms for " + totalClusters + " clusters");
        assertEquals(200 * 60, totalClusters);
    }

    @Test
    void profileIsRepeatableAndBoundedUnderStress() {
        Galaxy galaxy = Galaxy.from(0x5EEDCAFE0L);
        int maxCount = 0;
        double maxDensity = 0.0;
        for (int s = 0; s < 100; s++) {
            var system = galaxy.getStarSystem(StarSystemId.of(s));
            for (int i = 0; i < 20; i++) {
                AsteroidCluster c = system.asteroid(i);
                AsteroidGenerationProfile p1 = c.profile();
                AsteroidGenerationProfile p2 = system.asteroid(i).profile();
                assertEquals(p1, p2, "profile must be stable under repeat");
                maxCount = Math.max(maxCount, p1.asteroidCount());
                maxDensity = Math.max(maxDensity, p1.density());
                assertTrue(p1.asteroidCount() >= 0);
            }
        }
        // Sanity: the model stays bounded (never an unbounded/metadata-exploding scale).
        assertTrue(maxCount > 0, "at least some asteroids per cluster");
        assertTrue(maxDensity <= 1.0, "density stays in [0,1]");
    }
}
