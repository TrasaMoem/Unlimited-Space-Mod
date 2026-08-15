package com.modscreating.unlimitedspace.core.seed;

import com.modscreating.unlimitedspace.core.planets.PlanetId;

/**
 * Stable seed for a single planet. It depends only on the star system seed and the
 * planet's fixed orbit index ({@link Seeds#planet(long, int)}), never on the
 * display name or on the number of planets already generated.
 *
 * @param value the 64-bit seed
 */
public record PlanetSeed(long value) {

    /** Convenience: derive this planet's seed from its owning system + orbit index. */
    public static PlanetSeed forSlot(long starSystemSeed, int orbitIndex) {
        return new PlanetSeed(Seeds.planet(starSystemSeed, orbitIndex));
    }

    /** Convenience: derive this planet's seed from its owning system + planet id. */
    public static PlanetSeed forSlot(long starSystemSeed, PlanetId planetId) {
        return forSlot(starSystemSeed, planetId.orbitIndex());
    }

    public SubsystemSeed subsystem(String name) {
        return new SubsystemSeed(name, Seeds.subsystem(value, name));
    }

    public long terrainSeed()    { return Seeds.subsystem(value, "terrain"); }
    public long biomeSeed()      { return Seeds.subsystem(value, "biome"); }
    public long oreSeed()        { return Seeds.subsystem(value, "ore"); }
    public long structureSeed()  { return Seeds.subsystem(value, "structures"); }
    public long vegetationSeed() { return Seeds.subsystem(value, "vegetation"); }
    public long materialSeed()   { return Seeds.subsystem(value, "materials"); }
}
