package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Continuous mountain system field (Stage 2).
 *
 * <p>Produces elongated tectonic ranges, mountain belts, massifs, foothills,
 * passes, and inter-range valley separation without circular blobs or hard binary switches.
 */
public final class MountainSystemField {

    private final long mountainSeed;

    /** ACT V3.1: shared envelope scratch (see MountainRangeField#envelopes). */
    private final double[] envelopeScratch = new double[2];
    public MountainSystemField(long planetSeed) {
        this.mountainSeed = Seeds.derive(planetSeed, "us.mountain.system");
    }

    /**
     * Compute mountain and foothill envelopes and ridged crest structure.
     *
     * @param x coordinate
     * @param z coordinate
     * @param mountainCoverage planetary coverage [0, 1]
     * @param alpineWeight continuous alpine character weight [0, 1]
     * @param tectonicActivity planetary tectonic intensity [0, 1]
     * @param erosion planetary erosion [0, 1]
     * @param widthMul range width multiplier
     * @return signed mountain elevation contribution in blocks
     */
    public MountainOutput sample(int x, int z, double mountainCoverage, double alpineWeight,
                                 double tectonicActivity, double erosion, double widthMul) {
        // Continuous effective coverage: alpine tendency ensures cold/rocky worlds keep mountain belts
        double effectiveCoverage = Math.max(0.02, Math.min(0.98,
                mountainCoverage * 0.65 + alpineWeight * 0.25 + tectonicActivity * 0.15));

        // Use anisotropic domain warping for elongated tectonic collision belts
        double warpX = x + 380.0 * (GlobalTerrainFields.value01(mountainSeed + 0x1A, x, z, 1.0 / 1800.0, 0) - 0.5);
        double warpZ = z + 380.0 * (GlobalTerrainFields.value01(mountainSeed + 0x1B, x, z, 1.0 / 1800.0, 1) - 0.5);

        // Core range and foothill envelopes. ACT V3.1: both from ONE smoothed cell field
        // (bit-identical, half the lattice reads).
        MountainRangeField.envelopes(mountainSeed, effectiveCoverage, (int) warpX, (int) warpZ,
                envelopeScratch);
        double coreEnv = envelopeScratch[0];
        double footBand = envelopeScratch[1];

        // Ridged crest profile with sharpness inversely proportional to erosion
        double crest = MountainRangeField.crest(mountainSeed, widthMul, (int) warpX, (int) warpZ);
        double sharpness = 1.0 + (1.0 - erosion) * 1.8;
        double sharpCrest = Math.pow(Math.max(0.0, crest), sharpness);

        // Passes / gaps cut periodically through ranges
        double passNoise = GlobalTerrainFields.value01(mountainSeed + 0x77L, (int) warpX, (int) warpZ, 1.0 / 450.0, 2);
        double passFactor = 0.55 + 0.45 * Math.sin(passNoise * Math.PI * 2.0);

        // Mountain elevation calculation
        // Ranges: +80..300 blocks target corridor
        double peakRelief = coreEnv * sharpCrest * passFactor;
        double foothillRelief = Math.max(0.0, footBand - coreEnv) * (0.35 + 0.65 * crest);

        return new MountainOutput(coreEnv, footBand, peakRelief, foothillRelief);
    }

    public record MountainOutput(
            double coreEnvelope,
            double foothillEnvelope,
            double peakRelief,
            double foothillRelief
    ) {}
}
