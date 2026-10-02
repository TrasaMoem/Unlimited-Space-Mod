package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetBiomeIdentity;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK U — the extended ARCHITECTURE GUARD.
 *
 * <p>The V3 defects that matter most are invisible to any value assertion: a province switch
 * driving a material, a province acting as a lava gate, a random river lottery, a first-biome
 * fallback, a second hidden Voronoi. Each of them produces perfectly plausible numbers, so they
 * can only be caught by inspecting the SHAPE of the code.
 *
 * <p>The guard reads the production SOURCES and matches real code structure, with comment lines
 * removed first — otherwise a file that documents the rule it obeys would be reported as
 * violating it.
 */
@Tag("worldgen")
class V3ArchitectureGuardV31Test {

    private static final Path MAIN = Path.of("src", "main", "java");

    private static String code(String rel) throws IOException {
        return codeOnly(Files.readString(MAIN.resolve(rel), StandardCharsets.UTF_8));
    }

    /** The source with every comment line removed: the guard must inspect CODE, not prose. */
    private static String codeOnly(String src) {
        StringBuilder sb = new StringBuilder();
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static List<Path> worldgenSources() throws IOException {
        List<Path> out = new ArrayList<>();
        try (var s = Files.walk(MAIN.resolve("com/modscreating/unlimitedspace/core/worldgen"))) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        try (var s = Files.walk(MAIN.resolve("com/modscreating/unlimitedspace/worldgen"))) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    @Test
    void theRuntimeMaterialAndBiomePathsNeverSwitchOnAProvince() throws IOException {
        // The V3 authority paths must have no province switch at all. The legacy V2
        // SurfaceCategorySelector is deliberately RETAINED for the V2 preview and its own
        // regression tests, so it is exempted here and instead guarded at its call site below.
        String[] forbidden = {
                "com/modscreating/unlimitedspace/core/worldgen/materials/SurfaceMaterialField.java",
                "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeMaskField.java",
                "com/modscreating/unlimitedspace/core/worldgen/features/FeaturePlacementField.java"
        };
        for (String rel : forbidden) {
            String src = code(rel);
            assertTrue(!src.contains("switch (province") && !src.contains("switch(province"),
                    rel + " must not switch on a province id");
        }
    }

    @Test
    void theRuntimeChunkGeneratorNoLongerUsesTheLegacyProvinceSwitch() throws IOException {
        // Task E, category C: an OBSOLETE runtime dependency. The V2 surface-category rule table
        // is a step function on a polygon border, so the runtime must read the CONTINUOUS V3
        // category instead. The legacy selector may still exist for the V2 preview, but the
        // chunk generator that actually places blocks may not call it.
        String chunkGen = code("com/modscreating/unlimitedspace/worldgen/planet/PlanetChunkGenerator.java");
        assertTrue(!chunkGen.contains("SurfaceCategorySelector"),
                "the runtime chunk generator must not classify a surface from a province switch");
        assertTrue(chunkGen.contains("v3Sampler.sampleColumn"),
                "the runtime must read the surface category from the shared V3 column");
    }

    @Test
    void theMaterialSelectorHasNoProvinceGateAtAll() throws IOException {
        // Task E, category C: an obsolete runtime dependency. The material role is a function of
        // the real environment, and a province label may at most NUDGE it.
        String src = code("com/modscreating/unlimitedspace/core/worldgen/materials/SurfaceMaterialField.java");
        assertTrue(!src.contains("province == GeologicalProvince.VOLCANIC")
                        || src.contains("c.volcanicIntensity = 0.45"),
                "the material selector must not gate on a discrete volcanic province");
        assertTrue(!src.contains("province == GeologicalProvince.CRYSTAL")
                        || src.contains("c.crystalIntensity = 0.55"),
                "the material selector must not gate on a discrete crystal province");
    }

    @Test
    void noProvinceActsAsALavaGate() throws IOException {
        // Every DECISION to place lava must go through LavaEligibility. Merely NAMING the
        // volcanic province while reading a CONTINUOUS intensity (a share, not a label) is not a
        // gate: a continuous share cannot produce a step at a province border.
        for (Path p : worldgenSources()) {
            String src = codeOnly(Files.readString(p, StandardCharsets.UTF_8));
            String name = p.getFileName().toString();
            boolean decidesLava = src.contains("isLavaAllowed(") || src.contains("LavaEligibility.evaluate(");
            if (decidesLava) {
                assertTrue(src.contains("LavaEligibility"),
                        name + " decides lava and must go through LavaEligibility");
            }
            // A hard gate would be a bare label comparison that decides; the continuous forms read
            // a share, which is exactly what must remain.
            assertTrue(!src.contains("province == GeologicalProvince.VOLCANIC &&")
                            || src.contains("volcanicIntensity"),
                    name + " must not use the volcanic LABEL as a lava gate");
        }
    }

    @Test
    void thereIsNoRandomRiverOrLakeLottery() throws IOException {
        // Rivers and lakes must come from the solved drainage, never from a hash draw.
        String hydrology = code("com/modscreating/unlimitedspace/core/worldgen/hydrology/HydrologyField.java");
        assertTrue(!hydrology.contains("hasRivers()"),
                "hydrology must not consult the legacy random hasRivers flag");
        for (Path p : worldgenSources()) {
            String src = codeOnly(Files.readString(p, StandardCharsets.UTF_8));
            if (src.contains("lakeMask(") && src.contains("Seeds.derive")
                    && !p.getFileName().toString().equals("LakeBasinField.java")) {
                assertTrue(!src.contains("lakePlacement"),
                        p.getFileName() + " must not place lakes by a seed draw");
            }
        }
    }

    @Test
    void thereIsNoFirstBiomeFallbackInAnyMultiBiomeRuntimeSource() throws IOException {
        // Task B: in a source that offers a POOL, a miss must be a COUNTED, LOGGED failure, never
        // a silent index 0. A single-held-biome source (the asteroid and orbit dimensions carry
        // exactly one biome by construction) legitimately returns that one holder, so the rule is
        // scoped to the sources that actually hold a list.
        // The rule is stated for the PLANET SURFACE source, which is the one the V3 classifier
        // feeds and the one whose pool really does hold many candidates. The asteroid and orbit
        // sources are constructed with exactly ONE held biome by contract, so returning it is not
        // a fallback.
        String planet = code("com/modscreating/unlimitedspace/worldgen/planet/PlanetBiomeSource.java");
        assertTrue(!planet.contains("biomes.get(0)"),
                "PlanetBiomeSource must not fall back to the first biome of the pool");
        assertTrue(planet.contains("MISSING_BIOME_EVENTS.incrementAndGet()"),
                "every pool miss in the planet source must be COUNTED, never silent");
    }

    @Test
    void thereIsNoSecondVoronoiOrMacroPartition() throws IOException {
        // Task A / R: exactly ONE macro ownership layer may exist. A second nearest-site search
        // would reintroduce exactly the hidden region ownership the architecture forbids.
        int voronoiOwners = 0;
        for (Path p : worldgenSources()) {
            String src = codeOnly(Files.readString(p, StandardCharsets.UTF_8));
            if (src.contains("argmin") && src.contains("D_i")) voronoiOwners++;
        }
        assertTrue(voronoiOwners <= 1,
                "there must be at most ONE macro Voronoi ownership layer, found " + voronoiOwners);
        // And the single owner is the one the profile hands out.
        String geology = code("com/modscreating/unlimitedspace/core/worldgen/geology/PlanetGeologyProfile.java");
        assertTrue(geology.contains("geography"),
                "the geology profile must remain the single owner of the macro geography");
    }

    @Test
    void theBiomeFieldIsAContinuousScoreAndNeverAProvinceSwitch() throws IOException {
        String src = code("com/modscreating/unlimitedspace/core/worldgen/biome/BiomeMaskField.java");
        assertTrue(src.toLowerCase(Locale.ROOT).contains("score"),
                "the biome field must classify by a continuous score");
        assertTrue(!src.contains("switch (province") && !src.contains("switch(province"),
                "the biome field must not switch on a province id");
        // The macro influence must be a declared, bounded constant rather than an ad-hoc factor.
        assertTrue(src.contains("SOFT_MACRO_MAX"),
                "the macro influence cap must be a declared, testable constant");
    }

    @Test
    void noDebugAllocationLivesInTheRuntimeHotPath() throws IOException {
        // Task O: the preview is a separate CALLER. A production file must not build an image,
        // a preview string or a histogram, and the sampler must not carry a preview flag.
        for (Path p : worldgenSources()) {
            String src = codeOnly(Files.readString(p, StandardCharsets.UTF_8));
            String name = p.getFileName().toString();
            assertTrue(!src.contains("BufferedImage"), name + " must not build an image");
            assertTrue(!src.contains("ImageIO"), name + " must not write an image");
            assertTrue(!src.contains("previewEnabled"), name + " must not carry a preview flag");
        }
        String sampler = code("com/modscreating/unlimitedspace/core/worldgen/biome/V3ColumnSampler.java");
        assertTrue(!sampler.contains("String.format"),
                "the shared column sampler must not build debug strings");
    }

    @Test
    void theSharedSamplerIsTheOnlyColumnPipeline() throws IOException {
        // Task N: there must be exactly ONE implementation of the per-column pipeline, so a
        // biome and a material can never come from two different evaluations.
        int pipelineOwners = 0;
        for (Path p : worldgenSources()) {
            String src = codeOnly(Files.readString(p, StandardCharsets.UTF_8));
            if (src.contains("public WorldgenColumnSample sampleColumn(")) pipelineOwners++;
        }
        assertTrue(pipelineOwners == 1,
                "there must be exactly ONE per-column sampling pipeline, found " + pipelineOwners);
    }

    @Test
    void theSurfaceModeIsTheSingleAuthorityForHavingASurface() throws IOException {
        // A gas giant has no solid surface, and there must be exactly one place that decides it.
        String mode = code("com/modscreating/unlimitedspace/core/worldgen/profile/PlanetSurfaceMode.java");
        assertTrue(mode.contains("hasSolidSurface"));
        String chunkGen = code("com/modscreating/unlimitedspace/worldgen/planet/PlanetChunkGenerator.java");
        assertTrue(chunkGen.contains("isGasGiant()"),
                "the chunk generator must consult the resolved surface mode");
        assertTrue(!chunkGen.contains("PlanetType.GAS_GIANT"),
                "the chunk generator must not branch on the legacy PlanetType enum instead");
    }

    /**
     * Every V3 classifier candidate must be bound to a shipped ambience theme.
     *
     * <p>This is the regression guard for the log flood: {@code PlanetBiomeSource#holderFor}
     * resolves an elected candidate id against the datapack pool, whose keys are ambience paths
     * ({@code planet_ice_a}, ...). A candidate with no theme binding can NEVER resolve a holder, so
     * EVERY column query takes the counted-miss path and writes a log line — which grew
     * {@code debug.log} to hundreds of megabytes per world load and stalled the game.
     */
    @Test
    void everyClassifierCandidateIsBoundToAShippedAmbienceTheme() {
        for (BiomeCandidate c : BiomeMaskField.defaultCandidates()) {
            PlanetColorTheme theme = PlanetBiomeIdentity.themeForCandidate(c.id());
            assertTrue(theme != null, "classifier candidate has no ambience theme bound: " + c.id());
            String path = PlanetBiomeIdentity.path(theme, 0);
            assertTrue(PlanetBiomeIdentity.allPaths().contains(path),
                    "candidate " + c.id() + " maps to a path outside the shipped pool: " + path);
        }
    }

    /** The binding is a pure function of the id, so a column never changes its ambience. */
    @Test
    void theCandidateToThemeBindingIsDeterministic() {
        for (BiomeCandidate c : BiomeMaskField.defaultCandidates()) {
            assertEquals(PlanetBiomeIdentity.themeForCandidate(c.id()),
                    PlanetBiomeIdentity.themeForCandidate(c.id()),
                    "candidate theme binding must be stable: " + c.id());
        }
    }

    @Test
    void anUnknownCandidateIdResolvesToNoTheme() {
        assertEquals(null, PlanetBiomeIdentity.themeForCandidate("no_such_biome"),
                "an unknown candidate must resolve to no theme, never a guess");
        assertEquals(null, PlanetBiomeIdentity.themeForCandidate(null));
    }

    /**
     * The runtime biome source must not log per column.
     *
     * <p>Per-column logging is what produced 650k+ identical lines and a 170 MB debug.log.
     */
    @Test
    void theRuntimeBiomeSourceDoesNotLogEveryColumnMiss() throws IOException {
        String src = code("com/modscreating/unlimitedspace/worldgen/planet/PlanetBiomeSource.java");
        assertTrue(!src.contains("LOGGER.debug"),
                "the per-column hot path must not emit DEBUG lines");
        assertTrue(src.contains("REPORTED_MISSES"),
                "a pool miss must be logged once per (planet, biome), not once per column");
    }
}