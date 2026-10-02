package com.modscreating.unlimitedspace.core.worldgen.geography;

/**
 * WORLDGEN V2 — the per-column MACRO SAMPLE: one geometry, one set of answers.
 *
 * <p>This is the <b>transition contract</b> of the whole worldgen stack. A single
 * {@link MacroGeography#sample} call produces, from ONE geometry:
 *
 * <ul>
 *   <li>{@link #province} — the primary (arg-min) archetype,</li>
 *   <li>{@link #secondaryProvince} — the second-smallest-distance archetype,</li>
 *   <li>{@link #signedBoundaryDistance} — the exact q-space bisector signed distance,</li>
 *   <li>{@link #transitionWeight} — derived from that SAME distance, and</li>
 *   <li>the continuous {@link ProvinceArchetype} attribute blends.</li>
 * </ul>
 *
 * <p>Label geometry, terrain geometry and material geometry therefore CANNOT diverge: they all
 * read this one object. There is no second boundary field anywhere in the codebase.
 *
 * <h2>Allocation policy</h2>
 * The instance is MUTABLE and REUSABLE: {@code MacroGeography.sample(x, z, out)} overwrites
 * it in place and allocates nothing. That is the hot-path contract (test Q).
 * {@link #snapshot()} produces an immutable copy for debug / UI / tests.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class MacroSample {

    // ---------------------------------------------------------------- discrete identity
    /** The primary archetype (nearest site). Never null. */
    public ProvinceArchetype province = ProvinceArchetype.OPEN_PLAINS;
    /** The secondary archetype (second nearest site). Never null. */
    public ProvinceArchetype secondaryProvince = ProvinceArchetype.OPEN_PLAINS;
    /** The catalog index of {@link #province}. */
    public int provinceId = 0;
    /** The catalog index of {@link #secondaryProvince}. */
    public int secondaryProvinceId = 0;
    /** The lattice cell of the nearest site. */
    public int primaryCellX, primaryCellZ;
    /** The lattice cell of the second nearest site. */
    public int secondaryCellX, secondaryCellZ;

    // ---------------------------------------------------------------- geometry quantities
    /** {@code D1 = |q - s1|^2} of the nearest site (q = warped query point). */
    public double d1;
    /** {@code D2 = |q - s2|^2} of the second nearest site. */
    public double d2;
    /** {@code L = |s2 - s1|}, the separation of the two sites. */
    public double siteSeparation;
    /**
     * The EXACT q-space signed distance to the bisector of the two nearest sites:
     * {@code (D2 - D1) / (2L)}. Positive on the primary side, zero exactly on the bisector,
     * negative on the secondary side. In BLOCKS.
     */
    public double signedBoundaryDistance;
    /**
     * {@code 0} exactly on the bisector, {@code 1} deep inside a province core. Derived from
     * {@link #signedBoundaryDistance} through a smooth saturating ramp of half-width
     * {@link MacroGeography#TRANSITION_HALF_WIDTH}.
     */
    public double transitionWeight;

    // ---------------------------------------------------------------- continuous attributes
    public double hillMultiplier = 1.0;
    public double mountainMultiplier = 1.0;
    public double uplift = 1.0;
    public double bias = 0.0;
    public double roughness = 1.0;
    public double carve = 1.0;
    public double dune = 0.0;
    public double volcanic = 0.0;
    public double crystal = 0.0;
    public double temperatureBias = 0.0;
    public double humidityBias = 0.0;
    public double vegetationBias = 0.5;
    public double precipitationBias = 0.5;
    public double waterAffinity = 0.5;
    public double continentalnessBias = 0.5;
    public double reliefAffinity = 0.5;

    /** The summed kernel weight that produced the attribute blend (diagnostics). */
    public double kernelMass;
    /** The primary archetype's prior weight in the planet's reachable set, [0,1]. */
    public double provinceWeight = 1.0;
    /**
     * The attribute-blend purity in [0,1]: 1 = a pure single-site core, 0 = an exact 50/50
     * boundary point. This is the honest measure of "how much of this column is owned by its
     * own province".
     */
    public double coreShare = 1.0;

    /** Reset to a defined neutral state. */
    public void reset() {
        province = ProvinceArchetype.OPEN_PLAINS;
        secondaryProvince = ProvinceArchetype.OPEN_PLAINS;
        provinceId = 0;
        secondaryProvinceId = 0;
        primaryCellX = 0;
        primaryCellZ = 0;
        secondaryCellX = 0;
        secondaryCellZ = 0;
        d1 = 0.0;
        d2 = 0.0;
        siteSeparation = 0.0;
        signedBoundaryDistance = 0.0;
        transitionWeight = 1.0;
        hillMultiplier = 1.0;
        mountainMultiplier = 1.0;
        uplift = 1.0;
        bias = 0.0;
        roughness = 1.0;
        carve = 1.0;
        dune = 0.0;
        volcanic = 0.0;
        crystal = 0.0;
        temperatureBias = 0.0;
        humidityBias = 0.0;
        vegetationBias = 0.5;
        precipitationBias = 0.5;
        waterAffinity = 0.5;
        continentalnessBias = 0.5;
        reliefAffinity = 0.5;
        kernelMass = 0.0;
        provinceWeight = 1.0;
        coreShare = 1.0;
    }
    /** The implied temperature01 of a column inside this province (affinity, not a hard gate). */
    public double temperature01(double planetTemperature01) {
        return clamp01(planetTemperature01 + temperatureBias);
    }

    /** The implied humidity01 of a column inside this province. */
    public double humidity01(double planetHumidity01) {
        return clamp01(planetHumidity01 + humidityBias);
    }

    /** True when the point sits inside a genuine macro transition band. */
    public boolean inTransition() {
        return transitionWeight < MacroGeography.TRANSITION_CORE_THRESHOLD;
    }

    /** The absolute distance to the bisector, in blocks. */
    public double boundaryDistance() {
        return Math.abs(signedBoundaryDistance);
    }

    /** The local mountain coverage for the terrain composer. */
    public double localMountainCoverage(double planetCoverage) {
        return clamp01(planetCoverage * mountainMultiplier);
    }

    /** True when the primary and secondary archetypes differ (a real border is nearby). */
    public boolean hasBorder() {
        return provinceId != secondaryProvinceId;
    }

    /** An immutable copy for debug output, UI and tests. */
    public Snapshot snapshot() {
        return new Snapshot(province, secondaryProvince, provinceId, secondaryProvinceId,
                primaryCellX, primaryCellZ, secondaryCellX, secondaryCellZ,
                d1, d2, siteSeparation, signedBoundaryDistance, transitionWeight, coreShare,
                hillMultiplier, mountainMultiplier, uplift, bias, roughness, carve, dune,
                volcanic, crystal, temperatureBias, humidityBias, vegetationBias,
                precipitationBias, waterAffinity, continentalnessBias, reliefAffinity,
                kernelMass, provinceWeight);
    }

    /** The immutable snapshot form of a {@link MacroSample}. */
    public record Snapshot(
            ProvinceArchetype province,
            ProvinceArchetype secondaryProvince,
            int provinceId,
            int secondaryProvinceId,
            int primaryCellX, int primaryCellZ,
            int secondaryCellX, int secondaryCellZ,
            double d1, double d2, double siteSeparation,
            double signedBoundaryDistance, double transitionWeight, double coreShare,
            double hillMultiplier, double mountainMultiplier, double uplift, double bias,
            double roughness, double carve, double dune, double volcanic, double crystal,
            double temperatureBias, double humidityBias, double vegetationBias,
            double precipitationBias, double waterAffinity, double continentalnessBias,
            double reliefAffinity, double kernelMass, double provinceWeight) {

        /** True when the primary and secondary archetypes differ. */
        public boolean hasBorder() {
            return provinceId != secondaryProvinceId;
        }

        /** The absolute distance to the bisector, in blocks. */
        public double boundaryDistance() {
            return Math.abs(signedBoundaryDistance);
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}