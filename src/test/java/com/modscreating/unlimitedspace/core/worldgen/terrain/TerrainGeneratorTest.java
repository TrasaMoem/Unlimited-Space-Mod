package com.modscreating.unlimitedspace.core.worldgen.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainGeneratorTest {

    private static final long SEED = 424242L;

    @Test
    void heightIsDeterministic() {
        ValueNoiseTerrainGenerator g = new ValueNoiseTerrainGenerator(SEED, 60.0, 20.0, 0.02, 2);
        for (int x = -50; x <= 50; x += 7) {
            for (int z = -50; z <= 50; z += 7) {
                assertEquals(g.height(x, z), g.height(x, z), 0.0, "height unstable at " + x + "," + z);
            }
        }
    }

    @Test
    void heightStaysNearBaseHeightWithinAmplitude() {
        double base = 60.0, amp = 20.0;
        ValueNoiseTerrainGenerator g = new ValueNoiseTerrainGenerator(SEED, base, amp, 0.02, 1);
        for (int x = -200; x <= 200; x += 13) {
            for (int z = -200; z <= 200; z += 13) {
                double h = g.height(x, z);
                assertTrue(h >= base - amp - 1e-6 && h <= base + amp + 1e-6,
                        "height " + h + " out of [" + (base - amp) + "," + (base + amp) + "]");
            }
        }
    }

    @Test
    void terrainIsNotFlat() {
        ValueNoiseTerrainGenerator g = new ValueNoiseTerrainGenerator(SEED, 60.0, 20.0, 0.02, 1);
        double first = g.height(0, 0);
        boolean varied = false;
        for (int i = 1; i < 500 && !varied; i++) {
            if (Math.abs(g.height(i, i) - first) > 0.5) varied = true;
        }
        assertTrue(varied, "expected some variation across the terrain");
    }

    @Test
    void differentSeedsProduceDifferentTerrain() {
        ValueNoiseTerrainGenerator a = new ValueNoiseTerrainGenerator(SEED, 60.0, 20.0, 0.02, 1);
        ValueNoiseTerrainGenerator b = new ValueNoiseTerrainGenerator(SEED + 1, 60.0, 20.0, 0.02, 1);
        boolean different = false;
        for (int i = 0; i < 200 && !different; i++) {
            if (a.height(i, i) != b.height(i, i)) different = true;
        }
        assertTrue(different, "expected different terrain for different seeds");
    }

    @Test
    void moreOctavesStillDeterministic() {
        ValueNoiseTerrainGenerator g = new ValueNoiseTerrainGenerator(SEED, 64.0, 15.0, 0.03, 4);
        for (int x = -20; x <= 20; x += 5) {
            for (int z = -20; z <= 20; z += 5) {
                assertEquals(g.height(x, z), g.height(x, z), 0.0);
            }
        }
    }
}
