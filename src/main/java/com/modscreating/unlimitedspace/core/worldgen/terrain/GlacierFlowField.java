package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Continuous glacial flow and trough field.
 *
 * <p>Produces U-shaped glacial valleys, cirques, ice flow channels, and crevasse zones
 * without flattening mountain ranges on cold worlds.
 */
public final class GlacierFlowField {

    private final long glacierSeed;

    public GlacierFlowField(long planetSeed) {
        this.glacierSeed = Seeds.derive(planetSeed, "us.glacier.flow");
    }

    /**
     * Compute glacial erosion and trough carving in blocks.
     *
     * @param x coordinate
     * @param z coordinate
     * @param glacialWeight continuous glacial tendency [0, 1]
     * @param mountainEnv mountain core envelope [0, 1]
     * @param baseValley valley corridor field [0, 1]
     * @param targetAmplitude base amplitude
     * @return signed trough modification (typically negative carving, with flat U-bottom)
     */
    public GlacialOutput sample(int x, int z, double glacialWeight, double mountainEnv,
                                double baseValley, double targetAmplitude) {
        if (glacialWeight <= 0.05) {
            return new GlacialOutput(0.0, 0.0);
        }

        // Glacial troughs follow valleys between mountain ranges
        double valleyAlign = baseValley * (0.4 + 0.6 * mountainEnv);
        if (valleyAlign <= 0.05) {
            return new GlacialOutput(0.0, 0.0);
        }

        // U-shaped profile: flat trough floor and steep parabolic sidewalls
        // Unlike V-shaped fluvial valleys (which are sharp at the bottom), U-valleys have |t|^0.3 or smoothed bottom
        double troughCross = Math.pow(Math.min(1.0, valleyAlign * 1.5), 0.45);
        double troughCarve = -troughCross * targetAmplitude * 0.85 * glacialWeight;

        // Ice flow crevasses and serac fields: localized cross-trough shearing
        double crevasseNoise = GlobalTerrainFields.value01(glacierSeed + 0x44L, x, z, 1.0 / 60.0, 0);
        double crevasse = (crevasseNoise > 0.72) ? -Math.sin((crevasseNoise - 0.72) / 0.28 * Math.PI) * 6.0 * glacialWeight : 0.0;

        return new GlacialOutput(troughCarve + crevasse, valleyAlign * glacialWeight);
    }

    public record GlacialOutput(
            double troughCarve,
            double glacialIntensity
    ) {}
}
