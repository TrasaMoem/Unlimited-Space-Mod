package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;

/**
 * The canonical PROOF planet for the Phase-1 vertical slice.
 *
 * <p>It is a single, fixed celestial body (star system 0, orbit 0) derived from a fixed
 * canonical world seed. This is deliberately a KNOWN, NAMED planet similar to how Creating
 * Space ships a named Earth/Mars — not a claim that the whole (future) universe is a single
 * planet. The purpose of this class is to prove the architectural chain:
 *
 * <pre>
 * canonical world seed
 *   -&gt; GalaxySeed -&gt; StarSystemSeed -&gt; PlanetSeed -&gt; PlanetProperties
 *   -&gt; PlanetWorldgenProfile -&gt; (PlanetChunkGenerator) -&gt; Minecraft planet world
 * </pre>
 *
 * Pure domain data: no Minecraft types, no Creating Space types. Because the identity is
 * derived from {@link PlanetId} and deterministic seeds (never a display name, never a
 * coordinate), the same inputs always reconstruct the same planet and world.
 */
public final class ProofPlanet {

    /** Fixed root seed for this slice's named planet (stable across restarts). */
    public static final long CANONICAL_WORLD_SEED = 0x5EEDCAFEL;

    public static final int SYSTEM_INDEX = 0;
    public static final int ORBIT_INDEX = 0;

    private ProofPlanet() {}

    public static GalaxySeed galaxySeed() {
        return new GalaxySeed(Seeds.galaxy(CANONICAL_WORLD_SEED));
    }

    public static StarSystemId systemId() {
        return StarSystemId.of(SYSTEM_INDEX);
    }

    public static PlanetId planetId() {
        return PlanetId.of(systemId(), ORBIT_INDEX);
    }

    public static long systemSeed() {
        return Seeds.starSystem(galaxySeed().value(), SYSTEM_INDEX);
    }

    public static PlanetSeed planetSeed() {
        return PlanetSeed.forSlot(systemSeed(), ORBIT_INDEX);
    }

    public static PlanetDefinition definition() {
        return PlanetPropertyGenerator.define(planetSeed(), systemId(), ORBIT_INDEX);
    }

    public static Planet planet() {
        return PlanetPropertyGenerator.generate(definition());
    }

    public static PlanetProperties properties() {
        return planet().properties();
    }

    /** The deterministic worldgen profile this planet's surface world is built from. */
    public static PlanetWorldgenProfile worldProfile() {
        return PlanetWorldgenProfile.from(planet());
    }

    /** The surface world destination identity (planet surface). */
    public static WorldDestination surfaceDestination() {
        return WorldDestination.planetSurface(planetId(), planetSeed());
    }

    /** The orbit world destination identity (planet orbit). */
    public static WorldDestination orbitDestination() {
        return WorldDestination.planetOrbit(planetId(), planetSeed());
    }
}