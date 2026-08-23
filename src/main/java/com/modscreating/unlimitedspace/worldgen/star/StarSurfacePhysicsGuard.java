package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * R14.9.3-E follow-up BUG FIX: on star surfaces, dropped items fell THROUGH the plasma ground.
 * Root cause: Creating Space's {@code ItemEntityMixin} injects the dimension's EXTREME procedural
 * gravity into dropped items, so a single tick's vertical step overshoots the thin plasma floor.
 *
 * <p>Fix (per decision): dropped items on star surfaces get VANILLA EARTH ITEM GRAVITY. The item's
 * own tick is cancelled (so the CS extreme-gravity injection never runs) and this guard applies the
 * exact vanilla {@code ItemEntity} physics instead: {@code vy = (vy - 0.04) * 0.98}, terminal
 * velocity {@code -3.92} blocks/tick, horizontal drag {@code x0.91}, then a normal colliding
 * {@code move(MoverType.SELF, ...)}. Items fall, land and rest on the plasma exactly like on an
 * ordinary overworld ground — and can never tunnel through it, because the speeds involved are the
 * same as vanilla's. The CS gravity VALUE itself is untouched — players/rockets are unaffected.
 *
 * <p>Additionally, any item already embedded in solid plasma or below the surface column is
 * teleported back on top of the surface immediately (legacy saves).
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID)
public final class StarSurfacePhysicsGuard {

    /** Exact vanilla {@code ItemEntity} gravity per tick (blocks/tick²). */
    public static final double VANILLA_ITEM_GRAVITY_PER_TICK = 0.04;
    /** Exact vanilla {@code ItemEntity} vertical drag factor. */
    public static final double VANILLA_ITEM_VERTICAL_DRAG = 0.98;
    /** Exact vanilla {@code ItemEntity} horizontal drag factor. */
    public static final double VANILLA_ITEM_HORIZONTAL_DRAG = 0.91;
    /** Exact vanilla {@code ItemEntity} terminal fall speed (blocks/tick). */
    public static final double VANILLA_ITEM_TERMINAL_VELOCITY = -3.92;

    /** How many blocks above an embedded item we scan for free air with support under it. */
    private static final int MAX_RESCUE_SCAN_UP = 24;

    private StarSurfacePhysicsGuard() {
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        Level level = item.level();
        if (!(level instanceof ServerLevel server)) return;
        if (!isStarSurfaceWorld(server)) return;

        // 0) Rescue: lift any item already embedded in solid blocks or sunk below the column top.
        BlockPos pos = item.blockPosition();
        BlockState at = server.getBlockState(pos);
        boolean embedded = !at.isAir() && !at.getCollisionShape(server, pos).isEmpty();
        int columnTop = server.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        boolean belowSurface = item.getY() < columnTop - 1.0;
        if (embedded || belowSurface) {
            int x = pos.getX();
            int z = pos.getZ();
            int startY = Math.max(columnTop, pos.getY());
            for (int dy = 0; dy <= MAX_RESCUE_SCAN_UP; dy++) {
                BlockPos candidate = new BlockPos(x, startY + dy, z);
                BlockState st = server.getBlockState(candidate);
                BlockState below = server.getBlockState(candidate.below());
                boolean free = st.isAir() || st.getCollisionShape(server, candidate).isEmpty();
                boolean supported = !below.isAir()
                        && !below.getCollisionShape(server, candidate.below()).isEmpty();
                if (free && supported) {
                    item.teleportTo(x + 0.5, candidate.getY() + 0.05, z + 0.5);
                    break;
                }
            }
        }

        // 1) VANILLA item gravity on star surfaces: cancel the real tick (which would inject the
        //    extreme CS gravity) and run the exact vanilla ItemEntity physics ourselves.
        Vec3 v = item.getDeltaMovement();
        double vx = v.x * VANILLA_ITEM_HORIZONTAL_DRAG;
        double vy = Math.max(
                (v.y - VANILLA_ITEM_GRAVITY_PER_TICK) * VANILLA_ITEM_VERTICAL_DRAG,
                VANILLA_ITEM_TERMINAL_VELOCITY);
        double vz = v.z * VANILLA_ITEM_HORIZONTAL_DRAG;
        item.setDeltaMovement(vx, vy, vz);
        item.move(MoverType.SELF, new Vec3(vx, vy, vz));
        item.fallDistance = 0.0f;

        // R14.9.3-E FIX ("cannot pick up blocks from the floor"): cancelling the item tick above also
        // freezes vanilla's PICKUP DELAY countdown, which lives inside {@code ItemEntity.tick()}.
        // A freshly mined block spawns with pickupDelay = 10 ticks; with the tick cancelled it stayed 10
        // forever, so the item lay on the floor but could NEVER be collected. We drive physics ourselves,
        // so we make the item collectible immediately instead of replicating vanilla's countdown.
        item.setPickUpDelay(0);

        event.setCanceled(true);
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

