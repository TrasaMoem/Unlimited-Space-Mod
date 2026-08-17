package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Archetype of a moon. Represents moon-specific generation logic.
 * Distinct from {@link PlanetType} — a moon type reflects moon-specific characteristics
 * (surface composition, activity, environment) rather than a scaled-down planet.
 */
public enum MoonType {

    ROCKY    (0.30, "rocky, cratered, geologically inactive"),
    BARREN   (0.22, "barren, minimal atmosphere, regolith-covered"),
    ICE      (0.18, "ice-dominated, potential subsurface water"),
    CRATERED (0.12, "heavily cratered, ancient surface, thin regolith"),
    OCEANIC  (0.08, "substantial water coverage, hydrology"),
    VOLCANIC (0.05, "volcanically active, hot terrain"),
    DESERT   (0.03, "arid, minimal water, rocky surface"),
    METALLIC (0.02, "metal-rich, high density, unusual composition");

    private final double occurrenceWeight;
    private final String description;

    MoonType(double occurrenceWeight, String description) {
        this.occurrenceWeight = occurrenceWeight;
        this.description = description;
    }

    public double occurrenceWeight() { return occurrenceWeight; }
    public String description() { return description; }

    /** Weighted, deterministic type selection (roughly realistic distribution). */
    static MoonType pickType(long seed) {
        double f = Seeds.fraction(seed, 0L);
        double acc = 0.0;
        for (MoonType type : values()) {
            acc += type.occurrenceWeight;
            if (f < acc) return type;
        }
        return ROCKY;
    }
}
