package com.modscreating.unlimitedspace.core.worldgen;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 — the ARCHITECTURE GUARD and the DEAD-OUTPUT contract.
 *
 * <p>This test reads the production sources themselves. It exists because the V3 defects that
 * matter most (a province switch driving terrain, a province acting as a lava gate, a random river
 * lottery, a silent first-biome fallback) are invisible to value assertions: they produce perfectly
 * plausible numbers. They can only be caught by inspecting the shape of the code.
 */
@Tag("worldgen")
class V3ArchitectureGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    private static List<Path> sourcesUnder(String packagePath) throws IOException {
        List<Path> out = new ArrayList<>();
        Path root = MAIN.resolve(packagePath);
        if (!Files.isDirectory(root)) return out;
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * The source with every comment line removed.
     *
     * <p>The guard has to inspect CODE, not prose: a file that documents the rule it obeys ("this
     * replaces the old {@code switch(province)} architecture") would otherwise be reported as
     * violating it.
     */
    private static String codeOnly(Path p) {
        StringBuilder sb = new StringBuilder();
        for (String line : read(p).split("\n")) {
            String t = line.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    void theDuneFieldIsNotGatedOnADiscreteMorphology() throws IOException {
        // The old architecture was: `morphology == DUNES -> dunes, else 0`. Dunes must now be a
        // continuous function of the planet character, never of a discrete morphology winner.
        String src = codeOnly(MAIN.resolve(
                "com/modscreating/unlimitedspace/core/worldgen/terrain/DuneMorphologyField.java"));
        assertTrue(!src.contains("== TerrainMorphology.DUNES"),
                "dunes must not be gated on a discrete morphology identity");
    }

    @Test
    void theElevationFieldNeverBranchesOnAProvinceId() throws IOException {
        // "No integer province ID can directly modify height." The elevation authority must not even
        // mention the province type.
        String src = codeOnly(MAIN.resolve(
                "com/modscreating/unlimitedspace/core/worldgen/terrain/ElevationField.java"));
        assertTrue(!src.contains("GeologicalProvince"),
                "ElevationField must not read a discrete geological province");
    }

    @Test
    void lavaEligibilityIsConsumedByProductionCode() throws IOException {
        // Every lava path must consult LavaEligibility; a province label is never sufficient.
        List<Path> sources = sourcesUnder("com/modscreating/unlimitedspace/core/worldgen");
        sources.addAll(sourcesUnder("com/modscreating/unlimitedspace/worldgen"));
        int consumers = 0;
        for (Path p : sources) {
            if (read(p).contains("LavaEligibility")) {
                consumers++;
            }
        }
        assertTrue(consumers > 0, "LavaEligibility must be consumed by production code");
    }

    @Test
    void hydrologyIsNotARandomLottery() throws IOException {
        // Rivers come from a solved drainage network. A random line or a "hasRivers" coin flip in
        // the terrain path is exactly the defect the V3 spec forbids.
        for (Path p : sourcesUnder("com/modscreating/unlimitedspace/core/worldgen/hydrology")) {
            String src = read(p);
            assertTrue(!src.contains("ThreadLocalRandom"),
                    p + ": hydrology must be deterministic, never random");
            assertTrue(!src.contains("new Random("),
                    p + ": hydrology must be deterministic, never random");
        }
        String solver = read(MAIN.resolve(
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/DrainageSolver.java"));
        assertTrue(solver.contains("flowAccumulation"),
                "rivers must be derived from flow accumulation");
        assertTrue(solver.contains("filled"),
                "the solver must pit-fill before routing, or lakes cannot be real");
    }

    @Test
    void theBiomeFieldIsAScoreAndNotAProvinceSwitch() throws IOException {
        String src = codeOnly(MAIN.resolve(
                "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeMaskField.java"));
        assertTrue(src.toLowerCase(java.util.Locale.ROOT).contains("score"),
                "the biome field must classify by a continuous score");
        assertTrue(!src.contains("switch (province") && !src.contains("switch(province"),
                "the biome field must not switch on a province id");
    }

    @Test
    void everyNewV3FieldHasAProductionConsumer() throws IOException {
        // A generation field that nothing reads is dead weight that will silently rot. Each V3
        // field must be referenced from at least one OTHER production source file.
        String[] required = {
                "core/worldgen/terrain/ElevationField.java",
                "core/worldgen/terrain/MountainSystemField.java",
                "core/worldgen/terrain/DuneMorphologyField.java",
                "core/worldgen/terrain/GlacierFlowField.java",
                "core/worldgen/terrain/WindDirectionField.java",
                "core/worldgen/hydrology/HydrologyField.java",
                "core/worldgen/hydrology/DrainageSolver.java",
                "core/worldgen/biome/BiomeMaskField.java",
                "core/worldgen/materials/SurfaceMaterialField.java",
                "core/worldgen/features/FeaturePlacementField.java",
                "core/worldgen/character/PlanetCharacter.java",
                "core/worldgen/character/LavaEligibility.java"
        };
        List<Path> all = new ArrayList<>(sourcesUnder("com"));
        for (String rel : required) {
            String type = rel.substring(rel.lastIndexOf('/') + 1).replace(".java", "");
            int consumers = 0;
            for (Path p : all) {
                if (p.toString().endsWith(rel)) continue;   // skip the declaration itself
                if (read(p).contains(type)) {
                    consumers++;
                }
            }
            assertTrue(consumers > 0,
                    "dead generation field: " + rel + " has no production consumer");
        }
    }

    @Test
    void theFiveV3StagesAreAllPresentOnDisk() {
        // The stages the ACT defines must exist as real files, not as a plan.
        String[] mustExist = {
                "com/modscreating/unlimitedspace/core/worldgen/character/PlanetCharacter.java",
                "com/modscreating/unlimitedspace/core/worldgen/character/PlanetCharacterWeights.java",
                "com/modscreating/unlimitedspace/core/worldgen/character/LavaEligibility.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/ElevationField.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/ElevationSample.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/MountainSystemField.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/GlacierFlowField.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/WindDirectionField.java",
                "com/modscreating/unlimitedspace/core/worldgen/terrain/DuneMorphologyField.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/HydrologyField.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/HydrologyTile.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/DrainageSolver.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/RiverMaskField.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/LakeBasinField.java",
                "com/modscreating/unlimitedspace/core/worldgen/hydrology/HydrologyScratch.java",
                "com/modscreating/unlimitedspace/core/worldgen/climate/ClimateField.java",
                "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeMaskField.java",
                "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeScoreWeights.java",
                "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeCandidate.java",
                "com/modscreating/unlimitedspace/core/worldgen/biome/BioticWeights.java",
                "com/modscreating/unlimitedspace/core/worldgen/materials/SurfaceMaterialField.java",
                "com/modscreating/unlimitedspace/core/worldgen/features/FeaturePlacementField.java"
        };
        for (String rel : mustExist) {
            assertTrue(Files.isRegularFile(MAIN.resolve(rel)), "missing V3 stage file: " + rel);
        }
    }
}
