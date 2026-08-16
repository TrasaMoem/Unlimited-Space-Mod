package com.modscreating.unlimitedspace.core.destination;

/**
 * Kind of world a celestial body can own.
 *
 * <p>Per the corrected architecture a body conceptually has a {@link #SURFACE} world
 * (where the player walks) and an {@link #ORBIT} world (a travel/staging area), in the
 * same spirit as the Earth/Mars surface &amp; orbit destinations that Creating Space
 * models as separate records. A {@link #SURFACE} or {@link #ORBIT} world is a distinct
 * {@link WorldDestination} with its own deterministic seed.
 *
 * <p>This is pure domain data; how these map to {@code ResourceLocation}s /{@code LevelStem}
 * entries is an adapter-layer concern (see {@link WorldDestination}).
 */
public enum WorldKind {
    SURFACE,
    ORBIT
}