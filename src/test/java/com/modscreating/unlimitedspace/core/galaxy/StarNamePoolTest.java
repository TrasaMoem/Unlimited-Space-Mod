package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled 10 000-name primary-star pool: full load, stable random pick. */
class StarNamePoolTest {

    @Test
    void loadsAllTenThousandNames() {
        assertEquals(10000, StarNamePool.size());
    }

    @Test
    void pickIsStablePerStarIdentity() {
        String a = StarNamePool.forStar(173, 0);
        String b = StarNamePool.forStar(173, 0);
        assertEquals(a, b);
        assertNotEquals(StarNamePool.forStar(173, 1).isBlank(), true);
    }
}
