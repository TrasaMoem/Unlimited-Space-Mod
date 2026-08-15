package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyLayout;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Minimal vertical POC chunk generator for {@code unlimitedspace:space}.
 *
 * <p>Per column it resolves the owning planet via {@link GalaxyLayout} and, when on a
 * planet surface, builds solid terrain from the pure-domain {@link TerrainGenerator}.
 * Coordinates that do not resolve to a planet are left as empty deep space. Heightmaps
 * are updated so {@code server.getHeight(...)} is reliable for teleports.
 */
public final class SpaceChunkGenerator extends ChunkGenerator {

    public static final MapCodec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.LONG.fieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, SpaceChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final long worldSeed;
    private final GalaxyLayout layout;

    public SpaceChunkGenerator(BiomeSource biomeSource, long worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldSeed = worldSeed;
        this.layout = GalaxyLayout.from(worldSeed);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** The deterministic layout used by this generator (matches command teleports). */
    public GalaxyLayout layout() {
        return layout;
    }

    public long worldSeed() {
        return worldSeed;
    }

    /** Terrain generator for the planet owning the column, or null for deep space. */
    private TerrainGenerator terrainFor(int bx, int bz) {
        GalaxyLayout.LookupResult r = layout.lookup(BlockPosToGalaxyCoordinate.fromBlock(bx, bz));
        return (r.planet() != null && r.profile() != null) ? TerrainGenerators.from(r.profile()) : null;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;

        for (int x = 0; x < 16; x++) {
            int bx = minX + x;
            for (int z = 0; z < 16; z++) {
                int bz = minZ + z;
                TerrainGenerator t = terrainFor(bx, bz);
                if (t == null) continue; // deep space: leave air
                int h = Mth.clamp((int) Math.round(t.height(bx, bz)), minY, maxY);
                int sectionIndex = -1;
                LevelChunkSection section = null;
                for (int y = minY; y <= h; y++) {
                    int si = chunk.getSectionIndex(y);
                    if (si != sectionIndex) {
                        section = chunk.getSection(si);
                        sectionIndex = si;
                    }
                    BlockState state = (y == h) ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                    section.setBlockState(x, y & 15, z, state, false);
                    surface.update(bx, y, bz, state);
                    floor.update(bx, y, bz, state);
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
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 64;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        TerrainGenerator t = terrainFor(x, z);
        if (t == null) return getMinY();
        return Mth.clamp((int) Math.round(t.height(x, z)), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        TerrainGenerator t = terrainFor(x, z);
        if (t != null) {
            int h = Mth.clamp((int) Math.round(t.height(x, z)), minY, level.getMaxBuildHeight() - 1);
            for (int y = minY; y <= h; y++) {
                states[y - minY] = (y == h) ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("UnlimitedSpace space: seed=" + worldSeed);
    }
}