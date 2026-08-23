package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.worldgen.PlasmaProfile;

/**
 * Deterministic multi-frequency procedural plasma texture (R14.9.1). Pure math, no Minecraft types —
 * directly unit-testable.
 *
 * <p>Construction: large-noise + medium granulation + fine turbulence + cellular/convection + hotspot +
 * cool masks + directional turbulence, then {@code value ──► dark ─► base ─► secondary ─► highlight ─►
 * white-hot}. Cells come from a Worley-like distance field with jittered seed points so they are organic
 * (never a grid of squares). Every output is a pure function of {@code (seed, profile)}, deterministic.
 */
public final class PlasmaTexture {

    public static final int DEFAULT_RESOLUTION = 96;
    public static final int MIN_RESOLUTION = 16;
    public static final int MAX_RESOLUTION = 128;

    private static final long HASH_PRIME = 0x9E3779B97F4A7C15L;

    private PlasmaTexture() {
    }

    public static int[] sample(int resolution, long seed, PlasmaProfile profile) {
        int n = Math.max(MIN_RESOLUTION, Math.min(MAX_RESOLUTION, resolution));
        int[] out = new int[n * n];

        int baseA = profile.baseArgb();
        int secA = profile.secondaryArgb();
        int hlA = profile.highlightArgb();
        int darkA = profile.darkArgb();
        float brightness = profile.brightness();
        float contrast = profile.contrast();
        float patternScale = 0.25f + 1.75f * profile.patternScale();
        float cellSize = profile.cellSize();
        float hotspotFreq = profile.hotspotFrequency();
        float flow = (float) Math.toRadians(profile.flowDegrees());
        float cf = (float) Math.cos(flow);
        float sf = (float) Math.sin(flow);

        float half = (n - 1) * 0.5f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (x - half) / half;
                float v = (y - half) / half;
                float ru = (u * cf - v * sf) * patternScale;
                float rv = (u * sf + v * cf) * patternScale;

                float large = noise(seed, 1L, ru + 50.0f, rv + 50.0f);
                float med = noise(seed, 2L, ru * 2.2f, rv * 2.2f);
                float fine = noise(seed, 3L, ru * 4.8f, rv * 4.8f);

                float cell = 1.0f - clamp01(worley(seed + 5000L, u, v, 1.0f + 2.6f * cellSize) * 1.05f);

                float hotspotRaw = noise(seed, 4L, ru * 2.0f + 31.0f, rv * 2.0f - 17.0f);
                float hotspot = smoothstep(0.82f - 0.24f * hotspotFreq, 0.98f, hotspotRaw);
                float cool = smoothstep(0.18f, 0.04f, noise(seed, 5L, ru * 1.6f - 40.0f, rv * 1.6f + 23.0f));

                float value = 0.50f * large + 0.30f * med + 0.15f * fine + 0.05f * hotspot;
                value = value * 0.55f + cell * 0.45f;
                value = (value - 0.5f) * (1.0f + contrast * 1.6f) + 0.5f * brightness + (0.42f * brightness - 0.12f);
                value = clamp01(value);
                value = value * (1.0f - cool) + value * 0.35f * cool;

                out[y * n + x] = ramp(value, darkA, baseA, secA, hlA);
            }
        }
        return out;
    }

    /**
     * R14.9.3-A — seamless plasma texture for the <em>enclosing</em> sky sphere. Unlike {@link #sample}
     * (a flat 2D plate, whose left/right edges do NOT match and would show a vertical seam when wrapped
     * around the dome's longitude), this samples a <b>3D field on the sphere surface</b>: the value is a
     * pure function of the unit direction, so longitude {@code u=0} and {@code u=1} land on the exact same
     * 3D point and produce the identical colour — no longitude seam, by construction.
     *
     * <p>The returned grid is {@code res*res} equirectangular texels: {@code index[v*res + u]} where {@code u}
     * is longitude and {@code v} is latitude ({@code 0=zenith, 1=nadir}). It is generated once per
     * {@code (seed, profile)} and cached by the caller — never per frame.
     */
    public static int[] sampleSphere(int resolution, long seed, PlasmaProfile profile) {
        int n = Math.max(MIN_RESOLUTION, Math.min(MAX_RESOLUTION, resolution));
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            float v = (y + 0.5f) / n;     // keep off the exact poles so the raster is stable
            for (int x = 0; x < n; x++) {
                float u = x / (float) n;  // 0 .. (n-1)/n; wraps back to 0 seamlessly
                out[y * n + x] = sampleSphereAt(seed, profile, u, v);
            }
        }
        return out;
    }

    /**
     * Evaluate the seamless plasma at one point of the dome. {@code u} is longitude ({@code 0..1}, wrapping),
     * {@code v} is latitude ({@code 0=zenith .. 1=nadir}). Deterministic and a pure function of
     * {@code (seed, profile, direction)}; exposed so a test can assert {@code u=0} and {@code u=1} match.
     */
    public static int sampleSphereAt(long seed, PlasmaProfile profile, float u, float v) {
        int baseA = profile.baseArgb();
        int secA = profile.secondaryArgb();
        int hlA = profile.highlightArgb();
        int darkA = profile.darkArgb();
        float brightness = profile.brightness();
        float contrast = profile.contrast();
        float patternScale = 0.25f + 1.75f * profile.patternScale();
        float cellSize = profile.cellSize();
        float hotspotFreq = profile.hotspotFrequency();
        float flow = (float) Math.toRadians(profile.flowDegrees());
        float cf = (float) Math.cos(flow);
        float sf = (float) Math.sin(flow);

        // Unit direction on the sphere (-Y = zenith / up, +Y = nadir / down). The longitude wraps into
        // [0,1) and the full-turn boundary is snapped so u=0 and u=1 land on the exact same direction.
        float phi = v * (float) Math.PI;
        float dy = -(float) Math.cos(phi);
        float sinPhi = (float) Math.sin(phi);
        float uu = u - (float) Math.floor(u);
        float ang = uu * (float) (Math.PI * 2.0);
        if (ang >= (float) (Math.PI * 2.0)) ang = 0.0f;
        float dx = sinPhi * (float) Math.cos(ang);
        float dz = sinPhi * (float) Math.sin(ang);

        // Directional turbulence (flow) applied in the x-y plane, then scaled into noise space.
        float rx = dx * cf - dy * sf;
        float ry = dx * sf + dy * cf;
        float rz = dz;
        float px = rx * patternScale;
        float py = ry * patternScale;
        float pz = rz * patternScale;

        float large = noise3(seed, 1L, px + 50.0f, py + 50.0f, pz + 50.0f);
        float med = noise3(seed, 2L, px * 2.2f, py * 2.2f, pz * 2.2f);
        float fine = noise3(seed, 3L, px * 4.8f, py * 4.8f, pz * 4.8f);

        float cell = 1.0f - clamp01(worley3(seed + 5000L, rx, ry, rz, 1.0f + 2.6f * cellSize) * 1.05f);

        float hotspotRaw = noise3(seed, 4L, px * 2.0f + 31.0f, py * 2.0f - 17.0f, pz * 2.0f - 9.0f);
        float hotspot = smoothstep(0.82f - 0.24f * hotspotFreq, 0.98f, hotspotRaw);
        float cool = smoothstep(0.18f, 0.04f,
                noise3(seed, 5L, px * 1.6f - 40.0f, py * 1.6f + 23.0f, pz * 1.6f + 11.0f));

        float value = 0.50f * large + 0.30f * med + 0.15f * fine + 0.05f * hotspot;
        value = value * 0.55f + cell * 0.45f;
        value = (value - 0.5f) * (1.0f + contrast * 1.6f) + 0.5f * brightness + (0.42f * brightness - 0.12f);
        value = clamp01(value);
        value = value * (1.0f - cool) + value * 0.35f * cool;

        return ramp(value, darkA, baseA, secA, hlA);
    }

    private static float noise(long seed, long salt, float x, float y) {
        int xi = floor(x);
        int yi = floor(y);
        float tx = x - xi;
        float ty = y - yi;
        float a = hash01(seed, salt, xi, yi);
        float b = hash01(seed, salt, xi + 1, yi);
        float c = hash01(seed, salt, xi, yi + 1);
        float d = hash01(seed, salt, xi + 1, yi + 1);
        float u = tx * tx * (3.0f - 2.0f * tx);
        float w = ty * ty * (3.0f - 2.0f * ty);
        return lerp(lerp(a, b, u), lerp(c, d, u), w);
    }

    private static float worley(long seed, float x, float y, float scale) {
        float gx = x * scale;
        float gy = y * scale;
        int ix = floor(gx);
        int iy = floor(gy);
        float best = Float.MAX_VALUE;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int cx = ix + i;
                int cy = iy + j;
                float px = cx + hash01(seed, 11L, cx, cy);
                float py = cy + hash01(seed, 29L, cx, cy);
                float dx = gx - px;
                float dy = gy - py;
                float dsq = dx * dx + dy * dy;
                if (dsq < best) best = dsq;
            }
        }
        return (float) Math.sqrt(best);
    }

    /** 3D trilinear value noise — used by {@link #sampleSphere} so the dome is seamless in longitude. */
    private static float noise3(long seed, long salt, float x, float y, float z) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        float tx = x - xi;
        float ty = y - yi;
        float tz = z - zi;
        float u = tx * tx * (3.0f - 2.0f * tx);
        float w = ty * ty * (3.0f - 2.0f * ty);
        float q = tz * tz * (3.0f - 2.0f * tz);
        float a = hash01(seed, salt, xi, yi, zi);
        float b = hash01(seed, salt, xi + 1, yi, zi);
        float c = hash01(seed, salt, xi, yi + 1, zi);
        float d = hash01(seed, salt, xi + 1, yi + 1, zi);
        float e = hash01(seed, salt, xi, yi, zi + 1);
        float f = hash01(seed, salt, xi + 1, yi, zi + 1);
        float g = hash01(seed, salt, xi, yi + 1, zi + 1);
        float h = hash01(seed, salt, xi + 1, yi + 1, zi + 1);
        float x0 = lerp(lerp(a, b, u), lerp(c, d, u), w);
        float x1 = lerp(lerp(e, f, u), lerp(g, h, u), w);
        return lerp(x0, x1, q);
    }

    /** 3D Worley distance field (27 nearest lattice seeds) — used by {@link #sampleSphere}. */
    private static float worley3(long seed, float x, float y, float z, float scale) {
        float gx = x * scale;
        float gy = y * scale;
        float gz = z * scale;
        int ix = floor(gx);
        int iy = floor(gy);
        int iz = floor(gz);
        float best = Float.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = ix + dx;
                    int cy = iy + dy;
                    int cz = iz + dz;
                    float px = cx + hash01(seed, 11L, cx, cy, cz);
                    float py = cy + hash01(seed, 29L, cx, cy, cz);
                    float pz = cz + hash01(seed, 47L, cx, cy, cz);
                    float ddx = gx - px;
                    float ddy = gy - py;
                    float ddz = gz - pz;
                    float dsq = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (dsq < best) best = dsq;
                }
            }
        }
        return (float) Math.sqrt(best);
    }

    private static int ramp(float t, int dark, int base, int secondary, int highlight) {
        float[] c;
        if (t < 0.25f) {
            c = interp(dark, base, t / 0.25f);
        } else if (t < 0.55f) {
            c = interp(base, secondary, (t - 0.25f) / 0.30f);
        } else if (t < 0.85f) {
            c = interp(secondary, highlight, (t - 0.55f) / 0.30f);
        } else {
            c = interp(highlight, 0xFFFFFFFF, (t - 0.85f) / 0.15f);
        }
        int r = clamp255(c[0]);
        int g = clamp255(c[1]);
        int b = clamp255(c[2]);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float[] interp(int a, int b, float t) {
        float[] ca = floats(a);
        float[] cb = floats(b);
        return new float[]{lerp(ca[0], cb[0], t), lerp(ca[1], cb[1], t), lerp(ca[2], cb[2], t)};
    }

    private static float[] floats(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f};
    }

    private static float hash01(long seed, long salt, int x, int y) {
        long h = seed ^ (salt * HASH_PRIME);
        h ^= x * 0x2545F4914F6CDD1DL;
        h ^= y * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 34)) * 0x377388B5C8E4A2F1L;
        h ^= (h >>> 29);
        h = (h & 0x7FFFFFFFL) ^ (h >>> 33);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }

    /** 3-argument lattice hash for {@link #noise3} / {@link #worley3} (same mixing as the 2D variant). */
    private static float hash01(long seed, long salt, int x, int y, int z) {
        long h = seed ^ (salt * HASH_PRIME);
        h ^= x * 0x2545F4914F6CDD1DL;
        h ^= y * 0x9E3779B97F4A7C15L;
        h ^= z * 0xD1B54A32D192ED03L;
        h = (h ^ (h >>> 34)) * 0x377388B5C8E4A2F1L;
        h ^= (h >>> 29);
        h = (h & 0x7FFFFFFFL) ^ (h >>> 33);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3.0f - 2.0f * t);
    }
    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static int clamp255(float v) {
        int i = (int) (v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
