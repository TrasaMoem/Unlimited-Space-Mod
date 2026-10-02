package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.MoonThermalModel;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHASE 9 tests: the canonical thermal facts the player and the diagnostics read
 * ("288 K (15 C)", "Temperate", "Water phase: Ice") are DERIVED from the same physical values the
 * worldgen consumes — never re-invented by the UI.
 */
class ThermalFactsTest {

    private static final long SEED = 0x51EEDL;

    @Test
    void temperatureTextCarriesBothScales() {
        assertEquals("288 K (15 C)", StellarThermalModel.temperatureText(288.15));
        assertTrue(StellarThermalModel.temperatureText(120.0).contains("120 K"));
        assertEquals(-153.15, StellarThermalModel.celsius(120.0), 1e-9);
        // Clamped to the canonical design range, so nonsense still prints sanely.
        assertTrue(StellarThermalModel.temperatureText(-500.0).contains("30 K"));
        assertTrue(StellarThermalModel.temperatureText(99_999.0).contains("4600 K"));
    }

    @Test
    void everyThermalClassHasALabel() {
        for (StellarThermalModel.ThermalClass c : StellarThermalModel.ThermalClass.VALUES) {
            assertNotNull(c.displayName());
            assertFalse(c.displayName().isBlank(), "unlabelled thermal class " + c);
        }
        for (MoonThermalModel.MoonThermalClass c : MoonThermalModel.MoonThermalClass.VALUES) {
            assertNotNull(c.displayName());
            assertFalse(c.displayName().isBlank(), "unlabelled moon thermal class " + c);
        }
    }

    @Test
    void derivedThermalAnnouncesItsOrbitAndFlux() {
        PlanetThermal derived = PlanetThermal.of(277.0, 1.0, 0.05, 1.0, 288.0);
        String text = derived.describe(288.0);
        assertTrue(text.contains("orbit 1.00 AU"), text);
        assertTrue(text.contains("flux 1.00x"), text);
        assertTrue(text.contains("T_eq 277 K"), text);
        assertTrue(text.contains("288 K (15 C)"), text);
        assertTrue(text.contains("temperate"), text);
        assertFalse(text.contains("no stellar context"), text);
    }

    @Test
    void legacyThermalSaysItHasNoStellarContext() {
        String text = PlanetThermal.none(150.0).describe(150.0);
        assertTrue(text.contains("150 K"), text);
        assertTrue(text.contains("frozen"), text);
        assertTrue(text.contains("no stellar context"), text);
        assertFalse(text.contains("orbit"), text);
    }

    @Test
    void waterPhaseLabelsCoverEveryPhase() {
        for (WaterPhaseModel.Phase p : WaterPhaseModel.Phase.VALUES) {
            assertNotNull(p.displayName());
            assertFalse(p.displayName().isBlank(), "unlabelled water phase " + p);
        }
        assertEquals("Ice", WaterPhaseModel.Phase.SOLID.displayName());
        assertEquals("Liquid water", WaterPhaseModel.Phase.LIQUID.displayName());
    }

    @Test
    void waterAvailabilityBlendIsTheWorldgenOne() {
        assertEquals(0.0, PlanetPhysicalProfileFactory.waterAbundance(0.0, 0.0), 1e-12);
        assertEquals(0.9, PlanetPhysicalProfileFactory.waterAbundance(0.9, 0.9), 1e-12);
        assertTrue(PlanetPhysicalProfileFactory.waterAbundance(0.0, 1.0) > 0.02,
                "atmospheric humidity alone keeps some water available");
        assertEquals(1.0, PlanetPhysicalProfileFactory.waterAbundance(4.0, 4.0), 1e-12);
    }

    @Test
    void uiPhaseNeverShowsLiquidWaterOnAFrozenWorld() {
        for (double k = StellarThermalModel.T_MIN; k < WaterPhaseModel.FREEZE_K; k += 4.0) {
            WaterPhaseModel.Phase p = WaterPhaseModel.ofProperties(k, AtmosphereType.MODERATE, 0.8,
                    PlanetPhysicalProfileFactory.waterAbundance(0.9, 0.9));
            assertNotEquals(WaterPhaseModel.Phase.LIQUID, p,
                    "frozen world reported liquid water at " + k + " K");
            assertNotEquals(WaterPhaseModel.Phase.VAPOR, p,
                    "frozen world reported boiling water at " + k + " K");
        }
    }

    @Test
    void uiFactsMatchTheWorldgenPhaseForEveryPlanetOfASystem() {
        Galaxy galaxy = Galaxy.from(SEED);
        StarSystem sys = galaxy.getStarSystem(galaxy.systemId(0));
        int compared = 0;
        for (int i = 0; i < sys.planetCount(); i++) {
            PlanetProperties props = sys.getPlanet(i).properties();
            if (props == null || props.isGasGiant()) continue;
            PlanetPhysicalProfile profile = PlanetPhysicalProfileFactory.create(SEED, props);
            double boiling = WaterPhaseModel.boilingK(profile.pressureClass());
            if (Math.abs(props.temperature() - WaterPhaseModel.FREEZE_K) < 1.0
                    || Math.abs(props.temperature() - WaterPhaseModel.MIXED_TOP_K) < 1.0
                    || Math.abs(props.temperature() - boiling) < 1.0) {
                continue;   // the log-normalized round trip may legitimately flip an exact boundary
            }
            WaterPhaseModel.Phase worldgen = WaterPhaseModel.ofProfile(profile);
            WaterPhaseModel.Phase ui = WaterPhaseModel.ofProperties(props.temperature(),
                    props.atmosphere(), props.atmosphericDensity(),
                    PlanetPhysicalProfileFactory.waterAbundance(props.waterCoverage(), props.humidity()));
            assertEquals(worldgen, ui, "UI phase differs from the worldgen phase at orbit " + i);
            compared++;
        }
        assertTrue(compared > 0, "no planet of the system was compared");
    }
}
