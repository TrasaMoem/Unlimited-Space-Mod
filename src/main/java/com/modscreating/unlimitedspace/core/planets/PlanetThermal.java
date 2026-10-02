package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;

/**
 * PHASE 1/2: the STELLAR-ENVIRONMENT facts of a planet, stored next to its properties so that
 * every downstream consumer (moons, biomes, materials, fluids, UI, diagnostics) reads the SAME
 * derived thermal context instead of recomputing or inventing one.
 *
 * <pre>
 * stars (all of them) ──► orbit (AU, e) ──► orbit-averaged flux ──► T_eq ──► T_surface
 * </pre>
 *
 * <p>{@code derived == false} means the planet was generated through the legacy path with no
 * stellar context (proof worlds / back-compat call sites): the temperature then still comes from
 * the archetype table, and the orbit/flux fields carry no physical meaning.
 *
 * @param equilibriumK  airless, albedo-corrected equilibrium temperature in K (0 when not derived)
 * @param orbitAU       physical orbital distance in AU (0 when not derived)
 * @param eccentricity  orbital eccentricity used by the flux averaging (0 when not derived)
 * @param stellarFlux   orbit-averaged relative solar flux (1 L☉ / AU² = 1)
 * @param greenhouse    bounded greenhouse multiplier applied to the equilibrium temperature
 *                      (1.0 = no atmosphere; see {@code StellarThermalModel.greenhouseMultiplier})
 * @param internalK     internal (tectonic / geothermal) heating contribution in K added on top
 *                      (0 for a geologically dead world; see {@code StellarThermalModel.internalHeatingK})
 * @param thermalClass  coarse thermal class derived from the FINAL surface temperature
 * @param derived       whether this context was really derived from a stellar system
 */
public record PlanetThermal(
        double equilibriumK,
        double orbitAU,
        double eccentricity,
        double stellarFlux,
        double greenhouse,
        double internalK,
        StellarThermalModel.ThermalClass thermalClass,
        boolean derived) {

    /** Derived context from a real stellar system (greenhouse / internal facts not carried). */
    public static PlanetThermal of(double equilibriumK, double orbitAU, double eccentricity,
                                   double stellarFlux, double surfaceK) {
        return of(equilibriumK, orbitAU, eccentricity, stellarFlux, 1.0, 0.0, surfaceK);
    }

    /**
     * ACT 1: the full derived context — every input of {@code T_surface = T_eq · G(P) + ΔT_internal}
     * is stored, so downstream consumers (moon model, UI, F3 overlay, previews, tests) read the same
     * chain instead of re-deriving it, and the stored pair can be verified exactly.
     */
    public static PlanetThermal of(double equilibriumK, double orbitAU, double eccentricity,
                                   double stellarFlux, double greenhouse, double internalK,
                                   double surfaceK) {
        return new PlanetThermal(equilibriumK, orbitAU, eccentricity, stellarFlux,
                greenhouse, internalK, StellarThermalModel.thermalClass(surfaceK), true);
    }

    /** Legacy context: no stellar system involved, only the surface temperature is meaningful. */
    public static PlanetThermal none(double surfaceK) {
        return new PlanetThermal(0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
                StellarThermalModel.thermalClass(surfaceK), false);
    }

    /** True when no ordinary surface volatile can exist (vapour / none). */
    public boolean isVolatileFree() {
        return thermalClass.isVolatileFree();
    }

    /** True when only solid water is possible at this thermal class. */
    public boolean isSolidWaterOnly() {
        return thermalClass.isSolidWaterOnly();
    }

    /** True when liquid water is thermally possible. */
    public boolean mayHoldLiquidWater() {
        return thermalClass.mayHoldLiquidWater();
    }

    /**
     * PHASE 9: the canonical one-line thermal fact string used by the F3 overlay, the navigation
     * UI and the headless reports. A derived context announces its orbit, flux, equilibrium
     * temperature, greenhouse multiplier and internal contribution; a legacy context says so
     * explicitly instead of pretending to have a star.
     *
     * @param surfaceK the REAL surface temperature carried by the planet's properties
     */
    public String describe(double surfaceK) {
        String core = StellarThermalModel.temperatureText(surfaceK)
                + " | " + thermalClass.displayName().toLowerCase(java.util.Locale.ROOT);
        if (!derived) return core + " | no stellar context";
        return String.format(java.util.Locale.ROOT,
                "orbit %.2f AU (e %.2f) | flux %.2fx | T_eq %.0f K | greenhouse x%.2f | internal %+.0f K | %s",
                orbitAU, eccentricity, stellarFlux, equilibriumK, greenhouse, internalK, core);
    }
}
