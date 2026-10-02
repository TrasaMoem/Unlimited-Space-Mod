package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;

/**
 * One registered biome candidate of the V3 continuous classifier.
 *
 * <p>{@link #preferredGeology()} is a SOFT affinity target, not a gate. The
 * {@link BiomeMaskField} caps its influence at {@link BiomeMaskField#SOFT_MACRO_MAX}, so a
 * candidate can never be selected purely because of the province it prefers.
 *
 * <p>The order of the constructor arguments is {@code (id, preferredGeology, weights)} so the
 * catalogue reads as "this biome, softly preferring this geology, with this envelope".
 */
public record BiomeCandidate(
        String id,
        GeologicalProvince preferredGeology,
        BiomeScoreWeights weights
) {

    /** Legacy accessor: the soft macro affinity target, under its historical name. */
    public GeologicalProvince preferredProvince() {
        return preferredGeology;
    }

    /**
     * The stable index of this candidate in {@link BiomeMaskField#defaultCandidates()}.
     *
     * <p>It is a POSITION in the immutable catalogue, not an enum ordinal, so the preview, the
     * diagnostics and the tests can all name a biome by a stable integer. It is resolved by a
     * lookup, so it is not on the per-column hot path.
     */
    public int catalogueIndex() {
        int i = BiomeMaskField.defaultCandidates().indexOf(this);
        return i < 0 ? 0 : i;
    }
}
