package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * Mountain range network (R21).
 *
 * <p>Mountains are a MATHEMATICAL FIELD, not a pile of objects. The field produces:
 *
 * <pre>
 * belt corridor   (wavelength ~2000 blocks, domain-warped)  -&gt; wideEnv  = outer mountain belt
 * range corridor  (wavelength ~950 blocks,  domain-warped)  -&gt; coreEnv  = inner range
 * crest           (ridged, wavelength ~430 blocks)          -&gt; peak structure
 * valley corridor (wavelength ~1100 blocks)                 -&gt; inter-range valleys
 * </pre>
 *
 * <p>{@code mountainCoverage} (a PLANET parameter) shifts the belt threshold: 0.0 removes
 * mountains entirely, 1.0 turns the planet into a dominant mountain world. Biome regions
 * modulate the coverage LOCALLY through the {@code coverage} argument.
 *
 * <p>All functions are continuous, deterministic and allocation-free (hot-path safe).
 */
final class MountainRangeField {

    /** Mountain-system cell size (blocks): one system is 500–3000 blocks across. */
    private static final int CELL = 1200;
    /**
     * Blend radius of the cell field. It is far smaller than the distance to any cell centre
     * excluded by the 5×5 window, so the field is continuous everywhere.
     */
    private static final double BLEND_RADIUS = CELL * 1.05;

    private MountainRangeField() {}

    /**
     * Inner mountain-range envelope in [0,1]: 1 inside the ranges, 0 on the open plains.
     *
     * <p>Coverage is a PLANET parameter and it maps ALMOST LINEARLY onto the measured mountain
     * share: the envelope is the smoothed mountain-cell field compared against a threshold
     * derived from the target share (probit of the target via a logistic approximation). This
     * is stable for every planet seed — a raw low-frequency noise threshold swung between 2%
     * and 90% for the same relief archetype.
     */
    static double rangeEnvelope(long seed, double coverage, double widthMul, int x, int z) {
        double target = targetShare(coverage);
        if (target <= 0.001) return 0.0;
        double threshold = 0.5 - NORMAL_SD * probit(target);
        return clamp01((cellField(seed, x, z) - threshold) / 0.12);
    }

    /**
     * ACT V3.1: BOTH envelopes from ONE smoothed cell field.
     *
     * <p>{@link #rangeEnvelope} and {@link #foothillBand} used to call {@link #cellField} with
     * IDENTICAL arguments, so the 25-cell distance-weighted average was computed twice per query
     * and twice more inside the mountain system: 100 lattice reads per column where 25 suffice.
     * The two differ only in their threshold and their softening, so they can share one evaluation.
     *
     * <p>The result is bit-identical to calling the two methods separately.
     *
     * @param out a two-element scratch: {@code out[0]} is the range envelope, {@code out[1]} the
     *            foothill band. Caller-owned so the hot path allocates nothing.
     */
    static void envelopes(long seed, double coverage, int x, int z, double[] out) {
        double cell = cellField(seed, x, z);
        double target = targetShare(coverage);
        double range = 0.0;
        if (target > 0.001) {
            range = clamp01((cell - (0.5 - NORMAL_SD * probit(target))) / 0.12);
        }
        double footTarget = Math.min(0.95, target * 1.9);
        double foot = 0.0;
        if (footTarget > 0.002) {
            foot = clamp01((cell - (0.5 - NORMAL_SD * probit(footTarget))) / 0.16);
        }
        out[0] = range;
        out[1] = foot;
    }

    /**
     * Outer (wider) system envelope in [0,1]. The difference
     * {@code foothillBand - rangeEnvelope} is the FOOTHILLS: a wide transition zone between
     * plains and mountains — never a wall.
     */
    static double foothillBand(long seed, double coverage, double widthMul, int x, int z) {
        double target = Math.min(0.95, targetShare(coverage) * 1.9);
        if (target <= 0.002) return 0.0;
        double threshold = 0.5 - NORMAL_SD * probit(target);
        return clamp01((cellField(seed, x, z) - threshold) / 0.16);
    }

    /** Target share of the planet inside mountain systems for a given coverage. */
    private static double targetShare(double coverage) {
        return clamp01((clamp01(coverage) - 0.15) * 0.90);
    }

    /** SD of the smoothed cell field (weighted mean of ~25 iid uniform cell values). */
    private static final double NORMAL_SD = 0.115;

    /** Logistic approximation of the normal quantile (probit). */
    private static double probit(double p) {
        double q = Math.max(1.0e-4, Math.min(1.0 - 1.0e-4, p));
        return Math.log(q / (1.0 - q)) / 1.70;
    }

    /**
     * Smoothed mountain-cell field in [0,1]: each cell of {@code CELL} blocks gets an iid
     * value, and a column reads the distance-weighted average over the 5×5 neighbourhood.
     * The blend radius is smaller than the distance to any centre outside the window, so the
     * field is continuous everywhere; the average of ~25 iid values gives a stable
     * distribution (law of large numbers) for every planet seed.
     */
    private static double cellField(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        double sum = 0.0, wsum = 0.0;
        double inv = 1.0 / BLEND_RADIUS;
        for (int dx = -2; dx <= 2; dx++) {
            double ox = x - (cx + dx + 0.5) * (double) CELL;
            for (int dz = -2; dz <= 2; dz++) {
                double oz = z - (cz + dz + 0.5) * (double) CELL;
                double w = 1.0 - Math.sqrt(ox * ox + oz * oz) * inv;
                if (w <= 1.0e-9) continue;
                sum += w * cellValue(seed, cx + dx, cz + dz);
                wsum += w;
            }
        }
        return wsum <= 1.0e-9 ? 0.5 : sum / wsum;
    }

    /**
     * ACT V3.1: the cell namespace fold is a CONSTANT. {@link #cellField} reads 25 cells and both
     * {@link #rangeEnvelope} and {@link #foothillBand} read it, so 50 string folds ran per
     * generated column. The mix order is unchanged, so the field stays bit-identical.
     */
    private static final long NS_CELL =
            com.modscreating.unlimitedspace.core.seed.Seeds.hash("us.terrain.mountaincell");

    private static double cellValue(long seed, int cx, int cz) {
        long h = com.modscreating.unlimitedspace.core.seed.Seeds
                .derive2(seed, NS_CELL, cx, cz);
        return com.modscreating.unlimitedspace.core.seed.Seeds.fraction(h, 0);
    }

    /**
     * Ridged crest structure in [0,1]: long connected chains inside the belts. Peak
     * modulation stays MODERATE (no spike noise) — the compositor shapes the profile.
     */
    static double crest(long seed, double widthMul, int x, int z) {
        return GlobalTerrainFields.ridgeField(seed + 0x9L, x, z,
                1.0 / (430.0 * widthMul), 190.0);
    }

    /**
     * Valley corridor field in [0,1]: wide smooth corridors BETWEEN the range systems.
     * High value = deep valley corridor. Never a random hole: corridors are continuous,
     * stretched along the terrain and connect to the lowlands.
     */
    static double valley(long seed, double widthMul, int x, int z) {
        double v = GlobalTerrainFields.ridgeField(seed + 0xCL, x, z,
                1.0 / (1100.0 * widthMul), 340.0);
        // Anti-correlate with the belt: valleys live between the corridors.
        return clamp01(1.0 - v);
    }

    /** Belt field: domain-warped ridge corridors in [0,1] (high along the chains). */
    private static double beltField(long seed, int x, int z, double frequency, double warpBlocks) {
        return GlobalTerrainFields.ridgeField(seed, x, z, frequency, warpBlocks);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
