package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.List;

/**
 * WORLDGEN V2 — the SECONDARY geological province field (~900 blocks), nested inside the macro
 * geography.
 *
 * <h2>What changed and why</h2>
 * The old {@code ProvinceField} was a 900-block cell that drew a province per cell and then
 * applied a REGION FILTER plus a weighted vote of the four surrounding cell corners. That is
 * the same family of defect the macro rewrite removed: an integer label decided by
 * aggregation, with a hard ecological gate layered on top.
 *
 * <p>The secondary geology is now a SECOND, INDEPENDENT nearest-site Voronoi at the same
 * 900-block scale, on its own seed:
 * <ul>
 *   <li><b>Ownership is nearest-site geometry</b> — {@code province = argmin D_i} over a fixed
 *       5&times;5 candidate window, exactly like the macro geography but at the finer scale.</li>
 *   <li><b>Attributes are CONTINUOUS weights</b> — a compact-support kernel blend over the same
 *       window, so the terrain receives continuous values and can never see a step.</li>
 *   <li><b>Discrete province IDs are used only for genuinely discrete outputs</b> — which blocks
 *       spawn, which feature placer runs. Never for a terrain amplitude.</li>
 * </ul>
 *
 * <p>There is NO 192-block label-driven fallback layer any more. What survives at that scale is
 * a label-FREE continuous noise used purely as local micro-relief texture, which by
 * construction cannot flip a major category.
 *
 * <p>Pure domain: no Minecraft types. Allocation-free in the hot path, with no mutable static
 * state of any kind.
 */
public final class ProvinceField {

    /** Secondary province cell size in blocks. */
    public static final int CELL_SIZE = 900;

    /** Fixed candidate window radius for the secondary Voronoi (5&times;5 = 25 sites). */
    public static final int WINDOW_RADIUS = 2;

    /**
     * The label-FREE micro texture scale, in blocks. It carries no province identity at all —
     * only a small continuous roughness modulation — so it can never flip a major category.
     */
    public static final int MICRO_CELL = 192;

    private static final String NS = "us.geology.province.field";
    /** Bounded jitter of the secondary sites, as a fraction of the cell. */
    private static final double JITTER = 0.30;
    /** Support radius of the continuous weight kernel, as a multiple of the cell size. */
    private static final double BLEND_CELLS = 1.8;

    private ProvinceField() {}

    /** Medium-seed accessor (diagnostics / previews). */
    public static long mediumSeedOf(long planetSeed, long provinceSeed) {
        return Seeds.derive(provinceSeed, NS + ".seed", planetSeed);
    }

    /** Canonical medium-seed from the province seed alone. */
    public static long mediumSeedOf(long provinceSeed) {
        return mediumSeedOf(provinceSeed, provinceSeed);
    }

    /**
     * The continuous province WEIGHTS of a column, written into {@code out}, which must have
     * room for every entry of {@code weights}. Returns the same array for chaining.
     *
     * <p>This is the form the terrain path consumes: continuous numbers, never a label. A
     * consumer that needs "the dominant province" takes the argmax itself, and only for a
     * genuinely discrete decision.
     */
    public static double[] weightsAt(long mediumSeed, List<GeologicalProvinceSelector.Weight> weights,
                                     int x, int z, double[] out) {
        int n = weights == null ? 0 : weights.size();
        if (n == 0 || out == null || out.length < n) return out;
        java.util.Arrays.fill(out, 0, n, 0.0);
        // A SECOND, independent bounded warp so the secondary geology does not simply inherit
        // the macro borders. It is deliberately small: the secondary field must stay INSIDE the
        // macro province, not compete with it.
        double wx = x + warpOffset(mediumSeed, x, z, 0);
        double wz = z + warpOffset(mediumSeed, x, z, 1);
        int cellX = (int) Math.floor(wx / CELL_SIZE);
        int cellZ = (int) Math.floor(wz / CELL_SIZE);
        double mass = 0.0;
        for (int dz = -WINDOW_RADIUS; dz <= WINDOW_RADIUS; dz++) {
            for (int dx = -WINDOW_RADIUS; dx <= WINDOW_RADIUS; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long h = Seeds.derive2(mediumSeed, NS + ".site", cx, cz);
                double sx = (cx + 0.5 + (Seeds.fraction(h, 1) - 0.5) * 2.0 * JITTER) * CELL_SIZE;
                double sz = (cz + 0.5 + (Seeds.fraction(h, 2) - 0.5) * 2.0 * JITTER) * CELL_SIZE;
                double ddx = wx - sx;
                double ddz = wz - sz;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                // Compact-support smooth kernel: value and first three derivatives vanish at
                // the support edge, so the blended weights are continuous everywhere.
                double t = dist / (CELL_SIZE * BLEND_CELLS);
                if (t >= 1.0) continue;
                double u = 1.0 - t * t;
                double u2 = u * u;
                double w = u2 * u2 * u;
                if (w <= 0.0) continue;
                out[provinceIndex(mediumSeed, weights, cx, cz)] += w;
                mass += w;
            }
        }
        if (mass <= 1.0e-12) {
            // No coverage: a defined uniform distribution beats NaN propagation.
            double uniform = 1.0 / n;
            for (int i = 0; i < n; i++) out[i] = uniform;
            return out;
        }
        double inv = 1.0 / mass;
        for (int i = 0; i < n; i++) out[i] *= inv;
        return out;
    }
    /**
     * The DOMINANT secondary province of a column — nearest-site ownership, used ONLY for
     * genuinely discrete outputs (block selection, feature placement), never for a terrain
     * amplitude.
     */
    public static GeologicalProvince nearestProvinceAt(long mediumSeed,
                                                       List<GeologicalProvinceSelector.Weight> weights,
                                                       int x, int z) {
        int n = weights == null ? 0 : weights.size();
        if (n == 0) return GeologicalProvince.PLAINS;
        double wx = x + warpOffset(mediumSeed, x, z, 0);
        double wz = z + warpOffset(mediumSeed, x, z, 1);
        int cellX = (int) Math.floor(wx / CELL_SIZE);
        int cellZ = (int) Math.floor(wz / CELL_SIZE);
        double best = Double.MAX_VALUE;
        int bestId = 0;
        for (int dz = -WINDOW_RADIUS; dz <= WINDOW_RADIUS; dz++) {
            for (int dx = -WINDOW_RADIUS; dx <= WINDOW_RADIUS; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long h = Seeds.derive2(mediumSeed, NS + ".site", cx, cz);
                double sx = (cx + 0.5 + (Seeds.fraction(h, 1) - 0.5) * 2.0 * JITTER) * CELL_SIZE;
                double sz = (cz + 0.5 + (Seeds.fraction(h, 2) - 0.5) * 2.0 * JITTER) * CELL_SIZE;
                double ddx = wx - sx;
                double ddz = wz - sz;
                double d = ddx * ddx + ddz * ddz;
                if (d < best) {
                    best = d;
                    bestId = provinceIndex(mediumSeed, weights, cx, cz);
                }
            }
        }
        return weights.get(bestId).province();
    }

