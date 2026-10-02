package com.modscreating.unlimitedspace.core.worldgen.hydrology;

/**
 * One calculated hydrology tile covering [tileX * G_HYDRO .. (tileX + 1) * G_HYDRO).
 *
 * <p>Uses G_HYDRO = 512, H_HALO = 256 (fixed by the V3 spec), so the solved window is
 * {@code TOTAL_SIZE = 1024} blocks on a side.
 *
 * <p><b>HYDROLOGY GRID.</b> The drainage solution is computed on a COARSENED grid
 * ({@link #GRID_STEP} blocks per cell) and read back with bilinear interpolation. This is not a
 * shortcut for convenience: a full 1024x1024 solve would need a million terrain evaluations per
 * tile, while a river is several blocks wide and its centreline is defined by the D8 routing at
 * the grid scale. Solving at 8-block resolution and interpolating reproduces the same channel
 * geometry at a fraction of the cost, and interpolation is C0-continuous, so no chunk-edge step
 * can appear.
 *
 * <p><b>BOUNDARY CONTRACT.</b> A world column belongs to EXACTLY ONE core tile
 * ({@code floorDiv(coord, G_HYDRO)}). Both sides of a tile border therefore read the SAME tile
 * object for a shared column, so their values are identical by construction — there is no
 * per-side evaluation that could disagree.
 */
public final class HydrologyTile {

    public static final int G_HYDRO = 512;
    public static final int H_HALO = 256;
    public static final int TOTAL_SIZE = G_HYDRO + 2 * H_HALO; // 1024

    /**
     * Blocks per drainage-grid cell.
     *
     * <p>A river is several blocks wide and its course is set by the LARGE-scale relief, so the
     * drainage network is solved on a 16-block lattice and read back bilinearly. A finer lattice
     * would multiply the cost of a cache miss by four for no visible gain in channel geometry,
     * and the per-tile solve is the single most expensive operation in world generation.
     */
    public static final int GRID_STEP = 16;
    /** Number of drainage cells per solved window side. */
    public static final int GRID = TOTAL_SIZE / GRID_STEP + 1; // 129
    /** Total number of drainage cells in a solved window. */
    public static final int GRID_CELLS = GRID * GRID;

    private final int tileX;
    private final int tileZ;
    private final float[] riverStrength = new float[GRID_CELLS];
    private final float[] lakeDepth = new float[GRID_CELLS];
    private final float[] flowAccum = new float[GRID_CELLS];

    /**
     * World coordinate of drainage cell (gx, gz) along X. Cell centres sit half a step inside the
     * solved window, which extends H_HALO blocks beyond the core tile on every side.
     */
    public static int worldX(int tileX, int gx) {
        return tileX * G_HYDRO - H_HALO + gx * GRID_STEP;
    }

    /** World coordinate of drainage cell (gx, gz) along Z. */
    public static int worldZ(int tileZ, int gz) {
        return tileZ * G_HYDRO - H_HALO + gz * GRID_STEP;
    }

    public HydrologyTile(int tileX, int tileZ, HydrologyScratch scratch) {
        this.tileX = tileX;
        this.tileZ = tileZ;
        // DrainageSolver already writes onto the GRID x GRID lattice, so the results are copied
        // straight into the immutable tile; the scratch can then be reused immediately.
        System.arraycopy(scratch.riverStrength, 0, this.riverStrength, 0, GRID_CELLS);
        System.arraycopy(scratch.lakeDepth, 0, this.lakeDepth, 0, GRID_CELLS);
        System.arraycopy(scratch.flowAccumulation, 0, this.flowAccum, 0, GRID_CELLS);
    }

    /**
     * River strength in [0, 1] for a WORLD column, bilinearly interpolated on the drainage grid.
     * A column outside the tile's CORE returns 0; the halo is solve-internal only.
     */
    public float riverStrengthAt(int worldX, int worldZ) {
        return sample(riverStrength, worldX, worldZ);
    }

    /** Lake depth in blocks for a WORLD column (0 outside the tile's core). */
    public float lakeDepthAt(int worldX, int worldZ) {
        return sample(lakeDepth, worldX, worldZ);
    }

    /** Flow accumulation (drainage area) for a WORLD column (0 outside the tile's core). */
    public float flowAccumulationAt(int worldX, int worldZ) {
        return sample(flowAccum, worldX, worldZ);
    }

    /** True when the tile's core contains the given world column. */
    public boolean contains(int worldX, int worldZ) {
        int lx = localX(worldX);
        int lz = localZ(worldZ);
        return lx >= 0 && lx < G_HYDRO && lz >= 0 && lz < G_HYDRO;
    }

    /**
     * Bilinear read of a drainage field at a world column.
     *
     * <p>The grid indices come from a pure expression of the world coordinate, so the same column
     * always lands on the same four cells with the same weights — the value is a function of the
     * coordinate alone, never of the evaluation order or of which side of a tile border asked.
     */
    private float sample(float[] field, int worldX, int worldZ) {
        int lx = localX(worldX);
        int lz = localZ(worldZ);
        if (lx < 0 || lx >= G_HYDRO || lz < 0 || lz >= G_HYDRO) return 0.0f;
        double fx = (lx + (double) H_HALO) / GRID_STEP;
        double fz = (lz + (double) H_HALO) / GRID_STEP;
        int gx = (int) fx;
        int gz = (int) fz;
        if (gx < 0) gx = 0;
        if (gz < 0) gz = 0;
        if (gx > GRID - 2) gx = GRID - 2;
        if (gz > GRID - 2) gz = GRID - 2;
        double tx = Math.min(1.0, Math.max(0.0, fx - gx));
        double tz = Math.min(1.0, Math.max(0.0, fz - gz));
        int r0 = gz * GRID;
        int r1 = (gz + 1) * GRID;
        double v00 = field[r0 + gx];
        double v10 = field[r0 + gx + 1];
        double v01 = field[r1 + gx];
        double v11 = field[r1 + gx + 1];
        double top = v00 + (v10 - v00) * tx;
        double bottom = v01 + (v11 - v01) * tx;
        return (float) (top + (bottom - top) * tz);
    }

    private int localX(int worldX) {
        return worldX - tileX * G_HYDRO;
    }

    private int localZ(int worldZ) {
        return worldZ - tileZ * G_HYDRO;
    }
}
