package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.SurfaceMaterial;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.core.worldgen.terrain.ValueNoiseTerrainGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
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
 * {@link TerrainGenerator} (built from {@link PlanetWorldgenProfile}/planet seed).
 * Terminal link of the Phase-3 POC pipeline:
 *
 * <pre>{@code
 * PlanetProperties -> PlanetWorldgenProfile -> TerrainGenerator -> this -> Minecraft terrain
 * }</pre>
 *
 * The codec stores only primitive config (seed + shape + blocks), so the world is
 * fully deterministic and reconstructible from a {@code LevelStem} datapack entry.
 */
public final class PlanetChunkGenerator extends ChunkGenerator {

    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.LONG.fieldOf("terrain_seed").forGetter(g -> g.terrainSeed),
            Codec.DOUBLE.fieldOf("base_height").forGetter(g -> g.baseHeight),
            Codec.DOUBLE.fieldOf("amplitude").forGetter(g -> g.amplitude),
            Codec.DOUBLE.fieldOf("frequency").forGetter(g -> g.frequency),
            Codec.INT.fieldOf("sea_level").forGetter(g -> g.seaLevel),
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.BOOL.fieldOf("has_water").forGetter(g -> g.hasWater),
            BlockState.CODEC.fieldOf("surface_block").forGetter(g -> g.surface),
            BlockState.CODEC.fieldOf("subsurface_block").forGetter(g -> g.subsurface),
            BlockState.CODEC.fieldOf("fluid_block").forGetter(g -> g.fluid)
    ).apply(inst, PlanetChunkGenerator::new));

    private final long terrainSeed;
    private final double baseHeight;
    private final double amplitude;
    private final double frequency;
    private final int seaLevel;
    private final int minY;
    private final int height;
    private final boolean hasWater;
    private final BlockState surface;
    private final BlockState subsurface;
    private final BlockState fluid;
    private final TerrainGenerator terrain;

    public PlanetChunkGenerator(BiomeSource biomeSource,
                                long terrainSeed,
                                double baseHeight,
                                double amplitude,
                                double frequency,
                                int seaLevel,
                                int minY,
                                int height,
                                boolean hasWater,
                                BlockState surface,
                                BlockState subsurface,
                                BlockState fluid) {
        super(biomeSource);
        this.terrainSeed = terrainSeed;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.seaLevel = seaLevel;
        this.minY = minY;
        this.height = height;
        this.hasWater = hasWater;
        this.surface = surface;
        this.subsurface = subsurface;
        this.fluid = fluid;
        this.terrain = new ValueNoiseTerrainGenerator(terrainSeed, baseHeight, amplitude, frequency);
    }

    /**
     * Build a generator straight from a domain profile, mapping abstract materials via
     * {@link PlanetBlocks}. Used for programmatic/tests and the debug teleport.
     */
    public static PlanetChunkGenerator from(BiomeSource biomeSource, PlanetWorldgenProfile profile) {
        SurfaceMaterial surface = profile.surfaceMaterial();
        return new PlanetChunkGenerator(
                biomeSource,
                profile.terrainSeed(),
                profile.baseHeight(),
                profile.amplitude(),
                profile.frequency(),
                (int) Math.round(profile.seaLevel()),
                -64,
                384,
                profile.hasWater(),
                PlanetBlocks.surface(surface),
                PlanetBlocks.subsurface(profile.subsurfaceMaterial()),
                PlanetBlocks.fluid(profile.fluid() == FluidProfile.WATER ? FluidProfile.WATER : FluidProfile.NONE));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private int surfaceHeight(int x, int z, LevelHeightAccessor level) {
        int h = (int) Math.round(terrain.height(x, z));
        return Mth.clamp(h, this.minY, level.getMaxBuildHeight() - 1);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minBX = chunk.getPos().getMinBlockX();
        int minBZ = chunk.getPos().getMinBlockZ();
        int maxY = chunk.getMaxBuildHeight() - 1;

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
                    // Heightmap grid is per-chunk local 16x16: must use local x/z, world y.
                    worldSurface.update(x, y, z, state);
                    oceanFloor.update(x, y, z, state);
                }

                if (hasWater) {
                    for (int y = h + 1; y <= Math.min(seaLevel, maxY); y++) {
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
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return seaLevel;
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
        for (int y = minY; y <= h; y++) {
            states[y - minY] = (y == h) ? surface : subsurface;
        }
        int top = Math.min(seaLevel, level.getMaxBuildHeight() - 1);
        if (hasWater) {
            for (int y = h + 1; y <= top; y++) {
                states[y - minY] = fluid;
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("UnlimitedSpace planet: seed=" + terrainSeed + " @ " + pos);
    }
}