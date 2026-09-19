package com.modscreating.unlimitedspace.core.worldgen.profile;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic factory for {@link PlanetPhysicalProfile} (R16 planet-diversity foundation).
 *
 * <p>Pure function of {@code (planetSeed, PlanetProperties)}:
 *
 * <pre>
 * PlanetIdentity + GalaxySeed -&gt; PlanetSeed -&gt; PlanetPhysicalProfile
 * </pre>
 *
 * <p>The same planet id + world seed always yields an identical profile; different planets get
 * genuinely different profiles because every secondary characteristic is drawn from a dedicated
 * {@link Seeds} subsystem slot ("us.physics.*") rather than from the raw {@code new Random()}.
 *
 * <p>The primary characteristics are inherited from the existing generator
 * ({@link PlanetProperties}: temperature / humidity / atmospheric density / water coverage /
 * tectonic-geological activity / erosion / life level), so the profile stays <em>consistent</em>
 * with the planet the rest of the pipeline already produced. The geological fine structure
 * (metallicity / crystal abundance / impact frequency / age / axial bias / heat flux) is derived
 * deterministically and correlated with those constraints (e.g. volcanic worlds are more
 * metallic, ancient worlds are more eroded).
 *
 * <p>Pure domain: no Minecraft/NeoForge types.
 */
public final class PlanetPhysicalProfileFactory {

    private static final String NS = "us.physics";

    /** Kelvin span mapped onto the normalized temperature axis (cold&rarr;hot). */
    private static final double KELVIN_LOW = 70.0;
    private static final double KELVIN_HIGH = 1000.0;

    private PlanetPhysicalProfileFactory() {}

    /** Canonical factory: planet seed + properties &rarr; physical profile. */
    public static PlanetPhysicalProfile create(long planetSeed, PlanetProperties p) {
        double temperature = normalizeKelvin(p.temperature());
        TemperatureBand band = TemperatureBand.of(temperature);
        double humidity = clamp01(p.humidity());
        double atmosphericDensity = clamp01(p.atmosphericDensity());
        PressureClass pressure = PressureClass.of(p.atmosphere(), atmosphericDensity);

        double tectonic = clamp01(p.geologicalActivity());
        double erosion = clamp01(p.erosion());

        // --- deterministic geological fine structure, correlated with the primary constraints ---
        double volcanic = clamp01(0.72 * tectonic + 0.28 * draw(planetSeed, "volcanic"));
        double geothermal = clamp01(0.55 * volcanic + 0.30 * tectonic + 0.15 * draw(planetSeed, "geothermal"));

        // Hot volcanic worlds concentrate metal; cold ancient worlds concentrate crystals.
        double metallicity = clamp01(0.40 * volcanic + 0.35 * draw(planetSeed, "metallicity")
                + 0.25 * (1.0 - clamp01(p.humidity())) * temperature);
        double crystal = clamp01(0.45 * (1.0 - erosion) + 0.35 * (1.0 - temperature)
                + 0.20 * draw(planetSeed, "crystal"));

        double relativeAge = clamp01(draw(planetSeed, "age"));
        // Older worlds have been weathered longer, younger worlds are fresh and cratered.
        double impact = clamp01(0.35 + 0.45 * (1.0 - relativeAge) + 0.20 * draw(planetSeed, "impact"));
        double effectiveErosion = clamp01(0.7 * erosion + 0.3 * relativeAge);

        double mineralAbundance = clamp01(0.35 * p.resources().mineralRichness()
                + 0.30 * metallicity + 0.20 * tectonic
                + 0.15 * (p.resources().rareMaterials() ? 1.0 : draw(planetSeed, "mineral")));

        boolean gaseous = p.surface() == PlanetSurface.GASEOUS;
        double organic = gaseous
                ? 0.0
                : clamp01(p.lifeLevel() * 0.6 + p.vegetationDensity() * 0.4);

        double waterAbundance = gaseous ? 0.0 : clamp01(p.waterCoverage() * 0.75 + p.humidity() * 0.25);
        double oceanCoverage = gaseous ? 0.0 : clamp01(p.waterCoverage());
        double continentality = gaseous ? 0.5 : clamp01(1.0 - p.waterCoverage());

        // Radiation: thin, hot, old atmospheres let more through.
        double radiation = clamp01(0.40 * (1.0 - atmosphericDensity) + 0.35 * temperature
                + 0.25 * draw(planetSeed, "radiation"));

        double axialBias = clamp01(0.30 + 0.40 * draw(planetSeed, "axial"));

        return new PlanetPhysicalProfile(
                temperature, band, humidity, atmosphericDensity, pressure,
                waterAbundance, oceanCoverage, continentality,
                tectonic, volcanic, geothermal, effectiveErosion, impact,
                mineralAbundance, metallicity, crystal, organic, radiation,
                geothermal, axialBias, relativeAge,
                GravityClass.of(p.gravity()), p.surface());
    }

    /** Convenience overload: derive from an already-built planet seed holder. */
    public static PlanetPhysicalProfile of(long planetSeed, PlanetProperties p) {
        return create(planetSeed, p);
    }

    /** Normalized temperature in {@code [0,1]} from Kelvin (clamped at both ends). */
    static double normalizeKelvin(double kelvin) {
        return clamp01((kelvin - KELVIN_LOW) / (KELVIN_HIGH - KELVIN_LOW));
    }

    private static double draw(long planetSeed, String slot) {
        long s = Seeds.derive(planetSeed, NS + "." + slot);
        return Seeds.fraction(s, 91001L);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
