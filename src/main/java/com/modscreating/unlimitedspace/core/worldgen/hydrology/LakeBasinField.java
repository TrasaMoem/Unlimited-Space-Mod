package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.terrain.GlobalTerrainFields;

/**
 * Continuous lake basin field.
 *
 * <p>Identifies actual physical depressions (continental basins, caldera floors,
 * valley sinks) capable of holding standing water bodies without hardcoded circles.
 */
public final class LakeBasinField {

    private final long lakeSeed;

    public LakeBasinField(long planetSeed) {
        this.lakeSeed = Seeds.derive(planetSeed, "us.lake.basin");
    }

    /**
     * Compute lake mask in [0, 1] at (x, z).
     *
     * @param x world X
     * @param z world Z
     * @param wetWeight planetary wet tendency [0, 1]
     * @param basinField large-scale continental basin field [0, 1]
     * @param valleyField valley corridor field [0, 1]
     * @return continuous lake mask [0, 1]
     */
    public double lakeMask(int x, int z, double wetWeight, double basinField, double valleyField) {
        if (wetWeight <= 0.08) return 0.0;

        // Lakes emerge in deep continental basins and valley junctions
        double depression = basinField * 0.7 + valleyField * 0.3;
        if (depression < 0.65) return 0.0;

        double warpX = x + 120.0 * (GlobalTerrainFields.value01(lakeSeed + 0x1A, x, z, 1.0 / 400.0, 0) - 0.5);
        double warpZ = z + 120.0 * (GlobalTerrainFields.value01(lakeSeed + 0x1B, x, z, 1.0 / 400.0, 1) - 0.5);

        double localDepression = GlobalTerrainFields.value01(lakeSeed + 0x33L, (int) warpX, (int) warpZ, 1.0 / 300.0, 2);
        double combined = (depression - 0.65) / 0.35 * localDepression * wetWeight;

        return Math.max(0.0, Math.min(1.0, (combined - 0.25) / 0.45));
    }
}
