package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * R14.6.3 CLIENT-ONLY diagnostic command: {@code /usclientcs <rl>} prints the CLIENT-side values for
 * a resource location (travel-map membership, client CSDimensionUtil gravity, arrivalHeight,
 * isOrbit) so the remote client state can be compared directly against the server's
 * {@code /unlimitedspace cscheck <rl>}.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ProceduralCsClientCommand {

    private ProceduralCsClientCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("usclientcs")
                .then(Commands.argument("rl", StringArgumentType.greedyString())
                        .executes(ctx -> traceClient(StringArgumentType.getString(ctx, "rl")))));
    }

    private static int traceClient(String rlString) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(rlString.trim());
            var travelMap = CSDimensionUtil.getTravelMap();
            var entry = travelMap == null ? null : travelMap.get(rl);
            player.displayClientMessage(Component.literal("=== CLIENT PROCEDURAL CS TRACE ==="), false);
            player.displayClientMessage(Component.literal("RL: " + rl), false);
            player.displayClientMessage(Component.literal("Client travelMap membership: "
                    + (travelMap != null && travelMap.containsKey(rl) ? "YES" : "NO")), false);
            player.displayClientMessage(Component.literal("Client CSDimensionUtil gravity: "
                    + (entry == null ? "MISSING (fallback 9.81)" : String.valueOf(entry.gravity()))), false);
            player.displayClientMessage(Component.literal("Client arrivalHeight: "
                    + (entry == null ? "MISSING (fallback 64)" : String.valueOf(entry.arrivalHeight()))), false);
            player.displayClientMessage(Component.literal("Client isOrbit: " + CSDimensionUtil.isOrbit(rl)), false);
        } catch (Throwable t) {
            player.displayClientMessage(Component.literal("usclientcs error: " + t.getMessage()), false);
        }
        return 1;
    }
}