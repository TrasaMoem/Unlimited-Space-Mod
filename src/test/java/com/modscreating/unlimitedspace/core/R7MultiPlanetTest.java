package com.modscreating.unlimitedspace.core;

import com.modscreating.unlimitedspace.core.destination.WorldDestination;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.seed.WorldSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7: proves the single-planet proof architecture generalises to multiple independent
 * procedural planets driven by ONE reusable world-seed + slot pipeline. Pure domain
 * (no Minecraft types), so it runs in plain JUnit like the R6 core tests.
 */
class R7MultiPlanetTest {

    private static final long WORLD_SEED = 0x1234567890ABCDEDL;
    private static final int PLANETS = 3;
    private static final int SYSTEM = 0;

    private static Planet[] planets(long worldSeed) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystemId sys = StarSystemId.of(SYSTEM);
        Planet[] out = new Planet[PLANETS];
        for (int orbit = 0; orbit < PLANETS; orbit++) {
            out[orbit] = galaxy.getStarSystem(sys).getPlanet(orbit);
        }
        return out;
    }

    private static PlanetId id(int orbit) {
        return PlanetId.of(StarSystemId.of(SYSTEM), orbit);
    }

    @Test
    void worldSeedFacadeMirrorsDomainSeedChain() {
        WorldSeed ws = WorldSeed.of(WORLD_SEED);
        assertEquals(new GalaxySeed(Seeds.galaxy(WORLD_SEED)), ws.galaxySeed());
        assertEquals(ws.starSystemSeed(SYSTEM), Seeds.starSystem(ws.galaxySeed().value(), SYSTEM));
        assertEquals(ws.planetSeed(SYSTEM, 0), Seeds.planet(ws.starSystemSeed(SYSTEM), 0));
    }

    @Test
    void planetIdsAreDerivedFromStableSlot() {
        Planet[] p = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(id(i), p[i].id(), "stable identity from slot");
            assertEquals("system_0000_planet_0" + i, p[i].id().code());
        }
    }

    @Test
    void planetsHaveUniqueSeedsPropertiesAndProfiles() {
        Planet[] p = planets(WORLD_SEED);
        PlanetWorldgenProfile[] prof = new PlanetWorldgenProfile[PLANETS];
        for (int i = 0; i < PLANETS; i++) prof[i] = PlanetWorldgenProfile.from(p[i]);
        for (int a = 0; a < PLANETS; a++) {
            assertEquals(p[a].seed(), PlanetSeed.forSlot(Galaxy.from(WORLD_SEED).starSystemSeed(SYSTEM), a),
                    "planet seed is deterministic from world seed + slot");
            for (int b = a + 1; b < PLANETS; b++) {
                assertNotEquals(p[a].id(), p[b].id(), "different slots => different PlanetId");
                assertNotEquals(p[a].seed(), p[b].seed(), "different slots => different PlanetSeed");
                assertNotEquals(p[a].properties(), p[b].properties(), "different slots => different PlanetProperties");
                assertNotEquals(prof[a], prof[b], "different slots => different PlanetWorldgenProfile");
                assertNotEquals(prof[a].terrainSeed(), prof[b].terrainSeed(), "terrain seeds differ");
            }
        }
    }

    @Test
    void surfaceAndOrbitAreDistinctWorldsForEachPlanet() {
        Planet[] p = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            WorldDestination surf = WorldDestination.planetSurface(id(i), p[i].seed());
            WorldDestination orb = WorldDestination.planetOrbit(id(i), p[i].seed());
            assertNotEquals(surf.worldSeed(), orb.worldSeed(), "surface vs orbit world seed differs");
            assertNotEquals(surf.code(), orb.code(), "surface vs orbit code differs");
            assertEquals(WorldKind.SURFACE, surf.worldKind());
            assertEquals(WorldKind.ORBIT, orb.worldKind());
            assertTrue(surf.code().endsWith("_surface"));
            assertTrue(orb.code().endsWith("_orbit"));
        }
        assertNotEquals(
                WorldDestination.planetSurface(id(0), p[0].seed()),
                WorldDestination.planetSurface(id(1), p[1].seed()),
                "different planets must have different surface destinations");
    }

    @Test
    void sameWorldSeedReproducesSameThreePlanets() {
        Planet[] a = planets(WORLD_SEED);
        Planet[] b = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(a[i], b[i], "restart must reproduce the planet");
            assertEquals(a[i].properties(), b[i].properties(), "restart must reproduce properties");
        }
    }

    @Test
    void differentWorldSeedProducesDifferentPlanets() {
        Planet[] a = planets(WORLD_SEED);
        Planet[] b = planets(WORLD_SEED ^ 0xDEADBEEFL);
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(a[i].id(), b[i].id(), "same slot => same stable id");
            assertNotEquals(a[i].seed(), b[i].seed(), "different world seed => different planet seed");
            assertNotEquals(a[i].properties(), b[i].properties(), "different world seed => different properties");
        }
    }

    @Test
    void threePlanetsHaveDistinctPlanetSeeds() {
        Planet[] p = planets(WORLD_SEED);
        Set<Long> seeds = new HashSet<>();
        for (Planet planet : p) seeds.add(planet.seed().value());
        assertEquals(PLANETS, seeds.size(), "three distinct planet seeds");
    }
}
