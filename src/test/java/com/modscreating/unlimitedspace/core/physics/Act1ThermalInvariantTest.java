package com.modscreating.unlimitedspace.core.physics;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonOrbitMetadata;
import com.modscreating.unlimitedspace.core.planets.MoonType;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.physics.MoonThermalModel.MoonThermal;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel.StarFluxContext;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 1 — STELLAR THERMAL INVARIANTS (the physical contract of the recalibrated chain).
 *
 * <p>Every invariant is asserted on the CANONICAL entry points only
 * ({@code StellarThermalModel.surfaceTemperature}, {@code PlanetProperties.temperature()},
 * {@code PlanetProperties.thermal()}, {@code MoonThermalModel.estimate}) so no test can pass by
 * re-implementing the physics:
 * <ol>
 *   <li>distance: same star/atmosphere/albedo, larger AU ⇒ colder;</li>
 *   <li>luminosity: same AU/atmosphere/albedo, brighter star ⇒ hotter (M..O visible ladder);</li>
 *   <li>internal heat: same flux, more internal heat ⇒ hotter — and ZERO at a dead world;</li>
 *   <li>greenhouse: same flux, more pressure ⇒ hotter;</li>
 *   <li>multi-star: a bright companion heats the system;</li>
 *   <li>moons: inherit the parent's stellar context, tides add a secondary/local term;</li>
 *   <li>the stored {@code PlanetThermal} explains the surface temperature exactly;</li>
 *   <li>bands/classes: one band per Kelvin value, CRYOGENIC and MOLTEN reachable;</li>
 *   <li>PHASE 3 water phases are NOT regressed by the recalibration.</li>
 * </ol>
 */
class Act1ThermalInvariantTest {

    private static final long WORLD_SEED = 4242L;
    private static final double AU = 1.20;
    private static final double ECC = 0.05;
    private static final double ALBEDO = 0.28;
    private static final double PRESSURE = 0.50;
    private static final double INTERNAL = 0.40;

    private static StarFluxContext ctx(double... luminosities) {
        return new StarFluxContext(luminosities.clone());
    }

    private static double temp(StarFluxContext ctx, double au) {
        return StellarThermalModel.surfaceTemperature(ctx, au, ECC, ALBEDO, PRESSURE, INTERNAL);
    }

    /** Representative luminosity of a spectral class: the geometric mean of its type range. */
    private static double representativeLuminosity(StarType type) {
        return Math.sqrt(Math.max(1.0e-9, type.minLuminosity()) * type.maxLuminosity());
    }

    /* ------------------------------------------------------------ 1) distance */

    @Test
    void largerOrbitalDistanceAlwaysMeansAColderWorld() {
        StarFluxContext sun = ctx(1.0);
        double previous = Double.MAX_VALUE;
        for (double au = OrbitProfile.AU_MIN; au <= OrbitProfile.AU_MAX; au *= 1.35) {
            double t = temp(sun, au);
            assertTrue(t <= previous, "temperature must not rise with distance at " + au + " AU");
            previous = t;
        }
        assertTrue(temp(sun, 0.5) > temp(sun, 2.0) + 40.0,
                "the inner/outer contrast must be large, not cosmetic");
    }

    /* ------------------------------------------------------------ 2) luminosity */

    @Test
    void brighterStarsRaiseTheTemperatureAtTheSameDistance() {
        StarType[] ladder = {StarType.M, StarType.K, StarType.G, StarType.F, StarType.A,
                StarType.B, StarType.O};
        double previous = -1.0;
        for (StarType type : ladder) {
            double t = temp(ctx(representativeLuminosity(type)), AU);
            assertTrue(t > previous, type + " must be hotter than the previous class: "
                    + t + " <= " + previous);
            previous = t;
        }
        // Same distance, same atmosphere/albedo: the stellar class must be unmistakable.
        double m = temp(ctx(representativeLuminosity(StarType.M)), AU);
        double g = temp(ctx(representativeLuminosity(StarType.G)), AU);
        double o = temp(ctx(representativeLuminosity(StarType.O)), AU);
        assertTrue(m < g && g < o, "M/G/O must be ordered: " + m + " / " + g + " / " + o);
        assertTrue(o > m * 2.0, "the bright end must be far hotter than the dim end");
        // A black hole companion lights nothing: only the surviving star's luminosity remains.
        assertEquals(temp(ctx(1.0), AU), temp(ctx(1.0, 1.0e-4), AU), 0.5,
                "a black hole must not add usable flux");
    }

