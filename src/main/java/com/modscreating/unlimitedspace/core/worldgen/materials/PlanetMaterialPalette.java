package com.modscreating.unlimitedspace.core.worldgen.materials;

/**
 * Data-driven planet material palette: which materials a planet uses and in which
 * layers. Kept separate from Minecraft terrain generation.
 */
public record PlanetMaterialPalette(
        PlanetMaterial surface,
        PlanetMaterial subsurface,
        PlanetMaterial deepStone,
        PlanetMaterial sand,
        PlanetMaterial gravel) {

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
}