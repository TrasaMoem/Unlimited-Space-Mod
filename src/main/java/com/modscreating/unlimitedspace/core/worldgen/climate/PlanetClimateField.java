package com.modscreating.unlimitedspace.core.worldgen.climate;

import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;

/**
 * Spatially coherent planetary climate field (R21, bounded by R23/T-2).
 *
 * <p>Temperature / humidity are NOT {@code random(x,z)}: the field is the archetype's planetary
 * pattern shape (bands / one-sided / dual pole / patchy) + low-frequency warped noise
 * (wavelength ~2600 blocks) + a small regional perturbation (~700 blocks). Neighbouring
 * THOUSANDS of blocks therefore share the same climate, and transitions take hundreds of
 * blocks. Pure, allocation-free function of {@code (seed, x, z)}.
 *
 * <p><b>R23 (T-2):</b> the pattern is evaluated as a NORMALIZED shape in {@code [-1,1]} and
 * scaled by a band-dependent half-width, so the local climate is a bounded variation AROUND
 * the planet's canonical mean temperature instead of an independent field: a 41 K world can no
 * longer contain a fake temperate region, and a 2781 K world can no longer contain visually
 * cold holes.
 */
public final class PlanetClimateField {

    private static final double MACRO_WAVELENGTH = 2600.0;
    private static final double REGIONAL_WAVELENGTH = 700.0;
    private static final double WARP_BLOCKS = 420.0;
    /** R23: share of the band width the regional (700 block) perturbation may add. */
    private static final double REGIONAL_SHARE = 0.5;
    /** R23: humidity half-width around the archetype's baseline humidity. */
    private static final double HUMIDITY_WIDTH = 0.32;

    private PlanetClimateField() {}

    /**
     * R23 (T-2): half-width of the LOCAL temperature variation for a thermal band, in
     * normalized [0,1] units. Cold and inferno worlds vary little; temperate worlds vary most.
     */
    public static double temperatureWidth(TemperatureBand band) {
        if (band == null) return 0.20;
        return switch (band) {
            case FROZEN -> 0.12;
            case COLD -> 0.16;
            case TEMPERATE -> 0.22;
            case WARM -> 0.20;
            case HOT -> 0.18;
            case INFERNO -> 0.10;
        };
    }

    /** Normalized temperature in [0,1] at a world column. */
    public static double temperatureAt(long seed, ClimateArchetype arch, int x, int z) {
        return temperatureAt(seed, arch, 0.5, x, z);
    }

    /**
     * Back-compat entry point (archetype-owned mean): the mean is the archetype's baseline and
     * the width comes from the band of that baseline.
     */
    public static double temperatureAt(long seed, ClimateArchetype arch, double axialBias,
                                       int x, int z) {
        if (arch == null) return 0.5;
        return temperatureAt(seed, arch, arch.baseTemperature(),
                temperatureWidth(TemperatureBand.of(arch.baseTemperature())), axialBias, x, z);
    }

    /**
     * R23 (T-2) CANONICAL entry point: {@code planet mean temperature01 + bounded local
     * deviation + (later) the elevation lapse}. The deviation is {@code shape[-1,1] × width},
     * so the local climate can never leave the planet's thermal neighbourhood.
     *
     * @param mean01  canonical planetary mean temperature in [0,1] (from the physical profile)
     * @param width01 half-width of the local variation (see {@link #temperatureWidth})
     */
    public static double temperatureAt(long seed, ClimateArchetype arch, double mean01,
                                       double width01, double axialBias, int x, int z) {
        double mean = clamp01(mean01);
        if (arch == null) return mean;
        double width = clamp(width01, 0.04, 0.35);
        double shape = patternShape(seed, arch.pattern(), axialBias, x, z);
        double regional = macro01(seed + 0x71L, x, z, REGIONAL_WAVELENGTH) - 0.5;
        double dev = shape * width
                + regional * width * REGIONAL_SHARE * clamp01(arch.regionalVariance());
        return clamp01(mean + dev);
    }

