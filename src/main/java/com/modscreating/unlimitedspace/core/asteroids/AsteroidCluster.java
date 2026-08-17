package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.destination.AsteroidWorldDestination;
import com.modscreating.unlimitedspace.core.seed.AsteroidSeed;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

import java.util.Objects;

/**
 * Canonical domain object of a single asteroid cluster: its stable identity plus its
 * deterministic seed. Pure domain data; no Minecraft coupling.
 *
 * <p>An asteroid cluster is its own celestial object — NOT a planet, NOT a moon, NOT a
 * coordinate region inside {@code unlimitedspace:space}. Its generation profile, dominant
 * ore, material set and (future) world identity are all derived deterministically from the
 * {@link AsteroidSeed}.
 *
 * <p>Metadata is cheap: building a cluster never creates a Minecraft dimension.
 */
public final class AsteroidCluster {

    private final AsteroidClusterId id;
    private final AsteroidSeed seed;

    public AsteroidCluster(AsteroidClusterId id, AsteroidSeed seed) {
        this.id = Objects.requireNonNull(id, "id");
        this.seed = Objects.requireNonNull(seed, "seed");
    }

    public static AsteroidCluster of(AsteroidClusterId id, AsteroidSeed seed) {
        return new AsteroidCluster(id, seed);
    }

    /** Stable cluster identity (owning system + cluster index). */
    public AsteroidClusterId id() {
        return id;
    }

    /** Whether this cluster is exactly equivalent to another (identity + seed). */
    public AsteroidSeed seed() {
        return seed;
    }

    /** The parent star system of this cluster (explicit parent relationship). */
    public StarSystemId parentSystem() {
        return id.system();
    }

    /** The per-system cluster index. */
    public int clusterIndex() {
        return id.clusterIndex();
    }

    /** The deterministic generation profile (shape, density, material, ores). */
    public AsteroidGenerationProfile profile() {
        return AsteroidGenerationProfile.create(id, seed.value());
    }

    /** The cluster's dominant-ore profile (convenience, deterministic). */
    public AsteroidOreProfile oreProfile() {
        return profile().ore();
    }

    /** The cluster's material-composition profile. */
    public AsteroidMaterialProfile materialProfile() {
        return profile().material();
    }

    /** The cluster's single playable world destination identity (no separate orbit). */
    public AsteroidWorldDestination worldDestination() {
        return AsteroidWorldDestination.field(id, seed);
    }

    /** Stable debug/display key derived from identity + seed (never defines the identity). */
    public String displaySeedKey() {
        return id.code() + ":" + seed.value();
    }

    @Override
    public String toString() {
        return id.code();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AsteroidCluster that)) return false;
        return id.equals(that.id) && seed.equals(that.seed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, seed);
    }
}
