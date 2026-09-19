package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Free-form, composable material tag (R16 planet-diversity foundation).
 *
 * <p>Tags are the expressive middle ground between the coarse {@link MaterialFamily} and
 * concrete Minecraft blocks: a {@link MaterialSpec} carries a small set of tags, and
 * generation rules can require / forbid tags without enumerating families. Tags are pure
 * domain vocabulary (no Minecraft types) and are stable string-like identifiers used as map
 * keys and in debug output.
 *
 * <p>Unlike a Java {@code enum} the catalogue is intentionally open: additional tags can be
 * introduced by adding constants here without rewriting rules, and lookups never depend on
 * ordinals (so adding a tag can never silently re-map an existing one).
 */
public enum MaterialTag {

    // thermal
    COLD,
    TEMPERATE,
    HOT,
    MOLTEN,
    SULFUROUS,

    // hydrological
    DRY,
    WET,
    SALINE,
    CRYOGENIC,
    SUBLIMATING,

    // geological structure
    IGNEOUS,
    VOLCANIC,
    SEDIMENTARY,
    METAMORPHIC,
    IMPACT,
    FRACTURED,
    LAYERED,
    POROUS,
    DENSE,

    // composition
    FERRUGINOUS,
    METALLIC,
    CRYSTALLINE,
    SILICEOUS,
    CALCAREOUS,
    CARBONACEOUS,
    ORGANIC,

    // surface behaviour
    REGOLITH,
    LOOSE,
    COHESIVE,
    GLOSSY,
    GLOWING,
    RADIOACTIVE,

    // formation role
    SURFACE_FORMING,
    SUBSURFACE_FORMING,
    DEEP_FORMING,
    CAVE_FORMING,
    GEOTHERMAL,
    RARE,
    ACCENT
}
