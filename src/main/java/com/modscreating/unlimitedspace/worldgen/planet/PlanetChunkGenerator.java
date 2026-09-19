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
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignature;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
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
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.INT.fieldOf("sea_level").forGetter(g -> g.seaLevel),
            Codec.LONG.optionalFieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, PlanetChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final int systemIndex;
    private final int orbitIndex;
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

    private BlockState deepState;
    private TerrainGenerator terrain;
    // R17: multi-scale terrain shaper (province morphology + craters/canyons/ridges).
    private com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper shaper;
    private BlockState surface;
    private BlockState subsurface;
    private BlockState fluid;
    private boolean hasWater;

    public PlanetChunkGenerator(BiomeSource biomeSource, int systemIndex, int orbitIndex,
                                int minY, int height, int seaLevel, Optional<Long> worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.systemIndex = systemIndex;
        this.orbitIndex = orbitIndex;
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
                Planet planet = Galaxy.from(effectiveWorldSeed()).getStarSystem(pid.system()).getPlanet(orbitIndex);
                PlanetWorldgenProfile p = PlanetWorldgenProfile.from(planet);
                profile = p;
                // R8 hydrology fix: real sea level comes from waterCoverage + amplitude, not the
                // static JSON placeholder (which was only right for the proof planet).
                effectiveSeaLevel = (int) Math.round(p.seaLevel());
                seaLevel = effectiveSeaLevel;
                terrain = TerrainGenerators.from(p);
                shaper = com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper.create(
                        terrain, p.planetSeed(), p.geology().physical(), p.geology().provinces(),
                        p.geology().terrainSignature(), p.baseHeight(), p.amplitude());
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
                    BlockState secondaryGeology = PlanetBlocks.material(
                            geology.palette().secondarySurface() != null
                                    ? geology.palette().secondarySurface()
                                    : geology.palette().primarySurface());
                    for (GeologicalProvince province : geology.provinces().provinces()) {
                        BlockState primary = planetSurfaceStates.get(province);
                        BlockState mountain = geology.palette().mountain() != null
                                ? PlanetBlocks.material(geology.palette().mountain()) : secondaryGeology;
                        BlockState accent = geology.palette().accentFor(province) != null
                                ? PlanetBlocks.material(geology.palette().accentFor(province)) : primary;
                        BlockState crystal = province == GeologicalProvince.CRYSTAL
                                && geology.palette().crystal() != null
                                ? PlanetBlocks.material(geology.palette().crystal()) : mountain;
                        planetZoneSurfaceStates.put(province, new BlockState[]{
                                primary, secondaryGeology, crystal, accent});
                    }
                }
                                surface = PlanetBlocks.material(p.material().surface());
                subsurface = PlanetBlocks.material(p.material().subsurface());
                fluid = PlanetBlocks.fluid(p.fluid() == FluidProfile.WATER ? FluidProfile.WATER : FluidProfile.NONE);
                hasWater = p.hasWater();
                // R19 fluid ecology: the geology subsystem carries the planet's fluid identity
                // (global + per-province overrides, cached per planet). The Minecraft adapter
                // resolves each province's family to a registry-safe BlockState exactly once —
                // the per-column hot path below only does map lookups.
                if (geology != null && geology.fluidEcology() != null) {
                    // Ocean ecology gate: a world whose physical profile cannot host surface
                    // liquid stays dry, no matter what the legacy water profile says.
                    if (geology.fluidEcology().global() == com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily.NONE
                            || !com.modscreating.unlimitedspace.core.worldgen.fluids.OceanEcology
                                    .of(geology.physical()).hasLiquid()) {
                        hasWater = false;
                    }
                    for (GeologicalProvince province : geology.provinces().provinces()) {
                        planetFluidStates.put(province, PlanetFluids.blockFor(
                                geology.fluidEcology().familyAt(province)));
                    }
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

    GeologicalProvince provinceAt(int x, int z, ChunkAccess chunk) {
        GeologicalProvinceContext ctx = columnContext(x, z, chunk);
        return ctx == null ? GeologicalProvince.PLAINS : ctx.province();
    }

    /**
     * R18: unified per-column province context — the single source of truth for terrain shaping,
     * material selection, resource/vegetation/structure placement and the F3 debug line. Returns
     * the same {@link GeologicalProvinceContext} the terrain shaper derives its column from.
     */
    GeologicalProvinceContext columnContext(int x, int z, ChunkAccess chunk) {
        PlanetGeologyProfile g = geology;
        if (g == null) return null;
        int h = surfaceHeight(x, z, chunk);
        double span = Math.max(1.0, 2.0 * profile.amplitude());
        double elevation01 = Math.max(0.0, Math.min(1.0, (h - (profile.baseHeight() - profile.amplitude())) / span));
        return g.provinces().contextAt(x, z, elevation01);
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

                // R20: material selection = PROVINCE × MATERIAL ZONE (large coherent patches,
                // never per-column random switching). One map lookup + one cheap field sample.
                GeologicalProvince province = provinceAt(bx, bz, chunk);
                BlockState[] zoneStates = planetZoneSurfaceStates.get(province);
                BlockState surface = zoneStates != null
                        ? zoneStates[com.modscreating.unlimitedspace.core.worldgen.materials
                                .MaterialZoneMap.zoneAt(profile.materialSeed(), bx, bz)]
                        : planetSurfaceStates.getOrDefault(province, this.surface);
                BlockState subsurface = planetSubsurfaceStates.getOrDefault(province, this.subsurface);
                BlockState deep = deepState != null ? deepState : subsurface;

                for (int y = minY; y <= h; y++) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIndex) {
                        section = chunk.getSection(idx);
                        sectionIndex = idx;
                    }
                    // R16: layered geology — province surface, then subsurface, then deep stone.
                    BlockState state;
                    if (y == h) {
                        state = surface;
                    } else if (h - y <= 4) {
                        state = subsurface;
                    } else {
                        state = deep;
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
        PlanetWorldgenProfile prof = profile;
        info.add("UnlimitedSpace planet s" + systemIndex + "/o" + orbitIndex
                + " worldSeed=" + effectiveWorldSeed()
                + " pattern=" + (prof == null ? "?" : prof.terrainPattern())
                                + " surfaceMat=" + (prof == null ? "?" : prof.material().surface().blockId())
                + " sea=" + effectiveSeaLevel + " @ " + pos);
        // R8 diagnostics: prove the composite profile resolves per-slot.
        if (prof != null) {
            info.add("  terrain=" + prof.terrain().primaryPattern()
                    + " blend=" + String.format("%.2f", prof.terrain().blend())
                    + " baseH=" + String.format("%.1f", prof.terrain().baseHeight())
                    + " amp=" + String.format("%.1f", prof.terrain().amplitude()));
            info.add("  biomes=" + prof.biome().count() + " presets="
                    + prof.biome().presets() + " @x,z=" + pos.getX() + "," + pos.getZ()
                    + " -> " + prof.biome().biomeAt(pos.getX(), pos.getZ()));
                        info.add("  materials count=" + prof.material().count()
                    + " surface=" + prof.material().surface().blockId()
                    + " fluid=" + prof.water().fluid());
            info.add("  water coverage=" + String.format("%.2f", prof.water().waterCoverage())
                    + " seaLevel=" + String.format("%.1f", prof.water().seaLevel())
                    + " rivers=" + prof.water().hasRivers());
            info.add("  env temp=" + prof.environment().temperature()
                    + " humidity=" + String.format("%.2f", prof.environment().humidity())
                    + " atm=" + prof.environment().atmosphere()
                    + " gravity=" + prof.environment().gravity() + "g");
            PlanetGeologyProfile g16 = prof.geology();
            if (g16 != null) {
                info.add("  geology " + g16.summary());
                GeologicalProvinceContext columnCtx = geology != null
                        ? geology.provinces().contextAt(pos.getX(), pos.getZ(), 0.5)
                        : GeologicalProvinceContext.neutral(null);
                String provName = columnCtx == null ? "?" : columnCtx.province().name();
                String material = favoriteSurfaceMaterial();
                info.add("  province=" + provName
                        + (columnCtx == null ? "" : String.format(java.util.Locale.ROOT, "  strength=%.2f", columnCtx.strength()))
                        + "  surface=" + material
                        + "  features=" + featureFlags(material));
                // R19: compact environment line — fluid family AT THIS COLUMN's province,
                // quantized atmosphere class and the gradual ocean ecology.
                if (g16.fluidEcology() != null) {
                    info.add("  fluid=" + g16.fluidEcology().familyAt(columnCtx.province())
                            + " atmo=" + (g16.atmosphere() == null ? "?" : g16.atmosphere().coarseClass())
                            + " ocean=" + com.modscreating.unlimitedspace.core.worldgen.fluids.OceanEcology
                                    .of(g16.physical()));
                }
                if (g16.terrainSignature() != null) {
                    TerrainSignature sig = g16.terrainSignature();
                    info.add("  terrain " + sig.summary());
                }
                // R20: hierarchical terrain diagnostics — archetype, global fields, material zone.
                if (shaper != null) {
                    com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSample sample =
                            shaper.sample(pos.getX(), pos.getZ());
                    com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory surfCat =
                            com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategorySelector
                                    .classify(g16.physical(), shaper.archetype().primary(),
                                            columnCtx == null ? null : columnCtx.province());
                    info.add(String.format(java.util.Locale.ROOT,
                            "  archetype=%s continental=%.2f erosion=%.2f ridge=%.2f zone=%d"
                                    + " surface=%s",
                            shaper.archetype().summary(),
                            sample.continentalness(), sample.erosion(), sample.ridge(),
                            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialZoneMap
                                    .zoneAt(prof.materialSeed(), pos.getX(), pos.getZ()),
                            surfCat));
                }
                // R21: PLANETARY GEOGRAPHY — climate, relief, biome region, color theme.
                if (g16.climate() != null && g16.relief() != null && g16.regions() != null) {
                    var regionCtx = g16.regions().contextAt(pos.getX(), pos.getZ());
                    var biomeLabel = prof.biome().biomeForRegion(regionCtx.region());
                    double localCoverage = regionCtx.localMountainCoverage(
                            g16.relief().mountainCoverage());
                    com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial rock =
                            g16.palette().primarySurface();
                    info.add(String.format(java.util.Locale.ROOT,
                            "  climate=%s relief=%s mountains=%.2f (local %.2f)"
                                    + " biome=%s %.2f province=%s %.2f surface=%s theme=%s rock=%s",
                            g16.climate().label(), g16.relief().label(),
                            g16.relief().mountainCoverage(), localCoverage,
                            regionCtx.region(), regionCtx.strength(),
                            columnCtx == null ? "?" : columnCtx.province(),
                            columnCtx == null ? 0.0 : columnCtx.strength(),
                            biomeLabel, g16.colorTheme(),
                            rock == null ? "?" : rock.blockId()));
                }
            }
            info.add("  visual sky=0x" + Integer.toHexString(prof.visual().skyColor())
                    + " water=0x" + Integer.toHexString(prof.visual().waterColor()));
        }
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
}