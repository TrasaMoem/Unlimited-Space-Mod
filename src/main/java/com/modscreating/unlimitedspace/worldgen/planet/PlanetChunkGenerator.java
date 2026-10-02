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
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily;
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
     * ACT V3.7: the roles whose material the SURFACE path resolves, and therefore the roles that
     * get a spatial variant table. It is a fixed list, so the set of tables is a constant of the
     * build and the per-column work never depends on which roles happen to exist.
     */
    private static final com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole[]
            VARIANT_ROLES = {
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.PRIMARY_SURFACE,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SECONDARY_SURFACE,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.MOUNTAIN,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SEDIMENT,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.SOIL,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.GEOTHERMAL,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.CRYSTAL,
    };

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
     * ACT V3.7: the SPATIAL MATERIAL VARIANT tables, one per palette role, built ONCE per world.
     *
     * <p>Before V3.7 the surface block of a role was a per-planet constant, so every column of an
     * ice shell resolved PRIMARY_SURFACE to the same block no matter how much snow it accumulated
     * or how much rock was exposed. These tables are the pure-domain authority that turns the
     * already-sampled column into a spatially varying VARIANT of the elected role, and they are the
     * same objects the tests and the preview drive - there is no test-only branch.
     */
    private final java.util.Map<com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole,
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField>
            materialVariants = new java.util.EnumMap<>(
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.class);
    /**
     * ACT V3.7: the variant tables' {@code BlockState}s, resolved ONCE per world.
     *
     * <p>The per-column path must never touch a registry key, so each role's legal variants are
     * resolved to real states here and the hot path is a pure array lookup by variant index.
     */
    private final java.util.Map<com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole,
            BlockState[]> materialVariantStates = new java.util.EnumMap<>(
            com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.class);

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
     * ACT worldgen fix: whether the sand material may be applied at all on THIS planet. Resolved
     * ONCE per world from the planet's own {@code PlanetSurface} through
     * {@link com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics#mayLeadSurface}.
     * A dune LANDFORM may exist on any world, but the sand MATERIAL is refused wherever the surface
     * class does not admit it (volcanic leads with ash, an ice shell with frozen materials, a rocky
     * world with rock), so no world gets an incoherent hard sand wall.
     */
    private boolean duneSandAllowed;
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
    /** WORLDGEN V2: the per-worker column helper (reusable scratch over the immutable profile). */
    private com.modscreating.unlimitedspace.core.worldgen.geology.GeologyColumns columns;
    /**
     * WORLDGEN V3.1 (TASK N): the ONE shared per-column sampling pathway of this world. The biome,
     * the material role and the surface category all read the SAME sampled column, so they cannot
     * disagree with each other or with the terrain that column actually gets.
     */
    private com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler v3Sampler;
    /** The reusable per-column scratch: a column query allocates nothing. */
    private final com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample
            columnSample =
            new com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample();
    private BlockState surface;
    private BlockState subsurface;
    private BlockState fluid;
    private boolean hasWater;
    // PHASE 3: planet-scale water phase, resolved ONCE (drives fluid blocks + hasWater gate).
    private WaterPhaseModel.Phase waterPhase = WaterPhaseModel.Phase.LIQUID;
    /**
     * WORLDGEN V3.1: the surface mode of this planet. A GAS_GIANT has no solid surface at all, so
     * the generator must not query the elevation field, the hydrology, the material field or the
     * feature field. See {@link #isGasGiant()}.
     */
    private com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode surfaceMode =
            com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode.SOLID_ROCKY;
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
                // WORLDGEN V3.1: resolve the surface mode ONCE. A gaseous body is a gas giant,
                // so every downstream terrain/hydrology/material/feature path is disabled for it.
                surfaceMode = resolveSurfaceMode(p);
                terrain = TerrainGenerators.from(p);
                shaper = com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper.create(
                        terrain, p.planetSeed(), p.geology().physical(), p.geology().provinces(),
                        p.geology().terrainSignature(), p.baseHeight(), p.amplitude(),
                        null, p.geology().geography(), p.geology().climate());
                columns = new com.modscreating.unlimitedspace.core.worldgen.geology.GeologyColumns(
                        p.geology());
                // WORLDGEN V3.1 (TASK N): ONE sampler, shared by terrain, biome and material. It
                // is built from the very same collaborators the shaper was built from, so the
                // biome a column reports and the terrain that column gets come from one pipeline.
                v3Sampler = new com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler(
                        shaper,
                        new com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField(
                                p.geology().climate(), shaper.character(),
                                new com.modscreating.unlimitedspace.core.worldgen.terrain
                                        .WindDirectionField(p.planetSeed())),
                        new com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField(
                                shaper.character(),
                                new com.modscreating.unlimitedspace.core.worldgen.climate
                                        .ClimateField(p.geology().climate(), shaper.character(),
                                        new com.modscreating.unlimitedspace.core.worldgen.terrain
                                                .WindDirectionField(p.planetSeed())),
                                // V3.2: the candidate registry is built ONCE per world from THIS
                                // planet's admissibility + surface class, so an ecology that cannot
                                // exist here is never scored. The per-column hot path is unchanged.
                                com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField
                                        .candidatesFor(
                                        com.modscreating.unlimitedspace.core.worldgen.admissibility
                                                .PlanetAdmissibility.of(p.geology().physical(),
                                                        p.properties().surface()),
                                        p.properties().surface())),
                        p.geology().provinces());
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
                        // V3.3: the micro-facies partner ALWAYS inherits the per-province primary
                        // resolved above (same-theme texture inside the dominant material). The
                        // old conditional compared the province surface against the planet-wide
                        // primary id and, on any province with its own surface override, dropped
                        // the variant and duplicated the primary: the dominant slot then showed
                        // TWO province-scale materials side by side (the "sharp dark/light
                        // substrate layers"). Micro-facies is a 192-block texture INSIDE one
                        // province's material, never a second province-scale language.
                        BlockState micro = microVariant != null ? microVariant : primary;
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
                    // ACT worldgen fix (SEMANTIC GATE): the dune material override may only apply
                    // where the planet's own surface class semantically admits SAND. The landform
                    // stays wherever it exists; only the sand material is refused where it would be
                    // incoherent (volcanic -> ash, ice -> frozen, rocky -> rock), so no world gets a
                    // hard sand wall just because a dune LANDFORM is present.
                    com.modscreating.unlimitedspace.core.planets.PlanetSurface duneSurface =
                            geology.physical() == null ? null : geology.physical().surface();
                    duneSandAllowed = com.modscreating.unlimitedspace.core.worldgen.materials
                            .MaterialSemantics.mayLeadSurface(
                                    com.modscreating.unlimitedspace.core.worldgen.materials
                                            .MaterialSemanticFamily.SAND, duneSurface);
                    if (duneSandAllowed
                            && geology.palette().sediment() != null
                            && geology.palette().sediment().family() != null
                            && geology.palette().sediment().family().superFamily()
                                    == com.modscreating.unlimitedspace.core.worldgen.materials
                                            .MaterialFamily.MaterialSuperFamily.GRANULAR) {
                        BlockState sand = PlanetBlocks.material(geology.palette().sediment());
                        if (sand != null && !sand.isAir()) duneSandState = sand;
                    }
                    // ACT V3.7: build the SPATIAL MATERIAL VARIANT tables for every role whose
                    // material the surface path can resolve, ONCE per world.
                    //
                    // The candidate set of each table is the role's LEGAL set (the same physical
                    // and surface-class admission the palette uses), so this layer can only choose
                    // among materials the planet would have accepted anyway - it can never widen
                    // the palette, introduce a province gate or re-introduce a random roll. The
                    // BlockStates are resolved here so the per-column path is an array lookup.
                    long faciesSeed = p.materialSeed();
                    for (com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole role
                            : VARIANT_ROLES) {
                        com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField
                                field = com.modscreating.unlimitedspace.core.worldgen.materials
                                .MaterialVariantField.forRole(geology.physical(), role, faciesSeed,
                                        geology.palette().materialFor(role));
                        if (field.size() == 0) continue;
                        BlockState[] states = new BlockState[field.size()];
                        for (int i = 0; i < states.length; i++) {
                            states[i] = PlanetBlocks.material(field.at(i));
                        }
                        materialVariants.put(role, field);
                        materialVariantStates.put(role, states);
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

    /**
     * WORLDGEN V3.1 / ACT worldgen fix: the surface mode of a planet.
     *
     * <p>Delegates to the SINGLE canonical authority
     * {@link com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode#forProfile},
     * which the UI reads too, so the generated world and the navigation panel can never disagree.
     */
    private static com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode
            resolveSurfaceMode(PlanetWorldgenProfile p) {
        return com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode.forProfile(p);
    }

    /**
     * WORLDGEN V3.1: whether this world is a gas giant, i.e. has NO solid surface.
     *
     * <p>The single gate the chunk generator consults. A gas giant must not execute the shaper,
     * the elevation field, the hydrology, the material field, the feature field, the lava path or
     * the water path — and must not fake a solid world.
     */
    public boolean isGasGiant() {
        ensureProfile();
        return surfaceMode.isGasGiant();
    }

    /** The resolved surface mode of this world (diagnostics / preview). */
    public com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode surfaceMode() {
        ensureProfile();
        return surfaceMode;
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
     * WORLDGEN V2: the per-column geological context. The DISCRETE province drives block
     * selection; the CONTINUOUS weights drive every material and terrain amplitude.
     */
    GeologicalProvinceContext columnContext(int x, int z, ChunkAccess chunk) {
        return columnContext(x, z, chunk, null);
    }

    /** Canonical context with a pre-computed macro sample (avoids double evaluation). */
    GeologicalProvinceContext columnContext(int x, int z, ChunkAccess chunk, MacroSample macroOut) {
        PlanetGeologyProfile g = geology;
        if (g == null) return null;
        return provinceContextOf(g, x, z);
    }

    /** The continuous geological context of a column (nearest-site + continuous weights). */
    public static GeologicalProvinceContext provinceContextOf(PlanetGeologyProfile g, int x, int z) {
        double[] w = g.provinces().weightsAt(x, z, new double[g.provinces().weights().size()]);
        int best = 0;
        for (int i = 1; i < w.length; i++) if (w[i] > w[best]) best = i;
        return new GeologicalProvinceContext(g.provinces().weights().get(best).province(),
                w, g.provinces().weights(), g.physical());
    }

    /**
     * WORLDGEN V2: THE macro geography, sampled into a caller-owned MacroSample. Exactly one
     * instance per planet, owned by PlanetGeologyProfile, so the chunk generator, the terrain
     * shaper and the material path cannot disagree about macro ownership.
     */
    MacroSample macroSampleAt(int x, int z, MacroSample out) {
        PlanetGeologyProfile g = geology;
        if (g == null || g.geography() == null) return out;
        g.geography().sample(x, z, out);
        return out;
    }
    /** WORLDGEN V2: the local sub-biome at a column (per-worker scratch). */
    com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome subBiomeAt(
            int x, int z, double elevation01) {
        if (columns == null) {
            ensureProfile();
        }
        return columns == null
                ? com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome.MEADOW
                : columns.subBiomeAt(x, z, elevation01);
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
        // WORLDGEN V3.1: a gas giant has no water surface, so its sea level IS the world floor.
        if (surfaceMode.isGasGiant()) return minY;
        return effectiveSeaLevel;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        // WORLDGEN V3.1: a gas giant has no surface to measure. Returning the world floor is the
        // correct NeoForge answer: there is no terrain above it, so no heightmap may claim any.
        ensureProfile();
        if (surfaceMode.isGasGiant()) return minY;
        return surfaceHeight(x, z, level);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        // WORLDGEN V3.1: a gas giant produces an ALL-AIR column. This is the real NeoForge
        // no-surface contract, not terrain that is generated and then deleted: the elevation,
        // hydrology, material and feature fields are never queried at all.
        ensureProfile();
        if (surfaceMode.isGasGiant()) {
            BlockState[] air = new BlockState[level.getHeight()];
            Arrays.fill(air, Blocks.AIR.defaultBlockState());
            return new NoiseColumn(minY, air);
        }
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
        // WORLDGEN V3.1: a gas giant generates NOTHING. The shaper, the elevation field, the
        // hydrology, the surface-material field, the feature field, the lava path and the water
        // path are all skipped, and the chunk is returned with both heightmaps already at their
        // world-floor value. This is a real no-surface result, not generated-then-deleted.
        if (surfaceMode.isGasGiant()) {
            // Prime both heightmaps from the (empty) chunk: with no blocks placed, the real
            // world-surface and ocean-floor heights are the world floor, which is exactly what
            // a body with no solid surface should report.
            Heightmap.primeHeightmaps(chunk, java.util.Set.of(
                    Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
            return CompletableFuture.completedFuture(chunk);
        }
        ensureProfile();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minBX = chunk.getPos().getMinBlockX();
        int minBZ = chunk.getPos().getMinBlockZ();
        int maxY = chunk.getMaxBuildHeight() - 1;
        int sea = effectiveSeaLevel;

        MacroSample macroOut = new MacroSample();

        for (int x = 0; x < 16; x++) {
            int bx = minBX + x;
            for (int z = 0; z < 16; z++) {
                int bz = minBZ + z;
                int h = surfaceHeight(bx, bz, chunk);
                LevelChunkSection section = null;
                int sectionIndex = -1;

                // WORLDGEN V2 AUTHORITY ORDER:
                //   macroOut   = THE macro geography (one instance, one geometry)
                //   columnCtx  = the secondary province (nearest-site + continuous weights)
                macroSampleAt(bx, bz, macroOut);
                GeologicalProvinceContext columnCtx = columnContext(bx, bz, chunk, macroOut);
                GeologicalProvince province = columnCtx == null
                        ? GeologicalProvince.PLAINS : columnCtx.province();
                BlockState[] zoneStates = planetZoneSurfaceStates.get(province);
                BlockState zoneSurface;
                if (zoneStates != null) {
                    // R23 (M-1) + ACT 3 (P4): contextual role selection - THEME x MEDIUM PROVINCE
                    // x SURFACE CATEGORY, with the macro context as a bounded boundary input.
                    int zone = com.modscreating.unlimitedspace.core.worldgen.materials
                            .PlanetMaterialRoleSelector.zoneAt(
                                    geology != null ? geology.colorTheme() : null,
                                    columnCtx == null ? 0.0 : columnCtx.confidence(),
                                    surfaceCategoryAt(bx, bz, h, province), profile.materialSeed(),
                                    bx, bz, 1.0 - macroOut.transitionWeight);
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
                        SubBiome sub = columns.subBiomeAt(bx, bz, elevation01(bx, bz, h));
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
                // ---- WORLDGEN V3.4: the SURFACE DITHER, the Pufferfish principle ----
                // A biome boundary does NOT mean an instant hard switch of surface blocks. Inside
                // the transition zone a column may take the NEIGHBOURING biome's palette, decided
                // by a spatially COHERENT deterministic pattern (neighbouring columns are strongly
                // correlated), and the share the neighbour may take is 0 in the interior and at
                // most MAX_NEIGHBOUR_SHARE exactly on the contour. So the interior is untouched and
                // the contact zone is a genuine blend of two materials rather than a drawn line.
                zoneSurface = applyBoundaryDither(zoneSurface, bx, bz);

                // ---- ACT V3.7: the SPATIAL MATERIAL VARIANT ----
                // The role was already elected above from the real column signals, the boundary
                // dither has already mixed in the neighbour's palette, and only NOW is the concrete
                // VARIANT of that role resolved from the column's own continuous channels. This
                // ordering is what STAGE 7 requires: logical biome, then local role, then variant,
                // and only then may the boundary field blend the two biome palettes. Applying the
                // variant earlier would let it override the dither, and applying it later would
                // overwrite the dune and landform-exposure exceptions.
                zoneSurface = applyMaterialVariant(zoneSurface, bx, bz);

                BlockState columnSubsurface = planetSubsurfaceStates.getOrDefault(province, this.subsurface);
                BlockState columnDeep = deepState != null ? deepState : columnSubsurface;

                // ACT 6 section 5: DUNE MATERIAL. A column that is genuinely a dune core (the
                // landform identity is DUNE and the dune field is strong there) takes the
                // planet's own sand-family block, so the visible sand relief and the visible sand
                // material agree. Interdune ground and every other landform keep their themed
                // material unchanged - this is a narrow, landform-gated override, not a second
                // material system.
                if (duneSandAllowed
                        && duneSandState != null
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
        // ACT V4: the OASIS - a rare body of liquid on a world that otherwise has none. Water at
        // or below 100 C at the site, molten rock above it; both are pure functions of the site's
        // own physics (OasisModel), never a second hydrology system.
        PlanetFeaturePlacer.applyOasis(this, chunk, minBX, minBZ, sea);

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
        if (geology == null || v3Sampler == null) {
            return com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory.ROCKY;
        }
        // WORLDGEN V3.1 (TASK E): the category is a CONTINUOUS function of the column, read from
        // the SHARED V3 sample. The legacy V2 rule table is a step function: it puts a visible
        // one-column seam on every province border, which is exactly the failure this ACT removes.
        // The V2 selector is retained for the V2 preview and its own regression tests.
        v3Sampler.sampleColumn(bx, bz, columnSample);
        return columnSample.surfaceCategory;
    }

    /**
     * ACT V3.7: resolve the SPATIAL VARIANT of the column's elected material role.
     *
     * <p>Before V3.7 the surface block was the per-planet constant of the elected role, so a whole
     * ice shell stood on ONE block. This method replaces that constant with a variant chosen from
     * the role's own LEGAL material set, weighted by the column's continuous channels (snow
     * accumulation, rock exposure, glacial relief, elevation, sediment, heat, crystals) and
     * positioned by a coherent large-scale facies field.
     *
     * <p>Three properties are structural rather than tuned:
     * <ul>
     *   <li><b>it can only choose among legal materials</b> - the candidate table is built from
     *       {@code MaterialCatalog.admissibleCandidatesFor}, i.e. the same physical and
     *       surface-class admission the palette already used, so the variant layer can never
     *       invent a material, never widen the palette and never gate on a province;</li>
     *   <li><b>it is a pure function of the column</b> - the same column always yields the same
     *       variant, there is no {@code Random}, no per-column state and no allocation;</li>
     *   <li><b>it is coherent, not speckled</b> - the deciding field has a wavelength of hundreds
     *       of blocks, so neighbouring columns agree and the result is facies regions.</li>
     * </ul>
     *
     * <p>It is deliberately a no-op when the elected role has no variant table, so a role the
     * planet's palette never resolved keeps exactly its previous behaviour.
     *
     * @param own the surface state the role / zone / dither path already resolved
     * @return the variant surface state, or {@code own} when this column has no variant
     */
    private BlockState applyMaterialVariant(BlockState own, int bx, int bz) {
        if (materialVariants.isEmpty() || v3Sampler == null) return own;
        com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample c = columnSample;
        // The shared per-worker scratch is only fresh on the zoneStates branch, so the coordinates
        // are verified: a stale read would paint a column with a neighbour's variant.
        if (c.x != bx || c.z != bz) return own;
        com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole role = c.materialRole;
        if (role == null) return own;
        com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField field =
                materialVariants.get(role);
        BlockState[] states = materialVariantStates.get(role);
        if (field == null || states == null || states.length == 0) return own;
        int index = field.index(c, bx, bz);
        if (index < 0 || index >= states.length) return own;
        BlockState variant = states[index];
        // The theme still governs, exactly as it does for the dither: a variant the planet's own
        // colour language does not admit is never painted, so spatial variation can never break
        // the planet's identity.
        if (variant == null) return own;
        if (geology != null && geology.colorTheme() != null) {
            com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial mat = field.at(index);
            if (mat != null && !geology.colorTheme().admits(
                    com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog
                            .visualRoleOf(mat))) {
                return own;
            }
        }
        return variant;
    }

    /**
     * WORLDGEN V3.4: the deterministic surface-material DITHER of a transition zone.
     *
     * <p>This is the architectural principle behind Pufferfish's Biome Dither, reimplemented for
     * this generator: an existing biome boundary does not oblige the world to switch every surface
     * block at once, because nearby biome surfaces can genuinely mix inside a contact zone. The
     * source code of that mod was not consulted; only the principle was taken.
     *
     * <p>Three properties are structural, not tuned:
     * <ul>
     *   <li><b>interior is untouched</b> — {@code runnerUpShare01} is exactly 0 when the margin
     *       gate is closed, so the vast majority of columns return {@code own} unchanged;</li>
     *   <li><b>coherent, not per-block</b> — the decision compares a smooth, pair-specific
     *       {@code dither01} against the share, so neighbouring columns agree over patches of
     *       {@code DITHER_WAVELENGTH} blocks. Never a per-column random roll;</li>
     *   <li><b>bounded</b> — the neighbour can take at most {@code MAX_NEIGHBOUR_SHARE} of the
     *       zone, so the logical biome always stays the majority and F3, spawn, climate and mob
     *       ecology are unaffected (the biome source still returns ONE biome per column).</li>
     * </ul>
     *
     * @param own the column's own winner-palette surface state
     * @return the dithered surface state, or {@code own} when the column is not in a transition
     */
    private BlockState applyBoundaryDither(BlockState own, int bx, int bz) {
        if (own == null || geology == null || v3Sampler == null) return own;
        // The V3 sample for this column was already produced by surfaceCategoryAt() above; it is
        // the shared per-worker scratch, so read what is needed immediately and never retain it.
        com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample c = columnSample;
        // Guard against a STALE scratch: surfaceCategoryAt() is only reached on the zoneStates
        // branch, so outside it the object still holds a previous column. A stale read would paint
        // one column with a neighbour computed for another, so the coordinates are verified and a
        // mismatch simply means "no dither here" rather than a wrong palette.
        if (c.x != bx || c.z != bz) return own;
        double share = c.runnerUpShare01;
        if (!(share > 0.0) || c.runnerUpMaterialRole == null) return own;
        if (c.dither01 >= share) return own;
        com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial neighbour =
                geology.palette().materialFor(c.runnerUpMaterialRole);
        if (neighbour == null) return own;
        // The theme still governs: a material the planet's colour language does not admit is never
        // painted, so the dither can blend two palettes but can never break the planet's identity.
        if (geology.colorTheme() != null && !geology.colorTheme().admits(
                com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog
                        .visualRoleOf(neighbour))) {
            return own;
        }
        return PlanetBlocks.material(neighbour);
    }

    /**
     * WORLDGEN V3.1: the sampled V3 column of this world, or {@code null} before the profile has
     * been resolved. The result is the shared reusable scratch: consumers must read what they
     * need from it immediately and must not retain the reference.
     */
    com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample v3Column(int bx, int bz) {
        ensureProfile();
        if (v3Sampler == null) return null;
        v3Sampler.sampleColumn(bx, bz, columnSample);
        return columnSample;
    }

    /**
     * ACT 3 (P3.2): local water phase of a column — the canonical planet phase plus the
     * EXISTING {@link WaterPhaseModel#geothermalPocketPhase} exception. No new phase logic.
     */
    // ACT V4: package-private - the oasis site reads the SAME local phase authority through this
    // method rather than inventing a second opinion about what phase a column is in.
    WaterPhaseModel.Phase phaseForColumn(GeologicalProvince province) {
        WaterPhaseModel.Phase global = waterPhase;
        if (global == null || geology == null || geology.physical() == null) return global;
        return WaterPhaseModel.geothermalPocketPhase(global, province,
                geology.physical().geothermalFlux());
    }

    /**
     * ACT STAGE 4.1: the ALREADY-RESOLVED fluid block of a column's province.
     *
     * <p>It is the same {@code planetFluidStates} table the ocean fill and the R19 pools already
     * read, so ordinary hydrology cannot introduce a fluid the rest of the world does not use.
     * A {@code null} province (no geology context) falls back to the planet's own global fluid,
     * exactly as the ocean path does.
     */
    BlockState fluidForColumn(GeologicalProvince province, FluidFamily family,
                              WaterPhaseModel.Phase phase) {
        if (province != null) {
            BlockState resolved = planetFluidStates.get(province);
            if (resolved != null) return resolved;
        }
        return PlanetFluids.blockFor(family, phase);
    }
}
