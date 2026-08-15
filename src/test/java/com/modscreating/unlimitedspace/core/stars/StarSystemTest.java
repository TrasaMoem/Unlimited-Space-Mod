package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.galaxy.GalacticPosition;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyType;
import com.modscreating.unlimitedspace.core.planets.Planet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StarSystemTest {

    private static final long WORLD_SEED = 1337L;
    private static final Galaxy GALAXY = Galaxy.from(WORLD_SEED, GalaxyParameters.DEFAULT);

    @Test
    void starPropertiesAreWithinSpectralTypeRanges() {
        for (int i = 0; i < 60; i++) {
            Star star = GALAXY.getStarSystem(GALAXY.systemId(i)).star();
            StarType t = star.type();
            assertTrue(star.temperature() >= t.minTemperature() && star.temperature() <= t.maxTemperature(),
                    "temp " + star.temperature() + " out of range for " + t);
            assertTrue(star.size() >= t.minSize() && star.size() <= t.maxSize(),
                    "size " + star.size() + " out of range for " + t);
            assertTrue(star.luminosity() >= t.minLuminosity() && star.luminosity() <= t.maxLuminosity(),
                    "lum " + star.luminosity() + " out of range for " + t);
            assertEquals(t.colorRgb(), star.colorRgb());
        }
    }

    @Test
    void starSystemIdMatchesStarSystemId() {
        StarSystemId id = GALAXY.systemId(7);
        StarSystem sys = GALAXY.getStarSystem(id);
        assertEquals(id, sys.id());
        assertEquals(id, sys.star().systemId());
    }

    @Test
    void starSystemIsDeterministic() {
        StarSystemId id = GALAXY.systemId(2);
        StarSystem a = GALAXY.getStarSystem(id);
        StarSystem b = GALAXY.getStarSystem(id);
        assertEquals(a.seed(), b.seed());
        assertEquals(a.position(), b.position());
        assertEquals(a.star(), b.star());
    }

    @Test
    void orbitIndexDoesNotAffectSiblingSeeds() {
        StarSystem sys = GALAXY.getStarSystem(GALAXY.systemId(2));
        // planet seed is a pure function of the system seed + orbit index
        assertEquals(sys.seed(), GALAXY.starSystemSeed(2));
        long seed0 = sys.planetSeed(0);
        long seed1 = sys.planetSeed(1);
        assertNotEquals(seed0, seed1);
        // regeneration of orbit 0 is stable
        assertEquals(seed0, sys.planetSeed(0));
    }

    @Test
    void planetsAreGeneratedDeterministicallyPerOrbit() {
        StarSystem sys = GALAXY.getStarSystem(GALAXY.systemId(4));
        Planet p1 = sys.getPlanet(0);
        Planet p2 = sys.getPlanet(0);
        assertEquals(p1, p2);
        assertNotEquals(sys.getPlanet(0), sys.getPlanet(2));
    }

    @Test
    void allGalaxyTypesProducePositions() {
        for (GalaxyType type : GalaxyType.values()) {
            Galaxy g = Galaxy.from(WORLD_SEED, new GalaxyParameters(80.0, 0.9, type));
            GalacticPosition pos = g.getStarSystem(g.systemId(0)).position();
            assertFalse(Double.isNaN(pos.x()), "NaN position for " + type);
        }
    }
}
