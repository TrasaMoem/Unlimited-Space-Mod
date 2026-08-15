package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.SurfaceMaterial;
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
}