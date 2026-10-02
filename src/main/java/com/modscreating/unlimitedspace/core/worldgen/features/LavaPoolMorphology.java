package com.modscreating.unlimitedspace.core.worldgen.features;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * ACT 6 section 4 - the LAVA / BRINE POOL MORPHOLOGY SYSTEM.
 *
 * <p>WHAT WAS ACTUALLY WRONG in production: {@code PlanetFeaturePlacer.placePool} was hard-wired to
 * a fixed {@code dx/dz in 0..1} 2x2 block, at depth 1, with a hardcoded 6-cell rim. Every pool on
 * every world was therefore literally the same four blocks, which is why lava read as a randomly
 * stamped square texture rather than as a body of liquid with a shape.
 *
 * <p>THE SYSTEM: a pool is a DETERMINISTIC, PURE-DOMAIN shape. This class owns no Minecraft type and
 * performs no world access - the worldgen adapter only asks it a few questions per column:
 *
 * <pre>
 *   family()    : which of the &gt;= 15 shape families this pool is
 *   depthAt(dx,dz) : liquid depth at a local offset, 0 = outside the pool
 *   isInside    : whether the column carries liquid at all
 * </pre>
 *
 * <p>Each family has a genuinely different GEOMETRY (not 15 variations of one 2x2): compact, oval,
 * elongated X/Z, irregular blob, stepped blob, shallow/deep/asymmetric basin, crater-like, split
 * basin, branching, oxbow, lobed, narrow gorge, tiny-wide and large-soft. Sizes, orientation,
 * asymmetry, depth and edge irregularity all vary deterministically from the seed, so two pools of
 * the same family on the same world still differ.
 *
 * <p>Edge treatment: every family modulates its mask with a smooth, seeded edge-irregularity term,
 * so the outline is never a clean rectangle or a perfect circle. There is no per-block random noise
 * anywhere: the whole shape is a closed-form function of {@code (seed, dx, dz)}.
 *
 * <p>Pure domain: no Minecraft types. Deterministic pure functions of their inputs.
 */
public final class LavaPoolMorphology {

    // ------------------------------------------------------------------ size classes (blocks)

    /** Smallest pool class: a modest puddle. */
    public static final int SIZE_SMALL_MIN = 2;
    public static final int SIZE_MEDIUM_MIN = 5;
    /** Largest pool class radius: a real lake. */
    public static final int SIZE_LARGE_MAX = 15;

    /** Hard ceiling on the pool's bounding radius, so placement stays inside a safe chunk margin. */
    public static final int MAX_RADIUS = SIZE_LARGE_MAX;

    /**
     * ACT 6: the shape families. Seventeen genuinely different geometries; the deterministic shape
     * draw picks one per pool seed. The order is irrelevant to the result (the seed decides), the
     * COUNT is part of the production contract.
     */
    public enum Family {
        /** Small, roughly circular blob. */
        COMPACT,
        /** Elliptical, with an independent width/length ratio. */
        OVAL,
        /** Stretched along X. */
        ELONGATED_X,
        /** Stretched along Z. */
        ELONGATED_Z,
        /** Blob with a strong, seeded multi-lobe edge. */
        IRREGULAR_BLOB,
        /** Blob whose edge is stepped (lobed terraces, like a cooled crust). */
        STEPPED_BLOB,
        /** Wide, shallow dish. */
        SHALLOW_BASIN,
        /** Steep-sided deep pit. */
        DEEP_BASIN,
        /** Basin whose centre is offset from its outline (tilted dish). */
        ASYMMETRIC_BASIN,
        /** Ring / caldera-like shape with a hollow-ish interior. */
        CRATER_LIKE,
        /** Two overlapping lobes with a narrow isthmus. */
        SPLIT_BASIN,
        /** A main lobe with a smaller satellite lobe. */
        BRANCHING,
        /** A long, smoothly curved channel that doubles back (oxbow). */
        OXBOW,
        /** Multi-lobed clover shape. */
        LOBED,
        /** A narrow, deep, elongated gorge. */
        NARROW_GORGE,
        /** Very small, wide and flat. */
        TINY_WIDE,
        /** Very large, soft-edged sheet. */
        LARGE_SOFT
    }

