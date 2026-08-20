package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.5.3 pure-domain tests for {@link ProceduralRocketAccessibleDimensionFactory}.
 *
 * <p>These assert only the deterministic METADATA values (RL, gravity, arrivalHeight, orbitedBody,
 * adjacency) — they deliberately do NOT claim any Creating Space runtime registry sync (that is a
 * Minecraft/NeoForge lifecycle concern covered by the server-side runtime proof). No ServerLevel,
 * ChunkGenerator or DynamicDimensions call is ever made here.
 */
class ProceduralRocketAccessibleDimensionTest {

    private static final long SEED = 777L;
    private static final String NS = "unlimitedspace";

    private static StarSystem systemFor(int idx) {
        return Galaxy.from(SEED).getStarSystem(new StarSystemId(idx));
    }

    private static Planet anyPlanet() {
        for (int s = 0; s < 16; s++) {
            StarSystem sys = systemFor(s);
            if (sys.planetCount() > 0) {
                return sys.getPlanet(0);
            }
        }
        throw new AssertionError("no planet in scope");
    }

    private static Planet planetWithMoon() {
        for (int s = 0; s < 32; s++) {
            StarSystem sys = systemFor(s);
            for (int p = 0; p < sys.planetCount(); p++) {
                Planet planet = sys.getPlanet(p);
                if (planet.moonCount() > 0) {
                    return planet;
                }
            }
        }
        throw new AssertionError("no planet with a moon in scope");
    }

    @Test
    void planetSurfaceMetadataIsDeterministicAndPlayable() {
        Planet a = anyPlanet();
        ProceduralRocketAccessibleDimension d1 = ProceduralRocketAccessibleDimensionFactory.planetSurface(a, NS);
        ProceduralRocketAccessibleDimension d2 = ProceduralRocketAccessibleDimensionFactory.planetSurface(a, NS);
        assertEquals(d1, d2, "deterministic for the same planet");
        assertEquals("unlimitedspace:planet/" + a.id().code() + "/surface", d1.key());
        assertTrue(d1.gravity() > 0, "planet surface gravity must be positive");
        assertTrue(d1.arrivalHeight() > 0, "surface arrival must be above terrain");
        assertNotNull(d1.orbitedBody());
        assertTrue(d1.adjacentDimensions().containsKey(
                "unlimitedspace:planet/" + a.id().code() + "/orbit"),
                "surface adjacency includes its orbit");
    }

    @Test
    void planetOrbitIsZeroGravityAtCsArrival() {
        Planet a = anyPlanet();
        ProceduralRocketAccessibleDimension orbit = ProceduralRocketAccessibleDimensionFactory.planetOrbit(a, NS);
        assertEquals("unlimitedspace:planet/" + a.id().code() + "/orbit", orbit.key());
        assertEquals(0.0, orbit.gravity(), 1e-9, "orbit gravity must be 0");
        assertEquals(64, orbit.arrivalHeight(), "orbit arrival must be 64");
        assertEquals("unlimitedspace:planet/" + a.id().code() + "/surface", orbit.orbitedBody(),
                "orbit orbitedBody = its own surface RL");
    }

    @Test
    void moonMetadataUsesOwnGravityAndParentSurface() {
        Planet parent = planetWithMoon();
        Moon moon = parent.moon(0);
        ProceduralRocketAccessibleDimension surface = ProceduralRocketAccessibleDimensionFactory.moonSurface(parent, moon, NS);
        ProceduralRocketAccessibleDimension orbit = ProceduralRocketAccessibleDimensionFactory.moonOrbit(parent, moon, NS);
        assertEquals("unlimitedspace:moon/" + moon.id().code() + "/surface", surface.key());
        assertTrue(surface.gravity() > 0, "moon surface gravity must be positive");
        assertEquals("unlimitedspace:planet/" + parent.id().code() + "/surface",
                surface.orbitedBody(), "moon surface orbitedBody = parent planet surface");
        assertEquals(0.0, orbit.gravity(), 1e-9);
        assertEquals(64, orbit.arrivalHeight());
        assertEquals("unlimitedspace:moon/" + moon.id().code() + "/surface", orbit.orbitedBody());
    }

    @Test
    void asteroidIsWeightlessFieldAndStarOrbitIsWeightless() {
        for (int s = 0; s < 16; s++) {
            StarSystem sys = systemFor(s);
            if (sys.asteroidClusterCount() > 0) {
                AsteroidCluster cluster = sys.asteroid(0);
                ProceduralRocketAccessibleDimension def =
                        ProceduralRocketAccessibleDimensionFactory.asteroid(cluster, NS);
                assertEquals("unlimitedspace:asteroid/" + cluster.id().code(), def.key());
                assertEquals(0.0, def.gravity(), 1e-9, "asteroid is weightless");
                assertEquals("minecraft:overworld", def.orbitedBody());

                ProceduralRocketAccessibleDimension so =
                        ProceduralRocketAccessibleDimensionFactory.starOrbit(sys, NS);
                assertEquals(0.0, so.gravity(), 1e-9, "star orbit gravity is 0");
                assertEquals(64, so.arrivalHeight(), "star orbit arrival is 64");
                assertNotNull(so.orbitedBody());
                return;
            }
        }
    }

    @Test
    void differentBodiesProduceDistinctKeys() {
        Planet a = anyPlanet();
        Set<String> keys = new HashSet<>();
        keys.add(ProceduralRocketAccessibleDimensionFactory.planetSurface(a, NS).key());
        keys.add(ProceduralRocketAccessibleDimensionFactory.planetOrbit(a, NS).key());
        assertEquals(2, keys.size(), "surface and orbit of the same planet are distinct");

        Planet withMoon = planetWithMoon();
        Moon m = withMoon.moon(0);
        keys.add(ProceduralRocketAccessibleDimensionFactory.moonSurface(withMoon, m, NS).key());
        keys.add(ProceduralRocketAccessibleDimensionFactory.moonOrbit(withMoon, m, NS).key());
        assertEquals(4, keys.size(), "planet surface/orbit + moon surface/orbit all distinct");
    }

    @Test
    void twoDifferentPlanetsProduceDistinctKeys() {
        // Find two planets at different orbit slots / systems.
        Planet first = null;
        StarSystem prevSys = null;
        for (int s = 0; s < 32 && first == null; s++) {
            StarSystem sys = systemFor(s);
            for (int p = 0; p < sys.planetCount(); p++) {
                if (first == null) {
                    first = sys.getPlanet(p);
                }
            }
        }
        Planet second = null;
        for (int s = 0; s < 32 && second == null; s++) {
            StarSystem sys = systemFor(s);
            if (sys.id().equals(first.id().system())) {
                continue;
            }
            for (int p = 0; p < sys.planetCount(); p++) {
                if (second == null) {
                    second = sys.getPlanet(p);
                }
            }
        }
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.id(), second.id(), "need two distinct planets");
        assertNotEquals(
                ProceduralRocketAccessibleDimensionFactory.planetKey(first.id(), true),
                ProceduralRocketAccessibleDimensionFactory.planetKey(second.id(), true),
                "different planets produce different surface keys");
    }
}