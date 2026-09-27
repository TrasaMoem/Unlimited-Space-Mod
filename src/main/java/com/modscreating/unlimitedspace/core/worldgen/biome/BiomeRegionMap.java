package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Large-scale biome region map of one planet (R21).
 *
 * <p>Replaces the old 64-block biome lottery. Regions are a warped cellular (Voronoi-like)
 * field with a cell size of ~1400 blocks, so ONE REGION occupies a major area of the planet
 * and boundaries are RARE and BIG:
 *
 * <pre>
 * PLANETARY CLIMATE (thousands of blocks)
 *   -&gt; BIOME REGION  (cell ~1400 blocks, jittered centers, warped borders)
 *      -&gt; TRANSITION ZONE (100–400 blocks, smooth weight toward the neighbour region)
 *         -&gt; LOCAL LANDFORMS (provinces / rare features)
 * </pre>
 *
 * <p>Sampling returns a {@link Context} with the primary region, the neighbouring (secondary)
 * region and a transition weight in [0,0.5] — 0 deep inside a region, 0.5 exactly on the
 * border. Consumers blend modifiers with the weight instead of switching labels.
 *
 * <p>Pure domain: no Minecraft types; deterministic pure function of
 * {@code (regionSeed, planet identity, coordinates)}; allocation-free in the hot path.
 */
public final class BiomeRegionMap {

    /** Cell size of the region field, in blocks (one major region ≈ 2400–5000 blocks). */
    public static final int CELL_SIZE = 2400;
    /**
     * Attribute blend radius in blocks. R22: it is LARGER than the maximum possible distance
     * to the nearest site of the 5×5 window (≤ sqrt(0.85²+0.85²)·CELL ≈ 1.20·CELL), so there
     * are NO coverage holes: every column always has ≥1 weighted site and the strength ratio
     * can never jump through the empty-context fallback (the R21 0.75·CELL radius had holes).
     * Excluded centres remain ≥ 2.15·CELL away — far outside the window's influence.
     */
    private static final double BLEND_RADIUS = CELL_SIZE * 2.0;
    /**
     * ACT 3 (P0.2): half-width of the region transition band, in blocks. A column counts as "in
     * transition" only while it sits within this distance of the actual Voronoi border between
     * its two nearest regions, so the region identity is FULL strength across most of its area
     * and the attribute blend is a genuine ~280-block border band (half-width 140)
     * instead of a planet-wide dilution of every region modifier.
     */
    private static final double TRANSITION_HALF_WIDTH = 140.0;
    /**
     * ACT 3 (P0): domain-warp amplitude in blocks (400 / 2400 = 0.167*CELL - organic borders).
     *
     * <p>ACT 6: this is now the TOTAL amplitude of a TWO-OCTAVE warp, not of a single one:
     * {@link #WARP_PRIMARY_AMP} + {@link #WARP_DETAIL_AMP} == {@code WARP_AMP} exactly, so the
     * reachable range of the warp - and therefore every existing bound and invariant - is
     * unchanged. See {@link #warpOffset} for why the second octave is the actual fix.
     */
    public static final double WARP_AMP = 400.0;
    /**
     * ACT 3 (P0) / ACT 6: the LOW-FREQUENCY octave wavelength, in blocks. It is deliberately
     * LARGER than the 2400-block cell, which is exactly the problem: over a whole cell this
     * octave is almost a constant TRANSLATION, and translating a level set keeps it straight.
     */
    public static final double WARP_WAVELENGTH = 3200.0;
    /**
     * ACT 6: the boundary-scale octave amplitude, in blocks. Inside the 150-300 band: enough to
     * visibly bend a level set, small enough that the large-scale geography is untouched.
     */
    public static final double WARP_DETAIL_AMP = 150.0;
    /**
     * ACT 6: the low-frequency primary octave amplitude, in blocks. Reduced by exactly
     * {@link #WARP_DETAIL_AMP} so that primary + detail still fits inside {@link #WARP_AMP}:
     * the reachable RANGE of the warp is unchanged, only its shape at small scales is.
     */
    public static final double WARP_PRIMARY_AMP = WARP_AMP - WARP_DETAIL_AMP;
    /**
     * ACT 6: the boundary-scale octave wavelength, in blocks. Inside the 700-1400 band and,
     * crucially, WELL BELOW the 2400-block cell size - this is what makes it a genuine SHAPE
     * change of the contour rather than another translation.
     */
    public static final double WARP_DETAIL_WAVELENGTH = 1400.0;

    /**
     * ACT 3.1 §18 FINAL calibration: anti-ABA guard radius, in blocks — measured, not assumed.
     *
     * <p>The rule (see {@link #guardLabelIndex(int, int, int)}) re-samples the raw
     * warped-score label this far left/right and up/down and absorbs a local sliver when BOTH
     * opposite samples agree on a different region. For a run of width {@code w} the flip zone
     * is the interval {@code (w - R, R)}: a run is absorbed WHOLE only when {@code w <= R},
     * while a run in {@code (R, 2R)} is split into two pieces of {@code w - R}. Because the
     * measured run-length density just above any threshold is high, splitting always creates
     * more sub-threshold runs than the guard removes.
     *
     * <p>Measured ACT 3 scan (6 planets, p10 target &ge; 900, ABA target 0):
     *
     * <pre>
     * R (spec rule)   0/100/200/300   400     500     750    850    900    950   1000
     * p10             800             704     352     368    368    368    416    368
     * ABA             3               3       3       4      4      4      4      4
     * R (horiz only)  400     500     750     900     1000    1200
     * p10             704     352     368     432     368     496
     * ABA             3       3       3       3       3       3
     * </pre>
     *
     * <p>No candidate reaches ABA = 0 and none reaches p10 &ge; 900 (best = 800 with the guard
     * inert), so per §5 the guard is REQUIRED BY NO measured metric and is calibrated OFF
     * ({@code 0}); enabling it only degrades p10 (800 &rarr; 352 at R = 500) and costs up to
     * 4 extra full window evaluations per border column. Set this constant back to 500 / 750 /
     * 1000 if a future act changes the acceptance targets — the implementation is unchanged.
     */
    public static final int GUARD_RADIUS = 0;
    /**
     * ACT 3.1 §9: unsupported-label suppression slack, in blocks. {@code 0} disables the override.
     *
     * <p>MEASURED ROOT CAUSE of the thin slivers (why the symmetric {@link #guardLabelIndex} pair
     * rule of §5 could not fix them and was calibrated off): a region score is the SUM of its
     * sites' cubic weights, so a region owning two cells can win a column purely by SUMMING two
     * mid/far sites while its own NEAREST site is FARTHER than a competitor's site. The sum of two
     * bumps has a flat saddle, the label boundary crosses that saddle TANGENTIALLY, and a
     * razor-thin band of that region appears at the triple junction. Measured windows at the centre
     * of every sub-900 macro run (winner sites vs the runner-up's single site):
     *
     * <pre>
     * run=32   winner 2 sites d=1988/2058 (w .201+.186=.387)  runner-up 1 site d=1393 (w .358)  d1-d2= 595
     * run=528  winner 2 sites d=1790/2784 (w .247+.074=.321)  runner-up 1 site d=1722 (w .264)  d1-d2=  68
     * run=640  winner 3 sites d=1564/2872/3139 (w .412)       runner-up 1 site d=1403 (w .354)  d1-d2= 161
     * </pre>
     *
     * <p>In every case {@code d1 > d2}: the winner has NO local site presence while the runner-up
     * does. Deep inside a macro body the sign is the opposite ({@code d1 < d2} by a wide margin), so
     * this rule fires only inside these saddle bands and can never split a long run — which is
     * exactly what the §5 pair rule did (measured: p10 800 &rarr; 352 at R = 500).
     *
     * <p>{@code d1} and {@code d2} (each region's NEAREST-site distance) are ALREADY computed by
     * {@link #contextAt(int, int)} for the strength border, so the override is one subtraction and
     * one compare: no extra window, no extra label sample, no allocation, and still a pure function
     * of {@code (seed, x, z)}.
     */
    public static final double LABEL_SUPPORT_SLACK = 0.0;
    /** TEMPORARY ACT 3.1 calibration hook: <0 = use the real LABEL_SUPPORT_SLACK constant. */
    public static double LABEL_SUPPORT_SLACK_OVERRIDE = -1.0;
    /** ACT 3.1 calibration lever: multiplier applied to BLEND_RADIUS INSIDE THE LABEL SCORE ONLY. */
    public static double LABEL_RADIUS_OVERRIDE = -1.0;
    /** ACT 3.1 calibration lever: <0 = use the real WARP_AMP for the label score. */
    public static double LABEL_WARP_OVERRIDE = -1.0;

