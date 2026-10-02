package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * WORLDGEN V2 — the deterministic SITE LATTICE of a planet's macro geography.
 *
 * <p>One site per lattice cell of {@link #CELL_SIZE} blocks, placed at a bounded jitter offset
 * from the cell centre. The lattice lives in a frame ROTATED by a planet-seed-derived angle, so
 * the world X/Z axes never become the dominant displayed orientation of the macro geography.
 *
 * <h2>STRUCTURAL GUARANTEES (by construction, asserted by tests)</h2>
 * <ol>
 *   <li><b>Deterministic site generation</b> — a site's position is a pure function of
 *       {@code (planetSeed, cellX, cellZ)}; no RNG, no ordering, no shared mutable state.</li>
 *   <li><b>One site per lattice cell</b> — the table has exactly one entry per cell, so no
 *       cell can own two sites and the site count equals the cell count.</li>
 *   <li><b>Finite candidate neighborhood</b> — placement consults exactly
 *       {@code (2*CANDIDATE_RADIUS+1)^2 - 1} neighbouring cells, i.e. O(1) in the cell count.
 *       The lattice is expanded ONCE at construction, so the per-column cost is one table
 *       read.</li>
 *   <li><b>Bounded jitter</b> — a site never moves more than {@link #JITTER_SPAN} cells from
 *       its own centre. This is the fact that makes the fixed 7&times;7 candidate window
 *       provably sufficient (see {@link MacroGeography}).</li>
 * </ol>
 *
 * <h2>STATISTICAL TARGETS (measured, NOT guaranteed)</h2>
 * The placement below is a <b>deterministic anti-regularity heuristic</b>. It is explicitly
 * <b>not</b> Poisson-disk sampling, <b>not</b> blue noise and <b>not</b> a Mitchell
 * best-candidate generator. Spacing distribution, angular distribution, area CV and orientation
 * entropy are REPORTED by {@link GeographyMetrics} as measured properties of a given seed, and
 * they legitimately differ between seeds. No theorem here claims a "[0.3C, 1.7C] guarantee":
 * the only spacing facts that hold by construction are the bounded-jitter cap and "a site is
 * within one cell width of its own cell centre".
 *
 * <p>Immutable. Pure domain: no Minecraft types.
 */
public final class MacroSiteLattice {

    /** Base lattice cell size, in blocks. */
    public static final int CELL_SIZE = 2400;

    /**
     * Jitter band of the cell centre, in cell fractions. A site lies inside
     * {@code [0.5 - JITTER_SPAN, 0.5 + JITTER_SPAN]} of its cell on each axis, so its maximum
     * displacement from the cell centre is {@code JITTER_SPAN * CELL_SIZE}.
     *
     * <p>Bounded jitter is a STRUCTURAL guarantee, and it is what makes the fixed candidate
     * window correct: the worst-case distance from a query point to the nearest site is bounded
     * by {@code sqrt(2) * (0.5 + JITTER_SPAN) * CELL}, which for {@code JITTER_SPAN = 0.35} is
     * about {@code 1.20 * CELL} — far inside the {@code (3 - 0.35) * CELL} reach of the
     * 7&times;7 window.
     */
    public static final double JITTER_SPAN = 0.35;

    /** Radius (in cells) of the finite candidate neighborhood used by the placement heuristic. */
    public static final int CANDIDATE_RADIUS = 1;

    /** Number of placement candidates evaluated per cell by the anti-regularity heuristic. */
    public static final int CANDIDATES_PER_CELL = 4;

    /** Side of the precomputed site table in cells (a power of two, so folding is a mask). */
    public static final int TABLE_SIDE = 128;
    /** Bit mask that folds a cell coordinate into the table. */
    public static final int TABLE_MASK = TABLE_SIDE - 1;
    /** The documented periodicity of the folded table, in blocks. */
    public static final long TABLE_PERIOD_BLOCKS = (long) TABLE_SIDE * CELL_SIZE;
    /** Number of cells in the table. */
    public static final int TABLE_CELLS = TABLE_SIDE * TABLE_SIDE;

    private static final String NS = "us.geography.lattice";

    private final long seed;
    private final double cos;
    private final double sin;
    /** The bounded X jitter of each folded cell, in cell fractions. */
    private final double[] jitterX;
    /** The bounded Z jitter of each folded cell, in cell fractions. */
    private final double[] jitterZ;

    private MacroSiteLattice(long seed, double cos, double sin) {
        this.seed = seed;
        this.cos = cos;
        this.sin = sin;
        this.jitterX = new double[TABLE_CELLS];
        this.jitterZ = new double[TABLE_CELLS];
        expand();
    }

    /** Canonical factory: the planet's fully expanded site lattice. */
    public static MacroSiteLattice of(long planetSeed) {
        long s = Seeds.derive(planetSeed, NS);
        long angleHash = Seeds.derive(s, NS + ".rotation");
        double angle = Seeds.fraction(angleHash, 0) * (Math.PI * 2.0);
        return new MacroSiteLattice(s, Math.cos(angle), Math.sin(angle));
    }

    /**
     * The lattice rotation angle in radians, in {@code [0, 2pi)}.
     *
     * <p>Rotation exists so the world X/Z axes are not the dominant displayed orientation. It
     * does NOT create organic geometry by itself: that comes from {@link BoundedWarp}.
     * Orientation entropy is measured after generation ({@link GeographyMetrics}).
     */
    public double rotation() {
        return Math.atan2(sin, cos);
    }

    /** The lattice seed. */
    public long seed() {
        return seed;
    }

    /** The documented periodicity of the folded site table, in blocks. */
    public long periodBlocks() {
        return TABLE_PERIOD_BLOCKS;
    }

    /** The fold index of a cell in the precomputed table. */
    private static int index(int cellX, int cellZ) {
        return (cellX & TABLE_MASK) * TABLE_SIDE + (cellZ & TABLE_MASK);
    }

    /**
     * The site X of a cell in LATTICE blocks.
     *
     * <p>The table stores only the bounded JITTER of each cell, never an absolute position.
     * That is what makes the bit-fold correct: the jitter is genuinely periodic, while the
     * position is recomputed from the REQUESTED cell, so a negative cell coordinate returns
     * the site of that negative cell and not of its folded positive twin.
     */
    public double siteX(int cellX, int cellZ) {
        return (cellX + 0.5 + jitterX[index(cellX, cellZ)]) * CELL_SIZE;
    }

    /** The site Z of a cell in LATTICE blocks. See {@link #siteX}. */
    public double siteZ(int cellX, int cellZ) {
        return (cellZ + 0.5 + jitterZ[index(cellX, cellZ)]) * CELL_SIZE;
    }

    // ---------------------------------------------------------------- expansion

    /**
     * A fast, allocation-free 64-bit cell hash: a SplitMix64-style avalanche specialised for
     * two integer cell coordinates. Pure — determinism does not depend on {@code Seeds}.
     */
    private static long cellHash(long seed, int cx, int cz) {
        long h = seed + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 32;
        return h;
    }

    /** Map a hash to [0,1) using 53 mantissa bits. */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** One jitter draw in cell fractions, bounded by {@code +/- JITTER_SPAN}. */
    private static double jitterOf(long h, int slot) {
        long s = h * 0x2545F4914F6CDD1DL + slot * 0x9E3779B97F4A7C15L;
        s ^= s >>> 31;
        s *= 0xD6E8FEB86659FD93L;
        s ^= s >>> 32;
        return (unit(s) - 0.5) * (2.0 * JITTER_SPAN);
    }
    /**
     * The ANTI-REGULARITY placement heuristic.
     *
     * <p>For each cell this evaluates {@link #CANDIDATES_PER_CELL} candidate offsets drawn from
     * the cell hash and keeps the one that maximises the minimum distance to the
     * {@link #CANDIDATE_RADIUS} neighbourhood of <em>base</em> (non-optimised) neighbour
     * positions.
     *
     * <p>Using the neighbours' BASE positions is what makes the rule order-independent: no
     * cell depends on the choice another cell has already made, so the whole lattice is a pure
     * function of the seed and can be evaluated in any order, on any thread, in any JVM.
     *
     * <p>This is deliberately a heuristic and explicitly NOT Poisson-disk / blue-noise
     * sampling. It has no provable minimum-spacing theorem; see the class javadoc.
     */
    private void expand() {
        for (int cx = 0; cx < TABLE_SIDE; cx++) {
            for (int cz = 0; cz < TABLE_SIDE; cz++) {
                long h = cellHash(seed, cx, cz);
                int bestK = 0;
                double bestScore = -1.0;
                for (int k = 0; k < CANDIDATES_PER_CELL; k++) {
                    double px = (cx + 0.5 + jitterOf(h, k * 2)) * CELL_SIZE;
                    double pz = (cz + 0.5 + jitterOf(h, k * 2 + 1)) * CELL_SIZE;
                    double nearest = Double.MAX_VALUE;
                    for (int dz = -CANDIDATE_RADIUS; dz <= CANDIDATE_RADIUS; dz++) {
                        for (int dx = -CANDIDATE_RADIUS; dx <= CANDIDATE_RADIUS; dx++) {
                            if (dx == 0 && dz == 0) continue;
                            int nx = (cx + dx) & TABLE_MASK;
                            int nz = (cz + dz) & TABLE_MASK;
                            long nh = cellHash(seed, nx, nz);
                            double qx = (nx + 0.5 + jitterOf(nh, 0)) * CELL_SIZE;
                            double qz = (nz + 0.5 + jitterOf(nh, 1)) * CELL_SIZE;
                            double ddx = px - qx;
                            double ddz = pz - qz;
                            double d2 = ddx * ddx + ddz * ddz;
                            if (d2 < nearest) nearest = d2;
                        }
                    }
                    // STRICT '>' keeps the tie order deterministic (lowest candidate wins).
                    if (nearest > bestScore) {
                        bestScore = nearest;
                        bestK = k;
                    }
                }
                int idx = cx * TABLE_SIDE + cz;
                // Store the JITTER, not the absolute position: see siteX(int,int).
                jitterX[idx] = jitterOf(h, bestK * 2);
                jitterZ[idx] = jitterOf(h, bestK * 2 + 1);
            }
        }
    }
}