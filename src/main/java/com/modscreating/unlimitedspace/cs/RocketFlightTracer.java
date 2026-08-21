package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R14.6.4 diagnostic flight tracer. It watches {@code RocketContraptionEntity} instances on the
 * server and logs the ACTUAL flight/transition state at key markers so the real reason for
 * "rocket rises then falls without a dimension change" is observable in the log:
 *
 * <pre>
 *   ROCKET_FLIGHT_START          - rocket entered the propelled (TRAVELING) phase
 *   ROCKET_REACHED_TRANSITION    - position Y crossed the CS transition threshold (300)
 *   ROCKET_TARGET_LEVEL          - server.getLevel(destination) existence at that moment
 *   ROCKET_DIMENSION_CHANGE      - the rocket changed level (transition succeeded)
 *   ROCKET_FLIGHT_ABORT          - the rocket left the propelled phase WITHOUT changing dimension
 * </pre>
 *
 * Pure read-only instrumentation of the public CS API; it never modifies the rocket or the world.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RocketFlightTracer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final double TRANSITION_Y = 300.0D;

    private static final Map<UUID, FlightState> STATES = new HashMap<>();

    private record FlightState(boolean startLogged, boolean thresholdLogged, boolean targetLogged,
                               ResourceLocation origin, ResourceLocation destination, int lastStatusOrdinal) {
    }

    private RocketFlightTracer() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        // Scan every 10 ticks to keep the overhead negligible.
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            var whole = new net.minecraft.world.phys.AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            java.util.List<RocketContraptionEntity> rockets =
                    level.getEntitiesOfClass(RocketContraptionEntity.class, whole, rocket -> true);
            for (RocketContraptionEntity rocket : rockets) {
                trace(server, rocket);
            }
        }
    }

    private static void trace(MinecraftServer server, RocketContraptionEntity rocket) {
        try {
            UUID id = rocket.getUUID();
            boolean propelled = rocket.isInPropulsionPhase();
            ResourceLocation currentDim = rocket.level().dimension().location();
            ResourceLocation destination = rocket.destination;
            ResourceLocation origin = rocket.originDimension;
            int statusOrdinal = rocket.getEntityData().get(RocketContraptionEntity.STATUS_DATA_ACCESSOR).ordinal();
            FlightState state = STATES.get(id);

            if (!propelled) {
                if (state != null && state.startLogged && state.origin != null && !state.origin.equals(currentDim)) {
                    LOGGER.info("[R14.6.4] ROCKET_DIMENSION_CHANGE: id={} from={} to={} destination={}",
                            id, state.origin, currentDim, destination);
                    STATES.remove(id);
                    return;
                }
                if (state != null && state.startLogged && state.destination != null
                        && state.destination.equals(currentDim)) {
                    LOGGER.info("[R14.6.4] ROCKET_ARRIVED: id={} dimension={} destination={}",
                            id, currentDim, destination);
                    STATES.remove(id);
                    return;
                }
                if (state != null && state.startLogged) {
                    LOGGER.info("[R14.6.4] ROCKET_FLIGHT_ABORT: id={} statusLeftPropelled without dimension change "
                                    + "currentDim={} origin={} destination={}",
                            id, currentDim, origin, destination);
                    STATES.remove(id);
                }
                return;
            }

            if (state == null) {
                STATES.put(id, new FlightState(false, false, false, origin, destination, statusOrdinal));
                return;
            }

            double y = rocket.getY();
            boolean reached = y > TRANSITION_Y;
            boolean changed = state.origin != null && !state.origin.equals(currentDim);

            if (!state.startLogged()) {
                LOGGER.info("[R14.6.4] ROCKET_FLIGHT_START: id={} origin={} destination={} y={} status={}",
                        id, origin, destination, String.format(java.util.Locale.ROOT, "%.1f", y),
                        rocket.getEntityData().get(RocketContraptionEntity.STATUS_DATA_ACCESSOR));
                state = new FlightState(true, false, false, origin, destination, statusOrdinal);
                STATES.put(id, state);
            }

            if (reached && !state.thresholdLogged()) {
                LOGGER.info("[R14.6.4] ROCKET_REACHED_TRANSITION: id={} y={} destination={} status={}",
                        id, String.format(java.util.Locale.ROOT, "%.1f", y), destination,
                        rocket.getEntityData().get(RocketContraptionEntity.STATUS_DATA_ACCESSOR));
                state = new FlightState(true, true, false, origin, destination, statusOrdinal);
                STATES.put(id, state);
            }

            if (reached && state.thresholdLogged() && !state.targetLogged()) {
                boolean targetExists = targetLevel(server, destination) != null;
                String generator = targetExists
                        ? targetLevel(server, destination).getChunkSource().getGenerator().getClass().getSimpleName()
                        : "NONE";
                LOGGER.info("[R14.6.4] ROCKET_TARGET_LEVEL: destination={} server.getLevel={} generator={}",
                        destination, targetExists ? "EXISTS" : "NULL", generator);
                state = new FlightState(true, true, true, origin, destination, statusOrdinal);
                STATES.put(id, state);
            }

            if (changed) {
                LOGGER.info("[R14.6.4] ROCKET_DIMENSION_CHANGE: id={} from={} to={}",
                        id, origin, currentDim);
                STATES.remove(id);
            }
        } catch (Throwable t) {
            LOGGER.error("[R14.6.4] RocketFlightTracer trace failed", t);
        }
    }

    private static ServerLevel targetLevel(MinecraftServer server, ResourceLocation destination) {
        if (destination == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, destination));
    }
}