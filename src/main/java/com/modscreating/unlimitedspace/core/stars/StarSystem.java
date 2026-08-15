package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.galaxy.GalacticPosition;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
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

    @Override
    public String toString() {
        return id.code() + "[" + position() + ", " + star().type() + "]";
    }
}
