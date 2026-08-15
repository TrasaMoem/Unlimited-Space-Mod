package com.modscreating.unlimitedspace.worldgen.space;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Minimal biome source for space/deep-space POC. */
public final class SpaceBiomeSource extends BiomeSource {
    public static final MapCodec<SpaceBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Biome.CODEC.fieldOf("biome").forGetter(s -> s.biome),
            com.mojang.serialization.Codec.LONG.fieldOf("biome_seed").forGetter(s -> s.biomeSeed)
    ).apply(inst, SpaceBiomeSource::new));

    private final Holder<Biome> biome;
    private final long biomeSeed;

    public SpaceBiomeSource(Holder<Biome> biome, long biomeSeed) {
        this.biome = biome; this.biomeSeed = biomeSeed;
    }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.of(biome); }
    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) { return biome; }
    public long biomeSeed() { return biomeSeed; }
}