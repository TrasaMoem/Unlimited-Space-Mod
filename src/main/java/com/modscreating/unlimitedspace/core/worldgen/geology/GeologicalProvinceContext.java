package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * WORLDGEN V2 — the unified per-column geological context.
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; PROVINCE MAP -&gt; PROVINCE CONTEXT (this)
 *                                        -&gt; TERRAIN -&gt; MATERIALS -&gt; RESOURCES -&gt; FEATURES
 * </pre>
 *
 * <h2>The one rule</h2>
 * Every CONTINUOUS intensity in this class is a number that FADES. None of them is derived from
 * an {@code if (province == X)} comparison, because such a comparison is a step function and a
 * step function is exactly what produces a one-column wall at a province border.
 *
 * <p>The discrete {@link #province()} remains available for genuinely discrete outputs (which
 * block spawns). The {@code *Intensity} accessors are the continuous form and are what terrain,
 * materials and features must use.
 *
 * <p>Pure domain, no Minecraft types.
 *
 * @param province the DOMINANT province — DISCRETE OUTPUTS ONLY
 * @param weights  the CONTINUOUS province weights, aligned with the map's weight table
 * @param table    the map's reachable weight table, so a province can be resolved to a share
 * @param profile  the planet's physical profile
 */
public record GeologicalProvinceContext(
        GeologicalProvince province,
        double[] weights,
        java.util.List<GeologicalProvinceSelector.Weight> table,
        PlanetPhysicalProfile profile
) {

    /** A neutral fallback context (used when the map is unavailable). */
    public static GeologicalProvinceContext neutral(PlanetPhysicalProfile profile) {
        return new GeologicalProvinceContext(GeologicalProvince.PLAINS, new double[]{1.0},
                java.util.List.of(new GeologicalProvinceSelector.Weight(
                        GeologicalProvince.PLAINS, 1.0)), profile);
    }

    /** The CONTINUOUS share of a province at this column, in [0,1]. */
    public double share(GeologicalProvince p) {
        if (p == null || weights == null || table == null) return 0.0;
        double sum = 0.0;
        double chosen = 0.0;
        for (int i = 0; i < table.size() && i < weights.length; i++) {
            sum += weights[i];
            if (table.get(i).province() == p) chosen = weights[i];
        }
        return sum <= 0.0 ? 0.0 : chosen / sum;
    }

    /** The CONTINUOUS volcanic intensity in [0,1]: volcanic + geothermal share. */
    public double volcanicIntensity() {
        return share(GeologicalProvince.VOLCANIC) + 0.6 * share(GeologicalProvince.GEOTHERMAL);
    }

    /** The CONTINUOUS crystal intensity in [0,1]. */
    public double crystalIntensity() {
        return share(GeologicalProvince.CRYSTAL) + 0.3 * share(GeologicalProvince.GEOTHERMAL);
    }

    /** The CONTINUOUS lava-channel intensity in [0,1]. */
    public double lavaIntensity() {
        return volcanicIntensity();
    }

    /** The CONTINUOUS impact intensity in [0,1]. */
    public double impactIntensity() {
        return share(GeologicalProvince.CRATER);
    }

    /** The CONTINUOUS glacial intensity in [0,1]. */
    public double glacialIntensity() {
        return share(GeologicalProvince.GLACIAL);
    }

    /** The CONTINUOUS high-relief intensity in [0,1]. */
    public double mountainIntensity() {
        return share(GeologicalProvince.MOUNTAIN) + 0.5 * share(GeologicalProvince.CANYON);
    }

    /** The CONTINUOUS basin intensity in [0,1]. */
    public double basinIntensity() {
        return share(GeologicalProvince.BASIN) + share(GeologicalProvince.SALT);
    }

    /**
     * The blend PURITY in [0,1]: 1 = a pure single-province column, ~1/N = a genuine border.
     * Feature gating scales with this so nothing switches abruptly.
     */
    public double confidence() {
        if (weights == null || weights.length == 0) return 1.0;
        double best = 0.0;
        for (double w : weights) if (w > best) best = w;
        return best;
    }

    /** The relative dominance of the dominant province within the reachable set, in [0,1]. */
    public double strength() {
        return confidence();
    }

    /** Whether the column supports land vegetation (a real physical constraint, not a label). */
    public boolean supportsVegetation() {
        double hostile = share(GeologicalProvince.VOLCANIC)
                + share(GeologicalProvince.GEOTHERMAL) + share(GeologicalProvince.GLACIAL);
        return hostile < 0.85;
    }

    /** Whether the column favours lava-channel carving (a continuous intensity, not a boolean). */
    public boolean favoursLavaChannels() {
        return lavaIntensity() > 0.5;
    }

    /** Whether the column favours crystal spires (a continuous intensity, not a boolean). */
    public boolean favoursSpires() {
        return crystalIntensity() > 0.5;
    }

    /** Material affinity: the CONTINUOUS share of a province at this column. */
    public double materialPreference(GeologicalProvince p) {
        return share(p);
    }

    /** Resource rarity multiplier driven by the CONTINUOUS shares. */
    public double resourceMultiplier(GeologicalProvince p) {
        return 1.0 + 0.6 * share(GeologicalProvince.VOLCANIC)
                + 0.35 * share(GeologicalProvince.CRYSTAL)
                + 0.35 * share(GeologicalProvince.GLACIAL)
                + 0.2 * share(GeologicalProvince.CRATER)
                + 0.2 * share(GeologicalProvince.SALT)
                + 0.2 * share(GeologicalProvince.GEOTHERMAL)
                - 0.1 * share(p == null ? GeologicalProvince.PLAINS : p);
    }

    /**
     * Value equality that compares the weight VECTOR BY VALUE.
     *
     * <p>This override is required, not cosmetic: a record's generated {@code equals} uses
     * {@code Object.equals} for its components, and a {@code double[]} compares by IDENTITY.
     * Without this override two contexts sampled at the same column would compare unequal, and
     * every determinism test in the suite would fail for a reason that has nothing to do with
     * the generation itself.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeologicalProvinceContext other)) return false;
        if (province != other.province) return false;
        if (table == null ? other.table != null : !table.equals(other.table)) return false;
        return java.util.Arrays.equals(weights, other.weights);
    }

    @Override
    public int hashCode() {
        int h = province == null ? 0 : province.hashCode();
        h = h * 31 + java.util.Arrays.hashCode(weights);
        h = h * 31 + (table == null ? 0 : table.hashCode());
        return h;
    }

    @Override
    public String toString() {
        return "GeologicalProvinceContext[province=" + province + ", weights="
                + java.util.Arrays.toString(weights) + "]";
    }
}