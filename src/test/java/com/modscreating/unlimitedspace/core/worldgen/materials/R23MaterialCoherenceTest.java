package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R23 (M-1): the visible material identity is CONTEXTUAL (theme x province x surface), no
 * longer an independent zone lottery. Contract: dominant role >= 70%, secondary <= 20%,
 * geologic exceptions <= 10% and only in the provinces that justify them, accents <= 5% in
 * LARGE sparse patches. Also R23 (I): micro-facies cannot overwhelm the dominant language.
 */
@Tag("worldgen")
class R23MaterialCoherenceTest {

    private static final long SEED = 0x1234ABCDL;

    @Test
    void dominantRoleCoversThePlanet() {
        // R23 contract: >= 70% of the surface speaks the planet's dominant language.
        int[] counts = new int[4];
        int total = 0;
        for (int x = -3000; x <= 3000; x += 11) {
            for (int z = -3000; z <= 3000; z += 13) {
                counts[PlanetMaterialRoleSelector.zoneAt(null, 0.0,
                        null, SEED, x, z, 0.0)]++;
                total++;
            }
        }
        assertTrue(counts[0] >= total * 0.70,
                "dominant role must cover >=70%: " + (double) counts[0] / total);
        assertTrue(counts[1] <= total * 0.20,
                "secondary role must stay <=20%: " + (double) counts[1] / total);
        assertTrue(counts[2] <= total * 0.10,
                "geologic role must stay <=10%: " + (double) counts[2] / total);
        assertTrue(counts[3] <= total * 0.05,
                "accent role must stay <=5%: " + (double) counts[3] / total);
    }

    @Test
    void geologicRockNeverAppearsOutsideItsProvinces() {
        for (int x = -2000; x <= 2000; x += 17) {
            for (int z = -2000; z <= 2000; z += 19) {
                int zone = PlanetMaterialRoleSelector.zoneAt(null, 0.0,
                        null, SEED, x, z, 0.0);
                assertNotEquals(PlanetMaterialRoleSelector.GEOLOGIC, zone,
                        "geologic slot fired in a PLAINS province at " + x + "," + z);
            }
        }
    }

    @Test
    void zoneSelectionIsDeterministic() {
        assertEquals(
                PlanetMaterialRoleSelector.zoneAt(null, 1.0, null, SEED, 123, -456, 0.0),
                PlanetMaterialRoleSelector.zoneAt(null, 1.0, null, SEED, 123, -456, 0.0));
    }

    @Test
    void microFaciesIsASubtleTexture() {
        double above = 0;
        int total = 0;
        for (int x = -2000; x <= 2000; x += 9) {
            for (int z = -2000; z <= 2000; z += 9) {
                if (MaterialZoneMap.microFacies01(SEED, x, z) < 0.35) above++;
                total++;
            }
        }
        assertTrue(above / total < 0.40, "micro-facies must stay a texture: " + above / total);
    }
}
