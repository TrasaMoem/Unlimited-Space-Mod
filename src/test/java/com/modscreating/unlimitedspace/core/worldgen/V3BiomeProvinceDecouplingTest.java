package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK D — the visible biome map must NOT be a repainted province map.
 *
 * <p>This is the hard diagnostic {@code biome_vs_macro_province_correlation}. The failure it guards
 * against is invisible to any value assertion: a classifier that secretly switched on the province
 * would produce perfectly plausible biomes that simply happened to have polygon borders.
 *
 * <p>The sweep is TWO-DIMENSIONAL on purpose. A single transect can legitimately stay inside one
 * Voronoi cell for its whole length, which would make the "transitions across a boundary" check
 * vacuous; a grid genuinely crosses the macro geometry.
 */
@Tag("worldgen")
@Tag("audit")
class V3BiomeProvinceDecouplingTest {

    /**
     * The agreement ceiling.
     *
     * <p>The macro affinity is deliberately small (see {@link BiomeMaskField#SOFT_MACRO_MAX}), so
     * the elected biome coincides with the dominant geology on only a MINORITY of columns. A
     * threshold of 0.60 is far above what a +-20% soft nudge can reach on a real field stack, and
     * far below the ~1.0 a province switch would produce.
     */
    private static final double MAX_AGREEMENT = 0.60;

    @Test
    void theBiomeMapIsNotAProvinceMap() {
        int totalInside = 0;
        int totalAcross = 0;
        double agreementSum = 0.0;
        int seeds = 0;
        for (long seed : new long[]{0xC100L, 0xC101L, 0xC102L, 0xC103L, 0xC104L, 0xC105L}) {
            V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(seed);
            WorldgenColumnSample col = new WorldgenColumnSample();
            Map<String, Integer> pairs = new HashMap<>();
            int inside = 0;
            int across = 0;
            int n = 0;
            int step = 64;
            int prevBiome = Integer.MIN_VALUE;
            int prevProv = Integer.MIN_VALUE;
            // 2-D sweep: a grid really does cross the macro Voronoi geometry.
            for (int z = -1600; z <= 1600; z += step) {
                for (int x = -1600; x <= 1600; x += step) {
                    sampler.sampleColumn(x, z, col);
                    int prov = col.dominantGeology() == null ? -1 : col.dominantGeology().ordinal();
                    int biome = col.biome.catalogueIndex();
                    pairs.merge(biome + "|" + prov, 1, Integer::sum);
                    if (prevBiome != Integer.MIN_VALUE && biome != prevBiome) {
                        if (prov == prevProv) inside++;
                        else across++;
                    }
                    prevBiome = biome;
                    prevProv = prov;
                    n++;
                }
                prevBiome = Integer.MIN_VALUE;
            }
            int agree = 0;
            var catalogue = BiomeMaskField.defaultCandidates();
            for (Map.Entry<String, Integer> e : pairs.entrySet()) {
                String[] parts = e.getKey().split("\\|");
                int idx = Integer.parseInt(parts[0]);
                int provOrdinal = Integer.parseInt(parts[1]);
                if (idx >= 0 && idx < catalogue.size()) {
                    GeologicalProvince pref = catalogue.get(idx).preferredGeology();
                    if (pref != null && pref.ordinal() == provOrdinal) agree += e.getValue();
                }
            }
            double agreement = (double) agree / Math.max(1, n);
            assertTrue(agreement < MAX_AGREEMENT,
                    "the biome map reproduces province ownership on seed " + seed
                            + ": agreement=" + agreement + " (limit " + MAX_AGREEMENT + ")");
            assertTrue(inside > 0,
                    "biomes must be able to change INSIDE one macro province (seed " + seed + ")");
            assertTrue(across > 0,
                    "biomes must also be able to change ACROSS a macro boundary (seed " + seed + ")");
            agreementSum += agreement;
            totalInside += inside;
            totalAcross += across;
            seeds++;
        }
        assertTrue(seeds == 6);
        double meanAgreement = agreementSum / seeds;
        System.out.println("[V3.1] mean province/biome agreement = " + meanAgreement
                + ", transitions inside=" + totalInside + " across=" + totalAcross);
        assertTrue(totalInside > 0 && totalAcross > 0);
    }

    @Test
    void biomesChangeAlongElevationMoistureAndRiversWithoutAMacroBorder() {
        // The qualitative invariant of Task D: nearby changes create LOCAL biome transitions.
        V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(0xC200L);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int changes = 0;
        int changesWithSameProvince = 0;
        String prev = null;
        int prevProv = -1;
        double minElev = 2.0, maxElev = -1.0;
        double minHum = 2.0, maxHum = -1.0;
        double maxSlope = 0.0;
        int riverColumns = 0;
        for (int x = -2000; x <= 2000; x += 7) {
            sampler.sampleColumn(x, 733, col);
            minElev = Math.min(minElev, col.elevation01);
            maxElev = Math.max(maxElev, col.elevation01);
            minHum = Math.min(minHum, col.humidity01);
            maxHum = Math.max(maxHum, col.humidity01);
            maxSlope = Math.max(maxSlope, col.slope);
            if (col.riverMask > 0.01) riverColumns++;
            int prov = col.dominantGeology() == null ? -1 : col.dominantGeology().ordinal();
            if (prev != null && !prev.equals(col.biome.id())) {
                changes++;
                if (prov == prevProv) changesWithSameProvince++;
            }
            prev = col.biome.id();
            prevProv = prov;
        }
        assertTrue(maxElev - minElev > 0.15,
                "the sweep must really cross an elevation range, got " + (maxElev - minElev));
        assertTrue(maxHum - minHum > 0.10,
                "the sweep must really cross a moisture range, got " + (maxHum - minHum));
        assertTrue(maxSlope > 0.0, "the sweep must really cross a slope range");
        assertTrue(riverColumns > 0, "the sweep must really cross a river");
        assertTrue(changes > 0, "the biome map must change along the transect");
        assertTrue(changesWithSameProvince > 0,
                "biome changes must occur without any macro province change");
    }

    @Test
    void severalBiomesCoexistInsideASingleMacroTerritory() {
        // The strongest form of the decoupling claim: one province, several biomes.
        V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(0xC300L);
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<Integer, Set<String>> byProvince = new HashMap<>();
        for (int z = -1200; z <= 1200; z += 48) {
            for (int x = -1200; x <= 1200; x += 48) {
                sampler.sampleColumn(x, z, col);
                int prov = col.dominantGeology() == null ? -1 : col.dominantGeology().ordinal();
                byProvince.computeIfAbsent(prov, k -> new HashSet<>()).add(col.biome.id());
            }
        }
        int multi = 0;
        for (Set<String> s : byProvince.values()) if (s.size() >= 2) multi++;
        assertTrue(multi > 0,
                "at least one macro territory must contain several biomes, otherwise the biome "
                        + "map is just the province map");
    }
}