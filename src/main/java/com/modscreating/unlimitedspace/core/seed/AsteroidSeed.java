package com.modscreating.unlimitedspace.core.seed;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;

/**
 * Stable seed for a single asteroid cluster. It depends only on the owning star system seed
 * and the cluster's fixed index ({@link Seeds#asteroidField(long, int)}), never on display
 * names or on the number of clusters already generated.
 *
 * @param value the 64-bit seed
 */
public record AsteroidSeed(long value) {

    /** Derive this cluster's seed from its owning star system seed + cluster index. */
    public static AsteroidSeed forSlot(long starSystemSeed, int clusterIndex) {
        return new AsteroidSeed(Seeds.asteroidField(starSystemSeed, clusterIndex));
    }

    /** Derive this cluster's seed from a cluster identity (via the owning system seed). */
    public static AsteroidSeed forCluster(AsteroidClusterId id, long starSystemSeed) {
        return forSlot(starSystemSeed, id.clusterIndex());
    }

    public SubsystemSeed subsystem(String name) {
        return new SubsystemSeed(name, Seeds.subsystem(value, name));
    }

    public long generationSeed() { return Seeds.subsystem(value, "generation"); }
    public long materialSeed()   { return Seeds.subsystem(value, "materials"); }
    public long oreSeed()        { return Seeds.subsystem(value, "ore"); }
    public long patternSeed()    { return Seeds.subsystem(value, "pattern"); }
}
