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
    public static final float PLASMA_FRACTION = 0.62f;

    /**
     * R14.9.3-E follow-up: stars must be visually OPAQUE on orbits — nothing may be seen through
     * them. Texels within this radius fraction of the half-width are painted as a solid, non-additive
     * disc under the additive glow layer. Mirrors {@link #PLASMA_FRACTION}.
     */
    public static final float OPAQUE_DISC_FRACTION = PLASMA_FRACTION;

    /**
     * R14.9.3-E hotfix 2: saturation boost applied to the spectral palette when painting the sprite,
     * so the colour clearly reads against black space. Presentation-only: the hues themselves still
     * come exclusively from {@code StarColor} (the ONE colour source of truth).
     */
    private static final float SPRITE_SATURATION_BOOST = 1.30f;

    /** Radius (fraction of half-width) of the tiny near-white hot pinpoint at the very centre. */
    private static final float WHITE_PINPOINT_FRACTION = 0.14f;

    /** How far toward white even the pinpoint goes (was effectively 100% before — washed out). */
    private static final float PINPOINT_WHITENESS = 0.65f;

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
        float cr = saturate(ch(colorRgb, 16));
        float cg = saturate(ch(colorRgb, 8));
        float cb = saturate(ch(colorRgb, 0));
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

                float intensity = (core * 1.05f + band * 1.15f + halo * 0.40f) * glow;
                float alpha = clamp01(intensity * 1.05f + core * 0.22f);

                // Colour: R14.9.3-E hotfix 2 — the spectral colour is dominant across essentially the
                // WHOLE disc. Only a tiny pinpoint (14% of half-width) is pushed toward white (and
                // even that only 65%), so the star reads as a COLORED body with a hot sparkle at its
                // heart — never as a white/beige ball with a colored rim.
                float t = clamp01(d / WHITE_PINPOINT_FRACTION);
                float r = mix(mix(1.0f, cr, PINPOINT_WHITENESS), cr, t);
                float g = mix(mix(1.0f, cg, PINPOINT_WHITENESS), cg, t);
                float b = mix(mix(1.0f, cb, PINPOINT_WHITENESS), cb, t);
                if (d > PLASMA_FRACTION) {
                    float rim = clamp01((d - PLASMA_FRACTION) / (1.0f - PLASMA_FRACTION));
                    r = mix(r, cr * 0.72f, rim);
                    g = mix(g, cg * 0.72f, rim);
                    b = mix(b, cb * 0.72f, rim);
                }

                int ri = clamp255(r);
                int gi = clamp255(g);
                int bi = clamp255(b);
                out[y * n + x] = (clamp255(alpha) << 24) | (ri << 16) | (gi << 8) | bi;
            }
        }
        return out;
    }

    /** Default angular plasma lobes for the legacy {@code sample(res, seed, colorRgb)} form. */
    private static final float DEFAULT_PLASMA_LOBES = 5.0f;

    /**
     * R14.9.3-E: canonical cache key for a distant-star sprite. Sufficient and complete — two stars
     * can NEVER share one texture because only a system id was used: world seed, the star's UNIQUE
     * stable id code, its stage AND spectral class (two same-stage stars of different temperature
     * classes get different sprites) plus the resolution are all part of the key.
     */
    public static String cacheKey(long worldSeed, String starStableId,
                                  Enum<?> stage, Enum<?> spectralClass, int resolution) {
        return worldSeed + "|star|" + starStableId + "|" + stage.name()
                + "|" + spectralClass.name() + "|" + resolution;
    }


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

    /** R14.9.3-E: push a channel away from luminance to make the spectral hue pop on black space. */
    private static float saturate(float channel) {
        return clamp01((channel - 0.5f) * SPRITE_SATURATION_BOOST + 0.5f);
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int clamp255(float v) {
        int i = (int) (v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
