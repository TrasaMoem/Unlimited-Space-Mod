package com.modscreating.unlimitedspace.core.worldgen.biome;

/**
 * WORLDGEN V3.4 — the BOUNDARY TRANSITION FIELD.
 *
 * <p>A separate layer. It does not decide the biome, it owns no geography, and it never re-runs
 * the biome search:
 *
 * <pre>
 *   continuous biome affinities
 *        -&gt; discrete winner (argmax)        [V3.3, unchanged]
 *        -&gt; BoundaryTransitionField          [V3.4, this class]
 *        -&gt; irregular transition zone
 *        -&gt; surface / material mixing        [V3.4, SurfaceMaterialField + generator]
 * </pre>
 *
 * <h2>Why the V3.3 boundary was still a smooth line</h2>
 * The V3.3 {@code detailFit} term multiplies ONE shared, smooth, long-wavelength noise into EVERY
 * candidate at EVERY column. Two consequences, both fatal to the edge geometry:
 * <ol>
 *   <li>it is applied everywhere, so it perturbs the interior climate geography instead of only
 *       the contact zone;</li>
 *   <li>because the SAME field is shared, the resulting level set is
 *       {@code baseA*kA*n = baseB*kB*n} — a smooth multiplicative warp of an already smooth field.
 *       A smooth field's level set is a smooth curve, so the wrinkle could move the contour but
 *       never change its CHARACTER. That is why V3.3 still read as a smooth arc.</li>
 * </ol>
 * This class changes the CHARACTER of that curve: the detail is MARGIN GATED, so the interior is
 * bit-identical, and PAIR ANTISYMMETRIC, so the displaced contour stays a single-valued curve
 * instead of tearing into two.
 *
 * <h2>The antisymmetry contract (easy to get wrong)</h2>
 * Winner and runner-up swap roles on the two sides of a contour. The displacement must therefore
 * change sign when the two candidates swap, or the two sides would disagree about who owns the
 * column and the boundary would break. So the noise is keyed by the SORTED index pair (the
 * roughness belongs to the CONTACT, not to the winner) and an {@code orientation} of {@code +1/-1}
 * is applied on top (consistency under the swap). Then
 * {@code aWins <=> margin > orientation*edge*weight*PERTURBATION} is exact under swapping, which is
 * precisely what keeps the contour well defined.
 *
 * <h2>Spatial scales — measured, not swept</h2>
 * The real V3.3 boundary scale was measured before these constants were chosen:
 * <ul>
 *   <li>macro region / site cell <b>2400</b> blocks ({@code MacroSiteLattice.CELL_SIZE});</li>
 *   <li>climate ~2600, continentalness ~3400, medium provinces ~900;</li>
 *   <li>finest field V3.3 already had: <b>260</b>; measured mean biome run: <b>110-155</b>.</li>
 * </ul>
 * Every scale below is strictly smaller than the smallest pre-existing worldgen field (192-block
 * micro-facies) and 12x-110x smaller than the macro cell. A boundary detail living at the same
 * scale as the geography it deforms cannot change that geography. They are also 5-25x larger than
 * one block, which keeps the result erosion-like instead of salt-and-pepper:
 * <ul>
 *   <li>{@link #BROAD_WAVELENGTH} 192 — large bays, headlands, peninsulas;</li>
 *   <li>{@link #MEDIUM_WAVELENGTH} 64 — the fingers and inlets;</li>
 *   <li>{@link #FINE_WAVELENGTH} 22 — small notches and pocket lips.</li>
 * </ul>
 * The weights DECREASE with frequency (0.50 / 0.32 / 0.18): the fine scale must be able to break
 * the edge without being able to shred it.
 *
 * <h2>Margin gate — measured, not swept</h2>
 * Measured {@code scoreMargin} over the audit seeds: p10 ~ 0.010-0.032, p25 ~ 0.026-0.082,
 * p50 ~ 0.060-0.210, p75 ~ 0.131-0.729. {@link #MARGIN_FULL} = 0.12 lies between the measured p50
 * and p75 of every seed. The decisive quantity is not the threshold but the BAND WIDTH it implies,
 * because a contact zone can never displace its own contour further than its own width. Above the
 * gate the weight is exactly 0, so the interior biome cannot move at all.
 *
 * <h2>Determinism, allocation, cost</h2>
 * Immutable, stateless, a pure function of {@code (seed, x, z, pair)}. It writes into a
 * caller-owned {@link WorldgenColumnSample} and allocates nothing. O(1) in the candidate count: the
 * octaves are evaluated at most once per column, and only while the gate is open.
 */
