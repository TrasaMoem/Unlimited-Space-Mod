package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.nav.R15Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * R15.1: the Unlimited Space Rocket Control Block - a FUNCTIONAL REPLACEMENT of the
 * Creating Space Rocket Controls Block, extended with the Unlimited Space navigation UI.
 *
 * Primary job (same as CS): detect/assemble a glued rocket structure around this block
 * through the REAL Creating Space contraption pipeline (USRocketControlBlockEntity),
 * then control that rocket. The navigation map opens from the same screen and is only an
 * extension - destination selection stays secondary to rocket assembly/validity.
 *
 * Visually still parents the real creatingspace:block/rocket_controls model.
 */
public class RocketControlTerminalBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RocketControlTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ---- BlockEntity (the CS-equivalent assembly brain) ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new USRocketControlBlockEntity(USBlockEntities.ROCKET_CONTROL.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != USBlockEntities.ROCKET_CONTROL.get()) return null;
        return (lvl, pos, st, be) -> ((USRocketControlBlockEntity) be).tick();
    }

    // ---- interaction ----

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Legacy-placement fix: blocks placed BEFORE R15.1 have no BlockEntity.
            // Attach one lazily so control actions work for old placements too.
            if (!(serverLevel.getBlockEntity(pos)
                    instanceof com.modscreating.unlimitedspace.block.USRocketControlBlockEntity)) {
                serverLevel.setBlockEntity(new USRocketControlBlockEntity(
                        USBlockEntities.ROCKET_CONTROL_TYPE, pos, state));
                UnlimitedSpace.LOGGER.info(
                        "[unlimitedspace][R15.1] attached Rocket Control BlockEntity to legacy block at {}",
                        pos);
            }
            UnlimitedSpace.LOGGER.info("[unlimitedspace][R15.1] control block used at {} - opening navigation UI", pos);
            // Server-authoritative open: seed for the galaxy map + block position so every
            // control action (assemble/disassemble/schedule/status) targets THIS block.
            R15Packets.openScreen(serverPlayer, serverLevel.getSeed(), pos);
        }
        return InteractionResult.SUCCESS;
    }
}
