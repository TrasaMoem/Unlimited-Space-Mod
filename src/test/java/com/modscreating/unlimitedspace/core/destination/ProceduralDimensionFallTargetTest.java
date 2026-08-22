package com.modscreating.unlimitedspace.core.destination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.8 — pure {@link ProceduralDimension} orbit→surface fall-target tests. Proves that falling out
 * of a planet orbit goes to that same planet's surface, and a moon orbit to that same moon, using
 * the authoritative celestial relationship (not name suffixes or object indexes).
 */
class ProceduralDimensionFallTargetTest {

    @Test
    void planetOrbitFallsToSamePlanetSurface() {
        ProceduralDimension orbit =
                ProceduralDimension.parse("planet/system_4123_planet_01/orbit").orElseThrow();
        ProceduralDimension surface = orbit.fallTarget().orElseThrow();
        assertEquals(ProceduralDimension.Kind.PLANET_SURFACE, surface.kind());
        assertEquals(4123, surface.systemIndex());
        assertEquals(1, surface.planetIndex());
        assertEquals("planet/system_4123_planet_01/surface", surface.resourcePath());
    }

    @Test
    void moonOrbitFallsToSameMoonSurface() {
        ProceduralDimension orbit =
                ProceduralDimension.parse("moon/system_0077_planet_03_moon_02/orbit").orElseThrow();
        ProceduralDimension surface = orbit.fallTarget().orElseThrow();
        assertEquals(ProceduralDimension.Kind.MOON_SURFACE, surface.kind());
        assertEquals(3, surface.planetIndex());
        assertEquals(2, surface.moonIndex());
        assertEquals("moon/system_0077_planet_03_moon_02/surface", surface.resourcePath());
    }

    @Test
    void moonOrbitDoesNotFallToParentPlanetSurface() {
        ProceduralDimension orbit =
                ProceduralDimension.parse("moon/system_0077_planet_03_moon_02/orbit").orElseThrow();
        ProceduralDimension surface = orbit.fallTarget().orElseThrow();
        // The surface must be this moon, never the parent planet's surface.
        assertTrue(surface.resourcePath().contains("_moon_02"), surface.resourcePath());
        assertNotEquals("planet/system_0077_planet_03/surface", surface.resourcePath());
    }

    @Test
    void starOrbitFallsToStarSurface() {
        // R14.9: the star orbit (zero-g) falls to the star's own molten surface.
        ProceduralDimension orbit =
                ProceduralDimension.parse("star/system_0042/orbit").orElseThrow();
        ProceduralDimension surface = orbit.fallTarget().orElseThrow();
        assertEquals(ProceduralDimension.Kind.STAR_BODY, surface.kind());
        assertEquals(42, surface.systemIndex());
        assertEquals("star/system_0042/surface", surface.resourcePath());
    }

    @Test
    void surfacesAndNonOrbitsHaveNoFallTarget() {
        for (String path : List.of(
                "planet/system_0000_planet_00/surface",
                "moon/system_0000_planet_00_moon_00/surface",
                "asteroid/system_0000_asteroid_00",
                "star/system_0000/surface")) {
            assertTrue(ProceduralDimension.parse(path).orElseThrow().fallTarget().isEmpty(),
                    path + " must not fall to a different body");
        }
    }

    @Test
    void resourcePathRoundTrips() {
        for (String path : List.of(
                "planet/system_4123_planet_01/orbit",
                "planet/system_4123_planet_01/surface",
                "moon/system_0077_planet_03_moon_02/orbit",
                "moon/system_0077_planet_03_moon_02/surface",
                "asteroid/system_0099_asteroid_05",
                "star/system_0042/orbit")) {
            assertEquals(path, ProceduralDimension.parse(path).orElseThrow().resourcePath(), path);
        }
    }
}
