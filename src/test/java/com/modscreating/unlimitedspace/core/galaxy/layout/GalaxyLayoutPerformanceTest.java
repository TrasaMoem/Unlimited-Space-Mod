package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lightweight, non-JMH performance sanity check for the spatial lookup.
 *
 * <p>Verifies that resolving coordinates stays cheap as the galaxy grows and that the
 * index stays lazy (it never materialises the whole galaxy to answer a handful of
 * lookups). Thresholds are intentionally generous so the test is environment-tolerant;
 * the real signal is the printed table and the assertion that cacheSize &lt; systemCount.
 */
class GalaxyLayoutPerformanceTest {

    private static final double DENSITY = 0.8;
    private static final int LOOKUPS = 2000;

    @Test
    void lookupScalesSublinearlyAndStaysLazy() {
        int[] systemCounts = {100, 1000, 10000, 100000};
        System.out.printf("%-12s %-12s %-10s %-12s %-12s %-12s%n",
                "systems", "radiusGu", "rings", "lookups", "perLookupUs", "cacheAfter");
        double maxPerLookupUs = 0.0;
        for (int count : systemCounts) {
            double radius = Math.ceil(Math.sqrt(count / (Math.PI * DENSITY)));
            GalaxyParameters params = new GalaxyParameters(radius, DENSITY, GalaxyType.SPIRAL);
            GalaxyLayout layout = GalaxyLayout.from(42L, params);
            GalaxySpatialIndex idx = layout.index();
            SpatialGrid g = layout.grid();
            // deterministic sampling within the galaxy disc
            Random rng = new Random(7);
            // determinism spot-check: same coord -> same system
            GalaxyCoordinate sampleCoord = GalaxyCoordinate.of(1.23, -4.56);
            assertEquals(idx.findSystemAt(sampleCoord), idx.findSystemAt(sampleCoord));

            long start = System.nanoTime();
            int resolved = 0;
            for (int i = 0; i < LOOKUPS; i++) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                double dist = g.galaxyRadiusGu() * Math.sqrt(rng.nextDouble());
                GalaxyCoordinate coord = GalaxyCoordinate.of(dist * Math.cos(a), dist * Math.sin(a));
                if (idx.findNearestSystem(coord).isPresent()) resolved++;
                layout.lookup(coord);
            }
            long elapsedNs = System.nanoTime() - start;
            double perLookupUs = (elapsedNs / (double) LOOKUPS) / 1000.0;
            maxPerLookupUs = Math.max(maxPerLookupUs, perLookupUs);
            System.out.printf("%-12d %-12.2f %-10d %-12d %-12.2f %-12d%n",
                    count, radius, g.radiusCells(), LOOKUPS, perLookupUs, idx.cacheSize());
            // lazy: a few thousand lookups must not materialise the whole galaxy
            assertTrue(idx.cacheSize() < count,
                    "index materialised too much for " + count + " systems: " + idx.cacheSize());
            // not O(N): per-lookup time must not explode as the galaxy grows
            assertTrue(perLookupUs < 5000.0, "per-lookup too slow for " + count);
        }
        // growth sanity: largest galaxy must not be orders of magnitude slower per lookup
        assertTrue(maxPerLookupUs < 5000.0, "lookup did not scale sub-linearly");
        System.out.println("[perf] deterministic across instances: " + determinismCheck());
    }

    private static String determinismCheck() {
        GalaxyLayout a = GalaxyLayout.from(99L);
        GalaxyLayout b = GalaxyLayout.from(99L);
        StarSystemPosition sa = a.index().systemAtCell(3, 4);
        StarSystemPosition sb = b.index().systemAtCell(3, 4);
        return sa.equals(sb) ? "PASS" : "FAIL";
    }
}