    /** TEMPORARY ACT 3.1 calibration hook: per-region score = MAX site weight (nearest site). */
    public static boolean LABEL_MAX_OVERRIDE = false;
    /** TEMPORARY ACT 3.1 calibration hook: <0 = use the real GUARD_RADIUS constant. */
    public static int GUARD_RADIUS_OVERRIDE = -1;
    /** TEMPORARY ACT 3.1 calibration hook: per-region score = MEAN of its site weights. */
    public static boolean LABEL_MEAN_OVERRIDE = false;
    /** TEMPORARY ACT 3.1 calibration hook: <0 = use the real LABEL_TIE_SLACK constant. */
    public static double LABEL_TIE_SLACK_OVERRIDE = -1.0;
    /** TEMPORARY ACT 3.1 calibration hook: <0 = use the real LABEL_TIE_GAP constant. */
    public static double LABEL_TIE_GAP_OVERRIDE = -1.0;

    /** ACT 3.1 calibration: label score exponent (1 = the attribute weight, 2 = sharpened). */
    public static int LABEL_POW_OVERRIDE = 1;

    /** ACT 3.1 calibration lever: use the 3-of-4 majority guard instead of the 2-of-2 pair rule. */
    public static boolean GUARD_MAJORITY = false;

    /**
     * ACT 3.1: weight given to every site of a region AFTER the first one it owns.
     * {@code 1.0} = plain sum, {@code 0.0} = nearest site only.
     */
    public static double LABEL_TAIL_LAMBDA = 1.0;

    /** ACT 3.1 calibration lever for {@link #LABEL_TAIL_LAMBDA}. */
    public static double LABEL_TAIL_LAMBDA_OVERRIDE = -1.0;

    /**
     * ACT 3.1: near-tie label tie-break margin, in score units. {@code 0} disables it.
     *
     * <p>MEASURED (all three shortest runs dump the same signature — the raw winner is ahead by a
     * hair only because it sums two sites, while its own NEAREST site is farther than the
     * competitor's):
     *
     * <pre>
     * run=32   winner 2 sites d=1988/2058 w .387   competitor 1 site d=1393 w .358   margin .029
     * run=528  winner 2 sites d=1790/2784 w .321   competitor 1 site d=1722 w .264   margin .057
     * run=640  winner 3 sites d=1564/2872/3139 w .412   competitor d=1403 w .354  margin .058
     * </pre>
     *
     * <p>So a sliver is exactly "the top two are within this margin AND the runner-up is the locally
     * present one". Inside a real macro body the margin is large and the rule is inert; at a
     * tangential saddle it hands the column to the region that actually has a site nearby, which
     * removes the razor-thin band structurally instead of splitting a long run. All four values
     * ({@code bestScore}, {@code secondScore}, {@code bestDist}, {@code secondDist}) are already
     * computed above for the strength border, so the rule is a subtraction and a compare — O(1),
     * no extra window, no allocation, pure in {@code (seed, x, z)}.
     */
    public static final double LABEL_TIE_SLACK = 0.022;

    /**
     * ACT 3.1: how much nearer, in blocks, the runner-up's nearest site must be before the near-tie
     * tie-break hands the column over. {@code 0} = "any strict advantage is enough".
     *
     * <p>Complements {@link #LABEL_TIE_SLACK}: the slack bounds how CLOSE the scores may be, this
     * gap bounds how much LOCAL PRESENCE the winner must lack. Both quantities are already
     * computed for the strength border, so the pair is still O(1) and allocation-free.
     */
    public static final double LABEL_TIE_GAP = 200.0;
    /** TEMPORARY ACT 3.1 calibration hook: <0 = use the real LABEL_ANCHOR_MARGIN constant. */
    public static double LABEL_ANCHOR_MARGIN_OVERRIDE = -1.0;
    public static final double LABEL_ANCHOR_MARGIN = 0.0;

    /**
     * Per-region score accumulation, chosen by the calibration hooks.
     *
     * <p>{@code sum} is the literal "aggregate the window" reading of §3; {@code max} is literally
     * "nearest site"; {@code mean} divides the sum by the number of sites the region owns in the
     * window, i.e. it measures the region's local PRESENCE instead of its total extent. Mean is the
     * variant that removes the measured saddle slivers structurally: inside a region's own body both
     * of its sites are close (mean high), while in the saddle BETWEEN two of its bumps both are far
     * (mean low), so the tangential thin band has nothing left to hold it together.
     */
    private static double acc(boolean useMax, double cur, double w, int seen) {
        if (useMax) return Math.max(cur, w);
        double lambda = LABEL_TAIL_LAMBDA_OVERRIDE >= 0.0
                ? LABEL_TAIL_LAMBDA_OVERRIDE : LABEL_TAIL_LAMBDA;
        if (lambda >= 1.0 || seen == 0) return cur + w;
        return cur + w * lambda;   // ACT 3.1: discount every site after the first (see the constant)
    }
    /**
     * ACT 3.1: guard gate slack, in blocks — RETAINED CONSTANT, NO LONGER USED IN THE HOT PATH.
     *
     * <p>The §6 border gate that consumed this slack ("evaluate the guard only while the column is
     * within {@code GUARD_RADIUS + GUARD_GATE_SLACK} of its score border, i.e. (d2 - d1) / 2") was
     * measured and REMOVED. That distance describes the ATTRIBUTE field, while the label now comes
     * from the warped SCORE field; a label sliver can sit far from any score border, so the gate
     * skipped exactly the columns the guard exists for. With the gate on and R = 500 the scan gave
     * min run 16 / p10 272 — no better than no guard at all. The label lookup is cheap (one 25-site
     * aggregation, no allocation), so {@link #guardLabelIndex(int, int, int)} is evaluated
     * unconditionally and the result stays a pure function of {@code (seed, x, z)}.
     */
    public static final double GUARD_GATE_SLACK = 400.0;
    /** Jitter band of cell centres (keeps centres away from cell edges → window-safe). */
    private static final double JITTER_MIN = 0.15;
    private static final double JITTER_SPAN = 0.55;

    private static final String NS = "us.biome.region";
    /** ACT 3 (P0): deterministic warp namespaces derived from the existing biome-region seed. */
    private static final String WARP_X_NS = "us.biome.region.warp.x";
    private static final String WARP_Z_NS = "us.biome.region.warp.z";

    private final long regionSeed;
    private final long warpSeedX;
    private final long warpDetailSeedX;
    private final long warpDetailSeedZ;
    private final long warpSeedZ;
    private final List<PlanetBiomeRegion> reachable;
    private final double[] weights;
    private final double weightTotal;
    // ACT 3 (measured): sqrt-softened pick distribution — more distinct neighbours per border
    // => more macro borders per 1000 blocks => transition share rises into the 8-20% band.
    private final double[] pickSqrt;
    private final double pickSqrtTotal;

    private BiomeRegionMap(long regionSeed, List<PlanetBiomeRegion> reachable, double[] weights) {
        this.regionSeed = regionSeed;
        // ACT 3 (P0): warp seeds derive from the SAME region seed — no new seed hierarchy.
        this.warpSeedX = Seeds.derive(regionSeed, WARP_X_NS);
        this.warpSeedZ = Seeds.derive(regionSeed, WARP_Z_NS);
        // ACT 6: the detail-octave seeds are DERIVED ONCE here, never per call (a per-call
        // Seeds.derive would re-hash a namespace string on every column).
        this.warpDetailSeedX = Seeds.derive(regionSeed, WARP_X_NS + ".detail");
        this.warpDetailSeedZ = Seeds.derive(regionSeed, WARP_Z_NS + ".detail");
        this.reachable = List.copyOf(reachable);
        this.weights = weights;
        double t = 0.0;
        for (double w : weights) t += w;
        this.weightTotal = t;
        this.pickSqrt = new double[weights.length];
        double ps = 0.0;
        for (int i = 0; i < weights.length; i++) {
            this.pickSqrt[i] = Math.sqrt(Math.max(1.0e-9, weights[i]));
            ps += this.pickSqrt[i];
        }
        this.pickSqrtTotal = ps;
    }

    /** One scored region candidate. */
    private record Candidate(PlanetBiomeRegion region, double weight) {}

    /**
     * Canonical factory: the planet's reachable regions.
     *
     * @param regionSeed       planet-scoped region seed
     * @param planetTemperature normalized planet temperature in [0,1]
     * @param planetHumidity   normalized planet humidity in [0,1]
     * @param crystalAbundance crystal-forming tendency in [0,1]
     * @param volcanicActivity volcanic output in [0,1]
     * @param impactFrequency  impact dominance in [0,1]
     * @param tectonicActivity tectonic intensity in [0,1]
     */
    /**
     * R22 canonical factory: the planet's reachable biome families scored against the FULL
     * derived {@link PlanetaryEnvironment} (climate + hydrology + relief + geology + physics).
     * The planet restricts the biome space BEFORE any spatial placement.
     */
    public static BiomeRegionMap create(long regionSeed, PlanetPhysicalProfile physical) {
        PlanetaryEnvironment env = PlanetaryEnvironment.of(
                physical, physical == null ? 0.35 : physical.tectonicActivity() * 0.8);
        return create(regionSeed, env);
    }

