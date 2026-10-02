package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLDGEN V2.1 — the CALIBRATION contract for the two INDEPENDENT controls.
 *
 * <p>The original §7 asked for one knob to fix two independent quantities. They are not one knob:
 *
 * <ul>
 *   <li>{@code H = transitionHalfWidth} feeds ONLY {@code transitionWeight} (and therefore
 *       {@code snapShare} and the measured band width). It cannot move a province border.</li>
 *   <li>{@code K = kernelExponent} shapes ONLY the continuous attribute blend
 *       (and therefore {@code coreShare} and the attribute gradient). It cannot move a border
 *       either, and it does not change {@code BLEND_RADIUS}, so the coverage guarantee holds.</li>
 * </ul>
 *
 * <p>These tests pin that separation, then sweep both controls and report the measured table.
 */
@DisplayName("V2.1 geography calibration")
@Tag("worldgen")
@Tag("audit")
class GeographyCalibrationTest {

    /** The target band for the TRUE median transition width, in blocks. */
    private static final double WIDTH_LO = 150.0;
    private static final double WIDTH_HI = 350.0;
    /** The coreShare target, i.e. a dominant site must own most of the kernel mass. */
    private static final double CORE_TARGET = 0.75;

    private static PlanetaryEnvironment env() {
        return PlanetaryEnvironment.ofScalars(0.50, 0.50, 0.25, 0.20, 0.10, 0.50);
    }

    private static MacroGeography geo(long seed, double h, int exponent) {
        return MacroGeography.calibrated(seed, env(), h, exponent);
    }

