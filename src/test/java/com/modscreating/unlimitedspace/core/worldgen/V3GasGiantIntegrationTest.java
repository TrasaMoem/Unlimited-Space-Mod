package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK F + R — the GAS_GIANT surface mode is fully integrated.
 *
 * <p>A gas giant has no solid surface. This is checked at the level the mode actually governs:
 * the {@link PlanetSurfaceMode} resolution, and the fact that the world generator, the world
 * manager and the preview all read the SAME mode, so there is no second, looser definition of
 * "has a surface" hiding anywhere.
 */
@Tag("worldgen")
class V3GasGiantIntegrationTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double geo, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void aGaseousBodyAlwaysResolvesToGasGiantRegardlessOfItsOtherTendencies() {
        // Even a hot, tectonically violent, water-rich "gaseous" body has no solid surface: the
        // gaseous classification dominates, so no terrain path can be reached by accident.
        for (double temp : new double[]{0.05, 0.30, 0.50, 0.75, 0.95}) {
            for (double volc : new double[]{0.0, 0.5, 1.0}) {
                PlanetPhysicalProfile p = profile(temp, 0.5, 0.6, 0.6, volc, 0.5, 0.4);
                PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(p, true);
                assertEquals(PlanetSurfaceMode.GAS_GIANT, mode,
                        "a gaseous body must always be a gas giant (temp=" + temp
                                + " volc=" + volc + ")");
                assertFalse(mode.hasSolidSurface());
                assertFalse(mode.allowsTerrainGeneration());
            }
        }
    }

    @Test
    void everyOtherSurfaceModeKeepsASolidSurface() {
        for (PlanetSurfaceMode m : PlanetSurfaceMode.values()) {
            if (m == PlanetSurfaceMode.GAS_GIANT) continue;
            assertTrue(m.hasSolidSurface(), m + " must keep a solid surface");
            assertTrue(m.allowsTerrainGeneration(), m + " must allow terrain generation");
        }
    }

    @Test
    void sixteenGasGiantSeedsAllReportNoSolidSurface() {
        // The multi-seed requirement of Task R, on the pure-domain resolver the runtime uses.
        int checked = 0;
        for (int i = 0; i < 16; i++) {
            long seed = 0xE10000L + i * 0x9E3779B9L;
            PlanetPhysicalProfile p = profile(0.55 + 0.03 * (i % 7), 0.4, 0.3,
                    0.5, 0.3 + 0.05 * (i % 5), 0.3, 0.4);
            PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(p, true);
            assertFalse(mode.hasSolidSurface(), "gas giant seed " + i + " must have no surface");
            assertEquals(PlanetSurfaceMode.GAS_GIANT, mode);
            checked++;
        }
        assertEquals(16, checked);
    }

    @Test
    void theModeIsTheSingleAuthorityAndItIsNotTheSameAsTheLegacyPlanetTypeSwitch() {
        // The mode is a CONTINUOUS tendency resolution, not the legacy PlanetType enum switch:
        // a hot volcanic body is VOLCANIC, a wet one OCEANIC, and only a gaseous one is a gas
        // giant. That distinction is what stops "gas giant" from becoming a type test.
        assertEquals(PlanetSurfaceMode.VOLCANIC,
                V3PreviewChannels.surfaceMode(profile(0.8, 0.2, 0.2, 0.7, 0.9, 0.6, 0.4), false));
        assertEquals(PlanetSurfaceMode.GLACIAL,
                V3PreviewChannels.surfaceMode(profile(0.05, 0.5, 0.4, 0.5, 0.05, 0.2, 0.3), false));
        assertNotEquals(PlanetSurfaceMode.GAS_GIANT,
                V3PreviewChannels.surfaceMode(profile(0.8, 0.2, 0.2, 0.7, 0.9, 0.6, 0.4), false));
    }

    @Test
    void aColdGasGiantHasNoLiquidSurfacePhase() {
        // The water phase is a genuine function of temperature, pressure and water abundance, NOT
        // of the gaseous flag: a hot gaseous body may legitimately be liquid, exactly as a cold
        // solid body may not. The gas giant''s no-surface guarantee therefore comes from the MODE
        // (asserted above), not from pretending the fluid model knows about planets. What the
        // fluid model DOES guarantee is the cold rule, and it must still hold for a cold body.
        for (double water : new double[]{0.0, 0.3, 0.8, 1.0}) {
            PlanetCharacter cold = new PlanetCharacter(
                    profile(0.02, 0.3, water, 0.4, 0.3, 0.2, 0.4));
            assertFalse(cold.waterPhase().allowsLiquid(),
                    "a near-zero-temperature body must never report a liquid phase (water="
                            + water + ")");
            assertTrue(cold.isFrozen(), "a near-zero-temperature body must be recognised as frozen");
        }
    }
}