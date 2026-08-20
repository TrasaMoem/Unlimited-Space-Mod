package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R13.15 — destination resolution. Verifies the single {@link DestinationResolver} maps a
 * (system, object, destination) triple deterministically for planets/moons/stars/asteroid
 * fields, and rejects invalid inputs explicitly.
 */
class DestinationResolverTest {

    private static final long SEED = 777L;

    private static Galaxy galaxy() {
        return Galaxy.from(SEED);
    }

    /** Find (systemIndex, canonicalObjectIndex, moonCount) for a planet that has >=1 moon. */
    private record PlanetWithMoon(int systemIndex, int objectIndex, int moonCount) {
    }

    private static PlanetWithMoon findPlanetWithMoon() {
        Galaxy g = galaxy();
        for (int s = 0; s < 64; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int i = 0; i < objs.size(); i++) {
                CelestialObject obj = objs.get(i);
                if (obj.kind() == ObjectKind.PLANET && obj.planet().moonCount() >= 1) {
                    return new PlanetWithMoon(s, i, obj.planet().moonCount());
                }
            }
        }
        throw new AssertionError("no planet with a moon found for seed " + SEED);
    }

    /** Find the (systemIndex, objectIndex) of the first planet entry in any system. */
    private record PlanetPos(int systemIndex, int objectIndex) {
    }

    private static PlanetPos findPlanet() {
        Galaxy g = galaxy();
        for (int s = 0; s < 32; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int i = 0; i < objs.size(); i++) {
                if (objs.get(i).kind() == ObjectKind.PLANET) {
                    return new PlanetPos(s, i);
                }
            }
        }
        throw new AssertionError("no planet entry found for seed " + SEED);
    }
    @Test
    void planetSurfaceAndOrbitResolve() {
        PlanetPos p = findPlanet();
        ResolvedDestination surf = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 0);
        ResolvedDestination orbit = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 1);
        assertTrue(surf.ok());
        assertTrue(orbit.ok());
        assertEquals(DestinationKind.PLANET_SURFACE, surf.destinationKind());
        assertEquals(DestinationKind.PLANET_ORBIT, orbit.destinationKind());
        assertNotNull(surf.planet());
        assertNotNull(orbit.planet());
        assertEquals(ObjectKind.PLANET, surf.objectKind());
    }

    @Test
    void moonSurfaceAndOrbitResolveViaPlanetDestinationIndex() {
        PlanetWithMoon p = findPlanetWithMoon();
        // destination 2 = Moon 0 Surface, 3 = Moon 0 Orbit
        ResolvedDestination s2 = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 2);
        ResolvedDestination s3 = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 3);
        assertTrue(s2.ok());
        assertTrue(s3.ok());
        assertEquals(DestinationKind.MOON_SURFACE, s2.destinationKind());
        assertEquals(DestinationKind.MOON_ORBIT, s3.destinationKind());
        assertNotNull(s2.moon());
        assertNotNull(s3.moon());
        assertEquals(0, s2.moon().moonIndex());
        assertEquals(0, s3.moon().moonIndex());
        // destination for the LAST moon must resolve within bounds
        int lastSurface = 2 + (p.moonCount() - 1) * 2;
        int lastOrbit = 3 + (p.moonCount() - 1) * 2;
        assertTrue(DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), lastSurface).ok());
        assertTrue(DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), lastOrbit).ok());
    }

    @Test
    void moonIndexBeyondCountIsInvalid() {
        PlanetWithMoon p = findPlanetWithMoon();
        int beyondSurface = 2 + p.moonCount() * 2;
        int beyondOrbit = 3 + p.moonCount() * 2;
        assertFalse(DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), beyondSurface).ok());
        assertFalse(DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), beyondOrbit).ok());
        assertEquals(ResolveError.INVALID_DESTINATION,
                DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), beyondSurface).error());
    }

    @Test
    void starBodyAndOrbitResolveButHigherIsInvalid() {
        // index 0 of any system is the primary star (documented canonical ordering).
        ResolvedDestination body = DestinationResolver.resolve(galaxy(), 0, 0, 0);
        ResolvedDestination orbit = DestinationResolver.resolve(galaxy(), 0, 0, 1);
        assertTrue(body.ok());
        assertTrue(orbit.ok());
        assertEquals(DestinationKind.STAR_BODY, body.destinationKind());
        assertEquals(DestinationKind.STAR_ORBIT, orbit.destinationKind());
        assertNotNull(body.star());
        // star destination 2 is invalid
        assertFalse(DestinationResolver.resolve(galaxy(), 0, 0, 2).ok());
        assertEquals(ResolveError.INVALID_DESTINATION,
                DestinationResolver.resolve(galaxy(), 0, 0, 2).error());
    }
    @Test
    void asteroidFieldMapsEveryDestinationToSameField() {
        Galaxy g = galaxy();
        for (int s = 0; s < 32; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int i = 0; i < objs.size(); i++) {
                CelestialObject obj = objs.get(i);
                if (obj.kind() == ObjectKind.ASTEROID_FIELD) {
                    for (int d : new int[]{0, 1, 5}) {
                        ResolvedDestination r = DestinationResolver.resolve(g, s, i, d);
                        assertTrue(r.ok(), "asteroid dest " + d + " must resolve");
                        assertEquals(DestinationKind.ASTEROID_FIELD, r.destinationKind());
                        assertNotNull(r.asteroid());
                        assertEquals(obj.asteroid().id(), r.asteroid().id(),
                                "the same field regardless of destination index");
                    }
                    return;
                }
            }
        }
        throw new AssertionError("no asteroid field entry found for seed " + SEED);
    }

    @Test
    void invalidSystemObjectAndDestinationAreExplicit() {
        ResolvedDestination noSys = DestinationResolver.resolve(galaxy(), -1, 0, 0);
        assertFalse(noSys.ok());
        assertEquals(ResolveError.INVALID_SYSTEM, noSys.error());

        int size = galaxy().getStarSystem(galaxy().systemId(0)).canonicalCelestialObjects().size();
        ResolvedDestination noObj = DestinationResolver.resolve(galaxy(), 0, size, 0);
        assertFalse(noObj.ok());
        assertEquals(ResolveError.INVALID_OBJECT, noObj.error());

        ResolvedDestination noDest = DestinationResolver.resolve(galaxy(), 0, 0, -1);
        assertFalse(noDest.ok());
        assertEquals(ResolveError.INVALID_DESTINATION, noDest.error());
    }

    @Test
    void resolutionIsDeterministic() {
        PlanetWithMoon p = findPlanetWithMoon();
        ResolvedDestination a = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 2);
        ResolvedDestination b = DestinationResolver.resolve(galaxy(), p.systemIndex(), p.objectIndex(), 2);
        assertEquals(a.destinationKind(), b.destinationKind());
        assertNotNull(a.moon());
        assertEquals(a.moon().id(), b.moon().id());
        assertEquals(a.object().code(), b.object().code());
    }
}


