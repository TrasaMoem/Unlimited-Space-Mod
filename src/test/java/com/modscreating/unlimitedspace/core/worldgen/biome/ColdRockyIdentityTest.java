package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ACT worldgen fix — STAGE 0.2 / STAGE 0.3: geological identity is the AUTHORITY, climate is
 * modulation. A merely cold {@code SOLID_ROCKY} world must keep its rocky identity (and its
 * exposed / frozen rock), and a volcanic / desert / ice world must not be renamed by a climate
 * tendency.
 */
class ColdRockyIdentityTest {

    @Test
    void aColdRockyWorldStaysRockyRegardlessOfGlacialTendency() {
        // temperature01 = 150 K is genuinely cold; glacialWeight is deliberately extreme to prove
        // the climate tendency can no longer rewrite the identity.
        for (double t : new double[]{0.10, 0.18, 0.20, 0.25}) {
            PlanetSurfaceMode mode = PlanetSurfaceMode.of(PlanetSurface.SOLID_ROCKY, false,
                    t, 0.5, 0.2, 0.95, 0.1, 0.2, 0.2);
            assertEquals(PlanetSurfaceMode.SOLID_ROCKY, mode,
                    "a cold SOLID_ROCKY world must stay SOLID_ROCKY at T01=" + t
                            + ", got " + mode);
        }
    }

    @Test
    void climateTendenciesCannotRewriteGeology() {
        assertEquals(PlanetSurfaceMode.VOLCANIC,
                PlanetSurfaceMode.of(PlanetSurface.SOLID_VOLCANIC, false,
                        0.9, 0.1, 0.95, 0.1, 0.9, 0.2, 0.2),
                "a dune tendency must not turn a volcanic world into a desert");
        assertEquals(PlanetSurfaceMode.DUNE_ARID,
                PlanetSurfaceMode.of(PlanetSurface.SOLID_DESERT, false,
                        0.9, 0.1, 0.9, 0.05, 0.05, 0.2, 0.1),
                "a desert world must resolve to DUNE_ARID");
        assertEquals(PlanetSurfaceMode.GLACIAL,
                PlanetSurfaceMode.of(PlanetSurface.SOLID_ICE, false,
                        0.12, 0.5, 0.1, 0.8, 0.05, 0.2, 0.4),
                "an ice world must resolve to GLACIAL");
        assertEquals(PlanetSurfaceMode.OCEANIC,
                PlanetSurfaceMode.of(PlanetSurface.OCEANIC, false,
                        0.5, 0.8, 0.1, 0.1, 0.05, 0.2, 0.9),
                "an ocean world must resolve to OCEANIC");
        // The identity-free overload is retained for synthetic diagnostics only.
        assertEquals(PlanetSurfaceMode.GLACIAL,
                PlanetSurfaceMode.of(false, 0.02, 0.5, 0.5, 0.95, 0.05, 0.2, 0.4),
                "without a geological identity the tendency fallback still applies");
    }

    // classifySurface(volcanic, crystal, glacial, temp01, lake, river, slope, elev01, duneRelief,
    //                 humidity01, waterProx, organic, wetness01, rockShare, mountainEnv,
    //                 hotVolcanicWorld, duneWeight)
    private static SurfaceCategory classify(double temperature01, double rockShare) {
        return V3ColumnSampler.classifySurface(0.0, 0.0, 0.0, temperature01, 0.0, 0.0,
                0.10, 0.30, 0.0, 0.40, 0.0, 0.0, 0.0, rockShare, 0.30, false, 0.0);
    }

    @Test
    void aColdRockDominatedColumnIsNotAClacier() {
        // The reported values: SOLID_ROCKY at T=150 K with rockShare 0.7 and 0.9.
        for (double rock : new double[]{0.70, 0.90}) {
            SurfaceCategory c = classify(0.20, rock);
            assertNotEquals(SurfaceCategory.GLACIAL, c,
                    "temperature alone must not turn a rock-dominated column into GLACIAL"
                            + " (rockShare=" + rock + ")");
        }
    }

    @Test
    void aColdNonRockColumnIsStillATrueGlacier() {
        // The fix must not delete the glacier: a genuinely cold, rock-poor column stays GLACIAL.
        assertEquals(SurfaceCategory.GLACIAL, classify(0.15, 0.10),
                "a cold, rock-poor column is a genuine glacial plain");
    }

    @Test
    void anExposedRockFaceStaysRockyEvenWhenCold() {
        // Steep / high rock exposure => bare rock, at any temperature.
        SurfaceCategory steepCold = V3ColumnSampler.classifySurface(0.0, 0.0, 0.0, 0.15,
                0.0, 0.0, 0.70, 0.40, 0.0, 0.30, 0.0, 0.0, 0.0, 0.40, 0.40, false, 0.0);
        assertEquals(SurfaceCategory.ROCKY, steepCold,
                "an exposed rock face must stay ROCKY on a cold world");
        SurfaceCategory highCold = V3ColumnSampler.classifySurface(0.0, 0.0, 0.0, 0.15,
                0.0, 0.0, 0.20, 0.80, 0.0, 0.30, 0.0, 0.0, 0.0, 0.40, 0.40, false, 0.0);
        assertEquals(SurfaceCategory.ROCKY, highCold,
                "high exposed rock must stay ROCKY on a cold world");
    }
}