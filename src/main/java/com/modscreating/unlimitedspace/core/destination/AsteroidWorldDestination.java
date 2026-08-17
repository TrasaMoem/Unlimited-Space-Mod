package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.seed.AsteroidSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic, pure-domain identity of the real world that belongs to an asteroid cluster.
 *
 * <p>For MVP an asteroid cluster has exactly ONE playable destination (the field itself) and
 * deliberately NO separate orbit world — an asteroid orbit system is NOT part of this phase.
 * This mirrors {@link WorldDestination} / {@link MoonWorldDestination} but collapses the
 * surface/orbit distinction into a single {@code _field} world.
 *
 * <p>Imports no Minecraft types; the mapping to
 * {@code ResourceLocation}/{@code ResourceKey<LevelStem>} and to a Creating Space
 * {@code RocketAccessibleDimension} is the adapter-layer responsibility
 * (see {@code worldgen.asteroid.AsteroidWorldBinding}).
 *
 * <p>Identity rules honoured: {@link #worldSeed()} is a pure function of the cluster seed, and
 * {@link #code()} derives from the stable {@link AsteroidClusterId} — never a display name —
 * so {@code (AsteroidClusterId, seed)} always reconstructs the same world.
 *
 * @param cluster   the owning asteroid cluster
 * @param seed      the owning cluster's stable seed
 * @param worldSeed deterministic seed for the asteroid world
 */
public record AsteroidWorldDestination(
        AsteroidClusterId cluster,
        AsteroidSeed seed,
        long worldSeed) {

    /** The single field/destination world of the cluster. */
    public static AsteroidWorldDestination field(AsteroidClusterId cluster, AsteroidSeed seed) {
        return new AsteroidWorldDestination(cluster, seed, deriveWorldSeed(seed.value()));
    }

    /** The owning body kind is always {@link BodyKind#ASTEROID_CLUSTER}. */
    public BodyKind bodyKind() {
        return BodyKind.ASTEROID_CLUSTER;
    }

    /**
     * Stable destination code that an adapter can turn into dimension/registry keys,
     * e.g. {@code system_0000_asteroid_00_field}.
     */
    public String code() {
        return cluster.code() + "_field";
    }

    /**
     * Deterministic per-world seed, namespace-separated from all other seeds so that later
     * worldgen algorithm changes never reshuffle the world identity.
     */
    private static long deriveWorldSeed(long asteroidSeed) {
        return Seeds.derive(asteroidSeed, "unlimitedspace.dest.asteroid");
    }
}
