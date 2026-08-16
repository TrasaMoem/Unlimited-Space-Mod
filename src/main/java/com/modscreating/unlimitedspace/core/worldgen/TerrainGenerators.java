package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import com.modscreating.unlimitedspace.core.worldgen.terrain.ValueNoiseTerrainGenerator;

/**
 * Factory that turns a {@link PlanetWorldgenProfile} into a ready-to-use
 * {@link TerrainGenerator}. Keeping the construction here makes the concrete
 * algorithm swappable later (e.g. a contender with continentalness/erosion) without
 * touching consumers.
 */
public final class TerrainGenerators {

    private TerrainGenerators() {}

        /**
     * Build a terrain generator from a profile. R8: the pattern's octaves + amplitude /
     * frequency multipliers are applied here, so a single {@link PlanetWorldgenProfile}
     * can yield different SHAPES (flat vs hills vs cratered ...) without touching the
     * {@link ValueNoiseTerrainGenerator} algorithm or its callers.
     */
    public static TerrainGenerator from(PlanetWorldgenProfile profile) {
        TerrainPattern pattern = profile.terrainPattern();
        return new ValueNoiseTerrainGenerator(
                profile.terrainSeed(),
                profile.baseHeight(),
                profile.amplitude() * pattern.amplitudeMultiplier(),
                profile.frequency() * pattern.frequencyMultiplier(),
                pattern.octaves());
    }
}
