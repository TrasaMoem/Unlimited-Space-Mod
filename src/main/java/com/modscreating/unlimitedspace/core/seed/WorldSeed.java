package com.modscreating.unlimitedspace.core.seed;

/**
 * Authoritative entry point of the procedural pipeline. Represents the Minecraft world
 * seed (as returned by {@code server.overworld().getSeed()}) and feeds the deterministic
 * domain seed chain. Pure domain; no Minecraft types.
 *
 * <p>Lifecycle (approved Phase R7 architecture):
 * <pre>{@code
 * WorldSeed
 *   -> GalaxySeed   (Seeds.galaxy)
 *   -> StarSystemSeed (Seeds.starSystem)
 *   -> PlanetSeed    (Seeds.planet)
 *   -> PlanetProperties
 *   -> PlanetWorldgenProfile
 *   -> PlanetChunkGenerator (MC side, runtime-derived)
 * }</pre>
 *
 * @param value the 64-bit Minecraft world seed
 */
public record WorldSeed(long value) {

    public static WorldSeed of(long value) {
        return new WorldSeed(value);
    }

    /**
     * Authoritative galaxy seed derived from this world seed. Replaces the old
     * {@code ProofPlanet.CANONICAL_WORLD_SEED = 0x5EEDCAFE} constant — planets are now
     * derived from the REAL Minecraft world seed, not a frozen fixture.
     */
    public GalaxySeed galaxySeed() {
        return new GalaxySeed(Seeds.galaxy(value));
    }

    /** Star-system seed for the given (stable) system index. */
    public long starSystemSeed(int systemIndex) {
        return Seeds.starSystem(galaxySeed().value(), systemIndex);
    }

    /** Planet seed for the given system + orbit slot. */
    public long planetSeed(int systemIndex, int orbitIndex) {
        return Seeds.planet(starSystemSeed(systemIndex), orbitIndex);
    }

    /** A cluster's stable seed for the given system + cluster index. */
    public long asteroidSeed(int systemIndex, int clusterIndex) {
        return Seeds.asteroidField(starSystemSeed(systemIndex), clusterIndex);
    }

    /** Convenience: an {@link com.modscreating.unlimitedspace.core.seed.AsteroidSeed} for the slot. */
    public com.modscreating.unlimitedspace.core.seed.AsteroidSeed asteroid(int systemIndex, int clusterIndex) {
        return com.modscreating.unlimitedspace.core.seed.AsteroidSeed.forSlot(starSystemSeed(systemIndex), clusterIndex);
    }
}