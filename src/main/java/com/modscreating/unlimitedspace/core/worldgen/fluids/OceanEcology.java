package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Gradual ocean-coverage ecology of a planet (R19 fluid-ecology stage).
 *
 * <p>Water is deliberately NOT binary ("ocean or dry"): the surface-liquid fraction is a
 * continuous function of the planet's physical profile, so the same planet profile always
 * yields the same seascape class and the same {@link #liquidFraction()}. A HIGH-abundance
 * world grows large connected oceans, a MEDIUM world grows seas + lakes, a LOW world keeps
 * isolated basins, and a VERY LOW world stays mostly dry.
 *
 * <p>Pure domain, no Minecraft types; deterministic given the physical profile.
 */
public enum OceanEcology {

    /** Connected planet-wide oceans dominate the surface. */
    LARGE_OCEANS(1.00),
    /** Regional seas plus inland lakes — the classic continental look. */
    SEAS_AND_LAKES(0.62),
    /** Sparse isolated basins; most of the surface is dry land. */
    ISOLATED_LAKES(0.28),
    /** Essentially a dry world; liquid survives only in the rarest basins (or not at all). */
    MOSTLY_DRY(0.06),
    /** No surface liquid is physically possible (vacuum + no water). */
    NO_SURFACE_LIQUID(0.0);

    /** Representative continuous surface-liquid fraction in [0,1] for this class. */
    private final double liquidFraction;

    OceanEcology(double liquidFraction) {
        this.liquidFraction = liquidFraction;
    }

    /** The continuous surface-liquid fraction in [0,1] (0 = dry, 1 = ocean world). */
    public double liquidFraction() {
        return liquidFraction;
    }

    /** True when any surface liquid exists at all. */
    public boolean hasLiquid() {
        return liquidFraction > 0.0;
    }

    /**
     * Canonical classification: physical profile &rarr; ocean ecology.
     *
     * <p>Blends waterAbundance (how much liquid exists), oceanCoverage (how much of it pools
     * into basins) and continentality (how much of it is locked inland). A dry + continental
     * world dries out faster than its raw waterAbundance suggests; a wet + low-continentality
     * world floods readily.
     */
    public static OceanEcology of(PlanetPhysicalProfile p) {
        if (p == null) return NO_SURFACE_LIQUID;
        if (!p.canHoldSurfaceLiquid()) return NO_SURFACE_LIQUID;

        // Continuous liquid share: abundance vs ocean pooling, moderated by continental locking.
        double continentalLock = 0.35 * p.continentality();
        double share = p.waterAbundance() * (0.55 + 0.9 * p.oceanCoverage()) - continentalLock;

        if (share >= 0.42) return LARGE_OCEANS;
        if (share >= 0.20) return SEAS_AND_LAKES;
        if (share >= 0.06) return ISOLATED_LAKES;
        return MOSTLY_DRY;
    }
}