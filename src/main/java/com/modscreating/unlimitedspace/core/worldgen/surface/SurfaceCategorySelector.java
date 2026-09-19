package com.modscreating.unlimitedspace.core.worldgen.surface;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainArchetype;

/**
 * Deterministic classification of a column's {@link SurfaceCategory} (R20).
 *
 * <p>Implements the required storytelling link:
 * <pre>
 * PhysicalProfile + TerrainArchetype + Province -&gt; SurfaceCategory
 *   HOT + DRY + DESERT            -&gt; DUSTY / SANDY / ROCKY
 *   COLD + WET + GLACIAL          -&gt; FROZEN / GLACIAL
 *   HIGH_VOLCANIC                 -&gt; ASHEN / VOLCANIC / ROCKY
 *   HIGH_WATER + BASIN            -&gt; SEDIMENTARY / MUDDY
 *   HIGH_CRYSTAL                  -&gt; CRYSTALLINE / ROCKY
 * </pre>
 * Root cause of the old "surface solid rocky everywhere" bug: {@code PlanetSurface} is mapped
 * 1:1 from the coarse {@code PlanetType} (ROCKY/FOREST/BARREN → SOLID_ROCKY), so most planets
 * collapsed onto one rocky identity. This selector restores variety WITHOUT touching the
 * planet-identity pipeline.
 */
public final class SurfaceCategorySelector {

    private SurfaceCategorySelector() {}

    /** Legacy 3-arg classification (R20 contract, kept for existing call-sites/tests). */
    public static SurfaceCategory classify(PlanetPhysicalProfile p, TerrainArchetype archetype,
                                           GeologicalProvince province) {
        return classify(p, archetype, province, null, null, 0.5);
    }

    /**
     * R21 COMPOSITE classification:
     *
     * <pre>
     * Planet Climate + Planet Relief + Geology(province) + elevation -&gt; SurfaceCategory
     *   COLD + MOUNTAIN + GLACIAL  -&gt; GLACIAL / FROZEN
     *   COLD + BASIN  + HIGH WATER -&gt; FROZEN SEDIMENTARY
     *   HOT  + VOLCANIC            -&gt; ASHEN / VOLCANIC
     *   DRY  + CANYON              -&gt; DUSTY / SANDY (badland)
     *   WET  + LOWLAND             -&gt; SEDIMENTARY / MUDDY
     *   CRYSTAL + HIGH CRYSTAL     -&gt; CRYSTALLINE
     * </pre>
     *
     * {@code ROCKY} is a genuine LAST RESORT (bare bedrock) — never the default answer.
     */
    public static SurfaceCategory classify(PlanetPhysicalProfile p, TerrainArchetype archetype,
                                           GeologicalProvince province,
                                           com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype climate,
                                           com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype relief,
                                           double elevation01) {
        if (p == null) return SurfaceCategory.ROCKY;
        if (province == null) province = GeologicalProvince.PLAINS;
        boolean hot = p.temperature() > 0.65;
        boolean cold = p.temperature() < 0.35;
        boolean dry = p.humidity() < 0.30;
        boolean wet = p.humidity() > 0.65;
        boolean water = p.waterAbundance() > 0.45;
        boolean frozenClimate = climate != null && climate.isFrozenDominant();
        boolean dryClimate = climate != null && climate.isDryDominant();
        boolean calmRelief = relief != null && relief.isCalm();
        boolean mountainous = relief != null && relief.isMountainous();

        switch (province) {
            case GLACIAL:  return cold || frozenClimate ? SurfaceCategory.GLACIAL : SurfaceCategory.FROZEN;
            case VOLCANIC: return hot ? SurfaceCategory.VOLCANIC : SurfaceCategory.ASHEN;
            case GEOTHERMAL: return SurfaceCategory.VOLCANIC;
            case CRYSTAL:  return SurfaceCategory.CRYSTALLINE;
            case SALT:     return SurfaceCategory.SALINE;
            case BASIN:    return water || wet ? SurfaceCategory.SEDIMENTARY
                    : (dry || dryClimate ? SurfaceCategory.DUSTY : SurfaceCategory.MUDDY);
            case CANYON:   return dry || dryClimate ? SurfaceCategory.SANDY : SurfaceCategory.DUSTY;
            case CRATER:   return dry || dryClimate ? SurfaceCategory.DUSTY : SurfaceCategory.ROCKY;
            case MOUNTAIN:
                // High cold mountains are frozen; only bare temperate/hot rock stays ROCKY.
                if (cold || frozenClimate) return SurfaceCategory.FROZEN;
                if (dry || dryClimate) return SurfaceCategory.DUSTY;
                return SurfaceCategory.ROCKY;
            case PLAINS:   break;
        }

        // --- PLAINS / generic landfall: climate-driven, ROCKY is the last resort ---
        if (p.crystalAbundance() > 0.60) return SurfaceCategory.CRYSTALLINE;
        if (p.volcanicActivity() > 0.60) return hot ? SurfaceCategory.VOLCANIC : SurfaceCategory.ASHEN;
        if (cold || frozenClimate) return wet ? SurfaceCategory.GLACIAL : SurfaceCategory.FROZEN;
        if (wet && water) return SurfaceCategory.SEDIMENTARY;
        if (hot && dry) return archetype == TerrainArchetype.DESERT_WORLD
                ? SurfaceCategory.SANDY : SurfaceCategory.DUSTY;
        if (dry || dryClimate) return SurfaceCategory.DUSTY;
        if (wet) return SurfaceCategory.SEDIMENTARY;
        // Temperate mid-range: fertile lowlands carry soil, calm worlds sediment, and only
        // bare mountainous bedrock stays genuinely ROCKY.
        if (p.organicPotential() > 0.25 && !mountainous) return SurfaceCategory.ORGANIC;
        if (calmRelief) return SurfaceCategory.SEDIMENTARY;
        if (elevation01 > 0.62 || mountainous) return SurfaceCategory.ROCKY;
        return SurfaceCategory.ORGANIC;
    }
}
