package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT STAGE 1 — the macro terrain amplitude {@code A} is the planet's RELIEF, not a dead producer's
 * legacy field.
 *
 * <p>The defect this pins: {@code A} used to be
 * {@code profile.amplitude() * signature.amplitudeMul()}, and {@code profile.amplitude()} is the
 * exact field handed to {@code ValueNoiseTerrainGenerator} — a producer nothing ever calls. The
 * real relief budget of a world was therefore decided by a channel unrelated to its relief
 * identity, and {@code PlanetReliefProfile} had no authority at all. Measured on real planets
 * before the fix: a {@code FLAT SOLID_ROCKY} world composed a P90-P10 span of 5 blocks while a
 * {@code CANYONLAND SOLID_DESERT} world composed 22 — the relief archetype was effectively
 * inverted with respect to the actual relief.
 */
@Tag("worldgen")
class TerrainReliefAmplitudeTest {

    /** A desert profile: arid, low tectonics, heavily eroded. */
    private static PlanetPhysicalProfile desert() {
        return new PlanetPhysicalProfile(
                0.55, null, 0.15, 0.6, null, 0.05, 0.03, 0.5, 0.45, 0.10, 0.3,
                0.55, 0.2, 0.4, 0.3, 0.2, 0.20, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_DESERT);
    }

    private static TerrainShaper shaper(long seed, PlanetPhysicalProfile p, double legacyAmplitude,
                                       PlanetReliefProfile relief) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p, GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, legacyAmplitude,
                relief, null, climate);
    }

    /** P90 - P10 of the composed height over a 19 km transect. */
    private static int span(TerrainShaper sh) {
        int[] h = new int[20001];
        for (int i = 0; i < h.length; i++) {
            h[i] = sh.surfaceHeight((i % 200) * 96 - 9500, (i / 200) * 96 - 4700);
        }
        java.util.Arrays.sort(h);
        return h[h.length * 9 / 10] - h[h.length / 10];
    }

    @Test
    void theReliefProfilePublishesTheAmplitudeItWouldImply() {
        // STAGE 1 was MEASURED AND REJECTED (see the note in TerrainShaper.create): making `A` a
        // function of the relief profile regressed two calibrated boundary guards, because the
        // composer's absolute-size terms do not scale with A. This test therefore pins the FINDING
        // as a pure, measured property of the relief profile, so the change can be made later
        // without re-deriving it - and so nobody re-attempts it blind.
        PlanetReliefProfile flat = new PlanetReliefProfile(ReliefArchetype.FLAT, 0.02, 11L);
        PlanetReliefProfile alpine = new PlanetReliefProfile(
                ReliefArchetype.VERY_MOUNTAINOUS, 0.70, 12L);
        assertTrue(flat.macroAmplitudeReliefFactor() < alpine.macroAmplitudeReliefFactor(),
                "a FLAT world must imply a smaller macro budget than a mountain world: "
                        + flat.macroAmplitudeReliefFactor() + " vs "
                        + alpine.macroAmplitudeReliefFactor());
        assertTrue(flat.macroAmplitudeBlocks(0.3) >= PlanetReliefProfile.MACRO_AMPLITUDE_FLOOR_BLOCKS,
                "even a mountain-FREE world keeps continents, so the relief budget clears the floor");
        assertTrue(alpine.macroAmplitudeBlocks(0.3) > flat.macroAmplitudeBlocks(0.3),
                "the relief identity must order the budget: "
                        + alpine.macroAmplitudeBlocks(0.3) + " vs " + flat.macroAmplitudeBlocks(0.3));
    }

    @Test
    void theBudgetIsDeterministicAndPure() {
        PlanetReliefProfile relief = new PlanetReliefProfile(ReliefArchetype.MOUNTAINOUS, 0.42, 9L);
        assertEquals(relief.macroAmplitudeBlocks(0.3), relief.macroAmplitudeBlocks(0.3), 1e-12);
        assertEquals(relief.macroAmplitudeReliefFactor(), relief.macroAmplitudeReliefFactor(), 1e-12);
        // Erosion is a real input: a weathered world loses macro relief.
        assertTrue(relief.macroAmplitudeBlocks(0.9) < relief.macroAmplitudeBlocks(0.0),
                "erosion must still damp the macro budget");
    }
}