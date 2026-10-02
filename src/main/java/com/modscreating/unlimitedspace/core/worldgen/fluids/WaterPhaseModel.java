package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;

/**
 * PHASE 3 (hydrology): WATER PHASE of a surface — SOLID / LIQUID / VAPOR / NONE,
 * with an internal MIXED state for the near-freezing transition band.
 *
 * <p><b>Hard invariant (3.1):</b> below the freezing point ordinary liquid surface water
 * is IMPOSSIBLE. The band {@link #FREEZE_K}..{@link #MIXED_TOP_K} is a small gameplay
 * tolerance (brines freeze below pure water), never a licence for oceans on a frozen world.
 *
 * <p><b>Pressure (3.2):</b> a near-vacuum atmosphere cannot hold a stable surface liquid,
 * so VACUUM / TRACE pressure means dry (bare ice survives without pressure).
 *
 * <p><b>Boiling (3.3):</b> a bounded pressure-dependent boiling approximation — a thin
 * atmosphere boils water at far lower temperatures; a dense one holds it to well above
 * 373 K. Deliberately NOT a full phase diagram.
 *
 * <p>The canonical planet temperature is the REAL Kelvin value on
 * {@code PlanetProperties.temperature()} (Phase 1). The physical profile carries only the
 * log-normalized axis, so callers recover Kelvin via {@code StellarThermalModel.denormalizeKelvin}.
 *
 * <p>Pure domain: no Minecraft types. Deterministic, allocation-free.
 */
public final class WaterPhaseModel {

    private WaterPhaseModel() {}

    /** Freezing point of pure water (K). At or below this no liquid surface water exists. */
    public static final double FREEZE_K = 273.15;
    /** Top of the near-freezing mixed band (K): brine tolerance above the freeze point. */
    public static final double MIXED_TOP_K = 277.0;
    /** Boiling point of water at Earth-like (MODERATE) surface pressure (K). */
    public static final double BOIL_MODERATE_K = 373.15;
    /** Above this temperature even a dense atmosphere cannot keep surface water stable (K). */
    public static final double VOLATILE_LIMIT_K = 640.0;

    /** Water phase of a surface (public API; MIXED is the internal transition state). */
    public enum Phase {
        /** Frozen surface water: ice sheets, frozen seas. */
        SOLID,
        /** Stable liquid surface water. */
        LIQUID,
        /** Boiling / sublimating: no standing liquid survives. */
        VAPOR,
        /** No surface water at all (too little water, or thermally destructive). */
        NONE,
        /** Internal transition state: ice with possible local liquid (near-freezing band). */
        MIXED;

        public static final Phase[] VALUES = values();

        /** True when standing surface liquid (water/brine) may be placed. */
        public boolean allowsLiquid() {
            return this == LIQUID || this == MIXED;
        }

        /** True when ice-family blocks are the correct fluid fill. */
        public boolean isSolid() {
            return this == SOLID || this == MIXED;
        }

        /** True when NO ordinary surface water may exist (vapour or nothing). */
        public boolean isDry() {
            return this == VAPOR || this == NONE;
        }

        /** PHASE 9: human-facing label for the UI / diagnostics. */
        public String displayName() {
            return switch (this) {
                case SOLID -> "Ice";
                case MIXED -> "Ice / brine";
                case LIQUID -> "Liquid water";
                case VAPOR -> "Steam";
                case NONE -> "None";
            };
        }
    }

    /**
     * Planet-scale surface water phase.
     *
     * @param surfaceK       REAL Kelvin mean surface temperature (clamped to the canonical range)
     * @param pressure       quantized surface pressure class
     * @param waterAbundance normalized surface/subsurface water availability in [0,1]
     */
    public static Phase surfacePhase(double surfaceK, PressureClass pressure, double waterAbundance) {
        if (waterAbundance <= 0.02) return Phase.NONE;
        double k = StellarThermalModel.clampKelvin(surfaceK);
        // Extreme heat destroys surface volatiles outright (ultrahot / molten regimes).
        if (k >= VOLATILE_LIMIT_K) return Phase.NONE;
        // Near-vacuum cannot hold a stable liquid; bare ice survives without pressure.
        if (pressure != null && pressure.isNearVacuum()) {
            return k < FREEZE_K ? Phase.SOLID : Phase.NONE;
        }
        if (k < FREEZE_K) return Phase.SOLID;
        if (k < MIXED_TOP_K) return Phase.MIXED;
        if (k >= boilingK(pressure)) return Phase.VAPOR;
        return Phase.LIQUID;
    }

