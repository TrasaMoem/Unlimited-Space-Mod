package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Visual grouping of a material, used by the client layer to pick palettes, textures and
 * ambience (R16 planet-diversity foundation).
 *
 * <p>Kept separate from {@link MaterialFamily} because a geologically identical rock can read
 * very differently depending on its finish (glossy volcanic glass vs matte basalt). Pure
 * domain: no Minecraft types — the renderer maps each role to concrete tints/textures.
 */
public enum MaterialVisualRole {

    /** Dull, matte stone (the common case). */
    STONE,
    /** Very light / pale stone (calcite, salt, frost). */
    PALE_STONE,
    /** Very dark stone (basalt, blackstone, soot). */
    DARK_STONE,
    /** Reddish/ochre rock (rust, red sandstone). */
    RED_ROCK,
    /** Metallic, reflective surface (iron-rich rock, carbonyl rock). */
    METALLIC,
    /** Vitreous / glassy (obsidian, fulgurite, impact glass). */
    GLASSY,
    /** Faceted, mineralogical and emissive (amethyst, crystal veins). */
    CRYSTALLINE,
    /** Frozen / icy surface. */
    FROZEN,
    /** Loose granular sediment (sand, regolith). */
    GRANULAR,
    /** Organic / soil-like surface. */
    ORGANIC,
    /** Brightly emissive (luminite, molten vents). */
    LUMINOUS,
    /** Chemically altered / sulfurous crust. */
    CHEMICAL
}
