package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.StarWorldgenProfile;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetSeedCache;
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
 * R14.9 custom {@link ChunkGenerator} that renders a star's molten/plasma surface from the pure-domain
 * {@link StarWorldgenProfile} (via {@link StarBlocks}). Mirrors {@code PlanetChunkGenerator}: the codec
 * stores only the stable {@code system_index} slot + world bounds, and ALL material/height parameters are
 * derived at runtime from the real world seed plus the slot through {@code WorldSeed -&gt; Galaxy -&gt;
 * StarSystem -&gt; StarWorldgenProfile}, then mapped to Minecraft blocks by {@link StarBlocks}.
 *
 * <pre>{@code
 * WorldSeed -> Galaxy -> StarSystem -> StarWorldgenProfile -> this -> molten/plasma surface
 * }</pre>
 */
public final class StarChunkGenerator extends ChunkGenerator {

    public static final MapCodec<StarChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.INT.fieldOf("system_index").forGetter(g -> g.systemIndex),
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.INT.fieldOf("surface_base_y").forGetter(g -> g.surfaceBaseY),
            Codec.LONG.optionalFieldOf("world_seed").forGetter(g -> g.worldSeed)
    ).apply(inst, StarChunkGenerator::new));

    private final BiomeSource biomeSource;
    private final int systemIndex;
    private final int minY;
    private final int height;
    private final int surfaceBaseY;
    private final Optional<Long> worldSeed;
    private volatile StarWorldgenProfile profile;
    private BlockState surface;
    private BlockState subsurface;

    public StarChunkGenerator(BiomeSource biomeSource, int systemIndex, int minY, int height,
                              int surfaceBaseY, Optional<Long> worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.systemIndex = systemIndex;
        this.minY = minY;
        this.height = height;
        this.surfaceBaseY = surfaceBaseY;
        this.worldSeed = worldSeed;
    }

    long effectiveWorldSeed() {
        return worldSeed.isPresent() ? worldSeed.get() : PlanetSeedCache.get();
    }

    private void ensureProfile() {
        if (profile != null) return;
        synchronized (this) {
            if (profile == null) {
                StarWorldgenProfile p = StarWorldgenProfile.from(
                        Galaxy.from(effectiveWorldSeed()).getStarSystem(StarSystemId.of(systemIndex)));
                profile = p;
                surface = StarBlocks.surface(p.surfaceMaterial());
                subsurface = StarBlocks.subsurface(p.subsurfaceMaterial());
            }
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** Deterministic gentle plasma undulation around the profile's base Y (no {@code Random}). */
    private int surfaceHeight(int x, int z, LevelHeightAccessor level) {
        ensureProfile();
        StarWorldgenProfile p = profile;
        long s = Seeds.derive(effectiveWorldSeed(), "us.star.surface", systemIndex);
        double s1 = Seeds.fraction(s, 1L);
        double s2 = Seeds.fraction(s, 2L);
        double fx = x * 0.031;
        double fz = z * 0.031;
        double n = Math.sin(fx * (1.0 + s1) + fz * (1.0 + s2)) * 0.6
                + Math.sin(fz * 0.9 + s2 * 6.2831) * 0.4;
        int h = p.surfaceBaseY() + (int) Math.round(n * p.surfaceAmplitude());
        return Mth.clamp(h, this.minY, level.getMaxBuildHeight() - 1);
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        ensureProfile();
        return profile.surfaceBaseY();
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
        ensureProfile();
        int h = surfaceHeight(x, z, level);
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        for (int y = minY; y <= h; y++) {
            states[y - minY] = (y == h) ? surface : subsurface;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        ensureProfile();
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        int minY = getMinY();
        int maxY = chunk.getMaxBuildHeight() - 1;
        for (int x = 0; x < 16; x++) {
            int bx = chunk.getPos().getMinBlockX() + x;
            for (int z = 0; z < 16; z++) {
                int bz = chunk.getPos().getMinBlockZ() + z;
                int h = surfaceHeight(bx, bz, chunk);
                LevelChunkSection section = null;
                int sectionIndex = -1;
                for (int y = minY; y <= h && y <= maxY; y++) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIndex) {
                        section = chunk.getSection(idx);
                        sectionIndex = idx;
                    }
                    BlockState state = (y == h) ? surface : subsurface;
                    section.setBlockState(x, y & 15, z, state, false);
                    worldSurface.update(x, y, z, state);
                    oceanFloor.update(x, y, z, state);
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
        ensureProfile();
        StarWorldgenProfile p = profile;
        info.add("UnlimitedSpace star surface s" + systemIndex
                + " worldSeed=" + effectiveWorldSeed()
                + " baseY=" + p.surfaceBaseY()
                + " amp=" + p.surfaceAmplitude()
                + " stage=" + p.stage()
                + " material=" + p.surfaceMaterial()
                + " @ " + pos);
    }
}

