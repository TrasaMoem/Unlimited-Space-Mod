package com.modscreating.unlimitedspace.core.planets;

/**
 * Stable identity of one moon inside a planet.
 * Pure domain value; no Minecraft coupling.
 *
 * <p>Example: {@code system_0000_planet_03_moon_02}</p>
 *
 * @param parentPlanetId owning planet identity
 * @param moonIndex      per-planet moon index (>= 0)
 */
public record MoonId(PlanetId parentPlanetId, int moonIndex) {

    public MoonId {
        if (moonIndex < 0) throw new IllegalArgumentException("moonIndex must be >= 0");
    }

    public static MoonId of(PlanetId parentPlanetId, int moonIndex) {
        return new MoonId(parentPlanetId, moonIndex);
    }

    /** Stable code string, e.g. {@code system_0000_planet_03_moon_02}. */
    public String code() {
        return parentPlanetId.code() + "_moon_" + String.format("%02d", moonIndex);
    }
}