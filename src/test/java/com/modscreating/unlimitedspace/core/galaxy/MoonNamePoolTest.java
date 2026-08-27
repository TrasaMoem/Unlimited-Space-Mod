package com.modscreating.unlimitedspace.core.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The 30 000-name moon pool: full load and stable per-moon assignment. */
class MoonNamePoolTest {

    @Test
    void loadsAllThirtyThousandNames() {
        assertEquals(30000, MoonNamePool.size());
    }

    @Test
    void pickIsStablePerIdentity() {
        String a = MoonNamePool.forMoon(1234, 2, 1);
        String b = MoonNamePool.forMoon(1234, 2, 1);
        assertEquals(a, b);
        assertFalse(a.isBlank());
    }

    @Test
    void differentMoonsGetVaryingNames_duplicatesAllowed() {
        // repeats are allowed BY DESIGN - but not ALL moons may share one name
        Set<String> seen = new HashSet<>();
        for (int s = 0; s < 50; s++)
            for (int o = 0; o < 5; o++)
                for (int m = 0; m < 3; m++)
                    seen.add(MoonNamePool.forMoon(s, o, m));
        assertTrue(seen.size() > 100, "pool must spread names across moons");
    }

    @Test
    void neverReturnsNull() {
        assertNotNull(MoonNamePool.forMoon(-1, -1, -1));
        assertNotNull(MoonNamePool.forMoon(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}