package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic star generation. A star's parameters are functions of its seed and
 * fixed slots, and are constrained to the allowed ranges of its spectral type.
 */
public final class StarGenerator {

    private StarGenerator() {}

    private static final String STAR_NAMESPACE = "unlimitedspace.star";

    /** Star seed derived from the galaxy seed + vanilla system index. */
    public static long starSeed(long galaxySeed, int systemIndex) {
        return Seeds.derive(galaxySeed, STAR_NAMESPACE, systemIndex);
    }

    public static Star fromSeed(long galaxySeed, StarSystemId systemId) {
        long seed = starSeed(galaxySeed, systemId.index());
        StarType type = pickType(seed, 0);
        double temperature = Seeds.rangeDouble(seed, 1, type.minTemperature(), type.maxTemperature());
        double size = Seeds.rangeDouble(seed, 2, type.minSize(), type.maxSize());
        double luminosity = Seeds.rangeDouble(seed, 3, type.minLuminosity(), type.maxLuminosity());
        return Star.of(new StarId(systemId), seed, type, temperature, size, luminosity, type.colorRgb());
    }

    /** Weighted, deterministic type selection (realistic-ish occurrence). */
    static StarType pickType(long seed, long slot) {
        double f = Seeds.fraction(seed, slot);
        double acc = 0.0;
        for (Cand c : CANDIDATES) {
            acc += c.weight;
            if (f < acc) return c.type;
        }
        return StarType.M;
    }

    private record Cand(StarType type, double weight) {}

    // weights roughly reflect stellar population; order matters for cumulative sum.
    private static final Cand[] CANDIDATES = {
            new Cand(StarType.M, 0.45),
            new Cand(StarType.K, 0.30),
            new Cand(StarType.G, 0.13),
            new Cand(StarType.F, 0.06),
            new Cand(StarType.A, 0.04),
            new Cand(StarType.B, 0.015),
            new Cand(StarType.O, 0.005),
    };
}
