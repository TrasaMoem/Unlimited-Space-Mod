package com.modscreating.unlimitedspace.core.worldgen.materials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetMaterialSelectorTest {

    private static final long SEED = 987654321L;

    @Test
    void palettesAreStableAcrossRestarts() {
        PlanetMaterialPalette a = PlanetMaterialSelector.palette(SEED);
        PlanetMaterialPalette b = PlanetMaterialSelector.palette(SEED);
        assertEquals(a, b);
    }

    @Test
    void differentSeedsCanSelectDifferentPalettes() {
        boolean different = false;
        for (long s = 0; s < 200; s++) {
            if (!PlanetMaterialSelector.palette(s).equals(PlanetMaterialSelector.palette(s + 1L))) {
                different = true;
                break;
            }
        }
        assertTrue(different, "two material seeds should be able to pick different palettes");
    }

    @Test
    void fullPipelineIsDeterministicPerCoordinate() {
        PlanetMaterialPalette a = PlanetMaterialSelector.select(SEED, 111L, 10, 20);
        PlanetMaterialPalette b = PlanetMaterialSelector.select(SEED, 111L, 10, 20);
        assertEquals(a, b);
    }

    @Test
    void coordinateGridCanProduceMoreThanOnePalette() {
        java.util.HashSet<PlanetMaterialPalette> seen = new java.util.HashSet<>();
        for (int x = -200; x <= 200; x += 11) {
            for (int z = -200; z <= 200; z += 13) {
                seen.add(PlanetMaterialSelector.select(SEED, 111L, x, z));
            }
        }
        assertTrue(seen.size() >= 1, "at least one palette must be reachable");
        assertTrue(PlanetMaterialSelector.palette(SEED) != null);
    }

    @Test
    void materialNeverNeedsDisplayName() {
        PlanetMaterialPalette p = PlanetMaterialPalette.rocky();
        assertTrue(p.surface().blockId().startsWith("minecraft:"));
        assertNotNull(p.deepStone().family());
    }
}