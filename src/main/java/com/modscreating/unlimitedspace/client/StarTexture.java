package com.modscreating.unlimitedspace.client;

/**
 * Deterministic soft-glow star sprite (R14.7). Pure math, no Minecraft types — directly
 * unit-testable.
 *
 * <p>Given the star's authoritative spectral colour ({@code Star.colorRgb}, derived from
 * {@code StarType.colorRgb}) and its seed, produces a square ARGB sprite with:
 * <ul>
 *   <li>an intense white-hot centre;</li>
 *   <li>a coloured core band (the star's actual spectral colour stays dominant);</li>
 *   <li>a broad soft halo fading to transparent;</li>
 *   <li>small per-pixel intensity variation so the disc never looks like a flat circle.</li>
 * </ul>
 * The sprite is drawn additively, so outside-the-glow texels (alpha ~ 0) contribute nothing.
 */
public final class StarTexture {

    /** Side length of the star sprite. */
    public static final int DEFAULT_RESOLUTION = 64;

    /** Minimum sky-side sprite dimension. */
    public static final int MIN_RESOLUTION = 8;
    /** Maximum sky-side sprite dimension (bounded so a large cache stays cheap). */
    public static final int MAX_RESOLUTION = 96;

    /** Bright white-hot core radius as a fraction of the half-width. */
    private static final float CORE_FRACTION = 0.30f;

    /** Coloured plasma band radius as a fraction of the half-width (extends the core outward). */
    private static final float PLASMA_FRACTION = 0.62f;

    private static final long HASH_PRIME = 0x9E3779B97F4A7C15L;

    private StarTexture() {
    }

    /**
     * @param resolution square sprite side (clamped to {@link #MIN_RESOLUTION}..{@link #MAX_RESOLUTION})
     * @param seed       star seed (system seed + star index) so each star blinks differently
     * @param colorRgb   the star's spectral colour ({@code 0xRRGGBB})
     * @return square ARGB array, top-left first; alpha = glow intensity (for additive drawing)
     */
    public static int[] sample(int resolution, long seed, int colorRgb) {
        return sample(resolution, seed, colorRgb, DEFAULT_PLASMA_LOBES, 1.0f);
    }

    /**
     * R14.9 overload that threads the authoritative {@link com.modscreating.unlimitedspace.core.stars.StarVisualProfile}
     * into the sprite: {@code plasmaLobes} drives the number of angular plasma structures (so the disc
     * reads as a red-dwarf blob, a giant's lobes, a supergiant's broad turbulence or a compact remnant
     * point rather than a single smooth sphere) and {@code glowScale} scales the additive brightness of
     * the halo so hot/giant/remnant stages visibly out-glow a dim dwarf. The default form passes the
     * legacy {@code (5 lobes, glow 1.0)} so existing callers/tests are byte-identical.
     *
     * @param plasmaLobes angular plasma lobe count (clamped to 3..14); 5 = legacy main-sequence
     * @param glowScale   additive glow multiplier (clamped to 0..4); 1.0 = legacy brightness
     */
    public static int[] sample(int resolution, long seed, int colorRgb, float plasmaLobes, float glowScale) {
        int n = Math.max(MIN_RESOLUTION, Math.min(MAX_RESOLUTION, resolution));
        int[] out = new int[n * n];
        float cr = ch(colorRgb, 16);
        float cg = ch(colorRgb, 8);
        float cb = ch(colorRgb, 0);
        float lobes = Math.max(3.0f, Math.min(14.0f, plasmaLobes));
        float glow = Math.max(0.0f, Math.min(4.0f, glowScale));

        float half = (n - 1) * 0.5f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (x - half) / half;
                float v = (y - half) / half;
                float d = (float) Math.sqrt(u * u + v * v);   // 0 centre .. ~1 corner
                float ang = (float) Math.atan2(v, u);

                // Deterministic per-pixel turbulence drives the plasma "tongues" so the disc never
                // looks like a flat circle (R14.8: plasma-like, not a uniform glow).
                float n1 = hash(seed, 1234567L, x, y);
                float n2 = hash(seed, 7654321L, x, y);

                // `lobes` angular plasma lobes, drifting by the turbulence seed.
                float tongue = 0.5f + 0.5f * (float) Math.sin(ang * lobes + n1 * 6.2831855f);
                // Radial turbulence modulation (breaks the band into irregular structures).
                float rad = 0.82f + 0.36f * n2;

                // White-hot core (small, intense), coloured plasma band, broad soft halo.
                float core = (float) Math.pow(Math.max(0.0f, 1.0f - d / CORE_FRACTION), 2.2);
                float band = (float) Math.pow(Math.max(0.0f, 1.0f - d / PLASMA_FRACTION), 1.6)
                        * (0.55f + 0.55f * tongue) * rad;
                float halo = (float) Math.pow(Math.max(0.0f, 1.0f - d), 2.8);

                float intensity = (core * 1.55f + band * 0.85f + halo * 0.42f) * glow;
                float alpha = clamp01(intensity * 1.05f + core * 0.22f);

                // Colour: white-hot centre -> the star's spectral colour through the plasma band,
                // with a slightly deepened rim so the edge reads as coloured light, not white.
                float t = clamp01(d * 1.15f);
                float r = mix(1.0f, cr, t);
                float g = mix(1.0f, cg, t);
                float b = mix(1.0f, cb, t);
                if (d > 0.72f) {
                    float rim = (d - 0.72f) / 0.28f;
                    r = mix(r, cr * 0.90f, rim);
                    g = mix(g, cg * 0.90f, rim);
                    b = mix(b, cb * 0.90f, rim);
                }

                int ri = clamp255(r * intensity);
                int gi = clamp255(g * intensity);
                int bi = clamp255(b * intensity);
                out[y * n + x] = (clamp255(alpha) << 24) | (ri << 16) | (gi << 8) | bi;
            }
        }
        return out;
    }

    /** Default angular plasma lobes for the legacy {@code sample(res, seed, colorRgb)} form. */
    private static final float DEFAULT_PLASMA_LOBES = 5.0f;

    private static float hash(long seed, long salt, int x, int y) {
        long h = seed;
        h = h * HASH_PRIME + salt;
        h = h * HASH_PRIME + x * 2654435761L;
        h = h * HASH_PRIME + y * 40503L;
        h = (h ^ (h >>> 34)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 28)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }

    private static float ch(int argb, int shift) { return ((argb >> shift) & 0xFF) / 255.0f; }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int clamp255(float v) {
        int i = (int) (v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
