package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;

/**
 * Aggregated geological subsystem of one planet (R16 foundation, R21 planetary geography).
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; GEOLOGY (this)
 *            ├── provinces        (regional landform / geology)
 *            ├── climate          (planetary climate archetype + coherent field)
 *            ├── relief           (planetary relief archetype + mountain coverage)
 *            ├── biomeRegions     (LARGE macro regions + transition zones)
 *            ├── palette          (theme-weighted planetary material palette)
 *            ├── terrainSignature (feature strengths)
 *            └── fluidEcology / atmosphere
 * </pre>
 *
 * <p>Composed once from the planet seed + properties and exposed on
 * {@code PlanetWorldgenProfile}, so the chunk generator reads a single consistent object
 * instead of re-deriving materials per column.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record PlanetGeologyProfile(
        PlanetPhysicalProfile physical,
        GeologicalProvinceMap provinces,
        PlanetGeologyPalette palette,
        com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature terrainSignature,
        com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile fluidEcology,
        com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmosphere,
        PlanetClimateProfile climate,
        PlanetReliefProfile relief,
        BiomeRegionMap biomeRegions,
        PlanetColorTheme colorTheme
) {
    public PlanetGeologyProfile {}

    /** Canonical factory: planet seed + properties &rarr; complete geology. */
    public static PlanetGeologyProfile create(long planetSeed,
                                              com.modscreating.unlimitedspace.core.planets.PlanetProperties properties) {
        PlanetPhysicalProfile physical = PlanetPhysicalProfileFactory.create(planetSeed, properties);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(planetSeed, physical);
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(planetSeed, physical, provinces);
        com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature sig =
                com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector.create(planetSeed, physical);
        com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile fluid =
                com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile.create(planetSeed, physical, provinces.provinces());
        com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmo =
                com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile.create(planetSeed, physical);

        // --- R21 planetary geography: climate / relief / biome regions / color theme ---
        PlanetClimateProfile climate = PlanetClimateProfile.create(planetSeed, physical);
        PlanetReliefProfile relief = PlanetReliefProfile.create(planetSeed, physical);
        BiomeRegionMap regions = BiomeRegionMap.create(
                Seeds.derive(planetSeed, "us.biome.regions"),
                physical.temperature(), physical.humidity(), physical.crystalAbundance(),
                physical.volcanicActivity(), physical.impactFrequency(), physical.tectonicActivity());
        PlanetColorTheme theme = PlanetColorTheme.select(climate.archetype(), planetSeed);

        return new PlanetGeologyProfile(physical, provinces, palette, sig, fluid, atmo,
                climate, relief, regions, theme);
    }

    /** Convenience: derive the geology from an existing planet seed holder. */
    public static PlanetGeologyProfile from(long planetSeed,
                                            com.modscreating.unlimitedspace.core.planets.PlanetProperties properties) {
        return create(planetSeed, properties);
    }

    /** Dominant province (diagnostics/debug screen). */
    public GeologicalProvince dominantProvince() {
        return provinces.dominant();
    }

    /** Debug summary used by the chunk-generator F3 overlay. */
    public String summary() {
        return physical.summary() + " dominant=" + dominantProvince()
                + " materials=" + palette.distinctCount();
    }

    /** Province seed passthrough (used by deterministic per-column sampling). */
    public long provinceSeed() {
        return provinces.provinceSeed();
    }

    /** Stable subsystem seed for later feature stages. */
    public long featureSeed() {
        return Seeds.derive(physical == null ? 0L : provinces.provinceSeed(), "us.features");
    }

    /** R21: the planet's biome-region map (large macro regions + transitions). */
    public BiomeRegionMap regions() {
        return biomeRegions;
    }

    /** R21: one-line planetary identity for the F3 screen and headless summaries. */
    public String identitySummary() {
        return "climate=" + (climate == null ? "?" : climate.label())
                + " relief=" + (relief == null ? "?" : relief.label())
                + " mountains=" + String.format(java.util.Locale.ROOT, "%.2f",
                        relief == null ? 0.0 : relief.mountainCoverage())
                + " theme=" + (colorTheme == null ? "?" : colorTheme.label());
    }
}
