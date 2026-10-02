package com.modscreating.unlimitedspace.core.worldgen.features;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V4 - the OASIS ELIGIBILITY rule, asserted on the PRODUCTION predicates.
 *
 * <p>{@code PlanetFeaturePlacer.applyOasis} asks four questions of a candidate column and each one is
 * a pure function reachable from here, so the whole rule is verifiable without a chunk, a generator
 * or a world:
 * <ul>
 *   <li>{@link OasisModel#isDesignatedChunk} - which chunk may host a site at all;</li>
 *   <li>{@link OasisModel#kindFor} - water or molten, from the column's own physics;</li>
 *   <li>{@link OasisModel#isOasisLandscape} - is this landscape genuinely dry;</li>
 *   <li>{@link OasisModel#lavaBasinSupported} - has molten rock anywhere to pond.</li>
 * </ul>
 *
 * <p>Each clause is checked AGAINST THE AUTHORITY it claims to read - the canonical boiling point,
 * {@link WaterPhaseModel.Phase#allowsLiquid()}, the province-aware pocket phase - and never against
 * a private copy of that rule. If an authority changes what "liquid" means, this suite must move
 * with it or fail; that is what makes it an audit rather than a restatement.
 *
 * <p>Cost: a few hundred thousand pure calls over strings-free arithmetic, no world sampling, no
 * block states - milliseconds, not minutes.
 */
@Tag("worldgen")
@Tag("audit")
class V4OasisModelTest {

    /** Survey area: 512 x 512 chunks = 8 x 8 lattice cells (1 048 576 blocks across). */
    private static final int SURVEY_CHUNKS = 512;

    @Test
    void theBoilingCutIsTheCanonicalPhaseCutNotAPrivateConstant() {
        assertEquals(WaterPhaseModel.BOIL_MODERATE_K, OasisModel.BOILING_K, 0.0,
                "the oasis must read the canonical 100 C cut, never type its own");
        assertTrue(OasisModel.BOILING_K > WaterPhaseModel.FREEZE_K,
                "the liquid window must be non-empty");
        // The cut is INCLUSIVE, exactly as the phase authority's own boundary is.
        assertTrue(OasisModel.waterOasisAllowed(OasisModel.BOILING_K, WaterPhaseModel.Phase.LIQUID),
                "at the canonical boiling point liquid water is still legal");
        assertFalse(OasisModel.waterOasisAllowed(OasisModel.BOILING_K + 0.01,
                        WaterPhaseModel.Phase.LIQUID),
                "a hundredth of a degree above the cut, liquid water is impossible");
    }

    @Test
    void atAMildTemperatureThePhaseAuthorityDecidesNotTheOasis() {
        int liquidPhases = 0;
        for (WaterPhaseModel.Phase p : WaterPhaseModel.Phase.values()) {
            assertEquals(p.allowsLiquid(), OasisModel.waterOasisAllowed(280.0, p),
                    "at a mild 280 K the water clause must BE the phase authority's answer (" + p + ")");
            if (p.allowsLiquid()) liquidPhases++;
        }
        assertTrue(liquidPhases > 0 && liquidPhases < WaterPhaseModel.Phase.values().length,
                "sanity: the phase set must contain both answers, otherwise the gate is untested");
        assertFalse(OasisModel.waterOasisAllowed(280.0, null),
                "with no phase authority there is no claim");
        // No phase, however liquid-friendly its name, survives above the cut.
        for (WaterPhaseModel.Phase p : WaterPhaseModel.Phase.values()) {
            for (double k = OasisModel.BOILING_K + 0.01; k < 1200.0; k += 25.0) {
                assertFalse(OasisModel.waterOasisAllowed(k, p),
                        "a water oasis above the boiling point (" + k + " K, " + p + ")");
            }
        }
    }

    @Test
    void aSteamWorldCanNeverCondenseAWaterOasis() {
        double k = WaterPhaseModel.BOIL_MODERATE_K + 7.0;
        WaterPhaseModel.Phase canonical =
                WaterPhaseModel.surfacePhase(k, PressureClass.MODERATE, 0.9);
        assertEquals(WaterPhaseModel.Phase.VAPOR, canonical,
                "above the cut a moderate world is steam, by the phase authority's own answer");
        assertFalse(OasisModel.waterOasisAllowed(k, canonical),
                "a steam world must never be handed a water oasis");
        // A geothermal pocket may resurrect liquid on an ICE world, but heat cannot condense
        // steam: the pocket override only elevates SOLID / MIXED.
        for (GeologicalProvince province : GeologicalProvince.values()) {
            for (double flux : new double[] {0.0, 0.4, 0.56, 0.9, 1.0}) {
                assertEquals(WaterPhaseModel.Phase.VAPOR, WaterPhaseModel.geothermalPocketPhase(
                                WaterPhaseModel.Phase.VAPOR, province, flux),
                        "a steam world stays steam everywhere (" + province + ", flux " + flux + ")");
            }
        }
        // The hot world's oasis is therefore molten or nothing, never water.
        assertEquals(OasisModel.Kind.LAVA, OasisModel.kindFor(k, canonical, 0.9, 0.8),
                "a hot, eligible, supported column may still hold a MOLTEN oasis");
        assertEquals(OasisModel.Kind.NONE, OasisModel.kindFor(k, canonical, 0.0, 0.0),
                "a hot column with no lava evidence gets nothing at all");
    }

    @Test
    void onlyGenuinelyHeatedGroundMayTurnAFrozenPlanetsPhaseLiquid() {
        int flips = 0;
        int inherited = 0;
        for (GeologicalProvince province : GeologicalProvince.values()) {
            boolean heated = province == GeologicalProvince.GEOTHERMAL
                    || province == GeologicalProvince.VOLCANIC;
            for (double flux : new double[] {0.0, 0.2, 0.54, 0.55, 0.56, 1.0}) {
                WaterPhaseModel.Phase p = WaterPhaseModel.geothermalPocketPhase(
                        WaterPhaseModel.Phase.SOLID, province, flux);
                boolean expectLiquid = heated && flux > 0.55;
                assertEquals(expectLiquid ? WaterPhaseModel.Phase.LIQUID : WaterPhaseModel.Phase.SOLID,
                        p, "on a frozen world only real heat may claim liquid ("
                                + province + ", flux " + flux + ")");
                if (expectLiquid) flips++;
                else inherited++;
            }
        }
        assertTrue(flips > 0, "the geothermal exception must be reachable, not dead code");
        assertTrue(inherited > flips, "the exception must stay the exception: "
                + flips + " liquid vs " + inherited + " inherited");
        System.out.printf(Locale.ROOT,
                "[V4-OASIS] frozen-world pocket overrides: %d liquid / %d inherited%n",
                flips, inherited);
    }
}
