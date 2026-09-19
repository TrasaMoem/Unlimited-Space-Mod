package com.modscreating.unlimitedspace.core.worldgen.profile;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;

/**
 * Coarse surface-pressure classification (R16 planet-diversity foundation).
 *
 * <p>Derived from the planet's {@link AtmosphereType} density base plus the normalized
 * {@code atmosphericDensity}, quantified into a stable class. Fluid/vegetation/effect rules
 * use it to decide whether a liquid can exist at the surface and how hazily the atmosphere
 * reads. Pure domain: no Minecraft types.
 */
public enum PressureClass {

    VACUUM(0.00, 0.05),
    TRACE(0.05, 0.18),
    THIN(0.18, 0.40),
    MODERATE(0.40, 0.65),
    DENSE(0.65, 0.85),
    CRUSHING(0.85, 1.01);

    public static final PressureClass[] VALUES = values();

    private final double minPressure;
    private final double maxPressure;

    PressureClass(double minPressure, double maxPressure) {
        this.minPressure = minPressure;
        this.maxPressure = maxPressure;
    }

    public double minPressure() {
        return minPressure;
    }

    public double maxPressure() {
        return maxPressure;
    }

    /** True when the pressure is too low to hold a stable surface liquid. */
    public boolean isNearVacuum() {
        return this == VACUUM || this == TRACE;
    }

    /** Deterministic (atmosphere, density) &rarr; class. */
    public static PressureClass of(AtmosphereType atmosphere, double atmosphericDensity) {
        double base = atmosphere == null ? 0.0 : atmosphere.densityBase();
        double d = atmosphericDensity;
        if (d < 0.0) d = 0.0;
        if (d > 1.0) d = 1.0;
        // Blend the archetype's nominal density with the planet's actual density so both
        // the atmosphere kind and its realized thickness influence the class.
        double v = 0.5 * base + 0.5 * d;
        return of(v);
    }

    /** Deterministic class lookup for a normalized pressure in {@code [0,1]}. */
    public static PressureClass of(double normalized) {
        double v = normalized;
        if (v < 0.0) v = 0.0;
        if (v > 1.0) v = 1.0;
        for (PressureClass c : VALUES) {
            if (v < c.maxPressure) return c;
        }
        return CRUSHING;
    }
}
