package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12 resolver-level tests (test sets 8-10): destination resolution produces the
 * correct planet/moon identity, asteroid/space never resolve as a planet body,
 * and everything is deterministic from the world seed.
 */
class CelestialVisualResolverTest {

    private static final long WORLD_SEED = 1337L;

    /**
     * The datapack moon destinations ({@code system_0000_planet_00_moon_00}) exist only
     * for world seeds whose planet 00 has at least one moon. Find such a seed
     * deterministically so the moon resolution tests exercise a real body.
     */
    private static long worldSeedWithMoon() {
        for (long s = 1; s < 5000; s++) {
            Planet p = Galaxy.from(s).getStarSystem(StarSystemId.of(0)).getPlanet(0);
            if (p.moonCount() >= 1) return s;
        }
        return com.modscreating.unlimitedspace.core.destination.ProofPlanet.CANONICAL_WORLD_SEED;
    }

    @Test
    void planetOrbitDestinationResolvesToCorrectPlanetId() {
        ResolvedVisual v = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", WORLD_SEED);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.PLANET, v.kind());
        assertEquals(PlanetId.of(StarSystemId.of(0), 1), v.planetId());
        assertTrue(v.hasBody());
        assertFalse(v.onSurface());
        // procedural sky colour data must be present
        assertNotEquals(0, v.skyColorArgb());
        assertNotEquals(0, v.fogColorArgb());
    }

    @Test
    void planetSurfaceDestinationIsSurfaceWorld() {
        ResolvedVisual v = CelestialVisualResolver.compute("planet/system_0000_planet_00/surface", WORLD_SEED);
        assertNotNull(v);
        assertEquals(PlanetId.of(StarSystemId.of(0), 0), v.planetId());
        assertTrue(v.onSurface());
    }

