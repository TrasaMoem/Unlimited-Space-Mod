package com.modscreating.unlimitedspace.core.worldgen.profile;

import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;

/**
 * PLANETARY ENVIRONMENT (R22) — the derived GLOBAL environment state of one planet.
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; THIS (derived state) -&gt; BIOME FAMILIES / THEME / HYDROLOGY
 * </pre>
 *
 * <p>This is NOT a second profile: it is a pure derived view over the existing
 * {@link PlanetPhysicalProfile} (plus the relief/climate identity) that answers the questions
 * the biome-compatibility layer needs in CONTINUOUS form:
 *
 * <ul>
 *   <li>{@code temperatureTendency / humidityTendency} — global baselines in [0,1];</li>
 *   <li>{@code aridity} — global dryness (heat + low moisture);</li>
 *   <li>{@code surfaceWetness / oceanPotential / basinWaterPotential} — the hydrology regime
 *       (waterAbundance × oceanCoverage, moderated by continentality);</li>
 *   <li>{@code reliefRegime} — 0 = endless plains .. 1 = dominant mountain world;</li>
 *   <li>{@code volcanicIntensity / geothermalIntensity / crystalIntensity / metallicIntensity /
 *       erosionIntensity / impactIntensity / radiationIntensity / organicIntensity} —
 *       continuous geological driver intensities;</li>
 *   <li>{@code pressure01 / age01 / verticalScale} — atmosphere thickness, geological age and
 *       the gravity-driven vertical terrain scale;</li>
 *   <li>{@code waterPhase} — R23 (W-1) canonical surface water phase ({@code SOLID / LIQUID /
 *       MIXED / VAPOR / NONE}); it gates the hydrology so a frozen world cannot behave like a
 *       liquid-ocean world.</li>
 * </ul>
 *
 * <p>Every {@code PlanetPhysicalProfile} parameter is consumed by at least one accessor here,
 * so no planet statistic can exist "only for the F3 screen".
 *
 * <p>Pure domain: no Minecraft types. Deterministic pure function of its inputs.
 */
