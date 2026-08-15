package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Phase-3 POC terrain generator: deterministic multi-octave value noise around a
 * base elevation. {@code height(x, z) = baseHeight + &Sigma; amp_i * sample_i(x, z)}.
 *
 * <p>Even though the POC keeps it simple (default one octave), octaves are already
 * supported so later "multiple noise layers" require no rewrite of the consumer.
 * All randomness flows through {@link Seeds}, hence results are stable.
 */
public final class ValueNoiseTerrainGenerator implements TerrainGenerator {

    private final long terrainSeed;
    private final double baseHeight;
    private final double amplitude;
    private final double frequency;
    private final int octaves;

    public ValueNoiseTerrainGenerator(long terrainSeed, double baseHeight,
                                      double amplitude, double frequency) {
        this(terrainSeed, baseHeight, amplitude, frequency, 1);
    }

    public ValueNoiseTerrainGenerator(long terrainSeed, double baseHeight,
                                      double amplitude, double frequency, int octaves) {
        if (octaves < 1) throw new IllegalArgumentException("octaves must be >= 1");
        this.terrainSeed = terrainSeed;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.octaves = octaves;
    }

    @Override
    public long seed() {
        return terrainSeed;
    }

    @Override
    public double height(int x, int z) {
        double total = 0.0;
        double amp = amplitude;
        double freq = frequency;
        for (int i = 0; i < octaves; i++) {
            total += amp * octaveSample(x * freq, z * freq, i);
            amp *= 0.5;
            freq *= 2.0;
        }
        return baseHeight + total;
    }

    /** Single deterministic value-noise sample in {@code [-1, 1]}. */
    private double octaveSample(double x, double z, int octave) {
        int x0 = floor(x), z0 = floor(z);
        double tx = smoothstep(x - x0);
        double tz = smoothstep(z - z0);

        double v00 = corner(x0, z0, octave);
        double v01 = corner(x0, z0 + 1, octave);
        double v10 = corner(x0 + 1, z0, octave);
        double v11 = corner(x0 + 1, z0 + 1, octave);

        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    private double corner(int cx, int cz, int octave) {
        long h = Seeds.derive(terrainSeed, "us.terrain.value", cx, cz, octave);
        return 2.0 * Seeds.fraction(h, 0) - 1.0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
