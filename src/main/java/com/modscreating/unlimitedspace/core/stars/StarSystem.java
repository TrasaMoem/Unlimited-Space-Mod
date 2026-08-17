package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.galaxy.GalacticPosition;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.seed.AsteroidSeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Immutable DATA description of a single star system: stable id, seed,
 * deterministic position and its star. Planets are derived lazily by orbit index.
 * This class does NOT create Minecraft dimensions, nor depends on
 * ServerLevel / ChunkGenerator / BlockState.
 */
public final class StarSystem {

    private final StarSystemId id;
    private final long seed;
    private final GalacticPosition position;
    private final Star star;

    public StarSystem(StarSystemId id, long seed, GalacticPosition position, Star star) {
        this.id = id;
        this.seed = seed;
        this.position = position;
        this.star = star;
    }

    public StarSystemId id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public StarSystemSeed seedObject() {
        return new StarSystemSeed(seed);
    }

    public GalacticPosition position() {
        return position;
    }

    public Star star() {
        return star;
    }

    /** Planet seed for the given orbit slot; independent of generation order. */
    public long planetSeed(int orbitIndex) {
        return Seeds.planet(seed, orbitIndex);
    }

    /** Planet definition for the given orbit slot (generation order independent). */
    public PlanetDefinition definePlanet(int orbitIndex) {
        PlanetSeed planetSeed = PlanetSeed.forSlot(seed, orbitIndex);
        return PlanetPropertyGenerator.define(planetSeed, id, orbitIndex);
    }

    /** Full generated planet data for the given orbit slot (lazy). */
    public Planet getPlanet(int orbitIndex) {
        PlanetDefinition def = definePlanet(orbitIndex);
        return PlanetPropertyGenerator.generate(def);
    }

    /** Convenience: the moons of the planet at {@code orbitIndex} (domain metadata only). */
    public java.util.List<com.modscreating.unlimitedspace.core.planets.Moon> moons(int orbitIndex) {
        return getPlanet(orbitIndex).moons();
    }

    /** Convenience: moon count of the planet at {@code orbitIndex}. */
    public int moonCount(int orbitIndex) {
        return getPlanet(orbitIndex).moonCount();
    }

    /** A cluster's stable seed for the given cluster index (generation order independent). */
    public long asteroidSeed(int clusterIndex) {
        return Seeds.asteroidField(seed, clusterIndex);
    }

    /**
     * The asteroid cluster at the given cluster index (domain metadata only; never a world).
     * Parent relationship is explicit: the cluster's {@code parentSystem()} == this system's id.
     */
    public AsteroidCluster asteroid(int clusterIndex) {
        return AsteroidCluster.of(AsteroidClusterId.of(id, clusterIndex),
                AsteroidSeed.forSlot(seed, clusterIndex));
    }

    @Override
    public String toString() {
        return id.code() + "[" + position() + ", " + star().type() + "]";
    }
}