    /** The total number of distinct shape families (production contract: at least 15). */
    public static final int FAMILY_COUNT = Family.values().length;

    private final Family family;
    private final double radiusX;
    private final double radiusZ;
    private final double rotation;
    private final double asymmetry;
    private final int maxDepth;
    private final double edgeSeedA;
    private final double edgeSeedB;
    private final double edgeScaleX;
    private final double edgeScaleZ;
    private final double lobePhase;
    private final double edgeIrregularity;

    private LavaPoolMorphology(Family family, double radiusX, double radiusZ, double rotation,
                               double asymmetry, int maxDepth, double edgeSeedA, double edgeSeedB,
                               double edgeScaleX, double edgeScaleZ, double lobePhase,
                               double edgeIrregularity) {
        this.family = family;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.rotation = rotation;
        this.asymmetry = asymmetry;
        this.maxDepth = maxDepth;
        this.edgeSeedA = edgeSeedA;
        this.edgeSeedB = edgeSeedB;
        this.edgeScaleX = edgeScaleX;
        this.edgeScaleZ = edgeScaleZ;
        this.lobePhase = lobePhase;
        this.edgeIrregularity = edgeIrregularity;
    }

    /** The shape family of this pool. */
    public Family family() {
        return family;
    }

    /** Stable identifier used by diagnostics and tests (e.g. {@code "ELONGATED_X"}). */
    public String id() {
        return family.name();
    }

    /** Longest bounding radius in blocks (used for the placement bounds check). */
    public double maxRadius() {
        // PROVEN bound. The liquid mask is non-empty only while the normalized distance is below
        // 1 + 0.30 (edge wave) + 0.19 (asymmetry) = 1.49, and an ellipse of normalized distance D
        // satisfies |lx| < D*radiusX and |lz| < D*radiusZ. Rotation preserves the norm, so the
        // whole pool is inside a box of half-extent 1.49*sqrt(2)*max(radiusX, radiusZ) = 2.11*max.
        // A small margin on top makes this the value the placement uses for its BOUNDS check, so
        // a pool can never be clipped by a chunk edge.
        return Math.max(radiusX, radiusZ) * 2.15;
    }

    /** Maximum liquid depth of this pool, in blocks. Always at least 1. */
    public int maxDepth() {
        return maxDepth;
    }

    /** Deterministic pool shape for a seed. The same seed always gives an identical shape. */
    public static LavaPoolMorphology of(long seed) {
        Family[] families = Family.values();
        // Draw the family from a derived seed slot so the choice is stable and independent of the
        // parameter draws below (a family never depends on its own size roll).
        Family family = families[(int) (Seeds.fraction(
                Seeds.derive(seed, "us.lava.morphology.family"), 0L) * families.length)
                % families.length];
        long s = Seeds.derive(seed, "us.lava.morphology");

        double sizeRoll = Seeds.fraction(s, 1L);
        double[] radii = sizeRadii(family, sizeRoll);

        double rotation = Seeds.fraction(s, 2L) * Math.PI * 2.0;
        double asym = Seeds.rangeDouble(s, 3L, -0.35, 0.35);
        int depth = depthFor(family, Seeds.fraction(s, 4L));

        double edgeA = Seeds.fraction(s, 5L) * 6.2831853;
        double edgeB = Seeds.fraction(s, 6L) * 6.2831853;
        double edgeSx = 1.0 / (2.5 + 3.5 * Seeds.fraction(s, 7L));
        double edgeSz = 1.0 / (2.5 + 3.5 * Seeds.fraction(s, 8L));
        double lobePhase = Seeds.fraction(s, 9L) * 6.2831853;
        // Capped at 0.1875 so that irregularity * 1.6 <= 0.30, the clamp applied in shapeAt.
        double irregular = 0.10 + 0.0875 * Seeds.fraction(s, 10L);

        return new LavaPoolMorphology(family, radii[0], radii[1], rotation, asym, depth,
                edgeA, edgeB, edgeSx, edgeSz, lobePhase, irregular);
    }

