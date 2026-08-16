package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Generic, reusable binding seam that maps a {@link PlanetId} + {@link WorldKind}
 * (surface or orbit) to concrete Minecraft identifiers.
 *
 * <p>Dimension identity is stable and deterministic, derived only from the planet code and
 * the world kind: {@code planet/<planet-code>/<surface|orbit>} under {@code unlimitedspace:}.
 * One class serves every planet (Planet A, Planet B, Planet C, ...) -- no per-planet copies.
 *
 * <p>This is the destination-binding layer of the corrected architecture: the domain
 * (PlanetId) knows nothing about ResourceLocation/ResourceKey; Creating Space integration
 * is data-only (see {@code data/.../rocket_accessible_dimension/}).
 */
public final class PlanetWorldBinding {

    private PlanetWorldBinding() {}

    /** {@code unlimitedspace:planet/<planet-code>/<surface|orbit>} */
    public static ResourceLocation location(PlanetId planetId, WorldKind kind) {
        return ResourceLocation.fromNamespaceAndPath(
                UnlimitedSpace.MODID,
                "planet/" + planetId.code() + "/" + kind.name().toLowerCase());
    }

    public static ResourceKey<Level> level(PlanetId planetId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION, location(planetId, kind));
    }

    public static ResourceKey<LevelStem> levelStem(PlanetId planetId, WorldKind kind) {
        return ResourceKey.create(Registries.LEVEL_STEM, location(planetId, kind));
    }

    public static ResourceKey<DimensionType> dimensionType(PlanetId planetId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, location(planetId, kind));
    }
}
