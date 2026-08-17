package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.PlanetSeed;

import java.util.ArrayList;
import java.util.List;

/**
 * Bundle of the canonical definition and fully generated properties of a planet.
 * Pure domain data; no Minecraft coupling.
 *
 * <p>A planet is also the canonical owner of its 0..5 moons ({@link #moons()}),
 * derived deterministically from the planet seed.
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

    /** Deterministic 0..5 moon count for this planet. */
    public int moonCount() {
        return MoonPropertyGenerator.moonCount(definition.seed().value());
    }

    /** All moons of this planet (domain metadata only; never creates dimensions). */
    public List<Moon> moons() {
        int count = moonCount();
        long planetSeed = definition.seed().value();
        PlanetId planetId = definition.id();
        List<Moon> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(MoonPropertyGenerator.generate(new MoonId(planetId, i), planetSeed, i));
        }
        return list;
    }

    /** The moon at a given index, generated deterministically. */
    public Moon moon(int moonIndex) {
        if (moonIndex < 0 || moonIndex >= moonCount()) {
            throw new IllegalArgumentException("moonIndex " + moonIndex + " out of range 0.." + (moonCount() - 1));
        }
        return MoonPropertyGenerator.generate(
                new MoonId(definition.id(), moonIndex),
                definition.seed().value(), moonIndex);
    }
}
