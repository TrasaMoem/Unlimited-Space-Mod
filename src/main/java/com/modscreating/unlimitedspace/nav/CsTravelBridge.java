package com.modscreating.unlimitedspace.nav;

import com.rae.creatingspace.configs.CSConfigs;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.rae.creatingspace.content.rocket.contraption.RocketContraption;
import com.rae.creatingspace.content.rocket.engine.RocketEngineBlockEntity;
import com.rae.creatingspace.content.rocket.engine.design.PropellantType;
import com.rae.creatingspace.content.rocket.network.RocketContraptionLaunchPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Creating Space bridge (R13.10): feeds a resolved {@link ResourceLocation} destination
 * into the SAME official travel pipeline the normal Rocket Controls UI uses.
 *
 * <p>It uses only the verified public Creating Space API discovered by inspecting the 1.7.18
 * dependency:
 * <ul>
 *   <li>{@code RocketContraptionEntity} — the rocket entity.</li>
 *   <li>{@code RocketContraptionLaunchPacket(int entityId, ResourceLocation destination)} with
 *       its public {@code handle(ServerPlayer)} — the exact public handler the official client
 *       launch flow invokes (it sets the rocket's destination and runs trajectory/launch
 *       calculation).</li>
 * </ul>
 *
 * <p>Deliberately NOT implemented here: {@code player.teleport(...)}, {@code changeDimension}
 * in our code, custom {@code DimensionTransition}/{@code RocketPath}/fuel/deltaV, custom rocket,
 * or any alternative transport. The normal Creating Space rocket performs the actual travel
 * (launch, fuel check, deltaV/route, dimension transition to the target world).
 */
public final class CsTravelBridge {

    private static final double NEARBY_RADIUS = 24.0;
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    /** R23.2: private RocketContraption field holding the engine thrust/consumption table CS
     *  reads during its trajectory calculation. Written only by the in-place repair below. */
    private static Field tptField;

    static {
        try {
            tptField = RocketContraption.class.getDeclaredField("theoreticalPerTagFluidConsumption");
            tptField.setAccessible(true);
        } catch (Throwable t) {
            tptField = null;
            LOGGER.warn("[unlimitedspace][NAV] could not resolve RocketContraption TPT field", t);
        }
    }

    private CsTravelBridge() {
    }

    /**
     * Locate the rocket the player should launch: the {@code RocketContraptionEntity} they are
     * riding, else the nearest one in their chunk vicinity. Returns {@code null} if none.
     */
    public static RocketContraptionEntity findRocket(ServerPlayer player) {
        Entity e = player.getVehicle();
        while (e != null) {
            if (e instanceof RocketContraptionEntity rocket) {
                return rocket;
            }
            e = e.getVehicle();
        }
        if (player.level() instanceof ServerLevel level) {
            List<RocketContraptionEntity> nearby = level.getEntitiesOfClass(
                    RocketContraptionEntity.class,
                    player.getBoundingBox().inflate(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS),
                    rocket -> rocket.isAlive());
            if (!nearby.isEmpty()) {
                return nearby.get(0);
            }
        }
        return null;
    }

    /**
     * Hand the destination to the official Creating Space launch path. Returns {@code true}
     * when the public handler accepted the destination.
     *
     * <p>Before handing off, we guarantee the official initial-position map contains an entry
     * for this destination (see {@link #ensureInitialPosition}), because CS's
     * {@code CustomTeleporter.getTransition} reads that map and NPEs when the entry is missing —
     * exactly what happened before this fix.
     */
    public static boolean launch(ServerPlayer player, RocketContraptionEntity rocket,
                                  ResourceLocation destination) {
        if (rocket == null || destination == null) {
            return false;
        }
        ensureInitialPosition(rocket, destination);
        // R23.1 FIX ("the rocket refuses to launch even after disassemble/assemble" +
        // "fuel requirement changes between sessions"): CS's handelTrajectoryCalculation
        // computes the trip delta-V as {@code CSDimensionUtil.cost(rocket.originDimension,
        // destination)}. The NORMAL CS flow fills {@code originDimension} inside
        // {@code startNavigation(RocketPath)}, but {@code RocketContraptionLaunchPacket.handle}
        // (our official entry point) sets ONLY {@code destination}. The field therefore stays
        // NULL or holds a STALE origin from an earlier flight, so the cost lookup returns -1
        // (cost(null|unknown, dest)) or another wrong value, the Tsiolkovsky fuel estimate in
        // CS collapses/goes negative and the rocket verdict flips to BLOCKED - silently, and
        // regardless of re-assembling. Pointing the field at the rocket's REAL current
        // dimension makes CS read exactly the (origin -> destination) cost route that
        // AdminNav/ProceduralCsRuntime.ensureCostRoute guarantees just before this call.
        try {
            rocket.originDimension = rocket.level().dimension().location();
        } catch (Throwable ignored) {
            // defensive: never block the launch because of this alignment
        }
        ensureEngineData(rocket);
        new RocketContraptionLaunchPacket(rocket.getId(), destination).handle(player);
        return true;
    }

