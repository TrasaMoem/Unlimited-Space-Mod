package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.config.PlanetDimensionConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Adapter/binding layer that connects the pure domain ({@code PlanetId} conceptually)
 * to a Minecraft {@link ResourceLocation}/{@code ResourceKey}. The domain never knows
 * about this — exactly the wiring that lets the dimension architecture change later
 * (A/C/D) without touching {@code Galaxy}/{@code Planet}/{@code PlanetProperties}.
 *
 * <p>For the POC every selected planet maps to the single pre-registered dimension
 * {@code unlimitedspace:test_planet} (Variant A).
 */
public final class PlanetDimensionBinding {

    public static final String PLANET_DIMENSION_PATH = "test_planet";

    private PlanetDimensionBinding() {}

    public static ResourceLocation location() {
        return ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, PLANET_DIMENSION_PATH);
    }

    /** The {@code LevelStem} datapack key for the POC planet world. */
    public static ResourceKey<LevelStem> levelStem() {
        return ResourceKey.create(Registries.LEVEL_STEM, location());
    }

    public static ResourceKey<DimensionType> dimensionType() {
        return ResourceKey.create(Registries.DIMENSION_TYPE, location());
    }

    /** The runtime {@link Level} key for {@code server.getLevel(...)}. */
    public static ResourceKey<Level> level() {
        return ResourceKey.create(Registries.DIMENSION, location());
    }

    /** Which planet the POC binding points at (selected by debug config, not core). */
    public record PlanetSelection(int systemIndex, int orbitIndex) {}

    public static PlanetSelection selection() {
        return new PlanetSelection(
                PlanetDimensionConfig.SYSTEM_INDEX.get(),
                PlanetDimensionConfig.ORBIT_INDEX.get());
    }
}