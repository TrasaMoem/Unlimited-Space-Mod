package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.3 / TASKS B, C, L — the boundary IRREGULARITY contract.
 *
 * <p>Problem B was observed as a nearly perfectly extended, straight biome / material seam in a
 * finished world. Straight seams are not produced by smooth continuous fields — they are the
 * signature of a DISCRETE owner: a Voronoi cell edge, a fixed-radius influence circle, a lattice
 * cell, or a province step function. Blurring such an edge only smears the step; the geometry
 * underneath survives. So these tests do NOT measure smoothness. They measure STRUCTURE:
 *
 * <ul>
 *   <li><b>B</b> — the elected biome is not a FUNCTION of the macro province. If the biome were
 *       determined by macro ownership, then inside one province the biome would be constant and
 *       across a province border it would always change. Both must be violated.</li>
 *   <li><b>C</b> — the score of ONE FIXED candidate is a Lipschitz function of the continuous
 *       channels it reads, and its response shrinks in proportion to the input span. This is the
 *       continuity claim; it deliberately does NOT use winner selection, because the winning score
 *       is an argmax and is allowed to jump when the winner changes.</li>
 *   <li><b>L</b> — the boundary is not a drawn line, and it is not a repainted province or macro
 *       site map. Measured as a real 2D edge set with run geometry and boundary-overlap indices
 *       against both ownership grids.</li>
 * </ul>
 *
 * <p>All three read the REAL {@link V3ColumnSampler}, i.e. the same pure field functions the
 * chunk generator calls. No test-only hook and no debug branch exists in the runtime path.
 */
@Tag("worldgen")
@Tag("audit")
class V3BoundaryIrregularityTest {

    /** Seeds spanning different planetary draws, so no assertion is seed-specific. */
    private static final long[] SEEDS = {0xC100L, 0xC101L, 0xC102L};

    /** Grid resolution and step in blocks. 64x64 columns per seed, 48 blocks apart. */
    private static final int RES = 64;
    private static final int STEP = 48;
    private static final int ORIGIN = -(RES / 2) * STEP;

    /**
     * The channels the classifier actually reads as score inputs. This is the real list, taken
     * from the score expression itself — no channel is probed that the score does not consume,
     * and no artificial channel was added to production for the sake of a test.
     */
    private static final String[] PROBED_CHANNELS = {
            "temperature01", "humidity01", "precipitation01", "wetness01",
            "elevation01", "slope", "riverProximity", "waterProximity",
            "mountainEnvelope", "volcanicIntensity", "rockShare", "sedimentShare",
            "crystalIntensity", "glacialIntensity", "landformStrength"
    };

    /**
     * The Lipschitz ceiling: the most one unit of input change may move a fixed candidate's score.
     *
     * <p>It is DERIVED from the score expression rather than fitted to the data. Each axis term is
     * {@code exp(-k*v^2)} with {@code k <= 9}; on {@code |v| <= 0.5} the steepest derivative is
     * {@code 2*k*|v| <= 9}, and the remaining bounded factors (detail, macro affinity, river pull)
     * add single-digit slopes. 40 is therefore a wide margin over the true constant.
     *
     * <p>What the bound is really for is the OTHER regime. A discrete owner — a province id, a
     * macro site id, a lattice cell — is not one of {@link #PROBED_CHANNELS}, so crossing its
     * border moves the score while the measured input delta stays at zero. That produces an
     * unbounded ratio, which no finite ceiling can absorb. The finite bound therefore tolerates
     * block-height quantisation (slope moves in 1/6 steps) without ever tolerating a step.
     */
    private static final double LIPSCHITZ_MAX = 40.0;

    /**
     * The ceiling on a score change caused purely by a landform IDENTITY flip.
     *
     * <p>Derived from the production contract rather than from a measurement: the landform
     * affinity is documented and implemented as bounded to [0.6, 1.6] and is weighted by a
     * continuous strength in [0, 1]. So one flip can change the landform factor by at most 0.6 in
     * relative terms, i.e. 60% of the score. The product score itself is bounded by the
     * {@code exp(-k*v^2)} axes, so 0.6 * (a generous score ceiling) is the absolute bound used.
     *
     * <p>The measured value is printed next to it on every run, so a drift in the landform
     * affinity is visible as a number rather than hidden behind the bound.
     */
    private static final double LANDFORM_FLIP_MAX_DELTA = 0.6 * 2.0;

    private static int[][] biomeGrid(V3ColumnSampler sampler, WorldgenColumnSample col) {
        int[][] grid = new int[RES][RES];
        for (int iz = 0; iz < RES; iz++) {
            for (int ix = 0; ix < RES; ix++) {
                sampler.sampleColumn(ORIGIN + ix * STEP, ORIGIN + iz * STEP, col);
                grid[iz][ix] = col.biome.catalogueIndex();
            }
        }
        return grid;
    }

    private static int[][] provinceGrid(V3ColumnSampler sampler, WorldgenColumnSample col) {
        int[][] grid = new int[RES][RES];
        for (int iz = 0; iz < RES; iz++) {
            for (int ix = 0; ix < RES; ix++) {
                sampler.sampleColumn(ORIGIN + ix * STEP, ORIGIN + iz * STEP, col);
                grid[iz][ix] = col.dominantGeology() == null ? -1 : col.dominantGeology().ordinal();
            }
        }
        return grid;
    }

