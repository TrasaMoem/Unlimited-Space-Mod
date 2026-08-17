package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Stable identity of one asteroid cluster inside a star system.
 * Pure domain value; no Minecraft coupling.
 *
 * <p>Example: {@code system_0000_asteroid_00}. The identity is a value object based on the
 * owning {@link StarSystemId} + a fixed per-system cluster index. It is independent of any
 * display name and of how many other clusters / systems have been generated. The stable
 * seed is derived the same way ({@code Seeds.asteroidField(starSystemSeed, clusterIndex)}),
 * so {@code WorldSeed + StarSystemId + clusterIndex} always reconstruct the same cluster.
 *
 * @param system       owning star system
 * @param clusterIndex stable, non-negative per-system cluster index
 */
public record AsteroidClusterId(StarSystemId system, int clusterIndex) {

    public AsteroidClusterId {
        if (clusterIndex < 0) throw new IllegalArgumentException("clusterIndex must be >= 0");
        java.util.Objects.requireNonNull(system, "system");
    }

    public static AsteroidClusterId of(StarSystemId system, int clusterIndex) {
        return new AsteroidClusterId(system, clusterIndex);
    }

    /** Stable code string, e.g. {@code system_0000_asteroid_00}. */
    public String code() {
        return system.code() + "_asteroid_" + String.format("%02d", clusterIndex);
    }

    @Override
    public String toString() {
        return code();
    }
}
