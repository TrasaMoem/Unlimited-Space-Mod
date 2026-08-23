package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * R15.1: BlockEntity registration for the Unlimited Space Rocket Control Block.
 * The BE type is bound to our block and hosts the CS-compatible assembly brain
 * ({@link USRocketControlBlockEntity}).
 */
public final class USBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UnlimitedSpace.MODID);

    /** Assigned during registration; used by the BE factory (avoids holder self-reference). */
    public static volatile BlockEntityType<USRocketControlBlockEntity> ROCKET_CONTROL_TYPE;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<USRocketControlBlockEntity>>
            ROCKET_CONTROL = BLOCK_ENTITIES.register("rocket_control",
            () -> {
                BlockEntityType<USRocketControlBlockEntity> type = new BlockEntityType<>(
                        // NOTE: 1.21.1 BlockEntitySupplier signature is (pos, state).
                        (pos, state) -> new USRocketControlBlockEntity(ROCKET_CONTROL_TYPE, pos, state),
                        java.util.Set.of(UnlimitedSpace.ROCKET_CONTROL_TERMINAL.get()), null);
                ROCKET_CONTROL_TYPE = type;
                return type;
            });

    private USBlockEntities() {}
}
