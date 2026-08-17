package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic water/hydrology profile for one planet (R8).
 *
 * <p>Replaces the R7 behavior of pinning {@code seaLevel = baseHeight}.
 * The sea level is derived from {@code waterCoverage} relative to the terrain
 * span, so a 28%-coverage world floods basins instead of becoming a 50% ocean,
 * and a 90% coverage world becomes a true ocean world.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record PlanetWaterProfile(
        double waterCoverage,
        double seaLevel,
        double coastalBias,
        boolean hasRivers,
        FluidProfile fluid
) {

    public static PlanetWaterProfile create(long planetSeed, PlanetProperties p,
                                            double baseHeight, double amplitude) {
        double coverage = p.waterCoverage();
        boolean gas = p.surface() == com.modscreating.unlimitedspace.core.planets.PlanetSurface.GASEOUS;
        boolean hasWater = !gas && coverage > 0.01;

        double sea;
        if (!hasWater) {
            sea = baseHeight;
        } else {
            // waterCoverage is the fraction of terrain below sea level; solving
            // for a uniform noise span gives the basin-flooding model.
            double lo = baseHeight - amplitude;
            double hi = baseHeight + amplitude;
            sea = baseHeight + amplitude * (2.0 * coverage - 1.0);
            if (sea < lo) sea = lo;
            if (sea > hi) sea = hi;
        }

        double coastal = Math.max(0.0, Math.min(1.0, (coverage - 0.3) / 0.7));
        long riverSeed = Seeds.derive(planetSeed, "us.water.rivers");
        boolean rivers = hasWater && riverSeed % 3 != 0 && coverage < 0.95;

        return new PlanetWaterProfile(coverage, sea, coastal, rivers,
                hasWater ? FluidProfile.WATER : FluidProfile.NONE);
    }
}