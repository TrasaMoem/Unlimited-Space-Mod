package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel.StarFluxContext;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 1 — STELLAR THERMAL &amp; ORBITAL CALIBRATION: the DISTRIBUTION test.
 *
 * <p>The R23 audit found the galaxy producing "mostly &gt; 274 K" worlds (p50 ≈ 359 K) because the
 * orbital fan was compressed inward, every planet received an unconditional +15 K of internal heat,
 * the greenhouse prior was biased towards the dense/aqueous archetypes and the luminosity contrast
 * was cancelled by the fan scaling.
 *
 * <p>This class measures the RESULT of the recalibration on a large deterministic sample
 * ({@value #WORLD_SEEDS} world seeds × {@value #SYSTEMS_PER_SEED} systems) and pins the ACT 1
 * acceptance targets:
 * <ol>
 *   <li>median in 220..280 K;</li>
 *   <li>50..300 K is the dominant population;</li>
 *   <li>cold and cryogenic worlds are common;</li>
 *   <li>&gt; 1000 K worlds exist but are uncommon; 2500..4600 K is reachable but rare;</li>
 *   <li>the 30 K and 4600 K clamps are NOT doing the population's work;</li>
 *   <li>distance and stellar luminosity both clearly drive the temperature.</li>
 * </ol>
 *
 * <p>These are DESIGN POPULATION PRIORS of the procedural universe, not measured exoplanet
 * statistics. The sample is deterministic, so the measured numbers are stable across runs.
 */
class Act1ThermalCalibrationTest {

    private static final long[] WORLD_SEEDS = {777L, 424242L, 20260922L};
    private static final int SYSTEMS_PER_SEED = 400;

    /** ACT 1 diagnostic bins in K (the recommended report buckets). */
    private static final double[][] BINS = {
            {0.0, 50.0}, {50.0, 80.0}, {80.0, 120.0}, {120.0, 180.0}, {180.0, 220.0},
            {220.0, 260.0}, {260.0, 300.0}, {300.0, 400.0}, {400.0, 600.0}, {600.0, 1000.0},
            {1000.0, 1600.0}, {1600.0, 2500.0}, {2500.0, 4601.0}};

    /** One sampled planet: the canonical thermal chain of a real generated world. */
    private record Sample(double temperature, double au, double eccentricity, double flux,
                          double equilibrium, double greenhouse, double internalK,
                          PlanetType type, StellarThermalModel.ThermalClass thermalClass,
                          StarType starType, double systemLuminosity, int starCount, int slot) {
    }

    private static List<Sample> sampleCache;

    /** Deterministic galaxy sample: every planet of {@value #SYSTEMS_PER_SEED} systems per seed. */
    private static List<Sample> sample() {
        if (sampleCache == null) {
            List<Sample> out = new ArrayList<>(4096);
            for (long worldSeed : WORLD_SEEDS) {
                Galaxy galaxy = Galaxy.from(worldSeed);
                for (int s = 0; s < SYSTEMS_PER_SEED; s++) {
                    StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
                    double luminosity = 0.0;
                    for (Star star : system.stars()) luminosity += Math.max(0.0, star.luminosity());
                    int planets = system.planetCount();
                    for (int o = 0; o < planets; o++) {
                        Planet planet = system.getPlanet(o);
                        PlanetProperties props = planet.properties();
                        PlanetThermal thermal = props.thermal();
                        out.add(new Sample(props.temperature(), thermal.orbitAU(),
                                thermal.eccentricity(), thermal.stellarFlux(),
                                thermal.equilibriumK(), thermal.greenhouse(), thermal.internalK(),
                                props.type(), thermal.thermalClass(), system.star().type(),
                                luminosity, system.stars().size(), o));
                    }
                }
            }
            sampleCache = List.copyOf(out);
        }
        return sampleCache;
    }

    private static double[] sortedTemperatures() {
        return sortedTemperatures(null);
    }

    private static double[] sortedTemperatures(StarType onlyPrimaryOf) {
        List<Sample> sample = sample();
        double[] raw = new double[sample.size()];
        int n = 0;
        for (Sample s : sample) {
            if (onlyPrimaryOf != null && s.starType() != onlyPrimaryOf) continue;
            raw[n++] = s.temperature();
        }
        double[] sorted = Arrays.copyOf(raw, n);
        Arrays.sort(sorted);
        return sorted;
    }

    private static double percentile(double[] sorted, double q) {
        int idx = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static double median(double[] sorted) {
        return sorted.length == 0 ? Double.NaN : percentile(sorted, 0.5);
    }

    private static int countBetween(double[] sorted, double lo, double hi) {
        int c = 0;
        for (double v : sorted) if (v >= lo && v <= hi) c++;
        return c;
    }

    private static double fraction(double[] sorted, double lo, double hi) {
        return sorted.length == 0 ? 0.0 : countBetween(sorted, lo, hi) / (double) sorted.length;
    }

    /* ------------------------------------------------------------ temperature distribution */

    /** ACT 1 core test: the SHAPE of the galaxy's temperature population. */
    @Test
    void planetTemperatureDistributionMatchesTheActOneTargets() {
        double[] s = sortedTemperatures();
        int n = s.length;
        assertTrue(n > 2000, "sample too small: " + n);

        int[] bins = new int[BINS.length];
        for (double t : s) {
            for (int i = 0; i < BINS.length; i++) {
                if (t >= BINS[i][0] && t < BINS[i][1]) {
                    bins[i]++;
                    break;
                }
            }
        }
        int clampLow = 0;
        int clampHigh = 0;
        int above274 = 0;
        int below80 = 0;
        for (double t : s) {
            if (t <= StellarThermalModel.T_MIN + 1e-6) clampLow++;
            if (t >= StellarThermalModel.T_MAX - 1e-6) clampHigh++;
            if (t > 274.0) above274++;
            if (t < 80.0) below80++;
        }
        double min = s[0];
        double max = s[n - 1];
        double p10 = percentile(s, 0.10);
        double p25 = percentile(s, 0.25);
        double p50 = percentile(s, 0.50);
        double p75 = percentile(s, 0.75);
        double p90 = percentile(s, 0.90);
        double p95 = percentile(s, 0.95);
        double p99 = percentile(s, 0.99);
        double share50300 = fraction(s, 50.0, 300.0);
        double shareBelow100 = fraction(s, StellarThermalModel.T_MIN, 100.0);
        double shareAbove1000 = fraction(s, 1000.0, StellarThermalModel.T_MAX);
        double shareAbove2500 = fraction(s, 2500.0, StellarThermalModel.T_MAX);
        double shareAbove274 = above274 / (double) n;

        System.out.printf(Locale.ROOT,
                "%n[ACT 1] planet temperature distribution (n=%d, %d world seeds x %d systems)%n",
                n, WORLD_SEEDS.length, SYSTEMS_PER_SEED);
        System.out.printf(Locale.ROOT,
                "  min=%.1f p10=%.1f p25=%.1f p50=%.1f p75=%.1f p90=%.1f p95=%.1f p99=%.1f max=%.1f K%n",
                min, p10, p25, p50, p75, p90, p95, p99, max);
        System.out.print("  bins:");
        for (int i = 0; i < BINS.length; i++) {
            System.out.printf(Locale.ROOT, " %.0f-%.0fK=%.1f%%", BINS[i][0], BINS[i][1],
                    100.0 * bins[i] / n);
        }
        System.out.printf(Locale.ROOT,
                "%n  share 50-300K=%.1f%%  <100K=%.1f%%  <80K=%.1f%%  >274K=%.1f%%"
                        + "  >1000K=%.1f%%  >=2500K=%.2f%%  clamp30=%d  clamp4600=%d%n",
                100.0 * share50300, 100.0 * shareBelow100, 100.0 * below80 / n,
                100.0 * shareAbove274, 100.0 * shareAbove1000, 100.0 * shareAbove2500,
                clampLow, clampHigh);
        System.out.print("  thermal classes:");
        int[] classes = new int[StellarThermalModel.ThermalClass.VALUES.length];
        for (Sample x : sample()) classes[x.thermalClass().ordinal()]++;
        for (int i = 0; i < classes.length; i++) {
            System.out.printf(Locale.ROOT, " %s=%.1f%%",
                    StellarThermalModel.ThermalClass.VALUES[i], 100.0 * classes[i] / n);
        }
        System.out.print("  bands:");
        int[] bands = new int[TemperatureBand.VALUES.length];
        for (double t : s) bands[TemperatureBand.ofKelvin(t).ordinal()]++;
        for (int i = 0; i < bands.length; i++) {
            System.out.printf(Locale.ROOT, " %s=%.1f%%", TemperatureBand.VALUES[i],
                    100.0 * bands[i] / n);
        }
        System.out.println();

        // 1) THE HEADLINE REQUIREMENT: the galaxy must NOT be mostly above freezing.
        assertTrue(shareAbove274 < 0.50,
                "the galaxy still produces mostly >274 K planets: " + (100.0 * shareAbove274) + "%");

        // 2) MEDIAN inside the ACT 1 target band.
        assertTrue(p50 >= 220.0 && p50 <= 280.0, "median outside the ACT 1 target: " + p50);

        // 3) 50..300 K is the DOMINANT population.
        assertTrue(share50300 >= 0.50, "50..300 K is not dominant: " + (100.0 * share50300) + "%");
        assertTrue(fraction(s, 300.0, 600.0) < share50300 / 2.0,
                "the 300..600 K region rivals the temperate population");

        // 4) COLD WORLDS are common; cryogenic worlds visibly exist.
        assertTrue(fraction(s, StellarThermalModel.T_MIN, 120.0) >= 0.05,
                "cryogenic worlds too rare: " + (100.0 * fraction(s, StellarThermalModel.T_MIN, 120.0)) + "%");
        assertTrue(fraction(s, StellarThermalModel.T_MIN, 200.0) >= 0.20,
                "cold worlds too rare: " + (100.0 * fraction(s, StellarThermalModel.T_MIN, 200.0)) + "%");
        assertTrue(fraction(s, StellarThermalModel.T_MIN, 80.0) >= 0.005,
                "30..80 K unreachable in practice");
        assertTrue(fraction(s, StellarThermalModel.T_MIN, 50.0) > 0.0,
                "no world at all below 50 K");

        // 5) HOT WORLDS exist but are uncommon; the 2500..4600 K span stays reachable.
        assertTrue(shareAbove1000 > 0.0, "no world above 1000 K at all");
        assertTrue(shareAbove1000 <= 0.15, "hot worlds too common: " + (100.0 * shareAbove1000) + "%");
        assertTrue(shareAbove2500 > 0.0, ">=2500 K unreachable");
        assertTrue(shareAbove2500 <= 0.03, ">=2500 K too common: " + (100.0 * shareAbove2500) + "%");

        // 6) The clamps must not be doing the population's work.
        assertTrue(clampLow / (double) n <= 0.05, "too many planets pinned at 30 K: " + clampLow);
        assertTrue(clampHigh / (double) n <= 0.005, "too many planets pinned at 4600 K: " + clampHigh);

        // 7) Physically shaped, not uniform: the warm tail decays monotonically.
        double t300 = fraction(s, 300.0, 400.0);
        double t400 = fraction(s, 400.0, 600.0);
        double t600 = fraction(s, 600.0, 1000.0);
        double t1000 = fraction(s, 1000.0, StellarThermalModel.T_MAX);
        assertTrue(t300 > t400 && t400 > t600 && t600 > t1000,
                String.format(Locale.ROOT, "warm tail not decaying: %.3f %.3f %.3f %.3f",
                        t300, t400, t600, t1000));

        // 8) The canonical band/class ladders stay fully populated at the extremes.
        assertTrue(bands[TemperatureBand.FROZEN.ordinal()] > 0, "FROZEN band unreachable");
        assertTrue(bands[TemperatureBand.INFERNO.ordinal()] > 0, "INFERNO band unreachable");
        assertTrue(classes[StellarThermalModel.ThermalClass.CRYOGENIC.ordinal()] > 0,
                "CRYOGENIC class unreachable");
        assertTrue(classes[StellarThermalModel.ThermalClass.TEMPERATE.ordinal()] > 0,
                "TEMPERATE class unreachable");
        assertTrue(classes[StellarThermalModel.ThermalClass.MOLTEN.ordinal()] > 0,
                "MOLTEN class unreachable");
    }

    /* ------------------------------------------------------------ orbital diagnostics (J) */

    @Test
    void orbitalDistributionIsBroadOrderedAndOuterWorldsAreColder() {
        List<Sample> sample = sample();
        double[] au = new double[sample.size()];
        int n = 0;
        for (Sample x : sample) au[n++] = x.au();
        double[] s = Arrays.copyOf(au, n);
        Arrays.sort(s);
        double mean = 0.0;
        for (double v : s) mean += v;
        mean /= n;

        System.out.printf(Locale.ROOT, "%n[ACT 1] orbital distance distribution (n=%d)%n", n);
        System.out.printf(Locale.ROOT,
                "  AU: min=%.3f p10=%.3f p25=%.3f p50=%.3f p75=%.3f p90=%.3f max=%.3f mean=%.3f%n",
                s[0], percentile(s, 0.10), percentile(s, 0.25), percentile(s, 0.50),
                percentile(s, 0.75), percentile(s, 0.90), s[n - 1], mean);

        // The fan must actually span the inner/outer regimes over one galaxy sample.
        assertTrue(s[0] >= OrbitProfile.AU_MIN && s[n - 1] <= OrbitProfile.AU_MAX,
                "orbit outside the AU clamp: " + s[0] + ".." + s[n - 1]);
        assertTrue(percentile(s, 0.10) < 0.9, "inner orbits missing: p10=" + percentile(s, 0.10));
        assertTrue(percentile(s, 0.50) >= 1.0 && percentile(s, 0.50) <= 2.2,
                "median orbit outside the calibrated fan: " + percentile(s, 0.50));
        assertTrue(percentile(s, 0.90) > 3.0, "outer orbits missing: p90=" + percentile(s, 0.90));

        // Per primary star type: median AU and the median temperature actually reached.
        System.out.printf(Locale.ROOT, "  by primary type:  type            n   medianAU  medianT(K)%n");
        for (StarType type : StarType.values()) {
            double[] tByStar = sortedTemperatures(type);
            if (tByStar.length == 0) continue;
            double[] auByStar = new double[tByStar.length];
            int m = 0;
            for (Sample x : sample) if (x.starType() == type) auByStar[m++] = x.au();
            auByStar = Arrays.copyOf(auByStar, m);
            Arrays.sort(auByStar);
            System.out.printf(Locale.ROOT, "    %-13s %5d  %8.3f  %10.1f%n",
                    type, m, percentile(auByStar, 0.50), median(tByStar));
        }

        // Distance invariant on the REAL galaxy: inner slots are clearly hotter than outer ones.
        double[] inner = new double[sample.size()];
        double[] outer = new double[sample.size()];
        int ni = 0;
        int no = 0;
        for (Sample x : sample) {
            if (x.slot() == 0) inner[ni++] = x.temperature();
            else if (x.slot() >= 4) outer[no++] = x.temperature();
        }
        double[] innerSorted = Arrays.copyOf(inner, ni);
        double[] outerSorted = Arrays.copyOf(outer, no);
        Arrays.sort(innerSorted);
        Arrays.sort(outerSorted);
        double innerMedian = median(innerSorted);
        double outerMedian = median(outerSorted);
        System.out.printf(Locale.ROOT,
                "  slot 0 median T=%.1f K (%d planets)   slot >=4 median T=%.1f K (%d planets)%n",
                innerMedian, ni, outerMedian, no);
        assertTrue(ni > 20 && no > 20, "slot sample too small");
        assertTrue(outerMedian < innerMedian * 0.75,
                "outer slots must be genuinely colder: inner=" + innerMedian
                        + " outer=" + outerMedian);

        // Strict ordering: AU(slot i+1) > AU(slot i) for every deterministic system.
        for (long worldSeed : WORLD_SEEDS) {
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int sys = 0; sys < 200; sys++) {
                StarSystem system = galaxy.getStarSystem(galaxy.systemId(sys));
                double total = 0.0;
                for (Star star : system.stars()) total += Math.max(0.0, star.luminosity());
                double previous = 0.0;
                for (int o = 0; o < system.planetCount(); o++) {
                    double d = OrbitProfile.forSlot(system.getPlanet(o).seed().value(), o, total).orbitAU();
                    assertTrue(d > previous, "orbit not monotonic in system " + sys
                            + " slot " + o + ": " + previous + " -> " + d);
                    previous = d;
                }
            }
        }
    }

    /* ------------------------------------------------------------ stellar class contrast (F, J) */

    @Test
    void stellarClassStaysVisibleInTheGeneratedTemperature() {
        long seed = 20260922L;
        Galaxy galaxy = Galaxy.from(seed);
        StarSystem system = galaxy.getStarSystem(galaxy.systemId(3));
        StarType[] ladder = {StarType.BLACK_HOLE, StarType.M, StarType.K, StarType.G,
                StarType.F, StarType.A, StarType.B, StarType.O};

        System.out.printf(Locale.ROOT,
                "%n[ACT 1] temperature by stellar class on the SAME system and slots%n");
        double previous = -1.0;
        for (StarType type : ladder) {
            double luminosity = Math.sqrt(Math.max(1.0e-9, type.minLuminosity()) * type.maxLuminosity());
            StarFluxContext ctx = new StarFluxContext(new double[]{
                    type == StarType.BLACK_HOLE ? 1.0e-4 : luminosity});
            int planets = system.planetCount();
            double[] temps = new double[planets];
            for (int o = 0; o < planets; o++) {
                PlanetDefinition def = system.definePlanet(o);
                temps[o] = PlanetPropertyGenerator.generateProperties(def, ctx).temperature();
            }
            Arrays.sort(temps);
            double medianT = median(temps);
            System.out.printf(Locale.ROOT,
                    "    %-11s L=%-10.5g Lsun  medianT=%8.1f K  coldest=%8.1f K  hottest=%8.1f K%n",
                    type, luminosity, medianT, temps[0], temps[planets - 1]);
            assertTrue(medianT > previous,
                    "stellar class must stay visible: " + type + " median " + medianT
                            + " <= previous " + previous);
            previous = medianT;
        }
    }

    /* ------------------------------------------------------------ star data audit (A) */

    @Test
    void stellarRadiusIsNotSilentlyUsedAsAThermalMultiplier() {
        // ACT 1 decision: the generated luminosity is the canonical thermal input; the visual
        // radius Star.size() is NOT multiplied into the flux. The deviation of the generated
        // luminosity from the Stefan-Boltzmann value R^2*T^4 is MEASURED and reported instead of
        // being hidden, because it is the reason the radius must not be used as a thermal factor.
        double maxDex = 0.0;
        int counted = 0;
        for (long worldSeed : WORLD_SEEDS) {
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int sys = 0; sys < 120; sys++) {
                StarSystem system = galaxy.getStarSystem(galaxy.systemId(sys));
                for (Star star : system.stars()) {
                    double sb = star.size() * star.size()
                            * Math.pow(star.temperature() / 5772.0, 4.0);
                    double ratio = Math.max(star.luminosity(), 1.0e-9) / Math.max(1.0e-9, sb);
                    maxDex = Math.max(maxDex, Math.abs(Math.log10(ratio)));
                    counted++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "%n[ACT 1] star data audit: %d stars, max |log10(L_generated / (R^2 * T^4))| = %.2f dex%n",
                counted, maxDex);
        assertTrue(counted > 100, "star sample too small");
        System.out.println("  -> generated L and R are independent seed draws; the luminosity stays"
                + " the canonical thermal input (no radius multiplier in PlanetThermal)");

        // Proof that the thermal chain consumes ONLY (luminosity, distance, eccentricity).
        StarFluxContext ctx = new StarFluxContext(new double[]{1.0, 4.0});
        double au = 2.0;
        double ecc = 0.1;
        double expected = 5.0 / (au * au) / Math.sqrt(1.0 - ecc * ecc);
        assertEquals(expected, StellarThermalModel.orbitAveragedFlux(ctx, au, ecc), 1.0e-12,
                "flux must be sum(L)/d^2 with the eccentricity correction — no radius multiplier");
    }
}


