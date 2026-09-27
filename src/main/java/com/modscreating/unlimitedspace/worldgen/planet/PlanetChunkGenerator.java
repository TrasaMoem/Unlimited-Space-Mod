package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Custom Minecraft {@link ChunkGenerator} that renders terrain from the pure-domain
 * {@link TerrainGenerator} (built from {@link PlanetWorldgenProfile} / planet seed).
 *
 * <p>R8: ONE generator drives many procedural worlds. The codec stores only a stable planet
 * SLOT (system_index + orbit_index) and world bounds. ALL terrain/biome/block parameters are
 * DERIVED at runtime from the real Minecraft world seed plus the slot through the pure domain
 * pipeline (WorldSeed -&gt; Galaxy -&gt; Planet -&gt; PlanetWorldgenProfile), then translated here
 * to Minecraft blocks via {@link PlanetBlocks}.
 *
 * <p>R8 fixes vs R7: the static {@code sea_level} from the datapack JSON is a placeholder
 * (only correct for the proof planet); the real sea level is derived from the planet's
 * {@code waterCoverage} and terrain amplitude inside {@link PlanetWorldgenProfile}, and applied
 * here in {@link #ensureProfile()} so a 28%-coverage world is no longer a 50%-ocean. Surface
 * materials come from the seed-driven material palette via {@link PlanetBlocks#material}
 * (e.g. stone/deepslate vs packed_ice/blue_ice vs basalt/blackstone per planet).
 *
 * <pre>{@code
 * WorldSeed -&gt; Galaxy -&gt; Planet -&gt; PlanetWorldgenProfile -&gt; TerrainGenerator -&gt; this -&gt; Minecraft terrain
 * }</pre>
 */
public final class PlanetChunkGenerator extends ChunkGenerator {

    /**
     * R8 codec: a static slot + world bounds only. {@code world_seed} is OPTIONAL and normally
     * absent from the static datapack JSON; when missing, the real seed is supplied at runtime
     * by {@link PlanetSeedCache} (set on ServerStartedEvent). The biome source (separate codec)
     * carries the same slot so it can derive its biome seed from the cache.
     */
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.INT.fieldOf("system_index").forGetter(g -> g.systemIndex),
            Codec.INT.fieldOf("orbit_index").forGetter(g -> g.orbitIndex),
            Codec.INT.optionalFieldOf("moon_index", -1).forGetter(g -> g.moonIndex),
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.INT.fieldOf("sea_level").forGetter(g -> g.seaLevel),
            Codec.LONG.optionalFieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, PlanetChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final int systemIndex;
    private final int orbitIndex;
    /**
     * ACT 2: the moon slot this generator renders, or {@code -1} for a planet surface.
     * {@code >= 0} means this world is a MOON SURFACE and every worldgen parameter is resolved
     * from the moon's OWN {@code MoonWorldgenProfile} — never from the parent planet.
     */
    private final int moonIndex;
    private final int minY;
    private final int height;
    private int seaLevel;
    private int effectiveSeaLevel;
    private final Optional<Long> worldSeed;
    private volatile PlanetWorldgenProfile profile;
    // R16: geological subsystem (physical profile + provinces + material palette).
    private PlanetGeologyProfile geology;
    // Resolved-once per-province block states (province -> surface / subsurface).
    private final java.util.Map<GeologicalProvince, BlockState> planetSurfaceStates =
            new java.util.EnumMap<>(GeologicalProvince.class);
    private final java.util.Map<GeologicalProvince, BlockState> planetSubsurfaceStates =
            new java.util.EnumMap<>(GeologicalProvince.class);
    // R16: per-province fluid BlockStates, resolved ONCE per planet (fluid ecology → adapter).
    private final java.util.Map<GeologicalProvince, BlockState> planetFluidStates =
            new java.util.EnumMap<>(GeologicalProvince.class);
    // R20: coherent material zones (province -> zone index -> surface BlockState).
    private final java.util.Map<GeologicalProvince, BlockState[]> planetZoneSurfaceStates =
            new java.util.EnumMap<>(GeologicalProvince.class);

    /**
     * ACT 6 section 5: the planet's own SAND-family surface block, resolved once.
     *
     * <p>The dune morphology now produces a real, readable sand relief, so a column that genuinely
     * IS a dune must also carry a sand-family material. The block is NOT hardcoded: it is the
     * EXISTING {@code SEDIMENT} ("sand / fine sediment") role of the planet's own geology palette,
     * which the material catalog already resolved to a sand-family material for this world, and it
     * is only used when that material really is granular - otherwise the column keeps its normal
     * themed material. This is the single existing sand override in production; no second
     * material system is introduced.
     */
    private BlockState duneSandState;
    /**
     * ACT 6 section 5: minimum landform strength at which a column counts as a real dune core and
     * therefore takes the sand material. Below it the dune field is just background relief and the
     * themed surface material is kept.
     */
    private static final double DUNE_MATERIAL_STRENGTH = 0.22;

    private BlockState deepState;
    private TerrainGenerator terrain;
    // R17: multi-scale terrain shaper (province morphology + craters/canyons/ridges).
    private com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper shaper;
    private BlockState surface;
    private BlockState subsurface;
    private BlockState fluid;
    private boolean hasWater;
    // PHASE 3: planet-scale water phase, resolved ONCE (drives fluid blocks + hasWater gate).
    private WaterPhaseModel.Phase waterPhase = WaterPhaseModel.Phase.LIQUID;
    // PHASE 8: climate-dependent surface strata depths, resolved ONCE per planet.
    private com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceStrata strata =
            new com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceStrata(2, 8);

    public PlanetChunkGenerator(BiomeSource biomeSource, int systemIndex, int orbitIndex,
                                int minY, int height, int seaLevel, Optional<Long> worldSeed) {
        this(biomeSource, systemIndex, orbitIndex, -1, minY, height, seaLevel, worldSeed);
    }

    /** ACT 2 canonical constructor: {@code moonIndex >= 0} renders a MOON surface world. */
    public PlanetChunkGenerator(BiomeSource biomeSource, int systemIndex, int orbitIndex,
                                int moonIndex, int minY, int height, int seaLevel,
                                Optional<Long> worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.systemIndex = systemIndex;
        this.orbitIndex = orbitIndex;
        this.moonIndex = moonIndex;
        this.minY = minY;
        this.height = height;
        this.seaLevel = seaLevel;
        this.effectiveSeaLevel = seaLevel;
        this.worldSeed = worldSeed;
    }

    /** Effective world seed: JSON value if present, else the runtime cache. */
    long effectiveWorldSeed() {
        return worldSeed.isPresent() ? worldSeed.get() : PlanetSeedCache.get();
    }

    private void ensureProfile() {
        if (profile != null) return;
        synchronized (this) {
            if (profile == null) {
                PlanetId pid = PlanetId.of(StarSystemId.of(systemIndex), orbitIndex);
                PlanetWorldgenProfile p;
                if (moonIndex >= 0) {
                    // ACT 2: MOON SURFACE — the profile is resolved from the moon's OWN
                    // MoonSeed -> MoonProperties -> MoonThermalModel chain. The parent planet
                    // is only the ecological eligibility prerequisite, never a data source.
                    p = com.modscreating.unlimitedspace.core.worldgen.MoonWorldgenProfile
                            .view(com.modscreating.unlimitedspace.core.planets.MoonId.of(pid, moonIndex),
                                    effectiveWorldSeed())
                            .worldgen();
                } else {
                    // ACT 2: canonical SYSTEM-AWARE planet path (actual habitability feeds the
                    // vegetation / structure / mob gates consumed by the feature stage).
                    p = PlanetWorldgenProfile.from(pid, effectiveWorldSeed());
                }
                profile = p;
                // R8 hydrology fix: real sea level comes from waterCoverage + amplitude, not the
                // static JSON placeholder (which was only right for the proof planet).
                effectiveSeaLevel = (int) Math.round(p.seaLevel());
                seaLevel = effectiveSeaLevel;
                terrain = TerrainGenerators.from(p);
                shaper = com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper.create(
                        terrain, p.planetSeed(), p.geology().physical(), p.geology().provinces(),
                        p.geology().terrainSignature(), p.baseHeight(), p.amplitude(),
                        null, null, p.geology().climate());
                // R16 planet-diversity: build the geological palette ONCE (deterministic) and
                // resolve every province's surface/subsurface block up front — the per-column
                // generator loop then only does map lookups, never registry/derivation work.
                geology = p.geology();
                if (geology != null) {
                    for (GeologicalProvince province : geology.provinces().provinces()) {
                        planetSurfaceStates.put(province, PlanetBlocks.material(
                                geology.palette().surfaceFor(province)));
                        planetSubsurfaceStates.put(province, PlanetBlocks.material(
                                geology.palette().secondarySurface() != null
                                        ? geology.palette().secondarySurface()
                                        : geology.palette().deepStone()));
                    }
                    if (geology.palette().deepStone() != null) {
                        deepState = PlanetBlocks.material(geology.palette().deepStone());
                    }
                    // R20: coherent MATERIAL ZONES — large regions of primary/secondary rock
                    // with rare accents, resolved ONCE per planet (pure map lookups per column).
                    // R23 (M-1): zone SLOTS come from the themed palette; the "geologic" slot
                    // carries mountain/thermal host rock ONLY where that context exists (else it
                    // falls back to the province's own primary - never "mountain everywhere").
                    // R23 (I): slot 4 is the MICRO-FACIES texture - a second PRIMARY resolved from
                    // the same theme, used only over provinces that inherit the planet primary.
                    BlockState secondaryGeology = PlanetBlocks.material(
                            geology.palette().secondarySurface() != null
                                    ? geology.palette().secondarySurface()
                                    : geology.palette().primarySurface());
                    com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial microMat =
                            geology.palette().primaryVariant();
                    BlockState microVariant = microMat != null
                            ? PlanetBlocks.material(microMat) : null;
                    for (GeologicalProvince province : geology.provinces().provinces()) {
                        BlockState primary = planetSurfaceStates.get(province);
                        BlockState mountain = geology.palette().mountain() != null
                                ? PlanetBlocks.material(geology.palette().mountain()) : secondaryGeology;
                        BlockState accent = geology.palette().accentFor(province) != null
                                ? PlanetBlocks.material(geology.palette().accentFor(province)) : primary;
                        BlockState crystal = province == GeologicalProvince.CRYSTAL
                                && geology.palette().crystal() != null
                                ? PlanetBlocks.material(geology.palette().crystal()) : mountain;
                        BlockState geologic = switch (province) {
                            case MOUNTAIN, VOLCANIC, GEOTHERMAL, CRYSTAL -> crystal;
                            default -> primary;
                        };
                        boolean inheritsPrimary = geology.palette().primarySurface() != null
                                && geology.palette().surfaceFor(province) != null
                                && geology.palette().surfaceFor(province).id()
                                        .equals(geology.palette().primarySurface().id());
                        BlockState micro = inheritsPrimary && microVariant != null
                                ? microVariant : primary;
                        planetZoneSurfaceStates.put(province, new BlockState[]{
                                primary, secondaryGeology, geologic, accent, micro});
                    }
                    // ACT 6 section 5: resolve the DUNE surface material ONCE.
                    //
                    // It is the planet's own SEDIMENT (sand / fine sediment) role from the geology
                    // palette - the single existing sand material of this world - and it is accepted only
                    // when the catalog really resolved it to a GRANULAR (sand-family) material, so a
                    // world whose sediment role is a rock keeps its own rock instead of being forced
                    // into a hardcoded minecraft:sand. No second material system, no hardcoded block.
                    if (geology.palette().sediment() != null
                            && geology.palette().sediment().family() != null
                            && geology.palette().sediment().family().superFamily()
                                    == com.modscreating.unlimitedspace.core.worldgen.materials
                                            .MaterialFamily.MaterialSuperFamily.GRANULAR) {
                        BlockState sand = PlanetBlocks.material(geology.palette().sediment());
                        if (sand != null && !sand.isAir()) duneSandState = sand;
                    }
                }
                                surface = PlanetBlocks.material(p.material().surface());
                subsurface = PlanetBlocks.material(p.material().subsurface());
                fluid = PlanetBlocks.fluid(p.fluid() == FluidProfile.WATER ? FluidProfile.WATER : FluidProfile.NONE);
                hasWater = p.hasWater();
                waterPhase = p.geology() != null
                        ? WaterPhaseModel.ofProfile(p.geology().physical())
                        : WaterPhaseModel.Phase.LIQUID;
                if (waterPhase.isDry()) hasWater = false;
                // R19 fluid ecology: the geology subsystem carries the planet's fluid identity
                // (global + per-province overrides, cached per planet). The Minecraft adapter
                // resolves each province's family to a registry-safe BlockState exactly once —
                // the per-column hot path below only does map lookups.
                if (geology != null && geology.fluidEcology() != null) {
                    // PHASE 3: planet-scale water phase from the REAL Kelvin temperature —
                    // SOLID (frozen seas), LIQUID, VAPOR (boiling worlds) or NONE (dry).
                    waterPhase = WaterPhaseModel.ofProfile(geology.physical());
                    // Ocean ecology gate: a world whose physical profile cannot host surface
                    // liquid stays dry, no matter what the legacy water profile says.
                    if (geology.fluidEcology().global() == com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily.NONE
                            || waterPhase.isDry()
                            || (!com.modscreating.unlimitedspace.core.worldgen.fluids.OceanEcology
                                    .of(geology.physical()).hasLiquid()
                                && !waterPhase.isSolid())) {
                        hasWater = false;
                    }
                    for (GeologicalProvince province : geology.provinces().provinces()) {
                        // PHASE 3.7: rare GEOTHERMAL/VOLCANIC pockets on a frozen world may
                        // stay liquid (brine), geology- and temperature-aware.
                        WaterPhaseModel.Phase provincePhase = WaterPhaseModel.geothermalPocketPhase(
                                waterPhase, province, geology.physical().geothermalFlux());
                        planetFluidStates.put(province, PlanetFluids.blockFor(
                                geology.fluidEcology().familyAt(province), provincePhase));
                    }
                }
                // PHASE 8: climate-dependent surface strata depths (surface 1–4,
                // subsurface 4–12, deep below), resolved ONCE per planet.
                if (geology != null) {
                    strata = com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceStrata
                            .of(p.properties().seed().value(), geology.physical());
                }
            }
        }
    }

    /** Programmatic construction straight from a domain profile (tests / debug). */
    public static PlanetChunkGenerator from(BiomeSource biomeSource, PlanetWorldgenProfile profile) {
        PlanetChunkGenerator g = new PlanetChunkGenerator(biomeSource, -1, -1, -64, 384,
                (int) Math.round(profile.seaLevel()), Optional.empty());
        g.profile = profile;
        int sea = (int) Math.round(profile.seaLevel());
        g.effectiveSeaLevel = sea;
        g.seaLevel = sea;
        g.terrain = TerrainGenerators.from(profile);
                g.surface = PlanetBlocks.material(profile.material().surface());
        g.subsurface = PlanetBlocks.material(profile.material().subsurface());
        g.fluid = PlanetBlocks.fluid(profile.fluid() == FluidProfile.WATER ? FluidProfile.WATER : FluidProfile.NONE);
        g.hasWater = profile.hasWater();
        g.waterPhase = profile.geology() != null
                ? WaterPhaseModel.ofProfile(profile.geology().physical())
                : WaterPhaseModel.Phase.LIQUID;
        if (g.waterPhase.isDry()) g.hasWater = false;
        return g;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * R16: province classification for a column. Normalizes the column height into the
     * terrain span and delegates to the deterministic province map (O(1), no allocation).
     */
    /** R16: read-only profile access for the feature stage. */
    PlanetWorldgenProfile profileProfile() {
        return profile;
    }

    /** R16: read-only geological subsystem access for the feature stage. */
    PlanetGeologyProfile geology() {
        return geology;
    }

    /** PHASE 3: read-only planet-scale water phase for the feature stage. */
    WaterPhaseModel.Phase waterPhase() {
        return waterPhase;
    }

    GeologicalProvince provinceAt(int x, int z, ChunkAccess chunk) {
        GeologicalProvinceContext ctx = columnContext(x, z, chunk);
        return ctx == null ? GeologicalProvince.PLAINS : ctx.province();
    }

    /**
     * R18: unified per-column province context — the single source of truth for terrain shaping,
     * material selection, resource/vegetation/structure placement and the F3 debug line.
     *
     * <p>ACT 3 (P1.1/P1.2): the LABEL is the canonical 900-block REGION-AWARE
     * {@code ProvinceField} province; the macro region at the column filters the geology so a
     * province can never contradict the ecology, and the 192-block layer contributes only its
     * continuous confidence (border fade / intensity).
     */
    GeologicalProvinceContext columnContext(int x, int z, ChunkAccess chunk) {
        return columnContext(x, z, chunk, regionContextAt(x, z));
    }

    /** ACT 3: canonical context with a pre-computed macro context (avoids double evaluation). */
    GeologicalProvinceContext columnContext(int x, int z, ChunkAccess chunk,
                                            com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap.Context regionCtx) {
        PlanetGeologyProfile g = geology;
        if (g == null) return null;
        int h = surfaceHeight(x, z, chunk);
        double span = Math.max(1.0, 2.0 * profile.amplitude());
        double elevation01 = Math.max(0.0, Math.min(1.0, (h - (profile.baseHeight() - profile.amplitude())) / span));
        com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region =
                regionCtx == null ? null : regionCtx.region();
        return g.provinces().canonicalContextAt(x, z, elevation01, region);
    }

    /**
     * ACT 3 (P0.3/P1.2): the macro region context at a column, climate-aware — inside a macro
     * transition band a grossly incompatible primary region yields to a compatible neighbour;
     * the region core keeps its identity.
     */
    com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap.Context regionContextAt(int x, int z) {
        PlanetGeologyProfile g = geology;
        if (g == null || g.regions() == null) return null;
        double localTemp = g.climate() == null ? Double.NaN : g.climate().temperatureAt(x, z);
        return g.regions().contextAt(x, z, localTemp);
    }

    int surfaceHeight(int x, int z, LevelHeightAccessor level) {
        ensureProfile();
        int h = shaper != null ? shaper.surfaceHeight(x, z) : (int) Math.round(terrain.height(x, z));
        return Mth.clamp(h, this.minY, level.getMaxBuildHeight() - 1);
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        ensureProfile();
        return effectiveSeaLevel;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return surfaceHeight(x, z, level);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int h = surfaceHeight(x, z, level);
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        int sea = effectiveSeaLevel;
        for (int y = minY; y <= h; y++) {
            states[y - minY] = (y == h) ? surface : subsurface;
        }
        int top = Math.min(sea, level.getMaxBuildHeight() - 1);
        if (hasWater) {
            for (int y = h + 1; y <= top; y++) {
                states[y - minY] = fluid;
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        ensureProfile();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minBX = chunk.getPos().getMinBlockX();
        int minBZ = chunk.getPos().getMinBlockZ();
        int maxY = chunk.getMaxBuildHeight() - 1;
        int sea = effectiveSeaLevel;

        for (int x = 0; x < 16; x++) {
            int bx = minBX + x;
            for (int z = 0; z < 16; z++) {
                int bz = minBZ + z;
                int h = surfaceHeight(bx, bz, chunk);
                LevelChunkSection section = null;
                int sectionIndex = -1;

                // ACT 3 (P1.2) AUTHORITY ORDER:
                //   regionCtx = macro region (climate-aware)      -> ecology/geology filter
                //   mediumProvince = 900 region-aware province    -> THE canonical identity
                //   192 micro layer = confidence/intensity only   -> never a major identity
                com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap.Context regionCtx =
                        regionContextAt(bx, bz);
                GeologicalProvinceContext columnCtx = columnContext(bx, bz, chunk, regionCtx);
                GeologicalProvince province = columnCtx == null
                        ? GeologicalProvince.PLAINS : columnCtx.province();
                BlockState[] zoneStates = planetZoneSurfaceStates.get(province);
                BlockState zoneSurface;
                if (zoneStates != null) {
                    // R23 (M-1) + ACT 3 (P4): contextual role selection - THEME x MEDIUM PROVINCE
                    // x SURFACE CATEGORY, with the macro context as a bounded boundary input.
                    int zone = com.modscreating.unlimitedspace.core.worldgen.materials
                            .PlanetMaterialRoleSelector.zoneAt(
                                    geology != null ? geology.colorTheme() : null, province,
                                    surfaceCategoryAt(bx, bz, h, province), profile.materialSeed(),
                                    bx, bz, regionCtx);
                    zoneSurface = zoneStates[Math.min(zone, zoneStates.length - 1)];
                    // R23 (I): MICRO-FACIES - same-theme texture inside the dominant slot only.
                    if (zone == 0 && zoneStates.length > 4 && zoneStates[4] != zoneStates[0]
                            && com.modscreating.unlimitedspace.core.worldgen.materials.MaterialZoneMap
                                    .microFacies01(profile.materialSeed(), bx, bz) < 0.35) {
                        zoneSurface = zoneStates[4];
                    }
                    // R23 (E-1): the local SUB-BIOME may retint the SECONDARY slot with a
                    // theme-compatible ecology role (soil / sediment / crystal), never the theme.
                    if (zone == 1 && geology != null) {
                        SubBiome sub = geology.subBiomeAt(bx, bz, elevation01(bx, bz, h));
                        com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial eco =
                                sub.preferredRole() == null
                                        ? null : geology.palette().materialFor(sub.preferredRole());
                        if (eco != null && geology.colorTheme() != null && geology.colorTheme().admits(
                                com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog
                                        .visualRoleOf(eco))) {
                            zoneSurface = PlanetBlocks.material(eco);
                        }
                    }
                } else {
                    zoneSurface = planetSurfaceStates.getOrDefault(province, this.surface);
                }
                BlockState columnSubsurface = planetSubsurfaceStates.getOrDefault(province, this.subsurface);
                BlockState columnDeep = deepState != null ? deepState : columnSubsurface;

                // ACT 6 section 5: DUNE MATERIAL. A column that is genuinely a dune core (the
                // landform identity is DUNE and the dune field is strong there) takes the
                // planet's own sand-family block, so the visible sand relief and the visible sand
                // material agree. Interdune ground and every other landform keep their themed
                // material unchanged - this is a narrow, landform-gated override, not a second
                // material system.
                if (duneSandState != null
                        && shaper != null
                        && shaper.landformIdentity(bx, bz)
                                == com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity.DUNE
                        && shaper.landformStrength(bx, bz) >= DUNE_MATERIAL_STRENGTH) {
                    zoneSurface = duneSandState;
                    columnSubsurface = duneSandState;
                }

                // PHASE 6.9: landform exposure — deep rock is exposed at the surface inside
                // ravine / fissure / sinkhole cuts (ravine → deep rock, etc.).
                double exposure = shaper != null ? shaper.landformExposure(bx, bz) : 0.0;
                BlockState topState = exposure > 0.60 ? columnDeep : zoneSurface;
                BlockState underState = exposure > 0.80 ? columnDeep : columnSubsurface;

                // PHASE 8: climate-dependent strata depths (surface 1–4, subsurface 4–12).
                int surfDepth = strata.surfaceDepth();
                int coverDepth = strata.coverDepth();

                for (int y = minY; y <= h; y++) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIndex) {
                        section = chunk.getSection(idx);
                        sectionIndex = idx;
                    }
                    // PHASE 8: layered geology — surface cover, then subsurface, then deep.
                    int depth = h - y;
                    BlockState state;
                    if (depth < surfDepth) {
                        state = topState;
                    } else if (depth < coverDepth) {
                        state = underState;
                    } else {
                        state = columnDeep;
                    }
                    int ly = y & 15;
                    section.setBlockState(x, ly, z, state, false);
                    worldSurface.update(x, y, z, state);
                    oceanFloor.update(x, y, z, state);
                }

                if (hasWater) {
                    // R19: the column's fluid follows its PROVINCE — a cold+wet planet with
                    // global cryogenic seas can host warm mineral-brine geothermal basins or
                    // water-like lake basins. Resolved state, no registry work per column.
                    BlockState columnFluid = planetFluidStates.getOrDefault(province, this.fluid);
                    if (columnFluid != null && !columnFluid.isAir()) {
                        for (int y = h + 1; y <= Math.min(sea, maxY); y++) {
                            LevelChunkSection waterSection = chunk.getSection(chunk.getSectionIndex(y));
                            waterSection.setBlockState(x, y & 15, z, columnFluid, false);
                            worldSurface.update(x, y, z, columnFluid);
                        }
                    }
                }
            }
        }

        // R16: connect the Phase 8/9 selectors to dynamic planet generation.
        PlanetFeaturePlacer.applyOres(this, chunk, minBX, minBZ, columnContext(minBX + 8, minBZ + 8, chunk));
        PlanetFeaturePlacer.applyVegetation(this, chunk, minBX, minBZ, sea);
        PlanetFeaturePlacer.applyStructure(this, chunk, minBX, minBZ, sea);
        // R19 ambient life: fluid formations (lava channels / geothermal pools) + vents.
        PlanetFeaturePlacer.applyFluidFeatures(this, chunk, minBX, minBZ, sea);
        // ACT 6 section 8: a canonically HABITABLE world is guaranteed reachable liquid water.
        PlanetFeaturePlacer.applyHabitableWater(this, chunk, minBX, minBZ, sea);

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        // Surface layer is already produced in fillFromNoise.
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No caves in the POC.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No natural mob spawning in the POC.
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        // ACT 2.3 -> ACT 6: Unlimited Space contributes NOTHING to the vanilla F3 overlay.
        //
        // The mod used to append an "Unlimited Space:" section here (identity, temperature, climate,
        // habitability, biome/province and, before that, the full development dump: pattern internals,
        // material weights, strata, terrain budgets, raw noise values, seed domains). F3 belongs to
        // the player and to vanilla's own fields; a mod-owned multi-line section only crowds it out.
        //
        // Everything that was printed here is still available where it actually belongs:
        //   - the explicit HABITABLE / STERILE verdict and the bio-potential number -> the flight /
        //     navigation panel (com.modscreating.unlimitedspace.client.nav.RocketControlNavigationScreen,
        //     which reads the canonical WorldStatusText habitability source), and
        //   - every development diagnostic (profile dumps, material weights, terrain budgets,
        //     raw field values, seed domains) -> TerrainDiagnostics, MapPreview, GalaxyCommands
        //     and the unit tests.
        //
        // Deliberately no `info.add(...)` here: the method must stay a no-op so vanilla F3 renders
        // exactly its own fields. Do not reintroduce a replacement block.
    }

    /** R18: a representative surface block id for the debug screen. */
    private String favoriteSurfaceMaterial() {
        PlanetGeologyProfile g = geology;
        if (g == null) return "?";
        var pm = g.palette().primarySurface();
        return pm == null ? "?" : pm.blockId();
    }

    /** R18: brief province feature flags for the debug screen. */
    private String featureFlags(String material) {
        StringBuilder sb = new StringBuilder();
        if (material != null) {
            sb.append(material.contains("sulfur") || material.contains("ember") || material.contains("basalt") ? "VENT " : "");
            sb.append(material.contains("crystal") || material.contains("prism") ? "CRYSTAL " : "");
            sb.append(material.contains("impact") || material.contains("rust") ? "IMPACT " : "");
        }
        return sb.length() == 0 ? "-" : sb.toString().trim();
    }

    /** R23: normalized column elevation in [0,1] (from the terrain compositor). */
    private double elevation01(int bx, int bz, int h) {
        double span = Math.max(1.0, 2.0 * profile.amplitude());
        return Math.max(0.0, Math.min(1.0,
                (h - (profile.baseHeight() - profile.amplitude())) / span));
    }

    /**
     * R23 (E-1/M-1): the column's {@link com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory}
     * - now a real material-selection input, not a diagnostics-only label.
     */
    private com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory surfaceCategoryAt(
            int bx, int bz, int h, GeologicalProvince province) {
        if (geology == null) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
        }
        return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategorySelector.classify(
                geology.physical(),
                shaper != null ? shaper.archetype().primary() : null,
                province,
                geology.climate() != null ? geology.climate().archetype() : null,
                geology.relief() != null ? geology.relief().archetype() : null,
                elevation01(bx, bz, h),
                // ACT 3 (P3.2): the canonical water phase gates liquid-dependent categories;
                // the existing geothermal-pocket exception is preserved (brine basins survive
                // on frozen worlds inside volcanic / geothermal provinces).
                phaseForColumn(province),
                // ACT 4: bounded landform reaction (dune -> sandy, crystal ridge -> crystalline,
                // caldera -> volcanic, crater -> dusty, slump -> sediment). Never overrides the
                // planet's frozen/volcanic/crystalline/saline identity or the water phase.
                shaper == null ? null : shaper.landformIdentity(bx, bz),
                shaper == null ? 0.0 : shaper.landformStrength(bx, bz));
    }

    /**
     * ACT 3 (P3.2): local water phase of a column — the canonical planet phase plus the
     * EXISTING {@link WaterPhaseModel#geothermalPocketPhase} exception. No new phase logic.
     */
    private WaterPhaseModel.Phase phaseForColumn(GeologicalProvince province) {
        WaterPhaseModel.Phase global = waterPhase;
        if (global == null || geology == null || geology.physical() == null) return global;
        return WaterPhaseModel.geothermalPocketPhase(global, province,
                geology.physical().geothermalFlux());
    }
}
