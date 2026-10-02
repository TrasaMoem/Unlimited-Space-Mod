package com.modscreating.unlimitedspace.core.worldgen.features;

import com.modscreating.unlimitedspace.core.worldgen.character.LavaEligibility;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;

/**
 * Continuous mask provider for planetary feature placement (Stage 6 / V3).
 *
 * <p>Features are placed strictly according to continuous probability masks:
 * - mountain mask
 * - dune mask (duneWeight * sediment)
 * - lava mask (lavaEligibility * localHotspot)
 * - river mask
 * - lake mask
 * - vegetation mask (organic * wetness * tempSuitability * surfaceSuitability)
 */
public final class FeaturePlacementField {

    private final PlanetCharacter character;

    public FeaturePlacementField(PlanetCharacter character) {
        this.character = character;
    }

    /**
     * Probability of placing vegetation feature at (x, z).
     */
    public double vegetationMask(double temp01, double wetness01, double slope, boolean isSolidSurface) {
        if (!isSolidSurface || !character.waterPhase().allowsLiquid()) return 0.0;
        if (slope > 0.55) return 0.0; // steep cliffs do not grow trees

        double tDist = Math.abs(temp01 - 0.50);
        double tSuit = Math.max(0.0, 1.0 - tDist * 3.0);
        return character.weights().organicWeight() * wetness01 * tSuit;
    }

    /**
     * Probability / eligibility of placing lava or geothermal vents at (x, z).
     */
    public double lavaMask(double localHotspot, double terrainSuitability) {
        return LavaEligibility.evaluate(
                character.surfaceKelvin(),
                character.weights().volcanicWeight(),
                localHotspot,
                terrainSuitability
        );
    }
}
