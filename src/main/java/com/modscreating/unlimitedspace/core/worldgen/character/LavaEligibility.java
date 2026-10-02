package com.modscreating.unlimitedspace.core.worldgen.character;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * HARD SAFETY SYSTEM for lava generation across the mod.
 *
 * <p>Formula:
 * {@code lavaEligibility = thermalSurfaceEligibility * volcanicPotential * localHotspot * terrainSuitability}
 *
 * <p>COLD LAVA RULE:
 * On cryogenic/frozen worlds (T < 273.15 K), giant lava seas are forbidden (lavaSurfaceShare == 0).
 * Only localized, micro geothermal hot pockets (< 400 blocks^2) are physically permitted.
 *
 * <p>CRITICAL ARCHITECTURE INVARIANT:
 * Never allow {@code province == VOLCANIC} to be sufficient by itself to spawn surface lava.
 */
public final class LavaEligibility {

    private LavaEligibility() {}

    /** Freezing threshold in Kelvin. Below this, surface lava seas are forbidden. */
    public static final double FROZEN_THRESHOLD_K = WaterPhaseModel.FREEZE_K;

    /**
     * Compute planetary baseline lava eligibility in [0,1].
     */
    public static double planetBaseline(double kelvin, double volcanicActivity, double geothermalFlux) {
        if (volcanicActivity <= 0.05 && geothermalFlux <= 0.05) {
            return 0.0;
        }

        // COLD LAVA RULE (hard safety): below freezing, unconfined surface lava is impossible, so
        // the PLANET-WIDE baseline is exactly zero. A frozen world may only ever show a localised
        // geothermal micro-vent, and that is granted exclusively by the column-level
        // {@link #evaluate} path, which additionally requires an extreme local hotspot and a
        // terrain-suitability factor. A non-zero baseline here is what used to let an entire frozen
        // province flood itself with lava.
        if (kelvin < FROZEN_THRESHOLD_K) {
            return 0.0;
        }

        // Thermal eligibility: warmer worlds allow an increasingly open lava surface.
        double thermalFactor = clamp01((kelvin - 250.0) / 150.0);
        double volcanicPotential = clamp01(volcanicActivity * 0.7 + geothermalFlux * 0.3);
        return clamp01(thermalFactor * volcanicPotential);
    }

    /**
     * Compute full column-level lava eligibility.
     *
     * @param planetK            planet surface temperature in Kelvin
     * @param volcanicPotential  planetary volcanic activity [0,1]
     * @param localHotspot       local thermal/volcanic hotspot intensity [0,1]
     * @param terrainSuitability suitability factor (e.g., negative relief, fissure/caldera floor) [0,1]
     * @return continuous lava eligibility in [0,1]
     */
    public static double evaluate(double planetK, double volcanicPotential,
                                  double localHotspot, double terrainSuitability) {
        if (volcanicPotential <= 0.01 || localHotspot <= 0.05) return 0.0;

        double thermalFactor;
        if (planetK < FROZEN_THRESHOLD_K) {
            // Cold worlds: strictly forbid large unconfined lava bodies.
            // Only intense localized hotspots can trigger micro-activity.
            if (localHotspot < 0.85) return 0.0;
            thermalFactor = 0.15 * (localHotspot - 0.85) / 0.15;
        } else {
            thermalFactor = clamp01((planetK - 240.0) / 120.0);
        }

        return clamp01(thermalFactor * volcanicPotential * localHotspot * terrainSuitability);
    }

    /**
     * Whether a fluid pool or channel at this column is allowed to be filled with molten fluid.
     */
    public static boolean isLavaAllowed(double eligibility, boolean isFrozenPlanet) {
        if (isFrozenPlanet) {
            // On frozen planets, eligibility must be exceptionally high (strict localized hotspot)
            return eligibility > 0.08;
        }
        return eligibility > 0.20;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
