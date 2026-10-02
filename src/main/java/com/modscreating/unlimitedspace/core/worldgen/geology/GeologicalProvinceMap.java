package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.List;

/**
 * WORLDGEN V2 — the province map of one planet: a SECONDARY, independent nearest-site Voronoi
 * at the ~900-block scale, nested inside the macro geography.
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; PROVINCE MAP (this) -&gt; continuous weights
 *              |-&gt; dominant province  (DISCRETE outputs only: blocks, features)
 *              '-&gt; micro texture      (label-free, local roughness only)
 * </pre>
 *
 * <h2>Invariants of this class</h2>
 * <ul>
 *   <li>There is exactly ONE province field. The old 192-block label layer is gone; what remains
 *       at that scale is a label-free continuous texture.</li>
 *   <li>Ownership is nearest-site geometry ({@link ProvinceField#nearestProvinceAt}), never a
 *       cell vote and never a macro-region filter.</li>
 *   <li>The terrain path consumes {@link #weightsAt} — a vector of CONTINUOUS numbers. A
 *       discrete province ID is produced only where a genuinely discrete answer is required.</li>
 * </ul>
 *
 * <p>Pure domain: no Minecraft types. Deterministic given {@code (provinceSeed, weights, x, z)}.
 *
 * @param provinceSeed the province subsystem seed
 * @param profile      the planet's physical profile
 * @param weights      the reachable provinces with their prior weights (never empty)
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
        return new GeologicalProvinceMap(Seeds.derive(planetSeed, "us.geology.provinces"),
                profile, null);
    }

    /** The dominant (highest-weight) province — the planet default / debug label. */
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

    /** All reachable provinces, in weight order. */
    public List<GeologicalProvince> provinces() {
        return weights.stream().map(GeologicalProvinceSelector.Weight::province).toList();
    }

    /** The secondary-field medium seed. */
    public long mediumSeed() {
        return ProvinceField.mediumSeedOf(provinceSeed);
    }

    /**
     * The CONTINUOUS province weights of a column, written into {@code out}.
     *
     * <p>This is what the terrain, material and ecology paths read. There is no integer label in
     * this path at all, so no province border can step a terrain amplitude.
     */
    public double[] weightsAt(int x, int z, double[] out) {
        return ProvinceField.weightsAt(mediumSeed(), weights, x, z, out);
    }

    /**
     * The DOMINANT province of a column. Use ONLY for genuinely discrete outputs (which block
     * spawns, which feature placer runs). Never for a continuous terrain parameter.
     */
    public GeologicalProvince provinceAt(int x, int z) {
        return ProvinceField.nearestProvinceAt(mediumSeed(), weights, x, z);
    }

    /** The LABEL-FREE micro texture in [0,1]: local roughness only, never an identity. */
    public double microTextureAt(int x, int z) {
        return ProvinceField.microTexture01(mediumSeed(), x, z);
    }

    /** The CONTINUOUS share of one province at a column, in [0,1]. */
    public double shareAt(int x, int z, double[] scratch, GeologicalProvince province) {
        ProvinceField.weightsAt(mediumSeed(), weights, x, z, scratch);
        return ProvinceField.shareOf(scratch, weights, province);
    }

    /** Cheap chunk-level province (uses the chunk corner column). */
    public GeologicalProvince provinceAtChunk(int chunkX, int chunkZ) {
        return provinceAt(chunkX * 16 + 8, chunkZ * 16 + 8);
    }

    /** Province at a province-CELL centre (diagnostics / the province modifier field). */
    public GeologicalProvince provinceAtCellCenter(int cellX, int cellZ) {
        return provinceAt((int) Math.round((cellX + 0.5) * ProvinceField.CELL_SIZE),
                (int) Math.round((cellZ + 0.5) * ProvinceField.CELL_SIZE));
    }

    /** A correctly sized scratch buffer for {@link #weightsAt}. */
    public double[] newScratch() {
        return new double[weights.size()];
    }

    /**
     * Value equality on the seed + weight table. Two maps built from the same planet seed are
     * equal, which is what makes a whole {@code PlanetGeologyProfile} reproducible.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeologicalProvinceMap other)) return false;
        return provinceSeed == other.provinceSeed && weights.equals(other.weights);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(provinceSeed) * 31 + weights.hashCode();
    }

    @Override
    public String toString() {
        return "GeologicalProvinceMap[seed=" + provinceSeed + ", provinces=" + provinces() + "]";
    }
}