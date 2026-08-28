package com.modscreating.unlimitedspace.client.nav;

import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.stars.StarType;

/**
 * R26: visual profile of one star for the SYSTEMS orbital map.
 *
 * <p>Combines the EXISTING canonical data - {@link StarType} (spectral family + canonical
 * colorRgb), {@link StarStage} (evolutionary stage derived by {@code StarStage.from}),
 * and the star's actual temperature / size / luminosity - into a small set of layered
 * rendering parameters. No gameplay or generation data is modified; this is a pure
 * client-side visual projection.
 *
 * <p>Families: M≈RED_DWARF, K≈ORANGE, G≈YELLOW, A≈WHITE, B≈BLUE share the same color
 * family through the canonical {@code colorRgb()}; each keeps its own intensity/shape
 * identity so the class is readable before opening the info panel. GIANT / SUPERGIANT /
 * BLACK_HOLE (and the exotic StarStages) have dedicated rendering behaviour.
 */
public final class StarVisualProfile {

    /** Special rendering behaviours beyond the layered glowing sphere. */
    public enum Style { NORMAL, BLACK_HOLE, NEUTRON_STAR, SUPERNOVA }

    public final int coreColor;    // the star body
    public final int glowColor;    // corona layers
    public final int haloColor;    // outermost halo
    public final float haloFactor; // halo radius = visualRadius * haloFactor (clamped at draw)
    public final int layers;       // 1..3 corona steps
    public final float coreFrac;   // hot-core fraction of the body radius
    public final float glowAlpha;  // base corona alpha (0..1), scaled by luminosity
    public final Style style;

    private StarType type;   // kept only for animation-parameter lookup (visual use)
    private StarStage stage; // kept only for animation-parameter lookup (visual use)

    private StarVisualProfile(int coreColor, int glowColor, int haloColor,
                              float haloFactor, int layers, float coreFrac,
                              float glowAlpha, Style style) {
        this.coreColor = coreColor;
        this.glowColor = glowColor;
        this.haloColor = haloColor;
        this.haloFactor = haloFactor;
        this.layers = layers;
        this.coreFrac = coreFrac;
        this.glowAlpha = glowAlpha;
        this.style = style;
    }

    /** Fallback for the SUN of the Sol catalog (no procedural {@link Star} instance). */
    public static StarVisualProfile sunLike() {
        return new StarVisualProfile(0xFFFFF6DC, 0xFFFFE29A, 0xFFF2D16B,
                1.9f, 2, 0.45f, 0.62f, Style.NORMAL);
    }

    /** Resolve the profile from existing star data (never mutates anything). */
    public static StarVisualProfile of(Star star) {
        if (star == null) return sunLike();
        StarType t = star.type();
        StarStage s = StarStage.from(star);
        // Luminosity -> subtle intensity hint (log-compressed, always clamped).
        float lumN = clamp01((float) (Math.log10(Math.max(0.02, star.luminosity())) / 4.0) + 0.55f);

        StarVisualProfile p = switch (t) {
            case M, RED_DWARF -> // compact, cool, quiet: dark warm body, tight halo
                    new StarVisualProfile(0xFFE0A87A, 0xFFB8562E, 0xFF7A3418,
                            1.5f, 1, 0.30f, 0.42f, Style.NORMAL);
            case K, ORANGE -> // amber: brighter, wider golden corona than M
                    new StarVisualProfile(0xFFFFD9B2, 0xFFE8944A, 0xFFC87430,
                            1.8f, 2, 0.40f, 0.55f, Style.NORMAL);
            case G, YELLOW -> // the Sun-like family: white core, golden corona
                    new StarVisualProfile(0xFFFFF6E4, 0xFFFFE29A, 0xFFF0C070,
                            1.9f, 2, 0.45f, 0.62f, Style.NORMAL);
            case F -> // between G and A: bright warm-white, lighter halo
                    new StarVisualProfile(0xFFFFFFF6, 0xFFFFECC8, 0xFFE8CFA0,
                            1.85f, 2, 0.45f, 0.60f, Style.NORMAL);
            case A, WHITE -> // cold white: sharper, blue-tinged corona
                    new StarVisualProfile(0xFFFFFFFF, 0xFFCAD7FF, 0xFF9FB4E0,
                            1.9f, 2, 0.50f, 0.62f, Style.NORMAL);
            case B, BLUE -> // intense blue-white: stronger, tighter corona
                    new StarVisualProfile(0xFFF4F8FF, 0xFF9DB4FF, 0xFF6E86E8,
                            2.2f, 3, 0.52f, 0.68f, Style.NORMAL);
            case O -> // the extreme: electric blue, big thin halo, high energy
                    new StarVisualProfile(0xFFFFFFFF, 0xFF8FB0FF, 0xFF6A6AE8,
                            2.8f, 3, 0.56f, 0.75f, Style.NORMAL);
            case GIANT -> // broad diffuse envelope, warm core
                    new StarVisualProfile(0xFFFFC088, 0xFFE87038, 0xFFB85828,
                            2.4f, 3, 0.30f, 0.55f, Style.NORMAL);
            case SUPERGIANT -> // massive, unstable-looking, multi-shell
                    new StarVisualProfile(0xFFFFB0A0, 0xFFE85038, 0xFFA82A22,
                            3.0f, 3, 0.26f, 0.60f, Style.NORMAL);
            case BLACK_HOLE -> // rendered by the dedicated accretion-disk path
                    new StarVisualProfile(0xFF03050C, 0xFFFF8844, 0xFF9A6CFF,
                            2.2f, 0, 0.0f, 0.0f, Style.BLACK_HOLE);
        };
                StarVisualProfile q = refine(p, s, lumN);
        q.type = t;
        q.stage = s;
        return q;
    }

