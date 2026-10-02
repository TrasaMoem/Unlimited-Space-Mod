package com.modscreating.unlimitedspace.core.worldgen.fluids;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 3 tests: the water-phase hard invariants.
 *
 * <p>The critical bug this guards (C2): cold worlds used to receive LIQUID surface water
 * because CRYOGENIC resolved to {@code Blocks.WATER}. Now a frozen world can only ever
 * produce SOLID phase, and hot worlds lose ordinary water to VAPOR / NONE.
 */
@Tag("worldgen")
class WaterPhaseModelTest {

    private static final PressureClass MODERATE = PressureClass.MODERATE;

    @Test
    void belowFreezingOrdinaryLiquidWaterIsImpossible() {
        // The hard invariant: T < 273.15 K → never LIQUID, whatever the pressure / water.
        for (double k = 30.0; k < WaterPhaseModel.FREEZE_K; k += 5.0) {
            WaterPhaseModel.Phase p = WaterPhaseModel.surfacePhase(k, MODERATE, 1.0);
            final double kk = k;
            assertNotEquals(WaterPhaseModel.Phase.LIQUID, p,
                    () -> "liquid water at " + kk + " K violates the freeze invariant");
            assertEquals(WaterPhaseModel.Phase.SOLID, p,
                    () -> "frozen water at " + kk + " K must be SOLID");
        }
    }

    @Test
    void warmModerateWorldsHoldLiquid() {
        assertEquals(WaterPhaseModel.Phase.LIQUID,
                WaterPhaseModel.surfacePhase(288.0, MODERATE, 0.6));
        assertEquals(WaterPhaseModel.Phase.LIQUID,
                WaterPhaseModel.surfacePhase(300.0, PressureClass.DENSE, 0.8));
    }

    @Test
    void nearFreezingBandIsMixedNotFullyLiquid() {
        assertEquals(WaterPhaseModel.Phase.MIXED,
                WaterPhaseModel.surfacePhase(274.5, MODERATE, 0.5));
        assertTrue(WaterPhaseModel.surfacePhase(275.5, MODERATE, 0.5).isSolid());
    }

    @Test
    void thinAtmospheresBoilFarBelowDenseOnes() {
        assertTrue(WaterPhaseModel.boilingK(PressureClass.THIN) < WaterPhaseModel.BOIL_MODERATE_K,
                "thin air must boil below 373 K");
        assertTrue(WaterPhaseModel.boilingK(PressureClass.DENSE) > WaterPhaseModel.BOIL_MODERATE_K,
                "dense air must boil above 373 K");
        assertEquals(WaterPhaseModel.Phase.VAPOR,
                WaterPhaseModel.surfacePhase(350.0, PressureClass.THIN, 0.8));
    }

    @Test
    void hotWorldsLoseOrdinarySurfaceWater() {
        assertEquals(WaterPhaseModel.Phase.VAPOR,
                WaterPhaseModel.surfacePhase(500.0, PressureClass.DENSE, 0.9));
        assertEquals(WaterPhaseModel.Phase.VAPOR,
                WaterPhaseModel.surfacePhase(420.0, MODERATE, 0.9));
        // Ultrahot worlds: not even vapour — the surface volatiles are gone entirely.
        assertTrue(WaterPhaseModel.surfacePhase(1200.0, PressureClass.CRUSHING, 0.9).isDry());
    }

