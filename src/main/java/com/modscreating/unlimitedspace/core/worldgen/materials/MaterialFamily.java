package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Reusable material family for planet surfaces (Phase 8, extended in R16).
 *
 * <p>Not bound to concrete Minecraft blocks, and never keyed by a planet's display name.
 *
 * <p>R16 planet-diversity foundation: the original 7 coarse families ({@link #ROCK} ..
 * {@link #ALIEN_ROCK}) are kept verbatim for back-compat (existing palettes, asteroid
 * profiles, client visual resolution and tests all depend on them). A finer, geologically
 * meaningful family catalogue is added alongside so material rules can express
 * "hot-only", "cold-only", "volcanic-only" or "crystalline" composition without abusing a
 * single coarse label. Each new constant belongs to a {@link MaterialSuperFamily} for
 * broad reasoning and to a {@link MaterialDurability} tier for tool/hardness defaults.
 */
public enum MaterialFamily {

    // ---------------- legacy coarse families (unchanged, back-compat) ----------------
    ROCK(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    SAND(MaterialSuperFamily.GRANULAR, MaterialDurability.SOFT),
    ICE(MaterialSuperFamily.ICE, MaterialDurability.SOFT),
    METAL(MaterialSuperFamily.METALLIC, MaterialDurability.STONE),
    CRYSTAL(MaterialSuperFamily.CRYSTALLINE, MaterialDurability.GEM),
    ORGANIC(MaterialSuperFamily.ORGANIC, MaterialDurability.SOFT),
    ALIEN_ROCK(MaterialSuperFamily.STONE, MaterialDurability.STONE),

    // ---------------- R16 fine rock families ----------------
    ROCK_LIGHT(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    ROCK_DARK(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    ROCK_RED(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    ROCK_VOLCANIC(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    ROCK_METALLIC(MaterialSuperFamily.METALLIC, MaterialDurability.STONE),
    ROCK_GLASS(MaterialSuperFamily.CRYSTALLINE, MaterialDurability.STONE),
    ROCK_CRYSTALLINE(MaterialSuperFamily.CRYSTALLINE, MaterialDurability.GEM),
    ROCK_FROZEN(MaterialSuperFamily.ICE, MaterialDurability.STONE),
    ROCK_SALINE(MaterialSuperFamily.SEDIMENTARY, MaterialDurability.SOFT),
    ROCK_SULFURIC(MaterialSuperFamily.STONE, MaterialDurability.SOFT),
    ROCK_BASALTIC(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    ROCK_SEDIMENTARY(MaterialSuperFamily.SEDIMENTARY, MaterialDurability.STONE),
    ROCK_IMPACT(MaterialSuperFamily.STONE, MaterialDurability.STONE),

    // ---------------- R16 fine soil families ----------------
    SOIL_DRY(MaterialSuperFamily.ORGANIC, MaterialDurability.SOFT),
    SOIL_RICH(MaterialSuperFamily.ORGANIC, MaterialDurability.SOFT),
    SOIL_FROZEN(MaterialSuperFamily.ORGANIC, MaterialDurability.SOFT),
    SOIL_ASH(MaterialSuperFamily.ORGANIC, MaterialDurability.SOFT),
    SOIL_SALT(MaterialSuperFamily.SEDIMENTARY, MaterialDurability.SOFT),

    // ---------------- R16 fine sand / sediment families ----------------
    SAND_RED(MaterialSuperFamily.GRANULAR, MaterialDurability.SOFT),
    SAND_PALE(MaterialSuperFamily.GRANULAR, MaterialDurability.SOFT),
    SAND_DARK(MaterialSuperFamily.GRANULAR, MaterialDurability.SOFT),

    // ---------------- R16 special roles ----------------
    ORE_HOST(MaterialSuperFamily.STONE, MaterialDurability.STONE),
    SPECIAL(MaterialSuperFamily.STONE, MaterialDurability.STONE);

    /** Broad reasoning super-family every fine family belongs to. */
    public enum MaterialSuperFamily {
        STONE,
        GRANULAR,
        ICE,
        METALLIC,
        CRYSTALLINE,
        SEDIMENTARY,
        ORGANIC
    }

    /** Default durability tier (drives tool tier / hardness defaults for registered blocks). */
    public enum MaterialDurability {
        SOFT,
        STONE,
        GEM
    }

    private final MaterialSuperFamily superFamily;
    private final MaterialDurability durability;

    MaterialFamily(MaterialSuperFamily superFamily, MaterialDurability durability) {
        this.superFamily = superFamily;
        this.durability = durability;
    }

    public MaterialSuperFamily superFamily() {
        return superFamily;
    }

    public MaterialDurability durability() {
        return durability;
    }

    /** True for families that are only geologically plausible on a cryogenic world. */
    public boolean isColdOnly() {
        return this == ICE || this == ROCK_FROZEN || this == SOIL_FROZEN;
    }

    /** True for families that are only geologically plausible on a hot world. */
    public boolean isHotOnly() {
        return this == ROCK_VOLCANIC || this == ROCK_SULFURIC || this == SOIL_ASH || this == ROCK_BASALTIC;
    }

    /** True for families that require a volcanically/geothermally driven planet. */
    public boolean requiresVolcanism() {
        return this == ROCK_VOLCANIC || this == ROCK_SULFURIC || this == SOIL_ASH;
    }

    /** True for families that require a wet/saline environment. */
    public boolean requiresWater() {
        return this == ROCK_SALINE || this == SOIL_SALT || this == SOIL_RICH;
    }

    /** True for metal-bearing families. */
    public boolean isMetalliferous() {
        return superFamily == MaterialSuperFamily.METALLIC;
    }
}
