package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.terrain.GlobalTerrainFields;

/**
 * Continuous mathematical river mask field.
 *
 * <p>Generates terrain-following river channels derived from continental gradient,
 * valley corridors, and drainage routing without random wandering lines.
 */
public final class RiverMaskField {

    private final long riverSeed;

    public RiverMaskField(long planetSeed) {
        this.riverSeed = Seeds.derive(planetSeed, "us.river.mask");
    }

    /**
     * Compute river mask and depth at (x, z).
     *
     * @param x world X
     * @param z world Z
     * @param wetWeight wetness tendency [0, 1]
     * @param precipitation precipitation factor [0, 1]
     * @param valleyField valley corridor field [0, 1]
     * @return river channel carve in blocks (0 if no river, positive downward carve)
     */
    public double riverCarve(int x, int z, double wetWeight, double precipitation, double valleyField) {
        if (wetWeight <= 0.05 || precipitation <= 0.05) {
            return 0.0;
        }

        // Domain-warped drainage network running through valleys
        double wx = x + 180.0 * (GlobalTerrainFields.value01(riverSeed + 0x11L, x, z, 1.0 / 700.0, 0) - 0.5);
        double wz = z + 180.0 * (GlobalTerrainFields.value01(riverSeed + 0x22L, x, z, 1.0 / 700.0, 1) - 0.5);

        // Primary drainage channel line
        double ridge = GlobalTerrainFields.ridgeField(riverSeed + 0x33L, (int) wx, (int) wz, 1.0 / 550.0, 220.0);
        if (ridge < 0.94) return 0.0;

        // Narrow river core [0.94 .. 1.0] -> [0 .. 1]
        double core = (ridge - 0.94) / 0.06;
        // Rivers strengthen in valleys and lowlands with high precipitation
        double strength = core * (0.3 + 0.7 * valleyField) * wetWeight * precipitation;

        // River carving: 2..12 blocks
        return strength * 10.0;
    }
}
