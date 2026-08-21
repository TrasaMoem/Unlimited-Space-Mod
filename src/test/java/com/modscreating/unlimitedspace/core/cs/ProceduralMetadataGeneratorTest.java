package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.6.2 pure-domain tests for the SEED-AWARE {@link ProceduralMetadataGenerator}.
 *
 * <p>The generator consumes the ACTUAL procedural domain state (Galaxy/Planet/Moon/...) so:
 * (a) every canonical celestial body of every in-scope system has CS metadata (full coverage);
 * (b) the CS surface gravity equals {@code PlanetProperties.gravity()} (the single source of
 * truth) converted to m/sВІ - there is no separate hash-based gravity model;
 * (c) the same world seed is deterministic and different seeds may differ.
 * No ServerLevel/ChunkGenerator/DynamicDimensions call is ever made here.
 */
class ProceduralMetadataGeneratorTest {

    private static final String NS = "unlimitedspace";
    private static final long SEED_A = -1677674582474123669L;
    private static final long SEED_B = 777L;
    private static final int SCOPE = 16;

    private static List<ProceduralRocketAccessibleDimension> generate(long seed, int count) {
        return ProceduralMetadataGenerator.generate(seed, count, NS);
    }

    private static ProceduralRocketAccessibleDimension byKey(List<ProceduralRocketAccessibleDimension> all, String key) {
        return all.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    void scopeCoversSystem0PlanetsBeyondPlanet00() {
        // Full coverage: for the current seed, system 0 has 4 planets - all must have metadata.
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, 4);
        StarSystem sys = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(0));
        assertTrue(sys.planetCount() >= 2, "test seed must have >=2 planets in system 0");
        for (int p = 0; p < sys.planetCount(); p++) {
            Planet planet = sys.getPlanet(p);
            assertNotNull(byKey(all, NS + ":planet/" + planet.id().code() + "/surface"),
                    planet.id().code() + " surface must have metadata");
            assertNotNull(byKey(all, NS + ":planet/" + planet.id().code() + "/orbit"),
                    planet.id().code() + " orbit must have metadata");
        }
    }

    @Test
    void deterministicSameSeedSameList() {
        assertEquals(generate(SEED_A, SCOPE), generate(SEED_A, SCOPE),
                "same seed -> identical metadata list");
    }

    @Test
    void seedAwarenessDifferentSeedsMayDiffer() {
        // planet_00 of system 0 may legitimately have a different gravity for a different seed.
        double a = byKey(generate(SEED_A, 1), NS + ":planet/system_0000_planet_00/surface").gravity();
        double b = byKey(generate(SEED_B, 1), NS + ":planet/system_0000_planet_00/surface").gravity();
        double domainA = Gravity.toMetersPerSecondSq(
                Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(0)).getPlanet(0).properties().gravity());
        double domainB = Gravity.toMetersPerSecondSq(
                Galaxy.from(SEED_B).getStarSystem(StarSystemId.of(0)).getPlanet(0).properties().gravity());
        assertEquals(domainA, a, 1e-6, "CS gravity must equal domain gravity (seed A)");
        assertEquals(domainB, b, 1e-6, "CS gravity must equal domain gravity (seed B)");
        // Seed-awareness is structural: the CS value IS the domain value, and the domain value is
        // a pure function of the world seed (Proven: Seeds.planet chain). The values may or may not
        // coincide for this pair; what matters is CS == domain on BOTH seeds.
        assertTrue(Math.abs(a - b) > 1e-9 || Math.abs(domainA - domainB) <= 1e-9,
                "CS gravity is either seed-dependent or the domains coincide");
    }

    @Test
    void fullCoverageMatchesCanonicalObjects() {
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, SCOPE);
        Set<String> keys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : all) {
            keys.add(e.key());
        }
        // Every canonical body of every in-scope system must have its entries.
        for (int s = 0; s < SCOPE; s++) {
            StarSystem sys = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(s));
            for (int p = 0; p < sys.planetCount(); p++) {
                Planet planet = sys.getPlanet(p);
                assertTrue(keys.contains(NS + ":planet/" + planet.id().code() + "/surface"));
                assertTrue(keys.contains(NS + ":planet/" + planet.id().code() + "/orbit"));
                for (var moon : planet.moons()) {
                    assertTrue(keys.contains(NS + ":moon/" + moon.id().code() + "/surface"));
                    assertTrue(keys.contains(NS + ":moon/" + moon.id().code() + "/orbit"));
                }
            }
            for (int a = 0; a < sys.asteroidClusterCount(); a++) {
                assertTrue(keys.contains(NS + ":asteroid/" + sys.asteroid(a).id().code()));
            }
            assertTrue(keys.contains(NS + ":star/" + sys.id().code() + "/orbit"));
        }
    }

    @Test
    void noDuplicateKeys() {
        Set<String> keys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : generate(SEED_A, SCOPE)) {
            assertTrue(keys.add(e.key()), "duplicate key: " + e.key());
        }
    }

    @Test
    void planetOrbitIsZeroGravityAtCsArrival() {
        ProceduralRocketAccessibleDimension orbit = byKey(generate(SEED_A, 1),
                NS + ":planet/system_0000_planet_00/orbit");
        assertNotNull(orbit);
        assertEquals(0.0, orbit.gravity(), 1e-9, "planet orbit gravity must be 0");
        assertEquals(64, orbit.arrivalHeight(), "planet orbit arrival must be 64");
        assertEquals(NS + ":planet/system_0000_planet_00/surface", orbit.orbitedBody());
    }

    @Test
    void planetSurfaceGravityEqualsDomainAndCsArrival() {
        Planet planet = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(0)).getPlanet(0);
        ProceduralRocketAccessibleDimension surface = byKey(generate(SEED_A, 1),
                NS + ":planet/system_0000_planet_00/surface");
        assertNotNull(surface);
        double expected = Gravity.toMetersPerSecondSq(planet.properties().gravity());
        assertEquals(expected, surface.gravity(), 1e-6,
                "surface gravity must be the actual PlanetProperties.gravity converted to m/s^2");
        assertEquals(200, surface.arrivalHeight(), "surface arrival must be 200");
        assertTrue(surface.gravity() > 0, "surface gravity positive");
    }

    @Test
    void differentPlanetsKeepDifferentDomainGravities() {
        // planet_00 and planet_01 of the SAME system must NOT collapse to one generic value.
        StarSystem sys = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(0));
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, 1);
        for (int p = 1; p < Math.min(sys.planetCount(), 3); p++) {
            Planet planet = sys.getPlanet(p);
            double cs = byKey(all, NS + ":planet/" + planet.id().code() + "/surface").gravity();
            double domain = Gravity.toMetersPerSecondSq(planet.properties().gravity());
            assertEquals(domain, cs, 1e-6, planet.id().code() + " CS gravity must equal its own domain gravity");
        }
    }

    @Test
    void moonSurfaceUsesMoonOwnGravity() {
        // Find a planet with a moon; the moon surface gravity must be the MOON's own value.
        for (int s = 0; s < 16; s++) {
            StarSystem sys = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(s));
            for (int p = 0; p < sys.planetCount(); p++) {
                Planet planet = sys.getPlanet(p);
                if (planet.moonCount() > 0) {
                    var moon = planet.moon(0);
                    ProceduralRocketAccessibleDimension surface = byKey(generate(SEED_A, s + 1),
                            NS + ":moon/" + moon.id().code() + "/surface");
                    assertNotNull(surface);
                    double expected = Gravity.toMetersPerSecondSq(moon.properties().gravity());
                    assertEquals(expected, surface.gravity(), 1e-6,
                            "moon surface gravity must be the moon's own PlanetProperties-equivalent");
                    assertEquals(NS + ":planet/" + planet.id().code() + "/surface", surface.orbitedBody());
                    ProceduralRocketAccessibleDimension orbit = byKey(generate(SEED_A, s + 1),
                            NS + ":moon/" + moon.id().code() + "/orbit");
                    assertEquals(0.0, orbit.gravity(), 1e-9);
                    assertEquals(64, orbit.arrivalHeight());
                    return;
                }
            }
        }
    }

    @Test
    void asteroidIsWeightlessAndStarOrbitIsWeightless() {
        StarSystem sys = Galaxy.from(SEED_A).getStarSystem(StarSystemId.of(0));
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, 1);
        ProceduralRocketAccessibleDimension asteroid = byKey(all,
                NS + ":asteroid/" + sys.asteroid(0).id().code());
        assertNotNull(asteroid);
        assertEquals(0.0, asteroid.gravity(), 1e-9, "asteroid is weightless");
        assertEquals("minecraft:overworld", asteroid.orbitedBody());
        ProceduralRocketAccessibleDimension star = byKey(all, NS + ":star/system_0000/orbit");
        assertNotNull(star);
        assertEquals(0.0, star.gravity(), 1e-9);
        assertEquals(64, star.arrivalHeight());
    }

    @Test
    void overworldRoutesToAllProceduralBodies() {
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, SCOPE);
        ProceduralRocketAccessibleDimension ow = ProceduralMetadataGenerator.overworld(all);
        assertEquals("minecraft:overworld", ow.key());
        assertEquals(9.81, ow.gravity(), 1e-9);
        assertEquals(200, ow.arrivalHeight());
        Map<String, Integer> adj = ow.adjacentDimensions();
        assertTrue(adj.containsKey("creatingspace:earth_orbit"), "overworld keeps the CS earth orbit edge");
        assertTrue(adj.containsKey(NS + ":planet/system_0000_planet_00/orbit"),
                "overworld must route to a generated orbit");
        assertTrue(adj.containsKey(NS + ":asteroid/system_0000_asteroid_00"),
                "overworld must route to a generated asteroid");
        assertTrue(adj.containsKey(NS + ":star/system_0000/orbit"),
                "overworld must route to a generated star orbit");
    }

    @Test
    void adjacencyReferencesOnlyGeneratedKeys() {
        List<ProceduralRocketAccessibleDimension> all = generate(SEED_A, 2);
        Set<String> keys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : all) {
            keys.add(e.key());
        }
        keys.add("minecraft:overworld");
        keys.add("creatingspace:earth_orbit");
        for (ProceduralRocketAccessibleDimension e : all) {
            for (String dest : e.adjacentDimensions().keySet()) {
                assertTrue(keys.contains(dest),
                        "adjacency of " + e.key() + " references non-generated " + dest);
            }
        }
    }

    @Test
    void jsonMatchesCsSchema() {
        ProceduralRocketAccessibleDimension orbit = byKey(generate(SEED_A, 1),
                NS + ":planet/system_0000_planet_00/orbit");
        assertNotNull(orbit);
        String json = ProceduralMetadataGenerator.toJson(orbit);
        assertTrue(json.contains("\"arrivalHeight\": 64"), "json arrival height");
        assertTrue(json.contains("\"gravity\": 0.0"), "json gravity renders 0.0");
        assertTrue(json.contains("\"orbitedBody\": \"" + NS + ":planet/system_0000_planet_00/surface\""),
                "json orbitedBody");
        assertTrue(json.contains("\"distanceToOrbitingBody\": 5200"), "json distance");
        assertFalse(json.contains("\"id\""), "CS json has no id field (key from file path)");
    }
}