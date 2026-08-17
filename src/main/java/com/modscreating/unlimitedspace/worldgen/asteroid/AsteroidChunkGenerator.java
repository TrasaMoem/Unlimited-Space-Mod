package com.modscreating.unlimitedspace.worldgen.asteroid;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry.Body;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidGenerationProfile;
import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Dedicated {@link ChunkGenerator} for an asteroid cluster field world (R11).
 *
 * <p>This is deliberately NOT {@code PlanetChunkGenerator}: the world is VOID with discrete,
 * irregular asteroid bodies floating in space — not a solid terrain plane. The generator is
 * parameterised by a stable slot ({@code system_index} + {@code cluster_index}) and derives the
 * whole procedural asteroid profile at runtime from the real Minecraft world seed via the shared
 * {@link CelestialSeedCache} bridge:
 *
 * <pre>
 * WorldSeed -&gt; GalaxySeed -&gt; StarSystemSeed -&gt; AsteroidSeed -&gt; AsteroidGenerationProfile
 *      -&gt; AsteroidFieldGeometry -&gt; this -&gt; asteroid bodies + void
 * </pre>
 *
 * <p>All block decisions are pure functions of (asteroid seed, chunk coords, profile): no mutable
 * global RNG, no {@code Random()}, no dependence on generation order, and negative chunk/block
 * coordinates are handled safely.
 */
public final class AsteroidChunkGenerator extends ChunkGenerator {

    /** R11 codec: a static slot + world bounds only. {@code world_seed} is optional; when absent the real seed is supplied by {@link CelestialSeedCache}. */
    public static final MapCodec<AsteroidChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.INT.fieldOf("system_index").forGetter(g -> g.systemIndex),
            Codec.INT.fieldOf("cluster_index").forGetter(g -> g.clusterIndex),
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.LONG.optionalFieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, AsteroidChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final int systemIndex;
    private final int clusterIndex;
    private final int minY;
    private final int height;
    private final Optional<Long> worldSeed;

    private volatile AsteroidFieldGeometry geometry;
    private long asteroidSeed;

    public AsteroidChunkGenerator(BiomeSource biomeSource, int systemIndex, int clusterIndex,
                                  int minY, int height, Optional<Long> worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.systemIndex = systemIndex;
        this.clusterIndex = clusterIndex;
        this.minY = minY;
        this.height = height;
        this.worldSeed = worldSeed;
    }

    /** Effective world seed: JSON value if present, else the shared runtime cache. */
    long effectiveWorldSeed() {
        return worldSeed.isPresent() ? worldSeed.get() : CelestialSeedCache.get();
    }

    private void ensureGeometry() {
        if (geometry != null) return;
        synchronized (this) {
            if (geometry == null) {
                long ws = effectiveWorldSeed();
                asteroidSeed = Seeds.asteroidField(
                        Seeds.starSystem(Seeds.galaxy(ws), systemIndex), clusterIndex);
                AsteroidClusterId id = AsteroidClusterId.of(StarSystemId.of(systemIndex), clusterIndex);
                AsteroidGenerationProfile profile = AsteroidGenerationProfile.create(id, asteroidSeed);
                geometry = new AsteroidFieldGeometry(asteroidSeed, profile);
            }
        }
    }

    /** Programmatic construction straight from a domain profile (tests / debug). */
    public static AsteroidChunkGenerator from(BiomeSource biomeSource, AsteroidGenerationProfile profile,
                                              long asteroidSeed, int minY, int height) {
        int sys = profile.clusterId().system().index();
        int clu = profile.clusterId().clusterIndex();
        AsteroidChunkGenerator g = new AsteroidChunkGenerator(biomeSource, sys, clu, minY, height, Optional.empty());
        g.asteroidSeed = asteroidSeed;
        g.geometry = new AsteroidFieldGeometry(asteroidSeed, profile);
        return g;
    }

    public AsteroidFieldGeometry geometry() {
        ensureGeometry();
        return geometry;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        // Void baseline: actual bodies are placed in fillFromNoise.
        BlockState[] states = new BlockState[level.getHeight()];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(minY, states);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        ensureGeometry();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        // Last valid block this chunk owns (inclusive): columns chunkMin..chunkMin+15.
        // chunkMax = chunkMin + 16 is the FIRST column of the NEXT chunk; an inclusive x<=xTo loop
        // reaches local-x = 16, whose heightmap index (16*16=256) is outside 0..255 and throws
        // IllegalArgumentException on asteroid-body chunks (R11.1 CS arrival crash).
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        int maxY = chunk.getMaxBuildHeight() - 1;

        List<Body> bodies = geometry.bodiesInChunk(chunkMinX, chunkMinZ);
        for (Body b : bodies) {
            int yFrom = Math.max(minY, b.cy() - (int) Math.ceil(b.radius() * b.sy()));
            int yTo = Math.min(maxY, b.cy() + (int) Math.ceil(b.radius() * b.sy()));
            int xFrom = Math.max(chunkMinX, b.cx() - (int) Math.ceil(b.radius() * b.sx()));
            int xTo = Math.min(chunkMaxX, b.cx() + (int) Math.ceil(b.radius() * b.sx()));
            int zFrom = Math.max(chunkMinZ, b.cz() - (int) Math.ceil(b.radius() * b.sz()));
            int zTo = Math.min(chunkMaxZ, b.cz() + (int) Math.ceil(b.radius() * b.sz()));

            for (int x = xFrom; x <= xTo; x++) {
                for (int z = zFrom; z <= zTo; z++) {
                    for (int y = yFrom; y <= yTo; y++) {
                        if (geometry.isInside(b, x, y, z)) {
                            BlockState state = AsteroidBlocks.fromId(geometry.blockIdAt(x, y, z, b));
                            chunk.setBlockState(new BlockPos(x, y, z), state, false);
                            // Heightmap.update expects chunk-local (0..15) x/z, not global coords
                            // (see PlanetChunkGenerator). Global x/z packs to x*16+z into the 0..255
                            // column index; local x=16 -> 256 -> IllegalArgumentException.
                            worldSurface.update(x - chunkMinX, y, z - chunkMinZ, state);
                            oceanFloor.update(x - chunkMinX, y, z - chunkMinZ, state);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        // Surface layer is produced in fillFromNoise.
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No carving in a void field.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No natural mob spawning.
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        ensureGeometry();
        AsteroidGenerationProfile p = geometry.profile();
        info.add("UnlimitedSpace asteroid s" + systemIndex + "/c" + clusterIndex
                + " worldSeed=" + effectiveWorldSeed()
                + " pattern=" + p.shapePattern()
                + " density=" + String.format("%.2f", p.density())
                + " dominantOre=" + p.dominantOre()
                + " primaryMat=" + p.material().primary().blockId());
    }
}