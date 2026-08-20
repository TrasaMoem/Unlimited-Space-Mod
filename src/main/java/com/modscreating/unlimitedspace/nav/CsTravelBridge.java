package com.modscreating.unlimitedspace.nav;

import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.rae.creatingspace.content.rocket.network.RocketContraptionLaunchPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;

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
        new RocketContraptionLaunchPacket(rocket.getId(), destination).handle(player);
        return true;
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