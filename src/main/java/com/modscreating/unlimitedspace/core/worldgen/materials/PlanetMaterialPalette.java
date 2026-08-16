package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Data-driven planet material palette: which materials a planet uses and in which
 * layers. Kept separate from Minecraft terrain generation.
 *
 * <p>R8: the palette is now selected deterministically per planet (see
 * {@link PlanetMaterialSelector}) by surface category, so each planet gets a consistent
 * block family (rocky -> stone/deepslate, icy -> packed ice/blue ice, volcanic ->
 * basalt/blackstone, ...). The {@link #family()} of the surface material drives the
 * abstract {@link com.modscreating.unlimitedspace.core.worldgen.SurfaceMaterial} used
 * for identity, while the concrete {@code blockId}s drive the Minecraft blocks.
 */
public record PlanetMaterialPalette(
        PlanetMaterial surface,
        PlanetMaterial subsurface,
        PlanetMaterial deepStone,
        PlanetMaterial sand,
        PlanetMaterial gravel) {

    /** Semantic family of the surface material (drives abstract SurfaceMaterial mapping). */
    public MaterialFamily family() {
        return surface.family();
    }

    public static PlanetMaterialPalette rocky() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("rocky.surface", MaterialFamily.ROCK, "minecraft:stone"),
                PlanetMaterial.of("rocky.subsurface", MaterialFamily.ROCK, "minecraft:stone"),
                PlanetMaterial.of("rocky.deepstone", MaterialFamily.ROCK, "minecraft:deepslate"),
                PlanetMaterial.of("rocky.sand", MaterialFamily.SAND, "minecraft:sand"),
                PlanetMaterial.of("rocky.gravel", MaterialFamily.ROCK, "minecraft:gravel"));
    }

    public static PlanetMaterialPalette icy() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("icy.surface", MaterialFamily.ICE, "minecraft:packed_ice"),
                PlanetMaterial.of("icy.subsurface", MaterialFamily.ICE, "minecraft:packed_ice"),
                PlanetMaterial.of("icy.deepstone", MaterialFamily.ROCK, "minecraft:deepslate"),
                PlanetMaterial.of("icy.sand", MaterialFamily.SAND, "minecraft:snow_block"),
                PlanetMaterial.of("icy.gravel", MaterialFamily.ICE, "minecraft:blue_ice"));
    }

    public static PlanetMaterialPalette desert() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("desert.surface", MaterialFamily.SAND, "minecraft:sand"),
                PlanetMaterial.of("desert.subsurface", MaterialFamily.SAND, "minecraft:sandstone"),
                PlanetMaterial.of("desert.deepstone", MaterialFamily.ROCK, "minecraft:terracotta"),
                PlanetMaterial.of("desert.sand", MaterialFamily.SAND, "minecraft:red_sand"),
                PlanetMaterial.of("desert.gravel", MaterialFamily.ROCK, "minecraft:gravel"));
    }

    public static PlanetMaterialPalette volcanic() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("volcanic.surface", MaterialFamily.METAL, "minecraft:basalt"),
                PlanetMaterial.of("volcanic.subsurface", MaterialFamily.ROCK, "minecraft:basalt"),
                PlanetMaterial.of("volcanic.deepstone", MaterialFamily.ALIEN_ROCK, "minecraft:blackstone"),
                PlanetMaterial.of("volcanic.sand", MaterialFamily.SAND, "minecraft:red_sand"),
                PlanetMaterial.of("volcanic.gravel", MaterialFamily.ROCK, "minecraft:cobbled_deepslate"));
    }

    public static PlanetMaterialPalette metallic() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("metallic.surface", MaterialFamily.METAL, "minecraft:smooth_basalt"),
                PlanetMaterial.of("metallic.subsurface", MaterialFamily.METAL, "minecraft:smooth_basalt"),
                PlanetMaterial.of("metallic.deepstone", MaterialFamily.ROCK, "minecraft:deepslate"),
                PlanetMaterial.of("metallic.sand", MaterialFamily.METAL, "minecraft:iron_block"),
                PlanetMaterial.of("metallic.gravel", MaterialFamily.METAL, "minecraft:iron_block"));
    }

    public static PlanetMaterialPalette oceanic() {
        return new PlanetMaterialPalette(
                PlanetMaterial.of("oceanic.surface", MaterialFamily.SAND, "minecraft:sand"),
                PlanetMaterial.of("oceanic.subsurface", MaterialFamily.SAND, "minecraft:sandstone"),
                PlanetMaterial.of("oceanic.deepstone", MaterialFamily.ROCK, "minecraft:stone"),
                PlanetMaterial.of("oceanic.sand", MaterialFamily.SAND, "minecraft:sand"),
                PlanetMaterial.of("oceanic.gravel", MaterialFamily.ROCK, "minecraft:gravel"));
    }
}