    // ------------------------------------------------------------------ family geometry

    /**
     * The (radiusX, radiusZ) of a family for a given size roll. Each family has its OWN aspect
     * behaviour, so no two families produce the same outline: circular blobs, ellipses, X/Z
     * elongation, long thin gorges, wide sheets.
     */
    private static double[] sizeRadii(Family family, double sizeRoll) {
        double r = sizeBase(family, sizeRoll);
        return switch (family) {
            case COMPACT -> new double[]{r * 0.98, r * 1.00};
            case OVAL -> new double[]{r * 1.18, r * 0.82};
            case ELONGATED_X -> new double[]{r * 1.85, r * 0.55};
            case ELONGATED_Z -> new double[]{r * 0.55, r * 1.85};
            case IRREGULAR_BLOB -> new double[]{r * 1.10, r * 0.92};
            case STEPPED_BLOB -> new double[]{r * 1.05, r * 0.95};
            case SHALLOW_BASIN -> new double[]{r * 1.30, r * 1.12};
            case DEEP_BASIN -> new double[]{r * 0.80, r * 0.78};
            case ASYMMETRIC_BASIN -> new double[]{r * 1.22, r * 0.90};
            case CRATER_LIKE -> new double[]{r * 1.10, r * 1.00};
            case SPLIT_BASIN -> new double[]{r * 1.45, r * 0.80};
            case BRANCHING -> new double[]{r * 1.35, r * 0.85};
            case OXBOW -> new double[]{r * 1.70, r * 0.62};
            case LOBED -> new double[]{r * 1.15, r * 1.00};
            case NARROW_GORGE -> new double[]{r * 0.48, r * 2.10};
            case TINY_WIDE -> new double[]{r * 1.40, r * 1.20};
            case LARGE_SOFT -> new double[]{r * 1.25, r * 1.12};
        };
    }

    /** The size class base radius for a family, honouring the family's inherent size tendency. */
    private static double sizeBase(Family family, double sizeRoll) {
        // LARGE_SOFT is biased large, TINY_WIDE / COMPACT biased small, everything else rolls.
        double roll = switch (family) {
            case LARGE_SOFT -> 0.55 + 0.45 * sizeRoll;
            case TINY_WIDE, COMPACT -> 0.45 * sizeRoll;
            default -> sizeRoll;
        };
        if (roll < 0.34) return lerp(SIZE_SMALL_MIN, SIZE_MEDIUM_MIN, roll / 0.34);
        return lerp(SIZE_MEDIUM_MIN, SIZE_LARGE_MAX, (roll - 0.34) / 0.66);
    }

    /** Liquid depth for a family: basins are deep, sheets are 1 block, gorges cut deeper. */
    private static int depthFor(Family family, double roll) {
        return switch (family) {
            case SHALLOW_BASIN, TINY_WIDE -> 1 + (int) (roll * 1.99);              // 1-2
            case DEEP_BASIN, NARROW_GORGE -> 4 + (int) (roll * 3.99);            // 4-7
            case CRATER_LIKE -> 3 + (int) (roll * 2.99);                         // 3-5
            case COMPACT, OVAL, IRREGULAR_BLOB, STEPPED_BLOB,
                    ASYMMETRIC_BASIN -> 1 + (int) (roll * 2.99);                 // 1-3
            default -> 2 + (int) (roll * 2.99);                                  // 2-4
        };
    }

    // ------------------------------------------------------------------ shape evaluation

