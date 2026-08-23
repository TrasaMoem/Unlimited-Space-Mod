package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.worldgen.PlasmaProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlasmaVariant;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.1 — deterministic plasma texture tests. Proves a texture is a pure function of {@code (seed, profile)}
 * (same inputs → identical output), that the resolution is bounded ({@code [MIN..MAX]}), that different plasma
 * variants give genuinely different pixels (NOT a single recoloured texture), and that the output is opaque
 * (a solid material, not an additive glow sprite).
 */
class PlasmaTextureTest {

    @Test
    void sameSeedAndProfileProduceIdenticalTexture() {
        int[] a = PlasmaTexture.sample(64, 12345L, PlasmaVariant.GOLDEN_PHOTOSPHERE.profile());
        int[] b = PlasmaTexture.sample(64, 12345L, PlasmaVariant.GOLDEN_PHOTOSPHERE.profile());
        assertArrayEquals(a, b, "same seed + profile must be byte-identical");
    }

    @Test
    void differentSeedsProduceDifferentTexture() {
        int[] a = PlasmaTexture.sample(32, 1L, PlasmaVariant.RED_DWARF.profile());
        int[] b = PlasmaTexture.sample(32, 999L, PlasmaVariant.RED_DWARF.profile());
        assertFalse(Arrays.equals(a, b), "different seed must change the plasma pattern");
    }

    @Test
    void resolutionIsBoundedBetweenMinAndMax() {
        int[] tiny = PlasmaTexture.sample(4, 1L, PlasmaVariant.BLUE_STELLAR.profile());
        int[] huge = PlasmaTexture.sample(1000, 1L, PlasmaVariant.BLUE_STELLAR.profile());
        assertEquals(PlasmaTexture.MIN_RESOLUTION * PlasmaTexture.MIN_RESOLUTION, tiny.length);
        assertEquals(PlasmaTexture.MAX_RESOLUTION * PlasmaTexture.MAX_RESOLUTION, huge.length);
    }

    @Test
    void distinctVariantsProduceDistinctTextures() {
        int[] gold = PlasmaTexture.sample(40, 7L, PlasmaVariant.GOLDEN_PHOTOSPHERE.profile());
        int[] red = PlasmaTexture.sample(40, 7L, PlasmaVariant.RED_DWARF.profile());
        int[] blue = PlasmaTexture.sample(40, 7L, PlasmaVariant.BLUE_STELLAR.profile());
        assertFalse(Arrays.equals(gold, red), "gold vs red plasma must differ");
        assertFalse(Arrays.equals(gold, blue), "gold vs blue plasma must differ");
        assertFalse(Arrays.equals(red, blue), "red vs blue plasma must differ");
    }

    @Test
    void textureIsOpaqueNotAnAdditiveGlow() {
        int[] t = PlasmaTexture.sample(32, 5L, PlasmaVariant.ORANGE_CONVECTION.profile());
        assertTrue(t.length > 0);
        for (int c : t) {
            assertEquals(0xFF, (c >>> 24), "plasma texels must be fully opaque (solid material)");
        }
    }

    // ------------------------------------------------ R14.9.3-A seamless sky sphere

    @Test
    void sphereTextureIsSeamlessInLongitudeNoVerticalSeam() {
        // The old flat 2D plate does NOT match at its left/right edges, so wrapping it around the dome's
        // longitude showed a vertical seam. The seamless sphere sampler evaluates a 3D field on the sphere
        // surface, so longitude u=0 and u=1 must produce the IDENTICAL colour.
        PlasmaProfile gold = PlasmaVariant.GOLDEN_PHOTOSPHERE.profile();
        for (float v : new float[]{0.1f, 0.35f, 0.5f, 0.75f, 0.9f}) {
            int a = PlasmaTexture.sampleSphereAt(424242L, gold, 0.0f, v);
            int b = PlasmaTexture.sampleSphereAt(424242L, gold, 1.0f, v);
            assertEquals(a, b, "u=0 and u=1 must produce the same colour (no longitude seam) at v=" + v);
        }
    }

    @Test
    void sphereTextureIsDeterministic() {
        int[] a = PlasmaTexture.sampleSphere(48, 999L, PlasmaVariant.BLUE_STELLAR.profile());
        int[] b = PlasmaTexture.sampleSphere(48, 999L, PlasmaVariant.BLUE_STELLAR.profile());
        assertArrayEquals(a, b, "same seed + profile must be byte-identical");
    }

    @Test
    void sphereTextureIsOpaque() {
        int[] t = PlasmaTexture.sampleSphere(32, 7L, PlasmaVariant.RED_DWARF.profile());
        for (int c : t) {
            assertEquals(0xFF, (c >>> 24), "sky-sphere texels must be fully opaque");
        }
    }

    @Test
    void sphereTextureKeepsStarSpecificPalette() {
        // A bug fix must NOT collapse every star to one universal colour. Different variants and different
        // spectral stars must give genuinely different plasma, on the sphere sampler too.
        int[] gold = PlasmaTexture.sampleSphere(32, 7L, PlasmaVariant.GOLDEN_PHOTOSPHERE.profile());
        int[] red = PlasmaTexture.sampleSphere(32, 7L, PlasmaVariant.RED_DWARF.profile());
        int[] blue = PlasmaTexture.sampleSphere(32, 7L, PlasmaVariant.BLUE_STELLAR.profile());
        assertFalse(Arrays.equals(gold, red), "gold vs red sphere plasma must differ");
        assertFalse(Arrays.equals(gold, blue), "gold vs blue sphere plasma must differ");
        assertFalse(Arrays.equals(red, blue), "red vs blue sphere plasma must differ");
    }
}
