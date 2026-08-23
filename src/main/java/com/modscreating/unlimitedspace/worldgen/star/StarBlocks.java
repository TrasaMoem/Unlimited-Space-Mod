package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.core.worldgen.StarSurfaceMaterial;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R14.9.3-C — Minecraft mapping from the abstract {@link StarSurfaceMaterial} to a representative CUSTOM
 * plasma block of the {@link StarSurfaceBlockFamily} (no longer vanilla glowstone / sea-lantern / magma).
 *
 * <p>This is a fallback / per-material seam: the terrain generator itself now uses the full coherent
 * composition ({@link StarSurfaceComposer} + {@link StarPlasmaBlocks}) over the star's own 8-block family.
 * These methods just map the old material label to one representative registered custom block.
 */
public final class StarBlocks {

    private StarBlocks() {
    }

    public static BlockState surface(StarSurfaceMaterial material) {
        if (material == null) return StarPlasmaBlocks.state("dark_red_plasma");
        return switch (material) {
            case RED_MOLTEN -> StarPlasmaBlocks.state("crimson_plasma");
            case MOLTEN -> StarPlasmaBlocks.state("red_plasma");
            case BRIGHT_MOLTEN -> StarPlasmaBlocks.state("scarlet_plasma");
            case HIGH_ENERGY -> StarPlasmaBlocks.state("magenta_plasma");
            case INTENSE -> StarPlasmaBlocks.state("vermilion_plasma");
            case ACCRETION_DARK -> StarPlasmaBlocks.state("dark_red_plasma");
            case SUPERNOVA_SHELL -> StarPlasmaBlocks.state("crimson_plasma");
        };
    }

    public static BlockState subsurface(StarSurfaceMaterial material) {
        if (material == null) return StarPlasmaBlocks.state("dark_red_plasma");
        return switch (material) {
            case RED_MOLTEN -> StarPlasmaBlocks.state("blood_plasma");
            case MOLTEN -> StarPlasmaBlocks.state("ruby_plasma");
            case BRIGHT_MOLTEN -> StarPlasmaBlocks.state("crimson_plasma");
            case HIGH_ENERGY -> StarPlasmaBlocks.state("scarlet_plasma");
            case INTENSE -> StarPlasmaBlocks.state("vermilion_plasma");
            case ACCRETION_DARK -> StarPlasmaBlocks.state("dark_red_plasma");
            case SUPERNOVA_SHELL -> StarPlasmaBlocks.state("ruby_plasma");
        };
    }
}
