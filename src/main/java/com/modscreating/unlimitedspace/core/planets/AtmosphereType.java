package com.modscreating.unlimitedspace.core.planets;

/**
 * Atmosphere archetype with a nominal relative density. Pure data; describes
 * whether/how a planet supports life later, without Minecraft coupling.
 */
public enum AtmosphereType {
    NONE(0.02),
    TRACE(0.08),
    THIN(0.30),
    MODERATE(0.55),
    DENSE(0.80),
    CORROSIVE(0.60),
    GASEOUS(0.95);

    private final double densityBase;

    AtmosphereType(double densityBase) {
        this.densityBase = densityBase;
    }

    public double densityBase() {
        return densityBase;
    }
}
