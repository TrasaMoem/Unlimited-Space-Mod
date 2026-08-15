package com.modscreating.unlimitedspace.core.planets;

/**
 * High-level surface category of a planet. Deliberately not block-specific; it is a
 * semantic flag for future climate/surface-rule profiles.
 */
public enum PlanetSurface {
    SOLID_ROCKY,
    SOLID_ICE,
    SOLID_DESERT,
    SOLID_VOLCANIC,
    OCEANIC,
    GASEOUS;
}
