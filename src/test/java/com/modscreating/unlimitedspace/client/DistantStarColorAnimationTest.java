package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.stars.StarVisualProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.3-E — coloured & animated distant stars.
 *
 * <p>Verifies the pure pipeline {@code Star -> StarVisualProfile -> StarTexture/animation} used by
 * the distant-star path ({@code SystemStarRenderer.drawOrbitStars}): the visible colour is the
 * saturated per-class spectral colour (never washed to white by a shared tint or additive clipping),
 * different star types read as visibly different shapes, distant stars animate deterministically,
 * and the sprite cache key separates every individual star. No Minecraft types are touched.
 */
class DistantStarColorAnimationTest {

    private static final long WORLD_SEED = 20260823L;

    private static Star ofClass(StarType type, double kelvin) {
        return Star.of(new StarId(StarSystemId.of(1), 0), 42L, type, kelvin, 1.0, 1.0, type.colorRgb());
    }

    /** Average RGB over the coloured plasma-band annulus (d in [0.30..0.62] of the half-width). */
    private static float[] bandAverageOf(int[] tex, int res) {
        float half = (res - 1) * 0.5f;
        float sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int y = 0; y < res; y++) {
            for (int x = 0; x < res; x++) {
                float dx = (x - half) / half, dy = (y - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 0.30f || d > 0.62f) continue;
                int c = tex[y * res + x];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                n++;
            }
        }
        return new float[]{sr / n, sg / n, sb / n};
    }

    @Test
    void redStarPlasmaBandIsRedNotWhite() {
        StarVisualProfile m = StarVisualProfile.from(ofClass(StarType.M, 3100.0));
        int[] tex = StarTexture.sample(StarTexture.DEFAULT_RESOLUTION, 7L, m.plasmaColor(), m.plasmaLobes(), m.glowIntensity());
        float[] avg = bandAverageOf(tex, StarTexture.DEFAULT_RESOLUTION);
        assertTrue(avg[0] > avg[2] + 15f,
                "M star plasma band must be clearly RED: rgb=" + avg[0] + "," + avg[1] + "," + avg[2]);
        assertTrue(avg[0] > avg[1],
                "M star band red channel must dominate green (not yellow/white): " + avg[0] + " vs " + avg[1]);
    }

    @Test
    void blueStarDiffersVisiblyFromRedStar() {
        StarVisualProfile m = StarVisualProfile.from(ofClass(StarType.M, 3100.0));
        StarVisualProfile o = StarVisualProfile.from(ofClass(StarType.O, 40000.0));
        assertNotEquals(m.plasmaColor(), o.plasmaColor(), "M and O must have distinct plasma colours");
        int[] red = StarTexture.sample(64, 7L, m.plasmaColor(), m.plasmaLobes(), m.glowIntensity());
        int[] blue = StarTexture.sample(64, 7L, o.plasmaColor(), o.plasmaLobes(), o.glowIntensity());
        assertFalse(java.util.Arrays.equals(red, blue), "red vs blue star sprites must differ");
        float[] rAvg = bandAverageOf(red, 64);
        float[] bAvg = bandAverageOf(blue, 64);
        // Blue channel dominance flips sign between the two classes.
        assertTrue(rAvg[0] - rAvg[2] > 0, "M sprite band must be red-dominant");
        assertTrue(bAvg[2] - bAvg[0] > 0, "O sprite band must be blue-dominant");
    }
    @Test
    void differentStarTypesProduceDifferentShapes() {
        StarVisualProfile dwarf = StarVisualProfile.from(ofClass(StarType.M, 3100.0));
        StarVisualProfile giant = StarVisualProfile.from(
                Star.of(new StarId(StarSystemId.of(2), 0), 43L, StarType.GIANT, 4500.0, 20.0, 300.0, 0xFFFF8844));
        assertNotEquals(StarStage.RED_DWARF, giant.stage());
        assertNotEquals(dwarf.plasmaLobes(), giant.plasmaLobes(),
                "different stages must map to different plasma-lobe counts (visible shape difference)");
        int[] a = StarTexture.sample(64, 7L, dwarf.plasmaColor(), dwarf.plasmaLobes(), dwarf.glowIntensity());
        int[] b = StarTexture.sample(64, 7L, giant.plasmaColor(), giant.plasmaLobes(), giant.glowIntensity());
        assertFalse(java.util.Arrays.equals(a, b), "different types must not share one sprite shape");
    }

    @Test
    void distantStarsAnimateDeterministically() {
        StarAnimation t0a = StarAnimation.forSeed(WORLD_SEED, "system_0001", 0.0);
        StarAnimation t5a = StarAnimation.forSeed(WORLD_SEED, "system_0001", 5.0);
        // animated != static: the state genuinely changes over time...
        assertNotEquals(t0a.spinDeg(), t5a.spinDeg());
        assertTrue(Math.abs(t0a.breathScale() - t5a.breathScale()) > 1e-4,
                "halo must breathe over time");
        // ...and is deterministic: same seed + same time -> identical state.
        assertEquals(t0a, StarAnimation.forSeed(WORLD_SEED, "system_0001", 0.0));
        assertEquals(t5a.brightness(), StarAnimation.forSeed(WORLD_SEED, "system_0001", 5.0).brightness(), 0.0);
        // different stars animate with different phases.
        assertNotEquals(t0a.phaseDeg(), StarAnimation.forSeed(WORLD_SEED, "system_0002", 0.0).phaseDeg());
        // animation stays subtle and cheap: bounded breathing / brightness / slow spin.
        assertTrue(t0a.breathScale() > 0.9f && t0a.breathScale() < 1.1f);
        assertTrue(t0a.brightness() >= 0.88f && t0a.brightness() <= 1.0f);
    }

    @Test
    void cacheKeySeparatesEveryIndividualStar() {
        String k1 = StarTexture.cacheKey(1L, "system_0001", StarStage.MAIN_SEQUENCE,
                com.modscreating.unlimitedspace.core.stars.SpectralClass.G, 64);
        // Different world seed -> different key.
        assertNotEquals(k1, StarTexture.cacheKey(2L, "system_0001", StarStage.MAIN_SEQUENCE,
                com.modscreating.unlimitedspace.core.stars.SpectralClass.G, 64));
        // Different stable star id -> different key (never system-id-only sharing).
        assertNotEquals(k1, StarTexture.cacheKey(1L, "system_0001_star_01", StarStage.MAIN_SEQUENCE,
                com.modscreating.unlimitedspace.core.stars.SpectralClass.G, 64));
        // Same stage but different spectral class -> different key.
        assertNotEquals(k1, StarTexture.cacheKey(1L, "system_0001", StarStage.MAIN_SEQUENCE,
                com.modscreating.unlimitedspace.core.stars.SpectralClass.M, 64));
        // Different resolution -> different key.
        assertNotEquals(k1, StarTexture.cacheKey(1L, "system_0001", StarStage.MAIN_SEQUENCE,
                com.modscreating.unlimitedspace.core.stars.SpectralClass.G, 96));
    }

    @Test
    void spectralColourSequenceMatchesGuideline() {
        // M reddest ... O bluest: red-minus-blue dominance must strictly decrease M -> O.
        double[] temps = {3100, 4400, 5600, 6800, 8800, 20000, 40000};
        int prev = Integer.MAX_VALUE;
        for (double t : temps) {
            float[] rgb = com.modscreating.unlimitedspace.core.stars.StarColor.temperatureRgbFloats(t);
            int dominance = (int) ((rgb[0] - rgb[2]) * 255);
            assertTrue(dominance < prev, "colour must get bluer as temperature rises at " + t + "K");
            prev = dominance;
        }
        // And the M colour must be saturated red-orange, NOT washed white:
        float[] m = com.modscreating.unlimitedspace.core.stars.StarColor.temperatureRgbFloats(3100);
        assertTrue(m[0] > 0.85f && m[0] - m[2] > 0.35f, "M class must read red/deep orange");
    }

    @Test
    void coreRegionIsColouredNotWhiteBeige() {
        // Regression for the in-game report "stars still render white/beige": the CORE region of the
        // sprite is what visually dominates a distant star. Except for the tiny central pinpoint it
        // must carry the saturated spectral colour, not a white/beige mix.
        StarVisualProfile m = StarVisualProfile.from(ofClass(StarType.M, 3100.0));
        int[] tex = StarTexture.sample(64, 7L, m.plasmaColor(), m.plasmaLobes(), m.glowIntensity());
        float half = 31.5f;
        float sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float dx = (x - half) / half, dy = (y - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 0.30f || d <= 0.14f) continue;   // core ring outside the pinpoint
                int c = tex[y * 64 + x];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                n++;
            }
        }
        sr /= n; sg /= n; sb /= n;
        assertTrue(sr > sb + 60f,
                "M star CORE ring must be clearly RED, not white/beige: rgb=" + sr + "," + sg + "," + sb);
    }
}
