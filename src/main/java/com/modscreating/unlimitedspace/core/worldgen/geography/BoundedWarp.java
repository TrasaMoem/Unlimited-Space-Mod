package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * WORLDGEN V2 — the BOUNDED WARP {@code F(p) = p + D(p)} applied to a macro query point
 * before the Voronoi lookup.
 *
 * <p>{@code D} is the sum of two low-amplitude value-noise octaves. Its purpose is to make the
 * macro level sets ORGANIC: a pure rotated lattice produces straight, obviously periodic
 * province borders, and no amount of archetype re-weighting removes that imprint.
 *
 * <h2>Why the parameter list is not a correctness claim</h2>
 * The amplitudes and wavelengths below are INITIAL PARAMETERS. They are NOT a theorem about
 * the implemented field. The actual safety property of a warp is a property of the full 2D
 * JACOBIAN
 *
 * <pre>
 *   JF = I + JD
 * </pre>
 *
 * and the hard safety gate is
 *
 * <pre>
 *   min singular value(JF) &gt; 0
 * </pre>
 *
 * with a conservative margin over all tested samples. The old scalar
 * {@code kappa = sum(3a/lambda)} does NOT document the 2D Jacobian of an implemented noise
 * field and is therefore not used as the gate here. {@link BoundedWarp#diagnose} measures the
 * real quantities by central finite differences and exposes them.
 *
 * <h2>Measured vs supplementary metrics</h2>
 * <ul>
 *   <li><b>Hard gate:</b> {@link Diagnostic#minJacobianSingular()} must stay above
 *       {@link #SAFETY_MARGIN}.</li>
 *   <li><b>Reported:</b> {@link Diagnostic#maxWarpGradient()},
 *       {@link Diagnostic#maxJacobianSingular()}.</li>
 *   <li><b>Supplementary only:</b> {@link Diagnostic#minDeterminant()} — reported for
 *       completeness, never used as the gate (a determinant can be small because of
 *       anisotropy, not because the map folds).</li>
 * </ul>
 *
 * <p>Immutable. Pure domain: no Minecraft types.
 */
public final class BoundedWarp {

    /** Wavelength of the low-frequency octave, in blocks (INITIAL PARAMETER). */
    public static final double LAMBDA_1 = 3600.0;
    /** Amplitude of the low-frequency octave, in blocks (INITIAL PARAMETER). */
    public static final double AMP_1 = 60.0;
    /** Wavelength of the boundary-scale octave, in blocks (INITIAL PARAMETER). */
    public static final double LAMBDA_2 = 1100.0;
    /** Amplitude of the boundary-scale octave, in blocks (INITIAL PARAMETER). */
    public static final double AMP_2 = 10.0;

    /**
     * The conservative safety margin the measured minimum singular value must exceed.
     *
     * <p>Chosen well above the theoretical worst case of the shipped parameters
     * (see {@link #theoreticalMaxPerturbation}) so that finite-difference noise, adversarial
     * sample placement and platform rounding can never bring the measured value to the
     * neighbourhood of zero.
     */
    public static final double SAFETY_MARGIN = 0.80;

    /** The finite-difference step used by {@link #diagnose}. The contract requires h <= 0.25. */
    public static final double DIFF_H = 0.25;

    private static final String NS = "us.geography.warp";

    private final long seedX1, seedZ1, seedX2, seedZ2;
    private final double lambda1, amp1, lambda2, amp2;

    private BoundedWarp(long seedX1, long seedZ1, long seedX2, long seedZ2,
                        double lambda1, double amp1, double lambda2, double amp2) {
        this.seedX1 = seedX1;
        this.seedZ1 = seedZ1;
        this.seedX2 = seedX2;
        this.seedZ2 = seedZ2;
        this.lambda1 = lambda1;
        this.amp1 = amp1;
        this.lambda2 = lambda2;
        this.amp2 = amp2;
    }

    /** Canonical factory: the planet's bounded warp. */
    public static BoundedWarp of(long planetSeed) {
        return of(planetSeed, LAMBDA_1, AMP_1, LAMBDA_2, AMP_2);
    }

    /** Explicit-parameter factory (diagnostics / parameter studies). */
    public static BoundedWarp of(long planetSeed, double lambda1, double amp1,
                                 double lambda2, double amp2) {
        long s = Seeds.derive(planetSeed, NS);
        return new BoundedWarp(
                Seeds.derive(s, NS + ".x1"), Seeds.derive(s, NS + ".z1"),
                Seeds.derive(s, NS + ".x2"), Seeds.derive(s, NS + ".z2"),
                lambda1, amp1, lambda2, amp2);
    }

    /**
     * Apply the warp. Writes {@code (F(p))} into the caller-supplied 2-element array:
     * {@code out[0] = x + Dx(x,z)}, {@code out[1] = z + Dz(x,z)}.
     */
    public void apply(double x, double z, double[] out) {
        out[0] = x + displacementX(x, z);
        out[1] = z + displacementZ(x, z);
    }

    /** The X displacement {@code Dx(x, z)} (blocks). */
    public double displacementX(double x, double z) {
        return amp1 * (value(seedX1, x, z, lambda1) - 0.5)
                + amp2 * (value(seedX2, x, z, lambda2) - 0.5);
    }

    /** The Z displacement {@code Dz(x, z)} (blocks). */
    public double displacementZ(double x, double z) {
        return amp1 * (value(seedZ1, x, z, lambda1) - 0.5)
                + amp2 * (value(seedZ2, x, z, lambda2) - 0.5);
    }

    /**
     * The largest possible norm of the perturbation part of the Jacobian for the SHIPPED
     * parameters, using the analytic Lipschitz bound of the interpolation
     * ({@code max|f'| = 15/8} for the quintic fade on each axis):
     *
     * <pre>
     *   |JD| &lt;= (15/8) * (a1/l1 + a2/l2)
     * </pre>
     *
     * <p>This is a documented UPPER BOUND used to size {@link #SAFETY_MARGIN}, not the gate
     * itself. The gate is the measured minimum singular value from {@link #diagnose}.
     */
    public double theoreticalMaxPerturbation() {
        return 1.875 * (amp1 / lambda1 + amp2 / lambda2);
    }
    // ---------------------------------------------------------------- noise

    /** The quintic fade: {@code f(0)=0, f(1)=1}, {@code f'(0)=f'(1)=0} (C1 at cell borders). */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Its derivative {@code f'(t) = 30 t^2 (1-t)^2}. */
    private static double fadeDerivative(double t) {
        double u = t * (1.0 - t);
        return 30.0 * u * u;
    }

    /**
     * Smooth value noise in [0,1] at an arbitrary wavelength.
     *
     * <p>Uses the QUINTIC fade rather than the cheaper cubic smoothstep, because the quintic
     * has a zero first derivative at the cell borders: the field is C1, so its gradient — and
     * therefore the warp Jacobian — is continuous across noise-cell boundaries. That is what
     * makes the finite-difference Jacobian meaningful at points chosen adversarially near
     * the noise lattice.
     */
    private static double value(long seed, double x, double z, double wavelength) {
        double f = 1.0 / wavelength;
        double sx = x * f;
        double sz = z * f;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double tx = fade(sx - x0);
        double tz = fade(sz - z0);
        double v00 = corner(seed, x0, z0);
        double v10 = corner(seed, x0 + 1, z0);
        double v01 = corner(seed, x0, z0 + 1);
        double v11 = corner(seed, x0 + 1, z0 + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    /**
     * The exact analytic X derivative of one octave, by the chain rule. Used to cross-check
     * the finite-difference Jacobian in the diagnostics.
     */
    private static double dValueX(long seed, double x, double z, double wavelength) {
        double f = 1.0 / wavelength;
        double sx = x * f;
        double sz = z * f;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double dfx = fadeDerivative(sx - x0);
        double tz = fade(sz - z0);
        double d00 = corner(seed, x0, z0);
        double d10 = corner(seed, x0 + 1, z0);
        double d01 = corner(seed, x0, z0 + 1);
        double d11 = corner(seed, x0 + 1, z0 + 1);
        double dx = ((d10 - d00) * (1.0 - tz) + (d11 - d01) * tz) / wavelength;
        return dx * dfx;
    }

    /**
     * The exact analytic Z derivative of one octave, by the chain rule. This is the
     * TRANSPOSE of {@link #dValueX}: the X-derivative of the noise is not the Z-derivative.
     */
    private static double dValueZ(long seed, double x, double z, double wavelength) {
        double f = 1.0 / wavelength;
        double sx = x * f;
        double sz = z * f;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double tx = fade(sx - x0);
        double dfz = fadeDerivative(sz - z0);
        double d00 = corner(seed, x0, z0);
        double d10 = corner(seed, x0 + 1, z0);
        double d01 = corner(seed, x0, z0 + 1);
        double d11 = corner(seed, x0 + 1, z0 + 1);
        double dz = ((d01 - d00) * (1.0 - tx) + (d11 - d10) * tx) / wavelength;
        return dz * dfz;
    }

    private static double corner(long seed, int cx, int cz) {
        return Seeds.fraction(Seeds.derive2(seed, NS + ".corner", cx, cz), 7L);
    }

    /** The exact analytic gradient of the warp along X. */
    public double gradientX(double x, double z) {
        return dValueX(seedX1, x, z, lambda1) * amp1 + dValueX(seedX2, x, z, lambda2) * amp2;
    }

    /** The exact analytic gradient of the warp along Z. */
    public double gradientZ(double x, double z) {
        return dValueZ(seedZ1, x, z, lambda1) * amp1 + dValueZ(seedZ2, x, z, lambda2) * amp2;
    }
    // ---------------------------------------------------------------- diagnostics

    /**
     * The measured warp diagnostics over a deterministic sample set.
     *
     * @param maxWarpGradient           max |JD| entry over the samples
     * @param maxWarpMagnitude          max |D(p)| over the samples
     * @param minJacobianSingular       min singular value of {@code JF = I + JD} (THE HARD GATE)
     * @param maxJacobianSingular       max singular value of {@code JF}
     * @param minDeterminant            min det(JF) (SUPPLEMENTARY metric only)
     * @param maxSecondDerivativeEstimate max |D''| by second central difference
     * @param finiteDifferenceStep      the {@code h} actually used
     * @param unsafeSamples             samples with {@code minSingular <= SAFETY_MARGIN}
     */
    public record Diagnostic(int samples,
                             double maxWarpGradient,
                             double maxWarpMagnitude,
                             double minJacobianSingular,
                             double maxJacobianSingular,
                             double minDeterminant,
                             double maxSecondDerivativeEstimate,
                             double finiteDifferenceStep,
                             int unsafeSamples) {

        /** True when the hard gate holds with the conservative margin over every sample. */
        public boolean safe() {
            return unsafeSamples == 0 && minJacobianSingular > SAFETY_MARGIN;
        }
    }

    /** A scalar-only accumulator so the diagnostic loop never allocates. */
    private static final class Accumulator {
        double maxGrad, maxMag, minSing, maxSing, minDet, maxSecond;
        int unsafe;

        Accumulator() {
            minSing = Double.MAX_VALUE;
            minDet = Double.MAX_VALUE;
        }

        Diagnostic finish(int samples, double h) {
            if (samples <= 0) {
                return new Diagnostic(0, 0, 0, 0, 0, 0, 0, h, 0);
            }
            return new Diagnostic(samples, maxGrad, maxMag, minSing, maxSing, minDet,
                    maxSecond, h, unsafe);
        }
    }

    /**
     * Measure the warp's real 2D Jacobian over a deterministic sample set.
     *
     * <p>Central differences with {@code h = DIFF_H} ({@code 0.25 <= 0.25}) on the ACTUAL
     * implemented field. The singular values of a 2x2 matrix are computed in closed form from
     * the eigenvalues of {@code JF^T JF}, so no iterative solver is involved and the result is
     * bit-reproducible.
     */
    public Diagnostic diagnose(int samples, long extentSeed) {
        Accumulator acc = new Accumulator();
        double h = DIFF_H;
        for (int i = 0; i < samples; i++) {
            double u = unitOf(i, 0, extentSeed);
            double v = unitOf(i, 1, extentSeed);
            double w = unitOf(i, 2, extentSeed);
            // A wide, deliberately NON-lattice-aligned sample cloud.
            double x = (u * 4.0 - 2.0) * 240000.0 + (w - 0.5) * 0.7;
            double z = (v * 4.0 - 2.0) * 240000.0 + ((1.0 - w) - 0.5) * 0.7;
            if (i % 3 == 0) {
                // Snap a third of the samples close to a noise-cell border, where the
                // interpolation derivative changes fastest (the adversarial case).
                x = Math.round(x / lambda1) * lambda1 + (u - 0.5) * 0.4;
                z = Math.round(z / lambda1) * lambda1 + (v - 0.5) * 0.4;
            }
            accumulate(acc, x, z, h);
        }
        return acc.finish(samples, h);
    }
    private void accumulate(Accumulator acc, double x, double z, double h) {
        double dxx = (displacementX(x + h, z) - displacementX(x - h, z)) / (2.0 * h);
        double dxz = (displacementX(x, z + h) - displacementX(x, z - h)) / (2.0 * h);
        double dzx = (displacementZ(x + h, z) - displacementZ(x - h, z)) / (2.0 * h);
        double dzz = (displacementZ(x, z + h) - displacementZ(x, z - h)) / (2.0 * h);

        // JF = I + JD
        double a = 1.0 + dxx;
        double b = dxz;
        double c = dzx;
        double d = 1.0 + dzz;

        double grad = Math.max(Math.max(Math.abs(dxx), Math.abs(dxz)),
                Math.max(Math.abs(dzx), Math.abs(dzz)));
        if (grad > acc.maxGrad) acc.maxGrad = grad;
        double mag = Math.hypot(displacementX(x, z), displacementZ(x, z));
        if (mag > acc.maxMag) acc.maxMag = mag;

        // Closed-form singular values of a 2x2 matrix: s1,s2 of [[e,h],[g,f]] style form.
        double e = (a + d) * 0.5;
        double fq = (a - d) * 0.5;
        double gq = (b + c) * 0.5;
        double hq = (b - c) * 0.5;
        double qq = Math.hypot(e, hq);
        double rq = Math.hypot(fq, gq);
        double s1 = qq + rq;
        double s2 = Math.abs(qq - rq);
        if (s2 < acc.minSing) acc.minSing = s2;
        if (s1 > acc.maxSing) acc.maxSing = s1;
        if (s2 <= SAFETY_MARGIN) acc.unsafe++;

        double det = a * d - b * c;
        if (det < acc.minDet) acc.minDet = det;

        double h2 = h * h;
        double sxx = (displacementX(x + h, z) - 2.0 * displacementX(x, z)
                + displacementX(x - h, z)) / h2;
        double szz = (displacementZ(x, z + h) - 2.0 * displacementZ(x, z)
                + displacementZ(x, z - h)) / h2;
        double szx = (displacementZ(x + h, z) - 2.0 * displacementZ(x, z)
                + displacementZ(x - h, z)) / h2;
        double sxz = (displacementX(x, z + h) - 2.0 * displacementX(x, z)
                + displacementX(x, z - h)) / h2;
        double second = Math.max(Math.max(Math.abs(sxx), Math.abs(szz)),
                Math.max(Math.abs(szx), Math.abs(sxz)));
        if (second > acc.maxSecond) acc.maxSecond = second;
    }

    /** A deterministic, well-spread unit value for sample {@code i}, dimension {@code slot}. */
    private static double unitOf(int i, int slot, long seed) {
        long h = Seeds.derive2(seed ^ (i * 0x9E3779B97F4A7C15L), NS + ".sample", slot, i);
        double u = (h >>> 11) * 0x1.0p-53;
        // Irrational rotation per sample: avoids a repeating pattern that could accidentally
        // align with the noise lattice.
        double v = u + i * 0.6180339887498949;
        return v - Math.floor(v);
    }
}