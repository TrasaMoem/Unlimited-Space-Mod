package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Planetary wind direction and speed field.
 *
 * <p>Continuous 2D vector field used to shape eolian formations (barchans, linear dunes, yardangs)
 * and influence regional moisture transport. Pure domain, deterministic, allocation-free.
 */
public final class WindDirectionField {

    private final long windSeed;

    public WindDirectionField(long planetSeed) {
        this.windSeed = Seeds.derive(planetSeed, "us.wind.field");
    }

    /**
     * Angle of the prevailing wind at (x, z) in radians [-PI, PI].
     * Wavelength ~3200 blocks, domain-warped for organic planetary circulation.
     */
    public double windAngle(int x, int z) {
        double coarse = GlobalTerrainFields.value01(windSeed, x, z, 1.0 / 3200.0, 0);
        double medium = GlobalTerrainFields.value01(windSeed + 0x31L, x, z, 1.0 / 800.0, 1);
        double val = (0.75 * coarse + 0.25 * medium) * 2.0 * Math.PI - Math.PI;
        return val;
    }

    /**
     * Wind speed factor in [0, 1].
     */
    public double windSpeed(int x, int z) {
        double coarse = GlobalTerrainFields.value01(windSeed + 0x77L, x, z, 1.0 / 2400.0, 2);
        return 0.4 + 0.6 * coarse;
    }

    /**
     * Cell size of the legacy wind-frame quantization, in blocks.
     *
     * <p>V3.3: RETAINED ONLY so old call sites keep compiling. The cell-quantized origin
     * below was the architectural source of the long straight material strip: the inverse
     * map mixes the QUANTIZED along-wind coordinate with the UNQUANTIZED cross-wind
     * coordinate of the CURRENT column ({@code across}), so the returned origin is a
     * sawtooth function of position with 2048-block teeth. Subtracting it from the column
     * position re-injects those teeth into the dune phase {@code u} as a discontinuous,
     * wind-aligned ramp - a perfectly straight seam every 2048 blocks. The dune field no
     * longer calls it (see {@link DuneMorphologyField#sample}); it projects the CONTINUOUS
     * coordinates directly. New code must NOT use this.
     */
    public static final int WIND_CELL = 2048;

    /**
     * X origin of the wind cell containing (x, z), for a given wind angle.
     *
     * @deprecated V3.3: cell-quantized origins inject 2048-block sawtooth seams into any
     *     phase that subtracts them. Retained for source compatibility only.
     */
    @Deprecated
    public int cellOriginX(int x, int z, double theta) {
        return cellCoordinate(x, z, theta, true);
    }

    /**
     * Z origin of the wind cell containing (x, z), for a given wind angle.
     *
     * @deprecated V3.3: see {@link #cellOriginX}.
     */
    @Deprecated
    public int cellOriginZ(int x, int z, double theta) {
        return cellCoordinate(x, z, theta, false);
    }

    private int cellCoordinate(int x, int z, double theta, boolean xAxis) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);
        // Along-wind and cross-wind integer coordinates of the sample point.
        double along = x * c + z * s;
        double across = -x * s + z * c;
        double cell = xAxis ? along : across;
        int q = (int) Math.floor(cell / WIND_CELL) * WIND_CELL;
        // Map the quantized axis coordinate back into world space.
        return xAxis
                ? (int) Math.round(q * c - across * s)
                : (int) Math.round(q * s + across * c);
    }
}