    private static long[] seedRange(int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) out[i] = 0x5EED0000L + i * 0x9E3779B9L;
        return out;
    }

    // ================================================================ the two controls are independent

    @Test
    @DisplayName("H changes the transition band and nothing else")
    void h_controlsOnlyTheTransitionBand() {
        MacroSample a = new MacroSample();
        MacroSample b = new MacroSample();
        for (long seed : seedRange(8)) {
            MacroGeography wide = geo(seed, 300.0, 4);
            MacroGeography narrow = geo(seed, 120.0, 4);
            for (int i = 0; i < 4000; i++) {
                int x = (i * 401) % 300000 - 150000;
                int z = (i * 787) % 300000 - 150000;
                wide.sample(x, z, a);
                narrow.sample(x, z, b);
                // Ownership and the geometry behind it are IDENTICAL: H is not an input to the
                // argmin, so it cannot move a border or change a distance.
                assertEquals(a.provinceId, b.provinceId, "H must not change ownership");
                assertEquals(a.secondaryProvinceId, b.secondaryProvinceId,
                        "H must not change the secondary province");
                assertEquals(a.d1, b.d1, 1.0e-9, "H must not change D1");
                assertEquals(a.signedBoundaryDistance, b.signedBoundaryDistance, 1.0e-9,
                        "H must not change the signed boundary distance");
                // ...and it does change the transition weight, monotonically: a narrower band
                // saturates sooner, so a smaller H can only raise the weight.
                assertTrue(b.transitionWeight >= a.transitionWeight - 1.0e-12,
                        "a smaller H must not lower the transition weight");
            }
        }
    }

    @Test
    @DisplayName("the kernel exponent changes the blend and nothing else")
    void kernelExponentControlsOnlyTheBlend() {
        MacroSample a = new MacroSample();
        MacroSample b = new MacroSample();
        for (long seed : seedRange(8)) {
            MacroGeography soft = geo(seed, 300.0, 4);
            MacroGeography sharp = geo(seed, 300.0, 8);
            for (int i = 0; i < 4000; i++) {
                int x = (i * 401) % 300000 - 150000;
                int z = (i * 787) % 300000 - 150000;
                soft.sample(x, z, a);
                sharp.sample(x, z, b);
                assertEquals(a.provinceId, b.provinceId, "K must not change ownership");
                assertEquals(a.signedBoundaryDistance, b.signedBoundaryDistance, 1.0e-9,
                        "K must not change the boundary distance");
                assertEquals(a.transitionWeight, b.transitionWeight, 1.0e-12,
                        "K must not change the transition weight");
            }
        }
    }
    // ================================================================ H calibration

    @Test
    @DisplayName("the true median band width lands in 150..350 and scales with H")
    void h_hitsTheTrueMedianWidthTarget() {
        System.out.println("==== V2.1 CALIBRATION: transition half-width H ====");
        System.out.printf(Locale.ROOT, "%6s %10s %10s %10s%n",
                "H", "p50(blocks)", "mean", "snapShare");
        double chosen = Double.NaN;
        for (double h : new double[]{80.0, 100.0, 120.0, 150.0, 180.0, 200.0, 240.0, 300.0}) {
            double sumP50 = 0.0;
            double sumMean = 0.0;
            double sumSnap = 0.0;
            int n = 0;
            for (long seed : seedRange(8)) {
                GeographyMetrics m = GeographyMetrics.measure(geo(seed, h, 4), 30000, 24, 48);
                sumP50 += m.transitionWidthP50();
                sumMean += m.transitionWidthP50Mean();
                sumSnap += m.snapShare();
                n++;
            }
            double p50 = sumP50 / n;
            System.out.printf(Locale.ROOT, "%6.0f %10.1f %10.1f %10.4f%n",
                    h, p50, sumMean / n, sumSnap / n);
            if (chosen != chosen && p50 >= WIDTH_LO && p50 <= WIDTH_HI) chosen = h;
        }
        assertTrue(chosen == chosen,
                "no H in the swept range produced a true median band width inside "
                        + WIDTH_LO + ".." + WIDTH_HI);
    }

    // ================================================================ kernel exponent calibration

    @Test
    @DisplayName("kernel exponent sweep: coreShare, gradient and band width over 8+ seeds")
    void kernelExponentSweepIsReported() {
        System.out.println("==== V2.1 CALIBRATION: blend kernel exponent (H fixed at 300) ====");
        System.out.printf(Locale.ROOT, "%4s %10s %12s %12s %10s %12s%n",
                "K", "coreShare", "gradP99", "p50(width)", "snapShare", "moveShare");
        for (int k : MacroGeography.CALIBRATION_KERNEL_EXPONENTS) {
            double sumCore = 0.0;
            double sumGrad = 0.0;
            double sumP50 = 0.0;
            double sumSnap = 0.0;
            double sumMove = 0.0;
            int n = 0;
            for (long seed : seedRange(8)) {
                GeographyMetrics m = GeographyMetrics.measure(geo(seed, 300.0, k), 30000, 24, 48);
                sumCore += m.coreShare();
                sumGrad += m.attributeGradientP99();
                sumP50 += m.transitionWidthP50();
                sumSnap += m.snapShare();
                sumMove += m.moveShare();
                n++;
            }
            System.out.printf(Locale.ROOT, "%4d %10.4f %12.5f %12.1f %10.4f %12.5f%n",
                    k, sumCore / n, sumGrad / n, sumP50 / n, sumSnap / n, sumMove / n);
        }
        // The exponent sweep must actually move coreShare, otherwise the control is inert and
        // the §7 contradiction would not have been resolvable at all.
        long first = seedRange(8)[0];
        double softCore = GeographyMetrics.measure(
                geo(first, 300.0, MacroGeography.KERNEL_EXPONENT_DEFAULT), 30000, 24, 48).coreShare();
        double sharpCore = GeographyMetrics.measure(
                geo(first, 300.0, 8), 30000, 24, 48).coreShare();
        assertTrue(sharpCore > softCore,
                "raising the kernel exponent must concentrate mass and raise coreShare: "
                        + softCore + " -> " + sharpCore);
    }
    @Test
    @DisplayName("report whether the coreShare target is reachable at any allowed exponent")
    void coreShareTargetReachabilityIsReported() {
        System.out.println("==== V2.1: coreShare target " + CORE_TARGET + " reachability ====");
        for (int k : MacroGeography.CALIBRATION_KERNEL_EXPONENTS) {
            double min = Double.MAX_VALUE;
            double max = 0.0;
            for (long seed : seedRange(16)) {
                double c = GeographyMetrics.measure(geo(seed, 300.0, k), 30000, 24, 48).coreShare();
                min = Math.min(min, c);
                max = Math.max(max, c);
            }
            System.out.printf(Locale.ROOT, "K=%d  coreShare min=%.4f max=%.4f  %s%n",
                    k, min, max, min >= CORE_TARGET ? "MEETS TARGET" : "below target");
        }
        // A REPORT, not a gate. The brief forbids adding further kernel powers or touching
        // ownership if the target is missed, so the honest outcome is to surface it in the log.
        // The measured sweep shows the target is NOT reachable inside the allowed design space:
        // BLEND_RADIUS is pinned at 1.6*CELL (its coverage floor 1.20*CELL is off limits) and the
        // support therefore always contains ~8 sites, so the dominant site cannot hold 75% of the
        // mass. The acceptance criterion itself is what needs review, not the parameters.
    }

    // ================================================================ diagnostic correctness

    @Test
    @DisplayName("transitionWidthP50 is a true median of the measured distance field")
    void transitionWidthIsATrueMedian() {
        for (long seed : seedRange(8)) {
            GeographyMetrics m = GeographyMetrics.measure(geo(seed, 300.0, 4), 30000, 24, 48);
            assertTrue(m.transitionWidthP50() > 0.0, "the band must have a positive width");
            assertTrue(m.transitionWidthP50() <= m.transitionWidthP50Mean() * 4.0 + 1.0e-6,
                    "the median band width is implausibly far above the mean");
            // A column's full band width is 2*|signedBoundaryDistance| and the ramp saturates at
            // H, so no measured width can exceed 2H.
            assertTrue(m.transitionWidthP50() <= 2.0 * 300.0 + 1.0e-6,
                    "the measured band width exceeds 2H, which is impossible by construction");
        }
    }

    @Test
    @DisplayName("moveShare compares warp against the same lattice without warp")
    void moveShareIsAnHonestWarpMeasure() {
        for (long seed : seedRange(8)) {
            MacroGeography g = geo(seed, 300.0, 4);
            GeographyMetrics m = GeographyMetrics.measure(g, 30000, 24, 48);
            // The warp is a small bounded perturbation (max ~47 blocks on a 2400 cell), so it must
            // move a real but small MINORITY of owners. The old metric reported ~1.0 because it
            // compared a rotated cell against an unrotated one and so measured the rotation.
            assertTrue(m.moveShare() > 0.0,
                    "seed " + seed + ": the warp moved no owner at all, which cannot be right");
            assertTrue(m.moveShare() < 0.5,
                    "seed " + seed + " moveShare " + m.moveShare()
                            + " is implausibly high for a ~47-block warp on a 2400-cell lattice");
            MacroSample u = new MacroSample();
            g.sampleUnwarped(1234, -5678, u);
            assertTrue(u.provinceId >= 0, "the unwarped path must still resolve an owner");
        }
    }

    @Test
    @DisplayName("the 7x7 window still matches the 9x9 oracle at every calibration point")
    void oracleAgreementSurvivesCalibration() {
        for (long seed : seedRange(8)) {
            for (int k : MacroGeography.CALIBRATION_KERNEL_EXPONENTS) {
                MacroGeography g = geo(seed, 300.0, k);
                MacroSample fast = new MacroSample();
                MacroSample oracle = new MacroSample();
                int mismatch = 0;
                for (int i = 0; i < 8000; i++) {
                    int x = (i * 149) % 400000 - 200000;
                    int z = (i * 271) % 400000 - 200000;
                    g.sample(x, z, fast);
                    g.sampleOracle(x, z, oracle);
                    if (fast.provinceId != oracle.provinceId
                            || fast.secondaryProvinceId != oracle.secondaryProvinceId) mismatch++;
                }
                assertEquals(0, mismatch,
                        "seed " + seed + " exponent " + k + ": 7x7 vs 9x9 mismatches");
            }
        }
    }
}