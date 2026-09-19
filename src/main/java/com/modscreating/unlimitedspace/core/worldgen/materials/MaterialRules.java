package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Central geology/climate rule engine deciding which materials may appear on which planet
 * (R16 planet-diversity foundation).
 *
 * <p>All material validity logic lives here so the rules are:</p>
 * <ul>
 *   <li><b>deterministic</b> — a pure boolean function of {@code (MaterialSpec, PlanetPhysicalProfile)};</li>
 *   <li><b>testable without Minecraft</b> — no block/registry types are involved;</li>
 *   <li><b>single-sourced</b> — the resolver, the palette and the debug screen all agree.</li>
 * </ul>
 *
 * <p>The core invariant (tested): a hot planet can never receive a cold-only material, and a
 * cold planet can never receive a hot-only family. An incompatible candidate is simply
 * <em>dropped</em> rather than substituted, so an impossible planet degrades to an empty
 * candidate list instead of an incoherent surface.
 */
public final class MaterialRules {

    private static final double EPS = 1.0e-9;

    private MaterialRules() {}

    /**
     * Whether {@code spec} is geologically/climatically admissible on the planet described by
     * {@code profile}. {@code null} on either side is always inadmissible.
     */
    public static boolean isCompatible(MaterialSpec spec, PlanetPhysicalProfile profile) {
        if (spec == null || profile == null) return false;

        // --- climate windows ---
        if (profile.temperature() < spec.minTemperature() - EPS) return false;
        if (profile.temperature() > spec.maxTemperature() + EPS) return false;
        if (profile.humidity() < spec.minHumidity() - EPS) return false;
        if (profile.humidity() > spec.maxHumidity() + EPS) return false;

        // --- family-level hard bans: an explicit cold/hot family never crosses over ---
        if (spec.family().isColdOnly() && profile.isHotWorld()) return false;
        if (spec.family().isHotOnly() && profile.isColdWorld()) return false;

        // --- geology compatibility flags ---
        if (spec.requiresVolcanism() && !profile.isVolcanicallyDriven()) return false;
        if (spec.requiresWater() && !hasUsableWater(profile)) return false;
        if (spec.requiresImpact() && !profile.isImpactDominated()) return false;
        if (spec.requiresCrystals() && profile.crystalAbundance() < 0.30 - EPS) return false;
        if (spec.minMetallicity() > 0.0 && profile.metallicity() < spec.minMetallicity() - EPS) return false;
        if (spec.minTectonicActivity() > 0.0
                && profile.tectonicActivity() < spec.minTectonicActivity() - EPS) return false;

        // --- tag-level cross-checks (kept minimal; the flags above carry the real logic) ---
        if (spec.hasTag(MaterialTag.CRYOGENIC) && profile.temperature() > 0.35 + EPS) return false;
        if (spec.hasTag(MaterialTag.MOLTEN) && profile.temperature() < 0.65 - EPS) return false;
        if (spec.hasTag(MaterialTag.SULFUROUS) && !profile.isVolcanicallyDriven()) return false;
        if (spec.hasTag(MaterialTag.SALINE) && profile.waterAbundance() < 0.20 - EPS) return false;

        return true;
    }

    /** "Has enough water" test shared by {@link MaterialTag#SALINE} and {@code requiresWater}. */
    private static boolean hasUsableWater(PlanetPhysicalProfile profile) {
        return profile.canHoldSurfaceLiquid() || profile.waterAbundance() >= 0.25;
    }

    /**
     * R18: material &harr; province coherence. Returns whether a material "belongs" in a given
     * geological province, phrased as positive motivation rather than a static 1:1 map — e.g. a
     * CRATER province favours impact/breccia material, a VOLCANIC province favours volcanic /
     * basaltic, a GLACIAL province favours frozen, a CRYSTAL province crystalline. Materials
     * without a strong tie (e.g. plain stone) are always considered coherent (the neutral case),
     * preserving variety: a province is not forced to a single fixed block.
     */
    public static boolean isCoherentWithProvince(MaterialSpec spec, GeologicalProvince province) {
        if (spec == null || province == null) return true;
        return switch (province) {
            case VOLCANIC -> spec.family().requiresVolcanism() || spec.hasTag(MaterialTag.VOLCANIC)
                    || spec.hasTag(MaterialTag.IGNEOUS) || spec.family() == MaterialFamily.ROCK_BASALTIC
                    || spec.family() == MaterialFamily.ROCK_VOLCANIC
                    || spec.family() == MaterialFamily.ROCK_SULFURIC
                    || spec.family() == MaterialFamily.ROCK_METALLIC;
            case GEOTHERMAL -> spec.hasTag(MaterialTag.GEOTHERMAL) || spec.hasTag(MaterialTag.VOLCANIC)
                    || spec.hasTag(MaterialTag.GLOWING);
            case GLACIAL -> spec.family().isColdOnly() || spec.hasTag(MaterialTag.COLD)
                    || spec.hasTag(MaterialTag.CRYOGENIC)
                    || spec.family() == MaterialFamily.SOIL_FROZEN;
            case CRYSTAL -> spec.hasTag(MaterialTag.CRYSTALLINE) || spec.hasTag(MaterialTag.GLOWING)
                    || spec.family() == MaterialFamily.CRYSTAL
                    || spec.family() == MaterialFamily.ROCK_CRYSTALLINE;
            case CRATER -> spec.hasTag(MaterialTag.IMPACT) || spec.family() == MaterialFamily.ROCK_IMPACT
                    || spec.hasTag(MaterialTag.FRACTURED) || spec.family() == MaterialFamily.ROCK_GLASS;
            case SALT -> spec.hasTag(MaterialTag.SALINE) || spec.family() == MaterialFamily.SOIL_SALT
                    || spec.family() == MaterialFamily.ROCK_SALINE
                    || spec.hasTag(MaterialTag.CALCAREOUS);
            case CANYON -> spec.family() == MaterialFamily.ROCK_SEDIMENTARY
                    || spec.hasTag(MaterialTag.LAYERED) || spec.hasTag(MaterialTag.DRY);
            case BASIN, PLAINS -> true; // neutral: any land material can appear on low plains
            case MOUNTAIN -> spec.family() == MaterialFamily.ROCK
                    || spec.family() == MaterialFamily.ROCK_DARK
                    || spec.family() == MaterialFamily.ROCK_LIGHT
                    || spec.family() == MaterialFamily.ROCK_METALLIC;
        };
    }

    /**
     * Filter a candidate list down to the materials admissible on {@code profile}.
     * Order is preserved so the result stays deterministic (a pure function of the input order).
     */
    public static List<MaterialSpec> admissible(List<MaterialSpec> candidates,
                                                PlanetPhysicalProfile profile) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<MaterialSpec> out = new ArrayList<>(candidates.size());
        for (MaterialSpec spec : candidates) {
            if (isCompatible(spec, profile)) out.add(spec);
        }
        return List.copyOf(out);
    }
}
