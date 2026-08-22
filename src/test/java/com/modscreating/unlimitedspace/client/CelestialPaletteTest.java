package com.modscreating.unlimitedspace.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.7 — pure {@link CelestialPalette} value tests.
 *
 * <p>Checks ordering, weight summation, dominant colour, defensive weight clamping, value
 * equality (so identical bodies compare equal and cache collisions are impossible) and the
 * convenience array projections consumed by the sprite generator.
 */
class CelestialPaletteTest {

    @Test
    void emptyPaletteIsEmpty() {
        assertTrue(CelestialPalette.of(List.of()).isEmpty());
        assertEquals(0, CelestialPalette.of(List.of()).size());
        assertEquals(0f, CelestialPalette.of(List.of()).totalWeight(), 1e-6f);
    }

    @Test
    void weightsSumAndDominantAndArrays() {
        CelestialPalette p = CelestialPalette.of(List.of(
                CelestialPalette.Entry.of(0xFF123456, 0.40f),
                CelestialPalette.Entry.of(0xFFABCDEF, 0.60f)));
        assertEquals(2, p.size());
        assertEquals(1.0f, p.totalWeight(), 1e-6f);
        assertEquals(0xFFABCDEF, p.dominantArgb(), "heavier entry is dominant");
        assertArrayEquals(new int[]{0xFF123456, 0xFFABCDEF}, p.argbs());
        assertArrayEquals(new float[]{0.40f, 0.60f}, p.weights(), 1e-6f);
    }

    @Test
    void negativeWeightIsClampedToZero() {
        CelestialPalette p = CelestialPalette.of(List.of(CelestialPalette.Entry.of(0xFF000000, -3f)));
        assertEquals(0f, p.weights()[0], 1e-6f);
        assertEquals(0f, p.totalWeight(), 1e-6f);
    }

    @Test
    void palettesAreEqualByValue() {
        assertEquals(
                CelestialPalette.of(List.of(CelestialPalette.Entry.of(0xFF111111, 1f))),
                CelestialPalette.of(List.of(CelestialPalette.Entry.of(0xFF111111, 1f))));
    }

    @Test
    void dominantArgbUsesLargestWeightEvenWhenUnordered() {
        CelestialPalette p = CelestialPalette.of(List.of(
                CelestialPalette.Entry.of(0xFF0000FF, 0.10f),
                CelestialPalette.Entry.of(0xFF00FF00, 0.80f),
                CelestialPalette.Entry.of(0xFFFF0000, 0.10f)));
        assertEquals(0xFF00FF00, p.dominantArgb());
    }
}
