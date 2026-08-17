package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic asteroid body-placement pattern.
 *
 * <p>These are the abstract "how is this cluster laid out" shapes the generator will later
 * interpret (sparse rocks, dense belt, rubble cloud, ...). Selection is a pure function of
 * the asteroid seed + a fixed slot — clusters are NOT assigned patterns by plain index
 * (cluster 0 = sparse, cluster 1 = dense, ...). Adding a pattern never requires editing the
 * main generator.
 */
public enum AsteroidShapePattern {

    SPARSE_FLOATING_ROCKS("a few widely spaced floating rocks"),
    DENSE_BELT("a dense belt of many small bodies"),
    LARGE_BOULDERS("a handful of very large boulders"),
    FRACTURED_ROCKS("broken, fractured rock shards"),
    MIXED_SIZE_CLUSTER("bodies of strongly mixed sizes"),
    RUBBLE_CLOUD("a cloud of small rubble"),
    IRREGULAR_MASSIVE("irregular, massive bodies"),
    HIGH_DENSITY_FIELD("a very high density field");

    private final String description;

    AsteroidShapePattern(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Deterministic pattern selection from seed + fixed slot (uniform over the catalogue). */
    static AsteroidShapePattern select(long seed, long slot) {
        AsteroidShapePattern[] values = values();
        int idx = (int) (Seeds.fraction(seed, slot) * values.length);
        return values[idx];
    }
}
