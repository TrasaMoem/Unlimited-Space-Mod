package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * R15.1 compatibility fix: injects {@link USRocketControlInteraction} into EVERY
 * RocketContraptionEntity whose structure contains our control block.
 *
 * <p>Why reflection is needed: Create populates {@code Contraption.interactors} ONLY during
 * assembly and then PERSISTS that map into the contraption NBT. Rockets assembled before
 * this mod version was updated carry an interactors map WITHOUT our behaviour, and clicks
 * are dispatched exclusively through that map — so the controller could never be clicked.
 * On entity load (both sides) we add the behaviour for any missing entry; the client copy
 * must return true so Create sends the interaction packet to the server, which then opens
 * the R15 UI bound to the real rocket entity.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID)
public final class USRocketContraptionInteractorInjector {

    private static final Field INTERACTORS_FIELD;

    static {
        try {
            INTERACTORS_FIELD = Contraption.class.getDeclaredField("interactors");
            INTERACTORS_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private USRocketContraptionInteractorInjector() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof RocketContraptionEntity rocket)) return;
        if (event.getLevel().isClientSide) {
            // client copy must acknowledge the click so Create forwards it to the server
            inject(rocket, event.getLevel(), false);
        } else {
            // server side performs the authoritative open
            event.getLevel().getServer().execute(() -> inject(rocket, event.getLevel(), true));
        }
    }

    private static void inject(RocketContraptionEntity rocket, Level level, boolean server) {
        Contraption contraption = rocket.getContraption();
        if (contraption == null) return;
        try {
            @SuppressWarnings("unchecked")
            Map<BlockPos, MovingInteractionBehaviour> interactors =
                    (Map<BlockPos, MovingInteractionBehaviour>) INTERACTORS_FIELD.get(contraption);
            if (interactors == null) return;
            boolean changed = false;
            for (var entry : contraption.getBlocks().entrySet()) {
                if (entry.getValue().state().getBlock()
                        != UnlimitedSpace.ROCKET_CONTROL_TERMINAL.get()) continue;
                if (interactors.containsKey(entry.getKey())) continue;
                interactors.put(entry.getKey(), new USRocketControlInteraction());
                changed = true;
            }
            if (changed) {
                UnlimitedSpace.LOGGER.info(
                        "[unlimitedspace][R15.1] injected control-block interactor into rocket {} ({})",
                        rocket.getId(), server ? "server" : "client");
            }
        } catch (Throwable t) {
            UnlimitedSpace.LOGGER.warn(
                    "[unlimitedspace][R15.1] failed to inject interactor into rocket {}", rocket.getId(), t);
        }
    }
}
