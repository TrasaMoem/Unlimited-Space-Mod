package com.modscreating.unlimitedspace.nav;

import net.minecraft.resources.ResourceLocation;

/**
 * Abstraction over the two runtime registries that decide whether a mapped destination is
 * actually playable: the Creating Space {@code rocket_accessible_dimension} registry and the
 * world {@code LevelStem} registry. Kept as an interface so playability classification can be
 * unit-tested without a live server; the real reads live in {@link CsCatalog}.
 */
public interface DestinationCatalog {

    /** Whether the Creating Space {@code rocket_accessible_dimension} registry contains {@code rl}. */
    boolean csRegistered(ResourceLocation rl);

    /** Whether a {@code LevelStem} is registered for {@code rl}. */
    boolean hasLevelStem(ResourceLocation rl);
}