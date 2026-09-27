package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic, order-independent legacy biome selector (adapter layer).
 *
 * <p>This is a back-compat adapter exposing the original coarse
 * {@code (planetSeed,x,z) -> PlanetBiome} interface used by older MC adapters.
 * The R8 climate-aware path is {@link PlanetBiomeProfile#biomeAt(int,int)}.
 *
 * <p>Pure function: no global Random, no display names, stable across restarts.
 */
public final class PlanetBiomeSelector {

    private static final String NS = "us.biome.selector";
    private static final long X_SLOT = 30001L;
    private static final long Z_SLOT = 30002L;

    private PlanetBiomeSelector() {}

    /** Back-compat: legacy 4 archetypes (kept for old call-sites). */
    public static PlanetBiome select(double sample) {
        return switch ((int) Math.floor(sample * 4)) {
            case 0 -> PlanetBiome.HOT_DESERT;
            case 1 -> PlanetBiome.ROCKY_PLAINS;
            case 2 -> PlanetBiome.COLD_ROCKY_PLAINS;
            default -> PlanetBiome.WARM_WET;
        };
    }

    /** Back-compat convenience: (biomeSeed, x, z) -> legacy archetype. */
    public static PlanetBiome select(long planetSeed, int x, int z) {
        return select(sample(planetSeed, x, z));
    }

    /**
     * PHASE 4 (de-lottery): COHERENT large-scale biome field.
     *
     * <p>The legacy 64-block cell field made biome labels (and anything keyed on them, e.g.
     * materials) flip every ~64 blocks. This variant drives the SAME threshold archetypes
     * from a two-octave field with a ~720-block wavelength, so labels change over hundreds
     * of blocks and form real regions. Deterministic, allocation-free.
     */
    public static PlanetBiome selectCoherent(long biomeSeed, int x, int z) {
        double coarse = valueNoise(biomeSeed, x * (1.0 / 720.0), z * (1.0 / 720.0));
        double fine = valueNoise(Seeds.derive(biomeSeed, NS + ".fine"),
                x * (1.0 / 240.0), z * (1.0 / 240.0));
        return select(0.78 * coarse + 0.22 * fine);
    }

    /** Smooth value-noise sample in [0,1] at an arbitrary block frequency. */
    private static double valueNoise(long seed, double sx, double sz) {
        int x0 = floorI(sx), z0 = floorI(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        long h00 = Seeds.derive(seed, NS + ".vn", x0, z0);
        long h10 = Seeds.derive(seed, NS + ".vn", x0 + 1, z0);
        long h01 = Seeds.derive(seed, NS + ".vn", x0, z0 + 1);
        long h11 = Seeds.derive(seed, NS + ".vn", x0 + 1, z0 + 1);
        double a = lerp(Seeds.fraction(h00, X_SLOT), Seeds.fraction(h10, X_SLOT), tx);
        double b = lerp(Seeds.fraction(h01, Z_SLOT), Seeds.fraction(h11, Z_SLOT), tz);
        return lerp(a, b, tz);
    }

    private static int floorI(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Weighted-lookup seed for a grid cell (cheap, no allocation). */
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

    /** Climate-aware surface check (R8 forward path helper). */
    public static boolean surfaceValidForClimate(PlanetSurface surface,
                                                 double temp, double humidity, boolean hasWater) {
        return switch (surface) {
            case SOLID_ICE -> temp < 0;
            case SOLID_VOLCANIC -> temp > 0.6;
            case SOLID_DESERT -> humidity < 0.3;
            default -> true;
        };
    }

    private static double frac(double v) { return v - Math.floor(v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}