package com.modscreating.unlimitedspace.worldgen.asteroid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Minimal {@link BiomeSource} for an asteroid field world (R11).
 *
 * <p>The asteroid field is a void environment with discrete bodies; there are no per-column
 * biome regions worth selecting, so this source simply returns the first biome from its datapack
 * pool (the same behaviour as a FixedBiomeSource but with a registered codec so a LevelStem JSON
 * can reference {@code unlimitedspace:asteroid_biome_source}). It requires at least one biome.
 */
public final class AsteroidBiomeSource extends BiomeSource {

    public static final MapCodec<AsteroidBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(inst, AsteroidBiomeSource::new));

    private final List<Holder<Biome>> biomes;

    public AsteroidBiomeSource(List<Holder<Biome>> biomes) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("AsteroidBiomeSource requires >=1 biome");
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomes.get(0);
    }
}
