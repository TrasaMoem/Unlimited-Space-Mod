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
 *
 * <p>ACT 3 (P1): the CANONICAL province identity is the 900-block region-aware
 * {@link ProvinceField} layer ({@link #canonicalProvinceAt} /
 * {@link #canonicalContextAt}); the 192-block {@link GeologicalProvinceSelector} layer is
 * MICRO-FACIES support data (confidence / intensity / local variation) and must never
 * replace the medium label. The legacy {@link #provinceAt}/{@link #contextAt} answers are
 * preserved for compatibility and now delegate their LABEL to the region-aware medium path
 * when a region is supplied — see the region-aware overloads.
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
        return canonicalProvinceAt(x, z, elevation01, null);
    }

    /**
     * ACT 3 (P1.1): region-aware canonical province — the ONE authoritative province label.
     * Delegates to the 900-block region-filtered {@link ProvinceField}; the 192 micro layer
     * never replaces this label. A null region keeps the raw medium draw (no ecological filter).
     */
    public GeologicalProvince provinceAt(int x, int z, double elevation01,
                                         com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region) {
        return canonicalProvinceAt(x, z, elevation01, region);
    }

    /**
     * ACT 3 (P1): canonical medium province identity (900-scale, region-aware).
     * Single authoritative path for macro &gt; province &gt; micro hierarchy.
     */
    public GeologicalProvince canonicalProvinceAt(int x, int z, double elevation01,
                                                  com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region) {
        long mediumSeed = ProvinceField.mediumSeedOf(provinceSeed);
        return ProvinceField.provinceAt(mediumSeed, weights, profile, region, x, z, elevation01);
    }

    /** ACT 3: raw 192-block micro label (micro-facies support ONLY — never canonical identity). */
    public GeologicalProvince microProvinceAt(int x, int z, double elevation01) {
        double noise = GeologicalProvinceSelector.regionNoise(provinceSeed, x, z);
        return GeologicalProvinceSelector.classify(profile, weights, noise, elevation01);
    }

    /**
     * ACT 3 (P1.3): the raw, LABEL-FREE 192-block micro noise field in [0,1]. Terrain and
     * micro-facies consumers use this as continuous local texture (roughness / intensity) —
     * it carries no province identity, so it can never flip a major category.
     */
    public double microNoiseAt(int x, int z) {
        return GeologicalProvinceSelector.regionNoise(provinceSeed, x, z);
    }

    /** ACT 3: micro confidence/fade/intensity support value in [0,1] (continuous, no label flip). */
    public double microSupportAt(int x, int z, double elevation01) {
        GeologicalProvinceContext micro = GeologicalProvinceSelector.contextAt(
                provinceSeed, weights, profile, x, z, elevation01);
        return micro == null ? 1.0 : micro.confidence() * (0.35 + 0.65 * micro.strength());
    }

    /**
     * Unified per-column context (province + strength + profile). Single source of truth
     * for terrain, materials, resources and features at this column.
     *
     * <p>ACT 3 (P1): the LABEL is the canonical medium province (region-unfiltered legacy
     * path); continuous micro support (confidence/strength) is folded into the confidence
     * channel so micro geology modulates intensity without flipping identity.
     */
    public GeologicalProvinceContext contextAt(int x, int z, double elevation01) {
        return canonicalContextAt(x, z, elevation01, null);
    }

    /**
     * ACT 3 (P1.1): region-aware canonical context — the ONE authoritative per-column answer.
     * Label and strength come from the 900-block region-filtered medium layer; the 192 micro
     * layer contributes only its continuous confidence (border fade / intensity).
     */
    public GeologicalProvinceContext canonicalContextAt(int x, int z, double elevation01,
                                                        com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeRegion region) {
        GeologicalProvince canonical = canonicalProvinceAt(x, z, elevation01, region);
        double strength = ProvinceField.strengthOf(weights, canonical);
        GeologicalProvinceContext micro = GeologicalProvinceSelector.contextAt(
                provinceSeed, weights, profile, x, z, elevation01);
        double microConf = micro == null ? 1.0 : micro.confidence();
        return new GeologicalProvinceContext(canonical, strength, microConf, profile);
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
