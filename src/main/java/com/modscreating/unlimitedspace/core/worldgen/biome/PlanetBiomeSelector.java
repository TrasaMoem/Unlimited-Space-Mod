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