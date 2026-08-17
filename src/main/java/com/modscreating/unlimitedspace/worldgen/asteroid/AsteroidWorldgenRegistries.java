package com.modscreating.unlimitedspace.worldgen.asteroid;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the R11 asteroid worldgen codecs so a {@code LevelStem} datapack can reference them
 * by id: {@code unlimitedspace:asteroid} (chunk generator) and
 * {@code unlimitedspace:asteroid_biome_source} (biome source). Follows the same pattern as
 * {@code PlanetWorldgenRegistries}.
 */
public final class AsteroidWorldgenRegistries {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UnlimitedSpace.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, UnlimitedSpace.MODID);

    private AsteroidWorldgenRegistries() {}

    static {
        CHUNK_GENERATORS.register("asteroid", () -> AsteroidChunkGenerator.CODEC);
        BIOME_SOURCES.register("asteroid_biome_source", () -> AsteroidBiomeSource.CODEC);
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}
