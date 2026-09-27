package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.habitability.HabitableWaterSource;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.features.LavaPoolMorphology;
import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidFamily;
import com.modscreating.unlimitedspace.core.worldgen.fluids.FluidInteractions;
import com.modscreating.unlimitedspace.core.worldgen.fluids.PlanetFluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
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
        PlanetGeologyProfile geology = g.geology();
        if (props == null || geology == null) return;
        // R23 (E-1): the living path is the ecology channel - SubBiome x organicPotential x
        // wetness x radiation cap x province compatibility. The legacy PlanetBiome enum is no
        // longer a vegetation driver (it stays only where registered-biome naming needs it).
        long vegetationSeed = props.vegetationSeed();
        // ACT 2: the actual-habitability gate — non-habitable worlds grow exactly zero plants.
        boolean vegetationPermitted = g.profileProfile().life() != null
                && g.profileProfile().life().vegetationPermitted();
        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile physical =
                geology.physical();
        double organic = physical == null ? 0.0 : physical.organicPotential();
        double radiation = physical == null ? 0.0 : physical.radiation();
        double temperature = physical == null ? 0.5 : physical.temperature();
        double wetness = geology.environment() == null
                ? 0.4 : geology.environment().surfaceWetness();
        int h0 = g.surfaceHeight(minBX + 8, minBZ + 8, chunk);
        double span = Math.max(1.0, 2.0 * g.profileProfile().amplitude());

        for (int lx = 0; lx < 16; lx += 2) {
            for (int lz = 0; lz < 16; lz += 2) {
                int bx = minBX + lx;
                int bz = minBZ + lz;
                double elevation01 = Math.max(0.0, Math.min(1.0,
                        (g.surfaceHeight(bx, bz, chunk)
                                - (g.profileProfile().baseHeight() - g.profileProfile().amplitude())) / span));
                GeologicalProvinceContext ctx = g.columnContext(bx, bz, chunk);
                com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome sub =
                        geology.subBiomeAt(bx, bz, h0, elevation01);
                com.modscreating.unlimitedspace.core.worldgen.vegetation.PlantDefinition plant =
                        VegetationSelector.decideEcology(vegetationSeed, props, vegetationPermitted,
                                sub, ctx, organic, radiation, wetness, temperature, bx, bz);
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
        // ACT 2: structure gate — non-habitable planet/moon surfaces host ZERO structures.
        // The unrelated space dimension (SpaceChunkGenerator) is deliberately NOT migrated here.
        if (g.profileProfile().life() == null || !g.profileProfile().life().structureEligible()) {
            return;
        }
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
        // PHASE 3: geothermal pockets on a frozen world stay liquid (rare, geology-aware);
        // everywhere else the pool inherits the planet's water phase.
        FluidFamily family = fluids.familyAt(province);
        WaterPhaseModel.Phase poolPhase = WaterPhaseModel.geothermalPocketPhase(
                g.waterPhase(), province, geology.physical().geothermalFlux());
        BlockState poolFluid = PlanetFluids.blockFor(family, poolPhase);
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

    /**
     * ACT 6 section 4: the MORPHOLOGY POOL placement.
     *
     * <p>The previous implementation was hard-wired to a 2x2 block at depth 1 with a fixed
     * 6-cell rim, i.e. every pool on every world was the same four blocks. The shape now comes
     * from the production {@link LavaPoolMorphology} system (>= 15 genuinely different families,
     * each with its own width / length / orientation / asymmetry / depth / edge irregularity),
     * and the pool is placed as a real basin:
     * <ul>
     *   <li>the floor is CARVED below the surrounding surface, so the liquid sits in a depression
     *       and can never float in the air or hang over a void;</li>
     *   <li>every liquid column is checked for SUPPORT (a solid block directly underneath);</li>
     *   <li>every liquid column is checked for BOUNDS (inside the chunk, above the world floor,
     *       below the ceiling);</li>
     *   <li>neighbouring blocks are only overwritten where they are part of the pool, and the RIM
     *       uses the real material already present in this world (plus the existing molten
     *       contact crust) - no block is hardcoded.</li>
     * </ul>
     */
    private static void placePool(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ, int sea,
                                  FluidFamily family, BlockState poolFluid, long seed) {
        // The deterministic shape: >= 15 real geometry families, not 15 variations of a 2x2.
        LavaPoolMorphology morph = LavaPoolMorphology.of(seed);
        int radius = (int) Math.ceil(morph.maxRadius()) + 1;
        // BOUNDS: the pool centre must leave the whole shape inside the chunk with a margin.
        int span = Math.max(1, 16 - 2 * (radius + 1));
        int lx = radius + 1 + (int) (Seeds.fraction(seed, 4L) * span);
        int lz = radius + 1 + (int) (Seeds.fraction(seed, 5L) * span);
        int cx = minBX + lx;
        int cz = minBZ + lz;
        if (cx - radius < minBX || cx + radius >= minBX + 16
                || cz - radius < minBZ || cz + radius >= minBZ + 16) {
            return;   // bounds check (defensive: the margin above already guarantees it)
        }

        int worldFloor = chunk.getMinBuildHeight() + 1;
        int worldCeil = chunk.getMaxBuildHeight() - 2;
        boolean placedAny = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int depth = morph.depthAt(dx, dz);
                if (depth <= 0) continue;                     // outside the pool shape
                int bx = cx + dx;
                int bz = cz + dz;
                if (bx < minBX || bx >= minBX + 16 || bz < minBZ || bz >= minBZ + 16) continue;
                int h = g.surfaceHeight(bx, bz, chunk);
                if (h <= sea) continue;                      // below sea the sea itself owns it

                // The liquid surface is one block BELOW the surrounding terrain, so the pool is a
                // genuine depression with a real bank all around it - never a flat patch stamped
                // on top of the ground, and never a floating rectangle.
                int liquidTop = h - 1;
                int floor = liquidTop - depth;
                if (floor < worldFloor || liquidTop > worldCeil) continue;   // bounds check

                // SUPPORT: the floor block must exist and be solid, otherwise the liquid would
                // hang over a void. (A freshly filled column always has stone at the surface.)
                BlockPos floorPos = new BlockPos(bx, floor, bz);
                if (chunk.getBlockState(floorPos).isAir()) continue;

                // Carve the basin: the excavated blocks become the pool's liquid column.
                for (int y = floor + 1; y <= liquidTop; y++) {
                    BlockPos p = new BlockPos(bx, y, bz);
                    if (!chunk.getBlockState(p).isAir()) {
                        chunk.setBlockState(p, poolFluid, false);
                    }
                }
                placedAny = true;
            }
        }
        if (!placedAny) return;

        // RIM. One existing source, nothing hardcoded: MOLTEN against a water-like global fluid
        // produces the EXISTING deterministic cooled volcanic crust (the R19 FluidInteractions
        // contact reaction), applied to exactly the bank columns that touch this pool's liquid.
        // Every other family simply keeps the world's OWN surface material as its bank, so the
        // rim is by construction a real, compatible material of this world.
        PlanetGeologyProfile geology = g.geology();
        if (family == FluidFamily.MOLTEN && geology != null && geology.fluidEcology() != null) {
            FluidInteractions.ContactOutcome contact =
                    FluidInteractions.contact(FluidFamily.MOLTEN, geology.fluidEcology().global());
            if (contact.occurred()) {
                BlockState crust = PlanetBlocks.byId(contact.materialKey());
                if (crust != null && !crust.isAir()) {
                    applyPoolRim(g, chunk, minBX, minBZ, sea, cx, cz, radius, morph, crust, worldCeil);
                }
            }
        }
    }

    /**
     * ACT 6 section 4: the molten contact-crust rim. Only columns that are genuine bank columns of
     * THIS pool (a liquid column on at least one orthogonal side) are touched, so an unrelated
     * neighbouring biome is never overwritten by a pool's rim, and an air block is never filled
     * (so the crust can never float).
     */
    private static void applyPoolRim(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ,
                                     int sea, int cx, int cz, int radius,
                                     LavaPoolMorphology morph, BlockState crust, int worldCeil) {
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                if (morph.depthAt(dx, dz) > 0) continue;                 // inside the pool, not a rim
                if (!touchesLiquid(morph, dx, dz)) continue;              // not adjacent to liquid
                int bx = cx + dx;
                int bz = cz + dz;
                if (bx < minBX || bx >= minBX + 16 || bz < minBZ || bz >= minBZ + 16) continue;
                int h = g.surfaceHeight(bx, bz, chunk);
                if (h <= sea || h > worldCeil) continue;                   // bounds check
                BlockPos pos = new BlockPos(bx, h, bz);
                if (chunk.getBlockState(pos).isAir()) continue;           // never fill air
                chunk.setBlockState(pos, crust, false);
            }
        }
    }

    /** True when at least one of the four orthogonal neighbours of (dx, dz) carries liquid. */
    private static boolean touchesLiquid(LavaPoolMorphology morph, int dx, int dz) {
        return morph.depthAt(dx + 1, dz) > 0 || morph.depthAt(dx - 1, dz) > 0
                || morph.depthAt(dx, dz + 1) > 0 || morph.depthAt(dx, dz - 1) > 0;
    }

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
    // ================================================== ACT 6 section 8: HABITABLE WATER
    /**
     * ACT 6 section 8: the GUARANTEED WATER SOURCE of a canonically HABITABLE world.
     *
     * <p>The canonical validator decides that a world may host life, but nothing in generation
     * guaranteed that such a world actually CONTAINS reachable liquid water - a low-coverage world
     * could pass validation and still be a world where a player never finds water, while the UI
     * honestly (and misleadingly) reported HABITABLE. This stage closes that gap:
     * <pre>
     *   actual habitable (canonical SystemHabitability / MoonHabitability)
     *     AND the canonical water phase is stable LIQUID
     *       -> one deterministic spring per 32x32-chunk lattice cell
     * </pre>
     * <p>It adds a SMALL number of genuine surface water bodies, so an explorable habitable world
     * really is drinkable, without turning the planet into an ocean world. It introduces no new
     * habitability rule: the decision is read from the existing canonical LifeState, never derived
     * here.
     */
    static void applyHabitableWater(PlanetChunkGenerator g, ChunkAccess chunk, int minBX, int minBZ,
                                    int sea) {
        // Only worlds the CANONICAL chain actually calls habitable get a guaranteed source.
        com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile p = g.profileProfile();
        if (p == null || p.life() == null || !p.life().actualHabitable()) return;
        // The water must be the canonical LIQUID phase: a frozen or dry world keeps no liquid
        // (its validator answer already says STERILE, this is only a belt-and-braces guard).
        WaterPhaseModel.Phase phase = g.waterPhase();
        if (phase == null || phase == WaterPhaseModel.Phase.SOLID || phase == WaterPhaseModel.Phase.VAPOR
                || phase == WaterPhaseModel.Phase.NONE) {
            return;
        }
        BlockState water = PlanetFluids.blockFor(FluidFamily.WATER_LIKE, phase);
        if (water == null || water.isAir()) return;

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        if (!CelestialSeedCache.isSet()) return;
        long worldSeed = CelestialSeedCache.get();
        if (!HabitableWaterSource.isGuaranteedSpringChunk(worldSeed, cx, cz)) return;
        int[] place = HabitableWaterSource.springPlacement(worldSeed, cx, cz);
        int sx = minBX + place[0];
        int sz = minBZ + place[1];
        int radius = place[2];

        int h = g.surfaceHeight(sx, sz, chunk);
        if (h <= sea) return;                       // the sea already owns that column
        int worldFloor = chunk.getMinBuildHeight() + 1;
        int worldCeil = chunk.getMaxBuildHeight() - 2;
        int depth = 2 + (int) (Seeds.fraction(
                Seeds.derive(worldSeed, "unlimitedspace.habitability.spring.depth", cx, cz), 4L) * 2.99);

        // A genuine, roundish basin of liquid water carved into the surface. It is deterministic
        // and seed-stable, has a real bank all around it and never floats: the floor block must
        // exist before any liquid is written.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.hypot(dx, dz) / (radius + 0.5);
                if (d > 1.0) continue;
                int bx = sx + dx;
                int bz = sz + dz;
                if (bx < minBX || bx >= minBX + 16 || bz < minBZ || bz >= minBZ + 16) continue;
                int bh = g.surfaceHeight(bx, bz, chunk);
                if (bh <= sea) continue;
                int colDepth = Math.max(1, (int) Math.round(depth * (1.0 - d * d)));
                int top = bh - 1;
                int floor = top - colDepth;
                if (floor < worldFloor || top > worldCeil) continue;
                if (chunk.getBlockState(new BlockPos(bx, floor, bz)).isAir()) continue;   // support
                for (int y = floor + 1; y <= top; y++) {
                    BlockPos bp = new BlockPos(bx, y, bz);
                    if (!chunk.getBlockState(bp).isAir()) chunk.setBlockState(bp, water, false);
                }
            }
        }
    }
}
