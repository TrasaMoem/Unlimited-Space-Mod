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

    @Test
    void systemZeroFirstPlanetIsLandableSurface() {
        // runSpace() teleports onto system 0 / planet 0. This must resolve to a real
        // planet (not interplanetary deep space) so the player does not fall forever.
        GalaxyLayout l = GalaxyLayout.from(123L);
        com.modscreating.unlimitedspace.core.stars.StarSystemId id = new com.modscreating.unlimitedspace.core.stars.StarSystemId(0);
        var sys = l.systemById(id);
        var planets = l.planetsFor(sys);
        assertFalse(planets.isEmpty(), "system 0 must have at least one planet");
        var p0 = planets.get(0);
        GalaxyCoordinate center = GalaxyCoordinate.of(p0.x(), p0.z());
        var res = l.lookup(center);
        assertFalse(res.interGalacticVoid(), "planet center must not be inter-galactic void");
        assertNotNull(res.planet(), "planet center must resolve to its planet");
        assertEquals(p0.id(), res.planet().id());
        assertTrue(res.profile() != null, "planet center must carry a worldgen profile (solid terrain)");
    }
}