package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * The ONE authoritative client-side visual mapping from {@link Star} to a star's on-screen profile
 * (R14.9). It collapses the star's <em>temperature</em> (via {@link SpectralClass} and
 * {@link StarColor}) and its <em>stage/type</em> (via {@link StarStage}) into a single table:
 * core/plasma/halo colour, screen-size scale, plasma shape family, glow intensity and a
 * deterministic texture seed.
 *
 * <p>Two independent axes are kept separate so no producer hard-codes a single colour per shape:
 * <ul>
 *   <li><b>temperature → {@code spectralClass} → colour</b> (continuous, boundary-blending);</li>
 *   <li><b>stage/type → shape + scale + intensity + effect</b>.</li>
 * </ul>
 *
 * <p>Pure data; no Minecraft types, fully unit-testable.
 */
public record StarVisualProfile(
        SpectralClass spectralClass,
        StarStage stage,
        int coreColor,
        int plasmaColor,
        int haloColor,
        float baseRadiusScale,
        ShapeFamily shape,
        float glowIntensity,
        long textureSeed,
        String visualStyle
) {

    /** Shape family of the plainer plasma rendering. Drives how the star is drawn, not just tinted. */
    public enum ShapeFamily {
        SMOOTH_SPHERE,
        COMPACT_PLASMA,
        IRREGULAR_PLASMA,
        TURBULENT_LOBES,
        BROAD_TURBULENT,
        INTENSE_CORE,
        CONCENTRATED_CORE,
        ACCRETION_DISC,
        EXPANDING_SHELL
    }

    public static StarVisualProfile from(Star star, int index) {
        SpectralClass spectral = SpectralClass.fromTemperature(star.temperature());
        StarStage stage = StarStage.from(star);
        double temp = star.temperature();

        int core = StarColor.coreRgb(temp);
        int plasma = StarColor.temperatureRgb(temp);
        int halo = StarColor.haloRgb(temp);

        ShapeFamily shape = shapeFor(stage);
        float scale = scaleFor(stage, spectral);
        float glow = glowFor(stage, spectral);
        long texSeed = Seeds.derive(star.seed(), "us.client.star.profile", index);

        return new StarVisualProfile(spectral, stage, core, plasma, halo, scale, shape, glow,
                texSeed, styleLabel(stage));
    }

    public static StarVisualProfile from(Star star) {
        return from(star, 0);
    }

    // ------------------------------------------------------------------ per-stage visual table
    private static ShapeFamily shapeFor(StarStage stage) {
        return switch (stage) {
            case MAIN_SEQUENCE -> ShapeFamily.SMOOTH_SPHERE;
            case RED_DWARF -> ShapeFamily.COMPACT_PLASMA;
            case BLUE_DWARF -> ShapeFamily.IRREGULAR_PLASMA;
            case GIANT -> ShapeFamily.TURBULENT_LOBES;
            case SUPERGIANT -> ShapeFamily.BROAD_TURBULENT;
            case WHITE_DWARF -> ShapeFamily.INTENSE_CORE;
            case NEUTRON_STAR -> ShapeFamily.CONCENTRATED_CORE;
            case BLACK_HOLE -> ShapeFamily.ACCRETION_DISC;
            case SUPERNOVA -> ShapeFamily.EXPANDING_SHELL;
        };
    }

    /** Screen-space size basis. Stage dominates; temperature keeps colour independent of size. */
    private static float scaleFor(StarStage stage, SpectralClass spectral) {
        return switch (stage) {
            case NEUTRON_STAR -> 0.35f;
            case WHITE_DWARF -> 0.55f;
            case RED_DWARF -> 0.75f;
            case BLUE_DWARF -> 0.85f;
            case MAIN_SEQUENCE -> 1.0f;
            case GIANT -> 2.2f;
            case SUPERGIANT -> 3.5f;
            case BLACK_HOLE -> 1.4f;    // medium central object + large accretion glow
            case SUPERNOVA -> 4.2f;
        };
    }

    /** Additive glow brightness. Compact/hot remnants and shells are brighter; black holes are dark. */
    private static float glowFor(StarStage stage, SpectralClass spectral) {
        float base = switch (stage) {
            case NEUTRON_STAR -> 2.5f;
            case WHITE_DWARF -> 2.0f;
            case BLUE_DWARF -> 1.3f;
            case RED_DWARF -> 0.75f;
            case MAIN_SEQUENCE -> 1.0f;
            case GIANT -> 1.6f;
            case SUPERGIANT -> 2.2f;
            case BLACK_HOLE -> 0.18f;
            case SUPERNOVA -> 2.8f;
        };
        // Hotter classes read slightly brighter within a stage.
        float hotBoost = spectral.isHotterThan(SpectralClass.G) ? 0.18f : 0.0f;
        return base + hotBoost;
    }

    private static String styleLabel(StarStage stage) {
        return switch (stage) {
            case RED_DWARF -> "red-dwarf";
            case BLUE_DWARF -> "blue-dwarf";
            case MAIN_SEQUENCE -> "main-sequence";
            case GIANT -> "giant";
            case SUPERGIANT -> "supergiant";
            case WHITE_DWARF -> "white-dwarf";
            case NEUTRON_STAR -> "neutron-star";
            case BLACK_HOLE -> "black-hole";
            case SUPERNOVA -> "supernova";
        };
    }

    /** True for black holes — the renderer must draw a dark centre + accretion ring, not a sun. */
    public boolean isBlackHole() {
        return stage == StarStage.BLACK_HOLE;
    }

    /**
     * Number of angular plasma lobes for the star sprite, by shape family. Centralised here so the
     * renderer never owns a parallel if/else: each stage reads as a different plasma structure rather
     * than a single smooth sphere tinted by colour.
     */
    public float plasmaLobes() {
        return switch (shape) {
            case SMOOTH_SPHERE -> 5.0f;        // calm, even main-sequence plasma
            case COMPACT_PLASMA -> 6.0f;       // dense red-dwarf plasma
            case IRREGULAR_PLASMA -> 7.0f;     // energetic blue dwarf
            case TURBULENT_LOBES -> 7.0f;      // broad giant lobes
            case BROAD_TURBULENT -> 9.0f;      // wide supergiant turbulence
            case INTENSE_CORE -> 3.0f;         // tight white-dwarf core
            case CONCENTRATED_CORE -> 3.0f;    // tight neutron-star point
            case ACCRETION_DISC -> 8.0f;       // not used by the sprite (black hole draws a ring)
            case EXPANDING_SHELL -> 11.0f;     // ragged supernova shell
        };
    }
}
