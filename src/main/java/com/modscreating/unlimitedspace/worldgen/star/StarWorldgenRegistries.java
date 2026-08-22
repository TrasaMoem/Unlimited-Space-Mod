package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the R14.9 star-surface worldgen codecs so a {@code LevelStem} datapack can reference them
 * by id: {@code unlimitedspace:star} (chunk generator) and {@code unlimitedspace:star_biome_source}
 * (biome source). Follows the same pattern as {@code PlanetWorldgenRegistries} / {@code AsteroidWorldgenRegistries}.
 */
public final class StarWorldgenRegistries {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UnlimitedSpace.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, UnlimitedSpace.MODID);

    private StarWorldgenRegistries() {
    }

    static {
        CHUNK_GENERATORS.register("star", () -> StarChunkGenerator.CODEC);
        BIOME_SOURCES.register("star_biome_source", () -> StarBiomeSource.CODEC);
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}
