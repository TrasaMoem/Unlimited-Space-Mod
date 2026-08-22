package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.core.destination.ProceduralDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * R14.8.1 Objective A — server-side dimension restoration lifecycle repair.
 *
 * <p>{@code PlayerList.placeNewPlayer} reads the saved {@code Dimension} from the player NBT and
 * calls {@code server.getLevel(savedKey)}. For a lazy DynamicDimensions procedural world this is
 * {@code null} at placement time, so vanilla logs {@code "Unknown respawn dimension {}, defaulting
 * to overworld"} and places the player in the Overworld. The R14.8 "post-login teleport" then
 * materialises the world and teleports the player, sending a {@code ClientboundRespawnPacket} whose
 * {@code Holder<DimensionType>} Dynamic Dimensions registers on the SERVER <em>after</em> the
 * client's config-phase registry sync. The client's {@code DIMENSION_TYPE} IdMap lacks that id, so
 * decode throws {@code IllegalArgumentException: No value with id N} (verified {@code
 * IdMap.byIdOrThrow(15)}), surfacing as {@code DecoderException: Failed to decode packet
 * 'clientbound/minecraft:respawn'}.
 *
 * <p>Fix: materialise the saved procedural dimension(s) BEFORE any client's config-phase registry
 * sync. That registers each procedural {@code DimensionType} into the server's {@code
 * DIMENSION_TYPE} registry through the SAME {@link DynamicPlanetWorldManager} / Dynamic Dimensions
 * path used everywhere else, so (1) the server's config sync sends the type to the client via normal
 * registry synchronization (no custom injection, no second dimension registry), and (2)
 * {@code server.getLevel(savedKey)} is non-null when {@code placeNewPlayer} needs it, placing the
 * player directly in the procedural dimension — no Overworld flash, no invalid respawn decode.
 *
 * <p>Scans the world {@code playerdata} dir ({@code MinecraftServer.getWorldPath(LevelResource.
 * PLAYER_DATA_DIR)}) and materialises any {@code unlimitedspace:...} procedural dimension a saved
 * player resides in. Bounded by saved-player count, idempotent, never touches vanilla/CS dimensions.
 */
public final class ProceduralReconnectLoader {

    private static final Logger LOGGER = LogManager.getLogger();

    private ProceduralReconnectLoader() {
    }

    /**
     * Run AFTER {@code ProceduralCsRuntime.onServerStarted} has built the seed-aware CS travel map,
     * so each saved procedural dimension is materialised (and its {@code DimensionType} registered
     * in the server's {@code DIMENSION_TYPE} registry) BEFORE any client's config-phase registry
     * sync. Called from the R14.6.2 server-started bridge to guarantee ordering.
     */
    public static void preload(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            materializeSavedProceduralDimensions(server, playerDataDir);
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: server-start scan failed: {}",
                    t.toString());
        }
    }

    private static void materializeSavedProceduralDimensions(MinecraftServer server, Path playerDataDir) {
        if (playerDataDir == null || !Files.isDirectory(playerDataDir)) {
            LOGGER.debug("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: no playerdata dir at {}",
                    playerDataDir);
            return;
        }
        List<Path> dataFiles;
        try (Stream<Path> stream = Files.list(playerDataDir)) {
            dataFiles = stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".dat"))
                    .toList();
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: could not list {}: {}",
                    playerDataDir, t.toString());
            return;
        }
        if (dataFiles.isEmpty()) {
            LOGGER.debug("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: no player .dat files in {}",
                    playerDataDir);
            return;
        }
        for (Path file : dataFiles) {
            try {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                if (tag == null) {
                    continue;
                }
                String dimensionField = tag.getString("Dimension");
                if (dimensionField.isEmpty()) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.parse(dimensionField);
                if (ProceduralDimension.parse(rl.getPath()).isEmpty()) {
                    continue; // vanilla / Creating Space dimension -> leave untouched.
                }
                Optional<ServerLevel> result = ProceduralWorldMaterializer.materialize(server, rl);
                LOGGER.info("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: file={} savedDim={} "
                                + "materialized={} level={}",
                        file.getFileName(), rl, result.isPresent(),
                        result.isPresent() ? result.get().dimension() : "null");
            } catch (Throwable t) {
                LOGGER.warn("[unlimitedspace][R14.8.1] RECONNECT_PRELOAD: failed for {}: {}",
                        file.getFileName(), t.toString());
            }
        }
    }
}
