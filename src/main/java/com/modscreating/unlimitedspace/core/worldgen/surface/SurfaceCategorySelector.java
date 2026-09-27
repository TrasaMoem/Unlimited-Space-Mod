package com.modscreating.unlimitedspace.core.worldgen.surface;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.LandformIdentity;
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
     * R21 COMPOSITE classification. {@code ROCKY} is a genuine LAST RESORT (bare bedrock) —
     * never the default answer.
     *
     * <p>ACT 3 (P3.2): this is the shared decision chain; the phase-aware overload adds the
     * canonical water-phase gate on top of it.
     */
    public static SurfaceCategory classify(PlanetPhysicalProfile p, TerrainArchetype archetype,
                                           GeologicalProvince province,
                                           com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype climate,
                                           com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype relief,
                                           double elevation01) {
        return classifyCore(p, archetype, province, climate, relief, elevation01);
    }

    /**
     * ACT 3 (P3.2): PHASE-AWARE classification. Same decision chain; the canonical
     * {@link com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase} then
     * removes the categories that cannot physically exist:
     *
     * <pre>
     * VAPOR / NONE -&gt; no liquid-dependent ecology: MUDDY / SEDIMENTARY become dry ground
     *                 (DUSTY); the frozen categories are untouched.
     * SOLID        -&gt; liquid landscaping becomes the frozen transition the implementation
     *                 already defines: MUDDY -&gt; FROZEN, SEDIMENTARY -&gt; GLACIAL.
     * LIQUID/MIXED -&gt; unchanged (liquid ground is legal).
     * </pre>
     *
     * <p>Deliberately minimal: no unrelated category is touched and no new category appears.
     */
    public static SurfaceCategory classify(PlanetPhysicalProfile p, TerrainArchetype archetype,
                                           GeologicalProvince province,
                                           com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype climate,
                                           com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype relief,
                                           double elevation01,
                                           com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase) {
        return phaseAdjusted(
                classifyCore(p, archetype, province, climate, relief, elevation01), phase);
    }

    /**
     * ACT 4: PHASE-AWARE classification + bounded LANDFORM reaction. The optional landform
     * identity / strength only nudges a NEUTRAL surface toward the material the landform
     * implies (dune -&gt; sandy, crystal ridge -&gt; crystalline, caldera -&gt; volcanic, slump -&gt;
     * sediment). It can NEVER override the planetary physics (frozen/volcanic/crystalline/
     * saline categories), the ACT 3 province hierarchy or the water phase.
     */
    public static SurfaceCategory classify(PlanetPhysicalProfile p, TerrainArchetype archetype,
                                           GeologicalProvince province,
                                           com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype climate,
                                           com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype relief,
                                           double elevation01,
                                           com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase,
                                           LandformIdentity identity, double landformStrength) {
        SurfaceCategory c = phaseAdjusted(
                classifyCore(p, archetype, province, climate, relief, elevation01), phase);
        return applyLandformBias(c, identity, landformStrength);
    }

    /**
     * ACT 4: the bounded landform bias. Only neutral, soft/bare ground categories may be nudged,
     * and only when the landform is genuinely strong. Frozen / volcanic / crystalline / saline
     * categories are never touched (they already encode the planet's real identity).
     */
    private static SurfaceCategory applyLandformBias(SurfaceCategory c, LandformIdentity id,
                                                     double strength) {
        if (id == null || id == LandformIdentity.NONE || c == null) return c;
        if (strength < 0.40) return c;
        boolean neutral = c == SurfaceCategory.ROCKY || c == SurfaceCategory.DUSTY
                || c == SurfaceCategory.SEDIMENTARY || c == SurfaceCategory.MUDDY
                || c == SurfaceCategory.ORGANIC;
        if (!neutral) return c;
        switch (id) {
            case DUNE:
                return SurfaceCategory.SANDY;
            case CRYSTAL_RIDGE:
                return SurfaceCategory.CRYSTALLINE;
            case CALDERA:
                return c == SurfaceCategory.ROCKY || c == SurfaceCategory.DUSTY
                        ? SurfaceCategory.ASHEN : c;
            case CRATER:
                return c == SurfaceCategory.ROCKY ? SurfaceCategory.DUSTY : c;
            case SLUMP:
                return c == SurfaceCategory.ROCKY ? SurfaceCategory.SEDIMENTARY : c;
            default:
                return c;
        }
    }

    /** ACT 3 (P3.2): the minimal phase correction of a liquid-dependent category. */
    private static SurfaceCategory phaseAdjusted(
            SurfaceCategory c, com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel.Phase phase) {
        if (phase == null) return c;
        switch (phase) {
            case LIQUID, MIXED:
                return c;
            case VAPOR, NONE:
                if (c == SurfaceCategory.MUDDY || c == SurfaceCategory.SEDIMENTARY) {
                    return SurfaceCategory.DUSTY;
                }
                return c;
            case SOLID:
                if (c == SurfaceCategory.MUDDY) return SurfaceCategory.FROZEN;
                if (c == SurfaceCategory.SEDIMENTARY) return SurfaceCategory.GLACIAL;
                return c;
            default:
                return c;
        }
    }

    /** ACT 3 (P3.2): the shared (unchanged R21/R23) decision chain. */
    private static SurfaceCategory classifyCore(PlanetPhysicalProfile p, TerrainArchetype archetype,
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
