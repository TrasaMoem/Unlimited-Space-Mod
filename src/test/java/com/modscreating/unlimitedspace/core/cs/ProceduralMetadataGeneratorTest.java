package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.6 pure-domain tests for {@link ProceduralMetadataGenerator} (seed-independent CS metadata).
 *
 * <p>These assert only deterministic METADATA values (RL, gravity, arrivalHeight, orbitedBody,
 * adjacency, JSON schema). They deliberately do NOT claim runtime CS registry sync - that is the
 * Minecraft/NeoForge lifecycle concern of the virtual datapack (ProceduralCsPack). No ServerLevel,
 * ChunkGenerator or DynamicDimensions call is ever made here.
 */
class ProceduralMetadataGeneratorTest {

    private static final String NS = "unlimitedspace";
    private static final int SCOPE = 1000;

    private static List<ProceduralRocketAccessibleDimension> generate(int count) {
        return ProceduralMetadataGenerator.generate(count, NS);
    }

    private static ProceduralRocketAccessibleDimension byKey(List<ProceduralRocketAccessibleDimension> all, String key) {
        return all.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    void scopeCoversSystem910Planet() {
        List<ProceduralRocketAccessibleDimension> all = generate(SCOPE);
        String surface = "unlimitedspace:planet/system_0910_planet_00/surface";
        String orbit = "unlimitedspace:planet/system_0910_planet_00/orbit";
        assertNotNull(byKey(all, surface), "system 910 planet_00 surface must have metadata");
        assertNotNull(byKey(all, orbit), "system 910 planet_00 orbit must have metadata");
    }

    @Test
    void deterministicSameCountAndSameValues() {
        List<ProceduralRocketAccessibleDimension> a = generate(SCOPE);
        List<ProceduralRocketAccessibleDimension> b = generate(SCOPE);
        assertEquals(a, b, "same inputs -> identical metadata list");
        assertEquals(SCOPE * ProceduralMetadataGenerator.entriesPerSystem(), a.size());
    }

    @Test
    void noDuplicateKeys() {
        Set<String> keys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : generate(SCOPE)) {
            assertTrue(keys.add(e.key()), "duplicate key: " + e.key());
        }
    }
    @Test
    void planetOrbitIsZeroGravityAtCsArrival() {
        ProceduralRocketAccessibleDimension orbit = byKey(generate(1),
                "unlimitedspace:planet/system_0000_planet_00/orbit");
        assertNotNull(orbit);
        assertEquals(0.0, orbit.gravity(), 1e-9, "planet orbit gravity must be 0");
        assertEquals(64, orbit.arrivalHeight(), "planet orbit arrival must be 64");
        assertEquals("unlimitedspace:planet/system_0000_planet_00/surface", orbit.orbitedBody());
    }

    @Test
    void planetSurfaceHasPositiveGravityAndCsArrival() {
        ProceduralRocketAccessibleDimension surface = byKey(generate(1),
                "unlimitedspace:planet/system_0000_planet_00/surface");
        assertNotNull(surface);
        assertTrue(surface.gravity() > 0, "planet surface gravity must be positive");
        assertEquals(200, surface.arrivalHeight(), "planet surface arrival must be CS 200");
        assertTrue(surface.adjacentDimensions().containsKey(
                "unlimitedspace:planet/system_0000_planet_00/orbit"),
                "surface adjacency includes its orbit");
    }

    @Test
    void moonSurfaceAndOrbitSemantics() {
        List<ProceduralRocketAccessibleDimension> all = generate(1);
        ProceduralRocketAccessibleDimension surface = byKey(all,
                "unlimitedspace:moon/system_0000_planet_00_moon_00/surface");
        ProceduralRocketAccessibleDimension orbit = byKey(all,
                "unlimitedspace:moon/system_0000_planet_00_moon_00/orbit");
        assertNotNull(surface, "moon_00 surface metadata must exist");
        assertNotNull(orbit, "moon_00 orbit metadata must exist");
        assertTrue(surface.gravity() > 0, "moon surface gravity must be positive");
        assertEquals(200, surface.arrivalHeight(), "moon surface arrival must be CS 200");
        assertEquals(0.0, orbit.gravity(), 1e-9, "moon orbit gravity must be 0");
        assertEquals(64, orbit.arrivalHeight(), "moon orbit arrival must be 64");
        assertEquals("unlimitedspace:moon/system_0000_planet_00_moon_00/surface", orbit.orbitedBody());
    }

