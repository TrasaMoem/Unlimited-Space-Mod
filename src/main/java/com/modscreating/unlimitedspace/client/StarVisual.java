package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarType;

/**
 * Deterministic client-side visualisation metadata for one star of a system (R12).
 *
 * <p>Derived purely from the star data + the star-system seed, so the same
 * {@code (systemSeed, star, index)} always produces identical visuals. Apparent
 * size scales with radius + luminosity (distance is implicit per-system for the
 * visual layer); black holes get a compact, distinctly non-sun presentation.
 *
 * <p>No Minecraft types — directly unit-testable.
 */
public record StarVisual(
        Star star,
        int index,
        int colorRgb,
        float azimuthDeg,
        float elevationDeg,
        float apparentRadius,
        boolean blackHole
) {

    /** Visual azimuth base offset: spreads the system's stars apart in the sky. */
    private static final long AZIMUTH_SLOT_BASE = 105000L;
    /** Visual elevation base: keeps stars above the horizon in orbit views. */
    private static final long ELEVATION_SLOT_BASE = 105100L;

    public static StarVisual create(long starSystemSeed, Star star, int index) {
        boolean blackHole = star.type() == StarType.BLACK_HOLE;
        float azimuth = 180.0f + (float) (Seeds.fraction(starSystemSeed, AZIMUTH_SLOT_BASE + index * 2L) - 0.5) * 160.0f;
        float elevation = 12.0f + (float) Seeds.fraction(starSystemSeed, ELEVATION_SLOT_BASE + index * 2L) * 42.0f;
        float apparent = blackHole
                ? 6.0f
                : (float) (7.0 + 8.0 * Math.log10(1.0 + Math.max(star.luminosity(), 0.001))
                        + Math.min(star.size() * 3.0, 18.0));
        return new StarVisual(star, index, star.colorRgb(), azimuth, elevation, apparent, blackHole);
    }

    public float red() {
        return ((colorRgb >> 16) & 0xFF) / 255.0f;
    }

    public float green() {
        return ((colorRgb >> 8) & 0xFF) / 255.0f;
    }

    public float blue() {
        return (colorRgb & 0xFF) / 255.0f;
    }
}