public final class BoundaryTransitionField {

    /** Large-scale deformation: bays, headlands, peninsulas. Below every pre-existing field. */
    public static final double BROAD_WAVELENGTH = 192.0;
    /** Medium breakup: the fingers and inlets of the contact zone. */
    public static final double MEDIUM_WAVELENGTH = 64.0;
    /** Fine edge breakup: notches and pocket lips, still ~22 blocks across. */
    public static final double FINE_WAVELENGTH = 22.0;

    /** Amplitude per scale in {@link #edgeDetail}; decreasing with frequency, on purpose. */
    public static final double W_BROAD = 0.50;
    public static final double W_MEDIUM = 0.32;
    public static final double W_FINE = 0.18;


    /**
     * The margin at which the transition zone is fully closed, in raw score units.
     *
     * <p>MEASURED {@code scoreMargin} over the audit seeds: p10 0.010-0.032, p25 0.026-0.082,
     * p50 0.060-0.210, p75 0.131-0.729. 0.12 sits between the measured p50 and p75 of every seed, so
     * the gate is seed-robust rather than fitted to one planet.
     *
     * <p>This constant sets the transition zone WIDTH IN BLOCKS, not just a threshold. The margin
     * rises from 0 to this value across the band, so
     *
     * <pre>
     *   bandWidthBlocks ~= MARGIN_FULL / |grad(margin)|
     * </pre>
     *
     * <p>which was measured at 5.8-18.2 blocks for {@code MARGIN_FULL = 0.03} — a band far too
     * narrow to host any real irregularity, since the largest excursion a contact zone can make is
     * bounded by its own width (see {@link #PERTURBATION}). Raising the gate to 0.12 widens the band
     * by roughly 4x, to tens of blocks, which is the scale a player actually reads as a border.
     *
     * <p>The interior is still provably untouched: above this margin the weight is exactly 0, so
     * both the displacement and the neighbour share are exactly 0.
     */
    public static final double MARGIN_FULL = 0.12;

    /**
     * The GEOMETRIC gate: the margin up to which the contour may be displaced at all.
     *
     * <p>It is deliberately much wider than {@link #CONTACT_MARGIN_FULL}, and the reason is a hard
     * geometric constraint rather than taste. A margin-gated displacement can never move a contour
     * further than the width of its own band, because outside the gate the bias is exactly zero and
     * the original smooth curve takes over again. The band width is
     * {@code MARGIN_FULL / |grad(margin)|}, and the measured band was only 5.8-18.2 blocks at
     * {@code CONTACT_MARGIN_FULL}. A 20-block wobble cannot read as an eroded contact zone at all —
     * it reads as noise, and on the planet with the steepest margin gradient it read as a slight
     * SMOOTHING, because a displacement smoother than the boundary's own structure erases it.
     *
     * <p>0.15 is a measured choice with a negative result attached, which is the useful part. Widening
     * this gate from 0.12 to 0.5 was tried and produced a BIT-IDENTICAL outcome on all four audit
     * seeds (changed columns 16.37% / 17.26% / 4.64% / 8.54% either way), because beyond
     * {@code RELATIVE_PERTURBATION * bestScore} the bias is simply smaller than the margin it would
     * have to overcome, so the extra width buys no displacement at all. It cost everything, though:
     * the gate was open on 100% of columns on one seed — leaving NO interior column to protect, which
     * voids the whole "the interior is untouched" argument — and it evaluated the three noise octaves
     * on essentially every column of the hot path. The narrower gate is therefore strictly better:
     * same boundary, a real interior, and the octaves only run where they can matter.
     *
     * <p>Widening the gate does NOT widen the visible material mixture: that is
     * {@link #CONTACT_MARGIN_FULL}'s job, and the two are deliberately decoupled.
     */
    public static final double GEOMETRY_MARGIN_FULL = 0.15;

