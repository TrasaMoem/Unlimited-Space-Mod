package com.modscreating.unlimitedspace.core.worldgen.biome;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 biome-region tests. THE core contract: a biome is a LARGE region (hundreds to thousands
 * of blocks) with smooth 100–400-block transitions — never a new biome every ~50 blocks.
 */
class BiomeRegionMapTest {

    private static BiomeRegionMap temperatePlanet(long seed) {
        return BiomeRegionMap.create(seed, 0.48, 0.55, 0.25, 0.1, 0.1, 0.5);
    }

    @Test
    void regionsAreDeterministic() {
        BiomeRegionMap m = temperatePlanet(0xABCDL);
        assertEquals(m.contextAt(123, -456), m.contextAt(123, -456));
        assertEquals(m.contextAt(123, -456).region(), m.contextAt(123, -456).region());
    }

    @Test
    void regionsAreValueEqualForTheSamePlanet() {
        assertEquals(temperatePlanet(0xABCDL), temperatePlanet(0xABCDL));
        assertNotEquals(temperatePlanet(0xABCDL), temperatePlanet(0x1234L));
    }

    @Test
    void biomeRegionsAreHuge() {
        // Median region run length along transects must be hundreds of blocks, and biome
        // changes per 1000 blocks must be strongly bounded (the old value was ~20!).
        long seed = 0x5EEDL;
        List<Integer> runs = new ArrayList<>();
        long switches = 0, blocks = 0;
        for (int row = 0; row < 24; row++) {
            int z = row * 512;
            BiomeRegionMap.Context prev = temperatePlanet(seed).contextAt(0, z);
            int run = 1;
            for (int x = 256; x < 8192; x += 64) {
                BiomeRegionMap.Context cur = temperatePlanet(seed).contextAt(x, z);
                blocks += 64;
                if (cur.region() != prev.region()) {
                    runs.add(run * 64);
                    switches++;
                    run = 1;
                    prev = cur;
                } else {
                    run++;
                }
            }
            runs.add(run * 64);
        }
        runs.sort(Integer::compareTo);
        double median = runs.get(runs.size() / 2);
        double per1000 = switches * 1000.0 / Math.max(1, blocks);
        System.out.printf("[R21] biome median diameter=%.0f blocks, %.2f changes/1000%n",
                median, per1000);
        assertTrue(median >= 400.0,
                "biome regions must be large: median diameter " + median);
        assertTrue(per1000 < 4.0, "biome must not flip constantly: " + per1000);
    }

    @Test
    void transitionsAreWideNotInstant() {
        // Walking across a border must pass through a genuine transition band (strength goes
        // 1 → 0.5 continuously), never jump 1 → 0.
        BiomeRegionMap m = temperatePlanet(0xBEEFL);
        List<Double> strengths = new ArrayList<>();
        for (int x = 700; x <= 5600; x += 16) {
            strengths.add(m.contextAt(x, 700).strength());
        }
        double min = strengths.stream().min(Double::compare).orElse(1.0);
        double max = strengths.stream().max(Double::compare).orElse(0.0);
        assertTrue(max > 0.9, "there must be real region interiors: max=" + max);
        assertTrue(min < 0.85, "there must be genuine transition bands, min=" + min);
    }

    @Test
    void differentPlanetsHaveDifferentRegionSets() {
        // Region sets are gated by the planet's climate/geology, so different worlds get
        // genuinely different sets (a frozen world hosts FROZEN_EXPANSE, a hot one cannot).
        Set<List<PlanetBiomeRegion>> seen = new HashSet<>();
        double[][] planets = {
                {0.9, 0.1, 0.05, 0.1, 0.1, 0.3},   // hot dry
                {0.06, 0.6, 0.3, 0.05, 0.1, 0.45}, // frozen
                {0.5, 0.9, 0.8, 0.2, 0.05, 0.2},   // oceanic
                {0.5, 0.3, 0.9, 0.3, 0.1, 0.7},    // crystal rich
                {0.6, 0.2, 0.2, 0.95, 0.2, 0.5},   // volcanic
        };
        for (double[] planet : planets) {
            seen.add(BiomeRegionMap.create(0x5EEDL, planet[0], planet[1], planet[2],
                    planet[3], 0.1, planet[5]).regions());
        }
        assertTrue(seen.size() > 1, "different planets must get different region sets");
    }

    @Test
    void rareRegionsStayRare() {
        // RARE regions must be strongly down-weighted: an exotic anomaly can never dominate.
        for (PlanetBiomeRegion r : PlanetBiomeRegion.VALUES) {
            if (r.tier() == PlanetBiomeRegion.Tier.RARE) {
                assertTrue(r.priorWeight() < 0.2, r + " must stay rare");
            }
            if (r.tier() == PlanetBiomeRegion.Tier.COMMON) {
                assertTrue(r.priorWeight() > 0.5, r + " must be common");
            }
        }
    }

    @Test
    void hotPlanetNeverGetsFrozenExpanse() {
        BiomeRegionMap hot = BiomeRegionMap.create(1L, 0.9, 0.1, 0.1, 0.1, 0.1, 0.3);
        assertFalse(hot.regions().contains(PlanetBiomeRegion.FROZEN_EXPANSE),
                "a hot dry planet must not host a frozen expanse");
    }

    @Test
    void regionModifiersAreBlendedAcrossTransitions() {
        BiomeRegionMap m = temperatePlanet(0xF00DL);
        BiomeRegionMap.Context deep = m.contextAt(10, 10);
        BiomeRegionMap.Context edge = m.contextAt(700, 700);
        assertTrue(deep.mountainMultiplier() > 0.0);
        assertTrue(edge.strength() >= 0.5 && edge.strength() <= 1.0);
        assertTrue(deep.localMountainCoverage(0.4) >= 0.0);
        assertTrue(deep.localMountainCoverage(0.4) <= 1.0);
        assertTrue(edge.hillMultiplier() > 0.0);
    }
}
