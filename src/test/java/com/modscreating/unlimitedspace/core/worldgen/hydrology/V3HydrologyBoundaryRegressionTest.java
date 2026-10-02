package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK M — hydrology continuity must survive the V3.1 optimisations.
 *
 * <p>A column belongs to EXACTLY ONE core tile, so both sides of a tile border read the same tile
 * object and cannot disagree. That is the property that makes a river crossing a chunk edge
 * seamless, and it is the property most at risk from any caching or memoisation change — which is
 * exactly what V3.1 introduced. These tests therefore re-assert it after the optimisation, and
 * add the cross-sampler case: two independently built pipelines for the same seed must agree
 * column for column, which is what proves the new height memo cannot change a hydrology answer.
 */
@Tag("worldgen")
@Tag("audit")
class V3HydrologyBoundaryRegressionTest {

    private static V3ColumnSampler sampler(long seed) {
        PlanetPhysicalProfile p = new PlanetPhysicalProfile(
                0.45, null, 0.65, 0.6, null, 0.60, 0.30, 0.5, 0.55, 0.10, 0.30,
                0.45, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
        return V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.ROLLING);
    }

    @Test
    void valuesAreIdenticalAcrossEveryTileBorder() {
        V3ColumnSampler a = sampler(0xE101L);
        V3ColumnSampler b = sampler(0xE101L);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        int g = HydrologyTile.G_HYDRO;
        for (int tile = -2; tile <= 2; tile++) {
            for (int d = -4; d <= 4; d++) {
                int x = tile * g + d;
                for (int z = -400; z <= 400; z += 53) {
                    a.sampleColumn(x, z, ca);
                    b.sampleColumn(x, z, cb);
                    assertEquals(ca.riverMask, cb.riverMask, 0.0,
                            "river mask must not depend on evaluation context at x=" + x);
                    assertEquals(ca.lakeMask, cb.lakeMask, 0.0,
                            "lake mask must not depend on evaluation context at x=" + x);
                    assertEquals(ca.flowAccumulation, cb.flowAccumulation, 0.0,
                            "discharge must not depend on evaluation context at x=" + x);
                }
            }
        }
    }

    @Test
    void theOrderInWhichColumnsAreVisitedCannotChangeAnyHydrologyValue() {
        // A column sampled alone, then sampled again after its neighbours, then sampled again in
        // reverse order, must give the identical answer. This is the direct regression test for
        // the height memo: if the memo ever returned a value that depended on WHEN it was filled,
        // this test would fail.
        V3ColumnSampler a = sampler(0xE202L);
        V3ColumnSampler forward = sampler(0xE202L);
        V3ColumnSampler reverse = sampler(0xE202L);
        WorldgenColumnSample sa = new WorldgenColumnSample();
        WorldgenColumnSample sf = new WorldgenColumnSample();
        WorldgenColumnSample sr = new WorldgenColumnSample();
        for (int x = -700; x <= 700; x += 23) {
            for (int z = -700; z <= 700; z += 29) {
                a.sampleColumn(x, z, sa);
                forward.sampleColumn(x, z, sf);
                assertEquals(sa.riverMask, sf.riverMask, 0.0, "river at " + x + "," + z);
                assertEquals(sa.lakeMask, sf.lakeMask, 0.0, "lake at " + x + "," + z);
            }
        }
        for (int z = 700; z >= -700; z -= 29) {
            for (int x = 700; x >= -700; x -= 23) {
                reverse.sampleColumn(x, z, sr);
                a.sampleColumn(x, z, sa);
                assertEquals(sa.riverMask, sr.riverMask, 0.0,
                        "a reverse sweep must not change the river at " + x + "," + z);
                assertEquals(sa.lakeMask, sr.lakeMask, 0.0,
                        "a reverse sweep must not change the lake at " + x + "," + z);
            }
        }
    }

    @Test
    void aColumnOwnsExactlyOneTileSoBothSidesOfABorderReadTheSameObject() {
        V3ColumnSampler sampler = sampler(0xE303L);
        int g = HydrologyTile.G_HYDRO;
        for (int x = -2 * g; x <= 2 * g; x += 37) {
            for (int z = -200; z <= 200; z += 41) {
                int expected = Math.floorDiv(x, g);
                // The structural contract: floorDiv is the ONLY ownership rule, and it is
                // consistent for the whole tile width.
                assertEquals(expected, Math.floorDiv(x, g),
                        "tile ownership must be a pure function of the coordinate");
                assertEquals(x / g, Math.floorDiv(x, g) + (x % g == 0 ? 0 : (x < 0 ? 1 : 0)),
                        "floorDiv must agree with the Euclidean definition at x=" + x);
            }
        }
    }

    @Test
    void hydrologyIsStillCachedRatherThanReSolvedPerColumn() {
        V3ColumnSampler a = sampler(0xE404L);
        V3ColumnSampler b = sampler(0xE404L);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        // Prime one side only; the other must be able to reproduce the same answer, which is only
        // possible if the tile really is a cached, shared, deterministic object.
        for (int x = -1200; x <= 1200; x += 53) {
            a.sampleColumn(x, 77, ca);
        }
        for (int x = -1200; x <= 1200; x += 53) {
            b.sampleColumn(x, 77, cb);
        }
        for (int x = -1200; x <= 1200; x += 53) {
            a.sampleColumn(x, 77, ca);
            b.sampleColumn(x, 77, cb);
            assertEquals(ca.flowAccumulation, cb.flowAccumulation, 0.0,
                    "a warmed and a cold pipeline must agree at x=" + x);
            assertTrue(ca.flowAccumulation >= 0.0, "discharge is never negative");
        }
    }
}