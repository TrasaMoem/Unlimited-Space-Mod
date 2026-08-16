package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.SurfaceMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-only mapper from the abstract {@link SurfaceMaterial}/{@link FluidProfile}
 * to concrete {@link BlockState}s. This is the ONLY place in the codebase where the
 * abstract core materials become Minecraft blocks — the core never references
 * {@code BlockState}.
 */
public final class PlanetBlocks {

    private PlanetBlocks() {}

    public static BlockState surface(SurfaceMaterial material) {
        return switch (material) {
            case STONE -> Blocks.STONE.defaultBlockState();
            case ROCK -> Blocks.DIORITE.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case ICE -> Blocks.ICE.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            case GRASSY -> Blocks.GRASS_BLOCK.defaultBlockState();
            case METALLIC -> Blocks.SMOOTH_BASALT.defaultBlockState();
        };
    }

    public static BlockState subsurface(SurfaceMaterial material) {
        return switch (material) {
            case ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case SAND -> Blocks.SANDSTONE.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    public static BlockState fluid(FluidProfile profile) {
        return switch (profile) {
            case WATER -> Blocks.WATER.defaultBlockState();
            case NONE -> Blocks.AIR.defaultBlockState();
        };
    }

    /**
     * Resolve a concrete {@link BlockState} from a {@link PlanetMaterial}'s registry key.
     * Used by the R8 material palette: each planet now carries a seed-driven block family
     * (e.g. planetary surface = stone/deepslate vs packed_ice/blue_ice vs basalt/blackstone),
     * not a single static surface material.
     */
        public static BlockState material(PlanetMaterial material) {
        if (material == null) return Blocks.STONE.defaultBlockState();
        // Data-driven: the palette carries a stable registry key string; we resolve it to a
        // Blocks constant directly (robust against the 1.21 registry API rename of getValue).
        return switch (material.blockId()) {
            case "minecraft:deepslate" -> Blocks.DEEPSLATE.defaultBlockState();
            case "minecraft:packed_ice" -> Blocks.PACKED_ICE.defaultBlockState();
            case "minecraft:blue_ice" -> Blocks.BLUE_ICE.defaultBlockState();
            case "minecraft:sand" -> Blocks.SAND.defaultBlockState();
            case "minecraft:red_sand" -> Blocks.RED_SAND.defaultBlockState();
            case "minecraft:sandstone" -> Blocks.SANDSTONE.defaultBlockState();
            case "minecraft:gravel" -> Blocks.GRAVEL.defaultBlockState();
            case "minecraft:basalt" -> Blocks.BASALT.defaultBlockState();
            case "minecraft:blackstone" -> Blocks.BLACKSTONE.defaultBlockState();
            case "minecraft:cobbled_deepslate" -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case "minecraft:iron_block" -> Blocks.IRON_BLOCK.defaultBlockState();
            case "minecraft:snow_block" -> Blocks.SNOW_BLOCK.defaultBlockState();
            case "minecraft:terracotta" -> Blocks.TERRACOTTA.defaultBlockState();
            case "minecraft:smooth_basalt" -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case "minecraft:stone" -> Blocks.STONE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
