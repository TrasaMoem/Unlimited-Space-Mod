package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Generation role a material plays in a planet's material palette (R16 planet-diversity
 * foundation).
 *
 * <p>A {@link PlanetMaterialPalette} fills every role from the planet's own deterministic
 * material catalogue; the chunk generator then reads each role directly (surface / subsurface /
 * deep stone / cave / mountain / crater / geothermal / crystal / ore host / fluid family), so
 * worldgen never performs ad-hoc block selection.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum MaterialRole {

    /** Topmost surface layer of the default province. */
    PRIMARY_SURFACE,
    /** Second surface layer used to break up the primary (patches / exposed bedrock). */
    SECONDARY_SURFACE,
    /** Bulk stone filling the world below the surface. */
    DEEP_STONE,
    /** Rare high-contrast accent material (thin bands, exotic outcrops). */
    ACCENT,
    /** Rare high-value material (ore-bearing host or unusual rock). */
    RARE,
    /** Soil layer for biomes/provinces that support organics. */
    SOIL,
    /** Sand / fine sediment for deserts and coasts. */
    SEDIMENT,
    /** Host rock for ore deposits. */
    ORE_HOST,
    /** Cave lining / underground fill. */
    CAVE,
    /** Mountain / high-relief building material. */
    MOUNTAIN,
    /** Crater floor / impact ejecta material. */
    CRATER,
    /** Geothermal / hydrothermal alteration material. */
    GEOTHERMAL,
    /** Crystal / geode formation material. */
    CRYSTAL
}
