package com.modscreating.unlimitedspace.core.worldgen.climate;

/**
 * Spatially coherent planetary climate field (R21).
 *
 * <p>Temperature / humidity are NOT {@code random(x,z)}: the field is the archetype's planetary
 * pattern shape (bands / one-sided / dual pole / patchy) + low-frequency warped noise
 * (wavelength ~2600 blocks) + a small regional perturbation (~700 blocks). Neighbouring
 * THOUSANDS of blocks therefore share the same climate, and transitions take hundreds of
 * blocks. Pure, allocation-free function of {@code (seed, x, z)}.
 */
public final class PlanetClimateField {

    private static final double MACRO_WAVELENGTH = 2600.0;
    private static final double REGIONAL_WAVELENGTH = 700.0;
    private static final double WARP_BLOCKS = 420.0;

    private PlanetClimateField() {}

    /** Normalized temperature in [0,1] at a world column. */
    public static double temperatureAt(long seed, ClimateArchetype arch, int x, int z) {
        if (arch == null) return 0.5;
        double pattern = patternValue(seed, arch.pattern(), x, z, arch.baseTemperature());
        double regional = macro01(seed + 0x71L, x, z, REGIONAL_WAVELENGTH) - 0.5;
        return clamp01(pattern + arch.regionalVariance() * 0.55 * regional);
    }

    /** Normalized humidity in [0,1] at a world column. */
    public static double humidityAt(long seed, ClimateArchetype arch, int x, int z) {
        if (arch == null) return 0.5;
        // Moisture uses the same planetary-pattern machinery but as coherent PATCHY macro
        // structure: humidity never follows hard belts.
        double pattern = patternValue(seed + 0x19L, ClimateArchetype.ClimatePattern.PATCHY,
                x, z, arch.baseHumidity());
        double regional = macro01(seed + 0x83L, x, z, REGIONAL_WAVELENGTH) - 0.5;
        return clamp01(pattern + arch.regionalVariance() * 0.45 * regional);
    }

    /** Aridity = dryness in [0,1] (1 = bone dry), from humidity + temperature. */
    public static double aridityAt(long seed, ClimateArchetype arch, int x, int z) {
        double t = temperatureAt(seed, arch, x, z);
        double h = humidityAt(seed, arch, x, z);
        return clamp01(0.65 * (1.0 - h) + 0.35 * t);
    }

    // ------------------------------------------------------------------ internals

    /** The archetype's low-frequency distribution shape in [0,1], centred on {@code base}. */
    private static double patternValue(long seed, ClimateArchetype.ClimatePattern pattern,
                                       int x, int z, double base) {
        long axis = seed ^ 0x5DEECE66DL;
        double ax = Math.cos(axis * 1.0e-9);
        double az = Math.sin(axis * 1.0e-9);
        double radial = clamp01((x * ax + z * az) / 4096.0 + 0.5);   // 0..1 across the planet
        double field;
        switch (pattern) {
            case BANDS -> {
                double warp = (macroNoise(seed + 0x2L, x, z) - 0.5) * WARP_BLOCKS / 2048.0;
                field = base + (radial + warp - 0.5) * 0.9;
            }
            case ONE_SIDED -> field = base + (radial - 0.5) * 1.1;
            case DUAL_POLE -> {
                double dist = Math.abs(radial - 0.5) * 2.0;           // 0 middle .. 1 pole
                field = base + (0.5 - dist) * 0.9;
            }
            case EQUATORIAL -> {
                double dist = Math.abs(radial - 0.5) * 2.0;
                field = base + (dist - 0.5) * 0.6;
            }
            default -> field = base + (macroNoise(seed, x, z) - 0.5) * 1.6;  // PATCHY
        }
        return clamp01(field);
    }

    /** Smooth, strongly domain-warped macro noise in [0,1] (wavelength ~2600 blocks). */
    private static double macroNoise(long seed, int x, int z) {
        double wx = x + WARP_BLOCKS * (macro01(seed + 0x31L, x, z, REGIONAL_WAVELENGTH) - 0.5) * 2.0;
        double wz = z + WARP_BLOCKS * (macro01(seed + 0x37L, x, z, REGIONAL_WAVELENGTH) - 0.5) * 2.0;
        double coarse = macro01(seed, x, z, MACRO_WAVELENGTH);
        double warped = macro01d(seed + 0x5L, wx, wz, 1.0 / MACRO_WAVELENGTH);
        return clamp01(0.72 * coarse + 0.28 * warped);
    }

    /** Bilinear smooth value noise (no allocations). */
    private static double macro01(long seed, int x, int z, double wavelength) {
        return macro01d(seed, x, z, 1.0 / wavelength);
    }

    private static double macro01d(long seed, double wx, double wz, double frequency) {
        double sx = wx * frequency;
        double sz = wz * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smooth(sx - x0);
        double tz = smooth(sz - z0);
        double v00 = corner(seed, x0, z0);
        double v10 = corner(seed, x0 + 1, z0);
        double v01 = corner(seed, x0, z0 + 1);
        double v11 = corner(seed, x0 + 1, z0 + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    private static double corner(long seed, int cx, int cz) {
        long h = com.modscreating.unlimitedspace.core.seed.Seeds
                .derive(seed, "us.climate.field", cx, cz);
        return com.modscreating.unlimitedspace.core.seed.Seeds.fraction(h, 0);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
