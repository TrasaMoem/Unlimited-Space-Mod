package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonOrbitMetadata;
import com.modscreating.unlimitedspace.core.planets.MoonType;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.physics.MoonThermalModel.MoonThermal;
import com.modscreating.unlimitedspace.core.physics.MoonThermalModel.MoonThermalClass;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 2 — MOON THERMAL MODEL.
 *
 * <p>Pins the contract: a moon inherits its parent's stellar environment (never a recomputed
 * stellar distance), planetshine stays bounded, tidal heating grows with parent mass, moon
 * eccentricity and proximity — and tidal heating feeds geology WITHOUT guaranteeing volcanoes.
 */
class MoonThermalModelTest {

    private static final long WORLD_SEED = 777L;

    private static PlanetThermal thermal(double surfaceK, double orbitAU, double ecc, double flux) {
        return PlanetThermal.of(surfaceK * 0.9, orbitAU, ecc, flux, surfaceK);
    }

    private static MoonOrbitMetadata orbit(double relativeDistance, double eccentricity) {
        return new MoonOrbitMetadata(0, 1, relativeDistance, eccentricity, 0.4);
    }

    /* ------------------------------------------------------------ determinism */

    @Test
    void sameMoonSeedYieldsTheSameTemperature() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 25; s++) {
            StarSystem sys = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < sys.planetCount(); o++) {
                Planet p = sys.getPlanet(o);
                for (Moon m : p.moons()) {
                    double again = sys.getPlanet(o).moons().get(m.moonIndex())
                            .properties().temperature();
                    assertEquals(m.properties().temperature(), again, 0.0,
                            "moon temperature must be deterministic");
                    assertEquals(m.properties().tidalHeating(),
                            sys.getPlanet(o).moons().get(m.moonIndex()).properties().tidalHeating(),
                            0.0, "tidal heating must be deterministic");
                }
            }
        }
    }

    @Test
    void moonsAreReproducibleAcrossGalaxyRebuilds() {
        for (int s = 0; s < 10; s++) {
            Planet a = Galaxy.from(WORLD_SEED).getStarSystem(Galaxy.from(WORLD_SEED).systemId(s)).getPlanet(0);
            Planet b = Galaxy.from(WORLD_SEED).getStarSystem(Galaxy.from(WORLD_SEED).systemId(s)).getPlanet(0);
            assertEquals(a.moons(), b.moons(), "moon list must be reproducible");
        }
    }

    /* ------------------------------------------------------------ inherited stellar environment */

    @Test
    void aMoonInheritsItsParentsStellarEnvironment() {
        double coldFlux = 0.02;
        double hotFlux = 60.0;
        MoonThermal cold = MoonThermalModel.estimate(
                thermal(120.0, 1.0, 0.05, coldFlux), 0.3, 1.0, 1.0,
                orbit(0.5, 0.05), 0.2, 0.1, 0.1);
        MoonThermal hot = MoonThermalModel.estimate(
                thermal(700.0, 1.0, 0.05, hotFlux), 0.3, 1.0, 1.0,
                orbit(0.5, 0.05), 0.2, 0.1, 0.1);
        assertTrue(hot.temperatureK() > cold.temperatureK() + 100.0,
                "a moon of a brighter system must be warmer: " + cold.temperatureK()
                        + " vs " + hot.temperatureK());
        assertEquals(coldFlux, cold.stellarFlux(), 1e-9);
    }

    @Test
    void moonTemperatureStaysInsideTheCanonicalRange() {
        for (double flux : new double[]{1.0e-6, 0.05, 1.0, 50.0, 5000.0}) {
            for (double rel = 0.1; rel <= 1.0; rel += 0.3) {
                MoonThermal t = MoonThermalModel.estimate(
                        thermal(300.0, 1.0, 0.2, flux), 0.3, 5.0, 2.0,
                        orbit(rel, 0.28), 0.15, 0.8, 1.0);
                assertTrue(t.temperatureK() >= StellarThermalModel.T_MIN
                                && t.temperatureK() <= StellarThermalModel.T_MAX,
                        "moon temperature out of range: " + t.temperatureK());
            }
        }
    }

    @Test
    void aColdMoonStaysColdWhenTidalHeatingIsWeak() {
        MoonThermal t = MoonThermalModel.estimate(
                thermal(60.0, 20.0, 0.0, 0.002), 0.3, 0.1, 0.4,
                orbit(0.95, 0.01), 0.55, 0.0, 0.05);
        assertTrue(t.temperatureK() < 200.0, "weak tides must not warm a distant moon: " + t.temperatureK());
        assertEquals(MoonThermalClass.COLD_FROZEN, t.thermalClass());
        assertTrue(t.tidalHeating() < 0.05, "tidal heating must be negligible: " + t.tidalHeating());
    }

    /* ------------------------------------------------------------ tidal heating */

    @Test
    void higherEccentricityIncreasesTidalHeating() {
        double low = MoonThermalModel.tidalHeating(1.0, 0.3, 0.01);
        double mid = MoonThermalModel.tidalHeating(1.0, 0.3, 0.12);
        double high = MoonThermalModel.tidalHeating(1.0, 0.3, 0.28);
        assertTrue(low < mid && mid < high,
                "tidal heating must grow with eccentricity: " + low + " < " + mid + " < " + high);
    }

    @Test
    void closerOrbitIncreasesTidalHeating() {
        double far = MoonThermalModel.tidalHeating(1.0, 0.95, 0.2);
        double near = MoonThermalModel.tidalHeating(1.0, 0.1, 0.2);
        assertTrue(near > far * 3.0, "a close moon must be far more tidal-heated: " + far + " vs " + near);
    }

    @Test
    void moreMassiveParentIncreasesTidalHeating() {
        double small = MoonThermalModel.tidalHeating(0.3, 0.3, 0.2);
        double earth = MoonThermalModel.tidalHeating(1.0, 0.3, 0.2);
        double giant = MoonThermalModel.tidalHeating(5.0, 0.3, 0.2);
        assertTrue(small < earth && earth < giant, "tidal heating must grow with parent mass");
        assertTrue(giant <= 1.0, "tidal heating must stay bounded");
    }

    @Test
    void tidalHeatingIsAlwaysBounded() {
        for (double mass = 0.02; mass <= 12.0; mass *= 2.5) {
            for (double ecc = 0.0; ecc < 0.3; ecc += 0.1) {
                for (double rel = 0.1; rel <= 1.0; rel += 0.45) {
                    double h = MoonThermalModel.tidalHeating(mass, rel, ecc);
                    assertTrue(h >= 0.0 && h <= 1.0, "tidal heating out of range: " + h);
                }
            }
        }
    }

    @Test
    void aWarmTidalMoonIsPossible() {
        // Massive parent + close eccentric moon in a temperate system: the tides push a mid-slot
        // moon past the freezing point even though its star alone would not.
        MoonThermal t = MoonThermalModel.estimate(
                thermal(280.0, 1.2, 0.1, 0.6), 0.3, 6.0, 3.0,
                orbit(0.1, 0.28), 0.2, 0.25, 0.9);
        assertTrue(t.tidalHeating() > 0.5, "strong tidal case must be strong: " + t.tidalHeating());
        assertTrue(t.temperatureK() > 240.0,
                "tides must be able to make a moon warm: " + t.temperatureK());
    }

    @Test
    void aVolcanicTidalMoonIsPossible() {
        // The same geometry in a hot system: heat + a strong tidal drive become volcanism.
        MoonThermal t = MoonThermalModel.estimate(
                thermal(700.0, 0.8, 0.1, 8.0), 0.3, 6.0, 3.0,
                orbit(0.1, 0.28), 0.15, 0.4, 0.9);
        assertEquals(MoonThermalClass.VOLCANIC, t.thermalClass(),
                "a hot, strongly tidal, prone moon must be volcanic[" + t.temperatureK() + " K]");
        assertTrue(t.thermalClass().isGeologicallyDriven());
    }

    /* ------------------------------------------------------------ tidal → geology wiring */

    @Test
    void strongTidesRaiseGeologicalActivityAcrossTheGalaxy() {
        double tidalSum = 0.0;
        int tidalN = 0;
        double quietSum = 0.0;
        int quietN = 0;
        for (long worldSeed : new long[]{1L, 777L, 424242L}) {
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int s = 0; s < 60; s++) {
                StarSystem sys = galaxy.getStarSystem(galaxy.systemId(s));
                for (int o = 0; o < sys.planetCount(); o++) {
                    for (Moon m : sys.getPlanet(o).moons()) {
                        double tidal = m.properties().tidalHeating();
                        double geo = m.properties().geologicalActivity();
                        if (tidal > 0.40) {
                            tidalSum += geo;
                            tidalN++;
                        } else if (tidal < 0.05) {
                            quietSum += geo;
                            quietN++;
                        }
                    }
                }
            }
        }
        assertTrue(tidalN > 20, "not enough strongly tidal moons sampled: " + tidalN);
        assertTrue(quietN > 20, "not enough quiet moons sampled: " + quietN);
        double tidalAvg = tidalSum / tidalN;
        double quietAvg = quietSum / quietN;
        assertTrue(tidalAvg > quietAvg,
                "tidal heating must raise geological activity: tidal=" + tidalAvg
                        + " quiet=" + quietAvg);
        assertTrue(tidalAvg < 1.0, "geological activity must stay bounded");
    }

    /* ------------------------------------------------------------ planetshine */

    @Test
    void planetshineIsBoundedAndNeverDominates() {
        double low = MoonThermalModel.planetshine(0.05, 0.3, 0.95);
        double high = MoonThermalModel.planetshine(0.7, 6.0, 0.1);
        assertTrue(low >= 0.0 && high <= 1.0);
        assertTrue(high > low, "a big bright close parent must shine more");
        // The shine can only ever add a few percent to the inherited stellar flux.
        double base = 1.0;
        double withShine = StellarThermalModel.moonStellarFactor(base, 1.0);
        assertTrue(withShine <= base * 1.061, "planetshine must stay a small correction: " + withShine);
    }

    @Test
    void tidalHeatingAndPlanetshineAreDifferentMechanisms() {
        // Planetshine depends on the parent's brightness, tidal heating on its mass: a bright
        // big parent acts on both, a dark massive parent only on tides.
        MoonThermal bright = MoonThermalModel.estimate(
                thermal(200.0, 1.0, 0.1, 5.0), 0.55, 4.0, 3.0, orbit(0.4, 0.2), 0.2, 0.3, 0.4);
        MoonThermal dark = MoonThermalModel.estimate(
                thermal(200.0, 1.0, 0.1, 5.0), 0.05, 4.0, 3.0, orbit(0.4, 0.2), 0.2, 0.3, 0.4);
        assertEquals(bright.tidalHeating(), dark.tidalHeating(), 1e-12,
                "tidal heating must not depend on the parent's albedo");
        assertTrue(bright.planetshine() > dark.planetshine(),
                "planetshine must depend on the parent's brightness");
    }

    /* ------------------------------------------------------------ classification */

    @Test
    void thermalClassLadderMatchesTemperatureAndActivity() {
        assertEquals(MoonThermalClass.COLD_FROZEN,
                MoonThermalModel.classify(100.0, 0.0, 0.05));
        assertEquals(MoonThermalClass.COLD_GEOLOGICALLY_ACTIVE,
                MoonThermalModel.classify(100.0, 0.5, 0.05));
        assertEquals(MoonThermalClass.TEMPERATE, MoonThermalModel.classify(280.0, 0.0, 0.1));
        assertEquals(MoonThermalClass.WARM, MoonThermalModel.classify(360.0, 0.0, 0.1));
        assertEquals(MoonThermalClass.HOT, MoonThermalModel.classify(500.0, 0.05, 0.2));
        assertEquals(MoonThermalClass.VOLCANIC, MoonThermalModel.classify(900.0, 0.0, 0.1));
        assertEquals(MoonThermalClass.VOLCANIC, MoonThermalModel.classify(500.0, 0.9, 0.2));
        assertEquals(6, MoonThermalClass.VALUES.length);
        assertTrue(MoonThermalClass.COLD_FROZEN.isColdSurface());
        assertTrue(MoonThermalClass.COLD_GEOLOGICALLY_ACTIVE.mayHoldSubsurfaceLiquid());
        assertTrue(MoonThermalClass.TEMPERATE.mayHoldSurfaceLiquid());
        assertFalse(MoonThermalClass.HOT.mayHoldSurfaceLiquid());
        assertTrue(MoonThermalClass.VOLCANIC.isVolatileFree());
    }

    /* ------------------------------------------------------------ galaxy summary */

    @Test
    void strongTidesDoNotAlwaysMeanVolcanoes() {
        // A strongly tidal but non-volcanic-prone archetype must NOT become VOLCANIC: the
        // geology compatibility gate keeps it a cold, geologically active world instead.
        MoonThermal classy = MoonThermalModel.estimate(
                thermal(90.0, 2.0, 0.1, 0.01), 0.3, 6.0, 3.0, orbit(0.1, 0.28), 0.3, 0.2, 0.0);
        assertTrue(classy.tidalHeating() > 0.5);
        assertNotEquals(MoonThermalClass.VOLCANIC, classy.thermalClass(),
                "tidal forcing alone must not force a volcanic class when the interior is not prone");
    }

    @Test
    void galaxyMoonThermalSummary() {
        double[] temps = new double[20000];
        double[] tidal = new double[20000];
        int n = 0;
        int[] classes = new int[MoonThermalClass.VALUES.length];
        int parentHotButMoonCold = 0;
        int inherited = 0;
        double shineMax = 0.0;
        double deltaSum = 0.0;
        int deltaN = 0;
        for (long worldSeed : new long[]{1L, 777L, 424242L}) {
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int s = 0; s < 70; s++) {
                StarSystem sys = galaxy.getStarSystem(galaxy.systemId(s));
                for (int o = 0; o < sys.planetCount(); o++) {
                    Planet p = sys.getPlanet(o);
                    double parentT = p.properties().temperature();
                    for (Moon m : p.moons()) {
                        if (n >= temps.length) break;
                        temps[n] = m.properties().temperature();
                        tidal[n] = m.properties().tidalHeating();
                        n++;
                        classes[m.properties().thermalClass().ordinal()]++;
                        inherited++;
                        if (m.properties().thermal() != null) {
                            shineMax = Math.max(shineMax, m.properties().thermal().planetshine());
                        }
                        deltaSum += Math.abs(m.properties().temperature() - parentT);
                        deltaN++;
                        if (parentT > 420.0 && m.properties().temperature() < 200.0) {
                            parentHotButMoonCold++;
                        }
                    }
                }
            }
        }
        double[] sorted = java.util.Arrays.copyOf(temps, n);
        java.util.Arrays.sort(sorted);
        int tidalHigh = 0;
        int tidalLow = 0;
        double tidalMax = 0.0;
        for (int i = 0; i < n; i++) {
            if (tidal[i] > 0.40) tidalHigh++;
            if (tidal[i] < 0.05) tidalLow++;
            tidalMax = Math.max(tidalMax, tidal[i]);
        }
        System.out.printf(java.util.Locale.ROOT,
                "%n[PHASE 2] moon thermal summary (%d moons)%n", n);
        System.out.printf(java.util.Locale.ROOT,
                "  T: min=%.1f p25=%.1f p50=%.1f p75=%.1f p90=%.1f max=%.1f K%n",
                sorted[0], sorted[n / 4], sorted[n / 2], sorted[(3 * n) / 4], sorted[(9 * n) / 10],
                sorted[n - 1]);
        System.out.printf(java.util.Locale.ROOT,
                "  mean |T_moon - T_parent| = %.1f K   parent>420K but moon<200K = %d/%d   max planetshine = %.3f%n",
                deltaSum / Math.max(1, deltaN), parentHotButMoonCold, inherited, shineMax);
        System.out.print("  moonThermalClasses: ");
        for (int i = 0; i < classes.length; i++) {
            System.out.printf(java.util.Locale.ROOT, "%s=%d ", MoonThermalClass.VALUES[i], classes[i]);
        }
        System.out.printf(java.util.Locale.ROOT,
                "%n  tidal: >0.40 = %d, <0.05 = %d, max = %.3f%n", tidalHigh, tidalLow, tidalMax);
        System.out.println();

        assertTrue(n > 500, "sample too small: " + n);
        // Inheritance, not independence: a moon's baseline must track its parent's temperature.
        assertTrue(deltaSum / Math.max(1, deltaN) < 200.0,
                "moon temperature drifted too far from its parent: " + (deltaSum / deltaN));
        // Thermodynamically sensible: a moon of a genuinely hot world is never deep-frozen.
        assertTrue(parentHotButMoonCold < n * 0.05,
                "too many frozen moons of hot planets: " + parentHotButMoonCold + "/" + n);
        assertTrue(tidalHigh > 0 && tidalLow > tidalHigh, "tidal distribution looks wrong");
        assertTrue(shineMax <= 1.0, "planetshine out of bounds: " + shineMax);
        int present = 0;
        for (int c : classes) if (c > 0) present++;
        assertTrue(present >= 4, "only " + present + " moon thermal classes reachable");
    }
}
