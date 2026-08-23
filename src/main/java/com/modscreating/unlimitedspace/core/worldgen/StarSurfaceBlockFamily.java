package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.stars.SpectralClass;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarColor;
import com.modscreating.unlimitedspace.core.stars.StarStage;

import java.util.ArrayList;
import java.util.List;

/**
 * R14.9.3-C — the fixed 8-member custom star-surface plasma block family (pure domain, no Minecraft types).
 *
 * <p>This is the coherent stellar block set every star-surface world builds from (NOT vanilla glowstone /
 * sea-lantern / magma). As requested, the whole family is a <b>red-hot plasma</b> set: 8 members whose colours
 * are all close to red — deep dark-red / blood / crimson / ruby / bright red / scarlet / vermilion / magenta.
 * Each member has a stable registry identity ({@link #IDENTITY}), a fixed emissive glow level and a fixed
 * identity colour used to drive the registered {@code Block} texture.
 *
 * <p>For a specific star, {@link #forStar(Star)} resolves the same 8 identities with <b>temperature-driven</b>
 * base colours and composition weights (via {@link StarColor}, {@link SpectralClass} and {@link StarStage}):
 * a cool M dwarf is dominated by deep dark-red / blood / crimson, a G main-sequence by bright red, and a hot
 * O/B / giant by scarlet / vermilion / magenta hot-reds. The family stays red for every star, but which red
 * member dominates follows the star, keeping the surface, the surface sky and the orbital disc in the same
 * temperature family.
 *
 * <p>All colour stops are opaque {@code 0xAARRGGBB}. {@link #solid()} is always {@code true}: these are
 * solid, collidable, terrain-compatible blocks — never a lava fluid.
 */
public final class StarSurfaceBlockFamily {

    /** Immutable identity record for one fixed family member. */
    public record Identity(String registryPath, int colorArgb, float emissive) {
    }

    // ----------------------------------------------------------------- fixed 8-member identity (red family)

    /** The 8-member red family in canonical registration order. */
    public static final List<Identity> IDENTITY = List.of(
            new Identity("dark_red_plasma", 0xFF4A0E0E, 0.30f),
            new Identity("blood_plasma", 0xFF7A0C14, 0.45f),
            new Identity("crimson_plasma", 0xFFA01220, 0.55f),
            new Identity("ruby_plasma", 0xFFC81830, 0.62f),
            new Identity("red_plasma", 0xFFE63946, 0.72f),
            new Identity("scarlet_plasma", 0xFFFF3B30, 0.82f),
            new Identity("vermilion_plasma", 0xFFFF5520, 0.78f),
            new Identity("magenta_plasma", 0xFFD6336C, 0.85f)
    );

    /** Block count (8). Must stay within the required 5..10. */
    public static final int COUNT = IDENTITY.size();

    private static final int[][] IDENTITY_RGB = {
            {0x4A, 0x0E, 0x0E}, {0x7A, 0x0C, 0x14}, {0xA0, 0x12, 0x20}, {0xC8, 0x18, 0x30},
            {0xE6, 0x39, 0x46}, {0xFF, 0x3B, 0x30}, {0xFF, 0x55, 0x20}, {0xD6, 0x33, 0x6C}
    };

    // ================================================================ physical / depth-tier model

    /** The 2 LIGHTEST family members — these alone appear on the star surface (the task's requirement). */
    public static final List<String> SURFACE_TIER_PATHS = List.of("magenta_plasma", "scarlet_plasma");

    /**
     * The other 6 family members ordered BRIGHTEST → DARKEST. They fill the sub-surface by depth: the top
     * of the interior is the brightest of these, the bedrock is the darkest (dark_red_plasma last).
     */
    public static final List<String> SUBSURFACE_TIER_PATHS = List.of(
            "vermilion_plasma", "red_plasma", "ruby_plasma",
            "crimson_plasma", "blood_plasma", "dark_red_plasma");

    /**
     * Plasma block hardness for a netherite pickaxe (speed 9) giving the required break times:
     * {@code seconds = hardness * 1.5 / 9}. Surface (magenta/scarlet) hardness 18 → <b>3.0 s</b>;
     * deepest (dark_red) hardness 90 → <b>15.0 s</b>. Intermediate tiers ramp monotonically.
     */
    public static float hardnessFor(String registryPath) {
        return switch (registryPath) {
            case "magenta_plasma", "scarlet_plasma" -> 18.0f;   // 3.0 s
            case "vermilion_plasma" -> 24.0f;                   // 4.0 s
            case "red_plasma" -> 36.0f;                         // 6.0 s
            case "ruby_plasma" -> 48.0f;                        // 8.0 s
            case "crimson_plasma" -> 60.0f;                     // 10.0 s
            case "blood_plasma" -> 72.0f;                       // 12.0 s
            case "dark_red_plasma" -> 90.0f;                    // 15.0 s
            default -> 18.0f;
        };
    }

    /** Netherite pickaxe mining speed (used to derive the break-second assertions). */
    public static final float NETHERITE_PICKAXE_SPEED = 9.0f;

    /** Every plasma block is blast-proof (obsidian-tier+), so it withstands explosions. */
    public static final float EXPLOSION_RESISTANCE = 3600.0f;

