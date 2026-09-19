package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily;
import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidInteractions;
import com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyProfile;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResource;
import com.modscreating.unlimitedspace.core.worldgen.resources.PlanetResourceSelector;
import com.modscreating.unlimitedspace.core.worldgen.structures.StructureSelector;
import com.modscreating.unlimitedspace.core.worldgen.vegetation.VegetationSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/**
 * R16 planet-diversity feature stage, extended R18 to a single unified context.
 *
 * <p>Every stage (ores / vegetation / structures) reads the SAME
 * {@link GeologicalProvinceContext} of a column that the terrain shaper and material palette
 * use — one deterministic source of truth. Coherence, not randomness: a Volcanic province hosts
 * volcanic ores + vents, a Crystal province crystal ores + clusters, a Crater province impact
 * minerals + glass, a Glacial province frozen minerals and sparse/no vegetation.
 *
 * <p>Deterministic and allocation-light: no {@code new Random()}, no BlockEntity, no world
 * scans. Block ids resolve against the live registry with a safe fallback.
 */
final class PlanetFeaturePlacer {

    private PlanetFeaturePlacer() {}

    /** Deterministic, province-aware ore placement for one chunk. */
    static void applyOres(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ,
                          GeologicalProvinceContext ctx) {
        PlanetProperties props = g.profileProfile() == null ? null : g.profileProfile().properties();
        if (props == null) return;
        int oreSeed = (int) props.oreSeed();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        // Iron/diamond are always candidates; exotic ores are province-gated and rarity-tiered.
        List<PlanetResource> resources = PlanetResourceSelector.distributeFor(oreSeed, cx, cz, ctx);
        if (resources.isEmpty()) return;

        for (PlanetResource r : resources) {
            BlockState ore = PlanetBlocks.byId(r.targetBlock());
            if (ore.isAir()) continue;
            long veinSeed = Seeds.derive(props.oreSeed(), "us.resources.vein", cx, cz, r.id().hashCode());
            int veinSize = Math.max(1, r.veinSize() / 2);
            int baseY = clampY(chunk, r.minY() + (int) (Math.abs(veinSeed % Math.max(1, r.maxY() - r.minY() + 1))));
            for (int i = 0; i < veinSize; i++) {
                int vx = (int) ((veinSeed >> (i * 5)) & 15);
                int vz = (int) ((veinSeed >> (i * 5 + 7)) & 15);
                int vy = baseY + (int) ((veinSeed >> (i * 5 + 13)) & 3) - 1;
                BlockPos pos = new BlockPos(minBX + vx, vy, minBZ + vz);
                if (!chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, ore, false);
                }
            }
        }
    }

    /** Deterministic, province-aware vegetation for one chunk (sparse, biome-gated). */
    static void applyVegetation(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea) {
        PlanetProperties props = g.profileProfile() == null ? null : g.profileProfile().properties();
        if (props == null || g.geology() == null) return;
        long vegetationSeed = props.vegetationSeed();

        for (int lx = 0; lx < 16; lx += 2) {
            for (int lz = 0; lz < 16; lz += 2) {
                int bx = minBX + lx;
                int bz = minBZ + lz;
                PlanetBiome biome = g.profileProfile().biome().biomeAt(bx, bz);
                // Unified column context — the SAME one the terrain/material systems use.
                GeologicalProvinceContext ctx = g.columnContext(bx, bz, chunk);
                com.modscreating.unlimitedspace.core.worldgen.vegetation.PlantDefinition plant =
                        VegetationSelector.decideFor(vegetationSeed, props, biome, ctx, bx, bz);
                if (plant == null) continue;

                int h = g.surfaceHeight(bx, bz, chunk);
                if (h <= sea) continue; // never underwater

                BlockState soil = PlanetBlocks.byId(plant.blockId());
                if (soil.isAir()) continue;
                BlockPos pos = new BlockPos(bx, h + 1, bz);
                if (chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, soil, false);
                }
            }
        }
    }

    /** Deterministic, province-aware small geological structure for one chunk. */
    static void applyStructure(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea) {
        PlanetProperties props = g.profileProfile() == null ? null : g.profileProfile().properties();
        if (props == null) return;
        GeologicalProvinceContext ctx = g.columnContext(minBX + 8, minBZ + 8, chunk);
        var outcome = StructureSelector.decideFor(
                props.structureSeed(), props, ctx, chunk.getPos().x, chunk.getPos().z, minBX, minBZ);
        if (outcome.isEmpty()) return;

        StructureSelector.Outcome o = outcome.get();
        int h = g.surfaceHeight(minBX + o.localX(), minBZ + o.localZ(), chunk);
        if (h <= sea) return;
        BlockState ruin = PlanetBlocks.byId(o.structure().fakeBlockId());
        if (ruin.isAir()) ruin = Blocks.STONE.defaultBlockState();

        // Small footprint (feature cluster reads as one gesture, not a giant structure).
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 2}};
        for (int[] off : offsets) {
            BlockPos pos = new BlockPos(minBX + o.localX() + off[0], h, minBZ + o.localZ() + off[1]);
            if (!chunk.getBlockState(pos).isAir()) {
                chunk.setBlockState(pos, ruin, false);
            }
        }
    }

    private static int clampY(ChunkAccess chunk, int y) {
        int min = chunk.getMinBuildHeight() + 1;
        int max = chunk.getMaxBuildHeight() - 2;
        return Math.max(min, Math.min(max, y));
    }

    /**
     * R19 ambient life: fluid formations — volcanic lava-channel bottoms (priority 2),
     * geothermal brine pools (priority 3) and the rare GEOTHERMAL VENT.
     *
     * <p>Deterministic: pure function of {@code (featureSeed, chunk, province)}. Liquids
     * appear only where the planet's fluid profile physically admits them (the province's
     * local family resolves to a non-air fluid). Feature stage only — no fluid BlockEntity,
     * no world scans, a handful of setBlockState calls in the rare chunks that host features.
     */
    static void applyFluidFeatures(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea) {
        PlanetGeologyProfile geology = g.geology();
        if (geology == null || geology.fluidEcology() == null) return;
        GeologicalProvinceContext ctx = g.columnContext(minBX + 8, minBZ + 8, chunk);
        if (ctx == null || !ctx.favoursLavaChannels()) return;   // volcanic/geothermal regions only
        GeologicalProvince province = ctx.province();

        PlanetFluidProfile fluids = geology.fluidEcology();
        long featureSeed = geology.featureSeed();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        // Priority 2/3: the province's local fluid pools (VOLCANIC → molten channel bottoms,
        // GEOTHERMAL → brine pools). Only when the family resolves to a real liquid.
        FluidFamily family = fluids.familyAt(province);
        BlockState poolFluid = PlanetFluids.blockFor(family);
        if (!poolFluid.isAir()) {
            long poolSeed = Seeds.derive(featureSeed, "us.features.pool", cx, cz);
            double chance = province == GeologicalProvince.VOLCANIC ? 0.10 : 0.08;
            if (Seeds.fraction(poolSeed, 2L) < chance) {
                placePool(g, chunk, minBX, minBZ, sea, family, poolFluid, poolSeed);
            }
        }

        // Geothermal vent: rare, deterministic, land only.
        long ventSeed = Seeds.derive(featureSeed, "us.features.vent", cx, cz);
        double ventChance = province == GeologicalProvince.GEOTHERMAL ? 0.06 : 0.03;
        if (Seeds.fraction(ventSeed, 3L) < ventChance) {
            placeVent(g, chunk, minBX, minBZ, sea, ventSeed);
        }
    }

    /** A small surface pool of the province's local fluid + deterministic contact crust. */
    private static void placePool(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea,
                                  FluidFamily family, BlockState poolFluid, long seed) {
        int lx = 3 + (int) (Seeds.fraction(seed, 4L) * 10);
        int lz = 3 + (int) (Seeds.fraction(seed, 5L) * 10);
        int bx = minBX + lx;
        int bz = minBZ + lz;

        // 2x2 pool replacing the surface blocks (flush with the surrounding terrain).
        boolean placedAny = false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                int h = g.surfaceHeight(bx + dx, bz + dz, chunk);
                if (h <= sea) continue;   // below sea the sea itself owns the column
                BlockPos pos = new BlockPos(bx + dx, h, bz + dz);
                if (!chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, poolFluid, false);
                    placedAny = true;
                }
            }
        }
        if (!placedAny) return;

        // Deterministic contact reaction: MOLTEN + water-like global → cooled volcanic crust
        // ringing the pool (the exact "cooled volcanic material" interaction of R19).
        PlanetGeologyProfile geology = g.geology();
        if (family == FluidFamily.MOLTEN && geology.fluidEcology() != null) {
            FluidInteractions.ContactOutcome contact =
                    FluidInteractions.contact(FluidFamily.MOLTEN, geology.fluidEcology().global());
            if (contact.occurred()) {
                BlockState crust = PlanetBlocks.byId(contact.materialKey());
                int[][] rim = {{-1, 0}, {2, 0}, {0, -1}, {0, 2}, {-1, -1}, {2, 2}};
                for (int[] off : rim) {
                    int rx = bx + off[0];
                    int rz = bz + off[1];
                    int rh = g.surfaceHeight(rx, rz, chunk);
                    if (rh <= sea) continue;
                    BlockPos pos = new BlockPos(rx, rh, rz);
                    if (!chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, crust, false);
                    }
                }
            }
        }
    }

    /** One geothermal vent at a deterministic surface position (no underwater vents). */
    private static void placeVent(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea, long seed) {
        int lx = 4 + (int) (Seeds.fraction(seed, 6L) * 8);
        int lz = 4 + (int) (Seeds.fraction(seed, 7L) * 8);
        int bx = minBX + lx;
        int bz = minBZ + lz;
        int h = g.surfaceHeight(bx, bz, chunk);
        if (h <= sea) return;
        BlockPos pos = new BlockPos(bx, h, bz);
        if (!chunk.getBlockState(pos).isAir()) {
            chunk.setBlockState(pos, EnvironmentalBlocks.ventState(), false);
        }
    }
}