    @Test
    void pressureMattersVacuumWorldsStayDryOrFrozen() {
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(290.0, PressureClass.VACUUM, 0.9));
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(300.0, PressureClass.TRACE, 0.9));
        // Bare ice survives vacuum.
        assertEquals(WaterPhaseModel.Phase.SOLID,
                WaterPhaseModel.surfacePhase(120.0, PressureClass.VACUUM, 0.5));
    }

    @Test
    void noWaterMeansNoPhase() {
        assertEquals(WaterPhaseModel.Phase.NONE,
                WaterPhaseModel.surfacePhase(288.0, MODERATE, 0.0));
    }

    @Test
    void localTemperatureIsMonotonicInClimate() {
        double warmer = WaterPhaseModel.localSurfaceKelvin(240.0, 0.9, 0.0);
        double colder = WaterPhaseModel.localSurfaceKelvin(240.0, 0.1, 0.0);
        assertTrue(warmer > colder, "a warmer climate cell must never be colder");
        double valley = WaterPhaseModel.localSurfaceKelvin(240.0, 0.5, 0.0);
        double peak = WaterPhaseModel.localSurfaceKelvin(240.0, 0.5, 1.0);
        assertTrue(peak <= valley, "high ground must not be warmer");
        double prev = -1.0;
        for (double c = 0.0; c <= 1.0001; c += 0.1) {
            double t = WaterPhaseModel.localSurfaceKelvin(240.0, c, 0.2);
            assertTrue(t >= prev);
            prev = t;
        }
    }

    @Test
    void aNearFreezingPlanetCanFreeItsLowlandsAboveTheFreezePoint() {
        // 260 K planet + very warm climate cell + lowland → above the freeze point.
        double local = WaterPhaseModel.localSurfaceKelvin(260.0, 1.0, 0.0);
        assertTrue(local > WaterPhaseModel.FREEZE_K,
                "warm lowlands of a near-freezing world may thaw (got " + local + " K)");
        // But a deeply frozen planet cannot thaw: exp modulation is bounded (±37 %).
        assertTrue(WaterPhaseModel.localSurfaceKelvin(150.0, 1.0, 0.0) < WaterPhaseModel.FREEZE_K);
    }

    @Test
    void geothermalPocketsThawOnlyInThermalProvincesWithStrongFlux() {
        assertEquals(WaterPhaseModel.Phase.LIQUID, WaterPhaseModel.geothermalPocketPhase(
                WaterPhaseModel.Phase.SOLID, GeologicalProvince.GEOTHERMAL, 0.8));
        assertEquals(WaterPhaseModel.Phase.SOLID, WaterPhaseModel.geothermalPocketPhase(
                WaterPhaseModel.Phase.SOLID, GeologicalProvince.GEOTHERMAL, 0.3));
        // Non-thermal provinces never thaw.
        assertEquals(WaterPhaseModel.Phase.SOLID, WaterPhaseModel.geothermalPocketPhase(
                WaterPhaseModel.Phase.SOLID, GeologicalProvince.PLAINS, 1.0));
        // On a liquid world the pocket logic changes nothing.
        assertEquals(WaterPhaseModel.Phase.LIQUID, WaterPhaseModel.geothermalPocketPhase(
                WaterPhaseModel.Phase.LIQUID, GeologicalProvince.GEOTHERMAL, 0.0));
    }

    @Test
    void frozenPlanetsNeverResolveToPlainLiquidWaterFamilies() {
        int frozenWorlds = 0;
        for (long seed = 1; seed <= 60; seed++) {
            for (double normT : new double[]{0.02, 0.05, 0.10}) {
                PlanetPhysicalProfile p = frozenProfile(normT);
                WaterPhaseModel.Phase phase = WaterPhaseModel.ofProfile(p);
                if (!phase.isSolid()) continue;
                frozenWorlds++;
                FluidFamily family = FluidFamily.select(p);
                assertTrue(family == FluidFamily.CRYOGENIC
                                || family == FluidFamily.NONE
                                || family == FluidFamily.MOLTEN,
                        "frozen profile (T=" + normT + ") produced " + family);
            }
        }
        assertTrue(frozenWorlds > 0, "the sweep must contain frozen worlds");
    }

    /** A cold, wet, moderate-pressure profile at the given log-normalized temperature. */
    private static PlanetPhysicalProfile frozenProfile(double normT) {
        return new PlanetPhysicalProfile(
                normT, TemperatureBand.of(normT),
                0.6, 0.6, PressureClass.MODERATE,
                0.7, 0.6, 0.4,
                0.4, 0.2, 0.2, 0.3, 0.4,
                0.4, 0.3, 0.2, 0.3, 0.3,
                0.2, 0.5, 0.5,
                GravityClass.STANDARD, PlanetSurface.SOLID_ROCKY);
    }
}

