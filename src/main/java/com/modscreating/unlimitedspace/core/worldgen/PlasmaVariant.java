package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.stars.SpectralClass;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarColor;
import com.modscreating.unlimitedspace.core.stars.StarStage;

/**
 * The eight deterministic stellar-plasma variant families (R14.9.1) that drive a star surface's visual
 * language. They are the "NOT recoloured Minecraft lava" answer: each carries its own multi-scale pattern
 * recipe (cell size, hotspot frequency, flow, contrast, brightness) and its own temperature appropriate
 * colour stops, and they are chosen per star by {@link StarStage} + {@link SpectralClass}.
 *
 * <p>Two axes are deliberately kept separate so no producer hard-codes a single colour per shape:
 * <ul>
 *   <li><b>shape / scale / energy</b> — chosen by stage (dwarf vs main-sequence vs giant vs remnant);</li>
 *   <li><b>colour</b> — either a baked palette (cool red / warm gold / hot blue-white) or, for giant /
 *       supergiant, the star's own continuous temperature colour.</li>
 * </ul>
 *
 * <p>Pure domain data; no Minecraft types, fully unit-testable.
 */
public enum PlasmaVariant {

    /** G/F-like warm star — boiling golden-white plasma, dense small convection cells. */
    GOLDEN_PHOTOSPHERE(new PlasmaProfile(
            "golden-photosphere", 0xFFFFC94D, 0xFFFFA726, 0xFFFFF7D6, 0xFFB36A00,
            0.50f, 0.85f, 0.55f, 15.0f, 0.45f, 0.35f, false)),

    /** K-type orange star — larger convection cells, strong orange-red contrast, hot knots. */
    ORANGE_CONVECTION(new PlasmaProfile(
            "orange-convection", 0xFFFF8F1F, 0xFFFFB347, 0xFFFFE082, 0xFFB23A00,
            0.60f, 0.80f, 0.65f, 25.0f, 0.60f, 0.30f, false)),

    /** M-type red dwarf — compact dense cells, dark red regions, intense localized flares. */
    RED_DWARF(new PlasmaProfile(
            "red-dwarf-plasma", 0xFFE63A1E, 0xFF9B0E0E, 0xFFFFAF4D, 0xFF4A0808,
            0.40f, 0.85f, 0.80f, 35.0f, 0.30f, 0.45f, false)),

    /** A-type white star — near blinding, fine dense granulation, minimal saturated colour. */
    WHITE_HOT(new PlasmaProfile(
            "white-hot-plasma", 0xFFF4F4F4, 0xFFFFFBE0, 0xFFFFFFFF, 0xFFB8C4D8,
            0.30f, 1.00f, 0.75f, 5.0f, 0.25f, 0.25f, false)),

    /** O/B hot star — incandescent blue-white plasma, electric-blue boundaries, cyan turbulence. */
    BLUE_STELLAR(new PlasmaProfile(
            "blue-stellar-plasma", 0xFFCFE8FF, 0xFF64B5F6, 0xFFFFFFFF, 0xFF123A6B,
            0.40f, 1.00f, 0.80f, 20.0f, 0.35f, 0.30f, false)),

    /** Giant/supergiant — very large convection cells, huge irregular hot regions; colour follows the star. */
    SUPERGIANT_TURBULENCE(new PlasmaProfile(
            "supergiant-turbulence", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000,
            1.00f, 1.00f, 0.70f, 45.0f, 1.00f, 0.40f, true)),

    /** Active / flare-rich star — large bright arcs, hotspots, flare streaks, dark cells. */
    FLARE_RICH(new PlasmaProfile(
            "flare-rich-plasma", 0xFFFF6D00, 0xFFFF3D00, 0xFFFFF59D, 0xFF7A0C00,
            0.70f, 1.00f, 0.90f, 60.0f, 0.55f, 0.70f, false)),

    /** White dwarf / neutron star — extremely compact, high-frequency bright texture, white-blue. */
    EXOTIC_REMNANT(new PlasmaProfile(
            "exotic-remnant-plasma", 0xFFEAF4FF, 0xFFB3D4FF, 0xFFFFFFFF, 0xFF6A89B8,
            0.20f, 1.00f, 0.90f, 10.0f, 0.20f, 0.20f, false));

    private final PlasmaProfile profile;

    PlasmaVariant(PlasmaProfile profile) {
        this.profile = profile;
    }

    public PlasmaProfile profile() {
        return profile;
    }

    public static PlasmaVariant forStar(Star star) {
        SpectralClass spectral = SpectralClass.fromTemperature(star.temperature());
        StarStage stage = StarStage.from(star);
        return switch (stage) {
            case RED_DWARF -> RED_DWARF;
            case BLUE_DWARF -> BLUE_STELLAR;               // compact hot → electric blue
            case MAIN_SEQUENCE -> mainSequence(spectral);
            case GIANT, SUPERGIANT -> SUPERGIANT_TURBULENCE; // follows the star's own colour
            case WHITE_DWARF, NEUTRON_STAR -> EXOTIC_REMNANT;
            case BLACK_HOLE -> EXOTIC_REMNANT;             // void renderer takes over anyway
            case SUPERNOVA -> FLARE_RICH;                  // most energetic family
        };
    }

    public static boolean isVoidFamily(Star star) {
        StarStage stage = StarStage.from(star);
        return stage == StarStage.BLACK_HOLE;
    }

    private static PlasmaVariant mainSequence(SpectralClass spectral) {
        return switch (spectral) {
            case O, B -> BLUE_STELLAR;
            case A -> WHITE_HOT;
            case F, G -> GOLDEN_PHOTOSPHERE;
            case K -> ORANGE_CONVECTION;
            case M -> RED_DWARF;
        };
    }

    /**
     * The effective profile for a surface: the baked one, or (for giant/supergiant) the colour stops
     * replaced by the star's own temperature colour. Always deterministic for the same {@code (variant, star)}.
     */
    public PlasmaProfile resolvedProfile(Star star) {
        if (!profile.temperatureDriven()) {
            return profile;
        }
        int temp = StarColor.temperatureRgb(star.temperature());
        int core = StarColor.coreRgb(star.temperature());
        int halo = StarColor.haloRgb(star.temperature());
        return new PlasmaProfile(profile.name(), temp,
                mix(temp, core, 0.55f), core, halo,
                profile.patternScale(), profile.brightness(), profile.contrast(),
                profile.flowDegrees(), profile.cellSize(), profile.hotspotFrequency(), true);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
