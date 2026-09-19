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

    public GeothermalVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextDouble() > 0.30) return;   // periodic, not continuous

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        // Steam plume; ~1/6 of plumes carry a hot ember (the vent's molten character).
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.06, 0.0);
        } else {
            level.addParticle(ParticleTypes.CLOUD, x, y, z,
                    0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
        }
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