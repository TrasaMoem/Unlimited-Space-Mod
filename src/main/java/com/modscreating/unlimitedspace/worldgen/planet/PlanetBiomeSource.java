package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Deterministic, seed-driven {@link BiomeSource} for procedural planet surfaces (R8).
 *
 * <p>R7 returned a single fixed biome for every planet. R8 derives the biome per column
 * from the planet's real biome seed (read at runtime from {@link PlanetSeedCache} plus the
 * stable slot stored in the datapack JSON) via the pure-domain {@link PlanetBiomeSelector}.
 * The datapack JSON lists the Minecraft biome pool in the fixed order of {@link PlanetBiome}
 * (OCEAN, HOT_DRY, COLD_DRY, WARM_WET); the selector maps each column to one archetype and
 * the source resolves it to a {@link Holder}.
 *
 * <p>Mirrors {@code worldgen.space.SpaceBiomeSource} so the two sources share the exact same
 * biome-selection logic. No display-name lookup; fully deterministic.
 */
public final class PlanetBiomeSource extends BiomeSource {

    /** Fixed mapping archetype -> index into the JSON biome pool. */
    private static final Map<PlanetBiome, Integer> ORDINAL =
            Collections.unmodifiableMap(Map.of(
                    PlanetBiome.OCEAN, 0, PlanetBiome.HOT_DRY, 1,
                    PlanetBiome.COLD_DRY, 2, PlanetBiome.WARM_WET, 3));

    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes),
            Codec.INT.fieldOf("system_index").forGetter(s -> s.systemIndex),
            Codec.INT.fieldOf("orbit_index").forGetter(s -> s.orbitIndex)
    ).apply(inst, PlanetBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final int systemIndex;
    private final int orbitIndex;

    public PlanetBiomeSource(List<Holder<Biome>> biomes, int systemIndex, int orbitIndex) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("PlanetBiomeSource requires >=1 biome");
        this.biomes = biomes;
        this.systemIndex = systemIndex;
        this.orbitIndex = orbitIndex;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    /** Real planet biome seed = Seeds.subsystem(Seeds.planet(Seeds.starSystem(galaxy(worldSeed), systemIndex), orbitIndex), "biome"). */
    long biomeSeed() {
        long worldSeed = PlanetSeedCache.get();
        long galaxy = Seeds.galaxy(worldSeed);
        long system = Seeds.starSystem(galaxy, systemIndex);
        long planet = Seeds.planet(system, orbitIndex);
        return Seeds.subsystem(planet, "biome");
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        PlanetBiome b = PlanetBiomeSelector.select(biomeSeed(), x, z);
        int idx = ORDINAL.getOrDefault(b, 0);
        return biomes.get(Math.min(idx, biomes.size() - 1));
    }
}