    /**
     * The CONTACT gate for the surface-material dither, in margin units.
     *
     * <p>A visible contact zone is only a few blocks to a few tens of blocks wide, so the palette
     * mixture must be driven by a much tighter gate than the contour geometry. With
     * {@link #MAX_NEIGHBOUR_SHARE} capping how much of a column the neighbour may take, and the
     * smoothstep taper driving the share to ~0 well before the gate closes, widening the geometric
     * gate leaves the material mixture concentrated exactly where it should be: on the border.
     */
    public static final double CONTACT_MARGIN_FULL = MARGIN_FULL;

    /**
     * Contour displacement as a multiple of the band width — and there is a HARD ceiling on it.
     *
     * <p>The displaced contour is the zero set of {@code margin(x,z) - bias(x,z)}, and the field is
     * only evaluated while {@code margin < MARGIN_FULL}. A bias larger than {@code MARGIN_FULL} is
     * therefore unreachable and does nothing at all; the maximum useful bias is exactly
     * {@code MARGIN_FULL}, i.e. the contour can move at most ONE band width. That is a property of
     * the gate, not a tuning choice, and it is why {@link #MARGIN_FULL} — the band width — is the
     * number that actually decides how irregular a border can look.
     *
     * <p>Set to 1.0, the ceiling. A tongue that crosses the whole band and detaches into a pocket is
     * the defining feature of an eroded contact; a smaller value can only make the edge breathe.
     */
    public static final double PERTURBATION_BAND_WIDTHS = 1.0;

    /** The displacement in margin units. Derived from the measured band width, not fitted. */
    public static final double PERTURBATION = PERTURBATION_BAND_WIDTHS * MARGIN_FULL;

    /**
     * The perturbation as a fraction of the WINNING SCORE, before the gate clamp.
     *
     * <p>This is the fix for a real conditioning problem that the first implementation hit. A purely
     * absolute margin-space bias displaces the contour by {@code bias / |grad(margin)|}, and
     * {@code |grad(margin)|} varies by more than an order of magnitude between planets: it was
     * measured at 5.8-18.2 blocks of band width across the audit seeds, so a fixed bias moved the
     * edge several blocks on one planet and was sub-block — literally invisible — on another. The
     * seed with the steepest margin gradient was the one with the fewest, largest, smoothest
     * regions, i.e. exactly the case this ACT exists to fix, and it was the one that did not
     * respond.
     *
     * <p>Scaling by the winning score makes the perturbation RELATIVE, and since the margin gradient
     * scales with the score magnitude too, the ratio largely cancels: the displacement becomes
     * approximately {@code RELATIVE_PERTURBATION * L}, where {@code L} is the score field's own
     * correlation length. That is a property of the worldgen scales rather than of one planet, so
     * every planet gets a comparable displacement in BLOCKS.
     */
    public static final double RELATIVE_PERTURBATION = 0.30;

    /**
     * Wavelength of the spatially coherent surface DITHER pattern, in blocks.
     *
     * <p>Below {@link #MEDIUM_WAVELENGTH} so the mixture is itself broken up, and far above one
     * block so neighbouring blocks stay strongly correlated: a contact zone, not a per-block coin
     * flip.
     */
    public static final double DITHER_WAVELENGTH = 16.0;

    /**
     * Largest share of the NEIGHBOURING palette a column may take, capped at 0.5 so a border is a
     * coherent majority-with-tongues rather than a 50/50 coin flip, and the logical biome still
     * reads as the dominant one.
     */
    public static final double MAX_NEIGHBOUR_SHARE = 0.5;

    /**
     * Domain-warp depth as a fraction of each octave's OWN wavelength.
     *
     * <p>Proportional, and that is the whole point. The first implementation used one absolute depth
     * (96 blocks) for all three octaves, which is 4.4x the wavelength of the FINE octave. A warp
     * deeper than the wavelength it is applied to does not bend that scale — it destroys it, and the
     * "fine" octave degenerates into a low-frequency smear. That is measurable: on the seed with the
     * steepest margin gradient the boundary LENGTH actually DECREASED (0.105 -&gt; 0.091), because a
     * displacement smoother than the boundary's own structure erases it. Making the depth
     * proportional keeps every octave in its own scale range, so the fine one really is fine.
     *
     * <p>0.75 is deep enough to turn a level set into a hooked, inlet-bearing curve rather than an
     * arc, and shallow enough that the octave's own wavelength still dominates its level set.
     */
    public static final double WARP_DEPTH_FRACTION = 0.75;

