package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;

/**
 * Biotic suitability weights indicating how hospitable local conditions are for organic life.
 */
public record BioticWeights(
        double temperatureSuitability,
        double moistureSuitability,
        double radiationTolerance,
        double overallViability
) {

    public static BioticWeights evaluate(PlanetCharacter character, double localTemp01, double localWet01) {
        if (character == null || !character.waterPhase().allowsLiquid()) {
            return new BioticWeights(0.0, 0.0, 0.0, 0.0);
        }

        double tDist = Math.abs(localTemp01 - 0.50);
        double tSuit = Math.max(0.0, 1.0 - tDist * 3.0);
        double mSuit = Math.max(0.0, Math.min(1.0, localWet01 * 1.5));
        double radTol = Math.max(0.0, 1.0 - character.profile().radiation() * 1.2);

        double viability = tSuit * mSuit * radTol * character.organicWeight();
        return new BioticWeights(tSuit, mSuit, radTol, viability);
    }
}
