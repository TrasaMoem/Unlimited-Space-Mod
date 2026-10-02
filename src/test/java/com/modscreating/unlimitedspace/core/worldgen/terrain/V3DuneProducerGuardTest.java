package com.modscreating.unlimitedspace.core.worldgen.terrain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 1.1: {@link DuneMorphologyField} is the SOLE producer of dune relief.
 *
 * <p>The defect this guards: {@code TerrainShaper.sampleInto} used to add a SECOND dune field
 * ({@code TerrainFields.duneSeaField(fieldSeed + 0x4C, ...)}) directly to the height, on top of the
 * V3 {@code ElevationField} which already runs {@code DuneMorphologyField}. One erg was therefore
 * rendered as two superimposed dune fields, doubling both the amplitude and the block-scale slope.
 *
 * <p>This is a source-shape architecture guard: it pins the single-producer contract so the double
 * producer cannot silently return through a refactor. The {@code TerrainFields.duneSeaField} API
 * itself is intentionally KEPT (parity with the existing architecture guards and is still used as a
 * cheap DUNE classifier), so the guard checks only that it is no longer added to the composed
 * height.
 */
class V3DuneProducerGuardTest {

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void terrainShaperDoesNotAddTheeLegacyDuneFieldToHeight() throws IOException {
        String shaper = read("src/main/java/com/modscreating/unlimitedspace/core/worldgen/terrain/TerrainShaper.java");
        assertFalse(shaper.contains("+= TerrainFields.duneSeaField"),
                "TerrainShaper must NOT add the legacy duneSeaField to the composed height - "
                        + "DuneMorphologyField is the only dune-relief producer");
        assertFalse(shaper.contains("duneStrength * 0.05"),
                "the removed legacy dune strength term must not be re-introduced");
    }

    @Test
    void elevationFieldIsTheRealDuneProducer() throws IOException {
        String elevation = read("src/main/java/com/modscreating/unlimitedspace/core/worldgen/terrain/ElevationField.java");
        assertTrue(elevation.contains("duneMorphology.sample("),
                "ElevationField LEVEL 2 must run DuneMorphologyField - it is the sole dune producer");
    }

    @Test
    void theLegacyDuneApiIsKeptNotDeleted() throws IOException {
        // The ACT explicitly says not to delete the TerrainFields API without need.
        String fields = read("src/main/java/com/modscreating/unlimitedspace/core/worldgen/terrain/TerrainFields.java");
        assertTrue(fields.contains("duneSeaField("),
                "TerrainFields.duneSeaField must stay available (it is still a DUNE classifier)");
    }

    @Test
    void waveLengthsAreUnchanged() {
        // MACRO / MESO / MICRO wavelengths must not have been retuned by the fix.
        assertTrue(DuneMorphologyField.MACRO_WAVELENGTH == 1500.0);
        assertTrue(DuneMorphologyField.MESO_WAVELENGTH == 320.0);
        assertTrue(DuneMorphologyField.RIPPLE_WAVELENGTH == 130.0);
    }
}