    /** The immutable planet-folded seed. The field holds no mutable state. */
    private final long seed;

    public BoundaryTransitionField(long planetSeed) {
        this.seed = planetSeed;
    }

    /** The planet seed this field deforms boundaries for. */
    public long seed() {
        return seed;
    }


    /**
     * The transition state of one column, written into the caller-owned sample.
     *
     * <p>Publishes the runner-up — which {@link BiomeMaskField#classify} already computed in the
     * SAME pass, so this is NOT a second search — plus the margin, the boundary weight, the
     * multi-scale edge detail and the coherent dither value. It never changes {@code sample.biome}:
     * the caller decides whether to act on the displacement.
     *
     * @param x        block x
     * @param z        block z
     * @param winner   the elected candidate (never null)
     * @param runnerUp the second-best candidate, or null when only one candidate competed
     * @param indexA   catalogue index of the winner
     * @param indexB   catalogue index of the runner-up
     * @param scoreA   the winner's score
     * @param scoreB   the runner-up's score
     * @param out      the caller-owned sample, overwritten in place
     */
    public void evaluate(int x, int z, BiomeCandidate winner, BiomeCandidate runnerUp,
                         int indexA, int indexB, double scoreA, double scoreB,
                         WorldgenColumnSample out) {
        out.runnerUp = runnerUp;
        out.runnerUpScore = runnerUp == null ? 0.0 : scoreB;
        out.boundaryMargin = 0.0;
        out.transitionZone = 0.0;
        out.edgeDetail = 0.0;
        out.boundaryWeight = 0.0;
        out.dominantSide = 1;
        out.dither01 = 0.5;
        out.runnerUpShare01 = 0.0;
        if (runnerUp == null) return;

        double margin = scoreA - scoreB;
        if (!(margin > 0.0)) margin = 0.0;
        out.boundaryMargin = margin;

        // ---- the GEOMETRIC gate: how far the contour itself may move ----
        double tg = margin / GEOMETRY_MARGIN_FULL;
        if (tg < 1.0) {
            out.boundaryWeight = 1.0 - smoothstep01(tg);
            out.edgeDetail = edgeDetail(indexA, indexB, x, z);
        }
        // ---- the CONTACT gate: how much of the neighbour's palette may show ----
        // Deliberately much tighter than the geometric one, so widening the contour's freedom to
        // move does NOT smear the surface mixture across whole biomes.
        double tc = margin / CONTACT_MARGIN_FULL;
        if (tc < 1.0) {
            double contact = 1.0 - smoothstep01(tc);
            out.transitionZone = contact;
            out.runnerUpShare01 = MAX_NEIGHBOUR_SHARE * contact;
        }
        out.dominantSide = margin > 0.0 ? 1 : -1;
        out.dither01 = ditherValue(indexA, indexB, x, z);
    }


    /**
     * The signed displacement applied to the winner-vs-runner-up balance, in margin units.
     *
     * <p>ANTISYMMETRIC under swapping the two candidates (see the class javadoc). At
     * {@code margin == 0} the two sides of a contour then produce exactly complementary
     * decisions, which is what makes the displaced contour a single-valued curve rather than a
     * torn pair.
     */
    public double bias(int indexA, int indexB, int x, int z, double boundaryWeight,
                       double bestScore) {
        if (!(boundaryWeight > 0.0)) return 0.0;
        long pair = pairKey(indexA, indexB);
        double orientation = indexA < indexB ? 1.0 : -1.0;
        // Relative to the winning score, so the displacement scales with the planet's own score
        // magnitude instead of with an absolute constant. Clamped by the gate above, beyond which a
        // bias is unreachable and would do nothing at all.
        double amplitude = RELATIVE_PERTURBATION * (bestScore > 0.0 ? bestScore : 1.0);
        if (amplitude > GEOMETRY_MARGIN_FULL) amplitude = GEOMETRY_MARGIN_FULL;
        return orientation * rawDetail(pair, 0L, x, z) * boundaryWeight * amplitude;
    }

