package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Deterministic, seed-driven {@link BiomeSource} for procedural planet surfaces (R8).
 *
 * <p>R8 derives the per-column biome from the planet's real {@link PlanetWorldgenProfile}
 * (read at runtime from {@link PlanetSeedCache} plus the stable slot stored in the
 * datapack JSON) via the pure-domain {@link PlanetBiomeProfile#biomeAt(int,int)}.
 * The datapack JSON lists the Minecraft biome pool with {@code system_index}/
 * {@code orbit_index}; the source resolves which planet it is, computes the
 * profile's climate-aware 1..5 distinct presets, and picks the column biome from
 * those.
 *
 * <p>Matching is by {@link PlanetBiome#minecraftAlias()} against the JSON
 * {@code biomes} pool — never by display name. No per-column hash into a global
 * pool; each planet owns its regional preset set.
 */
public final class PlanetBiomeSource extends BiomeSource {

    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes),
            Codec.INT.fieldOf("system_index").forGetter(s -> s.systemIndex),
            Codec.INT.fieldOf("orbit_index").forGetter(s -> s.orbitIndex)
    ).apply(inst, PlanetBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private final int systemIndex;
    private final int orbitIndex;

    // Alias -> Holder lookup, built lazily once the JSON biome pool is known.
    private Map<String, Holder<Biome>> aliasIndex;
    // Cached profile for the resolved planet (recomputed if the seed cache updates).
    private PlanetWorldgenProfile cachedProfile;
    private long cachedWorldSeed = Long.MIN_VALUE;

    public PlanetBiomeSource(List<Holder<Biome>> biomes, int systemIndex, int orbitIndex) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("PlanetBiomeSource requires >=1 biome");
        this.biomes = biomes;
        this.systemIndex = systemIndex;
        this.orbitIndex = orbitIndex;
        this.aliasIndex = null;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

        /** Real planet biome seed = subsystem("biome") of the planet derived from the world seed + slot. */
    private PlanetWorldgenProfile profile() {
        long ws = PlanetSeedCache.get();
        if (cachedProfile == null || ws != cachedWorldSeed) {
            StarSystemId sysId = StarSystemId.of(systemIndex);
            PlanetId pid = PlanetId.of(sysId, orbitIndex);
            cachedProfile = PlanetWorldgenProfile.from(pid, ws);
            cachedWorldSeed = ws;
            buildAliasIndex();
        }
        return cachedProfile;
    }

    private void buildAliasIndex() {
        Map<String, Holder<Biome>> m = new HashMap<>();
        for (Holder<Biome> b : biomes) {
            ResourceLocation rl = b.unwrapKey().map(k -> k.location()).orElse(null);
            if (rl != null) m.put(rl.toString(), b);
        }
        aliasIndex = Collections.unmodifiableMap(m);
    }

    /** Resolve a {@link PlanetBiome} preset to one of the Minecraft biomes in the JSON pool. */
        private Holder<Biome> resolve(PlanetBiome b) {
        if (b == null) return biomes.get(0);
        String alias = b.minecraftAlias();
        Holder<Biome> h = aliasIndex != null ? aliasIndex.get(alias) : null;
        if (h != null) return h;
        // Deterministic fallback: first pool entry (keeps worldgen stable even if
        // the JSON pool does not list this planet's preferred alias).
        return biomes.get(0);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        PlanetBiomeProfile bp = profile().biome();
        PlanetBiome b = bp.biomeAt(x, z);
        return resolve(b);
    }
}