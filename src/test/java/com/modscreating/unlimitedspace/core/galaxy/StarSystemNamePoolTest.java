package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The 10 000-name system pool: full load and GUARANTEED-UNIQUE assignment. */
class StarSystemNamePoolTest {

    private static final double RADIUS = 100.0;
    private static final double DENSITY = 0.8;
    private static final long SEED = 84213L;

    @Test
    void loadsAllTenThousandNames() {
        assertEquals(10000, StarSystemNamePool.size());
    }

    @Test
    void namesAreUniqueAcrossTheWholeDefaultGalaxy() {
        // every POPULATED cell of the default galaxy (radius 100, density 0.8)
        Set<String> seen = new HashSet<>();
        int assigned = 0;
        for (int index = 0; index < 40000; index++) {
            if (!StarSystemNamePool.isPopulated(RADIUS, DENSITY, index)) continue;
            String n = StarSystemNamePool.forSystem(RADIUS, DENSITY, SEED, index);
            assertTrue(seen.add(n), "duplicate name '" + n + "' at system " + index);
            assigned++;
        }
        assertTrue(assigned >= 7000 && assigned <= 10000,
                "expected ~8000 unique systems, got " + assigned);
    }

    @Test
    void pickIsStablePerWorldSeedAndIndex() {
        String a = StarSystemNamePool.forSystem(RADIUS, DENSITY, SEED, 4123);
        String b = StarSystemNamePool.forSystem(RADIUS, DENSITY, SEED, 4123);
        assertEquals(a, b);
    }
}
