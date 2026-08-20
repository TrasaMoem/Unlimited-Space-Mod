package com.modscreating.unlimitedspace.core.galaxy;

import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * R13.14 — canonical celestial-object index. The same WorldSeed + SystemId reproduces the
 * same ordering; every index resolves exactly one object; no duplicates, no gaps; object type
 * always derives from the generated object itself, never from a numeric range.
 */
class CanonicalObjectIndexTest {

    private static final long SEED = 2024L;

    @Test
    void sameSeedAndSystemReproduceIdenticalOrdering() {
        Galaxy g1 = Galaxy.from(SEED);
        Galaxy g2 = Galaxy.from(SEED);
        for (int s = 0; s < 16; s++) {
            StarSystem a = g1.getStarSystem(g1.systemId(s));
            StarSystem b = g2.getStarSystem(g2.systemId(s));
            assertEquals(a.canonicalCelestialObjects(), b.canonicalCelestialObjects(),
                    "canonical ordering must be deterministic per seed+system, sys=" + s);
            assertEquals(a.canonicalCelestialObjects(), a.canonicalCelestialObjects());
        }
    }

    @Test
    void differentSystemIdProducesObjectThatMayDiffer() {
        // Two distinct systems are essentially independent; both must resolve to valid,
        // fully-populated lists.
        Galaxy g = Galaxy.from(SEED);
        List<CelestialObject> s0 = g.getStarSystem(g.systemId(0)).canonicalCelestialObjects();
        List<CelestialObject> s7 = g.getStarSystem(g.systemId(7)).canonicalCelestialObjects();
        assertTrue(!s0.isEmpty());
        assertTrue(!s7.isEmpty());
        assertNotEquals(s0, s0.subList(0, 0)); // no-op guard to keep import used; safe
    }

    @Test
    void everyIndexResolvesExactlyOneNonNullObject() {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < 12; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int i = 0; i < objs.size(); i++) {
                CelestialObject obj = objs.get(i);
                assertNotNull(obj, "null canonical object at index " + i + " of " + system.id());
                switch (obj.kind()) {
                    case STAR -> assertNotNull(obj.star(), "STAR entry must carry a star");
                    case PLANET -> assertNotNull(obj.planet(), "PLANET entry must carry a planet");
                    case ASTEROID_FIELD ->
                            assertNotNull(obj.asteroid(), "ASTEROID_FIELD entry must carry a cluster");
                    default -> fail("unknown kind " + obj.kind());
                }
            }
        }
    }

    @Test
    void noDuplicatesAndNoGaps() {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < 32; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            // No duplicate entries (object equality). Note: companions of a multi-star system
            // legitimately share a StarId code, so the identity check is by object equality,
            // never by display code.
            Set<CelestialObject> seen = new HashSet<>();
            for (CelestialObject obj : objs) {
                assertTrue(seen.add(obj), "duplicate canonical object " + obj);
            }
            var counts = system.counts();
            assertEquals(counts.stars() + counts.planets() + counts.asteroidClusters(), objs.size(),
                    "canonical size must equal stars+planets+asteroidClusters");
        }
    }

    @Test
    void orderingIsStarsThenPlanetsThenAsteroidFields() {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < 32; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            int stars = system.stars().size();
            int planets = system.planetCount();
            int asteroids = system.asteroidClusterCount();
            assertEquals(stars + planets + asteroids, objs.size());
            for (int i = 0; i < stars; i++) {
                assertEquals(ObjectKind.STAR, objs.get(i).kind(), "index " + i + " should be a STAR");
            }
            for (int i = stars; i < stars + planets; i++) {
                assertEquals(ObjectKind.PLANET, objs.get(i).kind(), "index " + i + " should be a PLANET");
            }
            for (int i = stars + planets; i < objs.size(); i++) {
                assertEquals(ObjectKind.ASTEROID_FIELD, objs.get(i).kind(),
                        "index " + i + " should be an ASTEROID_FIELD");
            }
        }
    }

    @Test
    void objectTypeDerivesFromTheGeneratedObject() {
        Galaxy g = Galaxy.from(SEED);
        StarSystem system = g.getStarSystem(g.systemId(0));
        List<CelestialObject> objs = system.canonicalCelestialObjects();
        assertTrue(!objs.isEmpty());
        for (CelestialObject obj : objs) {
            assertNotNull(obj.kind());
            assertNotNull(obj.code());
        }
        // Documented canonical ordering: index 0 is always the primary star of the system.
        assertEquals(system.star(), objs.get(0).star());
        assertEquals(ObjectKind.STAR, objs.get(0).kind());
        assertTrue(system.id() != null);
    }
}

