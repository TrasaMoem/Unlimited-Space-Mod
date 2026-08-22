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

    /** Default grid width/height — 64x64 internal detail for the R14.7 orbital sprites. */
    public static final int DEFAULT_RESOLUTION = 64;

    /** Per-side min/max grid dim. */
    public static final int MIN_RESOLUTION = 4;
    public static final int MAX_RESOLUTION = 96;

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

    /**
     * R14.7: build a <em>block-weighted, spherical</em> body sprite.
     *
     * <p>{@code palette}/{@code weights} are the body's procedural material composition (each entry
     * is a real block's map colour); {@code weights} need not be normalised. The sprite is a circular
     * disc (transparent corners) with spherical shading, multiple material regions drawn in proportion
     * to their weight, oceans and polar ice where the body has them, and value-noise terrain detail.
     *
     * @param resolution square grid side (clamped)
     * @param seed       body seed (planet or moon's own seed)
     * @param palette    the body's weighted block colours
     * @param weights    per-entry weight (parallel to {@code palette})
     * @param waterArgb  ocean colour (ARGB, 0 = none)
     * @param waterBlend 0..1 how much of the surface is ocean
     * @param iceBlend   0..1 how strongly polar/ice-capped the body is
     * @return square ARGB array (alpha used for the disc edge), top-left first
     */
    public static int[] sample(int resolution, long seed, int[] palette, float[] weights,
                                int waterArgb, float waterBlend, float iceBlend) {
        int n = clampSide(resolution);
        float[] cum = cumulativeWeights(weights);
        int[] out = new int[n * n];
        float half = (n - 1) * 0.5f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = half <= 0f ? 0f : (x - half) / half;   // -1..1
                float v = half <= 0f ? 0f : (y - half) / half;
                out[y * n + x] = colorDiscCell(n, x, y, u, v, seed, cum, palette, waterArgb, waterBlend, iceBlend);
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

    // ------------------------------------------------------------------ R14.7 disc helpers
    /**
     * One cell of the weighted, spherical body sprite. Outside the disc (d>1) alpha is 0 so the
     * square billboard reads as a round planet; inside, a sphere normal gives centre-bright,
     * limb-dark shading and the palette is sampled in proportion to its weights.
     */
    private static int colorDiscCell(int n, int x, int y, float u, float v, long seed,
                                     float[] cum, int[] palette, int waterArgb,
                                     float waterBlend, float iceBlend) {
        if (palette == null || palette.length == 0) return 0;
        float d = (float) Math.sqrt(u * u + v * v);
        if (d > 1.0f) return 0;                       // outside the disc -> transparent
        float alpha = smoothstep(1.0f, 0.86f, d);
        if (alpha <= 0f) return 0;

        float nz = (float) Math.sqrt(Math.max(0.0, 1.0 - d * d));   // sphere normal z
        float shade = 0.40f + 0.60f * nz;            // spherical Lambert-ish falloff

        float region = vnoise(seed, 31007L, x, y, Math.max(1, n / 4));
        float mid    = vnoise(seed, 31008L, x, y, Math.max(1, n / 8));
        float fine   = vnoise(seed, 31009L, x, y, Math.max(1, n / 16));
        float polar  = 1.0f - Math.abs(2.0f * v - 1.0f);

        int dominant = palette[Math.min(dominantIndex(cum), palette.length - 1)];
        float dr = ch(dominant, 16), dg = ch(dominant, 8), db = ch(dominant, 0);

        // Oceans: contiguous basins where the body has water (drawn as a distinct material).
        if (waterBlend > 0.02f && region < waterBlend) {
            int wc = waterArgb == 0 ? dominant : waterArgb;
            float depth = 0.72f + 0.28f * mid;
            float wr = ch(wc, 16) * depth * shade;
            float wg = ch(wc, 8)  * depth * shade;
            float wb = ch(wc, 0)  * depth * shade;
            return packA(alpha, wr, wg, wb);
        }

        // Polar ice / snow caps for cold bodies (blend the palette toward pale).
        if (iceBlend > 0.01f && polar > 1.0f - 0.42f * iceBlend) {
            float t = 0.30f * iceBlend;
            float icy = 0.78f + 0.22f * mid;
            float ir = mix(dr, 1.0f, t) * icy * shade;
            float ig = mix(dg, 1.0f, t) * icy * shade;
            float ib = mix(db, 1.0f, t) * (icy + 0.03f) * shade;
            return packA(alpha, ir, ig, ib);
        }

        // Terrain: pick a weighted material colour, then add value-noise luma + spherical shade.
        int idx = cum.length == 0 ? 0 : pickIndex(cum, region);
        int c = palette[Math.min(idx, palette.length - 1)];
        float luma = 0.80f + 0.45f * (mid * 0.6f + fine * 0.4f - 0.5f);
        float r = ch(c, 16) * luma * shade;
        float g = ch(c, 8)  * luma * shade;
        float b = ch(c, 0)  * luma * shade;
        return packA(alpha, r, g, b);
    }

    /** Normalise weights into a cumulative array over [0,1]; empty palette -> trivial {1}. */
    private static float[] cumulativeWeights(float[] weights) {
        if (weights == null || weights.length == 0) return new float[]{1.0f};
        float total = 0f;
        for (float w : weights) if (w > 0f) total += w;
        if (total <= 0f) {
            float[] c = new float[weights.length];
            for (int i = 0; i < c.length; i++) c[i] = (i + 1f) / c.length;
            return c;
        }
        float[] cum = new float[weights.length];
        float acc = 0f;
        for (int i = 0; i < weights.length; i++) {
            acc += Math.max(0f, weights[i]) / total;
            cum[i] = Math.min(1f, acc);
        }
        cum[weights.length - 1] = 1f;
        return cum;
    }

    /** Map a value in [0,1) to a palette index via the cumulative weights. */
    private static int pickIndex(float[] cum, float r) {
        float t = r < 0f ? 0f : (r >= 1f ? 0.999999f : r);
        for (int i = 0; i < cum.length; i++) {
            if (t <= cum[i]) return i;
        }
        return cum.length - 1;
    }

    /** Index of the heaviest-weight palette entry (scan the cumulative gaps). */
    private static int dominantIndex(float[] cum) {
        int best = 0;
        float bestGap = -1f;
        float prev = 0f;
        for (int i = 0; i < cum.length; i++) {
            float gap = cum[i] - prev;
            if (gap > bestGap) {
                bestGap = gap;
                best = i;
            }
            prev = cum[i];
        }
        return best;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static int packA(float a, float r, float g, float b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
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