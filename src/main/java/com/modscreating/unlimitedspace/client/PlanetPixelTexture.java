package com.modscreating.unlimitedspace.client;

/**
 * Deterministic low-resolution pixel "map" of a celestial body (R12.7, Bug #1). Pure domain, no
 * Minecraft types — directly unit-testable.
 *
 * <p>Given the body's own seed + the single lamp colours already derived from its generated
 * material profile ({@link com.modscreating.unlimitedspace.core.worldgen.PlanetSurfaceColor} or
 * {@link com.modscreating.unlimitedspace.core.worldgen.MoonGenerationProfile#surfaceColorArgb}), it
 * builds a square {@code NxN} ARGB texture with multiple material-like regions:
 * <ul>
 *   <li>base terrain (the body's surface material colour);</li>
 *   <li>sub-surface / deep / bright-accent tonal zones (darken/lighten of the same material colour);</li>
 *   <li>oceans where the body has water (its water colour);</li>
 *   <li>polar ice / snow caps where the body is cold;</li>
 *   <li>micro speckles for extra local detail.</li>
 * </ul>
 * Nothing here invents a foreign palette — every region is derived from the body's own generated
 * material colour(s) plus a fixed terminator, so the result resembles the actual world blocks of
 * that surface. Rendering stays nearest-neighbour (each element = one texture cell).
 */
public final class PlanetPixelTexture {

    /** Default grid width/height — a square low-res texture with plenty of internal detail. */
    public static final int DEFAULT_RESOLUTION = 16;

    /** Per-side min/max grid dim. */
    public static final int MIN_RESOLUTION = 4;
    public static final int MAX_RESOLUTION = 48;

    private static final long HASH_PRIME = 6364136223846793005L;

    private PlanetPixelTexture() {
    }

    /**
     * @param resolution  square grid side (clamped to {@link #MIN_RESOLUTION}..{@link #MAX_RESOLUTION})
     * @param seed        body seed (planet seed or the moon's own seed — never the parent's)
     * @param surfaceArgb body's material surface colour (ARGB)
     * @param waterArgb   water colour (ARGB, 0 = none)
     * @param waterBlend  0..1 how much of the surface is ocean
     * @param iceBlend    0..1 how strongly polar/ice-capped the body is
     * @return packed ARGB array of {@code res*res} pixels, top-left first
     */
    public static int[] sample(int resolution, long seed, int surfaceArgb, int waterArgb,
                                float waterBlend, float iceBlend) {
        int n = clampSide(resolution);
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                out[y * n + x] = colorCell(n, x, y, seed, surfaceArgb, waterArgb, waterBlend, iceBlend);
            }
        }
        return out;
    }

    /** Clamp the grid side into the supported range. */
    public static int clampSide(int resolution) {
        return Math.max(MIN_RESOLUTION, Math.min(MAX_RESOLUTION, resolution));
    }

    // ------------------------------------------------------------------ per-cell colour
    private static int colorCell(int n, int x, int y, long seed,
                                 int surfaceArgb, int waterArgb, float waterBlend, float iceBlend) {
        float u = n <= 1 ? 0.5f : x / (float) (n - 1);
        float v = n <= 1 ? 0.5f : y / (float) (n - 1);

        float sr = ch(surfaceArgb, 16), sg = ch(surfaceArgb, 8), sb = ch(surfaceArgb, 0);
        float wr = waterArgb == 0 ? sr : ch(waterArgb, 16);
        float wg = waterArgb == 0 ? sg : ch(waterArgb, 8);
        float wb = waterArgb == 0 ? sb : ch(waterArgb, 0);

        // deterministic multi-scale value noise (coarse region + mid detail + fine speckle)
        float region = vnoise(seed, 31007L, x, y, Math.max(1, n / 4));
        float mid    = vnoise(seed, 31008L, x, y, Math.max(1, n / 8));
        float fine   = vnoise(seed, 31009L, x, y, Math.max(1, n / 16));
        float polar  = 1.0f - Math.abs(2.0f * v - 1.0f);   // 0 equator .. 1 pole

        // 1) Ocean: contiguous basins where the body has water.
        if (waterBlend > 0.02f && region < waterBlend * 0.9f) {
            float depth = 0.72f + 0.28f * mid;
            return pack(wr * depth, wg * depth, wb * depth);
        }

        // 2) Polar ice / snow caps for cold bodies.
        if (iceBlend > 0.01f && polar > 1.0f - 0.40f * iceBlend) {
            float t = 0.25f * iceBlend;
            float icy = 0.72f + 0.28f * mid;
            return pack(mix(sr, 1.0f, t) * icy, mix(sg, 1.0f, t) * icy, mix(sb, 1.0f, t) * (icy + 0.03f));
        }

        // 3) Terrain tonal zones based on the body's material colour.
        float m = mid * 0.6f + fine * 0.4f;
        float luma;
        if (m < 0.20f)      luma = 0.52f;   // deep / shadow
        else if (m < 0.40f) luma = 0.76f;   // sub-surface
        else if (m > 0.84f) luma = 1.08f;   // bright accent
        else                luma = 0.93f;   // base terrain

        // Fixed terminator bias (longitudinal light falloff) — deterministic day/night feel.
        float light = 0.78f + 0.22f * (0.5f + 0.5f * (float) Math.cos((u - 0.5f) * Math.PI));
        float L = luma * light;
        return pack(sr * L, sg * L, sb * L);
    }

    // ------------------------------------------------------------------ small helpers
    private static float vnoise(long seed, long salt, int x, int y, int cell) {
        int gx = Math.floorDiv(x, cell);
        int gy = Math.floorDiv(y, cell);
        float fx = (float) (x - gx * cell) / cell;
        float fy = (float) (y - gy * cell) / cell;
        float v00 = hash(seed, salt, gx, gy);
        float v10 = hash(seed, salt, gx + 1, gy);
        float v01 = hash(seed, salt, gx, gy + 1);
        float v11 = hash(seed, salt, gx + 1, gy + 1);
        return lerp(lerp(v00, v10, smooth(fx)), lerp(v01, v11, smooth(fy)), smooth(fy));
    }

    private static float hash(long seed, long salt, int gx, int gy) {
        long h = seed;
        h = h * HASH_PRIME + salt;
        h = h * HASH_PRIME + gx * 2654435761L;
        h = h * HASH_PRIME + gy * 40503L;
        h = (h ^ (h >>> 34)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 28)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float t) { return t * t * (3.0f - 2.0f * t); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float ch(int argb, int shift) { return ((argb >> shift) & 0xFF) / 255.0f; }

    private static int pack(float r, float g, float b) {
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}