    /**
     * Per-family ANIMATION parameters: {mainPulseHz, radiusAmplitude, flareHz}.
     * All slow by design; the bigger/hotter the star, the more energetic (but never fast).
     */
    public float[] animation() {
        // stage wins over family (a giant M dwarf breathes like a giant, not like a dwarf)
        if (stage == StarStage.SUPERNOVA) return new float[] {0.055f, 0.12f, 0.030f};
        if (stage == StarStage.SUPERGIANT) return new float[] {0.050f, 0.11f, 0.025f};
        if (stage == StarStage.GIANT) return new float[] {0.070f, 0.09f, 0.030f};
        if (style == Style.NEUTRON_STAR) return new float[] {0.45f, 0.08f, 0.20f};
        if (type == StarType.BLACK_HOLE) return new float[] {0.16f, 0.06f, 0.05f}; // disk spin Hz
        return switch (type) {
            case M, RED_DWARF -> new float[] {0.12f, 0.05f, 0.050f}; // calm, slow, quiet
            case K, ORANGE -> new float[] {0.15f, 0.06f, 0.070f};
            case G, YELLOW -> new float[] {0.18f, 0.06f, 0.080f};    // stable Sun-like
            case F -> new float[] {0.20f, 0.055f, 0.090f};
            case A, WHITE -> new float[] {0.22f, 0.06f, 0.100f};
            case B, BLUE -> new float[] {0.25f, 0.08f, 0.120f};
            case O -> new float[] {0.28f, 0.10f, 0.140f};            // most energetic, still slow
            case GIANT -> new float[] {0.070f, 0.09f, 0.030f};
            case SUPERGIANT -> new float[] {0.050f, 0.11f, 0.020f};
            default -> new float[] {0.18f, 0.06f, 0.080f};
        };
    }

    // __APPEND__

    /** Stage refinements: StarType answers the family, StarStage the current state. */
    private static StarVisualProfile refine(StarVisualProfile p, StarStage s, float lumN) {
        float alpha = Math.min(0.85f, p.glowAlpha + lumN * 0.15f); // luminosity hint
        switch (s) {
            case WHITE_DWARF: // tiny, extremely hot, compact
                return new StarVisualProfile(0xFFFFFFFF, 0xFFBFD4FF, 0xFF8FB0E8,
                        Math.min(p.haloFactor, 1.4f), 1, 0.75f, alpha, p.style);
            case NEUTRON_STAR:
                return new StarVisualProfile(0xFFFFFFFF, 0xFFBFE8FF, 0xFF7FD8FF,
                        1.6f, 1, 0.85f, alpha, Style.NEUTRON_STAR);
            case BLUE_DWARF:
                return new StarVisualProfile(0xFFEFF6FF, 0xFF9DB4FF, 0xFF7A94E8,
                        Math.min(p.haloFactor, 1.6f), 2, 0.55f, alpha, p.style);
            case GIANT:
                return new StarVisualProfile(p.coreColor, p.glowColor, p.haloColor,
                        Math.max(p.haloFactor, 2.2f), 3, Math.min(p.coreFrac, 0.34f),
                        alpha, p.style);
            case SUPERGIANT:
                return new StarVisualProfile(p.coreColor, p.glowColor, p.haloColor,
                        Math.max(p.haloFactor, 2.8f), 3, Math.min(p.coreFrac, 0.28f),
                        alpha, p.style);
            case SUPERNOVA:
                return new StarVisualProfile(0xFFFFE8D0, 0xFFFF7048, 0xFFC83A28,
                        3.2f, 3, 0.30f, alpha, Style.SUPERNOVA);
            default:
                return p;
        }
    }

    /** Clamped, readable VISUAL star radius; physical radius only shapes the impression. */
    public static float visualRadiusPx(Star star) {
        double size = star == null ? 1.0 : star.size();
        double sizeN = clamp01((Math.log10(Math.max(0.05, size)) + 1.3) / 2.3); // 0.05..100 R-Sol -> 0..1
        float r = 6.0f + (float) sizeN * 5.0f;
        StarStage s = star == null ? StarStage.MAIN_SEQUENCE : StarStage.from(star);
        if (s.isHuge()) r = Math.max(r, 10.5f);
        if (s.isCompact()) r = Math.min(r, 7.0f);
        return clampPx(r);
    }

    private static float clampPx(float v) {
        return v < 5.0f ? 5.0f : Math.min(v, 13.0f);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float clamp01(double v) {
        return clamp01((float) v);
    }
}