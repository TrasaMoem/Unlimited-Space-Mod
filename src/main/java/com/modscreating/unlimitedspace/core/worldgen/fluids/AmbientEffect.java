package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

/**
 * Province ambient-effect identity (R19 ambient-life stage).
 *
 * <p>The informational layer between PROVINCE / ATMOSPHERE and the client particle renderer:
 * which ambient life a region exhales (ash + embers, steam, glow motes, frost dust, impact or
 * salt dust), and how strongly the province itself drives it. The CLIENT stays fully
 * responsible for spawn rate / distance culling — this layer never spawns anything.
 *
 * <p>Pure domain, no Minecraft types; deterministic given the province.
 */
public enum AmbientEffect {

    /** Volcanic ashfall with occasional embers. */
    ASH_EMBERS(1.00),
    /** Geothermal steam plumes. */
    STEAM(0.85),
    /** Sparse glowing motes (crystal / luminous regions). */
    GLOW_MOTES(0.55),
    /** Frost and ice dust on glacial terrain. */
    FROST_DUST(0.65),
    /** Occasional impact dust (crater fields). */
    IMPACT_DUST(0.35),
    /** Subtle pale salt dust. */
    SALT_DUST(0.25),
    /** No province-driven ambient effect (plains / mountain / canyon / basin). */
    NONE(0.0);

    /** The province's own drive multiplier in [0,1] (part of the intensity function). */
    private final double provinceFactor;

    AmbientEffect(double provinceFactor) {
        this.provinceFactor = provinceFactor;
    }

    /** The province drive multiplier in [0,1] — one factor of {@code effectIntensity}. */
    public double provinceFactor() {
        return provinceFactor;
    }

    /**
     * The ambient effect a province exhales (deterministic; every volcanic region reads as
     * ash + embers, every geothermal region as steam, etc.).
     */
    public static AmbientEffect forProvince(GeologicalProvince province) {
        if (province == null) return NONE;
        return switch (province) {
            case VOLCANIC -> ASH_EMBERS;
            case GEOTHERMAL -> STEAM;
            case CRYSTAL -> GLOW_MOTES;
            case GLACIAL -> FROST_DUST;
            case CRATER -> IMPACT_DUST;
            case SALT -> SALT_DUST;
            default -> NONE;
        };
    }
}