    /** Normalised elliptical distance in the local frame (1.0 exactly on the nominal outline). */
    private double normalizedDistance(double lx, double lz) {
        // The effective distance is the MINIMUM over the family's components, so a union of
        // bodies (SPLIT, BRANCHING) is a true union and a channel (OXBOW) stays one body. Adding
        // a maximum anywhere would let a second, far component push the CORE distance up and
        // break the "the centre is the deepest column" invariant.
        return switch (family) {
            case OXBOW -> {
                // A long, smoothly curved channel that doubles back on itself.
                double t = lx / Math.max(1e-3, radiusX);
                double bend = 0.55 * radiusZ * Math.sin(t * 2.0);
                yield Math.hypot(t, (lz - bend) / Math.max(1e-3, radiusZ));
            }
            case SPLIT_BASIN -> Math.min(ellipse(lx + 0.42 * radiusX, lz, radiusX * 0.85, radiusZ),
                    ellipse(lx - 0.42 * radiusX, lz, radiusX * 0.85, radiusZ));
            case BRANCHING -> Math.min(ellipse(lx, lz, radiusX, radiusZ),
                    ellipse(lx + 0.85 * radiusX, lz - 0.35 * radiusZ,
                            radiusX * 0.55, radiusZ * 0.55) * 1.1);
            default -> ellipse(lx, lz, radiusX, radiusZ);
        };
    }

    private static double ellipse(double x, double z, double rx, double rz) {
        double a = x / Math.max(1e-3, rx);
        double b = z / Math.max(1e-3, rz);
        return Math.hypot(a, b);
    }

    /**
     * The signed "inside-ness" of a local offset: positive inside the pool, negative outside.

     * <p>Every family is a genuinely different function here - not a rescaled copy of one mask:
     * some are plain radial, some are lobe-modulated, one is ring-shaped, some are unions of
     * separate bodies, one is a curved channel. The seeded edge wave is added to the distance by
     * EVERY family, so no outline is ever a clean rectangle or a perfect circle.

     * <p>Pure and closed form: no per-block randomness, no lookup table, no state.
     */
    private double shapeAt(int dx, int dz) {
        double c = Math.cos(rotation);
        double s = Math.sin(rotation);
        double lx = dx * c + dz * s;
        double lz = -dx * s + dz * c;

        double d = normalizedDistance(lx, lz);
        // The asymmetry biases the OUTLINE (a lopsided basin) rather than translating the core,
        // so the deepest column of the pool is always its middle. The term vanishes at the centre.
        d += asymmetry * 0.55 * (lx / Math.max(1e-3, radiusX));
        // Seeded, smooth edge irregularity (organic outline). Two properties matter here:
        //   - the sum of the two waves is bounded, and the result is CLAMPED, so the outline can
        //     never be pushed past {@link #maxRadius()} (the placement bounds check relies on it);
        //   - the wave is GATED BY THE DISTANCE, so it is exactly zero at the centre. Without that
        //     gate the centre of a pool could be perturbed more than a neighbouring column, and
        //     the deepest column of the pool would no longer be its middle.
        double wave = edgeIrregularity * (edgeWave(lx, lz, edgeSeedA, edgeScaleX, edgeScaleZ)
                + 0.6 * edgeWave(lx, lz, edgeSeedB, edgeScaleX * 1.7, edgeScaleZ * 1.7));
        d += Math.max(-0.30, Math.min(0.30, wave)) * smooth(d);

        double v = 1.0 - d;   // >0 inside, <0 outside
        double ang = Math.atan2(lz, lx);
        return switch (family) {
            case COMPACT, OVAL, ELONGATED_X, ELONGATED_Z, TINY_WIDE -> v;
            case IRREGULAR_BLOB -> v - 0.18 * Math.abs(Math.sin(lobePhase + 3.0 * ang));
            case STEPPED_BLOB -> v + 0.12 * Math.sin(4.0 * ang + lobePhase) * smooth(v);
            case SHALLOW_BASIN -> v * (1.0 + 0.3 * smooth(v));
            // A sign-preserving bowl: flat near the rim, steepest in the middle. It MUST keep
            // the sign of v, or a squared profile would make the whole plane "inside".
            case DEEP_BASIN -> v <= 0.0 ? v : (v >= 1.0 ? v : v * v * (3.0 - 2.0 * v));
            // The asymmetry is a SHAPE bias (it shifts the outline), not a translation of the core.
            case ASYMMETRIC_BASIN -> v * (1.0 + 0.25 * Math.sin(lobePhase + 2.0 * ang));
            // A ring of raised rock around a shallower centre, but the CENTRE ITSELF is never
            // subtracted: the crater floor must stay the deepest column of the pool.
            case CRATER_LIKE -> v - 0.55 * Math.max(0.0, 1.0 - Math.abs(d - 0.62) / 0.30)
                    * clamp01(d / 0.35);
            // The isthmus is the ridge BETWEEN the two lobes, i.e. a barrier along lx = 0 that
            // runs across the pool in Z. Keyed on lx (NOT lz): a barrier keyed on lz would run
            // through BOTH lobe centres and would suppress the deepest point of the pool.
            case SPLIT_BASIN -> v - 0.30 * Math.exp(-(lx * lx) / (0.09 * radiusX * radiusX));
            case BRANCHING, OXBOW -> v;
            case LOBED -> v - 0.22 * Math.abs(Math.sin(3.0 * ang + lobePhase));
            // The narrow gorge is a real cut: a power below 1 steepens the walls while v(0) stays
            // the maximum, so the centre is still the deepest column.
            case NARROW_GORGE -> Math.signum(v) * Math.pow(Math.abs(v), 0.6);
            case LARGE_SOFT -> v <= 0.0 ? v : (v >= 1.0 ? v : v * v * (3.0 - 2.0 * v));
        };
    }
    private double edgeWave(double lx, double lz, double phase, double sx, double sz) {
        return Math.sin(lx * sx + phase) * Math.cos(lz * sz + phase * 0.7);
    }