    /* ------------------------------------------------------------ 3) internal heat */

    @Test
    void internalHeatIsIntensityShapedAndHasNoMandatoryFloor() {
        // ACT 1 (audit item W2/C): the old model added an unconditional +15 K to EVERY planet.
        assertEquals(0.0, StellarThermalModel.internalHeatingK(0.0), 1e-12,
                "a geologically dead world must receive NO internal heat");
        assertEquals(0.0, StellarThermalModel.internalHeatingK(-3.0), 1e-12);
        assertEquals(StellarThermalModel.internalSpanK(),
                StellarThermalModel.internalHeatingK(1.0), 1e-12);
        assertEquals(StellarThermalModel.internalSpanK() * 0.25,
                StellarThermalModel.internalHeatingK(0.5), 1e-12,
                "the internal term must be shaped as internal01^2 * span");
        assertTrue(StellarThermalModel.internalSpanK() <= 65.0,
                "internal heat must stay a bounded, non-stellar term");

        StarFluxContext cool = ctx(0.05);
        double dead = StellarThermalModel.surfaceTemperature(cool, 3.0, ECC, ALBEDO, 0.0, 0.0);
        double mild = StellarThermalModel.surfaceTemperature(cool, 3.0, ECC, ALBEDO, 0.0, 0.3);
        double molten = StellarThermalModel.surfaceTemperature(cool, 3.0, ECC, ALBEDO, 0.0, 1.0);
        assertTrue(dead < mild && mild < molten, "internal heat must warm the surface monotonically");
        assertTrue(mild - dead < 10.0,
                "a mildly active world must not gain a big floor: " + (mild - dead));
        assertEquals(StellarThermalModel.equilibriumTemperature(cool, 3.0, ECC, ALBEDO, 1.0),
                dead, 1e-9, "with no atmosphere and no internal heat only the stellar term remains");
    }

    /* ------------------------------------------------------------ 4) greenhouse */

    @Test
    void greenhouseStaysMeaningfulBoundedAndMonotonic() {
        StarFluxContext sun = ctx(1.0);
        double thin = StellarThermalModel.surfaceTemperature(sun, AU, ECC, ALBEDO, 0.0, 0.0);
        double medium = StellarThermalModel.surfaceTemperature(sun, AU, ECC, ALBEDO, 0.5, 0.0);
        double thick = StellarThermalModel.surfaceTemperature(sun, AU, ECC, ALBEDO, 1.0, 0.0);
        assertTrue(thin < medium && medium < thick, "pressure must raise the temperature");
        assertEquals(1.0, StellarThermalModel.greenhouseMultiplier(0.0), 1e-12);
        assertEquals(1.45, StellarThermalModel.greenhouseMultiplier(1.0), 1e-12);
        assertTrue(thick < thin * 1.5, "greenhouse must stay bounded, never runaway");
    }

    /* ------------------------------------------------------------ 5) multi-star systems (G) */

