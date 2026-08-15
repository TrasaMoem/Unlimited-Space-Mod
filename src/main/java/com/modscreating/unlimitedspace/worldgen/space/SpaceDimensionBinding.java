package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public final class SpaceDimensionBinding {
    public static final String SPACE_PATH = "space";
    private SpaceDimensionBinding() {}
    public static ResourceLocation location() { return ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, SPACE_PATH); }
    public static ResourceKey<LevelStem> levelStem() { return ResourceKey.create(Registries.LEVEL_STEM, location()); }
    public static ResourceKey<DimensionType> dimensionType() { return ResourceKey.create(Registries.DIMENSION_TYPE, location()); }
    public static ResourceKey<Level> level() { return ResourceKey.create(Registries.DIMENSION, location()); }
}