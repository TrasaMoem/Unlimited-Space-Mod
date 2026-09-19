package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.List;

/**
 * Deterministic province field of one planet (R16 planet-diversity foundation).
 *
 * <p>Wraps the planet's reachable province weights and answers
 * {@code provinceAt(x, z, elevation01)} for any world coordinate. Pure function of
 * {@code (provinceSeed, physical profile, coordinates)}, so the same planet always yields the
 * same province map and the classification stays O(1) per column (four corner samples +
 * a cumulative-weight walk).
 */
public record GeologicalProvinceMap(
        long provinceSeed,
        PlanetPhysicalProfile profile,
        List<GeologicalProvinceSelector.Weight> weights
) {

    public GeologicalProvinceMap {
        if (profile == null) throw new IllegalArgumentException("profile required");
        weights = weights == null || weights.isEmpty()
                ? GeologicalProvinceSelector.weights(profile)
                : List.copyOf(weights);
    }

    /** Canonical factory: planet seed + physical profile &rarr; province map. */
    public static GeologicalProvinceMap create(long planetSeed, PlanetPhysicalProfile profile) {
        long seed = Seeds.derive(planetSeed, "us.geology.provinces");
        return new GeologicalProvinceMap(seed, profile, null);
    }

    /** The dominant (highest-weight) province — used as the default/debug province. */
    public GeologicalProvince dominant() {
        GeologicalProvince best = GeologicalProvince.PLAINS;
        double bestWeight = -1.0;
        for (GeologicalProvinceSelector.Weight w : weights) {
            if (w.weight() > bestWeight) {
                bestWeight = w.weight();
                best = w.province();
            }
        }
        return best;
    }

    /** All reachable provinces, weighted order preserved. */
    public List<GeologicalProvince> provinces() {
        return weights.stream().map(GeologicalProvinceSelector.Weight::province).toList();
    }

    /**
     * Province at a world coordinate.
     *
     * @param elevation01 normalized surface elevation of the column in [0,1]
     *                    (from the terrain generator; refines high/low provinces)
     */
    public GeologicalProvince provinceAt(int x, int z, double elevation01) {
        double noise = GeologicalProvinceSelector.regionNoise(provinceSeed, x, z);
        return GeologicalProvinceSelector.classify(profile, weights, noise, elevation01);
    }

    /**
     * Unified per-column context (province + strength + profile). Single source of truth
     * for terrain, materials, resources and features at this column.
     */
    public GeologicalProvinceContext contextAt(int x, int z, double elevation01) {
        return GeologicalProvinceSelector.contextAt(provinceSeed, weights, profile, x, z, elevation01);
    }

    /** Cheap chunk-level province (uses the chunk corner elevation). */
    public GeologicalProvince provinceAtChunk(int chunkX, int chunkZ, double elevation01) {
        return provinceAt(chunkX * 16 + 8, chunkZ * 16 + 8, elevation01);
    }

    /**
     * Province at a province-CELL centre (R19: the single shared classification path —
     * {@code ProvinceTerrainModifier} blends the 3&times;3 cell neighbourhood through THIS
     * method instead of re-implementing the field, so there is exactly one conceptual
     * province sampling system; elevation stays the debug/neutral 0.5 band).
     */
    public GeologicalProvince provinceAtCellCenter(int cellX, int cellZ) {
        return provinceAt(cellX, cellZ, 0.5);
    }
}
