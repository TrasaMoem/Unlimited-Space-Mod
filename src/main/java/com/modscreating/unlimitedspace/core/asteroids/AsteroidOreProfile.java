package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic weighted ore-distribution profile for a single asteroid cluster.
 *
 * <p>Each cluster has exactly ONE {@link #dominantOre() dominant} ore with a strictly
 * higher generation weight than every other regular ore (ties are disabled by default;
 * they can be permitted via {@link #allowTies()} for explicitly agreed exceptions).
 * {@code dominantOre} has the highest configured generation weight, NOT "all asteroid
 * blocks become that ore".
 *
 * <p>Everything is a pure function of the {@link #generationSeed()} and fixed draw slots —
 * no runtime randomness, no per-chunk claims here. This proves the domain distribution
 * profile only; actual chunk-level ore frequency is an R11 worldgen concern.
 */
public record AsteroidOreProfile(
        long generationSeed,
        AsteroidOre dominantOre,
        Map<AsteroidOre, Double> weights,
        boolean allowTies) {

    public AsteroidOreProfile {
        Objects.requireNonNull(dominantOre, "dominantOre");
        Objects.requireNonNull(weights, "weights");
        if (weights.isEmpty()) throw new IllegalArgumentException("ore weights must not be empty");
        if (!weights.containsKey(dominantOre)) {
            throw new IllegalArgumentException("dominant ore must be present in the weight set");
        }
        // Reject zero / negative / null weights (invalid input is rejected, never silently trusted).
        for (Map.Entry<AsteroidOre, Double> e : weights.entrySet()) {
            Double w = e.getValue();
            if (w == null || !(w > 0.0)) {
                throw new IllegalArgumentException("ore weight must be positive for " + e.getKey());
            }
        }
        if (!allowTies && !dominantStrictlyHighest(dominantOre, weights)) {
            throw new IllegalArgumentException(
                    "dominant ore must have strictly the highest generation weight");
        }
    }

    private static boolean dominantStrictlyHighest(AsteroidOre dominant, Map<AsteroidOre, Double> weights) {
        double dom = weights.get(dominant);
        for (Map.Entry<AsteroidOre, Double> e : weights.entrySet()) {
            if (e.getKey() != dominant && e.getValue() >= dom) return false;
        }
        return true;
    }

    /**
     * Canonical factory: derive a fully valid, deterministic ore profile from the cluster's
     * ore seed. The dominant ore is chosen first, then every ore (including the dominant)
     * receives an independent seed-driven weight, with the dominant guaranteed strictly
     * highest because its range is always above the other ores' range.
     */
    public static AsteroidOreProfile create(long oreSeed) {
        AsteroidOre dominant = AsteroidOre.pickDominant(oreSeed, 70001L);

        EnumMap<AsteroidOre, Double> w = new EnumMap<>(AsteroidOre.class);
        // Dominant range [0.35, 0.60) — always above the others.
        double dominantWeight = 0.35 + 0.25 * Seeds.fraction(oreSeed, 70002L);
        for (AsteroidOre ore : AsteroidOre.values()) {
            if (ore == dominant) {
                w.put(ore, dominantWeight);
            } else {
                // Other ores range [0.01, 0.21) — strictly below dominant. No ties.
                w.put(ore, 0.01 + 0.20 * Seeds.fraction(oreSeed, 70003L + ore.ordinal()));
            }
        }
        return new AsteroidOreProfile(oreSeed, dominant, w, false);
    }

    /** Whether the dominant ore has strictly the highest configured weight. */
    public boolean dominantHasHighestWeight() {
        return dominantStrictlyHighest(dominantOre, weights);
    }

    /** The configured generation weight of an ore (0.0 for ores outside the set). */
    public double weightOf(AsteroidOre ore) {
        return weights.getOrDefault(ore, 0.0);
    }

    /** Sum of all configured weights (> 0 by construction). */
    public double totalWeight() {
        double total = 0.0;
        for (Double w : weights.values()) total += w;
        return total;
    }

    /** Normalised probability of an ore in {@code [0, 1]} (weights sum to a distribution). */
    public double probabilityOf(AsteroidOre ore) {
        double total = totalWeight();
        return total <= 0.0 ? 0.0 : weightOf(ore) / total;
    }
}
