package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R20 hierarchical-terrain tests: scale separation, smoothness, spatial coherence, bounds,
 * determinism. These encode the "planet must look like geography, not like noise" contract.
 */
class HierarchicalTerrainTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static PlanetGeologyProfile geology(long worldSeed, int system, int orbit) {
        Galaxy g = Galaxy.from(worldSeed);
        Planet p = g.getStarSystem(g.systemId(system)).getPlanet(orbit);
        return PlanetGeologyProfile.create(p.seed().value(), p.properties());
    }

    private static TerrainShaper shaper(long worldSeed, int system, int orbit) {
        PlanetGeologyProfile g = geology(worldSeed, system, orbit);
        double baseHeight = g.physical().temperature() * 24.0 + 64.0;
        return TerrainShaper.create(null, g.provinces().provinceSeed(), g.physical(),
                g.provinces(), g.terrainSignature(), baseHeight, 24.0);
    }

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double ocean, double tect, double volc,
                                                 double ero, double impact) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, ocean, 0.5, tect, volc, 0.3,
                ero, impact, 0.4, 0.3, 0.2, 0.3, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaperFor(long seed, PlanetPhysicalProfile p) {
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0);
    }

    @Test
    void terrainIsDeterministic() {
        TerrainShaper sh = shaper(WORLD_SEED, 0, 0);
        assertEquals(sh.surfaceHeight(123, -456), sh.surfaceHeight(123, -456));
        assertEquals(sh.sample(11, 22), sh.sample(11, 22));
    }

    @Test
    void terrainStaysInBounds() {
        TerrainShaper sh = shaper(WORLD_SEED, 0, 1);
        for (int x = -2048; x <= 2048; x += 13) {
            for (int z = -2048; z <= 2048; z += 17) {
                int h = sh.surfaceHeight(x, z);
                assertTrue(h >= sh.minBound() && h <= sh.maxBound(),
                        "height out of shaper bounds: " + h);
            }
        }
    }

    @Test
    void localDetailIsBounded() {
        // The tiny-detail layer may contribute at most a small fraction of the amplitude
        // budget: removing it must leave the geography intact.
        for (int orbit = 0; orbit < 4; orbit++) {
            TerrainShaper sh = shaper(WORLD_SEED, 0, orbit);
            double bound = sh.sample(0, 0).localDetailAmplitude() * 2.0;
            double span = sh.maxBound() - sh.minBound();
            assertTrue(bound <= span * 0.15,
                    "local detail budget too large on orbit " + orbit + ": " + bound
                            + " (span " + span + ")");
        }
    }

    @Test
    void adjacentColumnsAreSmooth() {
        // The old "potato field" bug: adjacent columns differed by multiple blocks everywhere.
        // New contract: mean |Δh| between adjacent columns stays small (details only), and no
        // single column may ever form a wall — province borders, feature thresholds and cell
        // neighbourhood scans are all continuous by construction.
        for (long worldSeed : new long[]{WORLD_SEED, 0xABCDEF01L}) {
            for (int orbit = 0; orbit < 3; orbit++) {
                TerrainShaper sh = shaper(worldSeed, 0, orbit);
                double sum = 0;
                int n = 0, worst = 0;
                for (int x = -800; x < 800; x += 3) {
                    for (int z = -800; z < 800; z += 3) {
                        int h = sh.surfaceHeight(x, z);
                        int gapX = Math.abs(h - sh.surfaceHeight(x + 1, z));
                        int gapZ = Math.abs(h - sh.surfaceHeight(x, z + 1));
                        sum += 0.5 * (gapX + gapZ);
                        worst = Math.max(worst, Math.max(gapX, gapZ));
                        n++;
                    }
                }
                double mean = sum / n;
                assertTrue(mean < 2.0, "mean adjacent-column slope too rough: " + mean
                        + " (seed " + worldSeed + " orbit " + orbit + ")");
                assertTrue(worst <= 18, "single-column wall detected: " + worst
                        + " (seed " + worldSeed + " orbit " + orbit + ")");
            }
        }
    }

    @Test
    void macroVarianceDominatesLocalVariance() {
        // Spatial autocorrelation: heights over 64-block steps must change much less than the
        // total geographic relief — large forms must dominate.
        TerrainShaper sh = shaper(WORLD_SEED, 0, 2);
        double macroDiff = 0;
        for (int i = 64; i < 4096; i += 64) {
            macroDiff += Math.abs(sh.surfaceHeight(i, 0) - sh.surfaceHeight(i - 64, 0));
        }
        macroDiff /= (4096 / 64 - 1);
        double relief = sh.maxBound() - sh.minBound();
        assertTrue(macroDiff < relief * 0.35,
                "64-block steps change height too much relative to relief: " + macroDiff
                        + " vs relief " + relief);
    }

    @Test
    void mountainRangesAreSpatiallyCoherent() {
        // High-tectonic world: heights along a transect must correlate over hundreds of blocks
        // (long chains), not decorrelate within tens of blocks.
        PlanetPhysicalProfile p = profile(0.4, 0.3, 0.1, 0.05, 0.95, 0.1, 0.15, 0.05);
        TerrainShaper sh = shaperFor(7777L, p);
        double near = 0, far = 0;
        int n = 0;
        for (int i = 0; i < 4096; i += 8) {
            int h = sh.surfaceHeight(i, 0);
            near += Math.abs(h - sh.surfaceHeight(i + 48, 0));
            far += Math.abs(h - sh.surfaceHeight(i + 1024, 0));
            n++;
        }
        near /= n;
        far /= n;
        assertTrue(near < far, "near-correlation (" + near + ") must beat far (" + far + ")");
        assertTrue(near < 12.0, "48-block height change too abrupt: " + near);
    }

    @Test
    void oceanWorldHasLargeLowRegions() {
        // OCEAN bias: a big share of the map must sit BELOW the base height (global geography).
        PlanetPhysicalProfile p = profile(0.5, 0.9, 0.9, 0.85, 0.2, 0.05, 0.4, 0.05);
        TerrainShaper sh = shaperFor(31415L, p);
        int below = 0, total = 0;
        for (int x = -1500; x <= 1500; x += 60) {
            for (int z = -1500; z <= 1500; z += 60) {
                if (sh.surfaceHeight(x, z) < 80.0) below++;
                total++;
            }
        }
        assertTrue(below > total * 0.30,
                "ocean world must have large low regions, got " + (double) below / total);
    }

    @Test
    void impactFrequencyDrivesCraterDensity() {
        PlanetPhysicalProfile quiet = profile(0.5, 0.4, 0.2, 0.1, 0.3, 0.1, 0.4, 0.02);
        PlanetPhysicalProfile battered = profile(0.5, 0.4, 0.2, 0.1, 0.3, 0.1, 0.4, 0.95);
        long seed = 8888L;
        TerrainShaper q = shaperFor(seed, quiet);
        TerrainShaper b = shaperFor(seed, battered);
        assertTrue(b.signature().effectiveCraterDensity()
                        > q.signature().effectiveCraterDensity() * 2.0,
                "impact frequency must drive crater density (q="
                        + q.signature().effectiveCraterDensity() + " b="
                        + b.signature().effectiveCraterDensity() + ")");
    }

    @Test
    void differentPlanetsProduceDifferentTerrain() {
        TerrainShaper a = shaper(WORLD_SEED, 0, 0);
        TerrainShaper b = shaper(WORLD_SEED, 0, 1);
        boolean differ = false;
        outer:
        for (int x = 0; x < 960; x += 17) {
            for (int z = 0; z < 960; z += 19) {
                if (a.surfaceHeight(x, z) != b.surfaceHeight(x, z)) {
                    differ = true;
                    break outer;
                }
            }
        }
        assertTrue(differ, "distinct planets must produce distinct terrain");
    }
}
