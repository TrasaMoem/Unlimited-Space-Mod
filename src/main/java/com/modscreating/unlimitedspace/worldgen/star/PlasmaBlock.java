package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.core.worldgen.StarSurfaceBlockFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R14.9.3-C — the custom star-surface plasma block. Solid and collidable (never a lava fluid).
 *
 * <p>As requested, any plasma block, when the player stands on it, hurts <b>4x harder than vanilla magma</b>
 * (R14.9.3-E follow-up doubled the original 2x value): {@link StarSurfaceBlockFamily#PLASMA_STAND_DAMAGE}
 * = 4.0 vs magma's base {@link StarSurfaceBlockFamily#MAGMA_DAMAGE} = 1.0. It ALSO IGNITES the entity
 * standing on it (the star's plasma sets you on fire). While the burning entity is on a star surface,
 * the client tints the flame overlay a ruby red ({@code RubyFlameTint}) instead of vanilla orange.
 */
public class PlasmaBlock extends MagmaBlock {

    /** Seconds of fire applied by standing on star plasma. */
    public static final int PLASMA_IGNITE_SECONDS = 5;

    public PlasmaBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        // R14.9.3-C: plasma hurts harder than vanilla magma. Magma's base stepOn deals 1.0; we do NOT
        // call super so the total is exactly PLASMA_STAND_DAMAGE (now 4.0), not 5.0.
        if (!entity.isSteppingCarefully() && !entity.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), StarSurfaceBlockFamily.PLASMA_STAND_DAMAGE);
            // R14.9.3-E follow-up: standing on star plasma SETS YOU ON FIRE (ruby-tinted client-side).
            entity.igniteForSeconds(PLASMA_IGNITE_SECONDS);
        }
    }
}