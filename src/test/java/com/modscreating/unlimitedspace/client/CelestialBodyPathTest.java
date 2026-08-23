package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12 test set 8-10: dimension path → celestial identity resolution.
 * Pure parsing; no Minecraft client is required.
 */
class CelestialBodyPathTest {

    @Test
    void planetOrbitPathResolvesToCorrectPlanetId() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("planet/system_0000_planet_01/orbit");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.PLANET, r.kind());
        assertEquals(PlanetId.of(StarSystemId.of(0), 1), r.planetId());
        assertEquals(StarSystemId.of(0), r.systemId());
        assertFalse(r.surface());
    }

    @Test
    void planetSurfacePathResolvesWithSurfaceFlag() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("planet/system_0000_planet_00/surface");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.PLANET, r.kind());
        assertEquals(PlanetId.of(StarSystemId.of(0), 0), r.planetId());
        assertTrue(r.surface());
    }

    @Test
    void moonPathResolvesToCorrectMoonId() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("moon/system_0000_planet_00_moon_03/surface");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.MOON, r.kind());
        assertEquals(MoonId.of(PlanetId.of(StarSystemId.of(0), 0), 3), r.moonId());
        assertEquals(PlanetId.of(StarSystemId.of(0), 0), r.planetId());
        assertTrue(r.surface());
    }

    @Test
    void asteroidPathDoesNotResolveAsPlanet() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("asteroid/system_0007_asteroid_00");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.ASTEROID, r.kind());
        assertNull(r.planetId());
        assertNull(r.moonId());
        assertEquals(StarSystemId.of(7), r.systemId());
    }

    @Test
    void starOrbitPathResolvesToStarKind() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("star/system_0000/orbit");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.STAR, r.kind());
        assertEquals(StarSystemId.of(0), r.systemId());
        assertNull(r.planetId());
        assertNull(r.moonId());
        assertFalse(r.surface());
    }

    @Test
    void starSurfacePathResolvesToStarKindWithSurfaceFlag() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("star/system_0000/surface");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.STAR, r.kind());
        assertEquals(StarSystemId.of(0), r.systemId());
        assertTrue(r.surface());
    }


    @Test
    void spacePathResolvesToVoid() {
        CelestialBodyPath.Result r = CelestialBodyPath.parse("space");
        assertNotNull(r);
        assertEquals(CelestialBodyPath.Kind.VOID, r.kind());
        assertNull(r.planetId());
        assertNull(r.moonId());
    }

    @Test
    void nonUnlimitedSpaceNamespaceIsIgnored() {
        assertNull(CelestialBodyPath.parseDimPath("minecraft", "overworld"));
        assertNull(CelestialBodyPath.parseDimPath("creatingspace", "earth_orbit"));
    }

    @Test
    void unknownPathReturnsNull() {
        assertNull(CelestialBodyPath.parse("planet/system_0000/whatever"));
        assertNull(CelestialBodyPath.parse(""));
        assertNull(CelestialBodyPath.parse(null));
    }
}