    @Test
    void asteroidIsWeightlessFieldWithSafeOrbitedBody() {
        ProceduralRocketAccessibleDimension asteroid = byKey(generate(1),
                "unlimitedspace:asteroid/system_0000_asteroid_00");
        assertNotNull(asteroid);
        assertEquals(0.0, asteroid.gravity(), 1e-9, "asteroid must be weightless");
        assertEquals(45, asteroid.arrivalHeight(), "asteroid arrival is the field centre Y");
        assertEquals("minecraft:overworld", asteroid.orbitedBody(),
                "asteroid orbitedBody must be a real always-loaded dimension (no NPE)");
    }
    @Test
    void starOrbitIsWeightlessWithRealOrbitedBody() {
        ProceduralRocketAccessibleDimension star = byKey(generate(1),
                "unlimitedspace:star/system_0000/orbit");
        assertNotNull(star);
        assertEquals(0.0, star.gravity(), 1e-9, "star orbit gravity must be 0");
        assertEquals(64, star.arrivalHeight(), "star orbit arrival must be 64");
        assertEquals("unlimitedspace:planet/system_0000_planet_00/surface", star.orbitedBody(),
                "star orbit orbitedBody is the always-generated planet_00 surface");
    }

    @Test
    void overworldRoutesToProceduralDestinations() {
        ProceduralRocketAccessibleDimension ow = ProceduralMetadataGenerator.overworld(generate(SCOPE));
        assertEquals("minecraft:overworld", ow.key());
        assertEquals(9.81, ow.gravity(), 1e-9);
        assertEquals(200, ow.arrivalHeight());
        Map<String, Integer> adj = ow.adjacentDimensions();
        assertTrue(adj.containsKey("creatingspace:earth_orbit"), "overworld keeps the CS earth orbit edge");
        assertTrue(adj.containsKey("unlimitedspace:planet/system_0910_planet_00/orbit"),
                "overworld must route to system 910 orbit (acceptance target)");
        assertTrue(adj.containsKey("unlimitedspace:asteroid/system_0910_asteroid_00"),
                "overworld must route to system 910 asteroid");
    }

    @Test
    void adjacencyReferencesOnlyGeneratedKeys() {
        List<ProceduralRocketAccessibleDimension> all = generate(2);
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
        ProceduralRocketAccessibleDimension orbit = byKey(generate(1),
                "unlimitedspace:planet/system_0000_planet_00/orbit");
        assertNotNull(orbit);
        String json = ProceduralMetadataGenerator.toJson(orbit);
        assertTrue(json.contains("\"arrivalHeight\": 64"), "json arrival height");
        assertTrue(json.contains("\"gravity\": 0.0"), "json gravity renders 0.0");
        assertTrue(json.contains("\"orbitedBody\": \"unlimitedspace:planet/system_0000_planet_00/surface\""),
                "json orbitedBody");
        assertTrue(json.contains("\"distanceToOrbitingBody\": 5200"), "json distance");
        assertTrue(json.contains("\"deltaV\": 200"), "json adjacency deltaV");
        assertFalse(json.contains("\"id\""), "CS json has no id field (key from file path)");
    }
    @Test
    void gravityDeterministicAndDifferentBodiesDiffer() {
        PlanetId a = PlanetId.of(StarSystemId.of(0), 0);
        PlanetId b = PlanetId.of(StarSystemId.of(0), 1);
        double ga1 = ProceduralMetadataGenerator.canonicalPlanetGravityMs(a);
        double ga2 = ProceduralMetadataGenerator.canonicalPlanetGravityMs(a);
        assertEquals(ga1, ga2, 1e-12, "same body -> same canonical gravity");
        assertTrue(ga1 > 0, "canonical planet gravity positive");
        assertNotEquals(ga1, ProceduralMetadataGenerator.canonicalPlanetGravityMs(b), 1e-12,
                "different bodies can differ (deterministic from stable id)");
    }

    @Test
    void canonicalGravityIsStableAcrossCalls() {
        // Same stable ids must produce identical gravity regardless of any world seed -
        // the registry metadata is generated from stable IDs only (documented R14.6 design).
        double g1 = ProceduralMetadataGenerator.canonicalPlanetGravityMs(
                PlanetId.of(StarSystemId.of(910), 0));
        double g2 = ProceduralMetadataGenerator.canonicalPlanetGravityMs(
                PlanetId.of(StarSystemId.of(910), 0));
        assertEquals(g1, g2, 1e-12);
        MoonId mid = MoonId.of(PlanetId.of(StarSystemId.of(0), 0), 0);
        assertTrue(ProceduralMetadataGenerator.canonicalMoonGravityMs(mid) > 0);
    }
}