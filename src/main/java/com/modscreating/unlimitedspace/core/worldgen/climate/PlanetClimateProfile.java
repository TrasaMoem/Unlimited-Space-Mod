package com.modscreating.unlimitedspace.core.worldgen.climate;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * The planet's climate subsystem (R21): archetype + spatially coherent field seeds.
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; CLIMATE (this) -&gt; BIOME REGIONS / SURFACE / THEME / FLUIDS
 * </pre>
 *
 * <p>Answers {@code temperatureAt / humidityAt / aridityAt} for any world column through
 * {@link PlanetClimateField}. The archetype gives the planetary identity; the field gives the
 * geography. Pure domain, deterministic from {@code (planetSeed, physical profile)}.
 *
 * <p><b>R23 (T-2):</b> the local field varies AROUND the planet's canonical mean temperature
 * (carried here as {@code meanTemperature01}) with a band-dependent half-width, instead of
 * being an independent absolute field. Kelvin stays the physical truth; this value is the
 * normalized (log-axis) derived form of exactly that temperature.
 *
 * @param archetype         the planet's core climatic identity
 * @param climateSeed       subsystem seed of the spatial field
 * @param meanTemperature01 canonical planetary mean temperature in [0,1] (normalized axis)
 */
public record PlanetClimateProfile(ClimateArchetype archetype, long climateSeed, double axialBias,
                                   double meanTemperature01) {

    /** Canonical factory: planet seed + physical profile &rarr; climate subsystem. */
    public static PlanetClimateProfile create(long planetSeed, PlanetPhysicalProfile physical) {
        ClimateArchetype arch = ClimateArchetypeSelector.create(planetSeed, physical);
        long seed = Seeds.derive(planetSeed, "us.climate.field.seed");
        // R22: the planet's axial climate bias drives the deterministic climate geometry.
        double bias = physical == null ? 0.5 : physical.axialClimateBias();
        // R23: the local field is centred on the planet's own temperature, never on the archetype.
        double mean = physical == null
                ? (arch == null ? 0.5 : arch.baseTemperature())
                : physical.temperature();
        return new PlanetClimateProfile(arch, seed, bias, mean);
    }

    /** R23: half-width of the local temperature variation (from the planet's thermal band). */
    public double temperatureWidth() {
        return PlanetClimateField.temperatureWidth(
                com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand
                        .of(meanTemperature01));
    }

    /** Normalized local temperature in [0,1] (spatially coherent, thousands of blocks). */
    public double temperatureAt(int x, int z) {
        return PlanetClimateField.temperatureAt(climateSeed, archetype, meanTemperature01,
                temperatureWidth(), axialBias, x, z);
    }

    /** Normalized local humidity in [0,1]. */
    public double humidityAt(int x, int z) {
        return PlanetClimateField.humidityAt(climateSeed, archetype, x, z);
    }

    /** Local aridity in [0,1] (1 = bone dry). */
    public double aridityAt(int x, int z) {
        return PlanetClimateField.aridityAt(climateSeed, archetype, x, z);
    }

    /** R22: temperature with elevation cooling (high columns are colder). */
    public double temperatureAt(int x, int z, double elevation01) {
        return PlanetClimateField.withElevationCooling(temperatureAt(x, z), elevation01);
    }

    /** R22: humidity with the water influence (columns near liquid are more humid). */
    public double humidityAt(int x, int z, double surfaceWetness01) {
        return PlanetClimateField.withWaterInfluence(humidityAt(x, z), surfaceWetness01);
    }

    /** Debug label, e.g. {@code FROZEN(DUAL_POLE)}. */
    public String label() {
        return archetype == null ? "?" : archetype.label();
    }
}
