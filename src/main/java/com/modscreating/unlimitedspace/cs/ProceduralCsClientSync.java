package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.cs.network.ProceduralCsSyncPacket;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * R14.6.3 CLIENT-side receiver for the seed-aware procedural CS metadata.
 *
 * <p>This class deliberately has NO {@code Dist.CLIENT}-only type in any method signature and is NOT
 * annotated client-only, so the dedicated server can load it (the {@code Minecraft} usage inside
 * method bodies is stripped by the NeoForge dist cleaner; the methods never execute on a server).
 * The handler injects the packet's authoritative seed-aware values into the client's
 * {@code CSDimensionUtil} travel map via the same public API the server uses
 * ({@code CSDimensionUtil.updatePlanetsFromRegistry}). The client map is a CLIENT-side copy for
 * client-side calculations (the CS gravity mixin for the local player); it never overrides server
 * authority (trajectory, dimension change, placement, physics).
 *
 * <p>It deliberately does NOT rebuild the CS cost map on the client (an all-pairs Dijkstra over
 * ~5 600 entries costs ~19 s); the authoritative cost/trajectory is computed on the server and the
 * client UI cost display is informational. It pre-warms the trivial cost map from the frozen
 * registry so a later {@code cost()} call never triggers a large lazy rebuild.
 */
public final class ProceduralCsClientSync {

    private static final Logger LOGGER = LogManager.getLogger();

    private ProceduralCsClientSync() {
    }

    /** Called by the payload handler on the client; dispatches to the client thread. */
    public static void apply(ProceduralCsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> applyOnClientThread(packet))
                .exceptionally(t -> {
                    LOGGER.error("[unlimitedspace][R14.6.3] client sync failed", t);
                    return null;
                });
    }

    /** Runs on the client render/game thread. */
    public static void applyOnClientThread(ProceduralCsSyncPacket packet) {
        try {
            // Pre-warm: the client's lazily built travel map (from the small frozen synced registry)
            // also builds the trivial cost map, so costAdjacentMap stays non-null and no later large
            // lazy cost-map rebuild (which would freeze the client) can be triggered by cost().
            CSDimensionUtil.getTravelMap();
            Registry<RocketAccessibleDimension> official = Minecraft.getInstance().getConnection()
                    .registryAccess().registryOrThrow(RocketAccessibleDimension.REGISTRY_KEY);
            MappedRegistry<RocketAccessibleDimension> reg =
                    new MappedRegistry<>(RocketAccessibleDimension.REGISTRY_KEY, Lifecycle.stable());
            Set<ResourceLocation> overrideKeys = new HashSet<>();
            for (ProceduralCsSyncPacket.Entry e : packet.entries()) {
                overrideKeys.add(e.rl());
            }
            for (Map.Entry<ResourceKey<RocketAccessibleDimension>, RocketAccessibleDimension> e : official.entrySet()) {
                if (!overrideKeys.contains(e.getKey().location())) {
                    reg.register(e.getKey(), e.getValue(), RegistrationInfo.BUILT_IN);
                }
            }
            for (ProceduralCsSyncPacket.Entry e : packet.entries()) {
                reg.register(ResourceKey.create(RocketAccessibleDimension.REGISTRY_KEY, e.rl()),
                        new RocketAccessibleDimension(0, e.orbitedBody(), e.arrivalHeight(), e.gravity(), Map.of()),
                        RegistrationInfo.BUILT_IN);
            }
            CSDimensionUtil.updatePlanetsFromRegistry(reg);
            LOGGER.info("[unlimitedspace][R14.6.3] client travel map updated with {} seed-aware procedural entries",
                    packet.entries().size());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][R14.6.3] client travel map apply failed", t);
        }
    }
}