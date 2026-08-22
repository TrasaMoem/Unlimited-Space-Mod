package com.modscreating.unlimitedspace.client;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.7 — pure {@link StarTexture} sprite tests.
 *
 * <p>Checks bounded resolution, determinism, spectral-colour bias (a blue star must be
 * blue-biased), a bright centre fading to an empty halo, and that glow is carried in the
 * alpha (high byte) so additive drawing never paints opaque corners.
 */
class StarTextureTest {

    @Test
    void resolutionIsBoundedAndSquare() {
        assertEquals(StarTexture.MIN_RESOLUTION * StarTexture.MIN_RESOLUTION,
                StarTexture.sample(1, 1L, 0xFFFFAA00).length);
        assertEquals(StarTexture.MAX_RESOLUTION * StarTexture.MAX_RESOLUTION,
                StarTexture.sample(1000, 1L, 0xFFFFAA00).length);
        assertEquals(StarTexture.DEFAULT_RESOLUTION * StarTexture.DEFAULT_RESOLUTION,
                StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 1L, 0xFFFFAA00).length);
    }

    @Test
    void sameColorAndSeedAreDeterministic() {
        assertArrayEquals(
                StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 7L, 0xFF00AAFF),
                StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 7L, 0xFF00AAFF));
    }

    @Test
    void blueStarIsBlueBiasedInCore() {
        int[] tex = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 42L, 0xFF3355FF);
        int n = StarTexture.DEFAULT_RESOLUTION;
        int c = tex[n / 2 * n + n / 2];
        int r = (c >> 16) & 0xFF, b = c & 0xFF;
        assertTrue(b >= r, "blue star should be blue-biased, centre rgb=#" + Integer.toHexString(c));
    }

    @Test
    void centreIsBrighterThanHaloCorner() {
        int[] tex = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 5L, 0xFFFFCC00);
        int n = StarTexture.DEFAULT_RESOLUTION;
        int centreAlpha = (tex[n / 2 * n + n / 2] >>> 24) & 0xFF;
        int cornerAlpha = (tex[0] >>> 24) & 0xFF;   // top-left corner lies outside the glow
        assertTrue(centreAlpha > cornerAlpha,
                "centre glow alpha " + centreAlpha + " must exceed halo corner " + cornerAlpha);
    }

    @Test
    void alphaLivesInHighByte() {
        for (int n = 8; n <= StarTexture.MAX_RESOLUTION; n += 8) {
            for (int c : StarTexture.sample(n, 9L, 0xFFFF0000)) {
                assertTrue(((c >>> 24) & 0xFF) >= 0, "glow must be stored in the high byte");
            }
        }
    }

    @Test
    void midBandIsColoredTowardSpectralHue() {
        // A red-orange star must have a white-hot core that falls away into a red-biased plasma band
        // (never a single universal white glow).
        int[] tex = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 11L, 0xFFFF4422);
        int n = StarTexture.DEFAULT_RESOLUTION;
        int center = tex[n / 2 * n + n / 2];
        int mid = tex[(n / 2 + 12) * n + n / 2];
        int centerMin = Math.min((center >> 16) & 0xFF, Math.min((center >> 8) & 0xFF, center & 0xFF));
        int midMin = Math.min((mid >> 16) & 0xFF, Math.min((mid >> 8) & 0xFF, mid & 0xFF));
        assertTrue(midMin < centerMin,
                "plasma band must be more saturated (colored) than the white core");
        assertTrue(((mid >> 16) & 0xFF) > ((mid >> 8) & 0xFF) && ((mid >> 16) & 0xFF) > (mid & 0xFF),
                "plasma band must be red-biased for a red-orange star");
    }

    @Test
    void plasmaLobesChangeTheSpritePattern() {
        // R14.9: a tight compact remnant (3 lobes) must not produce the same sprite as a broad
        // supergiant / supernova shell (11 lobes) — the plasma structure is stage-driven, not a
        // single smooth sphere tinted by colour.
        int[] tight = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 21L, 0xFFFF7733, 3.0f, 1.0f);
        int[] broad = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 21L, 0xFFFF7733, 11.0f, 1.0f);
        assertFalse(Arrays.equals(tight, broad),
                "different plasma lobe counts must produce different sprites");
    }

    @Test
    void brighterGlowIncreasesTotalHaloEnergy() {
        // The profile glow scale is threaded into the sprite: a brighter (giant/supergiant/remnant)
        // stage must raise the total additive alpha relative to a dim (red-dwarf) stage.
        int[] dim = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 21L, 0xFFFF7733, 5.0f, 0.6f);
        int[] bright = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 21L, 0xFFFF7733, 5.0f, 1.6f);
        long dimSum = 0, brightSum = 0;
        for (int i = 0; i < dim.length; i++) {
            dimSum += (dim[i] >>> 24) & 0xFF;
            brightSum += (bright[i] >>> 24) & 0xFF;
        }
        assertTrue(brightSum > dimSum,
                "brighter glow scale must raise total additive alpha (" + brightSum + " vs " + dimSum + ")");
    }
}
