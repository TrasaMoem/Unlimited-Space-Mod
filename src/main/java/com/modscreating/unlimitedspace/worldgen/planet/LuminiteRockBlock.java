package com.modscreating.unlimitedspace.worldgen.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R19 animated proof-of-concept: the Luminite Rock.
 *
 * <p>Subtle client-side ambient life, WITHOUT a BlockEntity (per the R19 performance rules):
 * <ul>
 *   <li>{@link #animateTick} — sparse glow motes, brightness-dependent: the darker the
 *       surroundings, the more visible the glow (frequency scales with {@code 1 - light/15});</li>
 *   <li>{@link #stepOn} — a RARE, DETERMINISTIC reaction: only pre-destined "reactive"
 *       positions (a fixed subset of block positions) flash a small sparkle when stepped on.
 *       Same position → same reaction, every time; no world state, no RNG retention.</li>
 * </ul>
 *
 * <p>No ticking BE, no random allocations beyond the vanilla animateTick draw; particles are
 * purely visual and never affect world generation or gameplay state.
 */
public class LuminiteRockBlock extends Block {

    /**
     * ACT 6 section 7: the per-emitter cooldown, in ticks. A luminite rock may emit once every 40
     * ticks (about 2 s) instead of on 5-20% of ALL 20 ticks. Luminite veins are common, so the old
     * per-block rate was the dominant contributor to the screen-wide shimmer.
     */
    public static final int PARTICLE_COOLDOWN_TICKS = 40;

    public LuminiteRockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // ACT 6 section 7, gate 1: an emitter no player can see is pure cost - stay silent.
        if (!EnvironmentalParticleThrottle.visible(nearestPlayerDistanceSq(level, pos))) return;
        // ACT 6 section 7, gate 2: the deterministic per-position cooldown. The brightness term
        // below now only MODULATES the rare burst, it no longer decides whether every block
        // animates on its own every tick.
        if (!EnvironmentalParticleThrottle.cooldownElapsed(pos.asLong(), level.getGameTime(),
                PARTICLE_COOLDOWN_TICKS)) {
            return;
        }
        // Brightness-dependent visibility: a luminite glow reads strongest in the dark. The term
        // can now only SUPPRESS a burst, never scale the base rate up.
        int light = level.getMaxLocalRawBrightness(pos);
        double visible = 1.0 - (light / 15.0);
        if (random.nextDouble() > 0.35 + 0.65 * visible) return;   // sparse by design

        // A rare BURST is preserved so a nearby rock still reads as a distinct glint.
        int count = 1 + (random.nextInt(10) == 0 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            double y = pos.getY() + 0.9 + random.nextDouble() * 0.15;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
            level.addParticle(ParticleTypes.END_ROD, x, y, z,
                    0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
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
        super.stepOn(level, pos, state, entity);
        // Rare deterministic reaction: (pos.hashCode() & 31) == 0 pre-destines ~1/32 of all
        // luminite positions as "reactive". No per-entity state, no world RNG consumption.
        if (!(level instanceof ServerLevel server)) return;
        if ((pos.hashCode() & 31) != 0) return;
        if (!entity.isSteppingCarefully()) {
            server.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    2, 0.2, 0.05, 0.2, 0.01);
        }
    }
}