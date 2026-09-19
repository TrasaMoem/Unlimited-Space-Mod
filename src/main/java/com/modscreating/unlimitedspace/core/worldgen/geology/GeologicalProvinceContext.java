package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.List;

/**
 * The unified per-column geological context (R18 provincial-coherence stage).
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; GEOLOGICAL PROVINCE -&gt; PROVINCE CONTEXT (this)
 *                             -&gt; TERRAIN -&gt; MATERIALS -&gt; RESOURCES -&gt; FEATURES
 * </pre>
 *
 * <p>This is the single deterministic source of truth for a column: which province it belongs
 * to, how dominant/confident that classification is, and which shaping/feature/material
 * preferences apply. All consumers (terrain shaper, chunk generator, feature placer, resource /
 * vegetation / structure selectors) read the SAME context instead of re-classifying the column
 * with different algorithms — removing the R17 divergence where terrain modifiers, materials and
 * features each sampled provinces differently.
 *
 * <p><b>Performance:</b> a compact, immutable value — no allocations per column beyond the small
 * record; the classification reuses the O(1) {@link GeologicalProvinceSelector} field. Weights
 * are passed in already normalized, so no list allocation happens inside the hot path.
 *
 * <p>Pure domain, no Minecraft types; deterministic given {@code (provinceSeed, weights, x, z,
 * elevation01)}.
 *
 * @param province    the dominant geological province of the column
 * @param strength    relative dominance of that province within the reachable set (0..1)
 * @param confidence  spatial confidence in [0,1]: 0 exactly AT a province border, 1 well inside
 *                    it. Province weights are planet constants, so the province LABEL flips
 *                    abruptly where the noise crosses a cumulative-weight threshold — any
 *                    feature amplitude gated by the label alone steps by tens of blocks there.
 *                    Every continuous intensity below is scaled by this value, so province
 *                    influence fades smoothly in and out.
 * @param profile     the planet's physical profile
 */
public record GeologicalProvinceContext(
        GeologicalProvince province,
        double strength,
        double confidence,
        PlanetPhysicalProfile profile
) {

    /** A neutral fallback context (used when the map is unavailable). */
    public static GeologicalProvinceContext neutral(PlanetPhysicalProfile profile) {
        return new GeologicalProvinceContext(GeologicalProvince.PLAINS, 1.0, 1.0, profile);
    }

    // ------------------------------------------------------------- fast predicates

    public boolean isProvince(GeologicalProvince p) {
        return province == p;
    }

    public boolean isVolcanic() {
        return province == GeologicalProvince.VOLCANIC;
    }

    public boolean isCrystal() {
        return province == GeologicalProvince.CRYSTAL;
    }

    public boolean isCrater() {
        return province == GeologicalProvince.CRATER;
    }

    public boolean isGlacial() {
        return province == GeologicalProvince.GLACIAL;
    }

    public boolean isGeothermal() {
        return province == GeologicalProvince.GEOTHERMAL;
    }

    /**
     * Whether the province supports land vegetation. Volcano/lava and glacial provinces do not.
     */
    public boolean supportsVegetation() {
        return province != GeologicalProvince.VOLCANIC
                && province != GeologicalProvince.GEOTHERMAL
                && province != GeologicalProvince.GLACIAL;
    }

    /**
     * Whether the province favours lava-channel carving (volcanic / geothermal uplift regions).
     */
    public boolean favoursLavaChannels() {
        return province == GeologicalProvince.VOLCANIC || province == GeologicalProvince.GEOTHERMAL;
    }

    /** Whether the province favours crystal spires (localized, not planet-wide). */
    public boolean favoursSpires() {
        return province == GeologicalProvince.CRYSTAL || province == GeologicalProvince.GEOTHERMAL;
    }

    // ------------------------------------------------------------- R20 continuous intensities
    // Boolean province gates used to switch feature amplitudes ON/OFF between adjacent
    // columns — a hidden vertical wall at province borders. Feature gating must scale with
    // the CONTINUOUS province strength so it fades in/out smoothly.

    /** Continuous volcanic intensity in [0,1] (fades to 0 at province borders). */
    public double volcanicIntensity() {
        double f = province == GeologicalProvince.VOLCANIC ? 1.0
                : province == GeologicalProvince.GEOTHERMAL ? 0.6 : 0.0;
        return f * strength * confidence;
    }

    /** Continuous crystal-spire intensity in [0,1]. */
    public double crystalIntensity() {
        double f = province == GeologicalProvince.CRYSTAL ? 1.0
                : province == GeologicalProvince.GEOTHERMAL ? 0.3 : 0.0;
        return f * strength * confidence;
    }

    /** Continuous lava-channel intensity in [0,1]. */
    public double lavaIntensity() {
        return volcanicIntensity();
    }

    /** Whether the province favours impact ejecta / impact glass. */
    public boolean favoursImpact() {
        return province == GeologicalProvince.CRATER;
    }

    /** Material-affinity: bulbous per-province material preference (0 none .. 1 strong). */
    public double materialPreference(GeologicalProvince p) {
        return p == province ? 1.0 : 0.0;
    }

    /** Resource rarity multiplier for the province (e.g. volcanic hosts more metals). */
    public double resourceMultiplier(GeologicalProvince p) {
        if (p != province) return 0.0;
        return switch (province) {
            case VOLCANIC -> 1.6;
            case CRYSTAL, GLACIAL -> 1.35;
            case CRATER, SALT, GEOTHERMAL -> 1.2;
            default -> 1.0;
        };
    }
}