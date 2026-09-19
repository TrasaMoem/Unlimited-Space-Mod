package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Deterministic atmosphere identity for a planet (R19 ecology stage).
 *
 * <p>Complements (never replaces) the existing visual sky/fog profile: this is an extra,
 * quantized informational layer (fog density, particle density, dustiness, colour temperature,
 * visibility, haze, heat-distortion intensity) that ambient particles and the client effects
 * can read. It is a pure function of {@code (planetSeed, physical profile)}.
 *
 * <p>Pure domain: no Minecraft types.
 *
 * @param fogDensity      normalized fog/thickness in [0,1]
 * @param particleDensity normalized ambient particle density in [0,1]
 * @param dustiness       normalized suspended dust in [0,1]
 * @param colorTemp       colour temperature in {COOL, NEUTRAL, WARM}
 * @param visibility      relative visibility in [0,1] (1 = clear, 0 = opaque)
 * @param haze            normalized haze/glow in [0,1]
 * @param heatDistortion  heat-shimmer intensity in [0,1] (0 = none)
 */
public record AtmosphereProfile(
        double fogDensity,
        double particleDensity,
        double dustiness,
        ColorTemp colorTemp,
        double visibility,
        double haze,
        double heatDistortion
) {

    /** Coarse colour-temperature of the atmosphere. */
    public enum ColorTemp { COOL, NEUTRAL, WARM }

    /**
     * Coarse atmospheric class for the F3 debug line and particle density scaling
     * (quantized from {@code fogDensity}; VACUUM worlds host no ambient particles).
     */
    public enum AtmoClass { VACUUM, THIN, MODERATE, DENSE }

    /** The quantized atmospheric class of this planet. */
    public AtmoClass coarseClass() {
        if (fogDensity <= 0.12) return AtmoClass.VACUUM;
        if (fogDensity <= 0.40) return AtmoClass.THIN;
        if (fogDensity <= 0.75) return AtmoClass.MODERATE;
        return AtmoClass.DENSE;
    }

    private static final String NS = "us.atmosphere";

    /** Canonical factory: planet seed + physical profile &rarr; atmosphere identity. */
    public static AtmosphereProfile create(long planetSeed, PlanetPhysicalProfile p) {
        if (p == null) {
            return new AtmosphereProfile(0.2, 0.1, 0.1, ColorTemp.NEUTRAL, 0.95, 0.1, 0.0);
        }
        long s = Seeds.derive(planetSeed, NS);

        // Fog grows with atmospheric density, haze with temperature + density.
        double density = clamp01(p.atmosphericDensity());
        double fog = clamp01(0.1 + 0.85 * density + 0.15 * p.temperature());
        double haze = clamp01(0.05 + 0.6 * density + 0.3 * p.radiation());
        double visibility = clamp01(1.0 - 0.9 * fog);

        // Dust: dry + eroded + thin-pressure worlds loft more particulates; dense atmospheres too.
        double dryness = 1.0 - p.humidity();
        double dustiness = clamp01(0.3 * dryness + 0.4 * p.erosion()
                + 0.3 * (1.0 - p.atmosphericDensity()));
        double particle = clamp01(0.02 + 0.5 * density + 0.4 * dustiness);

        // Colour temperature from thermal band.
        ColorTemp temp = p.isHotWorld() ? ColorTemp.WARM
                : p.isColdWorld() ? ColorTemp.COOL : ColorTemp.NEUTRAL;

        // Heat distortion: volcanic/geothermal drive shimmer even in thin air.
        double heat = clamp01(0.10 * p.volcanicActivity() + 0.12 * p.geothermalFlux()
                + 0.25 * p.volcanicActivity());

        return new AtmosphereProfile(fog, particle, dustiness, temp, visibility, haze, heat);
    }

    /**
     * Ambient-effect intensity (in [0,1]) — the common intensity function.
     *
     * <pre>intensity = planet factor &times; province factor &times; local factor</pre>
     *
     * @param planetFactor    per-planet base (volcanic, geodesic, ...)
     * @param provinceFactor  per-province multiplier (volcanic ash high, plains low)
     * @param localFactor     optional per-position factor (distance / density), default 1
     */
    public double effectIntensity(double planetFactor, double provinceFactor, double localFactor) {
        return clamp01(planetFactor * provinceFactor * (localFactor < 0 ? 1.0 : localFactor));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}