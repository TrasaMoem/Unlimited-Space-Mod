package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R17 terrain-shaper tests: determinism, bounds, spatial continuity (no vertical walls),
 * planet-to-planet variety.
 */
class TerrainShaperTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static PlanetGeologyProfile geology(long worldSeed, int system, int orbit) {
        Planet p = Galaxy.from(worldSeed).getStarSystem(Galaxy.from(worldSeed).systemId(system))
                .getPlanet(orbit);
        return PlanetGeologyProfile.create(p.seed().value(), p.properties());
    }

    private static TerrainShaper shaper(long worldSeed, int system, int orbit) {
        PlanetGeologyProfile g = geology(worldSeed, system, orbit);
        double baseHeight = g.physical().temperature() * 24.0 + 64.0;
        return TerrainShaper.create(null, g.provinces().provinceSeed(), g.physical(),
                g.provinces(), g.terrainSignature(), baseHeight, 24.0);
    }

    // ------------------------------------------------------------- determinism

    @Test
    void sameChunkCoordinatesSameTerrain() {
        TerrainShaper sh = shaper(WORLD_SEED, 0, 0);
        for (int x = -256; x <= 256; x += 11) {
            for (int z = -256; z <= 256; z += 13) {
                assertEquals(sh.surfaceHeight(x, z), sh.surfaceHeight(x, z),
                        "terrain must be a pure function of (seed, x, z)");
            }
        }
    }

    @Test
    void terrainRespectsHeightBounds() {
        TerrainShaper sh = shaper(WORLD_SEED, 0, 0);
        double min = 2000, max = -2000;
        for (int x = -1024; x <= 1024; x += 7) {
            for (int z = -1024; z <= 1024; z += 7) {
                int h = sh.surfaceHeight(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        // The shaper clamps to [base - 3A, base + 3.5A]; sanity-check a sane spread.
        assertTrue(max - min > 10, "terrain must have meaningful relief (span " + (max - min) + ")");
        assertTrue(min >= -40 && max <= 240, "terrain must stay within world bounds (min=" + min + " max=" + max + ")");
    }

    @Test
    void terrainIsSpatiallyContinuousNoWalls() {
        TerrainShaper sh = shaper(WORLD_SEED, 0, 0);
        for (int x = -512; x < 512; x += 9) {
            int a = sh.surfaceHeight(x, 0);
            int b = sh.surfaceHeight(x + 1, 0);
            int gap = Math.abs(a - b);
            assertTrue(gap <= 60,
                    "adjacent columns must not form a vertical wall (gap " + gap + " at x=" + x + ")");
        }
    }

    @Test
    void differentPlanetsProduceDifferentTerrain() {
        TerrainShaper a = shaper(WORLD_SEED, 0, 0);
        TerrainShaper b = shaper(WORLD_SEED, 0, 1);
        boolean differ = false;
        outer:
        for (int x = 0; x < 480; x += 17) {
            for (int z = 0; z < 480; z += 19) {
                if (a.surfaceHeight(x, z) != b.surfaceHeight(x, z)) {
                    differ = true;
                    break outer;
                }
            }
        }
        assertTrue(differ, "two distinct planets must be able to produce different terrain");
    }

    @Test
    void signatureSurvivesThroughProfile() {
        PlanetGeologyProfile g = geology(WORLD_SEED, 0, 0);
        assertNotNull(g.terrainSignature(), "geology profile must expose the terrain signature");
        TerrainSignature sig = g.terrainSignature();
        assertTrue(sig.amplitudeMul() > 0.0, "amplitude multiplier must be positive");
    }

    @Test
    void lavaChannelIsNegativeAndWithinBounds() {
        // R18: lava channels only ever LOWER terrain (negative-only), and never exceed the
        // shaper's bounds — a strong volcanic carve must still resolve to a valid world Y.
        long seed = 123456L;
        double strength = 1.0, amp = 24.0;
        double minSeen = 0, maxSeen = 0;
        for (int x = -512; x <= 512; x += 7) {
            for (int z = -512; z <= 512; z += 7) {
                double carve = TerrainFields.lavaChannel(seed, x, z, strength, amp);
                assertTrue(carve <= 0.0 + 1.0e-9,
                        "lava channel must only carve downward (negative), got " + carve);
                minSeen = Math.min(minSeen, carve);
                maxSeen = Math.max(maxSeen, carve);
            }
        }
        assertTrue(maxSeen <= 0.0, "carve must never raise terrain");
        assertTrue(minSeen >= -amp, "carve must never punch through the terrain bounds");
    }

    @Test
    void volcanicShaperStaysInHeightBounds() {
        // A volcanically-driven planet the shaper builds must still respect world bounds.
        PlanetGeologyProfile g = geology(WORLD_SEED, 0, 0);
        TerrainShaper sh = shaper(WORLD_SEED, 0, 0);
        for (int x = -256; x <= 256; x += 9) {
            for (int z = -256; z <= 256; z += 9) {
                int h = sh.surfaceHeight(x, z);
                assertTrue(h >= -40 && h <= 240, "shaped height out of bounds: " + h);
            }
        }
    }
}
