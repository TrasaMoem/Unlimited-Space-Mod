package com.modscreating.unlimitedspace.client;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12.7 Bug #1 — deterministic planet/moon pixel textures (pure math only).
 *
 * <p>Checks: same seed+colours => identical texture; different bodies can differ; a texture carries
 * multiple material regions; the palette responds to the body's material colours (e.g. oceans);
 * resolution is bounded; moon (own seed) is deterministic and independent of the parent seed.
 * These tests never claim to prove the final on-screen look.
 */
class PlanetPixelTextureTest {

    @Test
    void sameSeedSameColorsYieldIdenticalTexture() {
        int[] a = PlanetPixelTexture.sample(16, 42L, 0xFF9A8F80, 0, 0.30f, 0.15f);
        int[] b = PlanetPixelTexture.sample(16, 42L, 0xFF9A8F80, 0, 0.30f, 0.15f);
        assertArrayEquals(a, b);
    }

    @Test
    void differentSeedsCanDiffer() {
        int[] base = PlanetPixelTexture.sample(16, 0L, 0xFF8B8FA0, 0, 0.2f, 0.1f);
        boolean anyDiff = false;
        for (long s = 1; s < 50; s++) {
            if (!Arrays.equals(base, PlanetPixelTexture.sample(16, s, 0xFF8B8FA0, 0, 0.2f, 0.1f))) {
                anyDiff = true;
                break;
            }
        }
        assertTrue(anyDiff, "different body seeds should be able to produce different textures");
    }

    @Test
    void textureHasMultipleMaterialRegions() {
        // a neutral rocky body with a mild water/ice mix must contain several distinct pixel colours
        int[] tex = PlanetPixelTexture.sample(16, 7L, 0xFF9A8F80, 0xFF2E5FA3, 0.45f, 0.30f);
        Set<Integer> distinct = new HashSet<>();
        for (int c : tex) distinct.add(c);
        assertTrue(distinct.size() >= 4,
                "expected several material/region colours, got " + distinct.size());
    }

    @Test
    void oceanRegionsReflectWaterColour() {
        // high water + blue water -> a substantial blue fraction (derived from the material's water)
        int water = 0xFF2E5FA3;
        int[] tex = PlanetPixelTexture.sample(16, 99L, 0xFF9A8F80, water, 0.85f, 0.0f);
        int blueish = 0;
        for (int c : tex) {
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (b > r + 20 && b > g + 20) blueish++;
        }
        assertTrue(blueish > tex.length * 0.3f,
                "oceans should dominate a high-water body, blue fraction=" + blueish + "/" + tex.length);
    }

    @Test
    void resolutionIsBoundedAndSquare() {
        // clamping: too large -> MAX, too small -> MIN, and the array is exactly n*n
        assertTrue(PlanetPixelTexture.sample(1000, 1L, 0xFF8B8FA0, 0, 0, 0).length
                == PlanetPixelTexture.MAX_RESOLUTION * PlanetPixelTexture.MAX_RESOLUTION);
        assertTrue(PlanetPixelTexture.sample(1, 1L, 0xFF8B8FA0, 0, 0, 0).length
                == PlanetPixelTexture.MIN_RESOLUTION * PlanetPixelTexture.MIN_RESOLUTION);
        assertTrue(PlanetPixelTexture.sample(16, 1L, 0xFF8B8FA0, 0, 0, 0).length == 16 * 16);
        // every pixel opaque ARGB
        for (int c : PlanetPixelTexture.sample(16, 1L, 0xFF8B8FA0, 0, 0, 0)) {
            assertTrue(((c >>> 24) & 0xFF) == 0xFF, "texture must be fully opaque");
        }
    }

    @Test
    void moonTextureIsDeterministicAndIndependentFromParent() {
        // moon uses its own seed: same moon seed => identical; a different (parent/other) seed differs
        assertArrayEquals(
                PlanetPixelTexture.sample(12, 1001L, 0xFFE8F2FF, 0, 0.1f, 0.6f),
                PlanetPixelTexture.sample(12, 1001L, 0xFFE8F2FF, 0, 0.1f, 0.6f));
        boolean diff = false;
        for (long s = 2000L; s < 2020L; s++) {
            if (!Arrays.equals(
                    PlanetPixelTexture.sample(12, 1001L, 0xFFE8F2FF, 0, 0.1f, 0.6f),
                    PlanetPixelTexture.sample(12, s, 0xFFE8F2FF, 0, 0.1f, 0.6f))) {
                diff = true;
                break;
            }
        }
        assertTrue(diff, "a moon's texture must come from its own seed, not the parent's");
    }
}
