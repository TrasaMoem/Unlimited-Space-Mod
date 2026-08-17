package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;

/**
 * Deterministic climate/environment profile for one planet (R8).
 *
 * <p>Wraps the raw {@link PlanetProperties} climate fields into a stable,
 * Minecraft-free view that the rest of the generation pipeline and later
 * gameplay phases (atmosphere/survival/etc.) can consume without depending on
 * the planet-properties record shape.
 *
 * <p>Pure domain object.
 */
public record PlanetEnvironmentProfile(
        double temperature,
        double humidity,
        double waterCoverage,
        double gravity,
        com.modscreating.unlimitedspace.core.planets.AtmosphereType atmosphere,
        double atmosphereDensity,
        double geologicalActivity,
        double lifeLevel,
        PlanetSurface surface,
        boolean isGasGiant
) {

    public static PlanetEnvironmentProfile from(PlanetProperties p) {
        return new PlanetEnvironmentProfile(
                p.temperature(),
                p.humidity(),
                p.waterCoverage(),
                p.gravity(),
                p.atmosphere(),
                p.atmosphericDensity(),
                p.geologicalActivity(),
                p.lifeLevel(),
                p.surface(),
                p.surface() == PlanetSurface.GASEOUS);
    }
}
