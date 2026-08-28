package com.modscreating.unlimitedspace.client;

/**
 * Deterministic procedural surface pattern for the OBJECT-tab planet body (R28).
 *
 * <p>Given the material/water/atmosphere facts resolved by {@link PlanetVisualResolver}, it
 * generates a square {@code res x res} ARGB texture of ONE planet's surface in the existing
 * pixel-art, sci-fi style. The texture carries no lighting — the caller applies a fixed
 * directional light + rotation at render time, so the surface pattern rotates with the planet
 * while the sun/terminator stays fixed.
 *
 * <p>Fully deterministic from the planet's own seed: the same planet always produces the same
 * pattern; it is generated once and cached, never rebuilt per frame. Each material
 * {@link PlanetVisualResolver.Style} has its own recognisable visual language (gas bands,
 * oceans, lava fissures, cratered rock, ice cracks, ...) instead of a flat single colour.
 */
public final class PlanetSurfacePattern {

    /** Texture grid side (fixed detail for cached sprites; renderer scales to the view). */
    public static final int DEFAULT_RESOLUTION = 96;

    private static final long HASH_PRIME = 6364136223846793005L;

    private PlanetSurfacePattern() {
    }

    /** Generate the plain (unlit) surface disc texture. Alpha = 0 outside the disc. */
    public static int[] generate(PlanetVisualResolver.Look look, int resolution) {
        int n = Math.max(16, Math.min(96, resolution));
        int[] out = new int[n * n];

        // Precomputed crater table (rocky worlds): stable per seed, reused for all cells.
        long seed = look.surface();
        int craterCount = look.style() == PlanetVisualResolver.Style.ROCKY || look.style() == PlanetVisualResolver.Style.DUNE
                ? 2 + (int) (h(seed, 901L, 3, 7) * 3)   // 2..4
                : 0;
        double[] craters = new double[craterCount * 3];
        for (int c = 0; c < craterCount; c++) {
            craters[c * 3] = (h(seed, 902L, c, 0) * 2.0 - 1.0);          // u
            craters[c * 3 + 1] = (h(seed, 903L, c, 11) * 2.0 - 1.0);     // v
            craters[c * 3 + 2] = 0.10 + 0.16 * h(seed, 904L, c, 29);     // radius
        }

        float half = (n - 1) * 0.5f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = half == 0f ? 0f : (x - half) / half;   // -1..1
                float v = half == 0f ? 0f : (y - half) / half;
                if (u * u + v * v > 1.0f) { out[y * n + x] = 0; continue; }
                out[y * n + x] = cellColor(look, seed, n, x, y, u, v, craters);
            }
        }
        return out;
    }
    private static int cellColor(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                                 float u, float v, double[] craters) {
        return switch (look.style()) {
            case GASGIANT -> gasGiant(look, seed, n, x, y, u, v);
            case OCEAN -> ocean(look, seed, n, x, y, u, v);
            case ICY -> icy(look, seed, n, x, y, v);
            case LAVA -> lava(look, seed, n, x, y);
            case DUNE -> dune(look, seed, n, x, y, u, v, craters);
            case METAL -> metal(look, seed, n, x, y, v);
            case TOXIC -> toxic(look, seed, n, x, y, u, v);
            case FOREST -> forest(look, seed, n, x, y);
            default -> rocky(look, seed, n, x, y, u, v, craters);
        };
    }

    // --- gas giants: horizontal atmospheric bands, subtle storm spots, no continents ---
    private static int gasGiant(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                                float u, float v) {
        int base = look.surfaceArgb(), alt = look.secondaryArgb();
        double wobble = fnoise(seed, 4001L, x, y, Math.max(2, n / 20));
        double bandPos = v * 0.5 + 0.5 + wobble * 1.6;              // vertical band position
        double stripe = Math.sin(bandPos * (6.0 + (h(seed, 401L, x & 7, 3) * 4.0)) * Math.PI);
        double edge = Math.pow(Math.max(0.0, Math.abs(stripe) - 0.55), 0.5);
        int c = mix(base, alt, 0.5f + 0.5f * (float) stripe);
        // irregular band noise breaks perfect horizontal lines
        double bandNoise = fnoise(seed, 402L, x, y, Math.max(2, n / 3));
        c = mix(c, alt, 0.25f * (float) bandNoise);
        // localised storm spot
        if (fnoise(seed, 403L, x, y, Math.max(2, n / 6)) > 0.74 && h(seed, 404L, x, 2) < 0.6) {
            c = mix(c, 0xFFFFFF, 0.35f * (float) fnoise(seed, 405L, x, y, 2));
        }
        return c;
    }

    // --- ocean / earth-like: land masses on a water base, faint cloud streaks, polar ice ---
    private static int ocean(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                             float u, float v) {
        double polar = 1.0 - Math.abs(2.0 * v * 0.5 - 1.0) * 2.0;    // hmm: use |v| directly
        polar = 1.0 - Math.abs(v);                                   // 0 equator .. 1 pole
        double region = fnoise(seed, 5001L, x, y, Math.max(2, n / 3));
        double land = region * 0.65 + fnoise(seed, 5002L, x, y, Math.max(2, n / 6)) * 0.35;

        if (look.hasWater() && land < look.waterBlend() * 0.9) {
            // ocean with depth tone
            float depth = 0.66f + 0.34f * (float) fnoise(seed, 5003L, x, y, Math.max(2, n / 8));
            int w = look.waterColorArgb() != 0 ? look.waterColorArgb() : look.surfaceArgb();
            return dark(w, depth);
        }
        // land masses: base tone + darker continents + brighter high ground
        int c = look.surfaceArgb();
        if (land > 0.62) c = mix(c, look.secondaryArgb(), 0.45f);
        if (land > 0.85) c = mix(c, 0xFFFFFF, 0.22f);
        if (land < 0.20) c = dark(c, 0.72f);
        // faint cloud streaks
        if (look.atmosphereStrength() > 0.3f
                && fnoise(seed, 5004L, x, y, Math.max(2, n / 10)) > 0.9) {
            c = mix(c, 0xFFFFFF, 0.4f);
        }
        // polar ice caps
        if (look.iceBlend() > 0.01f && polar > 1.0f - 0.42f * look.iceBlend()) {
            c = mix(c, 0xFFFFFF, (0.25f + 0.5f * (float) polar) * look.iceBlend());
        }
        return c;
    }

    // --- ice worlds: pale sheets, bright polar ice, darker blue cracks ---
    private static int icy(PlanetVisualResolver.Look look, long seed, int n, int x, int y, float v) {
        float polar = 1.0f - (float) Math.abs(v);
        int c = mix(look.surfaceArgb(), 0xFFFFFF, 0.18f + 0.55f * polar);
        double crack = fnoise(seed, 6001L, x, y, Math.max(2, n / 3));
        double fine = fnoise(seed, 6002L, x, y, Math.max(2, n / 10));
        // jagged cracks read as thin darker-blue veins
        if (crack > 0.52 && fine > 0.30) {
            c = mix(c, 0x8FB8D8, 0.55f);                            // light blue crack
        }
        if (crack > 0.86) {
            c = mix(c, 0x5A86A8, 0.6f);                             // deeper blue fissure
        }
        // sheet tonal variation
        double sheet = fnoise(seed, 6003L, x, y, Math.max(2, n / 4));
        c = mix(c, 0xE6F2FF, 0.10f + 0.25f * (float) sheet);
        return c;
    }

    // --- volcanic: dark crust base with bright glowing fissures and embers ---
    private static int lava(PlanetVisualResolver.Look look, long seed, int n, int x, int y) {
        double fire = fnoise(seed, 7001L, x, y, Math.max(2, n / 4));
        double glow = fnoise(seed, 7002L, x, y, Math.max(2, n / 6));
        int c = dark(look.surfaceArgb(), 0.55f);                       // charcoal crust
        // glowing fissures: sharp thin vertical-like veins where both noises peak
        if (fire > 0.42 && glow > 0.18) {
            c = mix(c, 0xFFB42A, 0.35f + 0.45f * (float) glow);
        }
        if (fire > 0.80 && glow > 0.55) {
            c = 0xFFE87228;                                            // hot magma
        }
        // scattered ember spots
        if (h(seed, 7003L, x, 5) < 0.015) {
            c = 0xFFFFB833;
        }
        return c;
    }

    // --- desert / dune: ochre bands + darker regions (surface tint leads the palette) ---
    private static int dune(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                            float u, float v, double[] craters) {
        int base = look.surfaceArgb(), alt = look.secondaryArgb();
        double ridge = fnoise(seed, 8001L, x, y, Math.max(2, n / 12));
        double waves = Math.sin(u * 14.0 + (ridge - 0.5) * 6.0 + v * 2.0);
        double ridgeBright = Math.pow(Math.abs(ridge - 0.5) * 2.0, 1.6);
        int c = mix(base, alt, 0.4f * (float) (0.5 + 0.5 * waves));
        c = mix(c, 0xF2D9A0, 0.18f * (float) ridgeBright);            // bright dune crest
        c = mix(c, dark(base, 0.6f), 0.22f * (float) v);             // darker low bands
        // sparse dark regolith patches
        if (fnoise(seed, 8002L, x, y, Math.max(2, n / 4)) > 0.78) {
            c = mix(c, 0x6E5738, 0.4f);
        }
        // a few impact craters on the sand
        for (int i = 0; i < craters.length; i += 3) {
            double dx = u - craters[i], dy = v - craters[i + 1];
            double rr = craters[i + 2];
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < rr) c = d < rr * 0.55 ? mix(c, 0xFFFFFF, 0.08f)
                    : mix(c, 0x3A2414, 0.55f);
        }
        return c;
    }
    // --- metallic: high-contrast patches + cool reflective sheen ---
    private static int metal(PlanetVisualResolver.Look look, long seed, int n, int x, int y, float v) {
        int base = look.surfaceArgb();
        double patch = fnoise(seed, 9001L, x, y, Math.max(2, n / 3));
        int c = mix(base, dark(base, 0.5f), 0.7f * (float) patch);
        double spec = Math.pow(Math.max(0.0, Math.sin(v * 9.0 + fnoise(seed, 9002L, x, y, 4) * 3.0)), 3.0);
        c = mix(c, 0xD8E4F0, 0.28f * (float) spec);                    // cold reflective band
        if (patch > 0.9) c = mix(c, 0xFFFFFF, 0.25f);               // bright silver facet
        return c;
    }

    // --- toxic / exotic: restrained alien cyan/purple/green cloud swathes ---
    private static int toxic(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                             float u, float v) {
        double a = fnoise(seed, 1001L, x, y, Math.max(2, n / 3));
        double b = fnoise(seed, 1002L, x, y, Math.max(2, n / 4));
        double swirl = fnoise(seed, 1003L, (int) (x + 33 * b), y, Math.max(2, n / 5));
        int base = look.surfaceArgb();
        int c = mix(base, 0x5FA87A, 0.35f * (float) a);       // green vein
        c = mix(c, 0x7A5FB0, 0.28f * (float) swirl);         // purple/pink splotch
        c = mix(c, 0x4FA8C8, 0.30f * (float) b);             // cyan current
        if (a > 0.85) c = mix(c, 0xB8F0C0, 0.5f);            // bright toxic bloom
        return c;
    }

    // --- forest: muted green base, darker forest patches, brighter clearings ---
    private static int forest(PlanetVisualResolver.Look look, long seed, int n, int x, int y) {
        int base = look.surfaceArgb();
        double veg = fnoise(seed, 1101L, x, y, Math.max(2, n / 3));
        double wet = fnoise(seed, 1102L, x, y, 2);
        int c = mix(base, dark(base, 0.55f), 0.5f * (float) veg);
        c = mix(c, 0x9FBF7A, 0.25f * (float) wet);           // wet meadow green
        if (veg > 0.8) c = mix(c, 0x9CCA6A, 0.35f);          // bright canopy
        if (veg < 0.16) c = mix(c, dark(base, 0.4f), 0.7f);  // deep old forest
        return c;
    }

    // --- rocky / stone: continents, crater-like details, irregular darker regions ---
    private static int rocky(PlanetVisualResolver.Look look, long seed, int n, int x, int y,
                             float u, float v, double[] craters) {
        int base = look.surfaceArgb(), alt = look.secondaryArgb();
        double region = fnoise(seed, 1201L, x, y, Math.max(2, n / 3));
        double mid = fnoise(seed, 1202L, x, y, Math.max(2, n / 8));
        double fine = fnoise(seed, 1203L, x, y, Math.max(2, n / 16));
        double m = mid * 0.6 + fine * 0.4;
        int c = base;
        if (region < 0.34) c = dark(base, 0.62f);            // deep shaded basin
        else if (region > 0.68) c = mix(base, alt, 0.45f);    // continent-like patch
        if (m > 0.85) c = mix(c, 0xFFFFFF, 0.16f);            // weathered bright rock
        if (m < 0.16) c = mix(c, dark(base, 0.45f), 0.6f);    // shadowed cleft
        for (int i = 0; i < craters.length; i += 3) {
            double dx = u - craters[i], dy = v - craters[i + 1];
            double rr = craters[i + 2], d = Math.sqrt(dx * dx + dy * dy);
            if (d < rr) c = d < rr * 0.55 ? mix(c, dark(base, 0.8f), 0.5f)
                    : dark(c, 0.55f);                          // crater: dark rim, gritty floor
        }
        return c;
    }
    // ---------------------------------------------------------------- colour + noise helpers
    private static int mix(int a, int b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (clamp8((int) (ar + (br - ar) * t)) << 16)
                | (clamp8((int) (ag + (bg - ag) * t)) << 8)
                | clamp8((int) (ab + (bb - ab) * t));
    }

    /** Scale a colour's luminance by {@code f} in (0..1] towards black. */
    private static int dark(int c, float f) {
        f = f < 0 ? 0 : (f > 1 ? 1 : f);
        int r = (int) (((c >> 16) & 0xFF) * f);
        int g = (int) (((c >> 8) & 0xFF) * f);
        int b = (int) ((c & 0xFF) * f);
        return 0xFF000000 | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    /** Deterministic hash of (seed, salt, gx, gy) -> [0,1]. */
    private static double h(long seed, long salt, int gx, int gy) {
        long v = seed;
        v = v * HASH_PRIME + salt;
        v = v * HASH_PRIME + gx * 2654435761L;
        v = v * HASH_PRIME + gy * 40503L;
        v = (v ^ (v >>> 34)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 28)) * 0x94D049BB133111EBL;
        v = v ^ (v >>> 31);
        return (v & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** Bilinear value noise in px/cell-coordinate space, mapped to [-0.5,0.5]-ish fill. */
    private static double fnoise(long seed, long salt, int x, int y, int cell) {
        if (cell < 1) cell = 1;
        int gx = Math.floorDiv(x, cell), gy = Math.floorDiv(y, cell);
        double fx = (double) (x - gx * cell) / cell;
        double fy = (double) (y - gy * cell) / cell;
        double v00 = h(seed, salt, gx, gy);
        double v10 = h(seed, salt, gx + 1, gy);
        double v01 = h(seed, salt, gx, gy + 1);
        double v11 = h(seed, salt, gx + 1, gy + 1);
        return lerp(lerp(v00, v10, smooth(fx)), lerp(v01, v11, smooth(fy)), smooth(fy));
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}