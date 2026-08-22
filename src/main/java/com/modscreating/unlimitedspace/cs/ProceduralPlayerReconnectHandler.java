package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.ProceduralDimension;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * R14.8 Objective C вЂ” reconnect into the player's previous procedural dimension (and approximate
 * position) instead of being dumped into the Overworld.
 *
 * <p>Vanilla {@code PlayerList.placeNewPlayer} reads the saved {@code Dimension} from the player NBT,
 * calls {@code server.getLevel(savedKey)} and, when it returns {@code null} (which a lazy
 * DynamicDimensions procedural world always does before first materialisation), logs
 * {@code "Unknown respawn dimension {}, defaulting to overworld"} and places the player in the
 * Overworld. That is the verified failure (1.21.1 {@code PlayerList.placeNewPlayer}:160-167).
 *
 * <p>R14.8.1 repairs this BEFORE placement, not after. {@link ProceduralReconnectLoader#preload}, wired
 * into {@code ServerStartedEvent} (LOWEST, after the CS travel map is built), materialises every saved
 * procedural dimension BEFORE the first client's config-phase registry sync. This both (1) makes
 * {@code server.getLevel(savedKey)} non-null when {@code placeNewPlayer} needs it, so the player is
 * placed directly in the saved procedural dimension (no Overworld flash, no invalid respawn packet),
 * and (2) registers that dimension's {@code DimensionType} into the server registry before the sync, so
 * the client can decode the resulting respawn packet (no {@code DecoderException}).
 *
 * <p>This class additionally persists the player's last procedural position on logout (POS_TAG) and, on
 * login, re-materialises the dimension idempotently through the same {@link ProceduralWorldMaterializer}
 * seam as a correctness guard. It deliberately does NOT perform a post-login {@code teleportTo}: that
 * was the R14.8 "only fix" and it sends a {@code ClientboundRespawnPacket} whose runtime-registered
 * {@code DimensionType} the client has not necessarily synced, re-triggering the decoder crash.
 *
 * <p>Only a recognisable procedural dimension is ever acted on; invalid/corrupt/vanilla dimensions
 * keep vanilla behaviour. We never fall back to Overworld for a <em>valid</em> procedural world.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ProceduralPlayerReconnectHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Persistent-data tag holding the player's last procedural position across a disconnect. */
    private static final String POS_TAG = "unlimitedspace_last_procedural_pos";
    private static final String KEY_DIM = "dim";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YAW = "yaw";
    private static final String KEY_PITCH = "pitch";

    /** Fallback arrival Y (blocks) used when no persisted position is available, kept clear of terrain. */
    private static final double FALLBACK_ARRIVAL_Y = 200.0;

    private ProceduralPlayerReconnectHandler() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (server == null) {
            return;
        }

        // The dimension to restore is the CURRENT dimension the player was in when they disconnected,
        // which we persist on logout (POS_TAG). It is deliberately NOT player.getRespawnDimension():
        // that field is the RESPAWN point (the "SpawnDimension" NBT key), not the "Dimension" key that
        // PlayerList.placeNewPlayer reads to decide where to place the player вЂ” and which then falls
        // back to the Overworld when the lazy procedural world is not yet materialised. We mirror the
        // exact vanilla fallback (Overworld) only when the procedural dimension is invalid/unrecognised.
        String savedDimStr = savedDimension(player);
        if (savedDimStr == null) {
            return; // not a recognised procedural disconnect -> vanilla behaviour
        }
        ResourceLocation rl;
        try {
            rl = ResourceLocation.parse(savedDimStr);
        } catch (Throwable t) {
            LOGGER.warn("[unlimitedspace][R14.8] PLAYER_DIMENSION_RESOLVE: bad saved dim '{}'", savedDimStr);
            return;
        }
        if (ProceduralDimension.parse(rl.getPath()).isEmpty()) {
            return;
        }

        ServerLevel before = server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        Optional<ServerLevel> target = ProceduralWorldMaterializer.materialize(server, rl);
        if (target.isEmpty()) {
            LOGGER.error("[unlimitedspace][R14.8] PLAYER_DIMENSION_RESOLVE: requestedRL={} "
                            + "server.getLevel(initial)={} materialize=FAILED finalRL=overworld (invalid/corrupt)",
                    rl, before == null ? "null" : before.dimension());
            return;
        }
        ServerLevel level = target.get();

        if (player.level().dimension().location().equals(level.dimension().location())) {
            LOGGER.info("[unlimitedspace][R14.8] PLAYER_DIMENSION_RESOLVE: requestedRL={} "
                            + "server.getLevel=present alreadyPlaced=true finalRL={}", rl, level.dimension());
            return;
        }

        Vec3 pos = savedPosition(player, level);
        float yaw = savedYaw(player);
        float pitch = savedPitch(player);
        LOGGER.info("[unlimitedspace][R14.8] PLAYER_DIMENSION_RESOLVE: requestedRL={} "
                        + "server.getLevel(initial)={} dynamicDimensions=materialized finalRL={} "
                        + "finalPos=({},{},{}) yaw={} pitch={}",
                rl, before == null ? "null" : before.dimension(), level.dimension(),
                pos.x, pos.y, pos.z, yaw, pitch);

        // R14.8.1: placeNewPlayer now restores the player directly into the saved procedural dimension
        // (ProceduralReconnectLoader materialises it at ServerStartedEvent, BEFORE the config-phase
        // registry sync, so the level is non-null there). The R14.8 post-login teleport is no longer
        // the fix and is NOT performed: teleporting here would send a ClientboundRespawnPacket whose
        // DimensionType the client has not necessarily synced, re-triggering the decoder crash.
        // If the player somehow is not already in the target dimension (should not happen with the
        // preload fix), we log and leave vanilla placement untouched rather than risk the crash.
        if (!player.level().dimension().location().equals(level.dimension().location())) {
            LOGGER.warn("[unlimitedspace][R14.8.1] PLAYER_NOT_RESTORED: uuid={} current={} target={} "
                            + "(no teleport to avoid respawn decode crash)",
                    player.getUUID(), player.level().dimension().location(), level.dimension().location());
        } else {
            LOGGER.info("[unlimitedspace][R14.8.1] PLAYER_RESTORED: uuid={} dim={} pos=({},{},{})",
                    player.getUUID(), level.dimension(), pos.x, pos.y, pos.z);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> dim = player.level().dimension();
        ResourceLocation rl = dim.location();
        if (ProceduralDimension.parse(rl.getPath()).isEmpty()) {
            // Only track positions inside procedural worlds; keep vanilla data untouched.
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_DIM, rl.toString());
        tag.putDouble(KEY_X, player.getX());
        tag.putDouble(KEY_Y, player.getY());
        tag.putDouble(KEY_Z, player.getZ());
        tag.putFloat(KEY_YAW, player.getYRot());
        tag.putFloat(KEY_PITCH, player.getXRot());
        player.getPersistentData().put(POS_TAG, tag);
        // Verified: PlayerList.remove fires PlayerLoggedOutEvent (line 370) BEFORE PlayerList.save
        // (line 373), so this write is persisted with the player.
        LOGGER.info("[unlimitedspace][R14.8] PLAYER_SAVE: uuid={} dimension={} pos=({},{},{})",
                player.getUUID(), rl, player.getX(), player.getY(), player.getZ());
    }

    private static Vec3 savedPosition(ServerPlayer player, ServerLevel level) {
        CompoundTag tag = player.getPersistentData().getCompound(POS_TAG);
        boolean matches = tag.contains(KEY_DIM)
                && level.dimension().location().toString().equals(tag.getString(KEY_DIM))
                && tag.contains(KEY_X) && tag.contains(KEY_Y) && tag.contains(KEY_Z);
        if (matches) {
            return new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        }
        // No usable saved position (e.g. first reconnect after install): world spawn, raised to a
        // safe arrival height above terrain (CS surface arrival semantics, 200 вЂ” above any procedural
        // terrain the generator produces), never inside the ground.
        Vec3 spawn = level.getSharedSpawnPos().getBottomCenter();
        double arrival = Math.min(FALLBACK_ARRIVAL_Y, level.getMaxBuildHeight() - 8.0);
        return new Vec3(spawn.x, Math.max(arrival, spawn.y), spawn.z);
    }

    private static float savedYaw(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(POS_TAG);
        return tag.contains(KEY_YAW) ? tag.getFloat(KEY_YAW) : player.getYRot();
    }

    private static float savedPitch(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(POS_TAG);
        return tag.contains(KEY_PITCH) ? tag.getFloat(KEY_PITCH) : player.getXRot();
    }

    /**
     * The player's last procedural dimension, taken from the record persisted on logout (the
     * authoritative current-dimension the player actually was in). If no such record exists we fall
     * back to the respawn dimension as a secondary heuristic (e.g. a player who slept in a procedural
     * world before the tag existed). Returns {@code null} when neither resolves to a procedural body,
     * in which case vanilla behaviour (including the Overworld fallback for an unknown dimension) is
     * left untouched.
     */
    private static String savedDimension(ServerPlayer player) {
        String fromTag = player.getPersistentData().getCompound(POS_TAG).getString(KEY_DIM);
        if (!fromTag.isEmpty()) {
            return fromTag;
        }
        ResourceLocation respawn = player.getRespawnDimension().location();
        if (ProceduralDimension.parse(respawn.getPath()).isPresent()) {
            return respawn.toString();
        }
        return null;
    }
}
