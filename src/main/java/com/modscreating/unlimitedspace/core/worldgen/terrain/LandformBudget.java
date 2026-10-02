package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PHASE 6 (landform budget): the PLANETARY composition of local landforms.
 *
 * <p>A planet does not get every landform at full strength — the budget is a weighted
 * composition derived from its physical profile (DRY + HIGH_EROSION → gullies and ravines;
 * TECTONIC → fissures; GLACIAL → crevasses; old eroded → sinkholes and depressions). The
 * weights are soft intensities in [0,1]: they gate AMPLITUDE and COVERAGE, never switch
 * anything on/off abruptly.
 *
 * <pre>
 * HILLS   — lives in the relief layer (PlanetReliefProfile / TerrainShaper step 5)
 * CRATERS — live in the rare-feature layer (TerrainShaper step 4)
 * DUNES   — live in the rare-feature layer (TerrainShaper duneField)
 * THIS    — gullies / ravines / fissures / sinkholes / crevasses / depressions
 * </pre>
 *
 * <p>Pure domain, deterministic, allocation-free.
 */
public record LandformBudget(
        double gully,
        double ravine,
        double fissure,
        double sinkhole,
        double crevasse,
        double depression
) {

    /** Canonical budget of a planet (pure function of the physical profile). */
    public static LandformBudget of(PlanetPhysicalProfile p) {
        if (p == null) return NONE;
        double erosion = p.erosion();
        double tectonic = p.tectonicActivity();
        double aridity = 1.0 - p.humidity();
        double age = p.relativeAge();
        double wet = p.waterAbundance();
        boolean cold = p.isColdWorld();
        double coldness = 1.0 - Math.min(1.0, p.temperature() * 3.0);

        // Dry + eroded worlds carve connected drainage (gullies, ravines).
        double gully = 0.55 * erosion + 0.45 * aridity * erosion;
        double ravine = 0.40 * erosion * aridity + 0.30 * tectonic * erosion + 0.10 * erosion;
        // Tectonics open fissure networks; old eroded worlds grow sinkholes; humid old
        // worlds dissolve into depressions; only genuinely cold worlds crack into crevasses.
        double fissure = tectonic * tectonic;
        double sinkhole = 0.45 * erosion * age + 0.25 * wet * age;
        // Crevasses are RARE, spatially coherent cracks — never a planet-wide lattice.
        double crevasse = cold ? 0.30 + 0.35 * coldness * erosion : 0.05 * coldness;
        double depression = 0.40 * erosion * (1.0 - tectonic) + 0.15 * age;
        return new LandformBudget(
                clamp01(gully), clamp01(ravine), clamp01(fissure),
                clamp01(sinkhole), clamp01(crevasse), clamp01(depression));
    }

    /** The empty budget (no landforms — proof / fallback worlds). */
    public static final LandformBudget NONE =
            new LandformBudget(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    /** True when the planet has any meaningful local landform drive at all. */
    public boolean any() {
        return gully > 0.08 || ravine > 0.08 || fissure > 0.08
                || sinkhole > 0.08 || crevasse > 0.08 || depression > 0.08;
    }

    /** One-line summary for the F3 / diagnostics. */
    public String summary() {
        return String.format(java.util.Locale.ROOT,
                "gully=%.2f ravine=%.2f fissure=%.2f sink=%.2f crev=%.2f dep=%.2f",
                gully, ravine, fissure, sinkhole, crevasse, depression);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
