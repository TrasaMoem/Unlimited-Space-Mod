package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.ProceduralDimension;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * R14.8 Objective A — guarantee the surface under a procedural orbit is materialised BEFORE a player
 * can fall through it, so Creating Space's orbit-descent transition
 * ({@code CSEventHandler.entityLivingEvent} → {@code CSDimensionUtil.planetUnder(dim)} →
 * {@code CustomTeleporter.getTransition}) always resolves to a loaded {@code ServerLevel} and lands
 * on the CORRECT body.
 *
 * <p>The authoritative relationship is read from {@code CSDimensionUtil.planetUnder} (which returns
 * the orbit's {@code orbitedBody} as a dimension); this class materialises exactly that surface through
 * the one {@link ProceduralWorldMaterializer} seam and logs the descent proof while a player is
 * actually below the orbit's descent threshold. No gravity or worldgen changes.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ProceduralOrbitFallGuard {

    private static final Logger LOGGER = LogManager.getLogger();

    /** CS descent threshold is {@code dimensionType().minY() + 10}. */
    private static final float DESCENT_MARGIN = 10.0f;

    /** Last orbit-descent logged per player, keyed by uuid; bounded by the live player count. */
    private static final Map<UUID, String> DESCENT_LOGGED = new HashMap<>();

    private ProceduralOrbitFallGuard() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ResourceLocation dimRl = p.level().dimension().location();
            Optional<ProceduralDimension> parsed = ProceduralDimension.parse(dimRl.getPath());
            if (parsed.isEmpty()) {
                DESCENT_LOGGED.remove(p.getUUID());
                continue;
            }
            ProceduralDimension dim = parsed.get();
            if (dim.kind() != ProceduralDimension.Kind.PLANET_ORBIT
                    && dim.kind() != ProceduralDimension.Kind.MOON_ORBIT
                    && dim.kind() != ProceduralDimension.Kind.STAR_ORBIT) {
                DESCENT_LOGGED.remove(p.getUUID());
                continue;
            }

            // Materialise the surface the orbit falls to (idempotent; after first load it is a cheap
            // existing-level lookup). This is what makes server.getLevel(target) non-null at descent.
            Optional<ServerLevel> surface =
                    ProceduralWorldMaterializer.materializeOrbitFallTarget(server, dim);
            if (surface.isEmpty()) {
                continue;
            }

            boolean descending = p.getY() < p.level().dimensionType().minY() + DESCENT_MARGIN;
            if (!descending) {
                continue;
            }
            String descentTag = dimRl.getPath() + "|surface";
            if (descentTag.equals(DESCENT_LOGGED.get(p.getUUID()))) {
                continue;
            }
            DESCENT_LOGGED.put(p.getUUID(), descentTag);

            String currentOrbit = "unlimitedspace:" + dim.resourcePath();
            String orbitedBody = resolvedFallTarget(p.level().dimension().location());
            String expected = dim.fallTarget()
                    .map(t -> "unlimitedspace:" + t.resourcePath()).orElse("?");
            ServerLevel targetLevel = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, surface.get().dimension().location()));

            LOGGER.info("[unlimitedspace][R14.8] ORBIT_DESCENT_BEGIN: currentOrbit={} orbitedBody={} " +
                            "expectedSurface={} resolvedSurface={} y={} condition=fallBelowMinY+{} " +
                            "server.getLevel(target)={}",
                    currentOrbit, orbitedBody, expected, surface.get().dimension().location(),
                    p.getY(), DESCENT_MARGIN, targetLevel != null);
            LOGGER.info("[unlimitedspace][R14.8] ORBIT_DESCENT: targetSurface={} ServerLevel={} " +
                            "exists={} -> CustomTeleporter -> surface arrival",
                    expected, surface.get().dimension(), targetLevel != null);
        }
    }

    /** The authoritative CS fall target for a dimension (its orbitedBody), or "?" if unavailable. */
    private static String resolvedFallTarget(ResourceLocation dim) {
        try {
            var under = CSDimensionUtil.planetUnder(dim);
            return under == null ? "?" : under.location().toString();
        } catch (Throwable t) {
            return "?";
        }
    }
}