    /**
     * PHASE 9 convenience for the UI / diagnostics: the surface phase straight from the RAW
     * planet (or moon) properties, i.e. without building a physical profile first. It applies the
     * same pressure quantization and consumes the same water availability the worldgen feeds into
     * {@link #surfacePhase} (see {@code PlanetPhysicalProfileFactory.waterAbundance}), so what the
     * player reads is what the chunk generator will place.
     *
     * @param surfaceK           REAL Kelvin mean surface temperature
     * @param atmosphere         atmosphere archetype (may be null)
     * @param atmosphericDensity normalized atmospheric density in [0,1]
     * @param waterAvailability  canonical water availability in [0,1]
     */
    public static Phase ofProperties(double surfaceK, AtmosphereType atmosphere,
                                     double atmosphericDensity, double waterAvailability) {
        return surfacePhase(surfaceK, PressureClass.of(atmosphere, atmosphericDensity),
                waterAvailability);
    }

    /**
     * Bounded pressure-dependent boiling approximation (3.3): thin air boils low,
     * dense air holds liquid to well above 373 K.
     */
    public static double boilingK(PressureClass pressure) {
        if (pressure == null) return BOIL_MODERATE_K;
        return switch (pressure) {
            case VACUUM -> 250.0;
            case TRACE -> 300.0;
            case THIN -> 330.0;
            case MODERATE -> BOIL_MODERATE_K;
            case DENSE -> 405.0;
            case CRUSHING -> 450.0;
        };
    }

    /**
     * PHASE 3.4: LOCAL surface Kelvin for one column — the planet's thermal baseline
     * modulated by the coherent climate field and the elevation lapse. Deliberately cheap
     * and monotonic: a warmer-than-average climate cell can push a near-freezing planet's
     * lowlands above the freeze point; a colder cell cannot turn a hot world icy.
     *
     * @param planetK     REAL Kelvin planet mean surface temperature
     * @param climateT01  normalized local climate temperature in [0,1] (0.5 = planet average)
     * @param elevation01 normalized column elevation in [0,1]
     */
    public static double localSurfaceKelvin(double planetK, double climateT01, double elevation01) {
        double k = StellarThermalModel.clampKelvin(planetK);
        double c = climateT01 < 0.0 ? 0.0 : (climateT01 > 1.0 ? 1.0 : climateT01);
        // ±37 % at the climate extremes (exp-space keeps the value strictly positive).
        double kClimate = k * Math.exp((c - 0.5) * 0.9);
        // Lapse rate: only high ground cools, and only above the upper third.
        double e = elevation01 < 0.0 ? 0.0 : (elevation01 > 1.0 ? 1.0 : elevation01);
        double lapse = Math.max(0.0, e - 0.55) * 2.2;
        return StellarThermalModel.clampKelvin(kClimate * (1.0 - 0.16 * lapse));
    }

    /**
     * PHASE 3.7: LOCAL phase of a geothermal pocket on a globally frozen world.
     * A GEOTHERMAL (or VOLCANIC) province with a strong geothermal flux may hold rare
     * local liquid water/brine; every other province inherits the global phase.
     */
    public static Phase geothermalPocketPhase(Phase global, GeologicalProvince province,
                                              double geothermalFlux) {
        if (global != Phase.SOLID && global != Phase.MIXED) return global;
        if (province != GeologicalProvince.GEOTHERMAL && province != GeologicalProvince.VOLCANIC) {
            return global;
        }
        return geothermalFlux > 0.55 ? Phase.LIQUID : global;
    }

    /**
     * Convenience: planet-scale phase straight from the physical profile (recovers the
     * REAL Kelvin from the profile's log-normalized temperature axis).
     */
    public static Phase ofProfile(PlanetPhysicalProfile p) {
        if (p == null) return Phase.NONE;
        double kelvin = StellarThermalModel.denormalizeKelvin(p.temperature01());
        return surfacePhase(kelvin, p.pressureClass(), p.waterAbundance());
    }

    /** True when this temperature still allows SOME surface volatile expression. */
    public static boolean supportsVolatiles(double surfaceK) {
        return StellarThermalModel.clampKelvin(surfaceK) < VOLATILE_LIMIT_K;
    }
}

