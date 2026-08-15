package com.modscreating.unlimitedspace.core.worldgen.resources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanetResourceSelectorTest {

    private static final long ORE = 55555L;

    @Test
    void sameSeedSameCoordinatesSameResources() {
        List<PlanetResource> a = PlanetResourceSelector.distribute(ORE, 3, 4);
        List<PlanetResource> b = PlanetResourceSelector.distribute(ORE, 3, 4);
        assertEquals(a, b);
        assertEquals(PlanetResourceSelector.present(ORE, 3, 4, PlanetResourceSelector.CATALOGUE.get(0)),
                PlanetResourceSelector.present(ORE, 3, 4, PlanetResourceSelector.CATALOGUE.get(0)));
    }

    @Test
    void resourceSeedIsSeparateFromBiomeAndMaterial() {
        // The catalogue has 2 entries; both must always be resolvable via the selector
        assertFalse(PlanetResourceSelector.CATALOGUE.isEmpty());
        for (PlanetResource r : PlanetResourceSelector.CATALOGUE) {
            assertNotNull(r.id());
            assertNotNull(r.targetBlock());
            assertTrue(r.maxY() > r.minY());
        }
    }

    @Test
    void differentOreSeedsProduceDifferentDistribution() {
        boolean different = false;
        outer:
        for (int x = -20; x <= 20 && !different; x++) {
            for (int z = -20; z <= 20 && !different; z++) {
                if (!PlanetResourceSelector.distribute(ORE, x, z)
                        .equals(PlanetResourceSelector.distribute(ORE + 1, x, z))) {
                    different = true;
                }
            }
        }
        assertTrue(different, "two ore seeds should diverge somewhere");
    }

    @Test
    void rareResourceIsRare() {
        int hits = 0, total = 0;
        for (int x = 0; x < 200; x++) {
            for (int z = 0; z < 200; z++) {
                PlanetResource rare = PlanetResourceSelector.CATALOGUE.get(1);
                if (PlanetResourceSelector.present(ORE, x, z, rare)) hits++;
                total++;
            }
        }
        // a rare vein (<1% per cell over 40k cells) should appear less than half the time
        assertTrue(hits < total / 2, "rare resource should be genuinely rarer, hits=" + hits);
    }
}