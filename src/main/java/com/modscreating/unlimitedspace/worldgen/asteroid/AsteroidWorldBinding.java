package com.modscreating.unlimitedspace.worldgen.asteroid;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Reusable binding seam that maps an {@link AsteroidClusterId} to concrete Minecraft
 * identifiers for its single playable world (the field itself — no separate orbit).
 *
 * <p>Dimension identity is stable and deterministic, derived only from the cluster code and
 * the {@code asteroid/} namespace: {@code unlimitedspace:asteroid/<cluster-code>}. It therefore
 * can never collide with the planet ({@code planet/...}) or moon ({@code moon/...}) dimensions.
 *
 * <p>This is the Minecraft-side mirror of the pure-domain
 * {@link com.modscreating.unlimitedspace.core.destination.AsteroidWorldDestination}, and the
 * destination adapter for the future Creating Space
 * {@code RocketAccessibleDimension} registration (R11). One class serves every cluster; this
 * phase only declares the seam, it does NOT register any asteroid dimension.
 */
public final class AsteroidWorldBinding {

    private AsteroidWorldBinding() {}

    /** {@code unlimitedspace:asteroid/<cluster-code>} */
    public static ResourceLocation location(AsteroidClusterId cluster) {
        return ResourceLocation.fromNamespaceAndPath(
                UnlimitedSpace.MODID, "asteroid/" + cluster.code());
    }

    public static ResourceKey<Level> level(AsteroidClusterId cluster) {
        return ResourceKey.create(Registries.DIMENSION, location(cluster));
    }

    public static ResourceKey<LevelStem> levelStem(AsteroidClusterId cluster) {
        return ResourceKey.create(Registries.LEVEL_STEM, location(cluster));
    }

    public static ResourceKey<DimensionType> dimensionType(AsteroidClusterId cluster) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, location(cluster));
    }
}