    /** Normalized humidity in [0,1] at a world column. */
    public static double humidityAt(long seed, ClimateArchetype arch, int x, int z) {
        return humidityAt(seed, arch, 0.5, x, z);
    }

    /** R23 (T-2): humidity with the axial bias + the elevation/water coupling. */
    public static double humidityAt(long seed, ClimateArchetype arch, double axialBias,
                                    int x, int z) {
        if (arch == null) return 0.5;
        double base = clamp01(arch.baseHumidity());
        double shape = patternShape(seed + 0x19L, ClimateArchetype.ClimatePattern.PATCHY,
                axialBias, x, z);
        double regional = macro01(seed + 0x83L, x, z, REGIONAL_WAVELENGTH) - 0.5;
        double dev = shape * HUMIDITY_WIDTH
                + regional * HUMIDITY_WIDTH * 0.6 * clamp01(arch.regionalVariance());
        return clamp01(base + dev);
    }

    /** R22 elevation effect: high columns are colder (lapse-rate analogue). */
    public static double withElevationCooling(double temperature, double elevation01) {
        double t = clamp01(elevation01);
        return clamp01(temperature - 0.22 * Math.max(0.0, t - 0.55) * 2.2);
    }

    /** R22 water effect: columns near liquid are more humid. */
    public static double withWaterInfluence(double humidity, double surfaceWetness01) {
        return clamp01(humidity + 0.18 * clamp01(surfaceWetness01));
    }

    /** Aridity = dryness in [0,1] (1 = bone dry), from humidity + temperature. */
    public static double aridityAt(long seed, ClimateArchetype arch, int x, int z) {
        double t = temperatureAt(seed, arch, x, z);
        double h = humidityAt(seed, arch, x, z);
        return clamp01(0.65 * (1.0 - h) + 0.35 * t);
    }

    // ------------------------------------------------------------------ internals

    /**
     * R23 (T-2): the archetype's low-frequency distribution shape as a NORMALIZED signal in
     * {@code [-1,1]} (0 = the planet's mean). Consumers scale it by their band half-width, so
     * the shape never carries an absolute temperature of its own.
     */
    private static double patternShape(long seed, ClimateArchetype.ClimatePattern pattern,
                                       double axialBias, int x, int z) {
        long axis = seed ^ 0x5DEECE66DL;
        double ax = Math.cos(axis * 1.0e-9);
        double az = Math.sin(axis * 1.0e-9);
        // R22: the axial climate bias shifts the pattern centre — a biased planet has a
        // genuine warm/cold hemisphere instead of a perfectly symmetric field.
        double centre = 0.5 + (clamp01(axialBias) - 0.5) * 0.55;
        double radial = clamp01((x * ax + z * az) / 4096.0 - (centre - 0.5) * 2.0 + 0.5);
        double raw;
        switch (pattern) {
            case BANDS -> {
                double warp = (macroNoise(seed + 0x2L, x, z) - 0.5) * WARP_BLOCKS / 2048.0;
                raw = (radial + warp - 0.5) * 0.9;
            }
            case ONE_SIDED -> raw = (radial - 0.5) * 1.1;
            case DUAL_POLE -> raw = (0.5 - Math.abs(radial - centre) * 2.0) * 0.9;
            case EQUATORIAL -> raw = (Math.abs(radial - centre) * 2.0 - 0.5) * 0.6;
            default -> raw = (macroNoise(seed, x, z) - 0.5) * 1.6;  // PATCHY
        }
        return clamp(raw / maxDeviation(pattern), -1.0, 1.0);
    }

    /** Largest absolute deviation a pattern can add before normalization. */
    private static double maxDeviation(ClimateArchetype.ClimatePattern pattern) {
        return switch (pattern) {
            case BANDS, DUAL_POLE -> 0.45;
            case ONE_SIDED -> 0.55;
            case EQUATORIAL -> 0.30;
            default -> 0.80;   // PATCHY
        };
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

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
