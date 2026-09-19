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

    public LuminiteRockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Brightness-dependent visibility: a luminite glow reads strongest in the dark.
        int light = level.getMaxLocalRawBrightness(pos);
        double visible = 1.0 - (light / 15.0);
        if (random.nextDouble() > 0.05 + 0.15 * visible) return;   // sparse by design

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
        double y = pos.getY() + 0.9 + random.nextDouble() * 0.15;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7;
        level.addParticle(ParticleTypes.END_ROD, x, y, z,
                0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
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