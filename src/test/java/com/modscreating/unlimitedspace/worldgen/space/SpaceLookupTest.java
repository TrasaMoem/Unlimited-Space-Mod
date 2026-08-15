package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyCoordinate;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceLookupTest {

    @Test
    void sameSeedSameResult() {
        GalaxyLayout a = GalaxyLayout.from(123L);
        GalaxyLayout b = GalaxyLayout.from(123L);
        GalaxyCoordinate c = GalaxyCoordinate.of(0.5, 0.5);
        assertEquals(a.lookup(c).interGalacticVoid(), b.lookup(c).interGalacticVoid());
    }

    @Test
    void differentSeedDifferentResultPossible() {
        GalaxyLayout a = GalaxyLayout.from(123L);
        GalaxyLayout b = GalaxyLayout.from(124L);
        GalaxyCoordinate c = GalaxyCoordinate.of(0.5, 0.5);
        assertNotNull(a.lookup(c));
        assertNotNull(b.lookup(c));
    }

    @Test
    void deepSpaceLookupIsSafeOutsideGalaxy() {
        GalaxyLayout l = GalaxyLayout.from(123L);
        assertTrue(l.lookup(GalaxyCoordinate.of(10_000, 10_000)).interGalacticVoid());
    }
}