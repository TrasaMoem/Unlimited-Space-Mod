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
 * @param archetype      the planet's core climatic identity
 * @param climateSeed    subsystem seed of the spatial field
 */
public record PlanetClimateProfile(ClimateArchetype archetype, long climateSeed) {

    /** Canonical factory: planet seed + physical profile &rarr; climate subsystem. */
    public static PlanetClimateProfile create(long planetSeed, PlanetPhysicalProfile physical) {
        ClimateArchetype arch = ClimateArchetypeSelector.create(planetSeed, physical);
        long seed = Seeds.derive(planetSeed, "us.climate.field.seed");
        return new PlanetClimateProfile(arch, seed);
    }

    /** Normalized local temperature in [0,1] (spatially coherent, thousands of blocks). */
    public double temperatureAt(int x, int z) {
        return PlanetClimateField.temperatureAt(climateSeed, archetype, x, z);
    }

    /** Normalized local humidity in [0,1]. */
    public double humidityAt(int x, int z) {
        return PlanetClimateField.humidityAt(climateSeed, archetype, x, z);
    }

    /** Local aridity in [0,1] (1 = bone dry). */
    public double aridityAt(int x, int z) {
        return PlanetClimateField.aridityAt(climateSeed, archetype, x, z);
    }

    /** Debug label, e.g. {@code FROZEN(DUAL_POLE)}. */
    public String label() {
        return archetype == null ? "?" : archetype.label();
    }
}
