package com.modscreating.unlimitedspace.nav;

import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Real {@link DestinationCatalog} backed by the running server's registries: the Creating
 * Space {@code rocket_accessible_dimension} datapack registry and the {@code LevelStem}
 * registry. Both are read-only lookups; nothing is registered or mutated here.
 */
public final class CsCatalog implements DestinationCatalog {

    private final Registry<RocketAccessibleDimension> csRegistry;
    private final Registry<LevelStem> levelStemRegistry;

    private CsCatalog(Registry<RocketAccessibleDimension> csRegistry,
                      Registry<LevelStem> levelStemRegistry) {
        this.csRegistry = csRegistry;
        this.levelStemRegistry = levelStemRegistry;
    }

    /** Build from a server, tolerating an absent CS registry (returns "not registered"). */
    public static CsCatalog of(MinecraftServer server) {
        Registry<RocketAccessibleDimension> cs = server.registryAccess()
                .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
        Registry<LevelStem> stems = server.registryAccess()
                .registry(Registries.LEVEL_STEM).orElse(null);
        return new CsCatalog(cs, stems);
    }

    @Override
    public boolean csRegistered(ResourceLocation rl) {
        return csRegistry != null && csRegistry.containsKey(rl);
    }

    @Override
    public boolean hasLevelStem(ResourceLocation rl) {
        return levelStemRegistry != null && levelStemRegistry.containsKey(rl);
    }
}