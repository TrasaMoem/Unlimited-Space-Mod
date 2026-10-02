package com.modscreating.unlimitedspace.core.worldgen.biome;

/**
 * Ideal environmental envelope and feature affinities of ONE biome candidate (V3.1).
 *
 * <p>Every field is a CONTINUOUS number. Nothing here is a switch, a bucket or a province, and
 * nothing here is a gate: the weights are the coefficients of a product score whose factors are
 * evaluated from the real local fields of a {@link WorldgenColumnSample}.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code idealTemp01 / idealHum01 / idealPrecip01} — the climate envelope.</li>
 *   <li>{@code idealElevation01 / idealWetness01} — the relief + hydrology envelope.</li>
 *   <li>{@code volcanicAffinity / mountainAffinity / duneAffinity / glacialAffinity} — how well the
 *       planetary and local TENDENCIES suit this biome. A negative value means "suppressed".</li>
 *   <li>{@code organicAffinity} — the life-supporting tendency the biome wants.</li>
 *   <li>{@code riverAffinity} — the riparian response: a positive value makes a REAL river or lake
 *       mask pull the classification toward this biome.</li>
 *   <li>{@code wetnessAffinity} — the standing-water response of the ecological wetness channel.</li>
 *   <li>{@code rockAffinity / sedimentAffinity / crystalAffinity} — the material fit: what the
 *       surface is actually made of at a matching column.</li>
 * </ul>
 */
public record BiomeScoreWeights(
        double idealTemp01,
        double idealHum01,
        double idealElevation01,
        double idealWetness01,
        double volcanicAffinity,
        double mountainAffinity,
        double duneAffinity,
        double organicAffinity,
        double riverAffinity,
        double wetnessAffinity,
        double rockAffinity,
        double sedimentAffinity,
        double crystalAffinity,
        double glacialAffinity
) {

    /** The precipitation axis: derived from the moisture envelope, so the catalogue stays coherent. */
    public double idealPrecip01() {
        return clamp01(0.5 * (idealHum01 + idealWetness01));
    }

    /** The V2 eight-parameter constructor, for the legacy coarse call path. */
    public BiomeScoreWeights(double idealTemp01, double idealHum01, double idealElevation01,
                             double idealWetness01, double volcanicAffinity,
                             double mountainAffinity, double duneAffinity, double organicAffinity) {
        this(idealTemp01, idealHum01, idealElevation01, idealWetness01, volcanicAffinity,
                mountainAffinity, duneAffinity, organicAffinity, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * LEGACY coarse score, retained so the pre-V3.1 eight-argument contract keeps working.
     *
     * <p>The real classification is {@link BiomeMaskField#classify(WorldgenColumnSample)}, which
     * reads every real local field. This method is a reduced form of the same climate/terrain
     * product and is used only where no column sample is available.
     */
    public double score(double temp01, double hum01, double elev01, double wet01,
                        double volcanic, double mountain, double dune, double organic) {
        double tempDist = Math.abs(temp01 - idealTemp01);
        double humDist = Math.abs(hum01 - idealHum01);
        double elevDist = Math.abs(elev01 - idealElevation01);
        double wetDist = Math.abs(wet01 - idealWetness01);

        double climateFit = Math.exp(-(tempDist * tempDist * 8.0 + humDist * humDist * 6.0));
        double terrainFit = Math.exp(-(elevDist * elevDist * 4.0 + wetDist * wetDist * 4.0));

        double featureFit = 1.0
                + volcanicAffinity * volcanic * 0.5
                + mountainAffinity * mountain * 0.5
                + duneAffinity * dune * 0.5
                + organicAffinity * organic * 0.5;

        return Math.max(0.0001, climateFit * terrainFit * featureFit);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
