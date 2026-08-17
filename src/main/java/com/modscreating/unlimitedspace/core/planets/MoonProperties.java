package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.MoonSeed;

/**
 * Fully generated, immutable properties of a moon.
 * Pure domain data; all values depend deterministically on the moon seed
 * (and its derived subsystem seeds). Never a copy of the parent planet's properties.
 *
 * @param id                 stable moon id (parent planet + index)
 * @param seed               the provoking moon seed
 * @param type               moon archetype
 * @param surface            semantic surface category
 * @param radiusProfile      radius relative to a reference world (typically &lt; 1)
 * @param gravity            surface gravity in Earth g (typically &lt; parent planet)
 * @param temperature        temperature in Kelvin
 * @param atmosphericDensity relative density in [0,1]
 * @param waterCoverage      surface water fraction in [0,1]
 * @param terrainRoughness   relief amplitude in [0,1]
 * @param erosion            erosion factor in [0,1]
 * @param geologicalActivity tectonic/volcanic activity in [0,1]
 * @param atmosphere         atmosphere archetype
 * @param ringState          whether this moon has a ring
 * @param orbit              deterministic orbital metadata
 */
public record MoonProperties(
        MoonId id,
        MoonSeed seed,
        MoonType type,
        PlanetSurface surface,
        double radiusProfile,
        double gravity,
        double temperature,
        double atmosphericDensity,
        double waterCoverage,
        double terrainRoughness,
        double erosion,
        double geologicalActivity,
        AtmosphereType atmosphere,
        boolean ringState,
        MoonOrbitMetadata orbit) {

    /** Convenience: does this moon plausibly support surface life? */
    public boolean isHabitable() {
        return surface != PlanetSurface.GASEOUS
                && temperature >= 240.0 && temperature <= 350.0
                && waterCoverage > 0.1 && atmosphericDensity > 0.15;
    }

    public PlanetId parentPlanetId() {
        return id.parentPlanetId();
    }
}
