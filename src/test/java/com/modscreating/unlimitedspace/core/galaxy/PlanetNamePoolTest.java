package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled 10 000-name planet pool: full load, stable random pick per planet. */
class PlanetNamePoolTest {

    @Test
    void loadsAllTenThousandNames() {
        assertEquals(10000, PlanetNamePool.size(), "both name lists must fully parse");
    }

    @Test
    void pickIsStablePerPlanetIdentity() {
        String a = PlanetNamePool.forPlanet(173, 2);
        String b = PlanetNamePool.forPlanet(173, 2);
        assertEquals(a, b);
    }

    @Test
    void namesAreNonBlank() {
        for (int i = 0; i < 1000; i++) {
            String n = PlanetNamePool.forPlanet(i * 7 + 3, i % 5);
            assertNotNull(n);
            assertFalse(n.isBlank());
        }
    }
}
