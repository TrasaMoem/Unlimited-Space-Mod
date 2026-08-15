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

    public static TerrainGenerator from(PlanetWorldgenProfile profile) {
        return new ValueNoiseTerrainGenerator(
                profile.terrainSeed(),
                profile.baseHeight(),
                profile.amplitude(),
                profile.frequency());
    }
}
