package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Large-scale, low-frequency global terrain fields (R20 hierarchical-terrain stage).
 *
 * <p>These are the PLANET-GEOGRAPHY signals — continents, oceans, erosion regimes, tectonic
 * belts, ridge systems and valleys. Every field is:
 * <ul>
 *   <li>VERY low frequency (wavelengths of 500–4000 blocks, never 20–50);</li>
 *   <li>continuous and smooth (smoothstep-interpolated value noise + domain warp);</li>
 *   <li>a pure function of {@code (seed, x, z)} — deterministic, allocation-free.</li>
 * </ul>
 *
 * <p>Scale separation contract (wavelength ≈ 1/frequency):
 * <pre>
 * GLOBAL   continentalness  ~3400 blocks
 * EROSION  erosion regime   ~2100 blocks
 * TECTONIC mountain belts   ~1500 blocks
 * MACRO    ridges/valleys    ~1000 blocks (foothills ~350)
 * REGIONAL medium relief      ~150 blocks
 * LOCAL    tiny detail         ~26 blocks (TINY amplitude — see TerrainShaper)
 * </pre>
 */
final class GlobalTerrainFields {

    private GlobalTerrainFields() {}

    /** Smooth value noise in [0,1] at a given (very low) frequency. */
    static double value01(long seed, int x, int z, double frequency, int octave) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    /** Two-octave smooth fbm in [0,1] (both octaves stay in the global scale band). */
    static double fbm2(long seed, int x, int z, double frequency) {
        double coarse = value01(seed, x, z, frequency, 0);
        double fine = value01(seed + 0x9DL, x, z, frequency * 3.0, 1);
        return 0.72 * coarse + 0.28 * fine;
    }

    /**
     * Continentalness in [0,1]: the planet's land/ocean geography. Wavelength ~3400 blocks;
     * slightly domain-warped so coasts are organic, never grid-like.
     */
    static double continentalness(long seed, int x, int z) {
        double wx = x + 520.0 * (value01(seed + 0x41L, x, z, 1.0 / 2600.0, 6) - 0.5);
        double wz = z + 520.0 * (value01(seed + 0x42L, x, z, 1.0 / 2600.0, 7) - 0.5);
        // R20 fix: sample at the FRACTIONAL warped position. Rounding the warp to int made
        // the lookup jump between lattice nodes — a hidden 1-block discontinuity that read
        // as a sudden ~30-block elevation wall across the whole planet.
        double c = 0.78 * value01d(seed, wx, wz, 1.0 / 3400.0, 0)
                + 0.22 * value01d(seed + 0x5L, wx, wz, 1.0 / 1100.0, 1);
        return clamp01(c);
    }

    /** Continuous-coordinate variant of {@link #value01} (same noise field, bilinear lookup). */
    static double value01d(long seed, double wx, double wz, double frequency, int octave) {
        double sx = wx * frequency;
        double sz = wz * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    /** Low-frequency erosion regime in [0,1] (wavelength ~2100 blocks). */
    static double erosionField(long seed, int x, int z) {
        return fbm2(seed + 0x17L, x, z, 1.0 / 2100.0);
    }

    /** Tectonic belt mask in [0,1] (wavelength ~1500 blocks) — where mountains may form. */
    static double tectonicField(long seed, int x, int z) {
        return fbm2(seed + 0x23L, x, z, 1.0 / 1500.0);
    }

    /**
     * Domain-warped ridged field in [0,1]. Ridge crests form LONG, connected, spatially
     * organized lines (mountain chains / valley networks). {@code frequency} is per-block;
     * {@code warp} scales the organic flow of the lines (in blocks).
     */
    static double ridgeField(long seed, int x, int z, double frequency, double warpBlocks) {
        double wx = x + warpBlocks * (value01(seed + 0x31L, x, z, frequency * 0.4, 8) - 0.5) * 2.0;
        double wz = z + warpBlocks * (value01(seed + 0x32L, x, z, frequency * 0.4, 9) - 0.5) * 2.0;
        double sx = wx * frequency, sz = wz * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, 10);
        double v10 = corner(seed, x0 + 1, z0, 10);
        double v01 = corner(seed, x0, z0 + 1, 10);
        double v11 = corner(seed, x0 + 1, z0 + 1, 10);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        double n = lerp(a, b, tz) * 2.0 - 1.0;
        return 1.0 - Math.abs(n);
    }

    /** Smooth basin field in [0,1] (wavelength ~2600 blocks): large continental depressions. */
    static double basinField(long seed, int x, int z) {
        return fbm2(seed + 0x4DL, x, z, 1.0 / 2600.0);
    }

    // ------------------------------------------------------------------ helpers

    private static double corner(long seed, int cx, int cz, int octave) {
        long h = Seeds.derive(seed, "us.terrain.global", cx, cz, octave);
        return Seeds.fraction(h, 0);
    }

    static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