    /**
     * Stand-on damage dealt by any plasma block. R14.9.3-C delivered 2x magma; R14.9.3-E follow-up
     * DOUBLED it again per request — 4x Minecraft's magma ({@link #MAGMA_DAMAGE}).
     */
    public static final float PLASMA_STAND_DAMAGE = 4.0f;

    /** Minecraft LavaBlock / magma contact damage baseline (1.0 per damage tick). */
    public static final float MAGMA_DAMAGE = 1.0f;

    /** Netherite-pickaxe break time in seconds for a plasma block surface (given {@link #hardnessFor}). */
    public static float breakSeconds(String registryPath) {
        return hardnessFor(registryPath) * 1.5f / NETHERITE_PICKAXE_SPEED;
    }

    /** True when {@code registryPath} is one of the 2 lightest surface tiers. */
    public static boolean isSurfaceTier(String registryPath) {
        return SURFACE_TIER_PATHS.contains(registryPath);
    }

    private StarSurfaceBlockFamily() {
    }
/**
     * Resolve the family for one specific star: 8 {@link StarSurfaceBlock}s whose colours come from the star's
     * own temperature (via {@link StarColor}) and whose weights come from spectral class + stage. Always
     * deterministic for the same star.
     */
    public static List<StarSurfaceBlock> forStar(Star star) {
        float[] p = StarColor.temperatureRgbFloats(star.temperature());
        SpectralClass spectral = SpectralClass.fromTemperature(star.temperature());
        StarStage stage = StarStage.from(star);
        float t = warmth(spectral);   // 0 = M/cool .. 1 = O/B/hot

        List<StarSurfaceBlock> out = new ArrayList<>(COUNT);
        int i = 0;
        for (Identity id : IDENTITY) {
            float[] base = baseColor(p, i, t);
            float emissive = id.emissive();
            float weight = weight(i, t, stage);
            out.add(new StarSurfaceBlock(
                    id.registryPath(),
                    argb(base[0], base[1], base[2]),
                    argb(base[0] * 0.42f, base[1] * 0.42f, base[2] * 0.42f),
                    argb(clamp01(mix(base[0], 1.0f, 0.5f)), clamp01(mix(base[1], 1.0f, 0.5f)), clamp01(mix(base[2], 1.0f, 0.5f))),
                    argb(1.0f, 1.0f, 1.0f),
                    weight,
                    true,
                    emissive));
            i++;
        }
        return out;
    }

    // ----------------------------------------------------------------- colour derivation (red family)

    /** 0 = cool (M) .. 1 = hot (O/B) for the spectral class. */
    private static float warmth(SpectralClass s) {
        return switch (s) {
            case O, B -> 1.0f;
            case A -> 0.82f;
            case F -> 0.62f;
            case G -> 0.50f;
            case K -> 0.28f;
            case M -> 0.0f;
        };
    }

    /**
     * Per-block red colour: each family member keeps its own red hue (never a fixed swatch per star), but the
     * star's temperature brightens it toward a bright hot-red, so a cool M star's crimson is deep and a hot
     * O/B star's crimson glows near-white. Which red member dominates is driven by {@link #weight}.
     */
    private static float[] baseColor(float[] p, int index, float t) {
        int[] c = IDENTITY_RGB[index];
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float brighten = 0.18f * t;
        return new float[]{
                clamp01(mix(r, 1.0f, brighten + 0.06f * (1.0f - t))),
                clamp01(mix(g, p[1], 0.22f * t)),
                clamp01(mix(b, p[2], 0.22f * t))
        };
    }

    // ----------------------------------------------------------------- weight derivation (red family)

    /** Composition weight for family {@code index} given warmth {@code t} and stage. */
    private static float weight(int index, float t, StarStage stage) {
        float[] coolWeight = {0.20f, 0.16f, 0.16f, 0.12f, 0.12f, 0.06f, 0.05f, 0.04f};
        float[] hotWeight = {0.03f, 0.05f, 0.07f, 0.09f, 0.13f, 0.16f, 0.14f, 0.13f};
        float w = (float) (coolWeight[index] * (1.0 - t) + hotWeight[index] * t);
        if (index == 0) w += 0.10f;             // dark-red: a stable cool base present on every star
        return Math.max(0.005f, stageMod(stage, index, w));
    }

    /** Stage influence: compact cool stars favour deep reds, compact hot / huge stars favour bright hot-reds. */
    private static float stageMod(StarStage stage, int index, float w) {
        boolean deep = index <= 2;              // dark_red, blood, crimson
        boolean hotRed = index >= 5;            // scarlet, vermilion, magenta
        return switch (stage) {
            case RED_DWARF -> deep ? w * 1.30f : hotRed ? w * 0.50f : w;
            case BLUE_DWARF -> hotRed ? w * 1.35f : deep ? w * 0.45f : w;
            case GIANT, SUPERGIANT -> hotRed ? w * 1.45f : deep ? w * 1.05f : w;
            case WHITE_DWARF, NEUTRON_STAR -> hotRed ? w * 1.30f : deep ? w * 0.45f : w;
            case SUPERNOVA -> hotRed ? w * 1.35f : w;
            default -> w; // MAIN_SEQUENCE: smooth mixed red palette
        };
    }

    // ----------------------------------------------------------------- small helpers

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static int argb(float r, float g, float b) {
        int ir = clamp255(r), ig = clamp255(g), ib = clamp255(b);
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    private static int clamp255(float v) {
        int i = (int) Math.round(v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}