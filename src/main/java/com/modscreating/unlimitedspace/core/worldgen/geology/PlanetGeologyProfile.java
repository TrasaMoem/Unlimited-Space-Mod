package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — the aggregated geological subsystem of one planet, and the SINGLE OWNER of the
 * macro geography.
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; GEOLOGY (this)
 *            |- geography   (MACRO: the one lattice + warp + Voronoi ownership)
 *            |- provinces   (SECONDARY ~900: nearest-site ownership + continuous weights)
 *            |- climate     (planetary climate archetype + coherent field)
 *            |- relief      (planetary relief archetype + mountain coverage)
 *            |- palette     (theme-weighted planetary material palette)
 *            |- terrainSignature (feature strengths)
 *            '- fluidEcology / atmosphere
 * </pre>
 *
 * <h2>SINGLE OWNERSHIP (the duplicate-map fix)</h2>
 * There is exactly ONE {@link MacroGeography} instance per planet and it lives here. Every
 * downstream consumer — the chunk generator, the terrain shaper, the biome classifier, the
 * material selector, the debug overlay and the headless preview — reads {@link #geography()}.
 * A second macro map can no longer be constructed anywhere, so the terrain and the material
 * path physically cannot disagree about macro ownership.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record PlanetGeologyProfile(
        PlanetPhysicalProfile physical,
        MacroGeography geography,
        GeologicalProvinceMap provinces,
        PlanetGeologyPalette palette,
        com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature terrainSignature,
        com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile fluidEcology,
        com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmosphere,
        com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile climate,
        com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile relief,
        PlanetColorTheme colorTheme
) {
    public PlanetGeologyProfile {
        if (geography == null) throw new IllegalArgumentException("geography required");
    }

    /** Canonical factory: planet seed + properties &rarr; complete geology. */
    public static PlanetGeologyProfile create(long planetSeed,
                                              com.modscreating.unlimitedspace.core.planets.PlanetProperties properties) {
        PlanetPhysicalProfile physical = PlanetPhysicalProfileFactory.create(planetSeed, properties);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(planetSeed, physical);
        // --- climate / relief / macro geography / color theme ---
        com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile climate =
                com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile
                        .create(planetSeed, physical);
        com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile relief =
                com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile
                        .create(planetSeed, physical);
        PlanetaryEnvironment environment = PlanetaryEnvironment.of(
                physical, relief.mountainCoverage());
        // THE macro geography. Built exactly once, here, and shared by reference everywhere.
        MacroGeography geography = MacroGeography.of(planetSeed, environment);
        // PLACEHOLDER_FACTORY
        // The CANONICAL thermal band is the hard authority for the planet's color identity; the
        // climate archetype only promotes one of the band-allowed themes.
        // V3.2: the thermal band is the band filter, the climate archetype promotes one survivor,
        // and PlanetAdmissibility + humidity decide which themes are physically possible at all.
        // A theme that cannot exist on this planet is never returned - not by the variety branch.
        PlanetAdmissibility admissibility = com.modscreating.unlimitedspace.core.worldgen
                .admissibility.PlanetAdmissibility.of(physical, properties.surface());
        PlanetColorTheme theme = PlanetColorTheme.select(
                physical.temperatureBand(),
                climate.archetype(), planetSeed, admissibility, physical.humidity());
        com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature sig =
                com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector
                        .create(planetSeed, physical);
        com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile fluid =
                com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile.create(
                        planetSeed, physical, provinces.provinces());
        com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmo =
                com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile.create(
                        planetSeed, physical);
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(planetSeed, physical,
                provinces, theme);

        return new PlanetGeologyProfile(physical, geography, provinces, palette, sig, fluid,
                atmo, climate, relief, theme);
    }

    /** Convenience: derive the geology from an existing planet seed holder. */
    public static PlanetGeologyProfile from(long planetSeed,
                                            com.modscreating.unlimitedspace.core.planets.PlanetProperties properties) {
        return create(planetSeed, properties);
    }

    /** THE macro geography. Every consumer reads it from here. */
    public MacroGeography geography() {
        return geography;
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

    /** The derived global environment (hydrology, geology, physics intensities). */
    public PlanetaryEnvironment environment() {
        return PlanetaryEnvironment.of(physical,
                relief == null ? 0.35 : relief.mountainCoverage());
    }

    /** R21: a one-line planetary identity for the F3 screen and headless summaries. */
    public String identitySummary() {
        return "climate=" + (climate == null ? "?" : climate.label())
                + " relief=" + (relief == null ? "?" : relief.label())
                + " mountains=" + String.format(java.util.Locale.ROOT, "%.2f",
                        relief == null ? 0.0 : relief.mountainCoverage())
                + " theme=" + (colorTheme == null ? "?" : colorTheme.label());
    }
}
