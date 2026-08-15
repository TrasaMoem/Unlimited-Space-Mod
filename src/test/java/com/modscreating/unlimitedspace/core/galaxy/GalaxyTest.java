package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyTest {

    @Test
    void seedIsDerivedFromWorldSeed() {
        Galaxy g = Galaxy.from(999L);
        assertEquals(Seeds.galaxy(999L), g.seed().value());
    }

    @Test
    void systemIdentityIsStableAcrossGalaxyInstances() {
        Galaxy a = Galaxy.from(777L);
        Galaxy b = Galaxy.from(777L);
        StarSystemId id = a.systemId(11);
        assertEquals(id, b.systemId(11));
        assertEquals(a.starSystemSeed(11), b.starSystemSeed(11));
    }

    @Test
    void starSystemIsDeterministicById() {
        Galaxy g = Galaxy.from(42L);
        StarSystemId id = g.systemId(5);
        StarSystem s1 = g.getStarSystem(id);
        StarSystem s2 = g.getStarSystem(id);
        assertEquals(s1.seed(), s2.seed());
        assertEquals(s1.position(), s2.position());
        assertEquals(s1.star(), s2.star());
        assertEquals(s1.id(), StarSystemId.of(5));
    }

    @Test
    void starPositionsStayWithinGalaxyRadius() {
        Galaxy g = Galaxy.from(31337L, GalaxyParameters.DEFAULT);
        for (int i = 0; i < 200; i++) {
            GalacticPosition pos = g.getStarSystem(g.systemId(i)).position();
            double maxR = GalaxyParameters.DEFAULT.radius();
            double dist = Math.sqrt(pos.x() * pos.x() + pos.y() * pos.y() + pos.z() * pos.z());
            assertTrue(dist <= maxR * 1.5,
                    "system " + i + " too far: " + dist + " > " + maxR);
        }
    }

    @Test
    void planetSeedMatchesReconstructionThroughGalaxy() {
        Galaxy g = Galaxy.from(2024L);
        int system = 3, orbit = 2;
        long viaGalaxy = Seeds.planet(g.starSystemSeed(system), orbit);
        long viaGalaxyHelper = g.planetSeed(system, orbit);
        long viaSystem = g.getStarSystem(g.systemId(system)).planetSeed(orbit);
        assertEquals(viaGalaxy, viaGalaxyHelper);
        assertEquals(viaGalaxy, viaSystem);
        }
}
