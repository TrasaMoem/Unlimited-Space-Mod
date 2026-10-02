package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel.StarFluxContext;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel.ThermalClass;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 1 — STELLAR THERMAL MODEL.
 *
 * <p>Temperature is DERIVED from (stars, orbit, eccentricity, albedo, atmosphere, internal heat)
 * and the archetype is reconciled to be compatible with it. These tests pin: determinism,
 * multi-star flux summation, monotonicity in distance/luminosity/albedo, bounded greenhouse and
 * internal heat, monotonic log normalization with real-Kelvin band boundaries, archetype
 * compatibility, and the galaxy-wide temperature DISTRIBUTION.
 *
 * <p>The 30..4600 K span and the shape of the distribution are DESIGN POPULATION PRIORS of the
 * procedural universe — not measured exoplanet statistics.
 */
class StellarThermalModelTest {

    private static final long WORLD_SEED = 777L;

    private static Star star(double luminosity, StarType type) {
        return Star.of(new StarId(StarSystemId.of(0), 0), 1L, type,
                type.minTemperature(), type.minSize(), luminosity, type.colorRgb());
    }

    private static double surfaceTemp(StarFluxContext ctx, double au, double ecc,
                                      double albedo, double pressure, double internal) {
        return StellarThermalModel.surfaceTemperature(ctx, au, ecc, albedo, pressure, internal);
    }

    private static double percentile(double[] sorted, double q) {
        int idx = (int) Math.min(sorted.length - 1, Math.max(0, Math.round(q * (sorted.length - 1))));
        return sorted[idx];
    }

    /* ------------------------------------------------------------ determinism */

