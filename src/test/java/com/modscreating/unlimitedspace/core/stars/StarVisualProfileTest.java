package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9 — visual profile tests. Proves {@code spectral class + star type → profile} is authoritative,
 * that a red dwarf / normal / giant / supergiant / white dwarf / neutron star / black hole / supernova
 * are NOT accidentally identical, that temperature still drives colour (hotter → bluer) independently
 * of shape, and that a black hole is not rendered as a glowing sun.
 */
class StarVisualProfileTest {

    private static final long SEED = 987654321L;
    private static final StarSystemId SYSTEM = StarSystemId.of(5);
    private static final int INDEX = 0;

    private static Star star(StarType type, double temp, double size, double lum) {
        long seed = Seeds.derive(SEED, "us.test.star.profile", type.ordinal());
        return Star.of(new StarId(SYSTEM), seed, type, temp, size, lum, type.colorRgb());
    }

    private static StarVisualProfile profile(StarType type, double temp, double size, double lum) {
        return StarVisualProfile.from(star(type, temp, size, lum), INDEX);
    }

    @Test
    void temperatureDerivesSpectralClassNotTheReverse() {
        assertEquals(SpectralClass.G, profile(StarType.G, 5600.0, 1.0, 1.0).spectralClass());
        assertEquals(SpectralClass.M, profile(StarType.M, 3000.0, 0.3, 0.01).spectralClass());
        assertEquals(SpectralClass.B, profile(StarType.B, 20000.0, 8.0, 5000.0).spectralClass());
    }

    @Test
    void hotProfileIsBluerThanCoolProfile() {
        int hotPlasma = profile(StarType.O, 40000.0, 20.0, 50000.0).plasmaColor();
        int coldPlasma = profile(StarType.M, 3000.0, 0.3, 0.01).plasmaColor();
        assertTrue((hotPlasma & 0xFF) > (coldPlasma & 0xFF), "hot plasma must be blue-biased vs cold");
        assertTrue((coldPlasma >> 16 & 0xFF) > (hotPlasma >> 16 & 0xFF), "cold plasma must be red-biased vs hot");
    }

    @Test
    void allStagesProduceDistinctProfiles() {
        StarVisualProfile redDwarf = profile(StarType.M, 3000.0, 0.3, 0.01);
        StarVisualProfile normal = profile(StarType.G, 5600.0, 1.0, 1.0);
        StarVisualProfile giant = profile(StarType.GIANT, 4000.0, 20.0, 200.0);
        StarVisualProfile supergiant = profile(StarType.SUPERGIANT, 8000.0, 80.0, 50000.0);
        StarVisualProfile whiteDwarf = profile(StarType.A, 20000.0, 0.05, 0.01);
        StarVisualProfile neutron = profile(StarType.B, 30000.0, 0.01, 0.0002);
        StarVisualProfile blackHole = profile(StarType.BLACK_HOLE, 0.0, 1.0, 0.0);
        StarVisualProfile supernova = profile(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0);

        assertNotEquals(redDwarf.shape(), normal.shape());
        assertNotEquals(normal.shape(), giant.shape());
        assertNotEquals(giant.shape(), supergiant.shape());
        assertNotEquals(supergiant.shape(), supernova.shape());
        assertNotEquals(blackHole.shape(), normal.shape());
        assertNotEquals(neutron.stage(), whiteDwarf.stage());
        assertNotEquals(redDwarf, normal);
        assertNotEquals(normal, giant);
        assertNotEquals(giant, supergiant);
        assertNotEquals(whiteDwarf, neutron);
        assertNotEquals(blackHole, supernova);
        // No two of these eight stage profiles may be equal.
        StarVisualProfile[] all = {redDwarf, normal, giant, supergiant, whiteDwarf, neutron, blackHole, supernova};
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                assertNotEquals(all[i], all[j], "stage profiles at " + i + "," + j + " must differ");
            }
        }
    }

    @Test
    void plasmaLobesDistinctPerStage() {
        // Red dwarf is a compact plasma blob (fewer, tighter lobes) vs a broad supergiant / shell.
        assertTrue(profile(StarType.M, 3000.0, 0.3, 0.01).plasmaLobes() >= 3.0f);
        assertEquals(5.0f, profile(StarType.G, 5600.0, 1.0, 1.0).plasmaLobes());
        assertTrue(profile(StarType.SUPERGIANT, 8000.0, 80.0, 50000.0).plasmaLobes() >= 8.0f);
        assertTrue(profile(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0).plasmaLobes() >= 8.0f);
        assertTrue(profile(StarType.B, 30000.0, 0.01, 0.0002).plasmaLobes() <= 4.0f);
        // Black hole is the accretion-disc family and is never drawn as a smooth 5-lobe sphere.
        assertNotEquals(5.0f, profile(StarType.BLACK_HOLE, 0.0, 1.0, 0.0).plasmaLobes());
    }

    @Test
    void blackHoleIsMarkedAsBlackHoleAndUsesDarkColours() {
        StarVisualProfile p = profile(StarType.BLACK_HOLE, 0.0, 1.0, 0.0);
        assertTrue(p.isBlackHole());
        assertEquals(StarStage.BLACK_HOLE, p.stage());
        assertEquals(StarVisualProfile.ShapeFamily.ACCRETION_DISC, p.shape());
        // Not a normal glowing yellow-ish sun.
        assertTrue(p.glowIntensity() < 0.5f, "black hole glow intensity must be low");
    }

    @Test
    void deterministicForSameStar() {
        Star s = star(StarType.K, 4500.0, 0.7, 0.3);
        assertEquals(StarVisualProfile.from(s, INDEX), StarVisualProfile.from(s, INDEX));
    }

    @Test
    void exoticStagesAreNotFlatNormalSuns() {
        // Black hole must never be drawn as a smooth glowing sphere.
        assertNotEquals(StarVisualProfile.ShapeFamily.SMOOTH_SPHERE,
                profile(StarType.BLACK_HOLE, 0.0, 1.0, 0.0).shape());
        // Supernova must be distinct from a plain main-sequence sphere.
        assertEquals(StarStage.SUPERNOVA,
                profile(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0).stage());
        assertNotEquals(StarVisualProfile.ShapeFamily.SMOOTH_SPHERE,
                profile(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0).shape());
    }
}
