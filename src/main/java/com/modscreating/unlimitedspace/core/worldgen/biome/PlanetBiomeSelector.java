package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic, order-independent biome selector for one planet (Phase 7).
 *
 * <p>Input: a stable planet-level seed (e.g. {@code PlanetProperties.biomeSeed()})
 * and the column coordinates. Output: a {@link PlanetBiome} archetype.
 *
 * <p>Implementation is a fixed-cell + value-noise style sample: a continuous
 * field {@code h(x,z)} in [0,1) is derived from the seed via {@link Seeds}
 * (pure function, no global Random), and the biome is chosen by thresholding it.
 * Same (seed, x, z) always yields the same biome; different regions yield
 * different biomes; different seeds shift the distribution. No allocation and no
 * mutable state per sample.
 */
public final class PlanetBiomeSelector {

    private static final String NS = "us.biome.selector";
    private static final long X_SLOT = 30001L;
    private static final long Z_SLOT = 30002L;

    private PlanetBiomeSelector() {}

    /** Weighted-lookup seed for a specific grid cell (cheap, no allocation). */
    public static long cellSeed(long planetSeed, int cx, int cz) {
        return Seeds.derive(planetSeed, NS, cx, cz);
    }

    /** Smooth value-noise field in [0,1) from planet seed + coordinates. */
    public static double sample(long planetSeed, int x, int z) {
        int cx = (int) Math.floor((double) x / 64);
        int cz = (int) Math.floor((double) z / 64);
        double tx = smoothstep(frac(x / 64.0));
        double tz = smoothstep(frac(z / 64.0));
        double v00 = Seeds.fraction(cellSeed(planetSeed, cx, cz), X_SLOT);
        double v10 = Seeds.fraction(cellSeed(planetSeed, cx + 1, cz), X_SLOT);
        double v01 = Seeds.fraction(cellSeed(planetSeed, cx, cz + 1), Z_SLOT);
        double v11 = Seeds.fraction(cellSeed(planetSeed, cx + 1, cz + 1), Z_SLOT);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    /** Map a sample value in [0,1) to a biome archetype by thresholds. */
    public static PlanetBiome select(double sample) {
        if (sample < 0.25) return PlanetBiome.OCEAN;
        if (sample < 0.55) return PlanetBiome.HOT_DRY;
        if (sample < 0.80) return PlanetBiome.COLD_DRY;
        return PlanetBiome.WARM_WET;
    }

    /** Convenience: (planetSeed, x, z) -> biome in one call (pure function). */
    public static PlanetBiome select(long planetSeed, int x, int z) {
        return select(sample(planetSeed, x, z));
    }

    private static double frac(double v) { return v - Math.floor(v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}