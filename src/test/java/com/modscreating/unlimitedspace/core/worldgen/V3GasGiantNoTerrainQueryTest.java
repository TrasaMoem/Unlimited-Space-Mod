package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK G + R — a gas giant must not EXECUTE the normal generation paths.
 *
 * <p>The ACT requires the counters to be literally zero, not merely small. This test proves two
 * complementary things:
 *
 * <ol>
 *   <li><b>Behaviour.</b> A gas giant resolves to a mode with no solid surface, and the V3 preview
 *       pass for that mode reports {@code terrainQueryCount == hydrologyQueryCount ==
 *       lavaQueryCount == 0} — the gas-giant branch is taken before any field is touched.</li>
 *   <li><b>Structure.</b> The production sources really do short-circuit: the chunk generator
 *       returns from {@code getBaseHeight}, {@code getBaseColumn} and {@code fillFromNoise} on the
 *       gas-giant branch <em>before</em> any shaper / hydrology / material / feature call, and no
 *       path generates terrain and then deletes it.</li>
 * </ol>
 */
@Tag("worldgen")
class V3GasGiantNoTerrainQueryTest {

    private static final Path CHUNK_GEN = Path.of("src", "main", "java", "com", "modscreating",
            "unlimitedspace", "worldgen", "planet", "PlanetChunkGenerator.java");
    private static final Path WORLD_MGR = Path.of("src", "main", "java", "com", "modscreating",
            "unlimitedspace", "worldgen", "dynamic", "DynamicPlanetWorldManager.java");
    private static final Path BIOME_SRC = Path.of("src", "main", "java", "com", "modscreating",
            "unlimitedspace", "worldgen", "planet", "PlanetBiomeSource.java");

    @Test
    void sixteenGasGiantSeedsExecuteNoTerrainHydrologyOrLavaQuery() throws IOException {
        int checked = 0;
        for (int i = 0; i < 16; i++) {
            long seed = 0xF10000L + i * 0x85EBCA6BL;
            var physical = V3BiomeRuntimeIntegrationTest.profile(
                    0.45 + 0.02 * (i % 9), 0.45, 0.30, 0.50, 0.30, 0.30, 0.40);
            PlanetSurfaceMode mode = V3PreviewChannels.surfaceMode(physical, true);
            assertFalse(mode.hasSolidSurface(), "seed " + i + " must have no solid surface");

            // The preview pass is the same pipeline the runtime uses. A gas giant produces no
            // columns at all, so every counter is exactly zero.
            Path dir = Path.of("build", "v31-gasgiant", "seed_" + i);
            V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, physical,
                    ReliefArchetype.ROLLING);
            var facts = V3PreviewChannels.render("gasgiant_" + i, sampler, mode,
                    dir.toFile(), 24, 128);
            assertEquals(0, facts.terrainQueryCount(),
                    "a gas giant must execute zero terrain queries (seed " + i + ")");
            assertEquals(0, facts.hydrologyQueryCount(),
                    "a gas giant must execute zero hydrology queries (seed " + i + ")");
            assertEquals(0, facts.lavaQueryCount(),
                    "a gas giant must execute zero lava queries (seed " + i + ")");
            assertFalse(facts.hasSolidSurface());
            String text = String.join("\n", facts.render());
            assertTrue(text.contains("hasSolidSurface           : false"),
                    "the facts must state plainly that there is no solid surface");
            checked++;
        }
        assertEquals(16, checked);
    }

    @Test
    void theChunkGeneratorShortCircuitsBeforeAnyTerrainPath() throws IOException {
        String src = stripComments(Files.readString(CHUNK_GEN, StandardCharsets.UTF_8));
        // The gas giant must be rejected at the very top of each generation entry point, BEFORE
        // the height is measured, BEFORE a NoiseColumn is filled and BEFORE fillFromNoise runs.
        int baseHeight = src.indexOf("public int getBaseHeight");
        int baseColumn = src.indexOf("public NoiseColumn getBaseColumn");
        int fill = src.indexOf("public CompletableFuture<ChunkAccess> fillFromNoise");
        int gateHeight = src.indexOf("isGasGiant()", baseHeight);
        int gateColumn = src.indexOf("isGasGiant()", baseColumn);
        int gateFill = src.indexOf("isGasGiant()", fill);
        assertTrue(gateHeight > baseHeight && gateHeight < src.indexOf("surfaceHeight(", baseHeight),
                "getBaseHeight must reject a gas giant BEFORE measuring any surface height");
        assertTrue(gateColumn > baseColumn && gateColumn < src.indexOf("Arrays.fill", baseColumn),
                "getBaseColumn must reject a gas giant BEFORE building a solid column");
        assertTrue(gateFill > fill && gateFill < src.indexOf("Heightmap worldSurface", fill),
                "fillFromNoise must reject a gas giant BEFORE any terrain loop");
        // And it must NOT be faked: no "generate then clear" pattern may exist.
        assertFalse(src.contains("fillFromNoise") && src.contains("Blocks.AIR.defaultBlockState());\n"
                        + "            for (int y"), "no generate-then-delete path is allowed");
    }

    @Test
    void theWorldManagerDoesNotMeasureASurfaceForAGasGiant() throws IOException {
        String src = stripComments(Files.readString(WORLD_MGR, StandardCharsets.UTF_8));
        assertTrue(src.contains("GASEOUS"),
                "the world manager must recognise a gaseous body explicitly");
        int gas = src.indexOf("GASEOUS");
        int surface = src.indexOf("registerSurface(rl, level, PLANET_SURFACE_ARRIVAL");
        assertTrue(gas > 0 && gas < surface,
                "the gas-giant branch must come BEFORE the surface arrival registration, so a "
                        + "gas giant never takes the terrain-dependent surface path");
    }

    @Test
    void theBiomeSourceClassifiesPerColumnThroughTheSharedPipeline() throws IOException {
        // The biome source must route the lookup through the ONE shared column sampler, so the
        // biome a column reports and the terrain that column gets can never diverge.
        String src = stripComments(Files.readString(BIOME_SRC, StandardCharsets.UTF_8));
        assertTrue(src.contains("columnSampler.sampleColumn(x, z, column)"),
                "the biome source must classify per column through the shared V3 sampler");
        assertTrue(src.contains("column.biome"),
                "the biome source must read the elected biome off the sampled column");
        assertTrue(!src.contains("biomes.get(0)"),
                "the biome source must never fall back to the first biome of the pool");
    }

    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}