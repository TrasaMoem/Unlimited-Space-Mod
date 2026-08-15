package com.modscreating.unlimitedspace.worldgen.space.adapter;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyCoordinate;
import com.modscreating.unlimitedspace.core.galaxy.layout.SpaceConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Thin, deterministic adapter between Minecraft block/chunk coordinates and
 * GalaxyCoordinate (Phase 5).
 *
 * <p>Contract:
 * <ul>
 *   <li>Division uses floor semantics (negative coordinates must be correct).</li>
 *   <li>Round-trip (block → galaxy → block-center) must be stable within 0.5 block.</li>
 *   <li>Chunk boundaries map to the same GalaxyCoordinate for all 16×16 blocks.</li>
 * </ul>
 */
public final class BlockPosToGalaxyCoordinate {

    private BlockPosToGalaxyCoordinate() {}

    private static final double SCALE = SpaceConstants.BLOCKS_PER_GALAXY_UNIT; // 256.0

    /** Convert block coordinates to GalaxyCoordinate (floor division). */
    public static GalaxyCoordinate fromBlock(long blockX, long blockZ) {
        return GalaxyCoordinate.of(blockX / SCALE, blockZ / SCALE);
    }

    public static GalaxyCoordinate fromBlock(BlockPos pos) {
        return fromBlock(pos.getX(), pos.getZ());
    }

    /** Convert ChunkPos to GalaxyCoordinate (uses the chunk origin block). */
    public static GalaxyCoordinate fromChunk(ChunkPos chunk) {
        return fromBlock(chunk.getMinBlockX(), chunk.getMinBlockZ());
    }

    /** Convert GalaxyCoordinate back to approximate block center (for teleport/debug). */
    public static BlockPos toBlockCenter(GalaxyCoordinate c) {
        long bx = (long) Math.floor(c.x() * SCALE + SCALE * 0.5);
        long bz = (long) Math.floor(c.z() * SCALE + SCALE * 0.5);
        return new BlockPos((int) bx, 64, (int) bz);
    }
}
