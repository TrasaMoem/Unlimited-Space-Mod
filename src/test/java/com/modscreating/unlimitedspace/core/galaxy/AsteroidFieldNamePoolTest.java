package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled 20 000-name asteroid-field pool: full load, stable random pick. */
class AsteroidFieldNamePoolTest {

    @Test
    void loadsAllTwentyThousandNames() {
        assertEquals(20000, AsteroidFieldNamePool.size());
    }

    @Test
    void pickIsStablePerFieldIdentity() {
        String a = AsteroidFieldNamePool.forField(173, 0);
        String b = AsteroidFieldNamePool.forField(173, 0);
        assertEquals(a, b);
        assertFalse(AsteroidFieldNamePool.forField(42, 3).isBlank());
    }
}
