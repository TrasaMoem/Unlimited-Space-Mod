package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — a HEADLESS geography report.
 *
 * <p>This exists because a visual worldgen defect must be diagnosable WITHOUT launching
 * Minecraft. It prints, for one planet seed: the measured warp JACOBIAN statistics (the hard
 * gate and its companions), the macro metrics that actually detect the old failure modes, the
 * 7&times;7 versus 9&times;9 oracle agreement, and the hot-path cost.
 *
 * <p>Pure domain: no Minecraft types. Runnable from a plain JUnit test or a {@code main}.
 */
public final class GeographyReport {

    private GeographyReport() {}

    /** A formatted multi-section report over a default scan window. */
    public static String of(long planetSeed) {
        return of(planetSeed, 30000, 24, 48);
    }

    /** A formatted report over the given scan window. */
    public static String of(long planetSeed, int halfSpan, int step, int transects) {
        return of(planetSeed, halfSpan, step, transects,
                MacroGeography.DEFAULT_TRANSITION_HALF_WIDTH, MacroGeography.KERNEL_EXPONENT_DEFAULT);
    }

    /**
     * A formatted report at an EXPLICIT calibration point. The two knobs are independent: H
     * scales the transition band in block space and nothing else, while the kernel exponent
     * sharpens the continuous attribute blend. Neither can move a province border, which comes
     * from the nearest-site argmin alone.
     */
    public static String of(long planetSeed, int halfSpan, int step, int transects,
                            double transitionHalfWidth, int kernelExponent) {
        PlanetaryEnvironment env = PlanetaryEnvironment.ofScalars(
                0.50, 0.50, 0.25, 0.20, 0.10, 0.50);
        MacroGeography g = MacroGeography.calibrated(planetSeed, env,
                transitionHalfWidth, kernelExponent);
        StringBuilder sb = new StringBuilder();
        sb.append("==== WORLDGEN V2 GEOGRAPHY REPORT ====\n");
        sb.append(String.format(java.util.Locale.ROOT,
                "seed=%d  cell=%d  window=%dx%d  catalog=%s%n",
                planetSeed, MacroSiteLattice.CELL_SIZE,
                2 * MacroGeography.WINDOW_RADIUS + 1, 2 * MacroGeography.WINDOW_RADIUS + 1,
                g.catalog().archetypes()));
        sb.append(String.format(java.util.Locale.ROOT,
                "lattice rotation=%.4f rad  table period=%d blocks  blend radius=%.0f%n"
                        + "ACTIVE KNOBS  H=%.1f blocks  kernel exponent=%d  (k=(1-t^2)^K)%n",
                g.lattice().rotation(), g.lattice().periodBlocks(), MacroGeography.BLEND_RADIUS,
                g.transitionHalfWidth(), g.kernelExponent()));
        sb.append('\n').append(warpSection(g.warp(), planetSeed));
        sb.append('\n').append(metricsSection(GeographyMetrics.measure(
                g, halfSpan, step, transects)));
        sb.append('\n').append(oracleSection(g));
        return sb.toString();
    }
    private static String warpSection(BoundedWarp warp, long seed) {
        BoundedWarp.Diagnostic d = warp.diagnose(40_000, seed);
        return String.format(java.util.Locale.ROOT,
                "-- WARP JACOBIAN (JF = I + JD, finite differences h=%.2f, %d points) --%n"
                        + "HARD GATE  min singular value : %.6f  (must exceed %.2f) -> %s%n"
                        + "           max singular value : %.6f%n"
                        + "           max |JD|            : %.6f%n"
                        + "           max |D|            : %.3f blocks%n"
                        + "           max |D''| estimate  : %.3e%n"
                        + "SUPPLEMENT min determinant    : %.6f (reported, never the gate)%n"
                        + "           unsafe samples     : %d%n"
                        + "           theoretical |JD|   : %.6f (analytic bound)%n",
                d.finiteDifferenceStep(), d.samples(),
                d.minJacobianSingular(), BoundedWarp.SAFETY_MARGIN,
                d.safe() ? "PASS" : "FAIL",
                d.maxJacobianSingular(), d.maxWarpGradient(), d.maxWarpMagnitude(),
                d.maxSecondDerivativeEstimate(), d.minDeterminant(), d.unsafeSamples(),
                warp.theoreticalMaxPerturbation());
    }

    private static String metricsSection(GeographyMetrics m) {
        return String.format(java.util.Locale.ROOT,
                "-- MACRO METRICS (%d samples, %d transects, step %d) --%n"
                        + "run length p10/median/p90/max : %.0f / %.0f / %.0f / %.0f blocks%n"
                        + "area CV                     : %.3f%n"
                        + "isolated fragments (SITE)   : %d%n"
                        + "short A-B-A (SITE key)      : %d%n"
                        + "isolated fragments (ARCH)   : %d%n"
                        + "short A-B-A (ARCH key)     : %d%n"
                        + "distinct archetypes seen    : %d%n"
                        + "orientation entropy         : %.4f (1 = every direction)%n"
                        + "mean straight run           : %.0f blocks%n"
                        + "snap share (full identity)  : %.4f%n"
                        + "move share (warp vs unwarped, SITE) : %.4f%n"
                        + "move share (warp vs unwarped, ARCH) : %.4f%n"
                        + "transition width p50 (TRUE median)  : %.1f blocks%n"
                        + "transition width mean       : %.1f blocks%n"
                        + "core share (blend purity)   : %.4f%n"
                        + "attribute gradient p99      : %.5f / 100 blocks%n"
                        + "mean neighbours per transect: %.2f%n"
                        + "mean border curvature       : %.4f%n",
                m.samples(), m.transects(), m.cellSize(),
                m.runP10(), m.runMedian(), m.runP90(), m.runMax(),
                m.areaCv(), m.isolatedFragments(), m.shortAbaEvents(),
                m.archetypeIsolated(), m.archetypeShortAba(), m.dominantCount(),
                m.orientationEntropy(), m.straightRunP90(),
                m.snapShare(), m.moveShare(), m.archetypeMoveShare(),
                m.transitionWidthP50(), m.transitionWidthP50Mean(), m.coreShare(),
                m.attributeGradientP99(), m.meanNeighbourCount(), m.meanCurvature());
    }

    private static String oracleSection(MacroGeography g) {
        MacroSample fast = new MacroSample();
        MacroSample oracle = new MacroSample();
        int mismatch = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            int x = (i * 149) % 400000 - 200000;
            int z = (i * 271) % 400000 - 200000;
            g.sample(x, z, fast);
            g.sampleOracle(x, z, oracle);
            if (fast.provinceId != oracle.provinceId
                    || fast.secondaryProvinceId != oracle.secondaryProvinceId) mismatch++;
        }
        return String.format(java.util.Locale.ROOT,
                "-- CANDIDATE WINDOW VALIDATION --%n"
                        + "7x7 (production) vs 9x9 (oracle) mismatches: %d / %d%n",
                mismatch, n);
    }

    /** A standalone entry point, so the report can be produced without a test runner. */
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 0x5EEDCAFE0L;
        System.out.println(of(seed));
    }
}