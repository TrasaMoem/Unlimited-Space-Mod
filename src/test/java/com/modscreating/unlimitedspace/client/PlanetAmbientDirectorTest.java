package com.modscreating.unlimitedspace.client.ambient;

import com.modscreating.unlimitedspace.core.destination.ProofPlanet;
import com.modscreating.unlimitedspace.core.worldgen.fluids.AmbientEffect;
import com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 5A — Test suite for PlanetAmbientDirector and PlanetAmbientEnvironment.
 */
@Tag("audit")
class PlanetAmbientDirectorTest {

    private static final long SEED = ProofPlanet.CANONICAL_WORLD_SEED;
    private PlanetAmbientEnvironment env;

    @BeforeEach
    void setUp() {
        PlanetAmbientDirector.get().reset();
        PlanetAmbientEnvironment.clearCache();
        env = PlanetAmbientEnvironment.compute("planet/system_0000_planet_00/surface", SEED);
        assertNotNull(env, "Proof planet surface environment must compute");
    }

    @Test
    void tokenBucketCeilingEnforced() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        int spawns = 0;
        for (long t = 0; t < 1000; t++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.tick(t, false, false, 0.0, 0.0, env);
            if (cand != null) spawns++;
        }
        assertTrue(spawns <= 602, "Spawn count " + spawns + " must not exceed ceiling of 600 + initial capacity");
    }

    @Test
    void rateIsWithinTargetRangeOverTime() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        int spawns = 0;
        for (long t = 0; t < 1000; t++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.tick(t, false, false, 0.0, 0.0, env);
            if (cand != null) spawns++;
        }
        assertTrue(spawns >= 150, "Spawn count " + spawns + " should be active for dense planet (>150 per 1000t)");
    }

    @Test
    void noDebtAccumulationAcrossPauses() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        for (long t = 0; t < 50; t++) {
            director.tick(t, false, false, 0.0, 0.0, env);
        }

        for (long t = 50; t < 10050; t++) {
            assertNull(director.tick(t, true, false, 0.0, 0.0, env));
        }

        assertEquals(0.0, director.tokens(), 1e-6);

        PlanetAmbientDirector.SpawnCandidate firstResume = director.tick(10050, false, false, 0.0, 0.0, env);
        assertNull(firstResume, "First tick after resume should not spawn because tokens were reset to 0");
    }

    @Test
    void screenOpenGateSuppressesSpawns() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        for (long t = 0; t < 100; t++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.tick(t, true, false, 0.0, 0.0, env);
            assertNull(cand, "Screen open must completely gate ambient spawning");
        }
    }

    @Test
    void singleplayerPauseGateSuppressesSpawns() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        for (long t = 0; t < 100; t++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.tick(t, false, true, 0.0, 0.0, env);
            assertNull(cand, "Singleplayer pause must completely gate ambient spawning");
        }
    }

    @Test
    void vacuumAtmosphereSuppressed() {
        AtmosphereProfile vacuum = new AtmosphereProfile(
                0.05, 0.0, 0.0, AtmosphereProfile.ColorTemp.COOL, 1.0, 0.0, 0.0
        );
        assertTrue(PlanetAmbientDirector.isGated(false, false, vacuum));

        AtmosphereProfile lowDensity = new AtmosphereProfile(
                0.30, 0.01, 0.0, AtmosphereProfile.ColorTemp.COOL, 0.9, 0.0, 0.0
        );
        assertTrue(PlanetAmbientDirector.isGated(false, false, lowDensity));
    }

    @Test
    void resetClearsAllState() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        for (long t = 0; t < 50; t++) {
            director.tick(t, false, false, 100.0, 200.0, env);
        }
        assertNotNull(director.cachedContext());
        assertNotEquals(0.0, director.tokens());

        director.reset();
        assertEquals(0.0, director.tokens());
        assertNull(director.cachedContext());
        assertEquals(Integer.MIN_VALUE, director.cachedChunkX());
        assertEquals(Integer.MIN_VALUE, director.cachedChunkZ());
    }

    @Test
    void lodBandPickingDistribution() {
        int nearCount = 0;
        int midCount = 0;
        int farCount = 0;
        int total = 100_000;

        Random rng = new Random(42);
        for (int i = 0; i < total; i++) {
            PlanetAmbientDirector.LodBand band = PlanetAmbientDirector.pickBand(rng.nextDouble());
            switch (band) {
                case NEAR -> nearCount++;
                case MID -> midCount++;
                case FAR -> farCount++;
            }
        }

        double nearShare = (double) nearCount / total;
        double midShare = (double) midCount / total;
        double farShare = (double) farCount / total;

        assertEquals(0.40, nearShare, 0.015, "NEAR share should be ~40%");
        assertEquals(0.35, midShare, 0.015, "MID share should be ~35%");
        assertEquals(0.25, farShare, 0.015, "FAR share should be ~25%");
    }

    @Test
    void candidateDistanceRespectsBandAndDeadZones() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        director.setRandom(new Random(12345));

        for (int i = 0; i < 5000; i++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.selectCandidate(0.0, 0.0, AmbientEffect.ASH_EMBERS);
            double dist = Math.sqrt(cand.blockX() * cand.blockX() + cand.blockZ() * cand.blockZ());

            assertTrue(cand.offsetX() >= 0.0 && cand.offsetX() < 1.0);
            assertTrue(cand.offsetZ() >= 0.0 && cand.offsetZ() < 1.0);

            PlanetAmbientDirector.LodBand band = cand.band();
            assertTrue(dist >= band.minDistance() - 1.5, "Distance " + dist + " too close for band " + band);
            assertTrue(dist <= band.maxDistance() + 1.5, "Distance " + dist + " too far for band " + band);
            assertTrue(dist >= 6.5, "Candidate spawned too close to player: " + dist);
        }
    }

    @Test
    void coarseContextCachingChunkAndInterval() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        director.tick(0, false, false, 10.0, 10.0, env);
        assertEquals(0, director.cachedChunkX());
        assertEquals(0, director.cachedChunkZ());
        assertEquals(0, director.lastContextRecomputeTick());

        director.tick(10, false, false, 12.0, 14.0, env);
        assertEquals(0, director.lastContextRecomputeTick());

        director.tick(25, false, false, 12.0, 14.0, env);
        assertEquals(25, director.lastContextRecomputeTick());

        director.tick(26, false, false, 35.0, 14.0, env);
        assertEquals(2, director.cachedChunkX());
        assertEquals(26, director.lastContextRecomputeTick());
    }

    @Test
    void deterministicEnvironmentComputation() {
        PlanetAmbientEnvironment e1 = PlanetAmbientEnvironment.compute("planet/system_0000_planet_00/surface", SEED);
        PlanetAmbientEnvironment e2 = PlanetAmbientEnvironment.compute("planet/system_0000_planet_00/surface", SEED);
        assertNotNull(e1);
        assertNotNull(e2);
        assertEquals(e1.atmosphere(), e2.atmosphere());
        assertEquals(e1.provinceAt(50, 50), e2.provinceAt(50, 50));
    }

    @Test
    void environmentCacheKeySeedIsolation() {
        PlanetAmbientEnvironment.clearCache();
        long seedA = 1000L;
        long seedB = 2000L;
        String path = "planet/system_0000_planet_00/surface";

        PlanetAmbientEnvironment envA = PlanetAmbientEnvironment.getOrCompute(seedA, path);
        PlanetAmbientEnvironment envB = PlanetAmbientEnvironment.getOrCompute(seedB, path);

        assertEquals(2, PlanetAmbientEnvironment.cacheSize());
        assertNotNull(envA);
        assertNotNull(envB);
    }

    @Test
    void environmentCacheLruCapacityBound() {
        PlanetAmbientEnvironment.clearCache();
        assertEquals(0, PlanetAmbientEnvironment.cacheSize());

        for (int i = 0; i < 25; i++) {
            PlanetAmbientEnvironment.getOrCompute(100L + i, "planet/system_0000_planet_00/surface");
        }

        assertEquals(PlanetAmbientEnvironment.CACHE_CAPACITY, PlanetAmbientEnvironment.cacheSize());
    }

    @Test
    void clearCacheEmptiesStorage() {
        PlanetAmbientEnvironment.getOrCompute(SEED, "planet/system_0000_planet_00/surface");
        assertTrue(PlanetAmbientEnvironment.cacheSize() > 0);

        PlanetAmbientEnvironment.clearCache();
        assertEquals(0, PlanetAmbientEnvironment.cacheSize());
    }

    @Test
    void nonPlanetDimensionsReturnNull() {
        assertNull(PlanetAmbientEnvironment.compute("planet/system_0000_planet_00/orbit", SEED));
        assertNull(PlanetAmbientEnvironment.compute("space/system_0000", SEED));
        assertNull(PlanetAmbientEnvironment.compute("moon/system_0000_planet_00_moon_00/surface", SEED));
        assertNull(PlanetAmbientEnvironment.compute("invalid/path", SEED));
        assertNull(PlanetAmbientEnvironment.compute(null, SEED));
    }

    @Test
    void tickStabilityUnderHighLoad() {
        PlanetAmbientDirector director = new PlanetAmbientDirector();
        int totalSpawns = 0;
        for (long t = 0; t < 10_000; t++) {
            PlanetAmbientDirector.SpawnCandidate cand = director.tick(
                    t,
                    false,
                    false,
                    Math.sin(t * 0.05) * 200.0,
                    Math.cos(t * 0.05) * 200.0,
                    env
            );
            if (cand != null) totalSpawns++;
        }
        assertTrue(totalSpawns > 0, "Spawns must occur over 10000 ticks");
        assertTrue(totalSpawns <= 6002, "Spawn count must obey token budget");
    }
}
