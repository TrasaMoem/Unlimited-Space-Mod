package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.MoonSeed;

/**
 * Canonical domain object of a single moon: its stable identity plus its fully
 * generated, independent properties. Pure domain data; no Minecraft coupling.
 *
 * <pre>
 * Planet
 *  └── Moon[]  (0..5 per planet)
 * </pre>
 *
 * @param id         stable moon identity (parent planet + index)
 * @param seed       deterministic moon seed (independent of parent planet)
 * @param properties fully generated moon properties
 */
public record Moon(MoonId id, MoonSeed seed, MoonProperties properties) {

    public static Moon of(MoonId id, MoonSeed seed, MoonProperties properties) {
        return new Moon(id, seed, properties);
    }

    public MoonType type() {
        return properties.type();
    }

    public PlanetId parentPlanetId() {
        return id.parentPlanetId();
    }

    public int moonIndex() {
        return id.moonIndex();
    }
}
