package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Canonical DATA description of a planet: its stable identity, seed and archetype.
 * Exists before any Minecraft world does; future worldgen consumes it.
 *
 * @param id         stable planet id
 * @param seed       stable planet seed
 * @param systemId   owning star system
 * @param orbitIndex orbit slot
 * @param type       planet archetype
 */
public record PlanetDefinition(PlanetId id, PlanetSeed seed,
                               StarSystemId systemId, int orbitIndex, PlanetType type) {

    public static PlanetDefinition of(PlanetSeed seed, StarSystemId systemId, int orbitIndex, PlanetType type) {
        return new PlanetDefinition(PlanetId.of(systemId, orbitIndex), seed, systemId, orbitIndex, type);
    }

    public String displaySeedKey() {
        return id.code() + ":" + seed.value();
    }
}
