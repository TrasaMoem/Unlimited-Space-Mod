package com.modscreating.unlimitedspace.core.stars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9 — continuous temperature→colour tests. Asserts the M < K < G < F < A < B < O ordering
 * qualitatively produces hotter/bluer (blue channel non-decreasing, red channel non-increasing)
 * rather than a set of seven flat hard-coded colours.
 */
class StarColorTest {

    /** Midpoint temperature of each class in coolest→hottest order (M first so names match assertions). */
    private static final double[] MIDPOINT_K = {3100, 4400, 5600, 6800, 8800, 20000, 40000};

    @Test
    void blueChannelIncreasesFromCoolToHot() {
        double prevBlue = -1;
        for (double k : MIDPOINT_K) {
            float[] rgb = StarColor.temperatureRgbFloats(k);
            assertTrue(rgb[2] >= prevBlue - 1e-4f,
                    "blue at " + k + " (" + rgb[2] + ") must be >= previous " + prevBlue);
            prevBlue = rgb[2];
        }
    }

    @Test
    void redChannelDecreasesFromCoolToHot() {
        double prevRed = Double.MAX_VALUE;
        for (double k : MIDPOINT_K) {
            float[] rgb = StarColor.temperatureRgbFloats(k);
            assertTrue(rgb[0] <= prevRed + 1e-4f,
                    "red at " + k + " (" + rgb[0] + ") must be <= previous " + prevRed);
            prevRed = rgb[0];
        }
    }

    @Test
    void coldStarIsRedderThanHotStar() {
        float[] m = StarColor.temperatureRgbFloats(3100.0);
        float[] o = StarColor.temperatureRgbFloats(40000.0);
        assertTrue(m[0] > o[0], "cold star red channel should exceed hot star");
        assertTrue(o[2] > m[2], "hot star blue channel should exceed cold star");
    }

    @Test
    void hotStarRendersBlueBiasedAndColdRedBiased() {
        float[] hot = StarColor.temperatureRgbFloats(StarType.O.minTemperature());
        float[] cold = StarColor.temperatureRgbFloats(StarType.M.minTemperature());
        assertTrue(hot[2] > hot[0], "O plasma should be blue-biased");
        assertTrue(cold[0] > cold[2], "M plasma should be red-biased");
    }

    @Test
    void coreIsBrighterThanPlasma() {
        // coreRgb pushes each channel toward white, so every channel must be >= the plasma channel.
        for (double k : MIDPOINT_K) {
            int core = StarColor.coreRgb(k);
            int plasma = StarColor.temperatureRgb(k);
            assertTrue(((core >> 16) & 0xFF) >= ((plasma >> 16) & 0xFF), "core R >= plasma R @" + k);
            assertTrue(((core >> 8) & 0xFF) >= ((plasma >> 8) & 0xFF), "core G >= plasma G @" + k);
            assertTrue((core & 0xFF) >= (plasma & 0xFF), "core B >= plasma B @" + k);
        }
    }
}
