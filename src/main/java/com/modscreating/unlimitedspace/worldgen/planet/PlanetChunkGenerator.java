package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
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
    private TerrainGenerator terrain;
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
                                surface = PlanetBlocks.material(p.material().surface());
                subsurface = PlanetBlocks.material(p.material().subsurface());
                fluid = PlanetBlocks.fluid(p.fluid() == FluidProfile.WATER ? FluidProfile.WATER : FluidProfile.NONE);
                hasWater = p.hasWater();
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

    private int surfaceHeight(int x, int z, LevelHeightAccessor level) {
        ensureProfile();
        int h = (int) Math.round(terrain.height(x, z));
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

                for (int y = minY; y <= h; y++) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIndex) {
                        section = chunk.getSection(idx);
                        sectionIndex = idx;
                    }
                    BlockState state = (y == h) ? surface : subsurface;
                    int ly = y & 15;
                    section.setBlockState(x, ly, z, state, false);
                    worldSurface.update(x, y, z, state);
                    oceanFloor.update(x, y, z, state);
                }

                if (hasWater) {
                    for (int y = h + 1; y <= Math.min(sea, maxY); y++) {
                        LevelChunkSection waterSection = chunk.getSection(chunk.getSectionIndex(y));
                        waterSection.setBlockState(x, y & 15, z, fluid, false);
                        worldSurface.update(x, y, z, fluid);
                    }
                }
            }
        }

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
            info.add("  visual sky=0x" + Integer.toHexString(prof.visual().skyColor())
                    + " water=0x" + Integer.toHexString(prof.visual().waterColor()));
        }
    }
}