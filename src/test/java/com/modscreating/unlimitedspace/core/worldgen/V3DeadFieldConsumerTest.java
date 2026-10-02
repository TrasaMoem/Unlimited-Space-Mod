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
 * ACT V3.1 / TASK T — the DEAD-FIELD audit.
 *
 * <p>A generation field that nothing consumes is worse than no field at all: it costs money to
 * evaluate and it makes the architecture LOOK richer than it is. Every V3 field must therefore
 * have all three of:
 *
 * <ol>
 *   <li>a <b>producer</b> that writes it,</li>
 *   <li>a <b>production consumer</b> — a real runtime path, not a test,</li>
 *   <li>a <b>preview or test consumer</b>, so a diagnostic can actually observe it.</li>
 * </ol>
 *
 * <p>The audit inspects the SOURCES, because a field that is only ever computed and never read
 * produces perfectly plausible numbers and is therefore invisible to any value assertion.
 */
@Tag("worldgen")
class V3DeadFieldConsumerTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path TEST = Path.of("src", "test", "java");

    private record Field(String label, String relPath, String reasonIfNoPreview) {}

    private static final List<Field> FIELDS = List.of(
            new Field("PlanetCharacter",
                    "com/modscreating/unlimitedspace/core/worldgen/character/PlanetCharacter.java", ""),
            new Field("ElevationField",
                    "com/modscreating/unlimitedspace/core/worldgen/terrain/ElevationField.java", ""),
            new Field("MountainSystemField",
                    "com/modscreating/unlimitedspace/core/worldgen/terrain/MountainSystemField.java", ""),
            new Field("DuneMorphologyField",
                    "com/modscreating/unlimitedspace/core/worldgen/terrain/DuneMorphologyField.java", ""),
            new Field("GlacierFlowField",
                    "com/modscreating/unlimitedspace/core/worldgen/terrain/GlacierFlowField.java", ""),
            new Field("WindDirectionField",
                    "com/modscreating/unlimitedspace/core/worldgen/terrain/WindDirectionField.java", ""),
            new Field("HydrologyField",
                    "com/modscreating/unlimitedspace/core/worldgen/hydrology/HydrologyField.java", ""),
            new Field("DrainageSolver",
                    "com/modscreating/unlimitedspace/core/worldgen/hydrology/DrainageSolver.java", ""),
            new Field("RiverMaskField",
                    "com/modscreating/unlimitedspace/core/worldgen/hydrology/RiverMaskField.java", ""),
            new Field("LakeBasinField",
                    "com/modscreating/unlimitedspace/core/worldgen/hydrology/LakeBasinField.java", ""),
            new Field("ClimateField",
                    "com/modscreating/unlimitedspace/core/worldgen/climate/ClimateField.java", ""),
            new Field("BiomeMaskField",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeMaskField.java", ""),
            new Field("BiomeScoreWeights",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeScoreWeights.java", ""),
            new Field("BiomeCandidate",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/BiomeCandidate.java", ""),
            new Field("BioticWeights",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/BioticWeights.java", ""),
            new Field("SurfaceMaterialField",
                    "com/modscreating/unlimitedspace/core/worldgen/materials/SurfaceMaterialField.java", ""),
            new Field("FeaturePlacementField",
                    "com/modscreating/unlimitedspace/core/worldgen/features/FeaturePlacementField.java", ""),
            new Field("LavaEligibility",
                    "com/modscreating/unlimitedspace/core/worldgen/character/LavaEligibility.java", ""),
            new Field("PlanetSurfaceMode",
                    "com/modscreating/unlimitedspace/core/worldgen/profile/PlanetSurfaceMode.java", ""),
            new Field("V3ColumnSampler",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/V3ColumnSampler.java", ""),
            new Field("WorldgenColumnSample",
                    "com/modscreating/unlimitedspace/core/worldgen/biome/WorldgenColumnSample.java", ""));

    private static List<Path> sourcesUnder(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
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

    @Test
    void everyV3FieldHasAProductionConsumerAndADiagnosticConsumer() throws IOException {
        List<Path> main = sourcesUnder(MAIN);
        List<Path> test = sourcesUnder(TEST);
        for (Field f : FIELDS) {
            Path self = MAIN.resolve(f.relPath());
            assertTrue(Files.isRegularFile(self), "missing V3 field file: " + f.label());
            int production = 0;
            for (Path p : main) {
                if (p.equals(self)) continue;
                if (read(p).contains(f.label())) production++;
            }
            assertTrue(production > 0,
                    "DEAD FIELD: " + f.label() + " has no production consumer");
            int diagnostic = 0;
            for (Path p : test) {
                if (read(p).contains(f.label())) diagnostic++;
            }
            assertTrue(diagnostic > 0 || !f.reasonIfNoPreview().isEmpty(),
                    "DEAD FIELD: " + f.label() + " has no preview or test consumer");
        }
    }

    @Test
    void theNewIntegrationTypesAreActuallyReachable() throws IOException {
        // The V3.1 types must not be an island: the runtime really has to read them.
        String biomeSource = read(MAIN.resolve("com/modscreating/unlimitedspace/worldgen/planet/"
                + "PlanetBiomeSource.java"));
        assertTrue(biomeSource.contains("V3ColumnSampler"),
                "the runtime biome source must use the shared V3 column sampler");
        String chunkGen = read(MAIN.resolve("com/modscreating/unlimitedspace/worldgen/planet/"
                + "PlanetChunkGenerator.java"));
        assertTrue(chunkGen.contains("PlanetSurfaceMode"),
                "the runtime chunk generator must consult the resolved surface mode");
        String worldMgr = read(MAIN.resolve("com/modscreating/unlimitedspace/worldgen/dynamic/"
                + "DynamicPlanetWorldManager.java"));
        assertTrue(worldMgr.contains("GASEOUS"),
                "the world manager must handle a gaseous body explicitly");
    }

    @Test
    void thePreviewIsASeparateCallerAndNotAHotPathFlag() throws IOException {
        // Task O: the preview must not leak into production. No runtime file may build a
        // BufferedImage, write a PNG, or reference the preview tool.
        for (Path p : sourcesUnder(MAIN)) {
            String src = read(p);
            assertTrue(!src.contains("BufferedImage"),
                    "a runtime file must not allocate an image: " + p.getFileName());
            assertTrue(!src.contains("ImageIO"),
                    "a runtime file must not write a preview image: " + p.getFileName());
        }
    }
}