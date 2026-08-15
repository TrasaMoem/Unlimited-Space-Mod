package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.core.galaxy.layout.*;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetDefinition;
import com.modscreating.unlimitedspace.core.planets.PlanetPropertyGenerator;
import com.modscreating.unlimitedspace.core.seed.GalaxySeed;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
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
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class SpaceChunkGenerator extends ChunkGenerator {

    public static final MapCodec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.LONG.fieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, SpaceChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final long worldSeed;
    private final GalaxyLayout layout;
    private final GalaxySpatialIndex index;

    public SpaceChunkGenerator(BiomeSource biomeSource, long worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldSeed = worldSeed;
        this.layout = GalaxyLayout.from(worldSeed);
        this.index = layout.index();
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        GalaxyCoordinate coord = BlockPosToGalaxyCoordinate.fromChunk(chunk.getPos());
        var result = layout.lookup(coord);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        if (result.planet() != null && result.profile() != null) {
            TerrainGenerator terrain = TerrainGenerators.from(result.profile());
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int bx = minX + x, bz = minZ + z;
                    int h = (int) Math.round(terrain.height(bx, bz));
                    for (int y = minY; y <= Math.min(h, maxY); y++) {
                        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                        BlockState state = (y == h ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
                        section.setBlockState(x, y & 15, z, state, false);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return 64; }
    @Override public int getMinY() { return -64; }
    @Override public int getBaseHeight(int x, int z, net.minecraft.world.level.levelgen.Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return 64; }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) { return new NoiseColumn(-64, new BlockState[level.getHeight()]); }
    @Override public void addDebugScreenInfo(java.util.List<String> info, RandomState random, BlockPos pos) { info.add("UnlimitedSpace space: seed=" + worldSeed); }
}