package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.cs.network.ProceduralCsSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * R14.6.3 networking glue for the seed-aware client synchronization.
 *
 * <p>Registers the {@link ProceduralCsSyncPacket} (server to client) and provides the server-side
 * send helper. The server is authoritative for flight (trajectory, cost, arrival, placement,
 * physics); the client copy only feeds CLIENT-side values (the CS gravity mixin for the local
 * player) and is never authoritative.
 */
public final class ProceduralCsNetworking {

    private static final Logger LOGGER = LogManager.getLogger();

    private ProceduralCsNetworking() {
    }

    /** Register the payload on the MOD bus ({@code RegisterPayloadHandlersEvent} is an IModBusEvent). */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UnlimitedSpace.MODID).versioned("1");
        registrar.playToClient(ProceduralCsSyncPacket.TYPE, ProceduralCsSyncPacket.STREAM_CODEC,
                ProceduralCsClientSync::apply);
        LOGGER.info("[unlimitedspace][R14.6.3] registered ProceduralCsSyncPacket payload");
    }

    /** Send the authoritative seed-aware metadata to one player (called on player login). */
    public static void sendSyncToPlayer(ServerPlayer player) {
        var entries = ProceduralCsRuntime.syncEntries();
        if (entries.isEmpty()) {
            LOGGER.warn("[unlimitedspace][R14.6.3] no seed-aware metadata to sync yet; player={}", player.getGameProfile().getName());
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player, new ProceduralCsSyncPacket(entries));
            LOGGER.info("[unlimitedspace][R14.6.3] sent {} seed-aware procedural entries to client {}",
                    entries.size(), player.getGameProfile().getName());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][R14.6.3] failed to send sync to player {}", player.getGameProfile().getName(), t);
        }
    }

    /**
     * R14.6.4: after a lazy on-demand system expansion the connected clients need the updated
     * seed-aware values; re-send the sync packet to every logged-in player.
     */
    public static void broadcastSyncToPlayers(net.minecraft.server.MinecraftServer server) {
        var entries = ProceduralCsRuntime.syncEntries();
        if (entries.isEmpty()) {
            return;
        }
        var payload = new ProceduralCsSyncPacket(entries);
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                PacketDistributor.sendToPlayer(player, payload);
            } catch (Throwable t) {
                LOGGER.error("[unlimitedspace][R14.6.4] failed to broadcast sync to {}", player.getGameProfile().getName(), t);
            }
        }
        LOGGER.info("[unlimitedspace][R14.6.4] broadcast {} seed-aware entries to {} clients",
                entries.size(), server.getPlayerList().getPlayers().size());
    }
}