    /**
     * R23.2 root-cause fix for "thrust 0 N < weight X N" refusals (the user's original
     * "rocket got stuck after landing" report): Creating Space's trajectory calculation
     * derives the rocket thrust from {@code RocketContraption.theoreticalPerTagFluidConsumption}
     * (TPT), while the UI thrust readout uses the plain {@code thrust} int field. The TPT map
     * is silently replaced by an EMPTY map when its codec fails to decode during the
     * contraption's NBT load (the map keys come from the PropellantType registry), so a rocket
     * that lands / gets reloaded in a new dimension ends up with thrust = 9810000 in the UI
     * but thrust = 0 inside CS - and CS refuses every launch with BLOCKED, forever, no matter
     * how often the player disassembles/reassembles in the meantime.
     *
     * <p>Repair: when CS's thrust sum is 0 while the real thrust field is positive, rebuild the
     * TPT map IN PLACE from the engine block entities stored in the contraption's own NBT,
     * replicating EXACTLY the math of {@code RocketContraption.addBlock} (per-engine
     * thrust, ISP and propellant-ratio map). This is the programmatic equivalent of the
     * "disassemble + assemble again" workaround, without touching the world.
     *
     * <p>R23.6: made PUBLIC and also invoked by {@code RocketFlightPlanner.compute} - the same
     * empty TPT also blanked the per-fluid OXYGEN/METHANE req-have rows after arrival, because
     * the per-propellant consumption rates are read from this very map.
     */
    public static void ensureEngineData(RocketContraptionEntity rocket) {
        try {
            if (!(rocket.getContraption() instanceof RocketContraption contraption)
                    || tptField == null) {
                return;
            }
            int csThrust = 0;
            for (RocketContraption.ConsumptionInfo info
                    : contraption.getTPTFluidConsumption().values()) {
                csThrust += info.partialThrust();
            }
            int realThrust = (int) contraption.getThrust();
            if (csThrust != 0 || realThrust <= 0) {
                return; // nothing to repair (either healthy or genuinely engineless)
            }
            HashMap<PropellantType, RocketContraption.ConsumptionInfo> rebuilt = new HashMap<>();
            double ispModifier = 1.0;
            try {
                ispModifier = (Double) CSConfigs.SERVER.rocketEngine.ISPModifier.get();
            } catch (Throwable ignored) {
            }
            int repairedThrust = 0;
            for (StructureBlockInfo block : contraption.getBlocks().values()) {
                if (block.nbt() == null || !block.state().hasBlockEntity()) {
                    continue;
                }
                BlockEntity be;
                try {
                    be = BlockEntity.loadStatic(block.pos(), block.state(), block.nbt(),
                            rocket.level().registryAccess());
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(be instanceof RocketEngineBlockEntity engine)) {
                    continue;
                }
                int thrust = engine.getThrust();
                if (thrust <= 0) {
                    continue;
                }
                repairedThrust += thrust;
                double consumption = thrust
                        / (engine.getIsp() * ispModifier * 9.81d);
                PropellantType type = engine.getPropellantType();
                HashMap<TagKey<Fluid>, Float> ratios =
                        new HashMap<>(type.getPropellantRatio());
                RocketContraption.multiplyMap(ratios, (float) consumption);
                RocketContraption.ConsumptionInfo existing =
                        rebuilt.getOrDefault(type,
                                new RocketContraption.ConsumptionInfo(new HashMap<>(), 0));
                rebuilt.put(type, existing.add(ratios, thrust));
            }
            if (repairedThrust <= 0) {
                LOGGER.warn("[unlimitedspace][NAV] TPT repair found no working engines "
                        + "(contraption thrust field says {})", realThrust);
                return;
            }
            tptField.set(contraption, rebuilt);
            LOGGER.warn("[unlimitedspace][NAV] repaired an EMPTY RocketContraption thrust table "
                    + "in place: csThrust 0 -> {} (thrust field {})", repairedThrust, realThrust);
        } catch (Throwable t) {
            LOGGER.warn("[unlimitedspace][NAV] TPT repair failed (launch continues as-is)", t);
        }
    }

    /**
     * Reproduction root cause fix:
     * <ul>
     *   <li>WHO fills the map in the NORMAL flow: the client sends {@code RocketEntryPosMapClientPacket}
     *       -> {@code RocketContraptionEntity.setInitialPosMap(...)}, and {@code RocketControlsBlockEntity.assemble()}
     *       also calls {@code rocket.setInitialPosMap(block.initialPosMap)} at assembly time.</li>
     *   <li>{@code RocketContraptionLaunchPacket.handle} only sets {@code rocket.destination} and runs
     *       trajectory calculation; it does NOT touch the position map, so an arbitrary admin
     *       destination is missing and {@code CustomTeleporter.getTransition} hits
     *       {@code initialPosMap.get(dest).getX()} == NPE.</li>
     * </ul>
     *
     * <p>This method uses the SAME public setter the official pipeline uses and merges into the
     * existing map (preserving any destinations the player already configured). For the admin
     * target we reuse the rocket's own position as its "initial position" — the exact semantics
     * of the map; CS itself computes the arrival Y from the destination's {@code arrivalHeight}.
     * No coordinates are hard-coded, no parallel map is introduced, nothing is teleported here.
     */
    private static void ensureInitialPosition(RocketContraptionEntity rocket,
                                              ResourceLocation destination) {
        HashMap<ResourceLocation, BlockPos> map = rocket.getInitialPosMap() != null
                ? new HashMap<>(rocket.getInitialPosMap())
                : new HashMap<>();
        if (!map.containsKey(destination)) {
            BlockPos pos = rocket.blockPosition();
            map.put(destination, new BlockPos(pos.getX(), 0, pos.getZ()));
            rocket.setInitialPosMap(map);
        }
    }
}