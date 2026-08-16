package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the vertical-slice guarantee that a planet is its own deterministic world:
 * the same {@code (planet, world kind)} always yields the same world seed and stable,
 * name-independent code, and distinct worlds are distinct.
 */
class WorldDestinationDerivationTest {

    private static final StarSystemId SYSTEM_4 = StarSystemId.of(4);

    private PlanetSeed planetSeed(int orbit) {
        // Deterministic chain: galaxy -> system -> planet (identical to production).
        GalaxySeed galaxy = new GalaxySeed(0x123456789ABCDEF0L);
        long systemSeed = Seeds.starSystem(galaxy.value(), SYSTEM_4.index());
        return PlanetSeed.forSlot(systemSeed, orbit);
    }

    @Test
    void samePlanetAndKindAlwaysYieldsSameWorld() {
        int orbit = 7;
        PlanetSeed seed = planetSeed(orbit);
        PlanetId id = PlanetId.of(SYSTEM_4, orbit);

        WorldDestination a = WorldDestination.planetSurface(id, seed);
        WorldDestination b = WorldDestination.planetSurface(id, seed);

        assertEquals(a.worldSeed(), b.worldSeed(), "surface world seed must be stable");
        assertEquals(a.code(), b.code(), "destination code must be stable");
        assertEquals(WorldKind.SURFACE, a.worldKind());
    }

    @Test
    void surfaceAndOrbitAreDistinctWorlds() {
        int orbit = 7;
        PlanetSeed seed = planetSeed(orbit);
        PlanetId id = PlanetId.of(SYSTEM_4, orbit);

        WorldDestination surface = WorldDestination.planetSurface(id, seed);
        WorldDestination orbitWorld = WorldDestination.planetOrbit(id, seed);

        assertNotEquals(surface.worldSeed(), orbitWorld.worldSeed(),
                "surface and orbit must have different world seeds");
        assertNotEquals(surface.code(), orbitWorld.code());
        assertEquals(WorldKind.ORBIT, orbitWorld.worldKind());
        assertTrue(surface.code().endsWith("_surface"));
        assertTrue(orbitWorld.code().endsWith("_orbit"));
    }

    @Test
    void differentPlanetYieldsDifferentWorld() {
        int orbitA = 7;
        int orbitB = 8;
        WorldDestination a = WorldDestination.planetSurface(PlanetId.of(SYSTEM_4, orbitA), planetSeed(orbitA));
        WorldDestination b = WorldDestination.planetSurface(PlanetId.of(SYSTEM_4, orbitB), planetSeed(orbitB));

        assertNotEquals(a.worldSeed(), b.worldSeed(), "different planets must have different world seeds");
        assertNotEquals(a.code(), b.code(), "different planets must have different codes");
    }

    @Test
    void sameOrbitInDifferentSystemsYieldsDifferentWorld() {
        int orbit = 3;
        GalaxySeed galaxy = new GalaxySeed(0x123L);
        long system0 = Seeds.starSystem(galaxy.value(), 0);
        long system1 = Seeds.starSystem(galaxy.value(), 1);

        WorldDestination a = WorldDestination.planetSurface(PlanetId.of(StarSystemId.of(0), orbit), PlanetSeed.forSlot(system0, orbit));
        WorldDestination b = WorldDestination.planetSurface(PlanetId.of(StarSystemId.of(1), orbit), PlanetSeed.forSlot(system1, orbit));

        assertNotEquals(a.worldSeed(), b.worldSeed(), "same orbit in different systems must differ");
    }

    @Test
    void differentGalaxySeedChangesWorldSeed() {
        int orbit = 2;
        long systemA = Seeds.starSystem(new GalaxySeed(111L).value(), 0);
        long systemB = Seeds.starSystem(new GalaxySeed(222L).value(), 0);
        PlanetId id = PlanetId.of(StarSystemId.of(0), orbit);

        WorldDestination a = WorldDestination.planetSurface(id, PlanetSeed.forSlot(systemA, orbit));
        WorldDestination b = WorldDestination.planetSurface(id, PlanetSeed.forSlot(systemB, orbit));

        assertNotEquals(a.worldSeed(), b.worldSeed(), "different world seed must change planet worlds");
    }

    @Test
    void codeIsNameIndependentAndDeterministic() {
        int orbit = 1;
        WorldDestination d = WorldDestination.planetSurface(PlanetId.of(SYSTEM_4, orbit), planetSeed(orbit));
        assertEquals("system_0004_planet_01_surface", d.code(),
                "code derives only from the stable body id, never a display name");
    }
}