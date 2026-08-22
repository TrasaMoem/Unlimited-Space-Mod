package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.core.worldgen.StarSurfaceMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-only mapper from the abstract {@link StarSurfaceMaterial} to concrete {@link BlockState}s
 * (R14.9). This is the only place the core star-worldgen material becomes a Minecraft block, exactly
 * mirroring {@code PlanetBlocks} for planets.
 *
 * <p>The palette is chosen so the surface reads as plasma / fire / molten / energy and stays visually
 * coherent with the star's orbital colour (red dwarf → deep red magma, hot blue → blue sea-lantern
 * glow, giant → bright glowstone, black hole → dark obsidian stand-in).
 */
public final class StarBlocks {

    private StarBlocks() {
    }

    public static BlockState surface(StarSurfaceMaterial material) {
        if (material == null) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return switch (material) {
            case RED_MOLTEN -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case MOLTEN -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case BRIGHT_MOLTEN -> Blocks.GLOWSTONE.defaultBlockState();
            case HIGH_ENERGY -> Blocks.SEA_LANTERN.defaultBlockState();
            case INTENSE -> Blocks.GLOWSTONE.defaultBlockState();
            case ACCRETION_DARK -> Blocks.OBSIDIAN.defaultBlockState();
            case SUPERNOVA_SHELL -> Blocks.GLOWSTONE.defaultBlockState();
        };
    }

    public static BlockState subsurface(StarSurfaceMaterial material) {
        if (material == null) return Blocks.BASALT.defaultBlockState();
        return switch (material) {
            case RED_MOLTEN -> Blocks.NETHERRACK.defaultBlockState();
            case MOLTEN -> Blocks.BASALT.defaultBlockState();
            case BRIGHT_MOLTEN -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case HIGH_ENERGY -> Blocks.DEEPSLATE.defaultBlockState();
            case INTENSE -> Blocks.SEA_LANTERN.defaultBlockState();
            case ACCRETION_DARK -> Blocks.OBSIDIAN.defaultBlockState();
            case SUPERNOVA_SHELL -> Blocks.MAGMA_BLOCK.defaultBlockState();
        };
    }
}
