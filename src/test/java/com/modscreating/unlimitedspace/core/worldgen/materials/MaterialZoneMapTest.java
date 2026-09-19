package com.modscreating.unlimitedspace.core.worldgen.materials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R20 material-zone tests: zones must form LARGE coherent patches with bounded switching
 * frequency — never per-column random block changes.
 */
class MaterialZoneMapTest {

    private static final long SEED = 0x1234ABCDL;

    @Test
    void zoneSelectionIsDeterministic() {
        assertEquals(MaterialZoneMap.zoneAt(SEED, 123, -456),
                MaterialZoneMap.zoneAt(SEED, 123, -456));
        for (int x = -2000; x <= 2000; x += 37) {
            int z = MaterialZoneMap.zoneAt(SEED, x, x * 3);
            assertTrue(z >= 0 && z < MaterialZoneMap.ZONES, "zone index out of range: " + z);
        }
    }

    @Test
    void zonesChangeRarelyAtBlockScale() {
        // Per-column switching is FORBIDDEN: walking 16 blocks must almost never change zone.
        int switches = 0, total = 0;
        for (int x = -1600; x < 1600; x++) {
            for (int z = -400; z < 400; z += 16) {
                if (MaterialZoneMap.zoneAt(SEED, x, z) != MaterialZoneMap.zoneAt(SEED, x + 16, z)) {
                    switches++;
                }
                total++;
            }
        }
        double rate = (double) switches / total;
        assertTrue(rate < 0.25, "material zone switches too often per 16 blocks: " + rate);
    }

    @Test
    void zonesDoChangeAtRegionalScale() {
        // But zones are not a single uniform material either: somewhere on the planet the map
        // must visit more than one zone (regions are 500–3000 blocks, so scan a wide span).
        boolean sawMultiple = false;
        int first = MaterialZoneMap.zoneAt(SEED, 0, 0);
        for (int x = 0; x < 9000; x += 13) {
            for (int z = 0; z < 9000; z += 997) {
                if (MaterialZoneMap.zoneAt(SEED, x, z) != first) {
                    sawMultiple = true;
                    break;
                }
            }
            if (sawMultiple) break;
        }
        assertTrue(sawMultiple, "material zones must vary across the planet");
    }

    @Test
    void dominantZoneCoversMostOfTheMap() {
        // 70–85% common terrain: zone 0 must dominate the map coherently.
        int[] counts = new int[MaterialZoneMap.ZONES];
        int total = 0;
        for (int x = -2000; x <= 2000; x += 17) {
            for (int z = -2000; z <= 2000; z += 17) {
                counts[MaterialZoneMap.zoneAt(SEED, x, z)]++;
                total++;
            }
        }
        assertTrue(counts[0] > total * 0.45,
                "dominant material must cover most of the planet: " + (double) counts[0] / total);
        assertTrue(counts[3] < total * 0.25,
                "accent material must stay rare: " + (double) counts[3] / total);
    }

    @Test
    void differentPlanetSeedsGiveDifferentZoneLayouts() {
        boolean differ = false;
        outer:
        for (int x = 0; x < 3000; x += 11) {
            for (int z = 0; z < 3000; z += 11) {
                if (MaterialZoneMap.zoneAt(111L, x, z) != MaterialZoneMap.zoneAt(222L, x, z)) {
                    differ = true;
                    break outer;
                }
            }
        }
        assertTrue(differ, "different planets must have different material zone layouts");
    }
}
