package com.modscreating.unlimitedspace.client.graphics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/**
 * Resolves a planet material's {@code blockId} registry key to the canonical vanilla
 * {@link MapColor} colour (R14.7). This is the single authoritative colour source for the orbital
 * visual palette: it reads the block's real {@code MapColor.col}, so an ice planet built from
 * {@code packed_ice}/{@code blue_ice} paints cyan/white, a rocky planet from {@code stone} paints
 * grey, a sandy planet from {@code sand}/{@code red_sand} paints tan/orange, etc. — matching the
 * blocks the world actually generates.
 *
 * <p>Client-side only (walks the block registry, never runs on a dedicated server). Every lookup is
 * guarded: a missing/bad block falls back to a neutral stone grey rather than crashing render.
 */
public final class BlockColorResolver {

    /** Fallback for an unknown block id — mid stone grey, never a crash. */
    private static final int FALLBACK = 0xFF8A8A8A;

    private BlockColorResolver() {
    }

    /**
     * @param blockId canonical registry key, e.g. {@code "minecraft:packed_ice"}
     * @return opaque ARGB ({@code 0xFFRRGGBB}) of the block's vanilla map colour
     */
    public static int argb(String blockId) {
        if (blockId == null || blockId.isEmpty()) return FALLBACK;
        try {
            ResourceLocation rl = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block == null) return FALLBACK;
            MapColor mapColor = block.defaultMapColor();
            if (mapColor == null) return FALLBACK;
            return 0xFF000000 | (mapColor.col & 0xFFFFFF);
        } catch (Throwable t) {
            return FALLBACK;
        }
    }
}
