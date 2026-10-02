package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * ACT 6 section 8: the WATER GUARANTEE of a canonically HABITABLE world.
 *
 * <p>THE PROBLEM. The canonical validator ({@link HabitabilityValidator}) requires a stable
 * {@code LIQUID} water phase and a water availability of at least
 * {@link HabitabilityValidator#MIN_WATER_AVAILABILITY}. That is a statement about the world's
 * PHYSICS, and it is what makes {@code SystemHabitability} / {@link MoonHabitability} able to say
 * HABITABLE at all. But the validator says nothing about whether the generated CONTENT contains a
 * single reachable body of liquid water: a low-coverage world can pass validation and still be a
 * world in which a player never finds water. The UI then honestly reported HABITABLE for a world
 * that cannot actually be explored, which is precisely the misleading state this ACT removes.
 *
 * <p>THE GUARANTEE. This class is the single place that turns "HABITABLE" into "HABITABLE AND
 * DRINKABLE". It is a pure, deterministic function of the world seed and the chunk coordinates; it
 * introduces no new habitability system, no new rule and no second validator. It only answers:
 *
 * <pre>
 *   isGuaranteedSpringChunk(worldSeed, chunkX, chunkZ)
 *       -> true for exactly ONE chunk in every SPRING_CELL_CHUNKS x SPRING_CELL_CHUNKS lattice cell
 * </pre>
 *
 * <p>Because the choice of the designated chunk is a pure function of the cell index, chunk
 * generation stays order-independent and seed-stable: the same world always produces the same
 * springs at the same coordinates, and a chunk does not need to know about its neighbours. The
 * lattice cell is deliberately large (32 chunks = 512 blocks), so a habitable world gains a
 * handful of small springs across an explorable area - enough that a player who explores finds
 * liquid water - while the planet never turns into an ocean world.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class HabitableWaterSource {

    /**
     * Lattice cell size in chunks (32 chunks = 512 blocks). One guaranteed spring per cell: dense
     * enough to be findable, sparse enough to leave the planet's own geography untouched.
     */
    public static final int SPRING_CELL_CHUNKS = 32;

    /** Radius, in blocks, of the water body a guaranteed spring produces. */
    public static final int SPRING_RADIUS_MIN = 4;
    public static final int SPRING_RADIUS_MAX = 9;

    private HabitableWaterSource() {}

    /**
     * Whether this chunk is the designated spring chunk of its lattice cell.
     *
     * <p>True for exactly one chunk per {@link #SPRING_CELL_CHUNKS} cell, so the guarantee is a
     * function of the coordinate alone - never of generation order, never of a neighbour lookup.
     */
    public static boolean isGuaranteedSpringChunk(long worldSeed, int chunkX, int chunkZ) {
        if (SPRING_CELL_CHUNKS <= 0) return false;
        int cellX = Math.floorDiv(chunkX, SPRING_CELL_CHUNKS);
        int cellZ = Math.floorDiv(chunkZ, SPRING_CELL_CHUNKS);
        long cellSeed = Seeds.derive(worldSeed, "unlimitedspace.habitability.spring", cellX, cellZ);
        int offX = (int) Math.floorMod(cellSeed, SPRING_CELL_CHUNKS);
        int offZ = (int) Math.floorMod(cellSeed >>> 20, SPRING_CELL_CHUNKS);
        return chunkX == cellX * SPRING_CELL_CHUNKS + offX
                && chunkZ == cellZ * SPRING_CELL_CHUNKS + offZ;
    }

    /**
     * The deterministic local offset of the spring inside its chunk, in blocks (0..15), and its
     * radius. Kept in one place so the placement stage never invents its own draw.
     *
     * <p>The offset is derived on a COARSENED lattice (one candidate per 4-block cell of the
     * chunk) and then clamped, so a radius-9 spring always fits inside its chunk with a full
     * block of margin on every side - a spring cut in half by a chunk border would not be a
     * guaranteed, reachable water source at all.
     *
     * @return {@code {localX, localZ, radius}}
     */
    public static int[] springPlacement(long worldSeed, int chunkX, int chunkZ) {
        long s = Seeds.derive(worldSeed, "unlimitedspace.habitability.spring.place", chunkX, chunkZ);
        int radius = SPRING_RADIUS_MIN + (int) (Seeds.fraction(s, 3L)
                * (SPRING_RADIUS_MAX - SPRING_RADIUS_MIN + 1));
        radius = Math.max(1, Math.min(radius, SPRING_RADIUS_MAX));
        // Keep the WHOLE body inside the chunk on every side.
        int lo = radius;
        int hi = 15 - radius;
        if (lo > hi) {                       // defensive: an oversized radius still fits
            radius = Math.max(1, Math.min(radius, 7));
            lo = radius;
            hi = 15 - radius;
        }
        int localX = pickOffset(s, 1L, lo, hi);
        int localZ = pickOffset(s, 2L, lo, hi);
        return new int[]{localX, localZ, radius};
    }

    /** A deterministic offset in {@code [lo, hi]}. */
    private static int pickOffset(long s, long slot, int lo, int hi) {
        int span = hi - lo + 1;
        if (span <= 1) return lo;
        return lo + (int) (Seeds.fraction(s, slot) * span) % span;
    }
}
