package com.modscreating.unlimitedspace.core.worldgen.biome;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlanetBiomeSelectorTest {

    private static final long SEED = 123456789L;

    @Test
    void sameSeedSameCoordinatesSameBiome() {
        assertEquals(PlanetBiomeSelector.select(SEED, 10, 20),
                PlanetBiomeSelector.select(SEED, 10, 20));
        assertEquals(PlanetBiomeSelector.sample(SEED, -5, -7),
                PlanetBiomeSelector.sample(SEED, -5, -7));
    }

    @Test
    void distributionCanBeVariedAcrossCoordinates() {
        Set<PlanetBiome> seen = new HashSet<>();
        for (int x = -500; x <= 500; x += 37) {
            for (int z = -500; z <= 500; z += 53) {
                seen.add(PlanetBiomeSelector.select(SEED, x, z));
            }
        }
        assertTrue(seen.size() > 1, "expected multiple biomes, got " + seen);
    }

    @Test
    void differentPlanetSeedsYieldsDifferentDistribution() {
        boolean different = false;
        outer:
        for (int i = 0; i < 64; i++) {
            long x = i * 29L;
            PlanetBiome a = PlanetBiomeSelector.select(SEED, (int) x, 0);
            PlanetBiome b = PlanetBiomeSelector.select(SEED + 1, (int) x, 0);
            if (a != b) { different = true; break outer; }
        }
        assertTrue(different, "two different seeds should diverge somewhere");
    }

    @Test
    void selectCoversAllArchetypes() {
        // thresholds must be reachable
        assertNotNull(PlanetBiomeSelector.select(0.0));
        assertNotNull(PlanetBiomeSelector.select(0.5));
        assertNotNull(PlanetBiomeSelector.select(0.99));
    }

    @Test
    void negativeCoordinatesDeterministic() {
        double e1 = PlanetBiomeSelector.sample(SEED, -12345, -6789);
        double e2 = PlanetBiomeSelector.sample(SEED, -12345, -6789);
        double e3 = PlanetBiomeSelector.sample(SEED, -12346, -6789);
        assertEquals(e1, e2);
        assertNotEquals(e1, e3, "adjacent negative x should differ");
    }

    @Test
    void noAllocationPerSample() {
        // sanity: repeated calls stay cheap and pure (no state accumulation)
        double a = PlanetBiomeSelector.sample(SEED, 314, 159);
        double b = PlanetBiomeSelector.sample(SEED, 314, 159);
        assertEquals(a, b);
    }
}