    @Test
    void moonDestinationResolvesToMoonIdAndParentDisc() {
        long seed = worldSeedWithMoon();
        ResolvedVisual v = CelestialVisualResolver.compute("moon/system_0000_planet_00_moon_00/orbit", seed);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.MOON, v.kind());
        assertEquals("system_0000_planet_00_moon_00", v.moonId().code());
        assertTrue(v.hasBody());
        assertNotEquals(0, v.parentDiscArgb(), "orbit moon should show the parent planet");
    }

    @Test
    void moonSurfaceHasNoParentDisc() {
        long seed = worldSeedWithMoon();
        ResolvedVisual v = CelestialVisualResolver.compute("moon/system_0000_planet_00_moon_00/surface", seed);
        assertNotNull(v);
        assertEquals(0, v.parentDiscArgb());
        assertTrue(v.onSurface());
    }

    @Test
    void asteroidDestinationNeverResolvesAsPlanet() {
        ResolvedVisual v = CelestialVisualResolver.compute("asteroid/system_0000_asteroid_00", WORLD_SEED);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.ASTEROID, v.kind());
        assertNull(v.planetId());
        assertNull(v.moonId());
        assertFalse(v.hasBody());
    }

    @Test
    void spaceDestinationIsVoidWithNoBody() {
        ResolvedVisual v = CelestialVisualResolver.compute("space", WORLD_SEED);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.VOID, v.kind());
        assertFalse(v.hasBody());
        assertNull(v.planetId());
    }

    @Test
    void sameSeedAndPathAlwaysResolveIdentically() {
        ResolvedVisual a = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", WORLD_SEED);
        ResolvedVisual b = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", WORLD_SEED);
        assertEquals(a, b);
        // different world seeds differ (visual identity is seed-driven)
        ResolvedVisual c = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", WORLD_SEED + 1L);
        assertNotEquals(a.skyColorArgb(), c.skyColorArgb());
    }

    @Test
    void starListFollowsSystemMultiplicity() {
        ResolvedVisual v = CelestialVisualResolver.compute("planet/system_0000_planet_00/orbit", WORLD_SEED);
        assertNotNull(v);
        assertFalse(v.stars().isEmpty());
        assertTrue(v.stars().size() <= 3, "systems have 1-3 stars, got " + v.stars().size());
    }

    /** R12.3 Bug #2 — every other planet/moon of the system is visible in the orbit sky. */
    @Test
    void orbitShowsSiblingBodiesOfTheSystem() {
        // Pick a seed whose system_0000 has several planets, so an orbit really has
        // *other* bodies to show rather than just stars and the planet below.
        long seed = worldSeedWithSiblingPlanets();
        ResolvedVisual v = CelestialVisualResolver.compute("planet/system_0000_planet_00/orbit", seed);
        assertNotNull(v);
        assertFalse(v.bodies().isEmpty(),
                "orbit must show the system's other bodies, not just stars and the planet below");

        // The body currently being orbited is drawn big below the camera (not as a sibling),
        // so its own code must never appear in the sibling list.
        String orbited = v.planetId().code();
        assertTrue(v.bodies().stream().noneMatch(b -> b.bodyCode().equals(orbited)),
                "the orbited planet must not also appear as a distant sibling");

        // Every sibling must be renderable — positive apparent size.
        assertTrue(v.bodies().stream().allMatch(b -> b.apparentSize() > 0.0f),
                "every sibling must have a positive apparent size");
    }

    /**
     * A seed whose system_0000 owns at least three planets, so an orbit provably has
     * sibling bodies to show. Falls back to a canonical seed (still safe) if none is found.
     */
    private static long worldSeedWithSiblingPlanets() {
        for (long s = 1; s < 20000; s++) {
            if (Galaxy.from(s).getStarSystem(StarSystemId.of(0)).planetCount() >= 3) {
                return s;
            }
        }
        return WORLD_SEED;
    }

    /** R12.3 Bug #2 — the asteroid / space sky also lists the host system's bodies. */
    @Test
    void asteroidAndSpaceShowSiblingBodies() {
        for (String path : new String[]{"asteroid/system_0000_asteroid_00", "space"}) {
            ResolvedVisual v = CelestialVisualResolver.compute(path, WORLD_SEED);
            assertNotNull(v);
            assertFalse(v.bodies().isEmpty(),
                    path + " must show the host system's bodies in the background sky");
        }
    }

    // ---------------------------------------------------------------- R12.6 multi-body context

    /** Planet orbit: the current planet is the anchor (never a sibling) + siblings + stars exist. */
    @Test
    void planetOrbitContainsCurrentSiblingsAndStars() {
        long seed = worldSeedWithSiblingPlanets();
        ResolvedVisual v = CelestialVisualResolver.compute("planet/system_0000_planet_00/orbit", seed);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.PLANET, v.kind());
        assertTrue(v.hasBody());
        // current anchor planet exists and is not duplicated as a distant sibling
        String current = v.planetId().code();
        assertTrue(v.bodies().stream().noneMatch(b -> b.bodyCode().equals(current)),
                "current planet must appear exactly once (as the anchor)");
        // the system's other planets must be visible as siblings
        assertTrue(v.bodies().stream().anyMatch(b -> b.apparentSize() > 0f),
                "planet orbit must show at least one distant sibling body");
        // system stars always present
        assertFalse(v.stars().isEmpty(), "planet orbit must show the system star(s)");
    }

    /** Moon orbit: parent planet featured at ~1/3 current-moon scale, current moon never duplicated. */
    @Test
    void moonOrbitContainsParentAtOneThirdScale() {
        long seed = worldSeedWithMoon();
        ResolvedVisual v = CelestialVisualResolver.compute("moon/system_0000_planet_00_moon_00/orbit", seed);
        assertNotNull(v);
        assertEquals(CelestialBodyPath.Kind.MOON, v.kind());
        assertTrue(v.hasBody());

        String parentCode = PlanetId.of(StarSystemId.of(0), 0).code();
        long parentCount = v.bodies().stream().filter(b -> b.bodyCode().equals(parentCode)).count();
        assertEquals(1, parentCount, "parent planet must appear exactly once (featured)");

        SiblingBody parent = v.bodies().stream().filter(b -> b.bodyCode().equals(parentCode)).findFirst().orElseThrow();
        assertEquals(CelestialVisualScale.parentBodyHalf(), parent.apparentSize(), 1e-3f,
                "parent planet must be ~1/3 of the current body's size");

        String currentMoonCode = v.moonId().code();
        assertTrue(v.bodies().stream().noneMatch(b -> b.bodyCode().equals(currentMoonCode)),
                "current moon must appear exactly once (as the anchor), never as a sibling");
    }

    /** The same seed + path always yields the same orbit context (deterministic). */
    @Test
    void orbitContextDeterministicForSameSeed() {
        long seed = worldSeedWithSiblingPlanets();
        ResolvedVisual a = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", seed);
        ResolvedVisual b = CelestialVisualResolver.compute("planet/system_0000_planet_01/orbit", seed);
        assertNotNull(a);
        assertEquals(a, b);
        // sibling scales/positions are stable across resolutions
        assertEquals(a.bodies(), b.bodies());
    }
}