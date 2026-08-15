package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the Phase 6H space worldgen codecs. */
public final class SpaceWorldgenRegistries {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UnlimitedSpace.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, UnlimitedSpace.MODID);

    private SpaceWorldgenRegistries() {}

    static {
        CHUNK_GENERATORS.register("space", () -> SpaceChunkGenerator.CODEC);
        BIOME_SOURCES.register("space_biome_source", () -> SpaceBiomeSource.CODEC);
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
    }
}