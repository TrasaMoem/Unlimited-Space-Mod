package com.modscreating.unlimitedspace.worldgen.star;

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
 * Minimal {@link BiomeSource} for a star surface world (R14.9). The star surface is a homogeneous
 * molten/plasma plane, so there are no meaningful per-column biome regions; this source simply returns
 * the first biome from its datapack pool (same behaviour as a FixedBiomeSource but with a registered
 * codec so a LevelStem JSON could reference {@code unlimitedspace:star_biome_source}). Mirrors
 * {@code AsteroidBiomeSource}.
 */
public final class StarBiomeSource extends BiomeSource {

    public static final MapCodec<StarBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(inst, StarBiomeSource::new));

    private final List<Holder<Biome>> biomes;

    public StarBiomeSource(List<Holder<Biome>> biomes) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("StarBiomeSource requires >=1 biome");
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
