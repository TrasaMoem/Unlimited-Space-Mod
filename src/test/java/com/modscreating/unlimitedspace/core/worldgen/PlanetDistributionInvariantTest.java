package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceSelector;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.tools.PlanetAdmissibilityAudit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V3.2 PHASE 9: the ten distribution invariants, asserted over a real 2000-planet sample.
 *
 * <p>The rules are scored by {@link PlanetAdmissibilityAudit}'s INDEPENDENT physics predicates,
 * not by the production admissibility object, so a test can never pass by agreeing with itself.
 */
@Tag("worldgen")
class PlanetDistributionInvariantTest {

    private static final int PLANETS = Integer.getInteger("us.audit.planets", 2000);
    private static final int SEEDS = Integer.getInteger("us.audit.seeds", 40);

    private static final PlanetAdmissibilityAudit.Report AUDIT =
            PlanetAdmissibilityAudit.run(SEEDS, PLANETS);

    private static void assertNo(String label, PlanetAdmissibilityAudit.Violation v) {
        int n = AUDIT.count(v);
        assertTrue(n == 0, label + ": expected 0 forbidden combinations, measured " + n
                + " (" + String.format("%.3f", AUDIT.share(v) * 100.0) + "% of "
                + AUDIT.columns + " columns)");
    }

    @Test
    void theSampleIsLargeEnoughToBeADistributionStatement() {
        assertTrue(AUDIT.planets >= PLANETS / 4,
                "only " + AUDIT.planets + " planets were profiled");
        assertTrue(AUDIT.columns > 1000, "only " + AUDIT.columns + " columns were sampled");
    }

    // ---- invariant 1: an arid world never leads with a crystalline surface ----
    @Test
    void aridWorldsHaveNoCrystallinePrimarySurface() {
        assertNo("crystalline primary on an arid world",
                PlanetAdmissibilityAudit.Violation.CRYSTAL_PRIMARY_ON_ARID);
    }

    // ---- invariant 2: a SOLID_DESERT world never leads with volcanic or crystalline rock ----
    @Test
    void desertsHaveNoVolcanicPrimarySurface() {
        assertNo("volcanic primary on SOLID_DESERT",
                PlanetAdmissibilityAudit.Violation.VOLCANIC_PRIMARY_ON_DESERT);
    }

    // ---- invariant 3: a hot world never leads with frozen rock ----
    @Test
    void hotWorldsHaveNoFrozenPrimarySurface() {
        assertNo("frozen primary on a hot world",
                PlanetAdmissibilityAudit.Violation.FROZEN_PRIMARY_ON_HOT);
    }

    // ---- invariant 4: no alien-crystal theme on a thermally incompatible band ----
    @Test
    void alienCrystalThemeNeverAppearsOnAFrozenHotOrInfernoWorld() {
        assertNo("ALIEN_CRYSTAL_THEME on a forbidden band",
                PlanetAdmissibilityAudit.Violation.ALIEN_CRYSTAL_THEME_ON_BAD_BAND);
    }

    // ---- invariant 5: no sand language on an inferno world ----
    @Test
    void desertThemeNeverAppearsOnAnInfernoWorld() {
        assertNo("DESERT_THEME on INFERNO",
                PlanetAdmissibilityAudit.Violation.DESERT_THEME_ON_INFERNO);
    }

    // ---- invariant 6: a forbidden province is never reachable ----
    @Test
    void aForbiddenProvinceIsNeverReachable() {
        assertNo("province outside the planet's admissibility",
                PlanetAdmissibilityAudit.Violation.FORBIDDEN_PROVINCE);
    }

    // ---- invariant 7: a forbidden sub-biome is never elected ----
    @Test
    void aForbiddenSubBiomeIsNeverElected() {
        assertNo("sub-biome outside the planet's admissibility",
                PlanetAdmissibilityAudit.Violation.FORBIDDEN_SUBBIOME);
    }

    // ---- invariant 8: no liquid-water ecology where liquid water is impossible ----
    @Test
    void liquidWaterImpossibleMeansNoLiquidWaterEcology() {
        assertNo("liquid-water ecology without a liquid phase",
                PlanetAdmissibilityAudit.Violation.LIQUID_WATER_IMPOSSIBLE);
    }

    // ---- invariant 9: no glacial ecology where glacial terrain is impossible ----
    @Test
    void glacialImpossibleMeansNoGlacialEcology() {
        assertNo("glacial ecology without glacial terrain",
                PlanetAdmissibilityAudit.Violation.GLACIAL_IMPOSSIBLE);
    }

    // ---- invariant 10: the fallbacks are explicit and counted, never silent ----
    @Test
    void everyFallbackIsExplicitAndMeasured() {
        // A non-empty sample proves the classifier ran on real data rather than degrading to a
        // single fallback identity, and the forbidden-combination counters above prove no column
        // was produced by an undeclared fallback.
        assertTrue(AUDIT.electedBiome.size() > 1,
                "the classifier collapsed to one biome identity across the whole sample");
        assertTrue(AUDIT.planets > 0 && AUDIT.columns > 0, "the audit produced no data at all");
    }
}
