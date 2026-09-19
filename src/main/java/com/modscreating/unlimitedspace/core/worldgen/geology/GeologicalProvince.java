package com.modscreating.unlimitedspace.core.worldgen.geology;

/**
 * Geological province of a planet surface (R16 planet-diversity foundation).
 *
 * <p>A province is a LARGE regional zone (hundreds of blocks across), not noise-sized patches.
 * Provinces are classified deterministically from terrain + climate signals and drive surface
 * material, stone, ores, fluids, vegetation, structures and particles — so a planet reads as a
 * set of recognisable regions rather than random noise.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum GeologicalProvince {

    /** Active volcanic terrain: fresh igneous flows, vents, sulfur. */
    VOLCANIC,
    /** High-relief mountain ranges. */
    MOUNTAIN,
    /** Impact-cratered terrain: ejecta, impact glass, breccia. */
    CRATER,
    /** Low basins / dry lakebeds / sediment traps. */
    BASIN,
    /** Default temperate plains. */
    PLAINS,
    /** Deeply eroded canyon terrain. */
    CANYON,
    /** Crystal-bearing fields. */
    CRYSTAL,
    /** Salt flats / evaporite basins. */
    SALT,
    /** Glacial terrain. */
    GLACIAL,
    /** Geothermal fields: hot springs, fumaroles, altered rock. */
    GEOTHERMAL;

    public static final GeologicalProvince[] VALUES = values();

    /** True for provinces that require significant surface liquid. */
    public boolean requiresWater() {
        return this == SALT;
    }

    /** True for provinces that only make sense on a volcanically driven planet. */
    public boolean requiresVolcanism() {
        return this == VOLCANIC || this == GEOTHERMAL;
    }

    /** True for provinces that only make sense on a cryogenic planet. */
    public boolean requiresCold() {
        return this == GLACIAL;
    }
}