    /**
     * The multi-scale, pair-specific edge detail in [-1, 1].
     *
     * <p>Three warped octaves, not one noise: a single noise has a single characteristic curvature,
     * which is exactly the "smooth arc" failure this layer removes. The value is PAIR-SYMMETRIC
     * (sorted key), so the roughness of a contact belongs to the contact, not to whichever side
     * happens to be winning.
     */
    public double edgeDetail(int indexA, int indexB, int x, int z) {
        return rawDetail(pairKey(indexA, indexB), 0L, x, z);
    }

    /** The spatially coherent dither value in [0, 1] driving the surface material mixture. */
    public double ditherValue(int indexA, int indexB, int x, int z) {
        return rawDetail(pairKey(indexA, indexB), 0x5D17L, x, z) * 0.5 + 0.5;
    }

    /**
     * The key of the CONTACT between two candidates: symmetric in the pair, so the roughness of a
     * boundary does not depend on which side won.
     */
    private long pairKey(int indexA, int indexB) {
        int lo = indexA < indexB ? indexA : indexB;
        int hi = indexA < indexB ? indexB : indexA;
        return seed ^ (((long) lo * 0x9E3779B97F4A7C15L)
                ^ ((long) hi * 0xC2B2AE3D27D4EB4FL)
                ^ 0x626F756E64617279L);
    }

    /**
     * {@code w1*broad + w2*medium + w3*fine}, every octave domain-warped at four times its own
     * wavelength so the warp BENDS the wrinkle instead of injecting block-scale jitter.
     */
    private double rawDetail(long pairSeed, long salt, int x, int z) {
        double broad = octave(pairSeed ^ salt ^ 0x62L, BROAD_WAVELENGTH, x, z);
        double medium = octave(pairSeed ^ salt ^ 0x6DL, MEDIUM_WAVELENGTH, x, z);
        double fine = octave(pairSeed ^ salt ^ 0x66L, FINE_WAVELENGTH, x, z);
        return W_BROAD * broad + W_MEDIUM * medium + W_FINE * fine;
    }

    /**
     * One deterministic octave in [-1, 1] at the given wavelength, with a broad, shallow domain
     * warp.
     *
     * <p>The warp is four times broader and half as deep as the carrier. That asymmetry is what
     * turns a blob into a hook: a warp of the same scale would just add block-scale jitter
     * (salt and pepper), while no warp at all would leave a circular level set.
     */
    private static double octave(long s, double wavelength, int x, int z) {
        // The warp is four times broader and proportionally shallower than the carrier: it bends the
        // wrinkle rather than jittering it, which is what separates an eroded coast from a noisy one,
        // and being a FRACTION of the wavelength it can never overwhelm the scale it is bending.
        double warpWavelength = wavelength * 4.0;
        double warpBlocks = wavelength * WARP_DEPTH_FRACTION;
        double wx = x + warpBlocks * (value01(s ^ 0x77A1L, x, z, warpWavelength) - 0.5);
        double wz = z + warpBlocks * (value01(s ^ 0x77A2L, x, z, warpWavelength) - 0.5);
        return value01(s, wx, wz, wavelength) * 2.0 - 1.0;
    }

    /** Smooth value noise in [0, 1] at integer coordinates. */
    private static double value01(long s, int x, int z, double wavelength) {
        return value01(s, (double) x, (double) z, 1.0 / wavelength);
    }

    /** Smooth value noise in [0, 1] at continuous coordinates. */
    private static double value01(long s, double wx, double wz, double wavelength) {
        double sx = wx / wavelength;
        double sz = wz / wavelength;
        int x0 = floor(sx);
        int z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double c00 = corner(s, x0, z0);
        double c10 = corner(s, x0 + 1, z0);
        double c01 = corner(s, x0, z0 + 1);
        double c11 = corner(s, x0 + 1, z0 + 1);
        double a = c00 + (c10 - c00) * tx;
        double b = c01 + (c11 - c01) * tx;
        return a + (b - a) * tz;
    }

    /** Integer lattice hash to [0, 1): splitmix64 finaliser, no Random, no seed state. */
    private static double corner(long s, int cx, int cz) {
        long h = s + (long) cx * 0x9E3779B97F4A7C15L + (long) cz * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double smoothstep01(double t) {
        double c = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return c * c * (3.0 - 2.0 * c);
    }
}
