package com.modscreating.unlimitedspace.core.galaxy;

/**
 * Configuration of a galaxy. Size and density are configurable and do NOT become
 * part of the stable identity or seed of any star system: they only shape
 * {@link Galaxy#estimatedSystemCount()} and the deterministic placement.
 *
 * @param radius      abstract radius in galaxy units
 * @param starDensity average density in stars per square unit
 * @param type        galaxy shape
 */
public record GalaxyParameters(double radius, double starDensity, GalaxyType type) {

    public static final GalaxyParameters DEFAULT =
            new GalaxyParameters(100.0, 0.8, GalaxyType.SPIRAL);

    public GalaxyParameters {
        if (radius <= 0) throw new IllegalArgumentException("radius must be positive");
        if (starDensity <= 0) throw new IllegalArgumentException("starDensity must be positive");
    }

    /** Cheap metadata estimate; never used to define identities or seeds. */
    public long estimatedSystemCount() {
        return (long) (radius * radius * starDensity);
    }
}
