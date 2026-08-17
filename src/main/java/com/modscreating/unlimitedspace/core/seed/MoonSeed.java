package com.modscreating.unlimitedspace.core.seed;

/**
 * Stable seed for a single moon. It depends only on the owning planet seed and the
 * moon's fixed index ({@link Seeds#moon(long, int)}), never on display names or on
 * the number of moons already generated.
 *
 * @param value the 64-bit seed
 */
public record MoonSeed(long value) {

    /** Derive this moon's seed from its owning planet seed + moon index. */
    public static MoonSeed forSlot(long planetSeed, int moonIndex) {
        return new MoonSeed(Seeds.moon(planetSeed, moonIndex));
    }

    /** Derive this moon's seed from a planet identity (via its seed value). */
    public static MoonSeed forPlanet(PlanetSeed planetSeed, int moonIndex) {
        return forSlot(planetSeed.value(), moonIndex);
    }

    public SubsystemSeed subsystem(String name) {
        return new SubsystemSeed(name, Seeds.subsystem(value, name));
    }

    public long terrainSeed()    { return Seeds.subsystem(value, "terrain"); }
    public long biomeSeed()      { return Seeds.subsystem(value, "biome"); }
    public long materialSeed()   { return Seeds.subsystem(value, "materials"); }
    public long waterSeed()      { return Seeds.subsystem(value, "water"); }
    public long environmentSeed(){ return Seeds.subsystem(value, "environment"); }
    public long visualSeed()     { return Seeds.subsystem(value, "visual"); }
}
