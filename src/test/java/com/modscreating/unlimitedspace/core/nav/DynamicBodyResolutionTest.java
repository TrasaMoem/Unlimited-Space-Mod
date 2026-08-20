package com.modscreating.unlimitedspace.core.nav;

import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// R14.4 pure-domain suite: Minecraft-free asserts on DestinationResolver + deterministic identities.
class DynamicBodyResolutionTest {

    private static final long SEED = 777L;

    private static int[] findFirst(ObjectKind kind, Predicate<CelestialObject> predicate) {
        Galaxy g = Galaxy.from(SEED);
        for (int s = 0; s < 12; s++) {
            StarSystem system = g.getStarSystem(g.systemId(s));
            List<CelestialObject> objs = system.canonicalCelestialObjects();
            for (int o = 0; o < objs.size(); o++) {
                CelestialObject obj = objs.get(o);
                if (obj.kind() == kind && predicate.test(obj)) {
                    return new int[]{s, o};
                }
            }
        }
        throw new AssertionError("no matching object (kind=" + kind + ") for seed " + SEED);
    }

    private static int[] findPlanetWithMoon() { return findFirst(ObjectKind.PLANET, o -> o.planet().moonCount() >= 1); }
    private static int[] findPlanetWithoutMoon() { return findFirst(ObjectKind.PLANET, o -> o.planet().moonCount() == 0); }
    private static int[] findAsteroidField() { return findFirst(ObjectKind.ASTEROID_FIELD, o -> true); }
    private static int[] findStar() { return findFirst(ObjectKind.STAR, o -> true); }

    @Test
    void planetSurfaceResolves() {
        int[] p = findPlanetWithMoon();
        ResolvedDestination r = DestinationResolver.resolve(Galaxy.from(SEED), p[0], p[1], 0);
        assertTrue(r.ok());
        assertEquals(DestinationKind.PLANET_SURFACE, r.destinationKind());
        assertNotNull(r.planet());
    }

    @Test
    void planetOrbitResolves() {
        int[] p = findPlanetWithMoon();
        ResolvedDestination r = DestinationResolver.resolve(Galaxy.from(SEED), p[0], p[1], 1);
        assertTrue(r.ok());
        assertEquals(DestinationKind.PLANET_ORBIT, r.destinationKind());
        assertEquals(1, r.destinationIndex());
    }

    @Test
    void planetMoonSurfaceAndOrbitResolve() {
        int[] p = findPlanetWithMoon();
        ResolvedDestination m0s = DestinationResolver.resolve(Galaxy.from(SEED), p[0], p[1], 2);
        ResolvedDestination m0o = DestinationResolver.resolve(Galaxy.from(SEED), p[0], p[1], 3);
        assertTrue(m0s.ok());
        assertTrue(m0o.ok());
        assertEquals(DestinationKind.MOON_SURFACE, m0s.destinationKind());
        assertEquals(DestinationKind.MOON_ORBIT, m0o.destinationKind());
        assertNotNull(m0s.moon());
        assertEquals(0, m0s.moon().moonIndex());
        assertNotNull(m0o.moon());
        assertEquals(0, m0o.moon().moonIndex());
        assertEquals(m0s.moon().parentPlanetId(), m0s.planet().id());
    }

    @Test
    void planetSurfaceRlIdentityIsDeterministic() {
        int[] p = findPlanetWithMoon();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination a = DestinationResolver.resolve(g, p[0], p[1], 0);
        ResolvedDestination b = DestinationResolver.resolve(g, p[0], p[1], 0);
        assertTrue(a.ok());
        String pathA = PlanetWorldBinding.locationPath(a.planet().id(), WorldKind.SURFACE);
        String pathB = PlanetWorldBinding.locationPath(b.planet().id(), WorldKind.SURFACE);
        assertEquals(pathA, pathB);
        assertTrue(pathA.matches("planet/system_\\d{4}_planet_\\d{2}/surface"), pathA);
        assertTrue(PlanetWorldBinding.locationPath(a.planet().id(), WorldKind.ORBIT)
                .matches("planet/system_\\d{4}_planet_\\d{2}/orbit"));
    }

    @Test
    void moonIdentityFromIdCodeIsDeterministic() {
        int[] p = findPlanetWithMoon();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination m0s = DestinationResolver.resolve(g, p[0], p[1], 2);
        assertTrue(m0s.ok());
        String code = m0s.moon().id().code();
        assertTrue(code.matches("system_\\d{4}_planet_\\d{2}_moon_\\d{2}"), code);
        assertEquals(code, DestinationResolver.resolve(g, p[0], p[1], 2).moon().id().code());
    }