    /**
     * The LABEL-FREE micro texture in [0,1] at the 192-block scale. It carries NO province
     * identity, so it can only add local surface character and can never flip a major category.
     */
    public static double microTexture01(long mediumSeed, int x, int z) {
        int cx = Math.floorDiv(x, MICRO_CELL);
        int cz = Math.floorDiv(z, MICRO_CELL);
        double fx = (x - cx * (double) MICRO_CELL) / MICRO_CELL;
        double fz = (z - cz * (double) MICRO_CELL) / MICRO_CELL;
        double v00 = corner(mediumSeed, cx, cz);
        double v10 = corner(mediumSeed, cx + 1, cz);
        double v01 = corner(mediumSeed, cx, cz + 1);
        double v11 = corner(mediumSeed, cx + 1, cz + 1);
        double tx = smoothstep(fx);
        double tz = smoothstep(fz);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return Math.min(1.0, Math.max(0.0, a + (b - a) * tz));
    }

    /**
     * The CONTINUOUS share of one province inside a weight vector, in [0,1]. This is what a
     * consumer uses when it wants "how volcanic is this column" — a number that fades, not a
     * boolean that flips.
     */
    public static double shareOf(double[] weights,
                                 List<GeologicalProvinceSelector.Weight> table,
                                 GeologicalProvince province) {
        if (weights == null || table == null || weights.length < table.size()) return 0.0;
        double sum = 0.0;
        double chosen = 0.0;
        for (int i = 0; i < table.size(); i++) {
            sum += weights[i];
            if (table.get(i).province() == province) chosen = weights[i];
        }
        return sum <= 0.0 ? 0.0 : chosen / sum;
    }

    /** The catalog index a cell draws, by a cumulative-weight walk over the reachable set. */
    private static int provinceIndex(long mediumSeed,
                                     List<GeologicalProvinceSelector.Weight> weights,
                                     int cellX, int cellZ) {
        double total = 0.0;
        for (GeologicalProvinceSelector.Weight w : weights) total += w.weight();
        if (total <= 0.0) return 0;
        long h = Seeds.derive2(mediumSeed, NS + ".province", cellX, cellZ);
        double pick = Seeds.fraction(h, 3) * total;
        double acc = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            acc += weights.get(i).weight();
            if (pick < acc) return i;
        }
        return weights.size() - 1;
    }

    /** A small bounded domain-warp offset, in blocks, for the secondary field. */
    private static double warpOffset(long seed, int x, int z, int axis) {
        double n = valueNoise(Seeds.derive(seed, NS + ".warp" + axis), x, z, 1400.0);
        return (n - 0.5) * 260.0;
    }

    private static double valueNoise(long seed, double x, double z, double wavelength) {
        double f = 1.0 / wavelength;
        double sx = x * f;
        double sz = z * f;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0);
        double v10 = corner(seed, x0 + 1, z0);
        double v01 = corner(seed, x0, z0 + 1);
        double v11 = corner(seed, x0 + 1, z0 + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    private static double corner(long seed, int cx, int cz) {
        return Seeds.fraction(Seeds.derive2(seed, NS + ".corner", cx, cz), 4L);
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}