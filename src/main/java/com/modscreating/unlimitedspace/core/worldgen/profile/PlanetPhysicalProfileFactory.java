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

    /** Kelvin span mapped onto the normalized temperature axis (log scale, DESIGN TARGETS). */
    private static final double KELVIN_LOW = com.modscreating.unlimitedspace.core.physics.StellarThermalModel.T_MIN;
    private static final double KELVIN_HIGH = com.modscreating.unlimitedspace.core.physics.StellarThermalModel.T_MAX;

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

        // V3.2 PHASE 3: crystal formation gets an EXPLICIT cold dependence.
        //
        // What was wrong: the old term was dominated by (1 - erosion), so a young, un-eroded HOT
        // desert scored ~0.5 and cleared the 0.30 "requiresCrystals" gate, which is how prismstone
        // and amethyst ended up on arid worlds. A crystal body is a slow, cold, thermally stable
        // crust product: it needs low temperature, low recycling (volcanism) and old, intact rock.
        //
        // The deterministic planet-seed draw is preserved as an independent term, so the value
        // still varies per planet while the PHYSICAL trend can no longer be overridden by it.
        double crystalKelvin = com.modscreating.unlimitedspace.core.physics.StellarThermalModel
                .denormalizeKelvin(temperature);
        // 0 at 300 K and above, rising to 1 at 180 K and below.
        double crystalColdFactor = clamp01((300.0 - crystalKelvin) / 120.0);
        // Crustal stability: tectonic recycling and volcanism both destroy crystal bodies.
        double crystalStability = clamp01(0.5 * (1.0 - tectonic) + 0.5 * (1.0 - volcanic));
        double crystal = clamp01(
                0.35 * crystalColdFactor
                        + 0.30 * (1.0 - erosion) * (0.30 + 0.70 * crystalColdFactor)
                        + 0.20 * crystalStability * (0.25 + 0.75 * crystalColdFactor)
                        + 0.15 * draw(planetSeed, "crystal"));

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

        double waterAvailability = gaseous ? 0.0 : waterAbundance(p.waterCoverage(), p.humidity());
        double oceanCoverage = gaseous ? 0.0 : clamp01(p.waterCoverage());
        double continentality = gaseous ? 0.5 : clamp01(1.0 - p.waterCoverage());

        // Radiation: thin, hot, old atmospheres let more through.
        double radiation = clamp01(0.40 * (1.0 - atmosphericDensity) + 0.35 * temperature
                + 0.25 * draw(planetSeed, "radiation"));

        double axialBias = clamp01(0.30 + 0.40 * draw(planetSeed, "axial"));

        return new PlanetPhysicalProfile(
                temperature, band, humidity, atmosphericDensity, pressure,
                waterAvailability, oceanCoverage, continentality,
                tectonic, volcanic, geothermal, effectiveErosion, impact,
                mineralAbundance, metallicity, crystal, organic, radiation,
                geothermal, axialBias, relativeAge,
                GravityClass.of(p.gravity()), p.surface());
    }

    /** Convenience overload: derive from an already-built planet seed holder. */
    public static PlanetPhysicalProfile of(long planetSeed, PlanetProperties p) {
        return create(planetSeed, p);
    }

    /**
     * PHASE 9: the canonical WATER AVAILABILITY blend — surface coverage weighted with
     * atmospheric humidity. Exposed so the navigation UI, the F3 overlay and the headless reports
     * can ask the water-phase model about exactly the water amount the worldgen will act on,
     * instead of guessing from {@code waterCoverage} alone.
     */
    public static double waterAbundance(double waterCoverage, double humidity) {
        return clamp01(waterCoverage * 0.75 + humidity * 0.25);
    }

    /** PHASE 1: monotonic LOG normalization of Kelvin in {@code [0,1]} (clamped both ends). */
    static double normalizeKelvin(double kelvin) {
        return com.modscreating.unlimitedspace.core.physics.StellarThermalModel.normalizeKelvin(kelvin);
    }

    /** PHASE 1: inverse of {@link #normalizeKelvin} (normalized axis → Kelvin). */
    public static double denormalizeKelvin(double normalized) {
        return com.modscreating.unlimitedspace.core.physics.StellarThermalModel.denormalizeKelvin(normalized);
    }

    private static double draw(long planetSeed, String slot) {
        long s = Seeds.derive(planetSeed, NS + "." + slot);
        return Seeds.fraction(s, 91001L);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