    @Test
    void aBrightCompanionHeatsTheSystem() {
        // Same orbit, same planet: the flare of a second star must be visible.
        StarFluxContext single = ctx(1.0);
        StarFluxContext binary = ctx(1.0, 0.6);
        double singleT = StellarThermalModel.surfaceTemperature(single, AU, ECC, ALBEDO, PRESSURE, INTERNAL);
        double binaryT = StellarThermalModel.surfaceTemperature(binary, AU, ECC, ALBEDO, PRESSURE, INTERNAL);
        assertTrue(binaryT > singleT + 5.0,
                "a bright companion must heat the system: " + singleT + " -> " + binaryT);
        // The system-level approximation is sum(L_i / d^2): no binary orbital mechanics invented.
        assertEquals(1.6, StellarThermalModel.totalRelativeFlux(binary, 1.0), 1e-12);
        assertEquals(1.0, StellarThermalModel.totalRelativeFlux(single, 1.0), 1e-12);

        // And it is visible on a REAL generated planet, not only in the raw model.
        boolean checked = false;
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 400 && !checked; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            if (system.stars().size() < 2) continue;
            double total = 0.0;
            double primary = Math.max(0.0, system.star(0).luminosity());
            for (int i = 0; i < system.stars().size(); i++) {
                total += Math.max(0.0, system.star(i).luminosity());
            }
            if (total <= primary) continue;
            var def = system.definePlanet(0);
            double withCompanion = PlanetPropertyGenerator
                    .generateProperties(def, StarFluxContext.of(system.stars())).temperature();
            double primaryOnly = PlanetPropertyGenerator
                    .generateProperties(def, ctx(Math.max(1.0e-4, primary))).temperature();
            assertTrue(withCompanion >= primaryOnly,
                    "the companion must not cool the system: " + withCompanion + " < " + primaryOnly);
            checked = true;
        }
        assertTrue(checked, "no multi-star system found in the sample");
    }

    /* ------------------------------------------------------------ 6) moons (H) */

    private static PlanetThermal parent(double flux, double surfaceK) {
        return PlanetThermal.of(surfaceK * 0.9, AU, ECC, flux, 1.25, 12.0, surfaceK);
    }

    private static MoonOrbitMetadata moonOrbit(double relativeDistance, double eccentricity) {
        return new MoonOrbitMetadata(0, 1, relativeDistance, eccentricity, 0.4);
    }

    @Test
    void aDistantQuietMoonOfACoolStarStaysCold() {
        MoonThermal moon = MoonThermalModel.estimate(parent(0.04, 150.0), 0.25, 1.0, 1.0,
                moonOrbit(0.95, 0.01), 0.30, 0.30, 0.05);
        assertTrue(moon.temperatureK() < 200.0,
                "a distant low-eccentricity moon of a cool star must stay cold: "
                        + moon.temperatureK());
        assertTrue(moon.tidalHeating() < 0.10, "a distant quiet moon heats little");
    }

    @Test
    void aCloseEccentricMoonIsWarmerThroughTides() {
        PlanetThermal parent = parent(0.04, 150.0);
        MoonThermal quiet = MoonThermalModel.estimate(parent, 0.25, 5.0, 2.0,
                moonOrbit(0.95, 0.01), 0.30, 0.30, 0.05);
        MoonThermal tormented = MoonThermalModel.estimate(parent, 0.25, 5.0, 2.0,
                moonOrbit(0.10, 0.28), 0.30, 0.30, 0.05);
        assertTrue(tormented.tidalHeating() > quiet.tidalHeating() + 0.3,
                "tidal heating must respond to proximity and eccentricity");
        assertTrue(tormented.temperatureK() > quiet.temperatureK() + 10.0,
                "an intensely tidal moon must be measurably warmer: "
                        + quiet.temperatureK() + " -> " + tormented.temperatureK());
        // Tides stay a SECONDARY/local term: the stellar context still dominates the mean.
        assertTrue(tormented.temperatureK() < 400.0, "tides must not replace stellar heating");
    }

    @Test
    void aDeadMoonGetsNoMandatoryInternalFloor() {
        MoonThermal dead = MoonThermalModel.estimate(parent(0.04, 150.0), 0.25, 1.0, 1.0,
                moonOrbit(0.95, 0.0), 0.30, 0.0, 0.0);
        assertEquals(0.0, dead.tidalHeating(), 1e-12);
        assertEquals(dead.equilibriumK(), dead.temperatureK(), 1e-9,
                "a dead, tidally quiet moon must equal its own equilibrium temperature");
    }

    @Test
    void moonsInheritTheParentSystemStellarContext() {
        MoonThermal cold = MoonThermalModel.estimate(parent(0.05, 120.0), 0.25, 1.0, 1.0,
                moonOrbit(0.5, 0.05), 0.25, 0.2, 0.2);
        MoonThermal hot = MoonThermalModel.estimate(parent(40.0, 900.0), 0.25, 1.0, 1.0,
                moonOrbit(0.5, 0.05), 0.25, 0.2, 0.2);
        assertTrue(hot.temperatureK() > cold.temperatureK() * 1.8,
                "moons must inherit the (hot vs cold) stellar context: "
                        + cold.temperatureK() + " / " + hot.temperatureK());
        assertTrue(cold.stellarFlux() < hot.stellarFlux());
    }

    @Test
    void realMoonsStayGluedToTheirParentPlanetTemperature() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int n = 0;
        int far = 0;
        double sum = 0.0;
        double maxDelta = 0.0;
        for (int s = 0; s < 60; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                double parentT = planet.properties().temperature();
                for (Moon moon : planet.moons()) {
                    double delta = Math.abs(moon.properties().temperature() - parentT);
                    sum += delta;
                    maxDelta = Math.max(maxDelta, delta);
                    if (delta > 400.0) far++;
                    n++;
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "%n[ACT 1] real moons: n=%d  mean |T_moon - T_parent| = %.1f K  max = %.1f K"
                        + "  (>400 K: %d)%n",
                n, sum / Math.max(1, n), maxDelta, far);
        assertTrue(n > 100, "moon sample too small: " + n);
        // The PHASE 2 contract: the moon tracks its parent's stellar environment.
        assertTrue(sum / n < 200.0,
                "moon temperature drifted off its parent's stellar context: " + (sum / n));
        assertTrue(far < n * 0.02,
                "too many moons far from their parent temperature: " + far + "/" + n);
    }

    /* ------------------------------------------------------------ 7) one canonical thermal field */

    @Test
    void theStoredThermalChainExplainsEveryPlanetTemperatureExactly() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        int habitable = 0;
        for (int s = 0; s < 120; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                PlanetProperties p = system.getPlanet(o).properties();
                PlanetThermal t = p.thermal();
                assertNotNull(t, "the canonical path must carry a thermal context");
                assertTrue(t.derived(), "the canonical path must derive its thermal context");

                // (a) the class is the canonical class of the canonical temperature
                assertEquals(StellarThermalModel.thermalClass(p.temperature()), t.thermalClass(),
                        "stored thermal class must match the surface temperature");

                // (b) ACT 1: T_surface is EXACTLY T_eq * G(P) + ΔT_internal — one chain, no second
                // thermal model anywhere downstream.
                double expected = StellarThermalModel.clampKelvin(
                        t.equilibriumK() * t.greenhouse() + t.internalK());
                assertEquals(expected, p.temperature(), 1e-9,
                        "surface temperature is not the stored thermal chain at orbit " + o);

                // (c) habitability / ecology consume that same canonical Kelvin value.
                // ACT 2: isHabitable() now delegates to the canonical HabitabilityValidator,
                // so the window asserted here IS the canonical Earth-like window.
                if (p.isHabitable()) {
                    habitable++;
                    assertTrue(p.temperature()
                                    >= com.modscreating.unlimitedspace.core.habitability
                                            .HabitabilityValidator.TEMP_MIN_K
                            && p.temperature()
                                    <= com.modscreating.unlimitedspace.core.habitability
                                            .HabitabilityValidator.TEMP_MAX_K,
                            "habitable world outside the canonical ACT 2 habitability window: "
                                    + p.temperature());
                }
                checked++;
            }
        }
        assertTrue(checked > 200, "sample too small: " + checked);
        assertTrue(habitable > 0, "no habitable world in the sample");
    }

    /* ------------------------------------------------------------ 8) bands & classes (M) */

    @Test
    void everyTemperatureMapsToExactlyOneBandAndTheLadderStaysReachable() {
        assertEquals(TemperatureBand.FROZEN, TemperatureBand.ofKelvin(StellarThermalModel.T_MIN));
        assertEquals(TemperatureBand.COLD, TemperatureBand.ofKelvin(200.0));
        assertEquals(TemperatureBand.TEMPERATE, TemperatureBand.ofKelvin(288.0));
        assertEquals(TemperatureBand.WARM, TemperatureBand.ofKelvin(360.0));
        assertEquals(TemperatureBand.HOT, TemperatureBand.ofKelvin(500.0));
        assertEquals(TemperatureBand.INFERNO, TemperatureBand.ofKelvin(StellarThermalModel.T_MAX));

        for (double k = StellarThermalModel.T_MIN; k <= StellarThermalModel.T_MAX; k *= 1.05) {
            TemperatureBand band = TemperatureBand.ofKelvin(k);
            assertNotNull(band, "no band for " + k + " K");
            double normalized = StellarThermalModel.normalizeKelvin(k);
            assertTrue(normalized >= band.minNormalized()
                            && normalized < band.maxNormalized() + 1e-12,
                    "band " + band + " does not contain " + k + " K (t01=" + normalized + ")");
            assertEquals(band, TemperatureBand.of(normalized),
                    "of() and ofKelvin() disagree at " + k + " K");
        }
        // Normalization stays anchored on the canonical Kelvin range.
        assertEquals(0.0, StellarThermalModel.normalizeKelvin(StellarThermalModel.T_MIN), 1e-12);
        assertEquals(1.0, StellarThermalModel.normalizeKelvin(StellarThermalModel.T_MAX), 1e-12);

        // The worldgen profile consumes the canonical Kelvin temperature without drifting.
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        for (int s = 0; s < 60; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                PlanetProperties p = system.getPlanet(o).properties();
                double t = p.temperature();
                if (nearBandBoundary(t)) continue;
                PlanetPhysicalProfile profile =
                        PlanetPhysicalProfileFactory.create(p.seed().value(), p);
                assertEquals(TemperatureBand.ofKelvin(t), profile.temperatureBand(),
                        "profile band differs from the canonical Kelvin band at " + t + " K");
                checked++;
            }
        }
        assertTrue(checked > 100, "band sample too small: " + checked);
    }

    private static boolean nearBandBoundary(double kelvin) {
        for (double boundary : new double[]{150.0, 240.0, 320.0, 400.0, 800.0}) {
            if (Math.abs(kelvin - boundary) < 0.5) return true;
        }
        return false;
    }

    /* ------------------------------------------------------------ 9) water regression (L) */

    @Test
    void waterPhaseRegressionCasesSurviveTheRecalibration() {
        // The recalibration moved the temperature distribution; it must NOT have moved the physics
        // of PHASE 3. These are the acceptance cases named by ACT 1 (L).
        assertEquals(WaterPhaseModel.Phase.SOLID,
                WaterPhaseModel.surfacePhase(41.0, PressureClass.MODERATE, 0.60),
                "41 K wet world must be frozen");
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(41.0, PressureClass.MODERATE, 0.00),
                "41 K dry world has no water at all");
        assertEquals(WaterPhaseModel.Phase.SOLID,
                WaterPhaseModel.surfacePhase(175.0, PressureClass.MODERATE, 0.60));
        assertEquals(WaterPhaseModel.Phase.LIQUID,
                WaterPhaseModel.surfacePhase(289.0, PressureClass.MODERATE, 0.80),
                "a ~289 K wet world must hold liquid water");
        assertEquals(WaterPhaseModel.Phase.LIQUID,
                WaterPhaseModel.surfacePhase(336.0, PressureClass.MODERATE, 0.50),
                "336 K under a moderate atmosphere is still liquid");
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(336.0, PressureClass.TRACE, 0.50),
                "336 K under a near-vacuum has no stable surface water");
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(2781.0, PressureClass.MODERATE, 0.80),
                "a molten world cannot keep any surface water");

        // Distribution level: the phase never contradicts the canonical temperature.
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int examined = 0;
        for (int s = 0; s < 80; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                PlanetProperties p = system.getPlanet(o).properties();
                if (p.type() == PlanetType.GAS_GIANT) continue;
                WaterPhaseModel.Phase phase = WaterPhaseModel.ofProperties(p.temperature(),
                        p.atmosphere(), p.atmosphericDensity(),
                        PlanetPhysicalProfileFactory.waterAbundance(p.waterCoverage(), p.humidity()));
                if (p.temperature() < WaterPhaseModel.FREEZE_K - 0.5) {
                    assertNotEquals(WaterPhaseModel.Phase.LIQUID, phase,
                            "frozen world reported liquid water at " + p.temperature() + " K");
                }
                if (p.temperature() >= WaterPhaseModel.VOLATILE_LIMIT_K) {
                    assertEquals(WaterPhaseModel.Phase.NONE, phase,
                            "hot world kept surface water at " + p.temperature() + " K");
                }
                examined++;
            }
        }
        assertTrue(examined > 100, "water sample too small: " + examined);
    }
}

