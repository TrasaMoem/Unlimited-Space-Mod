package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetBiomeIdentity;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * WORLDGEN V3.1 — the per-column, V3-driven {@link BiomeSource} of a procedural planet surface.
 *
 * <p>The dataflow is now the actual V3 pipeline:
 *
 * <pre>
 *   x, z
 *     -&gt; V3ColumnSampler.sampleColumn(x, z, scratch)   macro / elevation / climate / hydrology
 *     -&gt; BiomeMaskField.classify(scratch)               argmax of the continuous biome score
 *     -&gt; biome id                                      the elected BiomeCandidate
 *     -&gt; Holder&lt;Biome&gt;                                   a CACHED, immutable holder
 * </pre>
 *
 * <p><b>Why this replaced the one-biome-per-planet form.</b> R23 returned a single ambient
 * identity for the whole planet, so every column of a planet reported the same biome and the
 * visible biome carried no spatial information at all. The V3 classifier makes the biome a real
 * function of the local fields, so a river valley, a dune sea, a wetland and a snow line can all
 * appear inside one planet.
 *
 * <h2>No silent fallbacks (Task B)</h2>
 * <ul>
 *   <li>the candidate registry is never empty — {@link BiomeMaskField} fails loudly if it is;</li>
 *   <li>an elected biome whose registered holder is missing from the dimension pool is a COUNTED,
 *       LOGGED failure — the deliberate deterministic fallback is the planet's own ambient
 *       identity, and every occurrence is visible in {@link #missingBiomeEvents()};</li>
 *   <li>it is never "the first biome of the pool".</li>
 * </ul>
 *
 * <h2>Allocation</h2>
 * The registered holders are resolved ONCE into a per-candidate array, so
 * {@link #getNoiseBiome(int, int, int, Climate.Sampler)} performs a map lookup on the hot path and
 * allocates nothing. The per-column sample lives in a reusable scratch owned by a
 * {@link ThreadLocal}, because Minecraft queries biomes from several worker threads.
 *
 * <p>The macro GEOGRAPHY of a planet (a frozen expanse next to a volcanic belt) is carried by the
 * V3 field stack as a bounded SOFT affinity, never as a hard switch.
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
     * Number of times a per-column biome identity could not be resolved from the dimension pool.
     * The contract is {@code 0}; a non-zero value means the datapack pool is out of sync with the
     * shipped {@code PlanetBiomeIdentity#allPaths()} set and must be fixed, not papered over.
     */
    private static final AtomicInteger MISSING_BIOME_EVENTS = new AtomicInteger();

    /**
     * (planet, elected biome) pairs already reported to the log.
     *
     * <p>{@link #MISSING_BIOME_EVENTS} still counts EVERY miss, but the log line is written once
     * per pair: the miss is a per-COLUMN condition, so a per-column log turns a normal world load
     * into hundreds of thousands of identical lines.
     */
    private static final Set<String> REPORTED_MISSES = ConcurrentHashMap.newKeySet();

    private final List<Holder<Biome>> biomes;
    private final int systemIndex;
    private final int orbitIndex;
    /** ACT 2: moon slot (-1 = planet surface) - drives the moon-specific profile resolution. */
    private final int moonIndex;

    // Resource-location index of the JSON pool, built lazily once it is known.
    private Map<String, Holder<Biome>> biomeIndex;
    // Cached profile for the resolved planet (recomputed if the seed cache updates).
    private PlanetWorldgenProfile cachedProfile;
    private long cachedWorldSeed = Long.MIN_VALUE;

    // ---- V3.1 per-column state (all built once, reused per lookup) ----
    private volatile V3ColumnSampler columnSampler;
    /** Indexed by {@link BiomeCandidate} ordinal: the resolved registered holder, or null. */
    private volatile Map<String, Holder<Biome>> resolvedCandidates;
    private volatile Holder<Biome> ambientFallback;
    /**
     * The reusable per-column sample. Minecraft samples biomes from several worker threads, so the
     * scratch is thread-confined rather than shared — but it is still never allocated per query.
     */
    private static final ThreadLocal<WorldgenColumnSample> COLUMN_SCRATCH =
            ThreadLocal.withInitial(WorldgenColumnSample::new);

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

    /** Real planet biome profile derived from the world seed + the stable slot. */
    private PlanetWorldgenProfile profile() {
        long ws = PlanetSeedCache.get();
        if (cachedProfile == null || ws != cachedWorldSeed) {
            StarSystemId sysId = StarSystemId.of(systemIndex);
            PlanetId pid = PlanetId.of(sysId, orbitIndex);
            if (moonIndex >= 0) {
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

    /** Index the pool by {@code namespace:path} and by the bare path. */
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
     * The V3 column sampler for this planet, built ONCE per world seed.
     *
     * <p>It composes the same {@link TerrainShaper}, {@link ClimateField} and
     * {@link BiomeMaskField} the chunk generator uses, so the biome a column reports and the
     * terrain that column gets can never come from two different pipelines.
     */
    private V3ColumnSampler sampler() {
        long ws = PlanetSeedCache.get();
        V3ColumnSampler s = columnSampler;
        if (s == null || ws != cachedWorldSeed) {
            profile();
            PlanetWorldgenProfile prof = cachedProfile;
            if (prof == null || prof.geology() == null) return null;
            PlanetCharacter character = new PlanetCharacter(prof.geology().physical());
            ClimateField climate = new ClimateField(prof.geology().climate(), character,
                    new com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField(
                            prof.planetSeed()));
            // V3.2: the candidate registry is built ONCE per world seed from THIS planet's
            // admissibility, so an ecology that cannot exist here is never scored at all. The
            // per-column hot path is unchanged (argmax over an immutable list).
            BiomeMaskField biomeField = new BiomeMaskField(character, climate,
                    BiomeMaskField.candidatesFor(
                            com.modscreating.unlimitedspace.core.worldgen.admissibility
                                    .PlanetAdmissibility.of(prof.geology().physical(),
                                            prof.properties().surface()),
                            prof.properties().surface()));
            TerrainShaper shaper = TerrainShaper.create(
                    TerrainGenerators.from(prof), prof.planetSeed(), prof.geology().physical(),
                    prof.geology().provinces(), prof.geology().terrainSignature(),
                    prof.baseHeight(), prof.amplitude(),
                    null, prof.geology().geography(), prof.geology().climate());
            s = new V3ColumnSampler(shaper, climate, biomeField, prof.geology().provinces());
            columnSampler = s;
            resolvedCandidates = null;
        }
        return s;
    }

    /**
     * The registered {@link Holder} for an elected V3 biome id, cached across lookups.
     *
     * <p>A miss returns null: the caller then takes the DELIBERATE, DETERMINISTIC ambient fallback
     * and counts the miss. It is never a first-pool-entry guess.
     */
    private Holder<Biome> holderFor(String biomeId) {
        Map<String, Holder<Biome>> cache = resolvedCandidates;
        if (cache == null) {
            // Build the candidate -> holder index ONCE. The previous form rebuilt this map on every
            // MISS, which put a HashMap allocation + a full pool walk on the per-column hot path.
            Map<String, Holder<Biome>> m = new HashMap<>();
            Map<String, Holder<Biome>> index = biomeIndex();
            for (String path : PlanetBiomeIdentity.allPaths()) {
                Holder<Biome> h = index.get(PlanetBiomeIdentity.NAMESPACE + ":" + path);
                if (h == null) h = index.get(path);
                if (h != null) m.putIfAbsent(path, h);
            }
            // Bind every V3 candidate id to the ambience identity of ITS theme, so an elected
            // per-column biome resolves to a real registered holder instead of always missing.
            for (BiomeCandidate c : BiomeMaskField.defaultCandidates()) {
                PlanetColorTheme theme = PlanetBiomeIdentity.themeForCandidate(c.id());
                if (theme == null) continue;
                // Prefer the planet's own variant of the theme, then the other one: both are real
                // registered identities, and the choice stays deterministic for a given planet.
                Holder<Biome> h = m.get(PlanetBiomeIdentity.path(theme, ambientVariant()));
                if (h == null) h = m.get(PlanetBiomeIdentity.path(theme, 1 - ambientVariant()));
                if (h != null) m.putIfAbsent(c.id(), h);
            }
            cache = Collections.unmodifiableMap(m);
            resolvedCandidates = cache;
        }
        return cache.get(biomeId);
    }

    /**
     * The variant index ({@code 0} = {@code _a}, {@code 1} = {@code _b}) this planet's ambience
     * resolves to, derived from the same identity the ambient fallback uses.
     */
    private int ambientVariant() {
        PlanetWorldgenProfile prof = profile();
        long seed = prof == null ? cachedWorldSeed : prof.planetSeed();
        PlanetColorTheme theme = prof != null && prof.geology() != null
                ? prof.geology().colorTheme() : null;
        return PlanetBiomeIdentity.of(theme, seed).variant();
    }

    /**
     * WORLDGEN V3.1: the per-column biome lookup.
     *
     * <p>Spatial: the result is a function of {@code (x, z)} through the whole V3 field stack. It
     * is not one biome per planet, not one per macro province, not one per dimension and never a
     * first-pool-entry fallback. Allocation-free on the hot path.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        V3ColumnSampler columnSampler = sampler();
        if (columnSampler == null) {
            return ambientBiome();
        }
        WorldgenColumnSample column = COLUMN_SCRATCH.get();
        columnSampler.sampleColumn(x, z, column);
        BiomeCandidate elected = column.biome;
        if (elected == null) {
            // The classifier guarantees a non-null candidate; this is an explicit, counted failure
            // rather than a silent "first pool entry".
            MISSING_BIOME_EVENTS.incrementAndGet();
            LOGGER.error("[unlimitedspace][V3.1] biome classifier produced no candidate at x={} z={} "
                    + "(system {} orbit {}); falling back to the ambient identity", x, z,
                    systemIndex, orbitIndex);
            return ambientBiome();
        }
        Holder<Biome> resolved = holderFor(elected.id());
        if (resolved != null) return resolved;
        // The elected V3 biome has no registered identity in this pool. The pool is a
        // fixed ambience set, so this is a real, OBSERVABLE condition: counted, and logged
        // ONCE per (planet, biome) pair. Logging per column wrote hundreds of thousands of
        // identical lines per world load and grew debug.log into the hundreds of megabytes.
        MISSING_BIOME_EVENTS.incrementAndGet();
        if (REPORTED_MISSES.add(missKey(elected.id()))) {
            LOGGER.warn("[unlimitedspace][V3.1] elected biome {} has no registered holder in the pool "
                            + "of system {} orbit {}; using the planet ambient identity "
                            + "(further identical misses are counted, not logged)",
                    elected.id(), systemIndex, orbitIndex);
        }
        return ambientBiome();
    }

    /** Key that identifies one (planet, elected biome) miss for the one-shot log. */
    private String missKey(String biomeId) {
        return systemIndex + ":" + orbitIndex + ":" + moonIndex + ":" + biomeId;
    }

    /**
     * The planet's own ambient identity: its registered visual / sky / water identity.
     *
     * <p>It is the DELIBERATE, DETERMINISTIC fallback and the ambience of the planet, never the
     * per-column biome. It is used only for a pool miss or a classifier failure, both counted.
     */
    private Holder<Biome> ambientBiome() {
        Holder<Biome> cached = ambientFallback;
        if (cached != null) return cached;
        PlanetWorldgenProfile prof = profile();
        PlanetColorTheme theme = prof != null && prof.geology() != null
                ? prof.geology().colorTheme() : null;
        long seed = prof == null ? cachedWorldSeed : prof.planetSeed();
        PlanetBiomeIdentity identity = PlanetBiomeIdentity.of(theme, seed);
        Holder<Biome> exact = biomeIndex().get(
                PlanetBiomeIdentity.NAMESPACE + ":" + identity.path());
        if (exact != null) {
            ambientFallback = exact;
            return exact;
        }
        MISSING_BIOME_EVENTS.incrementAndGet();
        LOGGER.error("[unlimitedspace][V3.1] planet ambient biome {} missing from the dimension pool of "
                        + "system {} orbit {} (pool size {}). The datapack biome list must ship "
                        + "PlanetBiomeIdentity.allPaths().",
                PlanetBiomeIdentity.NAMESPACE + ":" + identity.path(), systemIndex, orbitIndex,
                biomes.size());
        Holder<Biome> sameTheme = biomeIndex().get(identity.otherVariantPath());
        if (sameTheme == null) {
            String prefix = "planet_" + PlanetBiomeIdentity.slug(identity.theme()) + "_";
            for (Map.Entry<String, Holder<Biome>> e : biomeIndex().entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    sameTheme = e.getValue();
                    break;
                }
            }
        }
        if (sameTheme == null) {
            throw new IllegalStateException(
                    "no planet biome of theme " + identity.theme() + " is shipped in the dimension pool; "
                            + "the pool must contain the PlanetBiomeIdentity set");
        }
        ambientFallback = sameTheme;
        return sameTheme;
    }

    /** Loud counter of identity / holder misses - tests and diagnostics assert this stays low. */
    public static int missingBiomeEvents() {
        return MISSING_BIOME_EVENTS.get();
    }
}