public record PlanetaryEnvironment(
        double temperatureTendency,
        double humidityTendency,
        double aridity,
        double surfaceWetness,
        double oceanPotential,
        double basinWaterPotential,
        double reliefRegime,
        double mountainCoverage,
        double volcanicIntensity,
        double geothermalIntensity,
        double crystalIntensity,
        double metallicIntensity,
        double mineralIntensity,
        double erosionIntensity,
        double impactIntensity,
        double radiationIntensity,
        double organicIntensity,
        double pressure01,
        double age01,
        double verticalScale,
        WaterPhaseModel.Phase waterPhase,
        PlanetPhysicalProfile source
) {

    /** R23 (W-1): how much of the water budget still exists as mobile surface liquid. */
    public static double phaseFactor(WaterPhaseModel.Phase phase) {
        if (phase == null) return 1.0;
        return switch (phase) {
            case SOLID -> 0.45;
            case LIQUID -> 1.0;
            case MIXED -> 0.6;
            case VAPOR -> 0.05;
            case NONE -> 0.0;
        };
    }

    /** R23 (W-1): humidity contribution of a phase (ice sublimes little, vapour is dry air). */
    private static double phaseMoisture(WaterPhaseModel.Phase phase) {
        if (phase == null) return 1.0;
        return switch (phase) {
            case SOLID -> 0.5;
            case LIQUID -> 1.0;
            case MIXED -> 0.7;
            case VAPOR -> 0.1;
            case NONE -> 0.0;
        };
    }

    /** R23 (W-1): legacy entry point — the canonical phase is derived from the profile. */
    public static PlanetaryEnvironment of(PlanetPhysicalProfile p, double mountainCoverage) {
        return of(p, WaterPhaseModel.ofProfile(p), mountainCoverage);
    }

    /**
     * R23 (W-1) CANONICAL factory: physical profile + canonical surface water phase.
     *
     * <p>The phase gates the hydrology: a SOLID (frozen) world keeps 45% of its water budget as
     * mobile surface water, a VAPOR world 5%, a dry world none — so a cryogenic planet can no
     * longer draw ocean/lowland biome families from a legacy water coverage alone.
     */
    public static PlanetaryEnvironment of(PlanetPhysicalProfile p,
                                          WaterPhaseModel.Phase phase,
                                          double mountainCoverage) {
        if (p == null) return neutral();
        double temp = clamp01(p.temperature());
        double hum = clamp01(p.humidity());
        double aridity = clamp01(0.65 * (1.0 - hum) + 0.35 * temp);
        // Hydrology: abundance pools into oceans, continentality locks water inland.
        double phaseMul = phaseFactor(phase);
        double oceanPotential = clamp01(p.waterAbundance() * phaseMul
                * (0.45 + 0.75 * p.oceanCoverage()));
        double continentalLock = 0.30 * p.continentality();
        double surfaceWetness = clamp01(oceanPotential * (1.0 - continentalLock)
                + 0.15 * hum * phaseMoisture(phase));
        double basinWaterPotential = clamp01(oceanPotential * (0.55 + 0.45 * (1.0 - p.continentality())));
        return new PlanetaryEnvironment(
                temp, hum, aridity,
                surfaceWetness, oceanPotential, basinWaterPotential,
                clamp01(mountainCoverage), clamp01(mountainCoverage),
                clamp01(0.75 * p.volcanicActivity() + 0.25 * p.geothermalFlux()),
                clamp01(p.geothermalFlux() * 0.7 + p.geothermalActivity() * 0.3),
                clamp01(p.crystalAbundance()),
                clamp01(p.metallicity()),
                clamp01(p.mineralAbundance()),
                clamp01(p.erosion()),
                clamp01(p.impactFrequency()),
                clamp01(p.radiation()),
                clamp01(p.organicPotential()),
                clamp01(p.atmosphericDensity()),
                clamp01(p.relativeAge()),
                verticalScale(p.gravityClass()),
                phase,
                p);
    }

    /** Neutral fallback (no profile available — e.g. legacy call sites). */
    public static PlanetaryEnvironment neutral() {
        return new PlanetaryEnvironment(0.5, 0.5, 0.5, 0.4, 0.35, 0.35,
                0.35, 0.35, 0.1, 0.1, 0.1, 0.1, 0.3, 0.3, 0.1, 0.1, 0.3,
                0.6, 0.5, 1.0, WaterPhaseModel.Phase.LIQUID, null);
    }

    /** Environment built ONLY from the legacy 7 scalars (back-compat factories/tests). */
    public static PlanetaryEnvironment ofScalars(double temperature, double humidity,
                                                 double crystal, double volcanic,
                                                 double impact, double tectonic) {
        double relief = clamp01(0.25 + 0.75 * tectonic);
        return new PlanetaryEnvironment(
                clamp01(temperature), clamp01(humidity),
                clamp01(0.65 * (1.0 - humidity) + 0.35 * temperature),
                clamp01(0.35 + 0.35 * humidity),
                clamp01(0.25 + 0.45 * humidity),
                clamp01(0.25 + 0.40 * humidity),
                relief, relief,
                clamp01(0.75 * volcanic + 0.10),
                clamp01(0.30 * volcanic),
                clamp01(crystal), 0.25, 0.35,
                0.30, clamp01(impact), 0.10, 0.35,
                0.60, 0.50, 1.0, WaterPhaseModel.Phase.LIQUID, null);
    }

    /** Gravity-driven vertical terrain scale (low gravity keeps tall fragile relief).
     *  Public so the terrain composer (ACT 4) reuses the SAME ACT 1 gravity scale instead of
     *  re-deriving it. */
    public static double verticalScale(GravityClass gravity) {
        if (gravity == null) return 1.0;
        return switch (gravity) {
            case MICRO -> 1.35;
            case LOW -> 1.15;
            case STANDARD -> 1.0;
            case HIGH -> 0.88;
            case CRUSHING -> 0.75;
        };
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
}
