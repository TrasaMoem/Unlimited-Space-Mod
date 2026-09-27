package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.MoonThermalModel;
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
        MoonOrbitMetadata orbit,
        MoonThermalModel.MoonThermal thermal) {

    /** PHASE 2 back-compat constructor: moons built without a parent context get no thermal state. */
    public MoonProperties(
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
        this(id, seed, type, surface, radiusProfile, gravity, temperature, atmosphericDensity,
                waterCoverage, terrainRoughness, erosion, geologicalActivity, atmosphere,
                ringState, orbit, null);
    }

    /** PHASE 2: derived thermal state of the moon (null only for legacy constructions). */
    public MoonThermalModel.MoonThermal thermalOrNull() {
        return thermal;
    }

    /** PHASE 2: tidal heating in [0,1] (0 when the moon was built without a thermal context). */
    public double tidalHeating() {
        return thermal == null ? 0.0 : thermal.tidalHeating();
    }

    /** PHASE 2: moon thermal class (derived from the real temperature when unavailable). */
    public MoonThermalModel.MoonThermalClass thermalClass() {
        if (thermal != null) return thermal.thermalClass();
        return temperature < 240.0
                ? MoonThermalModel.MoonThermalClass.COLD_FROZEN
                : temperature < 320.0
                        ? MoonThermalModel.MoonThermalClass.TEMPERATE
                        : temperature < 420.0
                                ? MoonThermalModel.MoonThermalClass.WARM
                                : MoonThermalModel.MoonThermalClass.HOT;
    }

    /**
     * ACT 2 — the canonical PHYSICAL habitability verdict of this moon (concept B). A pure
     * delegate to the SAME {@code HabitabilityValidator} the planets use (no duplicated
     * thresholds); the moon supplies its own temperature / atmosphere / pressure / gravity /
     * water coverage. NOTE: a moon is only ACTUALLY habitable (concept C) when its parent
     * planet is actually habitable AND this moon won its own 30% lottery AND the physics pass —
     * see {@code MoonHabitability}.
     */
    public boolean isHabitable() {
        return com.modscreating.unlimitedspace.core.habitability.HabitabilityValidator
                .isPhysicallyHabitable(
                        com.modscreating.unlimitedspace.core.habitability.HabitabilityProfile
                                .ofMoon(this));
    }

    public PlanetId parentPlanetId() {
        return id.parentPlanetId();
    }
}
