package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic star generation. A star's parameters are functions of its seed and
 * fixed slots, and are constrained to the allowed ranges of its spectral type.
 *
 * <p>R12: a system can be single, binary or trinary. {@link #starsFor(long, StarSystemId)}
 * derives the multiplicity deterministically and generates companions; the primary star
 * is byte-identical to the historical {@link #fromSeed(long, StarSystemId)} result so
 * existing single-star consumers and tests stay unchanged.
 */
public final class StarGenerator {

    private StarGenerator() {}

    private static final String STAR_NAMESPACE = "unlimitedspace.star";
    private static final String COMPANION_NAMESPACE = "unlimitedspace.star.companion";

    /** Star seed derived from the galaxy seed + vanilla system index. */
    public static long starSeed(long galaxySeed, int systemIndex) {
        return Seeds.derive(galaxySeed, STAR_NAMESPACE, systemIndex);
    }

    public static Star fromSeed(long galaxySeed, StarSystemId systemId) {
        return primary(starSeed(galaxySeed, systemId.index()), systemId);
    }

    /* ---------------- R12 multi-star support ---------------- */

    /**
     * Deterministic system multiplicity: 1 = single (70%), 2 = binary (20%),
     * 3 = trinary (10%). Driven by the star-system seed.
     */
    public static int systemStarCount(long starSystemSeed) {
        double f = Seeds.fraction(starSystemSeed, 77L);
        if (f < 0.70) return 1;
        if (f < 0.90) return 2;
        return 3;
    }

    /**
     * All stars of a system, primary first, in stable order.
     * Same {@code (galaxySeed, systemId)} always yields the same list.
     */
    public static List<Star> starsFor(long galaxySeed, StarSystemId systemId) {
        long seed = starSeed(galaxySeed, systemId.index());
        int count = systemStarCount(seed);
        List<Star> stars = new ArrayList<>(count);
        Star primary = primary(seed, systemId);
        stars.add(primary);
        for (int i = 1; i < count; i++) {
            stars.add(companion(seed, systemId, i, primary.temperature()));
        }
        return stars;
    }

    /** The system primary star (existing single-star logic, unchanged output). */
    public static Star primary(long starSystemSeed, StarSystemId systemId) {
        StarType type = pickType(starSystemSeed, 0);
        double temperature = Seeds.rangeDouble(starSystemSeed, 1, type.minTemperature(), type.maxTemperature());
        double size = Seeds.rangeDouble(starSystemSeed, 2, type.minSize(), type.maxSize());
        double luminosity = Seeds.rangeDouble(starSystemSeed, 3, type.minLuminosity(), type.maxLuminosity());
        return Star.of(new StarId(systemId), starSystemSeed, type, temperature, size, luminosity, type.colorRgb());
    }

    /**
     * Companion star (index >= 1). Derived from its own seed so every companion is
     * independent of generation order; companions lean towards cool, small stars
     * and are never hotter than the system primary.
     */
    static Star companion(long starSystemSeed, StarSystemId systemId, int index, double maxTemperature) {
        long seed = Seeds.derive(starSystemSeed, COMPANION_NAMESPACE, index);
        StarType type = pickCompanionType(seed);
        double lo = type.minTemperature();
        double hi = Math.min(type.maxTemperature(), maxTemperature);
        double temperature = (hi <= lo) ? maxTemperature : Seeds.rangeDouble(seed, 1, lo, hi);
        double size = Seeds.rangeDouble(seed, 2, type.minSize(), type.maxSize());
        double luminosity = Seeds.rangeDouble(seed, 3, type.minLuminosity(), type.maxLuminosity());
        return Star.of(new StarId(systemId), seed, type, temperature, size, luminosity, type.colorRgb());
    }

    /** Companion-type bias: mostly cool dwarfs, occasional G/F, rarely A. */
    static StarType pickCompanionType(long companionSeed) {
        double f = Seeds.fraction(companionSeed, 78L);
        if (f < 0.68) return StarType.M;
        if (f < 0.86) return StarType.K;
        if (f < 0.95) return StarType.G;
        if (f < 0.985) return StarType.F;
        return StarType.A;
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
