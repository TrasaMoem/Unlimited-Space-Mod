package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

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
        // --- R21 planetary geography: climate / relief / biome regions / color theme ---
        // --- PHASE 5: the COLOR THEME is resolved FIRST and drives the material palette —
        // one planet = one color language (thermal state → climate → theme → materials).
        PlanetClimateProfile climate = PlanetClimateProfile.create(planetSeed, physical);
        PlanetReliefProfile relief = PlanetReliefProfile.create(planetSeed, physical);
        PlanetaryEnvironment environment = PlanetaryEnvironment.of(
                physical, relief.mountainCoverage());
        BiomeRegionMap regions = BiomeRegionMap.create(
                Seeds.derive(planetSeed, "us.biome.regions"), environment);
        // R23 (T-3): the CANONICAL thermal band is the hard authority for the planet's color
        // identity; the climate archetype only promotes one of the band-allowed themes.
        PlanetColorTheme theme = PlanetColorTheme.select(
                com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand
                        .of(physical.temperature()),
                climate.archetype(), planetSeed);
        com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature sig =
                com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector.create(planetSeed, physical);
        com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile fluid =
                com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile.create(planetSeed, physical, provinces.provinces());
        com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmo =
                com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile.create(planetSeed, physical);
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(planetSeed, physical, provinces, theme);

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

    /**
     * PHASE 4: the local SUB-BIOME inside the macro region at a column.
     *
     * <p>Depends on the macro region, the coherent climate field, the column elevation and
     * the planet's hydrology/geology — never on an independent random field. Deterministic.
     *
     * @param elevation01 normalized column elevation in [0,1] (from the terrain compositor)
     */
    public SubBiome subBiomeAt(int x, int z, double elevation01) {
        return subBiomeAt(x, z, 0, elevation01);
    }

    /**
     * R23 (E-1/I): province-aware sub-biome. The medium {@link ProvinceField} layer (~900
     * blocks) now participates in the local ecological texture alongside the macro region,
     * the climate field and the elevation, so vegetation/retint consumers see the SAME
     * column expression the material path sees. The {@code columnH} (world height of the
     * column surface) is only used to derive the medium province at a stable scale; the
     * normalized {@code elevation01} stays the ecological input.
     */
    public SubBiome subBiomeAt(int x, int z, int columnH, double elevation01) {
        if (biomeRegions == null || climate == null) return SubBiome.MEADOW;
        double t = climate.temperatureAt(x, z);
        double h = climate.humidityAt(x, z);
        // ACT 3 (P0.3): the macro context is climate-aware — inside a transition band a grossly
        // incompatible primary region yields to a compatible neighbour (core keeps its identity).
        BiomeRegionMap.Context ctx = biomeRegions.contextAt(x, z, t);
        // ACT 3 (P3.1): the ecological wetness is PHASE-AWARE — liquid water only where the
        // canonical water phase allows it (frozen/dry worlds keep a frozen, not a marsh, state).
        com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase =
                environment() == null ? null : environment().waterPhase();
        double wet = phaseAwareWetness(physical, h, phase);
        com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region =
                ctx == null ? null : ctx.region();
        GeologicalProvince province = null;
        if (provinces != null && physical != null && region != null) {
            long mediumSeed = ProvinceField.mediumSeedOf(
                    physical.hashCode(), provinces.provinceSeed());
            province = ProvinceField.provinceAt(mediumSeed, provinces.weights(), physical,
                    region, x, z, elevation01);
        }
        if (province == null && provinces != null) {
            province = provinces.provinceAt(x, z, elevation01);
        }
        return SubBiome.select(provinceSeed(), x, z, ctx, t, h, elevation01, wet,
                environment(), province, phase);
    }

    /**
     * ACT 3 (P3.1): phase-aware ecological wetness. LIQUID keeps the historical mix
     * ({@code 0.4*waterAbundance + 0.6*humidity}); SOLID delivers a FROZEN wetness (present but
     * ecologically frozen), VAPOR/NONE deliver a dry value. The phase factor comes from the
     * canonical {@link PlanetaryEnvironment#phaseFactor} — no new phase behaviour is invented.
     */
    private static double phaseAwareWetness(
            com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile physical,
            double humidity, com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase) {
        double liquidMix = physical == null ? humidity
                : Math.min(1.0, 0.40 * physical.waterAbundance() + 0.60 * humidity);
        if (phase == null) return liquidMix;
        double factor = com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment
                .phaseFactor(phase);
        return Math.max(0.0, Math.min(1.0, liquidMix * factor));
    }

    /** R22: the derived GLOBAL environment (hydrology, geology, physics intensities). */
    public PlanetaryEnvironment environment() {
        return PlanetaryEnvironment.of(physical,
                relief == null ? 0.35 : relief.mountainCoverage());
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
