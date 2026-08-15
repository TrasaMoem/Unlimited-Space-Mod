package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.PlanetSeed;

/**
 * Bundle of the canonical definition and fully generated properties of a planet.
 * Pure domain data; no Minecraft coupling.
 *
 * @param definition the stable definition / identity
 * @param properties the derived properties
 */
public record Planet(PlanetDefinition definition, PlanetProperties properties) {

    public PlanetId id() {
        return definition.id();
    }

    public PlanetSeed seed() {
        return definition.seed();
    }
}
