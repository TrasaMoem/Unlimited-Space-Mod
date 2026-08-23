package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * R14.9.3-E follow-up BUG FIX: on star surfaces, dropped items appeared to FALL THROUGH the plasma
 * ground. Root cause: with extreme procedural gravity, a dropped item's per-tick vertical velocity
 * grows so large that its single-tick movement step overshoots the thin collision response and the
 * item ends up embedded under the surface.
 *
 * <p>Fix WITHOUT touching the gravity mechanics: for ITEM entities on star-surface worlds only,
 * clamp the per-tick downward motion to a collision-safe maximum (the gravity VALUE in the CS
 * metadata is unchanged; players/rockets are unaffected). A small rescue also lifts any item that
 * is already stuck inside solid plasma back to the surface.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID)
public final class StarSurfacePhysicsGuard {

    /** Collision-safe maximum item fall distance per tick (well under one block). */
    public static final double MAX_ITEM_FALL_PER_TICK = 0.75;

    private StarSurfacePhysicsGuard() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        Level level = item.level();
        if (!(level instanceof ServerLevel server)) return;
        if (!isStarSurfaceWorld(server)) return;

        // 1) Clamp per-tick fall speed so collision can always resolve against the plasma floor.
        Vec3 v = item.getDeltaMovement();
        if (v.y < -MAX_ITEM_FALL_PER_TICK) {
            item.setDeltaMovement(v.x, -MAX_ITEM_FALL_PER_TICK, v.z);
            item.fallDistance = 0.0f;
        }

        // 2) Rescue anything already embedded inside solid blocks (legacy stuck items).
        BlockPos pos = item.blockPosition();
        BlockState state = server.getBlockState(pos);
        if (!state.isAir() && !state.getCollisionShape(server, pos).isEmpty() && !item.onGround()) {
            BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            for (int i = 0; i < 8 && !server.getBlockState(scan).isAir(); i++) {
                scan.move(0, 1, 0);
            }
            if (server.getBlockState(scan).isAir()) {
                item.teleportTo(pos.getX() + 0.5, scan.getY(), pos.getZ() + 0.5);
                item.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * True only for the procedural STAR SURFACE worlds ({@code unlimitedspace:star/<code>/surface}).
     * Orbits stay zero-g, planets/moons are untouched.
     */
    public static boolean isStarSurfaceWorld(Level level) {
        String path = level.dimension().location().getPath();
        return path.startsWith("star/") && path.endsWith("/surface");
    }
}