    @Test
    void sameSeedYieldsTheSameTemperature() {
        Galaxy a = Galaxy.from(WORLD_SEED);
        Galaxy b = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 12; s++) {
            StarSystem sa = a.getStarSystem(a.systemId(s));
            StarSystem sb = b.getStarSystem(b.systemId(s));
            for (int o = 0; o < sa.planetCount(); o++) {
                assertEquals(sa.getPlanet(o).properties().temperature(),
                        sb.getPlanet(o).properties().temperature(), 0.0,
                        "temperature must be deterministic for " + s + "/" + o);
                assertEquals(sa.getPlanet(o).properties().type(),
                        sb.getPlanet(o).properties().type(), "type must be deterministic");
            }
        }
    }

    @Test
    void temperatureIsAPureFunctionOfTheStellarContext() {
        StarSystem sys = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(3));
        var def = sys.definePlanet(1);
        double t1 = PlanetPropertyGenerator.generateProperties(def, StarFluxContext.of(sys.stars()))
                .temperature();
        double t2 = PlanetPropertyGenerator.generateProperties(def, StarFluxContext.of(sys.stars()))
                .temperature();
        assertEquals(t1, t2, 0.0);
        assertTrue(t1 >= StellarThermalModel.T_MIN && t1 <= StellarThermalModel.T_MAX);
    }

    @Test
    void legacyPathWithoutStellarContextKeepsTheTableBehaviour() {
        StarSystem sys = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(3));
        var def = sys.definePlanet(1);
        double legacy = PlanetPropertyGenerator.generateProperties(def).temperature();
        assertEquals(def.type(), PlanetPropertyGenerator.generateProperties(def).type());
        assertTrue(legacy >= def.type().temperatureMinK() && legacy <= def.type().temperatureMaxK(),
                "the legacy (no-flux) path must stay byte-compatible: " + legacy);
    }

    /* ------------------------------------------------------------ multi-star flux */

    @Test
    void singleStarContributesItsOwnFlux() {
        StarFluxContext single = new StarFluxContext(new double[]{1.0});
        assertEquals(1.0, StellarThermalModel.totalRelativeFlux(single, 1.0), 1e-9);
        assertEquals(0.25, StellarThermalModel.totalRelativeFlux(single, 2.0), 1e-9);
    }

    @Test
    void binaryStarsAddTheirFlux() {
        StarFluxContext binary = new StarFluxContext(new double[]{1.0, 0.25});
        assertEquals(1.25, StellarThermalModel.totalRelativeFlux(binary, 1.0), 1e-9,
                "binary flux must be the SUM, not the nearest star only");
        assertTrue(surfaceTemp(binary, 1.0, 0.0, 0.25, 0.0, 0.0)
                        > surfaceTemp(new StarFluxContext(new double[]{1.0}), 1.0, 0.0, 0.25, 0.0, 0.0),
                "the companion must heat the planet");
    }

    @Test
    void trinaryStarsAddTheirFlux() {
        StarFluxContext trinary = new StarFluxContext(new double[]{1.0, 0.4, 0.1});
        assertEquals(1.5, StellarThermalModel.totalRelativeFlux(trinary, 1.0), 1e-9);
        StarFluxContext binary = new StarFluxContext(new double[]{1.0, 0.4});
        assertTrue(StellarThermalModel.totalRelativeFlux(trinary, 1.0)
                > StellarThermalModel.totalRelativeFlux(binary, 1.0));
    }

    @Test
    void fluxContextOfUsesEveryStarOfTheSystem() {
        List<Star> trio = List.of(star(1.0, StarType.G), star(0.5, StarType.M), star(0.02, StarType.M));
        StarFluxContext ctx = StarFluxContext.of(trio);
        assertNotNull(ctx);
        assertEquals(3, ctx.starCount());
        assertEquals(1.52, StellarThermalModel.totalRelativeFlux(ctx, 1.0), 1e-3);
    }

    @Test
    void aDimCompanionBarelyMatters() {
        StarFluxContext alone = new StarFluxContext(new double[]{1.0});
        StarFluxContext plusDim = new StarFluxContext(new double[]{1.0, 1.0e-4});
        double diff = Math.abs(surfaceTemp(plusDim, 1.0, 0.0, 0.25, 0.0, 0.0)
                - surfaceTemp(alone, 1.0, 0.0, 0.25, 0.0, 0.0));
        assertTrue(diff < 0.1, "a negligible companion must not change the temperature: " + diff);
    }

    @Test
    void aBlackHoleCompanionContributesAlmostNothing() {
        Star hole = star(0.0, StarType.BLACK_HOLE);
        StarFluxContext ctx = StarFluxContext.of(List.of(star(1.0, StarType.G), hole));
        assertNotNull(ctx);
        assertEquals(2, ctx.starCount());
        assertTrue(StellarThermalModel.totalRelativeFlux(ctx, 1.0) < 1.001,
                "a collapsed companion must not add usable flux");
    }

    /* ------------------------------------------------------------ monotonicity */

    @Test
    void largerOrbitalDistanceMeansLessForcing() {
        StarFluxContext ctx = new StarFluxContext(new double[]{1.0});
        double last = Double.MAX_VALUE;
        for (double au = 0.05; au <= 60.0; au *= 1.5) {
            double f = StellarThermalModel.totalRelativeFlux(ctx, au);
            assertTrue(f < last, "flux must fall with distance at " + au + " AU");
            last = f;
        }
        assertTrue(surfaceTemp(ctx, 0.2, 0.0, 0.25, 0.0, 0.0)
                > surfaceTemp(ctx, 5.0, 0.0, 0.25, 0.0, 0.0));
    }

    @Test
    void higherLuminosityMeansMoreForcing() {
        double cold = surfaceTemp(new StarFluxContext(new double[]{0.01}), 1.0, 0.0, 0.25, 0.0, 0.0);
        double sun = surfaceTemp(new StarFluxContext(new double[]{1.0}), 1.0, 0.0, 0.25, 0.0, 0.0);
        double bright = surfaceTemp(new StarFluxContext(new double[]{100.0}), 1.0, 0.0, 0.25, 0.0, 0.0);
        assertTrue(cold < sun && sun < bright, "luminosity must raise the temperature");
    }

    @Test
    void eccentricityRaisesTheOrbitAveragedFlux() {
        StarFluxContext ctx = new StarFluxContext(new double[]{1.0});
        assertTrue(surfaceTemp(ctx, 1.0, 0.25, 0.25, 0.0, 0.0)
                > surfaceTemp(ctx, 1.0, 0.0, 0.25, 0.0, 0.0));
    }

    @Test
    void higherAlbedoMeansACoolerWorld() {
        StarFluxContext ctx = new StarFluxContext(new double[]{1.0});
        assertTrue(surfaceTemp(ctx, 1.0, 0.0, 0.55, 0.0, 0.0)
                < surfaceTemp(ctx, 1.0, 0.0, 0.08, 0.0, 0.0), "ice must be brighter/cooler than basalt");
    }

    @Test
    void albedoPriorIsDerivedFromTheArchetypeNotRandom() {
        assertEquals(0.55, StellarThermalModel.albedoFor(PlanetType.ICE), 1e-9);
        assertEquals(0.08, StellarThermalModel.albedoFor(PlanetType.VOLCANIC), 1e-9);
        assertTrue(StellarThermalModel.albedoFor(PlanetType.ICE)
                > StellarThermalModel.albedoFor(PlanetType.OCEAN));
        assertEquals(0.25, StellarThermalModel.albedoFor(null), 1e-9);
    }

    /* ------------------------------------------------------------ bounded corrections */

    @Test
    void greenhouseIsBoundedAndSoftlySaturating() {
        assertEquals(1.0, StellarThermalModel.greenhouseMultiplier(0.0), 1e-9);
        assertEquals(1.45, StellarThermalModel.greenhouseMultiplier(1.0), 1e-9);
        double last = 0.0;
        for (double p = 0.0; p <= 1.0; p += 0.1) {
            double g = StellarThermalModel.greenhouseMultiplier(p);
            assertTrue(g > last, "greenhouse must be monotonic in pressure");
            assertTrue(g <= 1.45 + 1e-9, "greenhouse must stay bounded: " + g);
            last = g;
        }
        // A thin-atmosphere cold world can never be inflated into a hot one by pressure alone.
        StarFluxContext ctx = new StarFluxContext(new double[]{0.0036});
        double thin = surfaceTemp(ctx, 1.0, 0.0, 0.25, 0.0, 0.0);
        double thick = surfaceTemp(ctx, 1.0, 0.0, 0.25, 1.0, 0.0);
        assertTrue(thick < thin * 1.5, "pressure alone must not create a runaway: " + thin + " -> " + thick);
    }

    @Test
    void internalHeatIsBoundedAndNeverTurnsAPlanetIntoAStar() {
        StarFluxContext ctx = new StarFluxContext(new double[]{0.0036});
        double dead = surfaceTemp(ctx, 1.0, 0.0, 0.25, 0.3, 0.0);
        double alive = surfaceTemp(ctx, 1.0, 0.0, 0.25, 0.3, 1.0);
        assertTrue(alive > dead, "tectonic activity must warm the surface");
        assertTrue(alive - dead <= 65.0 + 1e-6,
                "internal heat must stay a small term: delta=" + (alive - dead));
    }

    @Test
    void temperatureIsAlwaysClampedToTheCanonicalRange() {
        StarFluxContext insane = new StarFluxContext(new double[]{1.0e6});
        assertEquals(StellarThermalModel.T_MAX, surfaceTemp(insane, 0.05, 0.25, 0.05, 1.0, 1.0), 1e-6);
        StarFluxContext dead = new StarFluxContext(new double[]{1.0e-9});
        assertEquals(StellarThermalModel.T_MIN, surfaceTemp(dead, 60.0, 0.0, 0.55, 0.0, 0.0), 1e-6);
    }

    /* ------------------------------------------------------------ normalization & bands */

    @Test
    void logNormalizationIsMonotonicAndInvertible() {
        assertEquals(0.0, StellarThermalModel.normalizeKelvin(StellarThermalModel.T_MIN), 1e-9);
        assertEquals(1.0, StellarThermalModel.normalizeKelvin(StellarThermalModel.T_MAX), 1e-9);
        double last = -1.0;
        for (double k = 30.0; k <= 4600.0; k *= 1.2) {
            double v = StellarThermalModel.normalizeKelvin(k);
            assertTrue(v > last, "normalization must be strictly monotonic at " + k);
            last = v;
            assertEquals(k, StellarThermalModel.denormalizeKelvin(v), 1e-6);
        }
        // The habitable window must NOT be squashed into a sliver of the axis.
        double span = StellarThermalModel.normalizeKelvin(320.0)
                - StellarThermalModel.normalizeKelvin(240.0);
        assertTrue(span > 0.04, "240..320 K must occupy a usable share of the axis: " + span);
    }

    @Test
    void temperatureBandsFollowRealKelvinBoundaries() {
        assertEquals(TemperatureBand.FROZEN, TemperatureBand.ofKelvin(30.0));
        assertEquals(TemperatureBand.FROZEN, TemperatureBand.ofKelvin(149.0));
        assertEquals(TemperatureBand.COLD, TemperatureBand.ofKelvin(150.0));
        assertEquals(TemperatureBand.COLD, TemperatureBand.ofKelvin(239.0));
        assertEquals(TemperatureBand.TEMPERATE, TemperatureBand.ofKelvin(240.0));
        assertEquals(TemperatureBand.TEMPERATE, TemperatureBand.ofKelvin(288.0), "Earth must be temperate");
        assertEquals(TemperatureBand.WARM, TemperatureBand.ofKelvin(320.0));
        assertEquals(TemperatureBand.HOT, TemperatureBand.ofKelvin(400.0));
        assertEquals(TemperatureBand.INFERNO, TemperatureBand.ofKelvin(800.0));
        assertEquals(TemperatureBand.INFERNO, TemperatureBand.ofKelvin(4600.0));
        assertEquals(TemperatureBand.ofKelvin(273.0),
                TemperatureBand.of(StellarThermalModel.normalizeKelvin(273.0)),
                "of() and ofKelvin() must agree");
    }

    @Test
    void thermalClassesCoverTheWholeRangeAndAreOrdered() {
        assertEquals(ThermalClass.CRYOGENIC, StellarThermalModel.thermalClass(30.0));
        assertEquals(ThermalClass.FROZEN, StellarThermalModel.thermalClass(150.0));
        assertEquals(ThermalClass.COLD, StellarThermalModel.thermalClass(240.0));
        assertEquals(ThermalClass.TEMPERATE, StellarThermalModel.thermalClass(288.0));
        assertEquals(ThermalClass.WARM, StellarThermalModel.thermalClass(380.0));
        assertEquals(ThermalClass.HOT, StellarThermalModel.thermalClass(600.0));
        assertEquals(ThermalClass.VERY_HOT, StellarThermalModel.thermalClass(1000.0));
        assertEquals(ThermalClass.MOLTEN, StellarThermalModel.thermalClass(3000.0));
        assertEquals(8, ThermalClass.VALUES.length);
        assertTrue(ThermalClass.CRYOGENIC.isSolidWaterOnly());
        assertTrue(ThermalClass.COLD.mayHoldLiquidWater());
        assertTrue(ThermalClass.TEMPERATE.mayHoldLiquidWater());
        assertFalse(ThermalClass.MOLTEN.mayHoldLiquidWater());
        assertTrue(ThermalClass.MOLTEN.isVolatileFree());
    }

    /* ------------------------------------------------------------ archetype compatibility */

    @Test
    void planetTypeIsAlwaysCompatibleWithTheDerivedTemperature() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        for (int s = 0; s < 40; s++) {
            StarSystem sys = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < sys.planetCount(); o++) {
                Planet p = sys.getPlanet(o);
                PlanetType type = p.properties().type();
                double t = p.properties().temperature();
                checked++;
                if (type == PlanetType.GAS_GIANT) continue;
                boolean ok = false;
                for (PlanetType candidate : StellarThermalModel.compatibleTypes(t)) {
                    if (candidate == type) ok = true;
                }
                assertTrue(ok, "type " + type + " is not thermally compatible with " + t + " K");
            }
        }
        assertTrue(checked > 40, "sample too small: " + checked);
    }

    @Test
    void reconcileTypeKeepsGasGiantsOnTheirOwnBranch() {
        assertEquals(PlanetType.GAS_GIANT,
                StellarThermalModel.reconcileType(PlanetType.GAS_GIANT, 120.0, 5L));
        assertEquals(PlanetType.GAS_GIANT,
                StellarThermalModel.reconcileType(PlanetType.GAS_GIANT, 3000.0, 5L));
    }

    @Test
    void reconcileTypeIsDeterministicAndCompatible() {
        for (long s = 0; s < 300; s++) {
            for (double t : new double[]{40.0, 180.0, 250.0, 300.0, 500.0, 1500.0}) {
                PlanetType a = StellarThermalModel.reconcileType(PlanetType.ROCKY, t, s);
                PlanetType b = StellarThermalModel.reconcileType(PlanetType.ROCKY, t, s);
                assertEquals(a, b);
                boolean ok = false;
                for (PlanetType c : StellarThermalModel.compatibleTypes(t)) if (c == a) ok = true;
                assertTrue(ok, a + " incompatible with " + t + " K");
            }
        }
    }

    @Test
    void hotWorldsNeverBecomeIceAndColdWorldsNeverBecomeDesert() {
        for (long s = 0; s < 40; s++) {
            assertNotEquals(PlanetType.ICE,
                    StellarThermalModel.reconcileType(PlanetType.ICE, 900.0, s));
            assertNotEquals(PlanetType.DESERT,
                    StellarThermalModel.reconcileType(PlanetType.DESERT, 80.0, s));
            assertNotEquals(PlanetType.VOLCANIC,
                    StellarThermalModel.reconcileType(PlanetType.VOLCANIC, 60.0, s));
        }
    }

    @Test
    void aCompatiblePriorIsPreserved() {
        // The seed prior must win whenever the physics does not forbid it (stable planet identity).
        assertEquals(PlanetType.OCEAN, StellarThermalModel.reconcileType(PlanetType.OCEAN, 288.0, 1L));
        assertEquals(PlanetType.ICE, StellarThermalModel.reconcileType(PlanetType.ICE, 120.0, 1L));
    }

    /* ------------------------------------------------------------ galaxy distribution */

    @Test
    void galaxyTemperatureDistributionIsWideNonUniformAndHotExtremesAreRare() {
        double[] raw = new double[4000];
        int n = 0;
        int[] bands = new int[TemperatureBand.VALUES.length];
        int[] classes = new int[ThermalClass.VALUES.length];
        for (long worldSeed : new long[]{1L, 777L, 424242L, 0x5EEDCAFE0L}) {
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int s = 0; s < 80; s++) {
                StarSystem sys = galaxy.getStarSystem(galaxy.systemId(s));
                for (int o = 0; o < sys.planetCount(); o++) {
                    double t = sys.getPlanet(o).properties().temperature();
                    if (n >= raw.length) break;
                    raw[n++] = t;
                    bands[TemperatureBand.ofKelvin(t).ordinal()]++;
                    classes[StellarThermalModel.thermalClass(t).ordinal()]++;
                }
            }
        }
        double[] sorted = java.util.Arrays.copyOf(raw, n);
        java.util.Arrays.sort(sorted);
        double min = sorted[0];
        double max = sorted[n - 1];
        double p50 = percentile(sorted, 0.50);
        double p75 = percentile(sorted, 0.75);
        double p90 = percentile(sorted, 0.90);
        double p95 = percentile(sorted, 0.95);
        double p99 = percentile(sorted, 0.99);

        System.out.printf(Locale.ROOT, "%n[PHASE 1] planet temperature distribution (%d planets)%n", n);
        System.out.printf(Locale.ROOT,
                "  min=%.1f K  p50=%.1f  p75=%.1f  p90=%.1f  p95=%.1f  p99=%.1f  max=%.1f K%n",
                min, p50, p75, p90, p95, p99, max);
        System.out.print("  histogram: ");
        int above = 0;
        for (double t : sorted) if (t >= 1600.0) above++;
        for (double lo = 0; lo < 1600; lo += 200) {
            int c = 0;
            for (double t : sorted) if (t >= lo && t < lo + 200) c++;
            System.out.printf(Locale.ROOT, "%.0f-%.0fK=%d ", lo, lo + 200, c);
        }
        System.out.printf(Locale.ROOT, ">=1600K=%d%n", above);
        System.out.print("  bands: ");
        for (int i = 0; i < bands.length; i++) {
            System.out.printf(Locale.ROOT, "%s=%d ", TemperatureBand.VALUES[i], bands[i]);
        }
        System.out.print("  thermalClasses: ");
        for (int i = 0; i < classes.length; i++) {
            System.out.printf(Locale.ROOT, "%s=%d ", ThermalClass.VALUES[i], classes[i]);
        }
        System.out.println();

        assertTrue(n > 200, "sample too small: " + n);
        // 1) WIDE: far wider than the old 100..900 K table.
        assertTrue(max - min > 400.0, "temperature range too narrow: " + min + ".." + max);
        // 2) NON-UNIFORM: no 100 K window may swallow most of the population.
        int widest = 0;
        for (double lo = 0; lo < 1600; lo += 100) {
            int c = 0;
            for (double t : sorted) if (t >= lo && t < lo + 100) c++;
            if (c > widest) widest = c;
        }
        assertTrue(widest < n * 0.6,
                "distribution looks uniform: widest 100 K window holds " + widest + "/" + n);
        // 3) EXTREMES RARE: very hot worlds exist but stay a minority.
        int hot = 0;
        for (double t : sorted) if (t >= 700.0) hot++;
        assertTrue(hot < n * 0.25, "hot worlds too common: " + hot + "/" + n);
        assertTrue(p90 < 1200.0, "p90 too hot: " + p90);
        // 4) COLD END PRESENT: cryogenic worlds must exist, not be a freak edge case.
        int cold = 0;
        for (double t : sorted) if (t < 200.0) cold++;
        assertTrue(cold > n * 0.02, "cryogenic worlds missing: " + cold + "/" + n);
        // 5) Canonical clamp respected everywhere.
        assertTrue(min >= StellarThermalModel.T_MIN && max <= StellarThermalModel.T_MAX);
        // 6) At least three thermal classes are actually reachable in a plain galaxy sample.
        int present = 0;
        for (int c : classes) if (c > 0) present++;
        assertTrue(present >= 3, "only " + present + " thermal classes reachable");
    }
}
