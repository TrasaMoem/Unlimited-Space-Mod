package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import com.modscreating.unlimitedspace.tools.V3PreviewFacts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK H — the V3 preview reports EVERY required distribution, machine-readably.
 *
 * <p>The fact document is what makes the preview checkable without an image viewer, so the ACT
 * requires it to be complete rather than illustrative. This test asserts that every required key
 * is present, that the numbers are physically sane, and that the summary statistics are ordered
 * (min &lt;= p10 &lt;= median &lt;= p90 &lt;= max) — an unordered quantile is a broken one.
 */
@Tag("worldgen")
@Tag("audit")
class V3PreviewFactsTest {

    private static V3PreviewFacts.Facts earthlike() throws Exception {
        var profile = V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45);
        var sampler = V3PreviewChannels.samplerFor(0x51EEDL, profile, ReliefArchetype.ROLLING);
        return V3PreviewChannels.render("earthlike", sampler,
                V3PreviewChannels.surfaceMode(profile, false),
                java.nio.file.Path.of("build", "v31-preview-facts", "earthlike").toFile(), 64, 80);
    }

    @Test
    void everyRequiredFactKeyIsPresent() throws Exception {
        var facts = earthlike();
        String doc = String.join("\n", facts.render());
        for (String key : new String[]{
                "duneShare", "glacialShare", "volcanicShare", "alpineShare", "rockShare",
                "organicShare", "sedimentShare", "wetShare", "lavaEligibilityShare",
                "heightMin", "heightP10", "heightMedian", "heightP90", "heightMax",
                "elevation01Min", "elevation01Median", "elevation01Max",
                "slopeMedian", "slopeP90", "positiveReliefShare", "negativeReliefShare",
                "mountainShare", "valleyShare", "basinShare",
                "riverShare", "lakeShare", "maxFlowAccumulation", "meanFlowAccumulation",
                "hydrologyContinuityFailures", "scoreMarginP10", "scoreMarginMedian",
                "scoreMarginP90", "provinceAgreement", "biomeVsProvinceDelta",
                "lavaEligibleShare", "actualLavaShare", "maxLavaBodySize",
                "geothermalExceptions", "terrainQueryCount", "hydrologyQueryCount",
                "lavaQueryCount", "hasSolidSurface"}) {
            assertTrue(doc.contains(key), "the fact document is missing a required key: " + key);
        }
        assertTrue(doc.contains("biomeShare["), "biome shares must be reported");
        assertTrue(doc.contains("subBiomeShare["), "sub-biome shares must be reported");
        assertTrue(doc.contains("materialShare["), "material shares must be reported");
        assertTrue(doc.contains("surfaceCategoryShare["), "surface category shares must be reported");
        assertTrue(doc.contains("provinceShare["), "province shares must be reported");
        assertTrue(doc.contains("biomeTransitionsInsideProvince"),
                "the in-province transition count is a required diagnostic");
        assertTrue(doc.contains("biomeTransitionsAcrossProvince"),
                "the across-boundary transition count is a required diagnostic");
    }

    @Test
    void theTerrainQuantilesAreOrderedAndPhysicallySane() throws Exception {
        V3PreviewFacts.Facts f = earthlike();
        assertTrue(f.heightMin() <= f.heightP10(), "height quantiles must be ordered");
        assertTrue(f.heightP10() <= f.heightMedian(), "height quantiles must be ordered");
        assertTrue(f.heightMedian() <= f.heightP90(), "height quantiles must be ordered");
        assertTrue(f.heightP90() <= f.heightMax(), "height quantiles must be ordered");
        assertTrue(f.heightMax() - f.heightMin() > 10.0,
                "a real planet must have real relief, got " + (f.heightMax() - f.heightMin()));
        assertTrue(f.elevation01Min() >= 0.0 && f.elevation01Max() <= 1.0,
                "elevation01 must stay normalised");
        assertTrue(f.elevation01Min() <= f.elevation01Median());
        assertTrue(f.elevation01Median() <= f.elevation01Max());
        assertTrue(f.slopeMedian() >= 0.0 && f.slopeMedian() <= 1.0);
        assertTrue(f.slopeMedian() <= f.slopeP90() + 1e-9);
        assertTrue(f.positiveReliefShare() > 0.0 && f.negativeReliefShare() > 0.0,
                "the terrain must have BOTH relief signs");
    }

    @Test
    void theSharesAreFractionsAndTheMarginsAreOrdered() throws Exception {
        V3PreviewFacts.Facts f = earthlike();
        for (double v : new double[]{f.riverShare(), f.lakeShare(), f.mountainShare(),
                f.valleyShare(), f.basinShare(), f.actualLavaShare(), f.lavaEligibleShare(),
                f.provinceAgreement()}) {
            assertTrue(v >= 0.0 && v <= 1.0, "a share must be a fraction, got " + v);
        }
        assertTrue(f.marginP10() <= f.marginMedian() + 1e-9, "score-margin quantiles ordered");
        assertTrue(f.marginMedian() <= f.marginP90() + 1e-9, "score-margin quantiles ordered");
        assertTrue(f.marginP10() >= 0.0, "a margin is never negative");
        assertTrue(f.actualLavaShare() <= f.lavaEligibleShare() + 1e-9,
                "actual lava can never exceed lava eligibility");
    }

    @Test
    void theFactDocumentIsWrittenToDiskForTheFinalPreviewPackage() throws Exception {
        earthlike();
        java.io.File facts = java.nio.file.Path.of("build", "v31-preview-facts", "earthlike", "facts.txt").toFile();
        assertTrue(facts.isFile() && facts.length() > 0, "facts.txt must be exported");
        List<String> lines = Files.readAllLines(facts.toPath(), StandardCharsets.UTF_8);
        assertTrue(lines.size() > 40, "the fact document must be substantive, got " + lines.size());
    }

    @Test
    void aGasGiantPreviewReportsNoSurfaceAndNoQueries() throws Exception {
        var profile = V3BiomeRuntimeIntegrationTest.profile(0.55, 0.4, 0.3, 0.5, 0.3, 0.3, 0.4);
        var sampler = V3PreviewChannels.samplerFor(0xBEEFL, profile, ReliefArchetype.ROLLING);
        PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(profile, true);
        V3PreviewFacts.Facts f = V3PreviewChannels.render("gasgiant", sampler, mode,
                java.nio.file.Path.of("build", "v31-preview-facts", "gasgiant").toFile(), 24, 128);
        assertEquals(PlanetSurfaceMode.GAS_GIANT, mode);
        assertTrue(!f.hasSolidSurface());
        assertEquals(0, f.terrainQueryCount());
        assertEquals(0, f.hydrologyQueryCount());
        assertEquals(0, f.lavaQueryCount());
    }
}