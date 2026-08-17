package com.modscreating.unlimitedspace.worldgen.asteroid;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-only mapper from the stable registry block-id strings produced by the pure-domain
 * {@link com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry} to concrete
 * {@link BlockState}s. This is the ONLY place where the abstract asteroid material/ore ids
 * become Minecraft blocks.
 *
 * <p>The reserved {@code SUPER_DENSE_ICE_ID} is intentionally NOT mapped: it is future-only and
 * no fake block id is ever created here (R11 must not implement the Super Dense Ice block).
 */
public final class AsteroidBlocks {

    private AsteroidBlocks() {}

    public static BlockState fromId(String blockId) {
        if (blockId == null) return Blocks.AIR.defaultBlockState();
        return switch (blockId) {
            case "minecraft:air" -> Blocks.AIR.defaultBlockState();
            case "minecraft:stone" -> Blocks.STONE.defaultBlockState();
            case "minecraft:deepslate" -> Blocks.DEEPSLATE.defaultBlockState();
            case "minecraft:gravel" -> Blocks.GRAVEL.defaultBlockState();
            case "minecraft:obsidian" -> Blocks.OBSIDIAN.defaultBlockState();
            case "minecraft:basalt" -> Blocks.BASALT.defaultBlockState();
            case "minecraft:smooth_basalt" -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case "minecraft:packed_ice" -> Blocks.PACKED_ICE.defaultBlockState();
            case "minecraft:blue_ice" -> Blocks.BLUE_ICE.defaultBlockState();
            case "minecraft:blackstone" -> Blocks.BLACKSTONE.defaultBlockState();
            case "minecraft:iron_ore" -> Blocks.IRON_ORE.defaultBlockState();
            case "minecraft:copper_ore" -> Blocks.COPPER_ORE.defaultBlockState();
            case "minecraft:gold_ore" -> Blocks.GOLD_ORE.defaultBlockState();
            case "minecraft:coal_ore" -> Blocks.COAL_ORE.defaultBlockState();
            case "minecraft:redstone_ore" -> Blocks.REDSTONE_ORE.defaultBlockState();
            case "minecraft:lapis_ore" -> Blocks.LAPIS_ORE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
