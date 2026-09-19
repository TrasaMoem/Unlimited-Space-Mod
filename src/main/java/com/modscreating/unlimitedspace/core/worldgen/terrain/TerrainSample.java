package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * A single-column terrain sample (R20): the hierarchical height plus the global fields that
 * produced it. Used by the debug screen, headless diagnostics and tests — one evaluation,
 * multiple consumers.
 *
 * @param height           final surface height (world Y)
 * @param continentalness  global land/ocean field in [0,1]
 * @param erosion          effective erosion regime in [0,1]
 * @param ridge            macro ridge/valley field in [0,1]
 * @param macroElevation   macro (mountains/valleys/basins) contribution in blocks
 * @param localDetailAmplitude  maximum |contribution| of the tiny-detail layer in blocks
 */
public record TerrainSample(
        int height,
        double continentalness,
        double erosion,
        double ridge,
        double macroElevation,
        double localDetailAmplitude,
        double mountainEnvelope,
        double foothillEnvelope,
        double localMountainCoverage
) {

    /** Continuous surface elevation normalized over the shaper's span in [0,1]. */
    public double elevation01(double minHeight, double maxHeight) {
        double span = maxHeight - minHeight;
        if (span <= 0.0) return 0.5;
        return Math.max(0.0, Math.min(1.0, (height - minHeight) / span));
    }
}
