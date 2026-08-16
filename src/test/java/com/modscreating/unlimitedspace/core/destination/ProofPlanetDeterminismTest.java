package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proof of the Phase-1 core guarantee: the whole chain
 * {@code WorldSeed -> PlanetId -> PlanetSeed -> PlanetProperties -> World generation identity}
 * is deterministic. "Restart" is modelled by recomputing the same inputs on a fresh object
 * graph; a different world seed must produce a different planet and different world.
 */
class ProofPlanetDeterminismTest {

    /** Build a {@link Planet} purely from a world seed (replicates the production chain). */
    private static Planet planetFor(long worldSeed) {
        GalaxySeed galaxy = new GalaxySeed(Seeds.galaxy(worldSeed));
        long systemSeed = Seeds.starSystem(galaxy.value(), ProofPlanet.SYSTEM_INDEX);
        PlanetSeed planetSeed = PlanetSeed.forSlot(systemSeed, ProofPlanet.ORBIT_INDEX);
        StarSystemId systemId = StarSystemId.of(ProofPlanet.SYSTEM_INDEX);
        PlanetDefinition def = PlanetPropertyGenerator.define(planetSeed, systemId, ProofPlanet.ORBIT_INDEX);
        return PlanetPropertyGenerator.generate(def);
    }

    @Test
    void sameInputsReconstructIdenticalPlanetAcrossRestart() {
        // Two independent recomputations of the same canonical identity must be identical.
        Planet p1 = ProofPlanet.planet();
        Planet p2 = ProofPlanet.planet();
        assertEquals(p1, p2, "restart must not change the planet");
        assertEquals(ProofPlanet.definition(), ProofPlanet.definition(), "definition must be stable");

        // Same for the world identities.
        assertEquals(ProofPlanet.surfaceDestination(), ProofPlanet.surfaceDestination());
        assertEquals(ProofPlanet.orbitDestination(), ProofPlanet.orbitDestination());
    }

    @Test
    void sameSeedSameIdentitySameProperties() {
        Planet p1 = planetFor(ProofPlanet.CANONICAL_WORLD_SEED);
        Planet p2 = planetFor(ProofPlanet.CANONICAL_WORLD_SEED);
        assertEquals(p1, p2);
        assertEquals(p1.id(), p2.id());
        assertEquals(p1.properties(), p2.properties());
    }

    @Test
    void differentWorldSeedYieldsDifferentPlanetAndWorld() {
        long seedA = ProofPlanet.CANONICAL_WORLD_SEED;
        long seedB = seedA ^ 0xDEADBEEFL;
        Planet a = planetFor(seedA);
        Planet b = planetFor(seedB);

        // Identity is orbit-index based, so the ID is the same, but the PROCEDURAL content differs.
        assertEquals(a.id(), b.id(), "same slot => same stable id");
        assertNotEquals(a.properties().temperature(), b.properties().temperature(),
                "different world seed must change planet properties");

        PlanetId idForBoth = a.id();
        long wsa = WorldDestination.planetSurface(idForBoth, a.seed()).worldSeed();
        long wsb = WorldDestination.planetSurface(idForBoth, b.seed()).worldSeed();
        assertNotEquals(wsa, wsb, "different world seed must change the surface world seed");
    }

    @Test
    void surfaceAndOrbitAreDistinctWorlds() {
        WorldDestination surface = ProofPlanet.surfaceDestination();
        WorldDestination orbit = ProofPlanet.orbitDestination();
        assertNotEquals(surface.worldSeed(), orbit.worldSeed());
        assertNotEquals(surface.code(), orbit.code());
        assertEquals(WorldKind.SURFACE, surface.worldKind());
        assertEquals(WorldKind.ORBIT, orbit.worldKind());
    }

    @Test
    void worldIdentityIsNameIndependent() {
        assertEquals("system_0000_planet_00_surface", ProofPlanet.surfaceDestination().code());
        assertEquals("system_0000_planet_00_orbit", ProofPlanet.orbitDestination().code());

        // The destination code must never depend on a display name: it is derived purely
        // from the stable body id (system index + orbit index), and the world seed from the
        // deterministic planet seed. Changing no visible string must change either.
        assertEquals(ProofPlanet.surfaceDestination(),
                WorldDestination.planetSurface(ProofPlanet.planetId(), ProofPlanet.planetSeed()));
    }
}