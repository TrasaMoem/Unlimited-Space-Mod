package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Multi-biome {@link BiomeSource} for the Phase-7 POC. It maps a list of Minecraft
 * biomes to {@link PlanetBiome} archetypes and, for each column, delegates the
 * selection to the deterministic {@link PlanetBiomeSelector} (planet seed + x/z).
 *
 * <p>The biome list in the datapack JSON MUST follow the fixed order of
 * {@link PlanetBiome} (OCEAN, HOT_DRY, COLD_DRY, WARM_WET). {@code biome_seed}
 * carries the planet-level deterministic seed; the selector is a pure function of
 * (seed, x, z), so results are stable across restarts and generation order.
 */
public final class SpaceBiomeSource extends BiomeSource {

    private static final Map<PlanetBiome, Integer> ORDINAL =
            java.util.Collections.unmodifiableMap(Map.of(
                    PlanetBiome.OCEAN, 0, PlanetBiome.HOT_DRY, 1,
                    PlanetBiome.COLD_DRY, 2, PlanetBiome.WARM_WET, 3));

    public static final MapCodec<SpaceBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes),
            Codec.LONG.fieldOf("biome_seed").forGetter(s -> s.biomeSeed)
    ).apply(inst, SpaceBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final long biomeSeed;

    public SpaceBiomeSource(List<Holder<Biome>> biomes, long biomeSeed) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("SpaceBiomeSource requires >=1 biome");
        this.biomes = biomes;
        this.biomeSeed = biomeSeed;
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
        PlanetBiome b = PlanetBiomeSelector.select(biomeSeed, x, z);
        int idx = ORDINAL.getOrDefault(b, 0);
        return biomes.get(Math.min(idx, biomes.size() - 1));
    }

    public long biomeSeed() {
        return biomeSeed;
    }
}