    @Test
    void asteroidClusterResolvesAndIdStable() {
        int[] a = findAsteroidField();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination r = DestinationResolver.resolve(g, a[0], a[1], 0);
        assertTrue(r.ok());
        assertEquals(DestinationKind.ASTEROID_FIELD, r.destinationKind());
        assertNotNull(r.asteroid());
        String code = r.asteroid().id().code();
        assertTrue(code.matches("system_\\d{4}_asteroid_\\d{2}"), code);
    }

    @Test
    void starBodyAndOrbitResolve() {
        int[] s = findStar();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination body = DestinationResolver.resolve(g, s[0], s[1], 0);
        ResolvedDestination orbit = DestinationResolver.resolve(g, s[0], s[1], 1);
        assertTrue(body.ok());
        assertTrue(orbit.ok());
        assertEquals(DestinationKind.STAR_BODY, body.destinationKind());
        assertEquals(DestinationKind.STAR_ORBIT, orbit.destinationKind());
        assertNotNull(body.star());
        assertNotNull(orbit.star());
    }
    @Test
    void differentBodyTypesDoNotCollide() {
        int[] p = findPlanetWithMoon();
        int[] a = findAsteroidField();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination surface = DestinationResolver.resolve(g, p[0], p[1], 0);
        ResolvedDestination orbit = DestinationResolver.resolve(g, p[0], p[1], 1);
        ResolvedDestination asteroid = DestinationResolver.resolve(g, a[0], a[1], 0);
        assertTrue(surface.ok() && orbit.ok() && asteroid.ok());
        assertEquals(DestinationKind.PLANET_SURFACE, surface.destinationKind());
        assertEquals(DestinationKind.PLANET_ORBIT, orbit.destinationKind());
        assertEquals(DestinationKind.ASTEROID_FIELD, asteroid.destinationKind());
        // Asteroid cluster identity shares no prefix with a planet/moon path and is not mistaken for one.
        String asteroidCode = asteroid.asteroid().id().code();
        assertTrue(asteroidCode.startsWith(surface.planet().id().system().code() + "_asteroid_"));
        assertTrue(PlanetWorldBinding.locationPath(surface.planet().id(), WorldKind.SURFACE)
                .startsWith("planet/" + surface.planet().id().system().code()));
    }

    @Test
    void asteroidAnyDestinationIndexMapsToSameCluster() {
        int[] a = findAsteroidField();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination d0 = DestinationResolver.resolve(g, a[0], a[1], 0);
        ResolvedDestination d3 = DestinationResolver.resolve(g, a[0], a[1], 3);
        assertTrue(d0.ok());
        assertTrue(d3.ok());
        assertEquals(d0.asteroid().id(), d3.asteroid().id(),
                "every non-negative destination index maps to the SAME asteroid cluster");
    }

    @Test
    void invalidDestinationFailsExplicitly() {
        // Negative destination index is rejected up-front (no silent clamping).
        int[] p = findPlanetWithMoon();
        ResolvedDestination neg = DestinationResolver.resolve(Galaxy.from(SEED), p[0], p[1], -1);
        assertTrue(neg.isError());
        assertNull(neg.destinationKind());

        // A planet with zero moons has no valid moon destinations.
        int[] noMoon = findPlanetWithoutMoon();
        ResolvedDestination moonDest = DestinationResolver.resolve(Galaxy.from(SEED), noMoon[0], noMoon[1], 2);
        assertTrue(moonDest.isError());
        assertNull(moonDest.destinationKind());
        assertNull(moonDest.moon());

        // Star supports only 0 (body) and 1 (orbit).
        int[] star = findStar();
        ResolvedDestination starExtra = DestinationResolver.resolve(Galaxy.from(SEED), star[0], star[1], 2);
        assertTrue(starExtra.isError());
    }

        @Test
    void starOrbitIsNeverReinterpretedAsPlanetOrAsteroid() {
        int[] s = findStar();
        Galaxy g = Galaxy.from(SEED);
        ResolvedDestination orbit = DestinationResolver.resolve(g, s[0], s[1], 1);
        assertTrue(orbit.ok());
        assertEquals(DestinationKind.STAR_ORBIT, orbit.destinationKind());
        assertNull(orbit.moon());
        assertNull(orbit.planet());
        assertNull(orbit.asteroid());
    }
}
