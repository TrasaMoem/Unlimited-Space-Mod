package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.nav.R15Packets;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * R15.1: interaction with the control block ON the already-assembled rocket.
 *
 * <p>Once assembled, the control block lives inside the RocketContraptionEntity (Create
 * removed it from the world), so the ordinary block-use path no longer fires. This is the
 * CS-equivalent of {@code RocketControlInteraction}, but instead of the schedule-only menu
 * it opens the FULL Unlimited Space control UI bound to the real rocket entity:
 * STATUS / DISASSEMBLE / SCHEDULE / DESTINATION / LAUNCH.
 */
public class USRocketControlInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
                                           com.simibubi.create.content.contraptions.AbstractContraptionEntity contraption) {
        UnlimitedSpace.LOGGER.info("[unlimitedspace][R15.1] control-block interaction inside contraption {}",
                contraption == null ? "null" : contraption.getClass().getSimpleName());
        if (!(contraption instanceof RocketContraptionEntity rocket)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer
                && contraption.level() instanceof ServerLevel serverLevel) {
            // Open the R15 control UI bound to the REAL assembled rocket entity
            // (blockPos = MIN sentinel: entity mode).
            R15Packets.openScreen(serverPlayer, serverLevel.getSeed(),
                    Long.MIN_VALUE, rocket.getId());
            UnlimitedSpace.LOGGER.info("[unlimitedspace][R15.1] opened navigation UI bound to rocket entity {}",
                    rocket.getId());
        }
        return true;
    }
}

