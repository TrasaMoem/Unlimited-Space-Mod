package com.modscreating.unlimitedspace.worldgen.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R19 environmental feature block: the GEOTHERMAL VENT.
 *
 * <p>A natural object (NOT a multi-block machine, NOT a BlockEntity):
 * <ul>
 *   <li>{@link #animateTick} — periodic steam plumes (with the occasional hot ember — the
 *       vent exhales its province's own fluid character);</li>
 *   <li>{@link #stepOn} — a SMALL environmental hazard: standing on an open vent hurts like
 *       vanilla magma (1.0) unless stepping carefully; living entities only (dropped items
 *       survive, per the R14.9.3-E lesson).</li>
 * </ul>
 *
 * <p>Generation: rare, deterministic, GEOTHERMAL/VOLCANIC provinces only (see
 * {@code PlanetFeaturePlacer.applyFluidFeatures}).
 */
public class GeothermalVentBlock extends Block {

    /** Seconds-equivalent: vent heat = vanilla magma's base hazard (small, ignorable). */
    public static final float VENT_STAND_DAMAGE = 1.0f;

    /**
     * ACT 6 section 7: the per-emitter cooldown, in ticks. A vent may emit once every 24 ticks
     * (about 1.2 s) instead of on 30% of ALL 20 ticks - the old rate produced a permanent steam
     * column around every visible vent, and a vent field turned into a particle storm.
     */
    public static final int PARTICLE_COOLDOWN_TICKS = 24;

    public GeothermalVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // ACT 6 section 7, gate 1: an emitter no player can see is pure cost - stay silent.
        if (!EnvironmentalParticleThrottle.visible(nearestPlayerDistanceSq(level, pos))) return;
        // ACT 6 section 7, gate 2: the deterministic per-position cooldown. This is what turns a
        // per-block per-tick dice roll into a bounded per-emitter rate, so a field of N vents adds
        // up to a known, modest particle count instead of a storm.
        if (!EnvironmentalParticleThrottle.cooldownElapsed(pos.asLong(), level.getGameTime(),
                PARTICLE_COOLDOWN_TICKS)) {
            return;
        }

        // A rare BURST is preserved: a plume is one to three particles emitted together, so the
        // vent still reads as a distinct occasional event rather than a uniform shimmer.
        int count = 1 + (random.nextInt(8) == 0 ? 2 : 0);
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            // Steam plume; ~1/6 of plumes carry a hot ember (the vent's molten character).
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.06, 0.0);
            } else {
                level.addParticle(ParticleTypes.CLOUD, x, y, z,
                        0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
            }
        }
    }

    /** Squared distance to the closest player, or infinity when the level has no player. */
    private static double nearestPlayerDistanceSq(Level level, BlockPos pos) {
        for (net.minecraft.world.entity.player.Player p : level.players()) {
            double d = p.getX() - pos.getX();
            double e = p.getY() - pos.getY();
            double f = p.getZ() - pos.getZ();
            double sq = d * d + e * e + f * f;
            if (sq < EnvironmentalParticleThrottle.MAX_VISIBLE_DISTANCE
                    * EnvironmentalParticleThrottle.MAX_VISIBLE_DISTANCE) {
                return sq;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        // Small hazard, vanilla-magma semantics: living entities only, careful-stepping safe,
        // no super call so the total is exactly VENT_STAND_DAMAGE (never a double hit).
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) return;
        if (!entity.isSteppingCarefully() && !entity.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), VENT_STAND_DAMAGE);
        }
    }
}