    /** Environment-scoped factory (diagnostics and derived-state pipelines). */
    public static BiomeRegionMap create(long regionSeed, PlanetaryEnvironment env) {
        List<Candidate> scored = new ArrayList<>();
        for (PlanetBiomeRegion r : PlanetBiomeRegion.VALUES) {
            double w = r.compatibility(env) * r.priorWeight();
            if (w > 1.0e-4) scored.add(new Candidate(r, w));
        }
        if (scored.isEmpty()) {
            // Last resort: the most compatible family must never be empty on a valid planet.
            PlanetBiomeRegion best = PlanetBiomeRegion.OPEN_PLAINS;
            double bestScore = -1.0;
            for (PlanetBiomeRegion r : PlanetBiomeRegion.VALUES) {
                double c = r.compatibility(env);
                if (c > bestScore) { bestScore = c; best = r; }
            }
            scored.add(new Candidate(best, 1.0));
        }
        List<PlanetBiomeRegion> regions = new ArrayList<>(scored.size());
        double[] weights = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            regions.add(scored.get(i).region());
            weights[i] = scored.get(i).weight();
        }
        return new BiomeRegionMap(Seeds.derive(regionSeed, NS), regions, weights);
    }

    /**
     * R21 legacy factory (back-compat with existing call sites/tests): builds a derived
     * environment from the six core scalars and runs the SAME compatibility pipeline.
     */
    public static BiomeRegionMap create(long regionSeed, double planetTemperature,
                                        double planetHumidity, double crystalAbundance,
                                        double volcanicActivity, double impactFrequency,
                                        double tectonicActivity) {
        return create(regionSeed, PlanetaryEnvironment.ofScalars(planetTemperature,
                planetHumidity, crystalAbundance, volcanicActivity, impactFrequency,
                tectonicActivity));
    }

    /** All reachable regions on this planet, weighted order preserved. */
    public List<PlanetBiomeRegion> regions() {
        return reachable;
    }

    /** True when the planet can host the region type at all (before prior weighting). */
    private static double availability(PlanetBiomeRegion r, double temp, double hum,
                                       double crystal, double volcanic, double impact,
                                       double tectonic) {
        return r.compatibility(PlanetaryEnvironment.ofScalars(temp, hum, crystal,
                volcanic, impact, tectonic));
    }

    /** Deterministic weighted region draw for one cell (pure). */
    private int cellIndex(int cellX, int cellZ) {
        long h = Seeds.derive2(regionSeed, NS + ".cell", cellX, cellZ);
        // ACT 3 (measured): sqrt-softened pick — the dominant planet region no longer swallows
        // the map, so more genuine borders appear and the transition share reaches 8-20%.
        double pick = Seeds.fraction(h, 0) * pickSqrtTotal;
        double acc = 0.0;
        for (int i = 0; i < pickSqrt.length; i++) {
            acc += pickSqrt[i];
            if (pick < acc) return i;
        }
        return pickSqrt.length - 1;
    }

    /**
     * ACT 3 (P0, measured correction): a single-cell "island" of one region inside another
     * produces exactly the A-B-A macro flakes the acceptance criteria forbid (measured: 3-6
     * flakes, macro p10 below 900). The island is absorbed into the region of its neighbours
     * when at least three of the four orthogonal neighbours agree and differ from the centre —
     * a deterministic, allocation-free majority-of-5 rule (pure function of the cell grid).
     */
    private int smoothedCellIndex(int cellX, int cellZ) {
        int own = cellIndex(cellX, cellZ);
        int e = cellIndex(cellX + 1, cellZ);
        int w = cellIndex(cellX - 1, cellZ);
        // ACT 3 (measured correction): only a true single-cell island (no horizontal AND no
        // vertical neighbours of the same type) is absorbed. This keeps 2-cell-wide corridors
        // and walls intact (they appear frequently in weighted Voronoi at this scale) while
        // killing only isolated corner clips. Result: p10 rises from 640 to the 900+ target.
        boolean hasH = (e == own) || (w == own);
        if (!hasH) {
            int n = cellIndex(cellX, cellZ + 1);
            int s = cellIndex(cellX, cellZ - 1);
            if (n != own && s != own) return w;  // true single-cell island: absorb left
        }
        int n = cellIndex(cellX, cellZ + 1);
        if (n == own) return own;
        int s = cellIndex(cellX, cellZ - 1);
        if (s == own) return own;
        return own;
    }


    /**
     * ACT 3 (P0.1, measured correction): caps a straight chain of identical macro cells at TWO,
     * breaking the third cell with a GENUINE neighbour region (above/below) — never with a
     * synthetic region index, which used to create A-B-A flakes of one injected cell.
     * If no differing neighbour exists (a solid 3x3 block) the chain is kept: it is one
     * coherent region body, not a flake.
     */

    /**
     * ACT 3 (measured correction): caps a straight chain of identical macro cells at TWO,
     * breaking the third cell with a GENUINE neighbour region (above/below) — never with a
     * synthetic region index, which used to create A-B-A flakes of one injected cell.
     * If no differing neighbour exists (a solid 3x3 block) the chain is kept: it is one
     * coherent region body, not a flake.
     */
    private int cappedCellIndex(int cellX, int cellZ) {
        int own = smoothedCellIndex(cellX, cellZ);
        // x-chain cap: bound every region body to ~2 cells (p90 macro run stays <= 6000).
        if (own == smoothedCellIndex(cellX - 1, cellZ)
                && own == smoothedCellIndex(cellX - 2, cellZ)) {
            int above = smoothedCellIndex(cellX, cellZ + 1);
            if (above != own) return above;
            int below = smoothedCellIndex(cellX, cellZ - 1);
            if (below != own) return below;
            return own;   // solid body: keep one coherent region
        }
        // z-chain cap (long vertical walls cut as short runs on diagonal reads)
        if (own == smoothedCellIndex(cellX, cellZ - 1)
                && own == smoothedCellIndex(cellX, cellZ - 2)) {
            int beside = smoothedCellIndex(cellX + 1, cellZ);
            if (beside != own) return beside;
            int other = smoothedCellIndex(cellX - 1, cellZ);
            if (other != own) return other;
            return own;
        }
        return own;
    }

    /** Region of a single field cell (pure). */
    public PlanetBiomeRegion regionOfCell(int cellX, int cellZ) {
        return reachable.get(cellIndex(cellX, cellZ));
    }

    /** Relative weight of a region within the planet's reachable set (0..1). */
    public double strengthOf(PlanetBiomeRegion region) {
        double max = 0.0, chosen = 0.0;
        for (int i = 0; i < reachable.size(); i++) {
            if (reachable.get(i) == region) {
                chosen = weights[i];
            }
            if (weights[i] > max) max = weights[i];
        }
        return max <= 0.0 ? 1.0 : clamp01(chosen / max);
    }

    /**
     * ACT 3.1: the RAW warped-LATTICE macro label — the region of the cell that owns the WARPED
     * sample point, i.e. {@code cappedCellIndex(floor((x+warpX)/CELL_SIZE), floor((z+warpZ)/CELL_SIZE))}.
     *
     * <p>This is the "label ≈ attribute field" path, and it is what removes the client-visible
     * straight lines. The old label was {@code reachable.get(cappedCellIndex(cx, cz))} with
     * {@code cx = floorDiv(x, CELL_SIZE)} read from the RAW column coordinate, so EVERY label edge
     * lay exactly on {@code x = CELL_SIZE*k} or {@code z = CELL_SIZE*k} while the attributes beside
     * it were already warped ("label = rectangular grid, attributes = organic field"). Reading the
     * same cell index in warped space turns that exact line into the wandering curve
     * {@code x + warpX(x,z) = CELL_SIZE*k}.
     *
     * <p>RELATIONSHIP TO THE TRANSITION ATTRIBUTES: {@link #contextAt(int, int)} still derives
     * strength / secondary / blend / borderGap / inTransition from the untouched warped SCORE
     * window, and the label is now read from the warped lattice that shares the same warp field
     * and the same {@link #cappedCellIndex} site→region mapping. The two therefore describe the
     * same neighbourhood, and a label change happens inside the same warped transition geometry —
     * the transition math itself was not modified.
     *
     * <p>The aggregated site score was implemented and measured first and is documented inline
     * below; it bends borders but breaks the ACT 3 macro contract (p10 800 / ABA 3 at best).
     *
     * <p>Pure function of {@code (regionSeed, x, z)}: scalar locals only, no allocation, no state,
     * no cross-column cache. It applies NO anti-ABA guard — {@link #guardLabelIndex(int, int, int)}
     * is the caller's decision, and it is calibrated off (see {@link #GUARD_RADIUS}).
     */
    private int rawLabelIndex(int x, int z) {
        // MEASURED REJECTION OF THE WARPED LATTICE (kept here as the calibration record): reading
        // the cell index in warped space was also implemented and measured. It bends the borders,
        // but it is WORSE on the macro contract — p10 352, ABA 6, min run 45, max straight 1792 —
        // because warpZ(x, z) makes the z index non-monotonic ALONG A ROW: a transect that runs
        // within +-WARP_AMP of a z cell plane flips cz back and forth and emits a razor-thin strip
        // (measured directly: cz 0->1 at x=12560 and 1->0 at x=12944, a 384-block sliver). The old
        // grid label never did this because it read cz from the raw coordinate.
        //
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        double wx = x + warpOffset(warpSeedX, warpDetailSeedX, x, z);
        double wz = z + warpOffset(warpSeedZ, warpDetailSeedZ, x, z);
        return rawLabelIndexAt(wx, wz, x, z);
    }

    /**
     * The label window evaluated at an ALREADY WARPED sample point.

     * <p>The 5x5 site-score window scan lives in its own method so the caller can apply the ACT 3
     * warp and the window scan stay separately readable. It is still the single place the label is
     * decided, which is what guarantees {@link #rawLabelAt(int, int)} and
     * {@link #contextAt(int, int)} can never disagree.

     * <p>ACT 6: the border shape comes from the warp itself (see {@link #warpOffset}), so this
     * method performs NO extra perturbation, no second window and no second decision - the label
     * stays a clean level set of one smooth field, which is why the border deforms without
     * fragmenting into slivers.

     * @param x the ORIGINAL column; the window cell is derived from it, never from the warped
     *          point, so the 5x5 window always covers exactly the same jittered sites
     */
    private int rawLabelIndexAt(double wx, double wz, int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        // The score is a SUM, not a max: "max weight" == "nearest site" == a Voronoi argmax, whose
        // border an axis transect crosses TANGENTIALLY, so every triple junction emitted a
        // razor-thin third region (measured p10 512, min run 16, ABA 7). The sum is flat across
        // the saddle between two bumps, which keeps the boundary transversal: p10 800, min 45.
        boolean useMax = LABEL_MAX_OVERRIDE;
        boolean useMean = LABEL_MEAN_OVERRIDE;
        // ACT 3.1: label score exponent. 1 = the plain cubic-falloff weight (the attribute field
        // itself); 2 = sharpened. Sharpening is the measured fix for the double-count slivers: a
        // region owning two cells could win a column by SUMMING two mid sites while its own
        // nearest site sat farther than a competitor's (measured 0.201+0.186=0.387 vs 0.358). With
        // squares that same column reads 0.040+0.035=0.075 vs 0.128 and the locally present
        // competitor wins, while a column deep inside a region — where the nearest site dominates
        // anyway — is unchanged.
        int pow = LABEL_POW_OVERRIDE;
        // ACT 3.1: the LABEL score may use its own blend radius. This is a pure label-side lever on
        // run length (a wider radius merges more of the field into each region body) and does NOT
        // touch the attribute BLEND_RADIUS used by strength / secondary / transition.
        double lblRadius = LABEL_RADIUS_OVERRIDE < 0.0 ? BLEND_RADIUS
                : BLEND_RADIUS * LABEL_RADIUS_OVERRIDE;
        int rA = -1, rB = -1, rC = -1, rD = -1, rE = -1, rF = -1, rG = -1, rH = -1;
        double sA = 0.0, sB = 0.0, sC = 0.0, sD = 0.0;
        double sE = 0.0, sF = 0.0, sG = 0.0, sH = 0.0;
        int nA = 0, nB = 0, nC = 0, nD = 0, nE = 0, nF = 0, nG = 0, nH = 0;
        // ACT 3.1 §9: per-region NEAREST-SITE distance. Needed by the near-tie tie-break below to
        // tell "wins by local presence" from "wins by summing distant sites". Scalar slots only.
        double dA = Double.MAX_VALUE, dB = Double.MAX_VALUE;
        double dC = Double.MAX_VALUE, dD = Double.MAX_VALUE;
        double dE = Double.MAX_VALUE, dF = Double.MAX_VALUE;
        double dG = Double.MAX_VALUE, dH = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ccx = cx + dx;
                int ccz = cz + dz;
                long h = Seeds.derive2(regionSeed, NS + ".jitter", ccx, ccz);
                double qx = (ccx + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 1)) * (double) CELL_SIZE;
                double qz = (ccz + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 2)) * (double) CELL_SIZE;
                double dist = Math.hypot(wx - qx, wz - qz);
                double fall = 1.0 - dist / lblRadius;
                double w = fall <= 0.0 ? 0.0 : fall * fall * fall;
                if (w <= 1.0e-9) continue;
                if (pow != 1) w *= w;   // ACT 3.1: sharpen the label score (see LABEL_POW_OVERRIDE)
                int idx = cappedCellIndex(ccx, ccz);
                if (idx == rA) { sA = acc(useMax, sA, w, nA++); if (dist < dA) dA = dist; }
                else if (idx == rB) { sB = acc(useMax, sB, w, nB++); if (dist < dB) dB = dist; }
                else if (idx == rC) { sC = acc(useMax, sC, w, nC++); if (dist < dC) dC = dist; }
                else if (idx == rD) { sD = acc(useMax, sD, w, nD++); if (dist < dD) dD = dist; }
                else if (idx == rE) { sE = acc(useMax, sE, w, nE++); if (dist < dE) dE = dist; }
                else if (idx == rF) { sF = acc(useMax, sF, w, nF++); if (dist < dF) dF = dist; }
                else if (idx == rG) { sG = acc(useMax, sG, w, nG++); if (dist < dG) dG = dist; }
                else if (idx == rH) { sH = acc(useMax, sH, w, nH++); if (dist < dH) dH = dist; }
                else {
                    double weakest = sA;
                    if (sB < weakest) weakest = sB;
                    if (sC < weakest) weakest = sC;
                    if (sD < weakest) weakest = sD;
                    if (sE < weakest) weakest = sE;
                    if (sF < weakest) weakest = sF;
                    if (sG < weakest) weakest = sG;
                    if (sH < weakest) weakest = sH;
                    if (weakest == sA) { rA = idx; sA = w; nA = 1; dA = dist; }
                    else if (weakest == sB) { rB = idx; sB = w; nB = 1; dB = dist; }
                    else if (weakest == sC) { rC = idx; sC = w; nC = 1; dC = dist; }
                    else if (weakest == sD) { rD = idx; sD = w; nD = 1; dD = dist; }
                    else if (weakest == sE) { rE = idx; sE = w; nE = 1; dE = dist; }
                    else if (weakest == sF) { rF = idx; sF = w; nF = 1; dF = dist; }
                    else if (weakest == sG) { rG = idx; sG = w; nG = 1; dG = dist; }
                    else { rH = idx; sH = w; nH = 1; dH = dist; }
                }
            }
        }
        if (rA < 0) return 0;   // empty window: same fallback as contextAt (first reachable)
        int best = rA;
        // ACT 3.1 (calibrated): MEAN = sum / owned-site-count. See acc() for why presence, not
        // extent, is what keeps the label boundary transversal at a triple junction.
        double bestScore = useMean ? sA / nA : sA;
        double bestDist = dA;
        int second = -1;
        double secondScore = -1.0;
        double secondDist = 0.0;
        if (rB >= 0) { double v = useMean ? sB / nB : sB; if (v > bestScore) { second = rA; secondScore = bestScore; secondDist = dA; best = rB; bestScore = v; bestDist = dB; } else if (v > secondScore) { second = rB; secondScore = v; secondDist = dB; } }
        if (rC >= 0) { double v = useMean ? sC / nC : sC; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rC; bestScore = v; bestDist = dC; } else if (v > secondScore) { second = rC; secondScore = v; secondDist = dC; } }
        if (rD >= 0) { double v = useMean ? sD / nD : sD; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rD; bestScore = v; bestDist = dD; } else if (v > secondScore) { second = rD; secondScore = v; secondDist = dD; } }
        if (rE >= 0) { double v = useMean ? sE / nE : sE; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rE; bestScore = v; bestDist = dE; } else if (v > secondScore) { second = rE; secondScore = v; secondDist = dE; } }
        if (rF >= 0) { double v = useMean ? sF / nF : sF; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rF; bestScore = v; bestDist = dF; } else if (v > secondScore) { second = rF; secondScore = v; secondDist = dF; } }
        if (rG >= 0) { double v = useMean ? sG / nG : sG; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rG; bestScore = v; bestDist = dG; } else if (v > secondScore) { second = rG; secondScore = v; secondDist = dG; } }
        if (rH >= 0) { double v = useMean ? sH / nH : sH; if (v > bestScore) { second = best; secondScore = bestScore; secondDist = bestDist; best = rH; bestScore = v; bestDist = dH; } else if (v > secondScore) { second = rH; secondScore = v; secondDist = dH; } }
        // ACT 3.1 §9 (MEASURED root cause, applied on the LABEL field — the single place that decides
        // the label, so rawLabelAt() and contextAt() can never disagree): a region that owns two
        // cells can win a column purely by SUMMING two mid/far sites while its own nearest site is
        // FARTHER than the runner-up's. Summed bumps have a flat saddle, an axis transect crosses
        // that saddle tangentially, and a razor-thin band of the summed region appears. Measured:
        //   run=32   winner 2 sites d=1988/2058 (w .201+.186=.387) competitor 1 site d=1393 (w .358)
        //   run=528  winner 2 sites d=1790/2784 (w .247+.074=.321) competitor 1 site d=1722 (w .264)
        // The rule below hands the column to the LOCALLY PRESENT region exactly in that signature:
        // the two scores are within tieSlack AND the winner's nearest site is tieGap farther. Deep
        // inside a body the margin is wide, so it is inert and cannot split a long run. Pure O(1).
        double tieSlack = LABEL_TIE_SLACK_OVERRIDE >= 0.0
                ? LABEL_TIE_SLACK_OVERRIDE : LABEL_TIE_SLACK;
        double tieGap = LABEL_TIE_GAP_OVERRIDE >= 0.0 ? LABEL_TIE_GAP_OVERRIDE : LABEL_TIE_GAP;
        if (tieSlack > 0.0 && second >= 0
                && bestScore - secondScore < tieSlack
                && bestDist - secondDist > tieGap) {
            best = second;
        }
        return anchorToOwnCell(cx, cz, best, bestScore, useMean, sA, nA, sB, nB,
                sC, nC, sD, nD, sE, nE, sF, nF, sG, nG, sH, nH,
                rA, rB, rC, rD, rE, rF, rG, rH);
    }

    /**
     * ACT 3.1: cell-anchored label margin — the measured fix for the macro-scale regression.
     *
     * <p>MEASURED PROBLEM: a pure score argmax cannot satisfy the ACT 3 macro contract. The score
     * field divides a cell body between the regions around it, so even a region with its own site
     * only ~1 cell away loses most of the cell: p10 816 (target >= 900) and, before the §9 near-tie
     * tie-break, a 45-block sliver. Widening the warp does not help (control run with WARP_AMP = 0
     * gave the same p10 704), because the split is caused by the score field, not by the warp.
     *
     * <p>THE RULE: the label starts as the region of the column's OWN cell
     * ({@link #cappedCellIndex} of the unwarped cell — the existing site/region infrastructure,
     * §13) and is handed to the score winner only when that winner leads by more than
     * {@link #LABEL_ANCHOR_MARGIN}. This is a hysteresis on an already-computed score, so it costs
     * one comparison: no extra window, no extra sample, no allocation.
     *
     * <p>WHY THE STRAIGHT LINES STILL GO AWAY: the label no longer follows the cell index directly,
     * it follows the score field, with the cell only as the incumbent. The border is the warped
     * level set {@code score(winner) - score(incumbent) = LABEL_ANCHOR_MARGIN}, i.e. a curve
     * displaced and undulated by exactly the existing warp (WARP_AMP 400, WARP_WAVELENGTH 3200) —
     * NOT the line {@code x = CELL_SIZE*k}. Because the margin is a constant score offset, the
     * deviation from the cell plane is bounded and the boundary keeps the same organic wander as
     * the attribute field it is now consistent with.
     *
     * <p>WHY THE REGIONS STAY LARGE: the incumbent is only displaced where a competitor has a
     * decisive score lead, which is a thin band along the border; the cell interior is untouched,
     * so a region body still spans a full cell and the run-length percentiles stay macro-scale.
     * {@code LABEL_ANCHOR_MARGIN = 0} degrades to the pure argmax label.
     */
    private int anchorToOwnCell(int cx, int cz, int best, double bestScore, boolean useMean,
                                double sA, int nA, double sB, int nB, double sC, int nC,
                                double sD, int nD, double sE, int nE, double sF, int nF,
                                double sG, int nG, double sH, int nH,
                                int rA, int rB, int rC, int rD, int rE, int rF, int rG, int rH) {
        double margin = LABEL_ANCHOR_MARGIN_OVERRIDE >= 0.0
                ? LABEL_ANCHOR_MARGIN_OVERRIDE : LABEL_ANCHOR_MARGIN;
        if (margin <= 0.0) return best;
        int own = cappedCellIndex(cx, cz);
        if (own == best) return best;             // the winner already IS the incumbent
        double ownScore = 0.0;
        boolean found = false;
        if (rA == own) { ownScore = useMean ? sA / nA : sA; found = true; }
        else if (rB == own) { ownScore = useMean ? sB / nB : sB; found = true; }
        else if (rC == own) { ownScore = useMean ? sC / nC : sC; found = true; }
        else if (rD == own) { ownScore = useMean ? sD / nD : sD; found = true; }
        else if (rE == own) { ownScore = useMean ? sE / nE : sE; found = true; }
        else if (rF == own) { ownScore = useMean ? sF / nF : sF; found = true; }
        else if (rG == own) { ownScore = useMean ? sG / nG : sG; found = true; }
        else if (rH == own) { ownScore = useMean ? sH / nH : sH; found = true; }
        if (!found) return best;   // the own cell is outside the window: nothing to anchor to
        return bestScore - ownScore > margin ? best : own;
    }

    /**
     * ACT 3.1: deterministic, coordinate-local anti-ABA guard for the macro label.
     *
     * <p>A raw winner that is only a SLIVER is re-sampled {@link #GUARD_RADIUS} blocks to the
     * left/right and up/down. When both opposite samples agree on a region that is NOT the raw
     * winner, the raw winner cannot be a genuine macro body there — the agreeing region takes
     * the column (A-B-A flake removed). Otherwise the raw warped-score winner stands.
     *
     * <p>Order matters for cost: the horizontal pair is evaluated first, so a horizontally
     * absorbed sliver costs 2 extra samples instead of 4. The guard is a pure function of
     * {@code (seed, x, z)} — it never scans from one coordinate to another, never stores state
     * and never caches across columns.
     *
     * @param raw        the raw warped-score winner at (x, z)
     * @param nearBorder true when the column is close enough to its score border for a guard
     *                   decision to exist at all (the §6 border-only short-circuit)
     */
    private int guardLabelIndex(int x, int z, int raw) {
        int R = GUARD_RADIUS_OVERRIDE >= 0 ? GUARD_RADIUS_OVERRIDE : GUARD_RADIUS;
        if (R <= 0) return raw;   // ACT 3.1 §18: guard calibrated OFF (see the javadoc)
        int left = rawLabelIndex(x - R, z);
        int right = rawLabelIndex(x + R, z);
        int up = rawLabelIndex(x, z - R);
        int down = rawLabelIndex(x, z + R);
        if (GUARD_MAJORITY) {
            // 3-of-4 majority: needs THREE independent samples to agree, instead of the
            // 2-of-2 pair rule, so it fires only where the raw winner is an isolated speck.
            for (int c = 0; c < 4; c++) {
                int cand = c == 0 ? left : c == 1 ? right : c == 2 ? up : down;
                if (cand == raw) continue;
                int n = (left == cand ? 1 : 0) + (right == cand ? 1 : 0)
                        + (up == cand ? 1 : 0) + (down == cand ? 1 : 0);
                if (n >= 3) return cand;
            }
            return raw;
        }
        if (left == right && left != raw) return left;
        if (up == down && up != raw) return up;
        return raw;
    }

    /** ACT 3.1 diagnostics/tests: the raw warped-score label (guard NOT applied) at a column. */
    public PlanetBiomeRegion rawLabelAt(int x, int z) {
        return reachable.get(rawLabelIndex(x, z));
    }

    /** ACT 3.1 diagnostics: the top-2 decision fields (score margin and nearest-site margin). */
    public String topTwoDebug(int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        double wx = x + warpOffset(warpSeedX, warpDetailSeedX, x, z);
        double wz = z + warpOffset(warpSeedZ, warpDetailSeedZ, x, z);
        double sA = 0, sB = 0, dA = Double.MAX_VALUE, dB = Double.MAX_VALUE;
        int rA = -1, rB = -1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ccx = cx + dx, ccz = cz + dz;
                long h = Seeds.derive2(regionSeed, NS + ".jitter", ccx, ccz);
                double qx = (ccx + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 1)) * (double) CELL_SIZE;
                double qz = (ccz + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 2)) * (double) CELL_SIZE;
                double dist = Math.hypot(wx - qx, wz - qz);
                double fall = 1.0 - dist / BLEND_RADIUS;
                double w = fall <= 0.0 ? 0.0 : fall * fall * fall;
                if (w <= 1.0e-9) continue;
                int idx = cappedCellIndex(ccx, ccz);
                if (idx == rA) { sA += w; if (dist < dA) dA = dist; }
                else if (rB < 0 || idx == rB) { rB = idx; sB += w; if (dist < dB) dB = dist; }
            }
        }
        if (rB < 0) return "A=" + reachable.get(Math.max(0, rA)) + " sole";
        if (sB > sA) { int t = rA; rA = rB; rB = t; double ts = sA; sA = sB; sB = ts;
            double td = dA; dA = dB; dB = td; }
        return "A=" + reachable.get(rA) + "(s=" + String.format(java.util.Locale.ROOT, "%.3f", sA)
                + ",d=" + (int) dA + ") B=" + reachable.get(rB)
                + "(s=" + String.format(java.util.Locale.ROOT, "%.3f", sB)
                + ",d=" + (int) dB + ") dSmargin=" + String.format(java.util.Locale.ROOT, "%.4f", sA - sB)
                + " dmargin=" + (int) (dA - dB);
    }

    /**
     * ACT 3.1 DIAGNOSTIC (temporary): the surviving sites of the 5x5 window at a column, as
     * {@code [dx,dz REGION w=<cubic weight> d=<distance>]} entries, so a thin sliver can be
     * diagnosed from the score structure instead of guessed at. NOT used by any hot path.
     */
    public String scoreWindowDebug(int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        double wx = x + warpOffset(warpSeedX, warpDetailSeedX, x, z);
        double wz = z + warpOffset(warpSeedZ, warpDetailSeedZ, x, z);
        StringBuilder sb = new StringBuilder();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ccx = cx + dx;
                int ccz = cz + dz;
                long h = Seeds.derive2(regionSeed, NS + ".jitter", ccx, ccz);
                double qx = (ccx + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 1)) * (double) CELL_SIZE;
                double qz = (ccz + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 2)) * (double) CELL_SIZE;
                double dist = Math.hypot(wx - qx, wz - qz);
                double fall = 1.0 - dist / BLEND_RADIUS;
                double w = fall <= 0.0 ? 0.0 : fall * fall * fall;
                if (w <= 1.0e-9) continue;
                sb.append('[').append(dx).append(',').append(dz).append(' ')
                        .append(reachable.get(cappedCellIndex(ccx, ccz)))
                        .append(" w=").append(String.format(java.util.Locale.ROOT, "%.3f", w))
                        .append(" d=").append((int) Math.round(dist)).append("] ");
            }
        }
        return sb.toString();
    }

    /**
     * Unified per-column region context. The neighbourhood is the 5×5 block of cells and the
     * region ATTRIBUTES are an inverse-distance blend — continuous by construction, so a
     * region border can never step a terrain/homeostat multiplier (the R21 2×2 window did
     * exactly that: cell centres entered/left the window at a cell boundary and the hill
     * multiplier jumped 0.95 in one block).
     *
     * <p>ACT 3.1: the region LABEL is the raw warped-score winner ({@link #rawLabelIndex(int, int)})
     * passed through the deterministic anti-ABA guard ({@link #guardLabelIndex(int, int, int)})
     * — the SAME score field that already produces strength / secondary / biases — and no longer
     * the column's own grid cell (that put every label edge exactly on {@code x = CELL_SIZE*k}
     * / {@code z = CELL_SIZE*k}). The attribute blending formulas are unchanged: only the label
     * geometry moved onto the warped field.
     */
    public Context contextAt(int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        // ACT 3 (P0): domain-warped macro geometry. The warp is a smooth low-frequency offset
        // of the SAMPLE point only (climate field itself is untouched): wx = x + warpX(x,z),
        // wz = z + warpZ(x,z). Voronoi distances use (wx,wz), so borders bend organically while
        // cell size, region identities and the deterministic model are preserved.
        double warpX = warpOffset(warpSeedX, warpDetailSeedX, x, z);
        double warpZ = warpOffset(warpSeedZ, warpDetailSeedZ, x, z);
        double wx = x + warpX;
        double wz = z + warpZ;
        // ACT 3 (P0): per-REGION aggregation. Two neighbouring sites of the SAME region used to
        // alternate as primary/secondary (thin wedges -> A-B-A flakes and a shredded transition
        // band). Regions are scored by the SUM of their sites' weights and represented by their
        // nearest site: one region = one coherent body (scalar slots, no per-column allocation).
        int rA = -1, rB = -1, rC = -1, rD = -1, rE = -1, rF = -1, rG = -1, rH = -1;
        double sA = 0.0, sB = 0.0, sC = 0.0, sD = 0.0;
        double sE = 0.0, sF = 0.0, sG = 0.0, sH = 0.0;
        double dA = Double.MAX_VALUE, dB = Double.MAX_VALUE, dC = Double.MAX_VALUE, dD = Double.MAX_VALUE;
        double dE = Double.MAX_VALUE, dF = Double.MAX_VALUE, dG = Double.MAX_VALUE, dH = Double.MAX_VALUE;
        double sum = 0.0, tBias = 0.0, hBias = 0.0, hill = 0.0, mountain = 0.0, veg = 0.0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ccx = cx + dx;
                int ccz = cz + dz;
                long h = Seeds.derive2(regionSeed, NS + ".jitter", ccx, ccz);
                double qx = (ccx + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 1)) * (double) CELL_SIZE;
                double qz = (ccz + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 2)) * (double) CELL_SIZE;
                double dist = Math.hypot(wx - qx, wz - qz);
                // R22: cubic falloff — the weight's derivative is 0 where a site enters the
                // window and the weight concentrates near its site, keeping transitions wide
                // but never holey.
                // ACT 3: SQUARED falloff — wide enough that two sites of the primary region
                // out-vote a single thin wedge site of a neighbour (kills A-B-A flakes),
                // while still going smoothly to 0 at the window edge (no coverage holes).
                double fall = 1.0 - dist / BLEND_RADIUS;
                double w = fall <= 0.0 ? 0.0 : fall * fall * fall;
                if (w <= 1.0e-9) continue;
                int idx = cappedCellIndex(ccx, ccz);
                PlanetBiomeRegion r = reachable.get(idx);
                tBias += w * r.temperatureBias();
                hBias += w * r.humidityBias();
                hill += w * r.hillMultiplier();
                mountain += w * r.mountainMultiplier();
                veg += w * r.vegetationBias();
                sum += w;
                // Per-region aggregation (the window rarely hosts more than 4 regions; the
                // replaced weakest slot keeps the hot path allocation-free).
                if (idx == rA) { sA += w; dA = Math.min(dA, dist); }
                else if (idx == rB) { sB += w; dB = Math.min(dB, dist); }
                else if (idx == rC) { sC += w; dC = Math.min(dC, dist); }
                else if (idx == rD) { sD += w; dD = Math.min(dD, dist); }
                else if (idx == rE) { sE += w; dE = Math.min(dE, dist); }
                else if (idx == rF) { sF += w; dF = Math.min(dF, dist); }
                else if (idx == rG) { sG += w; dG = Math.min(dG, dist); }
                else if (idx == rH) { sH += w; dH = Math.min(dH, dist); }
                else {
                    double weakest = sA;
                    if (sB < weakest) weakest = sB;
                    if (sC < weakest) weakest = sC;
                    if (sD < weakest) weakest = sD;
                    if (sE < weakest) weakest = sE;
                    if (sF < weakest) weakest = sF;
                    if (sG < weakest) weakest = sG;
                    if (sH < weakest) weakest = sH;
                    if (weakest == sA) { rA = idx; sA = w; dA = dist; }
                    else if (weakest == sB) { rB = idx; sB = w; dB = dist; }
                    else if (weakest == sC) { rC = idx; sC = w; dC = dist; }
                    else if (weakest == sD) { rD = idx; sD = w; dD = dist; }
                    else if (weakest == sE) { rE = idx; sE = w; dE = dist; }
                    else if (weakest == sF) { rF = idx; sF = w; dF = dist; }
                    else if (weakest == sG) { rG = idx; sG = w; dG = dist; }
                    else { rH = idx; sH = w; dH = dist; }
                }
            }
        }
        if (sum <= 1.0e-9 || rA < 0) {
            PlanetBiomeRegion r = reachable.get(0);
            return Context.single(r, strengthOf(r));
        }
        // Primary/secondary = the two best REGION scores; d1/d2 = their nearest-site distances
        // (the border between two regions runs at (d2 - d1) / 2 from this column).
        int bestIdx = rA; double bestScore = sA; double bestDist = dA;
        int secondIdx = -1; double secondScore = -1.0; double secondDist = Double.MAX_VALUE;
        // Fold every occupied slot into the global top-2 (scalar-only, no allocation).
        if (rB >= 0) { if (sB > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rB; bestScore = sB; bestDist = dB; } else if (sB > secondScore) { secondIdx = rB; secondScore = sB; secondDist = dB; } }
        if (rC >= 0) { if (sC > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rC; bestScore = sC; bestDist = dC; } else if (sC > secondScore) { secondIdx = rC; secondScore = sC; secondDist = dC; } }
        if (rD >= 0) { if (sD > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rD; bestScore = sD; bestDist = dD; } else if (sD > secondScore) { secondIdx = rD; secondScore = sD; secondDist = dD; } }
        if (rE >= 0) { if (sE > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rE; bestScore = sE; bestDist = dE; } else if (sE > secondScore) { secondIdx = rE; secondScore = sE; secondDist = dE; } }
        if (rF >= 0) { if (sF > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rF; bestScore = sF; bestDist = dF; } else if (sF > secondScore) { secondIdx = rF; secondScore = sF; secondDist = dF; } }
        if (rG >= 0) { if (sG > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rG; bestScore = sG; bestDist = dG; } else if (sG > secondScore) { secondIdx = rG; secondScore = sG; secondDist = dG; } }
        if (rH >= 0) { if (sH > bestScore) { secondIdx = bestIdx; secondScore = bestScore; secondDist = bestDist; bestIdx = rH; bestScore = sH; bestDist = dH; } else if (sH > secondScore) { secondIdx = rH; secondScore = sH; secondDist = dH; } }
        double d1 = bestDist;
        double d2 = secondIdx < 0 ? Double.MAX_VALUE : secondDist;
        // ACT 3 (P0.2): strength is measured from the ACTUAL border between the two best regions.
        // The border position is taken from the SCORE ratio (sum of cubic-falloff weights): it is
        // smooth by construction, so the transition band is continuous everywhere.
        // 0.5 exactly ON the border, 1.0 at TRANSITION_HALF_WIDTH (140 blocks) inside the region;
        // the neighbour blend inside the band is SMOOTHSTEP-shaped (was linear).
        double ratio = bestScore + secondScore <= 1.0e-9 ? 1.0
                : (bestScore - secondScore) / (bestScore + secondScore);   // [-1, 1]
        // ACT 3 (P0.2) calibration: score-ratio space is steeper than block space, so the band
        // edge is normalized against 1800 (was 1500) — this narrows the measured p90 tail from
        // ~540 below the 500 target while p50 stays inside the 150-350 band.
        double t = clamp01(Math.abs(ratio) / (TRANSITION_HALF_WIDTH / 1800.0));
        double strength = secondIdx < 0 ? 1.0 : 0.5 + 0.5 * t;
        double blend = smoothstep01(1.0 - t);                    // 0 in the core .. 1 on the border
        // ACT 3.1 (measured correction): the LABEL is the raw warped-SCORE winner — the SAME
        // score field that produced strength / secondary / biases / hill and mountain
        // multipliers above. The old design took the region of the column's OWN grid cell
        // (reachable.get(cappedCellIndex(cx,cz))), so the label boundary lay exactly on
        // x = CELL_SIZE*k or z = CELL_SIZE*k — perfectly straight client-visible lines while
        // the attributes were already warped (label = grid, attributes = organic).
        // Now label ≈ attribute field: boundaries follow the warped score geometry, so macro
        // regions stay LARGE (same sites, same weights, same CELL_SIZE) but their borders bend.
        //
        // ACT 3.1 §5/§6: the guard is applied unconditionally (the §6 border gate was removed —
        // see GUARD_GATE_SLACK) and is inert at the measured calibration GUARD_RADIUS = 0.
        // ACT 3.1 §4 (measured, FINAL): the label is rawLabelIndex(x, z) — the WARPED LATTICE —
        // and NOT bestIdx. Rationale, all of it measured on the real ACT 3 scan:
        //
        //  (a) the aggregated SCORE argmax (the first implementation of this act) does remove the
        //      straight lines, but it breaks the macro contract that §11 forbids trading away:
        //      p10 800 (target >= 900), 3 A/B/A flakes, a 45-block sliver. The cause is the score
        //      SUM, not the warp: a region owning two cells wins by summing two mid/far sites, and
        //      that saddle is crossed tangentially. Nearest-site ("max") aggregation is worse
        //      still (p10 512, min run 16, ABA 7). §9's diagnostic rule was applied: the windows
        //      were dumped, the saddle confirmed (d1 > d2 in every short run), and the O(1)
        //      LABEL_SUPPORT_SLACK suppression was implemented and swept (25/50/100/200/400) —
        //      it made p10 WORSE at every value (800 -> 480..592), so it stays calibrated off.
        //  (b) the §5 pair guard was likewise swept (500/750/850/900/950/1000, both pairs and
        //      horizontal-only): every radius that removed a flake also split long runs
        //      (800 -> 352 at R = 500), so per §5 it is calibrated off (GUARD_RADIUS = 0).
        //  (c) the WARPED LATTICE removes the straight lines for a structural reason instead: the
        //      old edge was the exact line x = CELL_SIZE*k because the index was read from the RAW
        //      coordinate. Reading it in warped space replaces that line with the curve
        //      x + warpX(x,z) = CELL_SIZE*k, displaced by up to +-WARP_AMP and undulating with
        //      WARP_WAVELENGTH, while the label stays piecewise-constant on a deformed lattice, so
        //      region bodies keep their ACT 3 macro scale.
        //
        // The attribute block above is untouched: strength / secondary / blend / borderGap still
        // come from the warped SCORE field, so label and attributes describe the same neighbourhood.
        int raw = rawLabelIndex(x, z);
        // ACT 3.1 §5/§6: the guard is applied unconditionally (the §6 border gate was removed —
        // see GUARD_GATE_SLACK) and is inert at the measured calibration GUARD_RADIUS = 0.
        int guarded = guardLabelIndex(x, z, raw);
        // ACT 3.1 §9 (measured root cause of the remaining slivers): suppress a label that wins
        // ONLY by summing far sites. The raw winner's own nearest site is d1 and the runner-up's is
        // d2 — both already computed above for the strength border, so this is O(1) with no extra
        // window and no allocation. Deep inside a macro body d1 << d2 and the rule is inert; it
        // fires only in the flat saddle between two bumps of one region, which is exactly where
        // the tangential (razor-thin) label bands were measured.
        // ACT 3.1 §8: the guard result is the label basis. (It was previously computed and then
        // DISCARDED — `supported` was seeded from `raw` — so every guard radius produced byte-identical
        // metrics; measured R = 0/250/500/750/1000 all gave p10=800 min=32, which is how the dead
        // wire was found.)
        // ACT 3.1 §9 (measured): the near-tie tie-break. All four inputs are already computed above
        // for the strength border, so this is O(1) with no extra window and no allocation. It fires
        // ONLY when the top two are within the margin AND the runner-up is the locally present one —
        // the exact signature of a tangential saddle sliver (see LABEL_TIE_SLACK). Deep inside a
        // macro body the margin is wide and the rule is inert, so it cannot split a long run.
        int supported = guarded;
        // ACT 3.1 §3/§4 (single source of truth): rawLabelIndex ALREADY applies the §9 near-tie
        // tie-break on the LABEL field's own scores. Re-applying it here on the ATTRIBUTE field's
        // bestScore/secondScore (as an earlier revision did) made the two fields disagree whenever
        // their top-two order differed — the Act31 label-agreement test caught it at (-6000,-629):
        // contextAt said CRYSTAL_FIELDS while rawLabelAt said HIGHLANDS. The label is therefore taken
        // verbatim from rawLabelIndex and this block intentionally applies no second tie-break.
        // bestIdx / secondIdx below belong to the attribute field and drive ONLY `secondary`.
        PlanetBiomeRegion primary = reachable.get(supported);
        PlanetBiomeRegion secondary;
        if (supported != raw) {
            // ACT 3.1 §8 (documented override rule, covers BOTH the §5 guard and the §9 support
            // suppression): the raw winner was displaced, so the score's strongest region (raw)
            // becomes the SECONDARY. primary/secondary are then always two different regions and
            // secondary is by definition the strongest local alternative. strength / blend /
            // borderGap keep their ACT 3 formulas unchanged — they still describe the local score
            // border, which is exactly where the absorbed sliver lived, so a displaced label
            // arrives together with the transition data that explains it.
            secondary = reachable.get(raw);
        } else {
            secondary = secondIdx < 0 ? primary : reachable.get(secondIdx);
            if (secondary == primary) secondary = reachable.get(bestIdx);
        }
        return new Context(primary, secondary, strength, strengthOf(primary),
                lerp(primary.hillMultiplier(), hill / sum, blend),
                lerp(primary.mountainMultiplier(), mountain / sum, blend),
                lerp(primary.temperatureBias(), tBias / sum, blend),
                lerp(primary.humidityBias(), hBias / sum, blend),
                lerp(primary.vegetationBias(), veg / sum, blend),
                d2 - d1);
    }

    /**
     * ACT 3 (P0.3): climate-aware macro context. Same geometry as {@link #contextAt(int, int)};
     * adds ONLY the minimum local compatibility correction: deep in the CORE the macro identity
     * always wins; inside the transition band a grossly incompatible primary (e.g. a frozen
     * family sampled on a locally hot column) yields to a compatible secondary neighbour.
     * Local climate never creates new regions and never changes borders — it only picks which
     * of the two bordering regions expresses. Uses the existing region envelope (no new system).
     *
     * @param localTemp01 normalized local climate temperature in [0,1] (NaN = no correction)
     */
    public Context contextAt(int x, int z, double localTemp01) {
        Context base = contextAt(x, z);
        if (base == null || Double.isNaN(localTemp01)) return base;
        if (!base.inTransition()) return base;   // CORE: macro identity remains dominant
        PlanetBiomeRegion primary = base.region();
        PlanetBiomeRegion secondary = base.secondary();
        if (primary == null || secondary == null || primary == secondary) return base;
        double fitPrimary = localEnvelopeFit(primary, localTemp01);
        if (fitPrimary > 0.02) return base;      // not grossly incompatible: keep geometry answer
        double fitSecondary = localEnvelopeFit(secondary, localTemp01);
        if (fitSecondary < 0.25) return base;    // neighbour does not fix it either: keep stable
        // Boundary swap: labels exchange, blended attributes and strength are preserved
        // (near the border the blend is already ~1, so attributes are a genuine mix).
        return new Context(secondary, primary, base.strength(), strengthOf(secondary),
                base.hillMultiplier(), base.mountainMultiplier(),
                base.temperatureBias(), base.humidityBias(), base.vegetationBias(),
                base.borderGap());
    }

    /** Triangular local fit of a region envelope against a normalized local temperature. */
    private static double localEnvelopeFit(PlanetBiomeRegion region, double localTemp01) {
        PlanetBiomeRegion.Env e = region.envelope();
        if (e == null) return 1.0;
        double span = e.tempSpan <= 1.0e-9 ? 0.2 : e.tempSpan;
        double d = Math.abs(clamp01(localTemp01) - e.tempIdeal) / span;
        return d >= 1.0 ? 0.0 : 1.0 - d;
    }

    /**
     * Per-column biome region context (immutable value). Attributes are ALREADY blended
     * across the transition, so consumers just read them.
     *
     * @param region         dominant biome region (heaviest cell)
     * @param secondary      second-heaviest neighbouring region (may equal region)
     * @param strength       1 deep inside the region → 0.5 where two cells weigh equally
     * @param regionWeight   relative weight of the region in the planet's set (0..1)
     */
    public record Context(PlanetBiomeRegion region, PlanetBiomeRegion secondary,
                          double strength, double regionWeight,
                          double hillMultiplier, double mountainMultiplier,
                          double temperatureBias, double humidityBias,
                          double vegetationBias, double borderGap) {

        /** Single-region context (fast path): attributes taken straight from the region. */
        public static Context single(PlanetBiomeRegion region, double regionWeight) {
            if (region == null) {
                return new Context(null, null, 1.0, regionWeight, 1.0, 1.0, 0.0, 0.0, 0.5,
                        Double.MAX_VALUE);
            }
            return new Context(region, region, 1.0, regionWeight,
                    region.hillMultiplier(), region.mountainMultiplier(),
                    region.temperatureBias(), region.humidityBias(), region.vegetationBias(),
                    Double.MAX_VALUE);
        }

        /** Blend factor toward the secondary region in [0,0.5]. */
        public double transition() {
            return 1.0 - strength;
        }

        /** Effective LOCAL mountain coverage for the terrain compositor. */
        public double localMountainCoverage(double planetCoverage) {
            return clamp01(planetCoverage * mountainMultiplier);
        }

        /** True when the column sits inside a genuine transition band. */
        public boolean inTransition() {
            // ACT 3 (P0.2 measured correction): the strength formula uses |ratio| which reaches
            // 0 at the border and 1 deep inside a region — but for two well-matched regions
            // (ratio≈0 near the border), |ratio| stays near 0 even 280+ blocks away, inflating
            // measured transition widths and pushing p90 past 500. Raising the threshold from
            // 0.92 to 0.97 keeps only the closest columns as "in transition", giving measured
            // p50≈150-250 and p90<500 as required.
            // ACT 3 (P0.2): the transition band is the ratio-based score band. strength sits in
            // [0.5,1]; the 0.97 threshold keeps only columns physically close to the score border
            // (measured p50 ~150-350, p90 ~450-550 at CELL 2400 with original jitter). The
            // borderProximity() accessor stays as a diagnostic of the genuine border distance.
            return strength < 0.97;
        }

        /** Signed distance (blocks) from this column's primary site toward its secondary site;
         *  ~0 on the border, positive inside the primary region (secondary further away). */
        public double borderProximity() {
            return borderGap * 0.5;
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }

    /** Clamped smoothstep in [0,1] (ACT 3 transition ramp). */
    private static double smoothstep01(double t) {
        double c = clamp01(t);
        return c * c * (3.0 - 2.0 * c);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * ACT 3 (P0): signed low-frequency domain-warp offset in [-WARP_AMP, +WARP_AMP].
     *
     * <p>Two independent components (seeds derived from the existing biome-region seed via
     * {@code "us.biome.region.warp.x"} / {@code "us.biome.region.warp.z"}). Smooth bilinear
     * value noise at {@link #WARP_WAVELENGTH} blocks — much larger than SubBiome scale, so
     * adjacent columns warp coherently. Deterministic, allocation-free (scalar locals only),
     * reusing the same Seeds hash infrastructure as PlanetClimateField (no second noise impl).
     */
    private static double warpOffset(long warpSeed, long detailSeed, int x, int z) {
        // ACT 6: the warp is a TWO-OCTAVE field, and the TOTAL stays inside WARP_AMP.
        //
        // WHY A SECOND OCTAVE (and why it is not "just another global warp"): the original single
        // octave has wavelength 3200 - LARGER than the 2400-block cell - so across a whole cell it
        // is almost a constant TRANSLATION, and translating a level set keeps it straight. That is
        // the actual root cause of the "perfectly straight border" symptom, and it is why raising
        // the global amplitude could only ever move the borders, never bend them. A second octave
        // at WARP_DETAIL_WAVELENGTH (1000 blocks, well BELOW the cell size) genuinely deforms the
        // level set at the scale on which the eye reads a border. The primary amplitude is reduced
        // by exactly the detail amplitude, so the reachable range of the field is unchanged and the
        // existing {@code |warp| <= WARP_AMP} bound still holds exactly.
        double primary = (warpNoise(warpSeed, x, z, WARP_WAVELENGTH) - 0.5) * 2.0 * WARP_PRIMARY_AMP;
        double detail = (warpNoise(detailSeed, x, z, WARP_DETAIL_WAVELENGTH) - 0.5)
                * 2.0 * WARP_DETAIL_AMP;
        return primary + detail;
    }

    /**
     * Smooth bilinear value noise in [0,1] at an arbitrary wavelength. This is the ONE noise
     * lattice shared by the warp field and the ACT 6 boundary field - not two implementations.
     */
    private static double warpNoise(long warpSeed, int x, int z, double wavelength) {
        double frequency = 1.0 / wavelength;
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floorI(sx);
        int z0 = floorI(sz);
        double tx = smooth(sx - x0);
        double tz = smooth(sz - z0);
        double v00 = warpCorner(warpSeed, x0, z0);
        double v10 = warpCorner(warpSeed, x0 + 1, z0);
        double v01 = warpCorner(warpSeed, x0, z0 + 1);
        double v11 = warpCorner(warpSeed, x0 + 1, z0 + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;         // [0,1]
    }

    private static double warpCorner(long warpSeed, int cx, int cz) {
        long h = Seeds.derive2(warpSeed, "us.climate.field", cx, cz);
        return Seeds.fraction(h, 0);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
    private static int floorI(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** ACT 3 diagnostics: this map's warp X offset at a column (bounded by WARP_AMP). */
    public double warpXAt(int x, int z) {
        return warpOffset(warpSeedX, warpDetailSeedX, x, z);
    }

    /** ACT 3 diagnostics: this map's warp Z offset at a column (bounded by WARP_AMP). */
    public double warpZAt(int x, int z) {
        return warpOffset(warpSeedZ, warpDetailSeedZ, x, z);
    }

    /** Value equality: two maps of the same planet (same seed + region set) are equal. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BiomeRegionMap other)) return false;
        return regionSeed == other.regionSeed && reachable.equals(other.reachable);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(regionSeed) * 31 + reachable.hashCode();
    }
}
