package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.nav.R15Packets;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * R15.1 fallback interaction path: right-clicking the control block ON an assembled
 * rocket opens the R15 control UI bound to that real RocketContraptionEntity.
 *
 * <p>Why a global handler instead of only {@code MovingInteractionBehaviour}: Create
 * serializes the per-contraption {@code interactors} map into NBT AT ASSEMBLY TIME, so
 * rockets assembled before the behaviour was registered keep an empty entry forever and
 * never dispatch the click. This handler checks the actual block under the crosshair and
 * therefore works for every rocket — fresh, old or re-loaded.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID)
public final class USRocketControlEntityInteraction {

    private USRocketControlEntityInteraction() {}

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof RocketContraptionEntity rocket)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return; // act server-side only

        var localPos = event.getLocalPos();
        var contraption = rocket.getContraption();
        if (contraption == null) return;
        StructureBlockInfo info = contraption.getBlocks().get(localPos);
        if (info == null || info.state().getBlock()
                != UnlimitedSpace.ROCKET_CONTROL_TERMINAL.get()) {
            return; // clicked some other block of the rocket
        }

        event.setCanceled(true); // consume the click
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UnlimitedSpace.LOGGER.info(
                    "[unlimitedspace][R15.1] controller clicked on assembled rocket {} - opening navigation UI",
                    rocket.getId());
            // Open the R15 control UI bound to the REAL assembled rocket entity.
            R15Packets.openScreen(serverPlayer, serverLevel.getSeed(),
                    Long.MIN_VALUE, rocket.getId());
        }
    }
}
