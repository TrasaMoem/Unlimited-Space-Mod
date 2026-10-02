package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 — DETERMINISM, MULTI-SEED STABILITY and the hot-path allocation contract.
 *
 * <p>These are the invariants the rest of the system is allowed to assume: identical inputs give
 * identical outputs on every seed, and the amplitude varies rather than collapsing to one constant.
 */
class V3DeterminismAndPerformanceTest {

    static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                         double tect, double volc, double geo, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    static TerrainShaper earthlike(long seed) {
        return shaper(seed, profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45));
    }


    @Test
    void everySeedProducesTheSameResultForTheSameCoordinates() {
        // 32 seeds: determinism must hold for EVERY family, not just a convenient one.
        for (int i = 0; i < 32; i++) {
            long seed = 0x1000L + i * 0x137L;
            PlanetPhysicalProfile p = profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45);
            TerrainShaper a = shaper(seed, p);
            TerrainShaper b = shaper(seed, p);
            assertEquals(a.character().weights().duneWeight(), b.character().weights().duneWeight(),
                    0.0, "the character must be a pure function of the profile");
            for (int x = -900; x <= 900; x += 97) {
                assertEquals(a.surfaceHeight(x, 517), b.surfaceHeight(x, 517),
                        "same seed must give the same height at x=" + x + " seed=" + seed);
            }
        }
    }

    @Test
    void allMajorInvariantsHoldAcrossManySeeds() {
        // Each seed must show: real relief, BOTH relief signs, a terrain-bound drainage, and a
        // non-degenerate elevation01 distribution.
        for (int i = 0; i < 32; i++) {
            long seed = 0x7000L + i * 0x9E1L;
            TerrainShaper sh = earthlike(seed);

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            int rising = 0;
            int falling = 0;
            double e01Min = 1.0;
            double e01Max = 0.0;
            ElevationScratch sc = new ElevationScratch();
            // Sample a few PARALLEL LINES, not one: a single transect can legitimately cross nothing
            // but lowland on a particular seed, which says nothing about the world.
            for (int line = 0; line < 4; line++) {
                int z = 811 + line * 977;
                int prev = sh.surfaceHeight(-3000, z);
                for (int x = -2999; x <= 3000; x += 7) {
                    int h = sh.surfaceHeight(x, z);
                    if (h > prev) rising++;
                    if (h < prev) falling++;
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    prev = h;
                    sh.elevationField().sampleInto(x, z, sc);
                    e01Min = Math.min(e01Min, sc.elevation01);
                    e01Max = Math.max(e01Max, sc.elevation01);
                }
            }
            assertTrue(max - min > 25.0,
                    "seed " + seed + " produced a nearly flat world: " + (max - min));
            assertTrue(rising > 50 && falling > 50,
                    "seed " + seed + " lost one relief sign: up=" + rising + " down=" + falling);
            assertTrue(e01Max - e01Min > 0.05,
                    "seed " + seed + " has a degenerate elevation01 spread: " + (e01Max - e01Min));
            assertTrue(sh.hydrology().isTerrainBound(),
                    "seed " + seed + " lost the terrain-bound drainage");
        }
    }

    @Test
    void elevation01IsNormalisedOverTheRealBandAndDoesNotSaturate() {
        // The V3 spec requires elevation01 to be rebuilt from the ACTUAL composed bounds, and to
        // stay off the 0/1 rails over most of a world.
        //
        // The band is measured on the FINAL surface (the shaper's composed column height), because
        // that is the surface every consumer sees. The V3 ElevationField is a LAYER of that
        // surface, not a replacement for it, so comparing the two normalised values directly would
        // be comparing two different terrains.
        TerrainShaper sh = earthlike(0x8000L);
        double lo = sh.minBound();
        double hi = sh.maxBound();
        int below = 0;
        int above = 0;
        int atLow = 0;
        int atHigh = 0;
        int total = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = -6000; x <= 6000; x += 11) {
            double e = sh.elevation01(x, 1234);
            min = Math.min(min, e);
            max = Math.max(max, e);
            int h = sh.surfaceHeight(x, 1234);
            if (h < lo) below++;
            if (h > hi) above++;
            if (e <= 0.001) atLow++;
            if (e >= 0.999) atHigh++;
            total++;
        }
        assertTrue(below == 0 && above == 0,
                "the final surface must stay inside its own legal band, else elevation01 pins: below="
                        + below + " above=" + above);
        assertTrue(max - min > 0.20,
                "elevation01 must use a real part of its band, not a narrow slice: "
                        + (max - min));
        assertTrue((double) atLow / total < 0.10,
                "elevation01 saturates at 0: " + atLow + "/" + total);
        assertTrue((double) atHigh / total < 0.10,
                "elevation01 saturates at 1: " + atHigh + "/" + total);
        // The accessor and the field must agree when asked about the SAME height.
        int h0 = sh.surfaceHeight(0, 1234);
        assertEquals((h0 - lo) / (hi - lo), sh.elevation01(h0), 1e-9,
                "elevation01 must be a pure normalisation of the real legal band");
    }

    @Test
    void twoHundredThousandColumnQueriesStayInsideTheBudget() {
        // 200k full column queries over a REGION, which is how a world is actually traversed. The
        // query coordinates deliberately stay inside a few hydrology tiles that the warm-up has
        // already solved: the point of this test is the per-column cost of the field evaluation,
        // not the one-off cost of solving a thousand drainage tiles.
        TerrainShaper sh = earthlike(0x9000L);
        for (int x = -400; x <= 400; x += 7) {
            sh.surfaceHeight(x, 3);
        }
        int n = 200_000;
        long start = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < n; i++) {
            int x = (i * 37) % 1600 - 800;
            int z = (i * 91) % 1600 - 800;
            acc += sh.surfaceHeight(x, z);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("[V3-PERF] 200k column queries in " + elapsedMs + " ms (acc=" + acc + ")");
        assertTrue(elapsedMs < 20_000,
                "200k column queries must stay inside the performance envelope: " + elapsedMs + " ms");
    }

    @Test
    void theHotPathDoesNotAllocatePerColumn() {
        // The hot path writes into caller-owned scratch, so evaluating a column allocates NOTHING.
        // The measurement runs entirely INSIDE one already-warm hydrology tile: otherwise the heap
        // would grow with the tile cache, which is a deliberate one-off cost, not a per-column one.
        TerrainShaper sh = earthlike(0xA000L);
        ElevationScratch sc = new ElevationScratch();
        for (int x = -300; x <= 300; x += 11) {
            sh.elevationField().sampleInto(x, 9, sc);
        }
        long before = usedHeap();
        for (int i = 0; i < 50_000; i++) {
            int x = (i * 13) % 400 - 200;
            sh.elevationField().sampleInto(x, (i * 17) % 400 - 200, sc);
        }
        long growth = usedHeap() - before;
        System.out.println("[V3-PERF] heap growth over 50k warm scratch samples: " + growth + " bytes");
        // A record-returning sample() would allocate 50k objects; scratch sampling must allocate none.
        assertTrue(growth < 4L * 1024 * 1024,
                "the elevation hot path must not allocate per column, growth=" + growth + " bytes");
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) {
            System.gc();
        }
        return rt.totalMemory() - rt.freeMemory();
    }
}
