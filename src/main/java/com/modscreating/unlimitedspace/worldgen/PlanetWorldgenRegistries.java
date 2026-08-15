package com.modscreating.unlimitedspace.worldgen;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetBiomeSource;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the custom Minecraft worldgen codecs so a {@code LevelStem} datapack can
 * reference them by id: {@code unlimitedspace:planet} (chunk generator) and
 * {@code unlimitedspace:planet_biome_source} (biome source).
 */
public final class PlanetWorldgenRegistries {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UnlimitedSpace.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, UnlimitedSpace.MODID);

    private PlanetWorldgenRegistries() {}

    static {
        CHUNK_GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
        BIOME_SOURCES.register("planet_biome_source", () -> PlanetBiomeSource.CODEC);
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}