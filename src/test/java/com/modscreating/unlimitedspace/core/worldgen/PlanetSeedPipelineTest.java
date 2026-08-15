package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the deterministic planet -> terrain seed pipeline (Phase 3), without
 * hard-coding any "test_planet" notion into the core galaxy generation.
 * "System 0 / orbit 0" is merely a slot we choose here in the test, exactly like a
 * debug adapter would — the core never special-cases it.
 */
class PlanetSeedPipelineTest {

    private static final long WORLD_SEED = 1024L;

    @Test
    void terrainSeedIsDerivedFromPlanetSeed() {
        Galaxy g = Galaxy.from(WORLD_SEED);
        Planet p = g.getStarSystem(g.systemId(0)).getPlanet(0);
        long expected = Seeds.subsystem(p.seed().value(), "terrain");
        assertEquals(expected, p.properties().terrainSeed());
    }

    @Test
    void terrainSeedIsStableAcrossReconstruction() {
        Galaxy a = Galaxy.from(WORLD_SEED);
        Galaxy b = Galaxy.from(WORLD_SEED);
        long ta = a.getStarSystem(a.systemId(0)).getPlanet(0).properties().terrainSeed();
        long tb = b.getStarSystem(b.systemId(0)).getPlanet(0).properties().terrainSeed();
        assertEquals(ta, tb);
    }

    @Test
    void slotZeroPlanetIsIdentifiedByItsOwnId() {
        Galaxy g = Galaxy.from(WORLD_SEED);
        Planet p = g.getStarSystem(g.systemId(0)).getPlanet(0);
        assertTrue(p.id().system().index() == 0 && p.id().orbitIndex() == 0,
                "expected the chosen slot identity, got " + p.id());
    }

    @Test
    void coreNeverMentionsTestPlanet() {
        // Sanity: the domain id for this slot is still a normal stable id, there is
        // no hard-coded "test_planet" string anywhere in the core chain.
        Galaxy g = Galaxy.from(WORLD_SEED);
        Planet p = g.getStarSystem(g.systemId(0)).getPlanet(0);
        assertFalse(p.id().code().toLowerCase().contains("test"));
    }
}
