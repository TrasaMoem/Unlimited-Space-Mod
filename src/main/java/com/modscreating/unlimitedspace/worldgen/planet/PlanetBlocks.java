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
        return byId(material.blockId());
    }

    /**
     * R16: registry-safe block resolution. The core palette carries opaque registry keys
     * (custom, vanilla or Creating Space); this is the ONLY place that turns a key into a
     * {@link BlockState}. Unknown/missing keys (e.g. a Creating Space block absent from the
     * runtime registry) degrade to stone instead of crashing generation.
     */
    public static BlockState byId(String blockId) {
        if (blockId == null || blockId.isBlank()) return Blocks.STONE.defaultBlockState();
        if (blockId.startsWith("unlimitedspace:")) {
            String path = blockId.substring("unlimitedspace:".length());
            BlockState custom = PlanetMaterialBlocks.state(path);
            if (custom != null && !custom.isAir() && custom.getBlock() != Blocks.STONE) return custom;
            return Blocks.STONE.defaultBlockState();
        }
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(blockId);
            if (rl != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)) {
                net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
                if (block != null) return block.defaultBlockState();
            }
        } catch (Throwable ignored) {
            // never let a bad registry key break chunk generation
        }
        return Blocks.STONE.defaultBlockState();
    }
}