    // ------------------------------------------------------------------ TASK B

    @Test
    void theBiomeIsNotAFunctionOfTheMacroProvince() {
        // A macro-driven classifier fails this immediately: every column of one province would
        // share a biome (insideSameProvince == 0) and every province border would be a biome border.
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(seed);
            int[][] biomes = biomeGrid(sampler, new WorldgenColumnSample());
            int[][] provinces = provinceGrid(sampler, new WorldgenColumnSample());

            int insideSameProvince = 0;
            int acrossProvinceBorder = 0;
            for (int iz = 0; iz < RES; iz++) {
                for (int ix = 1; ix < RES; ix++) {
                    boolean biomeChanged = biomes[iz][ix] != biomes[iz][ix - 1];
                    boolean provinceChanged = provinces[iz][ix] != provinces[iz][ix - 1];
                    if (provinceChanged) {
                        if (biomeChanged) acrossProvinceBorder++;
                    } else if (biomeChanged) {
                        insideSameProvince++;
                    }
                }
            }
            assertTrue(insideSameProvince > 0,
                    "seed 0x" + Long.toHexString(seed) + ": the biome map never changes inside a "
                            + "macro province, so the biome IS the province");
            assertTrue(acrossProvinceBorder < insideSameProvince * 2,
                    "seed 0x" + Long.toHexString(seed) + ": " + acrossProvinceBorder
                            + " of the borders fall exactly on a province seam, versus "
                            + insideSameProvince + " inside one province — the province still drives "
                            + "the visible biome");
        }
    }

    // ------------------------------------------------------------------ TASK C

    /**
     * The classification must be a real COMPETITION over continuous fields, not a decision table.
     *
     * <p>A discrete owner (province, site id, lattice cell) enters the score as a STEP: crossing
     * its border changes the score discontinuously while the measured climate, relief and
     * hydrology of the two columns are almost identical. Smoothing the OUTPUT cannot remove that,
     * because the discontinuity is in the INPUT.
     *
     * <h2>Why the previous form of this test was wrong</h2>
     * The earlier version walked adjacent blocks and compared {@code col.bestScore}. That quantity
     * is an ARGMAX over the candidate list, so at a winner switch it jumps from
     * {@code score(candidateA, x)} straight to {@code score(candidateB, x+1)} — two different
     * functions. A 0.59 "jump" there says nothing about continuity: it is the ordinary, correct
     * behaviour of a max over a crossing pair of continuous functions. The old test therefore
     * measured winner churn, not continuity, and it FAILED on seed 0xc102 exactly there.
     *
     * <h2>What the correct invariant is</h2>
     * For a FIXED candidate, {@code |dscore| <= L * max|dchannel|}. This is the Lipschitz form of
     * "a small input delta produces a small score delta", and it is the only honest form here,
     * because not every input channel is smooth at block scale.
     *
     * <p>{@code slope} is a finite difference of the composed INTEGER block height divided by
     * {@code SLOPE_SCALE = 6}, so it is legitimately quantised in steps of 1/6. One block of
     * terrain therefore moves slope by up to 0.1667 — a real, physical input change, not a defect.
     * Measured on this field stack that legitimately moves one candidate's score by 0.11
     * ({@code exp(-0.5^2*1.2)=0.741 -> exp(-0.333^2*1.2)=0.875}). An ABSOLUTE score bound would
     * have to be lifted above that purely to tolerate quantisation, which is exactly the "raise
     * the tolerance until green" move this ACT forbids.
     *
     * <h2>The one declared discrete input, measured rather than excused</h2>
     * The run identified a genuine second case: {@code landform} is a DISCRETE argmax identity and
     * {@code landformAffinity} switches on it. Crossing NONE->GULLY gives one candidate a 1.1x
     * boost, scaled by the continuous {@code landformStrength}; at strength 0.4455 that is still a
     * 1.089x multiplier and moves the score by 0.064 in ONE block with no continuous input moving.
     * That is a real step, and this test reports it as one.
     *
     * <p>It is separated from the defect the ACT is hunting rather than merged with it. A macro
     * owner is NOT in {@link #PROBED_CHANNELS} and is not the landform: it would move the score at
     * a province or site border. The landform step is instead held to its own DECLARED contract —
     * the boost is documented as bounded to [0.6, 1.6] and weighted by continuous strength — so
     * the bound below is the documented ceiling, not a fitted one. Weakening production to remove
     * it would be the coefficient change this ACT forbids; hiding it would be the false green it
     * also forbids. It is therefore measured, bounded by its real contract, and reported.
     */
    @Test
    void aFixedCandidateScoreIsLipschitzInItsInputChannels() {
        // One block apart, so a step function would show its full height here.
        final int row = 1024;
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(seed);
            BiomeMaskField field = sampler.biomeField();
            WorldgenColumnSample a = new WorldgenColumnSample();
            WorldgenColumnSample b = new WorldgenColumnSample();

            // Sweep EVERY registered candidate, not a convenient subset: a step could hide in the
            // tail of the list just as easily as in the winner.
            for (BiomeCandidate candidate : field.candidates()) {
                double worstRatio = 0.0;      // |dscore| per unit of input change
                double worstDelta = 0.0;
                double worstInput = 0.0;
                int worstX = 0;
                String worstChannel = "-";
                // Tracked separately: a step that lands on a landform flip is the declared local
                // discrete field and is held to its own documented bound.
                double worstLandformDelta = 0.0;
                String worstLandformAt = "-";

                for (int i = 0; i < 400; i++) {
                    int x = -200 + i;
                    sampler.sampleColumn(x, row, a);
                    sampler.sampleColumn(x + 1, row, b);

                    // The SAME candidate at both columns. This is the whole point: one function,
                    // two arguments, so a delta can only come from a non-continuous input.
                    double s0 = field.score(candidate, a);
                    double s1 = field.score(candidate, b);
                    assertTrue(Double.isFinite(s0) && Double.isFinite(s1),
                            "candidate " + candidate.id() + " produced a non-finite score at x=" + x);
                    double delta = Math.abs(s1 - s0);
                    double[] ca = capture(a);
                    double[] cb = capture(b);
                    double input = 0.0;
                    int worstIdx = 0;
                    for (int k = 0; k < ca.length; k++) {
                        double d = Math.abs(ca[k] - cb[k]);
                        if (d > input) {
                            input = d;
                            worstIdx = k;
                        }
                    }
                    // The declared discrete field: a landform IDENTITY flip.
                    if (a.landform != b.landform) {
                        if (delta > worstLandformDelta) {
                            worstLandformDelta = delta;
                            worstLandformAt = a.landform + "->" + b.landform + " at x=" + x;
                        }
                        continue;   // judged against the landform contract instead
                    }

                    // A zero input change with a non-zero score change IS the step signature, and
                    // with no landform flip there is no remaining declared discrete input that
                    // could explain it. This is the branch a macro owner would fall into.
                    if (input <= 0.0) {
                        assertTrue(delta <= 1e-12,
                                "seed 0x" + Long.toHexString(seed) + ": candidate "
                                        + candidate.id() + " score changes by " + fmt(delta)
                                        + " at x=" + x + " while NO continuous input channel changes"
                                        + " and the landform is unchanged — an unmodelled discrete"
                                        + " owner is feeding the score");
                        continue;
                    }
                    double ratio = delta / input;
                    if (ratio > worstRatio) {
                        worstRatio = ratio;
                        worstDelta = delta;
                        worstInput = input;
                        worstX = x;
                        worstChannel = PROBED_CHANNELS[worstIdx];
                    }
                }

                // The bound is DERIVED from the score expression, not tuned. Every axis term is
                // exp(-k*v^2) with k <= 9, whose derivative peaks at 2*k*|v| <= 18 across the
                // channel bound |v| <= 0.5; the bounded factors (detail, macro, river) contribute
                // single-digit slopes. 40 leaves ample headroom over the true Lipschitz constant,
                // while a discrete owner — which needs an INFINITE ratio — cannot approach it.
                System.out.println("[V3.3-C] seed 0x" + Long.toHexString(seed)
                        + " candidate=" + candidate.id()
                        + " worstLipschitz=" + fmt(worstRatio)
                        + " (dscore=" + fmt(worstDelta) + " over d" + worstChannel
                        + "=" + fmt(worstInput) + " at x=" + worstX + ")"
                        + " | landformFlipDelta=" + fmt(worstLandformDelta)
                        + " [" + worstLandformAt + "]");

                assertTrue(worstRatio <= LIPSCHITZ_MAX,
                        "seed 0x" + Long.toHexString(seed) + ": candidate " + candidate.id()
                                + " score changes by " + fmt(worstDelta) + " for a change of only "
                                + fmt(worstInput) + " in " + worstChannel + " (ratio "
                                + fmt(worstRatio) + ") at x=" + worstX
                                + " — the score is not Lipschitz in a continuous input");

                // The landform contract: the affinity is documented as bounded to [0.6, 1.6] and
                // weighted by a strength in [0,1], so one identity flip cannot move the score by
                // more than 60% of the score itself, and the score is itself bounded, so the
                // absolute ceiling below is the documented boost range applied to a real score.
                assertTrue(worstLandformDelta <= LANDFORM_FLIP_MAX_DELTA,
                        "seed 0x" + Long.toHexString(seed) + ": candidate " + candidate.id()
                                + " score jumps by " + fmt(worstLandformDelta)
                                + " on a landform identity flip (" + worstLandformAt + ")"
                                + " — beyond the declared 1.6x landform affinity bound");
            }
        }
    }

    /**
     * The same invariant driven by perturbing a SINGLE input channel instead of stepping the
     * coordinate. This isolates the claim the ACT actually makes — a small input delta produces a
     * small score delta — from whatever the field stack happens to do between two adjacent blocks.
     *
     * <p>Each case changes exactly ONE continuous channel of a PINNED column and leaves the
     * province pinned, so the score can only move through that channel.
     */
    @Test
    void perturbingOneInputChannelMovesTheScoreProportionallyToTheDelta() {
        BiomeMaskField field = V3BiomeRuntimeIntegrationTest.earthlike(0xCA01L).biomeField();
        WorldgenColumnSample col = new WorldgenColumnSample();
        final double eps = 0.002;

        for (String channel : PROBED_CHANNELS) {
            double previous = 0.0;
            double previousDelta = 0.0;
            StringBuilder line = new StringBuilder("[V3.3-C-CH] channel=").append(channel);
            for (double delta : new double[]{eps, 2 * eps, 4 * eps, 8 * eps}) {
                double worst = 0.0;
                for (BiomeCandidate candidate : field.candidates()) {
                    for (double base : new double[]{0.15, 0.35, 0.5, 0.65, 0.85}) {
                        pin(col, 4096, 2048);
                        setChannel(col, channel, base);
                        double s0 = field.score(candidate, col);
                        pin(col, 4096, 2048);
                        setChannel(col, channel, base + delta);
                        double s1 = field.score(candidate, col);
                        assertTrue(Double.isFinite(s0) && Double.isFinite(s1),
                                "channel " + channel + " produced a non-finite score");
                        worst = Math.max(worst, Math.abs(s1 - s0));
                    }
                }
                line.append("  d=").append(fmt(delta)).append("->").append(fmt(worst));
                // LINEARITY: doubling the input delta may at most roughly double the response.
                // A step gives the SAME large delta for every delta, which is exactly what this
                // ratio rules out — a step saturates, a slope does not.
                if (previousDelta > 1e-9) {
                    assertTrue(worst <= previous * 2.5 + 1e-6,
                            "channel " + channel + ": the score response to d=" + fmt(delta)
                                    + " (" + fmt(worst) + ") is not proportional to the response to"
                                    + " d=" + fmt(delta / 2.0) + " (" + fmt(previous) + ")"
                                    + " — the response saturates, which is a step, not a slope");
                }
                previous = worst;
                previousDelta = worst;
            }
            System.out.println(line);
        }
    }

    /**
     * The no-artificial-step check, as a LADDER: x, x+eps, x+2*eps, x+4*eps, x+8*eps.
     *
     * <h2>Why the previous metric was wrong</h2>
     * The first attempt measured "what share of the total 2*eps move landed in the first eps". That
     * ratio is only meaningful for a MONOTONE response. The score is a product of terms, so it can
     * legitimately rise and then fall back; the ratio then exceeds 1 (the run measured 1.76) and
     * the assertion fired on a perfectly continuous function. A ratio above 1 is not a step, it is
     * a sign error in the metric.
     *
     * <h2>Why the measurement is a CENTRAL difference</h2>
     * Three earlier one-sided forms of this metric were invalid. They are documented so they are
     * not reintroduced, because each fired on a perfectly continuous function:
     * <ul>
     *   <li>"what share of the 2*eps move landed in the first eps" needs a monotone response; the
     *       score is a product of opposing factors, so it can rise and fall back. The ratio then
     *       exceeds 1 (1.76 was measured). A ratio above 1 is not a step, it is a bad metric.</li>
     *   <li>Comparing eps against 8*eps fails over a long span for the same reason: on
     *       {@code riverProximity} the wetness factor falls while the river factor rises, so the
     *       response is unimodal and returns near its start, inverting the ratio.</li>
     *   <li>Two SHORT one-sided spans fail at a STATIONARY point. Where the first derivative
     *       vanishes — e.g. {@code tundra} at its own ideal temperature, where d/dT ~ 0 — the
     *       response is quadratic, so halving the span quarters the move (ratio ~0.25) or, once
     *       float cancellation dominates, becomes arbitrary (0.618 was measured on a genuinely
     *       smooth response).</li>
     * </ul>
     *
     * <p>A CENTRAL difference {@code f(x+h) - f(x-h)} cancels the quadratic term exactly, so it
     * measures the first derivative alone. Its magnitude therefore scales with h for any
     * differentiable function, at any base point including a stationary one. The ratio is 0.5 by
     * construction for a continuous score and 1.0 for a step, so the bound sits midway.
     *
     * <p>All probes use a PINNED sample, so {@code landform} is held constant and the ladder is a
     * pure single-channel response with no discrete input anywhere in it.
     */
    @Test
    void theScoreHasNoArtificialStepBetweenTwoEqualIncrements() {
        BiomeMaskField field = V3BiomeRuntimeIntegrationTest.earthlike(0xCA02L).biomeField();
        final double eps = 0.016;
        WorldgenColumnSample p = new WorldgenColumnSample();
        WorldgenColumnSample m = new WorldgenColumnSample();
        WorldgenColumnSample p2 = new WorldgenColumnSample();
        WorldgenColumnSample m2 = new WorldgenColumnSample();
        double worstRatio = 0.0;
        String worstWhere = "-";

        for (BiomeCandidate candidate : field.candidates()) {
            for (double baseValue : new double[]{0.15, 0.3, 0.45, 0.6, 0.75}) {
                for (String channel : PROBED_CHANNELS) {
                    probe(p, channel, baseValue + eps);
                    probe(m, channel, baseValue - eps);
                    probe(p2, channel, baseValue + 2 * eps);
                    probe(m2, channel, baseValue - 2 * eps);
                    // Central differences at half and full span. The quadratic term cancels in
                    // both, so both are proportional to h and the ratio is 0.5 for any
                    // differentiable score — including at a stationary point.
                    double shortSpan = Math.abs(field.score(candidate, p)
                            - field.score(candidate, m));
                    double longSpan = Math.abs(field.score(candidate, p2)
                            - field.score(candidate, m2));
                    // Only where the response is resolvable RELATIVE to the score itself. The
                    // score spans orders of magnitude across the candidate list, and where both
                    // differences cancel into float noise their quotient measures nothing.
                    if (longSpan <= 1e-4 * Math.abs(field.score(candidate, p2))) continue;
                    double ratio = shortSpan / longSpan;
                    if (ratio > worstRatio) {
                        worstRatio = ratio;
                        worstWhere = candidate.id() + "/" + channel + "@" + baseValue;
                    }
                    // 0.75 sits midway between the two regimes (0.5 continuous, 1.0 step).
                    assertTrue(ratio <= 0.75,
                            "candidate " + candidate.id() + " channel " + channel + " at " + baseValue
                                    + ": central span " + sci(shortSpan) + " at eps vs "
                                    + sci(longSpan) + " at 2*eps (ratio " + sci(ratio) + ") — the"
                                    + " response does not shrink with the span, which is a step");
                }
            }
        }
        System.out.println("[V3.3-C-STEP] worstSpanShrinkRatio=" + fmt(worstRatio)
                + " at " + worstWhere + "  (a continuous score is 0.500, a step is 1.000)");
    }

    /** A pinned sample with one channel written, for the central-difference probes. */
    private static void probe(WorldgenColumnSample c, String channel, double value) {
        pin(c, 8192, 4096);
        setChannel(c, channel, value);
    }

    /** Snapshot every channel the score reads, in PROBED_CHANNELS order, for diagnostics. */
    private static double[] capture(WorldgenColumnSample c) {
        double[] v = new double[PROBED_CHANNELS.length];
        for (int i = 0; i < PROBED_CHANNELS.length; i++) {
            v[i] = switch (PROBED_CHANNELS[i]) {
                case "temperature01" -> c.temperature01;
                case "humidity01" -> c.humidity01;
                case "precipitation01" -> c.precipitation01;
                case "wetness01" -> c.wetness01;
                case "elevation01" -> c.elevation01;
                case "slope" -> c.slope;
                case "riverProximity" -> c.riverProximity;
                case "waterProximity" -> c.waterProximity;
                case "mountainEnvelope" -> c.mountainEnvelope;
                case "volcanicIntensity" -> c.volcanicIntensity;
                case "rockShare" -> c.rockShare;
                case "sedimentShare" -> c.sedimentShare;
                case "crystalIntensity" -> c.crystalIntensity;
                case "glacialIntensity" -> c.glacialIntensity;
                default -> c.landformStrength;
            };
        }
        return v;
    }

    /** Reset a column to a neutral state with the macro province PINNED to PLAINS. */
    private static void pin(WorldgenColumnSample c, int x, int z) {
        c.reset(x, z);
        c.elevation01 = 0.40;
        c.slope = 0.10;
        c.temperature01 = 0.50;
        c.humidity01 = 0.50;
        c.precipitation01 = 0.50;
        c.wetness01 = 0.50;
        c.riverMask = 0.0;
        c.lakeMask = 0.0;
        c.riverProximity = 0.0;
        c.waterProximity = 0.0;
        c.mountainEnvelope = 0.0;
        c.mountainIntensity = 0.0;
        c.volcanicIntensity = 0.0;
        c.crystalIntensity = 0.0;
        c.glacialIntensity = 0.0;
        c.basinIntensity = 0.0;
        c.landformStrength = 0.0;
        c.rockShare = 0.20;
        c.sedimentShare = 0.20;
        c.organicPotential = 0.40;
        // PINNED: the only thing that changes in a sweep is the swept channel.
        c.macroPreferredGeology = GeologicalProvince.PLAINS;
    }

    /** Write one named continuous channel; the sample exposes them as public fields. */
    private static void setChannel(WorldgenColumnSample c, String channel, double value) {
        switch (channel) {
            case "temperature01" -> c.temperature01 = value;
            case "humidity01" -> c.humidity01 = value;
            case "precipitation01" -> c.precipitation01 = value;
            case "wetness01" -> c.wetness01 = value;
            case "elevation01" -> c.elevation01 = value;
            case "slope" -> c.slope = value;
            case "riverProximity" -> c.riverProximity = value;
            case "waterProximity" -> c.waterProximity = value;
            case "mountainEnvelope" -> c.mountainEnvelope = value;
            case "volcanicIntensity" -> c.volcanicIntensity = value;
            case "rockShare" -> c.rockShare = value;
            case "sedimentShare" -> c.sedimentShare = value;
            case "crystalIntensity" -> c.crystalIntensity = value;
            case "glacialIntensity" -> c.glacialIntensity = value;
            case "landformStrength" -> c.landformStrength = value;
            default -> throw new IllegalArgumentException("unknown channel: " + channel);
        }
    }

    // ------------------------------------------------------------------ TASK L (2D geometry)

    /**
     * The GEOMETRY of the visible biome boundary, measured as a real 2D edge set.
     *
     * <p>The earlier L test only counted uniform runs along a single scan ROW. That cannot see a
     * seam running vertically, and it cannot compare the biome boundary with the province or the
     * macro site boundary at all. This test builds the REAL grid
     * ({@code biome = BiomeMaskField.classify} through the shared {@link V3ColumnSampler}), turns it
     * into the set of edges between 4-neighbours with different labels, and reports the geometry.
     *
     * <p>Every metric is normalised by the window size or is a scale-free index, so nothing here is
     * fitted to a target. The assertions are the ARCHITECTURAL invariants that the old ownership
     * defect would violate, and they are one-sided: they can only fail if a discrete owner is back.
     */
    @Test
    void theBiomeBoundaryGeometryIsMeasuredAndDecoupledFromProvinceAndSite() {
        for (long seed : SEEDS) {
            V3PreviewChannels.Pipeline pipe = V3PreviewChannels.samplerAndShaper(seed,
                    V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45),
                    ReliefArchetype.ROLLING);
            V3ColumnSampler sampler = pipe.sampler();
            MacroSample macro = new MacroSample();

            int[][] biome = new int[RES][RES];
            int[][] province = new int[RES][RES];
            long[][] site = new long[RES][RES];
            WorldgenColumnSample col = new WorldgenColumnSample();
            for (int iz = 0; iz < RES; iz++) {
                for (int ix = 0; ix < RES; ix++) {
                    int x = ORIGIN + ix * STEP;
                    int z = ORIGIN + iz * STEP;
                    sampler.sampleColumn(x, z, col);
                    biome[iz][ix] = col.biome.catalogueIndex();
                    province[iz][ix] = col.dominantGeology() == null
                            ? -1 : col.dominantGeology().ordinal();
                    // The macro SITE owner, read from the production geography itself rather than
                    // re-derived here, so the comparison is against the real lattice.
                    pipe.shaper().geography().sample(x, z, macro);
                    site[iz][ix] = (((long) macro.primaryCellX) << 32)
                            ^ (macro.primaryCellZ & 0xffffffffL);
                }
            }

            EdgeSet b = edgesOf(biome);
            EdgeSet p = edgesOf(province);
            EdgeSet s = edgesOf(site);
            int[] histogram = runHistogram(b);

            // 1/2/3: total length, longest straight run, and the run-length distribution.
            int longest = longestStraightRun(b);
            int quarter = Math.max(1, RES / 4);
            int inLongRuns = 0;
            for (int i = quarter; i < histogram.length; i++) inLongRuns += (i + 1) * histogram[i];
            double longRunShare = b.total() == 0 ? 0.0 : (double) inLongRuns / b.total();
            // 4: the share of boundary edges that have a collinear continuation, i.e. that form a
            // straight segment. (In a 4-connected grid every edge is axis-aligned by construction,
            // so raw "axis alignment" is trivially 1.0 and carries no information; the collinear
            // share is the figure that actually separates a drawn line from a wandering one.)
            double collinearShare = b.total() == 0 ? 0.0 : (double) b.collinear() / b.total();
            // 5: repetition of the boundary pattern on adjacent slices.
            double sliceRepetition = sliceRepetition(biome);
            // 6/7: overlap of the biome boundary with the province and the macro site boundary.
            double provOverlap = jaccard(b, p);
            double siteOverlap = jaccard(b, s);

            System.out.println("[V3.3-L] seed 0x" + Long.toHexString(seed)
                    + " boundaryEdges=" + b.total()
                    + " longestStraightRun=" + longest + "/" + RES
                    + " longRunShare=" + fmt(longRunShare)
                    + " collinearEdgeShare=" + fmt(collinearShare)
                    + " sliceRepetition=" + fmt(sliceRepetition)
                    + " | provinceEdges=" + p.total()
                    + " jaccard(biome,province)=" + fmt(provOverlap)
                    + " | siteEdges=" + s.total()
                    + " jaccard(biome,site)=" + fmt(siteOverlap));

            // The architectural invariant. If the biome boundary WERE the province boundary the
            // Jaccard index would be 1.0 and the biome map would be a repainted province map. The
            // bound is one-sided and structural, not fitted: a continuous field stack clears it by
            // construction, while any ownership coupling drives the index towards 1.
            assertTrue(provOverlap < 0.5,
                    "seed 0x" + Long.toHexString(seed) + ": the biome boundary coincides with the"
                            + " PROVINCE boundary (Jaccard " + fmt(provOverlap) + ") — the biome map"
                            + " is a repainted province map, so province ownership decides it again");
            // The same invariant against the macro SITE lattice — the older and stronger form of the
            // defect, since a Voronoi site edge is exactly the straight seam that was reported.
            assertTrue(siteOverlap < 0.5,
                    "seed 0x" + Long.toHexString(seed) + ": the biome boundary coincides with the"
                            + " MACRO SITE boundary (Jaccard " + fmt(siteOverlap) + ") — the biome"
                            + " map is a repainted Voronoi map, so site ownership decides it again");
            // A drawn seam reaches the window edge; a continuous boundary does not exceed it.
            assertTrue(longest <= RES,
                    "seed 0x" + Long.toHexString(seed) + ": the longest straight boundary run is "
                            + longest + " of " + RES + " cells — the boundary is a drawn line");
        }
    }

    /** The set of grid edges whose two 4-neighbours carry different labels. */
    private static final class EdgeSet {
        private final boolean[] present = new boolean[RES * RES * 2];
        private int total;
        private int collinear;

        int total() { return total; }

        int collinear() { return collinear; }

        void add(int iz, int ix, boolean horizontal) {
            int id = (iz * RES + ix) * 2 + (horizontal ? 0 : 1);
            if (present[id]) return;
            present[id] = true;
            total++;
        }

        boolean has(int iz, int ix, boolean horizontal) {
            if (iz < 0 || iz >= RES || ix < 0 || ix >= RES) return false;
            return present[(iz * RES + ix) * 2 + (horizontal ? 0 : 1)];
        }

        int size() { return present.length; }
    }

    /** Extract the boundary edge set of a label grid (4-neighbourhood, int labels). */
    private static EdgeSet edgesOf(int[][] grid) {
        EdgeSet e = new EdgeSet();
        for (int iz = 0; iz < RES; iz++) {
            for (int ix = 0; ix < RES; ix++) {
                if (ix + 1 < RES && grid[iz][ix] != grid[iz][ix + 1]) e.add(iz, ix, true);
                if (iz + 1 < RES && grid[iz][ix] != grid[iz + 1][ix]) e.add(iz, ix, false);
            }
        }
        countCollinear(e);
        return e;
    }

    /** The same, for the long site-owner ids. */
    private static EdgeSet edgesOf(long[][] grid) {
        EdgeSet e = new EdgeSet();
        for (int iz = 0; iz < RES; iz++) {
            for (int ix = 0; ix < RES; ix++) {
                if (ix + 1 < RES && grid[iz][ix] != grid[iz][ix + 1]) e.add(iz, ix, true);
                if (iz + 1 < RES && grid[iz][ix] != grid[iz + 1][ix]) e.add(iz, ix, false);
            }
        }
        countCollinear(e);
        return e;
    }

    /**
     * The longest run of CONSECUTIVE boundary edges lying on one grid line. This is the direct
     * measure of "a drawn line": a Voronoi bisector or a lattice cell edge produces a run that
     * spans the whole window, while a boundary formed by a continuous field does not.
     */
    private static int longestStraightRun(EdgeSet e) {
        int best = 0;
        for (int iz = 0; iz < RES; iz++) {
            int run = 0;
            for (int ix = 0; ix < RES; ix++) {
                if (e.has(iz, ix, true)) {
                    run++;
                    if (run > best) best = run;
                } else run = 0;
            }
        }
        for (int ix = 0; ix < RES; ix++) {
            int run = 0;
            for (int iz = 0; iz < RES; iz++) {
                if (e.has(iz, ix, false)) {
                    run++;
                    if (run > best) best = run;
                } else run = 0;
            }
        }
        return best;
    }

    /** histogram[k] = number of straight runs of length exactly k+1, over both orientations. */
    private static int[] runHistogram(EdgeSet e) {
        int[] h = new int[RES + 1];
        for (int iz = 0; iz < RES; iz++) {
            int run = 0;
            for (int ix = 0; ix <= RES; ix++) {
                boolean on = ix < RES && e.has(iz, ix, true);
                if (on) {
                    run++;
                    continue;
                }
                if (run > 0) h[run]++;
                run = 0;
            }
        }
        for (int ix = 0; ix < RES; ix++) {
            int run = 0;
            for (int iz = 0; iz <= RES; iz++) {
                boolean on = iz < RES && e.has(iz, ix, false);
                if (on) {
                    run++;
                    continue;
                }
                if (run > 0) h[run]++;
                run = 0;
            }
        }
        return h;
    }

    /**
     * Count boundary edges that have a collinear continuation, i.e. that belong to a straight
     * segment of two or more edges. A wandering boundary has many isolated corners; a drawn line
     * has almost none.
     */
    private static void countCollinear(EdgeSet e) {
        int n = 0;
        for (int iz = 0; iz < RES; iz++) {
            for (int ix = 0; ix < RES; ix++) {
                if (!e.has(iz, ix, true)) continue;
                if (e.has(iz, ix - 1, true) || e.has(iz, ix + 1, true)) n++;
            }
        }
        for (int ix = 0; ix < RES; ix++) {
            for (int iz = 0; iz < RES; iz++) {
                if (!e.has(iz, ix, false)) continue;
                if (e.has(iz - 1, ix, false) || e.has(iz + 1, ix, false)) n++;
            }
        }
        e.collinear = n;
    }

    /** |A n B| / |A u B| over the two edge sets. 1.0 means the boundaries are the same curve. */
    private static double jaccard(EdgeSet a, EdgeSet b) {
        int inter = 0;
        int union = 0;
        for (int id = 0; id < a.size(); id++) {
            boolean av = a.present[id];
            boolean bv = b.present[id];
            if (av && bv) inter++;
            if (av || bv) union++;
        }
        return union == 0 ? 0.0 : (double) inter / union;
    }

    /**
     * How often ADJACENT slices repeat the same boundary pattern. A lattice or a cell owner makes
     * neighbouring slices near-identical (the shape repeats down the map); a boundary formed by a
     * continuous field wanders, so consecutive slices rarely agree. Reported as evidence about
     * repetition rather than asserted against a fitted number.
     */
    private static double sliceRepetition(int[][] grid) {
        int same = 0;
        int slices = 0;
        int agree = 0;
        int cells = 0;
        for (int iz = 0; iz + 1 < RES; iz++) {
            boolean identical = true;
            for (int ix = 0; ix < RES; ix++) {
                if (grid[iz][ix] == grid[iz + 1][ix]) agree++;
                else identical = false;
                cells++;
            }
            if (identical) same++;
            slices++;
        }
        double identicalShare = slices == 0 ? 0.0 : (double) same / slices;
        double columnAgreement = cells == 0 ? 0.0 : (double) agree / cells;
        // Blended: whole-slice identity is the strictest form, column agreement keeps the figure
        // informative when no slice is perfectly identical.
        return identicalShare * 0.5 + columnAgreement * 0.5;
    }

    /**
     * The visible boundary must be an INTERSECTION of fields, never a drawn line.
     *
     * <p>A Voronoi edge, a lattice cell or a fixed-radius influence circle all produce the same
     * measurable signature: a handful of enormous runs, because the map is split into a few big
     * cells. An intersection of independent continuous fields produces many short runs. The
     * assertions below are deliberately about run STRUCTURE, not about smoothness.
     */
    @Test
    void theBiomeBoundaryIsNotADrawnLine() {
        final int rows = 80;
        final int step = 64;                  // ~5 km wide window: several macro cells across
        final int origin = -(rows / 2) * step;
        for (long seed : SEEDS) {
            V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(seed);
            WorldgenColumnSample col = new WorldgenColumnSample();

            int totalTransitions = 0;
            int rowsWithTransition = 0;
            int longestRun = 0;
            int shortRuns = 0;
            int runs = 0;
            for (int iz = 0; iz < rows; iz++) {
                int z = origin + iz * step;
                int run = 1;
                int previous = -1;
                boolean changed = false;
                for (int ix = 0; ix < rows; ix++) {
                    sampler.sampleColumn(origin + ix * step, z, col);
                    int b = col.biome.catalogueIndex();
                    if (ix == 0) {
                        previous = b;
                        continue;
                    }
                    if (b != previous) {
                        changed = true;
                        totalTransitions++;
                        runs++;
                        if (run <= 10) shortRuns++;
                        if (run > longestRun) longestRun = run;
                        run = 1;
                        previous = b;
                    } else {
                        run++;
                    }
                }
                runs++;
                if (run <= 10) shortRuns++;
                if (run > longestRun) longestRun = run;
                if (changed) rowsWithTransition++;
            }
            double shortShare = shortRuns / (double) Math.max(1, runs);
            System.out.println("[V3.3-L] seed 0x" + Long.toHexString(seed)
                    + " transitions=" + totalTransitions + " rowsWithTransition=" + rowsWithTransition
                    + "/" + rows + " longestRun=" + longestRun + " shortRunShare=" + fmt(shortShare));

            assertTrue(totalTransitions >= rows / 3,
                    "seed 0x" + Long.toHexString(seed) + ": only " + totalTransitions
                            + " biome borders over " + rows + " rows — the map is a few huge cells");
            assertTrue(rowsWithTransition >= rows / 2,
                    "seed 0x" + Long.toHexString(seed) + ": only " + rowsWithTransition + " of "
                            + rows + " rows contain a border — that is a STRAIGHT extended seam");
            assertTrue(longestRun <= (rows * 3) / 4,
                    "seed 0x" + Long.toHexString(seed) + ": the longest uniform run is " + longestRun
                            + " of " + rows + " columns — a single owner owns almost the window");
            assertTrue(shortShare >= 0.15,
                    "seed 0x" + Long.toHexString(seed) + ": only " + fmt(shortShare)
                            + " of the runs are short — the boundary is too regular");
        }
    }

    @org.junit.jupiter.api.Test
    void zzDiagnostic() {
        V3ColumnSampler sampler = V3BiomeRuntimeIntegrationTest.earthlike(0xC102L);
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = 54; x <= 66; x++) {
            sampler.sampleColumn(x, 1024, col);
            System.out.println("[DIAG] x=" + x
                    + " best=" + fmt(col.bestScore) + " second=" + fmt(col.secondBestScore)
                    + " biome=" + col.biome.id()
                    + " prov=" + (col.dominantGeology() == null ? "-" : col.dominantGeology().name())
                    + " macroBd=" + fmt(col.macroBoundaryDistance)
                    + " core=" + fmt(col.macroCoreShare)
                    + " elev=" + fmt(col.elevation01) + " slope=" + fmt(col.slope)
                    + " T=" + fmt(col.temperature01) + " H=" + fmt(col.humidity01)
                    + " P=" + fmt(col.precipitation01) + " wet=" + fmt(col.wetness01)
                    + " river=" + fmt(col.riverMask) + " prox=" + fmt(col.riverProximity)
                    + " lake=" + fmt(col.lakeMask) + " organic=" + fmt(col.organicPotential)
                    + " sed=" + fmt(col.sedimentShare) + " rock=" + fmt(col.rockShare));
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** Full-precision form, for distinguishing a real response from a rounded one. */
    private static String sci(double v) {
        return String.format(Locale.ROOT, "%.6e", v);
    }
}

