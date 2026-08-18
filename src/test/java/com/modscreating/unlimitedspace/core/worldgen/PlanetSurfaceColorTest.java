package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12 test sets 1-2: deterministic, differentiated planet surface colours.
 */
class PlanetSurfaceColorTest {

    private static PlanetProperties props(long seedValue, PlanetType type, PlanetSurface surface,
                                          double temperature, double water) {
        return new PlanetProperties(
                new PlanetSeed(seedValue), type, surface,
                1.0, 1.0, temperature, 0.5,
                AtmosphereType.MODERATE, 0.5, water,
                0.5, 0.5, 0.5, 0.5, 0.5,
                new PlanetProperties.ResourceProfile(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.1, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void samePlanetAlwaysProducesSameColour() {
        PlanetProperties p = props(42L, PlanetType.ROCKY, PlanetSurface.SOLID_ROCKY, 280, 0.3);
        assertEquals(PlanetSurfaceColor.surfaceColorArgb(p),
                PlanetSurfaceColor.surfaceColorArgb(p));
    }

    @Test
    void differentPlanetsProduceDifferentColoursAcrossSeeds() {
        Set<Integer> colours = new HashSet<>();
        for (long s = 1; s <= 16; s++) {
            colours.add(PlanetSurfaceColor.surfaceColorArgb(
                    props(s, PlanetType.ROCKY, PlanetSurface.SOLID_ROCKY, 500.0, 0.3)));
        }
        assertTrue(colours.size() >= 2, "expected variation across seeds, got " + colours.size());
    }

    @Test
    void volcanicSurfaceIsDarkAndReddish() {
        int c = PlanetSurfaceColor.surfaceColorArgb(
                props(7L, PlanetType.VOLCANIC, PlanetSurface.SOLID_VOLCANIC, 800.0, 0.1));
        int r = (c >> 16) & 0xFF;
        int b = c & 0xFF;
        assertTrue(r > b, "volcanic surface should be reddish, r=" + r + " b=" + b);
        assertTrue(r < 140, "volcanic surface should stay dark, r=" + r);
    }

    @Test
    void icePlanetIsPaleBlue() {
        int c = PlanetSurfaceColor.surfaceColorArgb(
                props(11L, PlanetType.ICE, PlanetSurface.SOLID_ICE, 160.0, 0.6));
        int r = (c >> 16) & 0xFF;
        int b = c & 0xFF;
        assertTrue(b > r, "ice surface should be bluish, r=" + r + " b=" + b);
        assertTrue(b > 200, "ice surface should be pale, b=" + b);
    }

    @Test
    void desertPlanetIsWarm() {
        int c = PlanetSurfaceColor.surfaceColorArgb(
                props(13L, PlanetType.DESERT, PlanetSurface.SOLID_DESERT, 340.0, 0.05));
        int r = (c >> 16) & 0xFF;
        int b = c & 0xFF;
        assertTrue(r > b, "desert surface should be warm, r=" + r + " b=" + b);
    }

    @Test
    void gasGiantIsNotPureBlack() {
        int c = PlanetSurfaceColor.surfaceColorArgb(
                props(15L, PlanetType.GAS_GIANT, PlanetSurface.GASEOUS, 200.0, 0.0));
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        assertFalse(r == 0 && g == 0 && b == 0, "gas giant must have a visible tint");
        assertNotEquals(0, c);
    }
}