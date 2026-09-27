package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetBiomeIdentity;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Deterministic, seed-driven {@link BiomeSource} for procedural planet surfaces (R8, reworked by
 * R23/B-1).
 *
 * <p>The source derives the planet from its real {@link PlanetWorldgenProfile} (read at runtime
 * from {@link PlanetSeedCache} plus the stable slot stored in the datapack JSON).
 *
 * <p><b>R23 (B-1):</b> the REGISTERED biome is the planet's global visual / ambient identity
 * (sky, fog, water, grass, foliage) and it is generated from the planet's {@link PlanetColorTheme}
 * - the SAME theme that drives the planetary material palette. The identity is a deterministic
 * {@link PlanetBiomeIdentity} ({@code planet_<theme>_<a|b>}) and it resolves EXACTLY against the
 * dimension's biome pool:
 *
 * <ul>
 *   <li>no alias guessing, no legacy {@code PlanetBiome} preset switch in this path;</li>
 *   <li>no silent "first biome of the pool" fallback - a miss is a LOUD, counted error;</li>
 *   <li>{@code fallback share = 0} is an asserted invariant (see {@link #missingBiomeEvents()}).</li>
 * </ul>
 *
 * <p>The macro GEOGRAPHY of a planet (a frozen expanse next to a volcanic belt) is NOT carried by
 * this source - it lives in the pure-domain {@code BiomeRegionMap} and is expressed through
 * materials, terrain and features. The registered biome is the planetary ambience.
 *
 * <p>Matching is by the exact {@code ResourceLocation} of the theme identity against the JSON
 * biome pool - never by display name.
 */
public final class PlanetBiomeSource extends BiomeSource {

    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.list(Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes),
            Codec.INT.fieldOf("system_index").forGetter(s -> s.systemIndex),
            Codec.INT.fieldOf("orbit_index").forGetter(s -> s.orbitIndex),
            Codec.INT.optionalFieldOf("moon_index", -1).forGetter(s -> s.moonIndex)
    ).apply(inst, PlanetBiomeSource::new));

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * R23 (B-1): number of times the exact theme biome was NOT found in a dimension pool. The
     * contract is {@code 0}; a non-zero value means a datapack pool is out of sync with the
     * shipped {@code PlanetBiomeIdentity#allPaths()} set and must be fixed, not papered over.
     */
    private static final AtomicInteger MISSING_BIOME_EVENTS = new AtomicInteger();

    private final List<Holder<Biome>> biomes;
    private final int systemIndex;
    private final int orbitIndex;
    /** ACT 2: moon slot (-1 = planet surface) — drives the moon-specific profile resolution. */
    private final int moonIndex;

    // Resource-location index of the JSON pool (R23/B-1), built lazily once it is known.
    private Map<String, Holder<Biome>> biomeIndex;
    // Cached profile for the resolved planet (recomputed if the seed cache updates).
    private PlanetWorldgenProfile cachedProfile;
    private long cachedWorldSeed = Long.MIN_VALUE;

    public PlanetBiomeSource(List<Holder<Biome>> biomes, int systemIndex, int orbitIndex) {
        this(biomes, systemIndex, orbitIndex, -1);
    }

    /** ACT 2 canonical constructor: {@code moonIndex >= 0} resolves a MOON surface identity. */
    public PlanetBiomeSource(List<Holder<Biome>> biomes, int systemIndex, int orbitIndex,
                             int moonIndex) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("PlanetBiomeSource requires >=1 biome");
        this.biomes = biomes;
        this.systemIndex = systemIndex;
        this.orbitIndex = orbitIndex;
        this.moonIndex = moonIndex;
        this.biomeIndex = null;
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
            if (moonIndex >= 0) {
                // ACT 2: the registered biome identity comes from the MOON's own profile —
                // its theme is derived from the moon's OWN thermal state, never the parent's.
                cachedProfile = com.modscreating.unlimitedspace.core.worldgen.MoonWorldgenProfile
                        .view(com.modscreating.unlimitedspace.core.planets.MoonId.of(pid, moonIndex), ws)
                        .worldgen();
            } else {
                cachedProfile = PlanetWorldgenProfile.from(pid, ws);
            }
            cachedWorldSeed = ws;
            buildBiomeIndex();
        }
        return cachedProfile;
    }

    /** R23 (B-1): index the pool by {@code namespace:path} and by the bare path. */
    private void buildBiomeIndex() {
        Map<String, Holder<Biome>> m = new HashMap<>();
        for (Holder<Biome> b : biomes) {
            ResourceLocation rl = b.unwrapKey().map(k -> k.location()).orElse(null);
            if (rl == null) continue;
            m.put(rl.getNamespace() + ":" + rl.getPath(), b);
            m.putIfAbsent(rl.getPath(), b);
        }
        biomeIndex = Collections.unmodifiableMap(m);
    }

    private Map<String, Holder<Biome>> biomeIndex() {
        if (biomeIndex == null) buildBiomeIndex();
        return biomeIndex;
    }

    /**
     * R23 (B-1): the planet's registered biome, resolved EXACTLY from its theme identity.
     *
     * <p>On a miss this is a loud, counted failure - never the silent "first pool entry": the
     * same-theme sibling variant is used only so the world still loads, the miss is logged at
     * ERROR and counted in {@link #missingBiomeEvents()} for the tests to assert zero.
     */
    private Holder<Biome> resolvePlanetBiome() {
        PlanetWorldgenProfile prof = profile();
        PlanetColorTheme theme = prof != null && prof.geology() != null
                ? prof.geology().colorTheme() : null;
        long seed = prof == null ? cachedWorldSeed : prof.planetSeed();
        PlanetBiomeIdentity identity = PlanetBiomeIdentity.of(theme, seed);
        Holder<Biome> exact = biomeIndex().get(
                PlanetBiomeIdentity.NAMESPACE + ":" + identity.path());
        if (exact != null) return exact;
        MISSING_BIOME_EVENTS.incrementAndGet();
        LOGGER.error("[unlimitedspace][R23] planet biome {} missing from the dimension pool of "
                        + "system {} orbit {} (pool size {}). The datapack biome list must ship "
                        + "PlanetBiomeIdentity.allPaths().",
                PlanetBiomeIdentity.NAMESPACE + ":" + identity.path(), systemIndex, orbitIndex,
                biomes.size());
        Holder<Biome> sameTheme = biomeIndex().get(identity.otherVariantPath());
        if (sameTheme != null) return sameTheme;
        String prefix = "planet_" + PlanetBiomeIdentity.slug(identity.theme()) + "_";
        for (Map.Entry<String, Holder<Biome>> e : biomeIndex().entrySet()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        throw new IllegalStateException(
                "no planet biome of theme " + identity.theme() + " is shipped in the dimension pool; "
                        + "the pool must contain the PlanetBiomeIdentity set");
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // R23 (B-1): one global ambient identity per planet. Macro geography is expressed by
        // materials/terrain/features on top of BiomeRegionMap, not by biome switching.
        return resolvePlanetBiome();
    }

    /** R23 (B-1): loud counter of identity misses - tests and diagnostics assert this stays 0. */
    public static int missingBiomeEvents() {
        return MISSING_BIOME_EVENTS.get();
    }
}