package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — the MACRO GEOGRAPHY of one planet: the single owner of the macro geometry.
 *
 * <pre>
 *   deterministic site lattice
 *       -&gt; bounded smooth coordinate transform  (BoundedWarp: F(p) = p + D(p))
 *           -&gt; nearest-site Voronoi ownership    (argmin D_i over a FIXED window)
 * </pre>
 *
 * <h2>Ownership model (the whole point of the rewrite)</h2>
 * <pre>
 *   province           = argmin_i  D_i
 *   secondaryProvince  = second smallest D_i
 * </pre>
 *
 * There is exactly ONE geometry source of truth. There is NO aggregation of site
 * contributions, NO sum-of-weights region comparison, NO capped/smoothed/guarded label index,
 * NO anchor correction, NO support slack and NO tie hack. None of those can exist here because
 * none of those operations is performed anywhere in this class.
 *
 * <h2>Fixed candidate window</h2>
 * The query evaluates a FIXED {@link #WINDOW_RADIUS}&times;2+1 squared neighbourhood
 * (7&times;7 = 49 sites). The window is NEVER rotated, never switched on the fractional
 * coordinate and never selected dynamically. Correctness of a fixed window follows from the
 * lattice's <b>bounded jitter</b>: a site never moves more than
 * {@link MacroSiteLattice#JITTER_SPAN} cells from its own centre, so the nearest and
 * second-nearest sites of any query are provably inside the window. That claim is
 * <em>validated against a 9&times;9 oracle</em> by the test suite, not merely asserted.
 *
 * <h2>Tie-breaking</h2>
 * Ties are broken deterministically and <b>geometrically neutrally</b>: on an exact distance tie
 * the candidate with the lower linear cell index {@code (cz, cx)} wins. There is no hash-based
 * ownership injection — a hash must never be able to move a border.
 *
 * <p>Immutable. Pure domain: no Minecraft types.
 */
public final class MacroGeography {

    /** Candidate window radius in cells: 3 &rarr; a 7&times;7 = 49-site window. */
    public static final int WINDOW_RADIUS = 3;
    /** The 7&times;7 candidate site count. */
    public static final int WINDOW_SITES = (2 * WINDOW_RADIUS + 1) * (2 * WINDOW_RADIUS + 1);
    /** The independent ORACLE window radius used only by tests (9&times;9 = 81 sites). */
    public static final int ORACLE_RADIUS = 4;
    /** The 9&times;9 oracle side. */
    public static final int ORACLE_RADIUS_SIDE = 2 * ORACLE_RADIUS + 1;

    /**
     * DEFAULT half-width of the transition band in blocks: the distance from the bisector at
     * which {@link MacroSample#transitionWeight} has saturated to 1.
     *
     * <p>This is the shipped default only. The value actually in force is the immutable
     * per-instance {@link #transitionHalfWidth()}, which the calibration suite sets explicitly.
     * It is a constructor argument, never mutable static state, so two geographies calibrated
     * differently can coexist and neither can be changed after construction.
     */
    public static final double DEFAULT_TRANSITION_HALF_WIDTH = 300.0;

    /**
     * @deprecated use {@link #transitionHalfWidth()} for the value in force, or
     *             {@link #DEFAULT_TRANSITION_HALF_WIDTH} for the shipped default. Retained only
     *             so existing report/test call sites keep compiling.
     */
    @Deprecated
    public static final double TRANSITION_HALF_WIDTH = DEFAULT_TRANSITION_HALF_WIDTH;

    /**
     * The {@code transitionWeight} below which a column counts as "in transition", so the band
     * is a genuine few-hundred-block neighbourhood of the bisector rather than a planet-wide
     * dilution of every province.
     */
    public static final double TRANSITION_CORE_THRESHOLD = 0.97;

    /**
     * DEFAULT exponent of the compact blend kernel {@code (1 - t^2)^KERNEL_EXPONENT_DEFAULT}.
     *
     * <p>WARNING — a documented/implemented mismatch was found and FIXED while making this a real
     * parameter. The original kernel was written {@code u2 = u*u; return u2*u2*u;}, which is
     * {@code u^5}, while its javadoc claimed {@code (1 - t^2)^4}. The shipped behaviour was
     * therefore exponent 5, so this default is set to 5: making the exponent explicit must NOT
     * silently change production behaviour. Setting it to 4 would soften every province boundary
     * and drop the measured coreShare from 0.532 to 0.474.
     *
     * <p>The value in force is the immutable per-instance {@link #kernelExponent()}.
     */
    public static final int KERNEL_EXPONENT_DEFAULT = 5;

    /**
     * The candidate exponents the calibration suite is allowed to compare. Raising the exponent
     * sharpens the blend (mass concentrates on the dominant site, so {@code coreShare} rises)
     * WITHOUT changing {@link #BLEND_RADIUS}, so the coverage guarantee is untouched and only
     * smoothness improves: {@code (1 - t^2)^K} still has value and first three derivatives zero
     * at the support edge for every {@code K >= 4}.
     */
    public static final int[] CALIBRATION_KERNEL_EXPONENTS = {4, 6, 8};

    /**
     * Attribute blend support radius in blocks. It must be large enough that the nearest site
     * always contributes (no coverage holes) and small enough that the whole support fits
     * inside the fixed candidate window. With {@code CELL = 2400} and
     * {@code JITTER_SPAN = 0.35} the window's guaranteed coverage is
     * {@code (3 - 0.35) * CELL}, so {@code 1.6 * CELL} is comfortably inside the window and
     * comfortably outside the worst-case nearest-site distance of about
     * {@code sqrt(2) * 0.85 * CELL = 1.20 * CELL}.
     */
    public static final double BLEND_RADIUS = MacroSiteLattice.CELL_SIZE * 1.6;
    /** {@link #BLEND_RADIUS} squared, precomputed for the hot path. */
    private static final double BLEND_RADIUS_SQ = BLEND_RADIUS * BLEND_RADIUS;

    private final MacroSiteLattice lattice;
    private final BoundedWarp warp;
    private final ArchetypeCatalog catalog;
    private final double cos;
    private final double sin;
    /** Immutable transition half-width in force for THIS geography, in blocks. */
    private final double transitionHalfWidth;
    /** Immutable compact-kernel exponent in force for THIS geography. */
    private final int kernelExponent;

    private MacroGeography(MacroSiteLattice lattice, BoundedWarp warp, ArchetypeCatalog catalog) {
        this(lattice, warp, catalog, DEFAULT_TRANSITION_HALF_WIDTH, KERNEL_EXPONENT_DEFAULT);
    }

    /**
     * A fully explicit geography. Both calibration knobs are immutable constructor arguments:
     * there is NO mutable static calibration state anywhere, so a test can hold a geography
     * calibrated at one setting while production holds another, and neither can drift.
     *
     * @param halfWidth the transition half-width H in blocks; must be positive
     * @param exponent  the compact-kernel exponent; must be &gt;= 4 so the kernel keeps value and
     *                  its first three derivatives zero at the support edge
     */
    private MacroGeography(MacroSiteLattice lattice, BoundedWarp warp, ArchetypeCatalog catalog,
                           double halfWidth, int exponent) {
        if (!(halfWidth > 0.0)) {
            throw new IllegalArgumentException("halfWidth must be positive: " + halfWidth);
        }
        if (exponent < 4) {
            throw new IllegalArgumentException(
                    "kernel exponent must be >= 4 to keep the compact support C3: " + exponent);
        }
        this.lattice = lattice;
        this.warp = warp;
        this.catalog = catalog;
        this.transitionHalfWidth = halfWidth;
        this.kernelExponent = exponent;
        this.cos = Math.cos(lattice.rotation());
        this.sin = Math.sin(lattice.rotation());
    }

    /** Canonical factory: planet seed + derived environment &rarr; complete macro geography. */
    public static MacroGeography of(long planetSeed, PlanetaryEnvironment env) {
        return new MacroGeography(MacroSiteLattice.of(planetSeed), BoundedWarp.of(planetSeed),
                ArchetypeCatalog.of(planetSeed, env));
    }

    /**
     * CALIBRATION factory. Produces the SAME geometry, ownership and warp as {@link #of} — the
     * two calibration knobs cannot influence which site owns a column — but with a different
     * transition half-width and blend-kernel exponent.
     *
     * <p>This exists so the calibration suite can sweep H and the kernel exponent as independent
     * controls. It is a test/diagnostic entry point; production uses {@link #of}.
     */
    public static MacroGeography calibrated(long planetSeed, PlanetaryEnvironment env,
                                            double transitionHalfWidth, int kernelExponent) {
        return new MacroGeography(MacroSiteLattice.of(planetSeed), BoundedWarp.of(planetSeed),
                ArchetypeCatalog.of(planetSeed, env), transitionHalfWidth, kernelExponent);
    }

    /** The transition half-width H actually in force, in blocks. */
    public double transitionHalfWidth() {
        return transitionHalfWidth;
    }

    /** The compact-kernel exponent actually in force. */
    public int kernelExponent() {
        return kernelExponent;
    }

    /** The site lattice (diagnostics / metrics). */
    public MacroSiteLattice lattice() {
        return lattice;
    }

    /** The bounded warp (diagnostics / metrics). */
    public BoundedWarp warp() {
        return warp;
    }

    /** The archetype catalog (diagnostics / classifier priors). */
    public ArchetypeCatalog catalog() {
        return catalog;
    }

    /** HOT PATH. Sample into a caller-owned {@link MacroSample}. Allocates NOTHING. */
    public void sample(double x, double z, MacroSample out) {
        sampleInternal(x, z, out, WINDOW_RADIUS);
    }

    /**
     * The independent 9&times;9 ORACLE, so the test suite can prove the 7&times;7 window is
     * not merely plausible. Never called from production code.
     */
    public void sampleOracle(double x, double z, MacroSample out) {
        sampleInternal(x, z, out, ORACLE_RADIUS);
    }

    /** A convenience allocating wrapper (debug / tests only; NOT the hot path). */
    public MacroSample.Snapshot sample(double x, double z) {
        MacroSample out = new MacroSample();
        sample(x, z, out);
        return out.snapshot();
    }
    // ---------------------------------------------------------------- hot path

    /**
     * The compact-support smooth blend kernel {@code k(t) = (1 - t^2)^K} for {@code t < 1}, and
     * {@code 0} for {@code t >= 1}, where {@code K} is the per-instance {@link #kernelExponent()}.
     *
     * <p>{@code k}, {@code k'}, {@code k''} and {@code k'''} all vanish at {@code t = 1}, so a
     * site entering or leaving the support contributes no value and no first three derivatives
     * at the support edge. That is what makes the blended attribute field continuous across every
     * site's support boundary. Raising {@code K} does not weaken that: the multiplicity of the
     * zero at the edge only increases.
     *
     * <p>Exponentiation is by binary powering on a {@code double}, so no allocation and no
     * {@link Math#pow} call occur on the hot path.
     */
    private double kernel(double t) {
        if (t >= 1.0) return 0.0;
        double u = 1.0 - t * t;
        return intPow(u, kernelExponent);
    }

    /** {@code base^exponent} for a non-negative integer exponent, by binary powering. */
    private static double intPow(double base, int exponent) {
        double result = 1.0;
        double b = base;
        int e = exponent;
        while (e > 0) {
            if ((e & 1) != 0) result *= b;
            e >>>= 1;
            if (e > 0) b *= b;
        }
        return result;
    }

    /**
     * DIAGNOSTIC: the SAME site lattice, the SAME rotation and the SAME Voronoi rule, but with the
     * bounded warp DISABLED, so the query point is transformed by the identity only.
     *
     * <p>This exists for exactly one purpose: an honest {@code moveShare}. Comparing warped
     * ownership against the unwarped lattice answers "does the warp actually deform ownership?".
     * Comparing against the raw <em>unrotated cell index</em> — as the original metric did — does
     * not, because that comparison is dominated by the lattice rotation and by the site's bounded
     * jitter, both of which are present with or without a warp, so the number is ~1.0 for any
     * non-zero rotation and says nothing at all.
     *
     * <p>Ownership is not modified by this method; it simply removes one input, exactly as
     * {@link BoundedWarp} defines it. Never called from production code.
     */
    public void sampleUnwarped(double x, double z, MacroSample out) {
        sampleUnwarpedInternal(x, z, out, WINDOW_RADIUS);
    }

    /** {@link #sampleUnwarped} against the independent 9&times;9 oracle window. Diagnostics only. */
    public void sampleUnwarpedOracle(double x, double z, MacroSample out) {
        sampleUnwarpedInternal(x, z, out, ORACLE_RADIUS);
    }

    /** The warp-free twin of {@link #sampleInternal}: identity transform, then rotate. */
    private void sampleUnwarpedInternal(double x, double z, MacroSample out, int radius) {
        double wx = x;
        double wz = z;
        double qx = wx * cos - wz * sin;
        double qz = wx * sin + wz * cos;
        voronoi(qx, qz, out, radius, true);
    }

    /** The whole sample, allocation-free, over a FIXED window of {@code radius} cells. */
    private void sampleInternal(double x, double z, MacroSample out, int radius) {
        // 1. Bounded smooth coordinate transform F(p) = p + D(p), then rotate into lattice
        //    space. The rotation is an isometry, so the Voronoi can be evaluated in the
        //    rotated frame where the lattice is axis aligned.
        double wx = x + warp.displacementX(x, z);
        double wz = z + warp.displacementZ(x, z);
        double qx = wx * cos - wz * sin;
        double qz = wx * sin + wz * cos;
        voronoi(qx, qz, out, radius, false);
    }

    /**
     * The nearest-site Voronoi over the FIXED candidate window, in rotated lattice space.
     *
     * @param skipBlend when true only the discrete ownership quantities are computed
     *                  ({@code d1}, {@code d2}, the bisector distance and the cell indices),
     *                  which is all the {@code moveShare} diagnostic needs
     */
    private void voronoi(double qx, double qz, MacroSample out, int radius, boolean skipBlend) {
        int cellX = (int) Math.floor(qx / MacroSiteLattice.CELL_SIZE);
        int cellZ = (int) Math.floor(qz / MacroSiteLattice.CELL_SIZE);

        // 2. Nearest-site Voronoi over the FIXED window, keeping the exact nearest and second
        //    nearest site with their squared distances. Simultaneously accumulate the
        //    continuous attribute blend — the SAME candidate set, one pass, no second field.
        double bestD1 = Double.MAX_VALUE, bestD2 = Double.MAX_VALUE;
        int bestCx = 0, bestCz = 0, secondCx = 0, secondCz = 0;
        double bestSx = 0.0, bestSz = 0.0, secondSx = 0.0, secondSz = 0.0;

        // Continuous attribute accumulators (plain locals: the hot path must not allocate).
        double mass = 0.0;
        double aHill = 0.0, aMount = 0.0, aUplift = 0.0, aBias = 0.0, aRough = 0.0;
        double aCarve = 0.0, aDune = 0.0, aVolc = 0.0, aCryst = 0.0, aTemp = 0.0;
        double aHum = 0.0, aVeg = 0.0, aPrecip = 0.0, aWater = 0.0, aCont = 0.0, aRelief = 0.0;
        double bestKernel = 0.0;
        int bestId = -1;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                double sx = lattice.siteX(cx, cz);
                double sz = lattice.siteZ(cx, cz);
                double ddx = qx - sx;
                double ddz = qz - sz;
                double d = ddx * ddx + ddz * ddz;

                // Strict '<' with the loops scanning (dz, dx) in increasing order resolves an
                // exact distance tie in favour of the lower linear cell index (cz, cx) —
                // deterministic and geometrically neutral. No hash can move a border.
                if (d < bestD1) {
                    bestD2 = bestD1;
                    secondCx = bestCx; secondCz = bestCz;
                    secondSx = bestSx; secondSz = bestSz;
                    bestD1 = d; bestCx = cx; bestCz = cz; bestSx = sx; bestSz = sz;
                } else if (d < bestD2) {
                    bestD2 = d; secondCx = cx; secondCz = cz; secondSx = sx; secondSz = sz;
                }

                // Continuous attribute blend: the SAME fixed window, one pass, one geometry.
                if (!skipBlend && d < BLEND_RADIUS_SQ) {
                    double w = kernel(Math.sqrt(d) / BLEND_RADIUS);
                    if (w > 0.0) {
                        int id = catalog.indexOf(cx, cz);
                        ProvinceArchetype p = catalog.archetypeAt(id);
                        mass += w;
                        aHill += w * p.hillMultiplier();
                        aMount += w * p.mountainMultiplier();
                        aUplift += w * p.uplift();
                        aBias += w * p.bias();
                        aRough += w * p.roughness();
                        aCarve += w * p.carve();
                        aDune += w * p.dune();
                        aVolc += w * p.volcanic();
                        aCryst += w * p.crystal();
                        aTemp += w * p.temperatureBias();
                        aHum += w * p.humidityBias();
                        aVeg += w * p.vegetationBias();
                        aPrecip += w * p.precipitationBias();
                        aWater += w * p.waterAffinity();
                        aCont += w * p.continentalnessBias();
                        aRelief += w * p.reliefAffinity();
                        if (w > bestKernel) {
                            bestKernel = w;
                            bestId = id;
                        }
                    }
                }
            }
        }
        // A 7x7 window always yields at least two distinct cells; stay defensive so a
        // degenerate future window can never produce NaN geometry.
        if (secondCx == bestCx && secondCz == bestCz) {
            secondCx = bestCx + 1;
            secondCz = bestCz;
            bestD2 = bestD1 + 1.0;
        }

        out.primaryCellX = bestCx;
        out.primaryCellZ = bestCz;
        out.secondaryCellX = secondCx;
        out.secondaryCellZ = secondCz;
        out.d1 = bestD1;
        out.d2 = bestD2;

        // 3. The exact q-space bisector signed distance:
        //       signedDistanceQ = (D2 - D1) / (2L),  L = |s2 - s1|
        //    Positive on the PRIMARY side, exactly zero on the bisector.
        double lx = secondSx - bestSx;
        double lz = secondSz - bestSz;
        double separation = Math.sqrt(lx * lx + lz * lz);
        out.siteSeparation = separation;
        double signedDistance = separation > 0.0
                ? (bestD2 - bestD1) / (2.0 * separation) : 0.0;
        out.signedBoundaryDistance = signedDistance;

        // 4. transitionWeight, derived from that SAME geometric quantity. This is the ONLY thing
        //    the transition half-width H controls: it scales the band in block space and nothing
        //    else. It cannot move a border, because the border comes from the argmin above.
        double t = Math.abs(signedDistance) / transitionHalfWidth;
        out.transitionWeight = t >= 1.0 ? 1.0 : t * t * (3.0 - 2.0 * t);

        // 5. Normalise the continuous attribute blend.
        out.kernelMass = mass;
        if (mass <= 1.0e-12) {
            // Unreachable with the shipped parameters (the nearest site is always inside the
            // support), but a defined neutral answer beats NaN propagation.
            resetNeutral(out);
            out.provinceId = bestId < 0 ? 0 : bestId;
            out.secondaryProvinceId = out.provinceId;
            out.province = catalog.archetypeAt(out.provinceId);
            out.secondaryProvince = out.province;
            out.provinceWeight = catalog.relativeWeight(out.provinceId);
            out.coreShare = 1.0;
            return;
        }
        double inv = 1.0 / mass;
        out.hillMultiplier = aHill * inv;
        out.mountainMultiplier = aMount * inv;
        out.uplift = aUplift * inv;
        out.bias = aBias * inv;
        out.roughness = aRough * inv;
        out.carve = aCarve * inv;
        out.dune = aDune * inv;
        out.volcanic = aVolc * inv;
        out.crystal = aCryst * inv;
        out.temperatureBias = aTemp * inv;
        out.humidityBias = aHum * inv;
        out.vegetationBias = aVeg * inv;
        out.precipitationBias = aPrecip * inv;
        out.waterAffinity = aWater * inv;
        out.continentalnessBias = aCont * inv;
        out.reliefAffinity = aRelief * inv;
        // Blend purity: the dominant kernel mass share (1 = a pure single-site core).
        out.coreShare = bestKernel / mass;
        if (bestId >= 0) {
            out.provinceId = bestId;
            out.province = catalog.archetypeAt(bestId);
            out.provinceWeight = catalog.relativeWeight(bestId);
        }
        // The secondary archetype is the archetype of the SECOND nearest SITE — the exact
        // geometric partner of the boundary distance computed above. When both neighbours
        // happen to share the primary archetype there is genuinely no distinct neighbour; the
        // tests assert that case explicitly rather than papering over it.
        out.secondaryProvinceId = catalog.indexOf(secondCx, secondCz);
        out.secondaryProvince = catalog.archetypeAt(out.secondaryProvinceId);
    }

    private static void resetNeutral(MacroSample out) {
        out.hillMultiplier = 1.0;
        out.mountainMultiplier = 1.0;
        out.uplift = 1.0;
        out.bias = 0.0;
        out.roughness = 1.0;
        out.carve = 1.0;
        out.dune = 0.0;
        out.volcanic = 0.0;
        out.crystal = 0.0;
        out.temperatureBias = 0.0;
        out.humidityBias = 0.0;
        out.vegetationBias = 0.5;
        out.precipitationBias = 0.5;
        out.waterAffinity = 0.5;
        out.continentalnessBias = 0.5;
        out.reliefAffinity = 0.5;
    }

    /**
     * Value equality on the IDENTITY SEEDS of the three collaborators. Two geographies
     * built from the same planet seed and environment are equal, which is what makes a whole
     * {@code PlanetWorldgenProfile} reproducible and comparable.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MacroGeography other)) return false;
        return lattice.seed() == other.lattice.seed()
                && catalog.equals(other.catalog);
    }

    @Override
    public int hashCode() {
        return lattice.seed() == 0 ? catalog.hashCode()
                : (int) (lattice.seed() * 31 + catalog.hashCode());
    }

    @Override
    public String toString() {
        return "MacroGeography[cells=" + catalog.archetypes() + ", cell="
                + MacroSiteLattice.CELL_SIZE + ", window="
                + (2 * WINDOW_RADIUS + 1) + "x" + (2 * WINDOW_RADIUS + 1) + "]";
    }
}