    /** True when a local offset carries liquid. */
    public boolean isInside(int dx, int dz) {
        return shapeAt(dx, dz) > 0.0;
    }

    /**
     * The liquid depth (in blocks, at least 1) at a local offset, or 0 when the column carries no
     * liquid. The depth tapers smoothly toward the edge, so a pool has a natural shallow margin
     * instead of a cliff of full-depth blocks along a hard outline.
     */
    public int depthAt(int dx, int dz) {
        double s = shapeAt(dx, dz);
        if (s <= 0.0) return 0;
        // The depth is measured RELATIVE to this pool's own peak, not to the raw shape value:
        // a multi-lobe or channelled family never attains the raw value 1.0 at an integer column
        // (the lobe centre falls between two blocks), so a raw scaling would make every such
        // pool uniformly shallow. Normalising by the sampled peak guarantees that every pool
        // really does use its FULL declared depth range, and the taper toward the rim is
        // preserved because the peak is attained only at the deep point(s).
        int d = (int) Math.round((s / peak()) * maxDepth);
        if (d < 1) d = 1;
        if (d > maxDepth) d = maxDepth;
        return d;
    }

    /**
     * Lazily computed peak of {@link #shapeAt} over a small neighbourhood of the deep point.

     * Cached per instance: the shape is immutable, so one sweep is enough.

     */
    private double peakSample = -1.0;

    /** The normalised peak of the shape field, computed once on first use. */
    private double peak() {
        if (peakSample >= 0.0) return peakSample;
        int r = (int) Math.ceil(Math.max(radiusX, radiusZ)) + 2;
        double best = 0.0;
        for (int dx = -r; dx <= r; dx += 1) {
            for (int dz = -r; dz <= r; dz += 1) {
                if (!isInside(dx, dz)) continue;
                best = Math.max(best, shapeAt(dx, dz));
            }
        }
        peakSample = best > 1.0e-6 ? best : 1.0;
        return peakSample;
    }

    /**
     * True when a local offset is a genuine shallow EDGE column (liquid present but near the rim).
     * The adapter uses this to guarantee liquid is always ringed by real bank material.
     */
    public boolean isEdge(int dx, int dz) {
        double s = shapeAt(dx, dz);
        return s > 0.0 && s < 0.34;
    }

    // ------------------------------------------------------------------ helpers

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0 - 2.0 * c);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
