package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyLayout;
import com.modscreating.unlimitedspace.core.worldgen.TerrainGenerators;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialSelector;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResource;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResourceSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
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
 * <p>Per column it resolves the owning planet via {@link GalaxyLayout} and, on a planet
 * surface, builds terrain from the pure-domain {@link TerrainGenerator}. Surface / subsurface
 * / deep materials come from the planet's deterministic {@link PlanetMaterialSelector}
 * (materialSeed + biome + coords), and veins come from {@link PlanetResourceSelector}
 * (dedicated oreSeed + chunk coords). All pure functions, no global Random, no mutable
 * worldgen state, restart-stable.
 */
public final class SpaceChunkGenerator extends ChunkGenerator {

    public static final MapCodec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.LONG.fieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, SpaceChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final long worldSeed;
    private final GalaxyLayout layout;
    private final Map<String, BlockState> stateCache = new HashMap<>();

    public SpaceChunkGenerator(BiomeSource biomeSource, long worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldSeed = worldSeed;
        this.layout = GalaxyLayout.from(worldSeed);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    public GalaxyLayout layout() { return layout; }
    public long worldSeed() { return worldSeed; }

    private Context contextFor(int bx, int bz) {
        GalaxyLayout.LookupResult r = layout.lookup(BlockPosToGalaxyCoordinate.fromBlock(bx, bz));
        if (r.planet() == null || r.profile() == null || r.planetData() == null) return null;
        var p = r.planetData().properties();
        return new Context(TerrainGenerators.from(r.profile()), p.materialSeed(), p.biomeSeed(), p.oreSeed());
    }

    private BlockState stateFor(PlanetMaterial m) {
        if (m == null) return Blocks.STONE.defaultBlockState();
        return stateCache.computeIfAbsent(m.blockId(), id -> {
            ResourceLocation key;
            try { key = ResourceLocation.parse(id); }
            catch (Exception e) { return Blocks.STONE.defaultBlockState(); }
            Block b = BuiltInRegistries.BLOCK.get(key);
            return b != null ? b.defaultBlockState() : Blocks.STONE.defaultBlockState();
        });
    }

    private BlockState stateFor(String blockId) {
        return stateCache.computeIfAbsent(blockId, id -> {
            ResourceLocation key;
            try { key = ResourceLocation.parse(id); }
            catch (Exception e) { return Blocks.IRON_ORE.defaultBlockState(); }
            Block b = BuiltInRegistries.BLOCK.get(key);
            return b != null ? b.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
        });
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int chunkX = chunk.getPos().x, chunkZ = chunk.getPos().z;
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        int[] surf = new int[256];
        Arrays.fill(surf, Integer.MIN_VALUE);

        for (int x = 0; x < 16; x++) {
            int bx = minX + x;
            for (int z = 0; z < 16; z++) {
                int bz = minZ + z;
                Context ctx = contextFor(bx, bz);
                if (ctx == null) continue; // DEEP_SPACE
                int h = Mth.clamp((int) Math.round(ctx.terrain.height(bx, bz)), minY, maxY);
                surf[x * 16 + z] = h;
                PlanetMaterialPalette pal = PlanetMaterialSelector.select(ctx.materialSeed, ctx.biomeSeed, bx, bz);
                int si = -1;
                LevelChunkSection section = null;
                for (int y = minY; y <= h; y++) {
                    int s2i = chunk.getSectionIndex(y);
                    if (si != s2i) { section = chunk.getSection(s2i); si = s2i; }
                    BlockState st = stateFor(pal != null ? (y == h ? pal.surface() : pal.subsurface()) : null);
                    section.setBlockState(x, y & 15, z, st, false);
                    surface.update(x, y, z, st);
                    floor.update(x, y, z, st);
                }
            }
        }

        GalaxyLayout.LookupResult center = layout.lookup(BlockPosToGalaxyCoordinate.fromChunk(chunk.getPos()));
        if (center.planetData() != null) {
            long oreSeed = center.planetData().properties().oreSeed();
            List<PlanetResource> resources = PlanetResourceSelector.distribute(oreSeed, chunkX, chunkZ);
            for (PlanetResource r : resources) {
                BlockState ore = stateFor(r.targetBlock());
                for (int k = 0; k < r.veinSize(); k++) {
                    long h = veinMix(oreSeed, r.id(), chunkX, chunkZ, k);
                    int lx = (int) ((h) & 15);
                    int lz = (int) ((h >> 4) & 15);
                    int y = (int) (((h >> 8) & 255) + minY);
                    if (y < r.minY() || y > r.maxY() || y > maxY) continue;
                    int si = lx * 16 + lz;
                    if (surf[si] == Integer.MIN_VALUE || y > surf[si]) continue;
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(lx, y & 15, lz, ore, false);
                    UnlimitedSpace.LOGGER.info(
                            "[worldgen] space chunk ({},{}) ORE {} block={} at ({},{},{})",
                            chunkX, chunkZ, r.id(), r.targetBlock(),
                            minX + lx, y, minZ + lz);
                }
            }
        }

        // Minimal Phase 8 runtime-verification diagnostics: one summary line per space
        // chunk once real blocks have been written. Logged from the running Minecraft
        // worldgen so block ids / biome / ore counts can be observed and compared
        // across restarts. Pure observability — changes nothing about generation.
        Context centerCtx = contextFor(minX + 8, minZ + 8);
        if (centerCtx != null) {
            int h = surf[8 * 16 + 8];
            PlanetBiome biome = PlanetBiomeSelector.select(centerCtx.biomeSeed, minX + 8, minZ + 8);
            PlanetMaterialPalette pal = PlanetMaterialSelector.select(centerCtx.materialSeed, centerCtx.biomeSeed, minX + 8, minZ + 8);
            String surfBlock = pal != null ? pal.surface().blockId() : "?";
            String subBlock = pal != null ? pal.subsurface().blockId() : "?";
            int oreBlocks = center.planetData() != null
                    ? PlanetResourceSelector.distribute(center.planetData().properties().oreSeed(), chunkX, chunkZ).size()
                    : 0;
            UnlimitedSpace.LOGGER.info(
                    "[worldgen] space chunk ({},{}) PLANET center=({},{},{}) biome={} surface={} subsurface={} ore={}",
                    chunkX, chunkZ, minX + 8, h, minZ + 8, biome, surfBlock, subBlock, oreBlocks);
        } else {
            UnlimitedSpace.LOGGER.info("[worldgen] space chunk ({},{}) DEEP_SPACE", chunkX, chunkZ);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Deterministic (oreSeed, resource, chunk, slot) -> pseudo position. */
    private static long veinMix(long seed, String ns, long... args) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < ns.length(); i++) { h ^= ns.charAt(i); h *= 0x100000001B3L; }
        for (long a : args) { h ^= a; h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L; h ^= (h >>> 27); }
        return h;
    }

    private record Context(TerrainGenerator terrain, long materialSeed, long biomeSeed, long oreSeed) {}

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return 64; }
    @Override public int getMinY() { return -64; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Context ctx = contextFor(x, z);
        if (ctx == null) return getMinY();
        return Mth.clamp((int) Math.round(ctx.terrain.height(x, z)), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        Context ctx = contextFor(x, z);
        if (ctx != null) {
            int h = Mth.clamp((int) Math.round(ctx.terrain.height(x, z)), minY, level.getMaxBuildHeight() - 1);
            PlanetMaterialPalette pal = PlanetMaterialSelector.select(ctx.materialSeed, ctx.biomeSeed, x, z);
            for (int y = minY; y <= h; y++) {
                PlanetMaterial m = (pal != null) ? (y == h ? pal.surface() : pal.subsurface()) : null;
                states[y - minY] = stateFor(m);
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("UnlimitedSpace space: seed=" + worldSeed);
    }
}
