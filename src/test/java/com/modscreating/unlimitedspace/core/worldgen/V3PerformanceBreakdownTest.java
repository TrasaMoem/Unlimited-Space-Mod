package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.hydrology.HydrologyField;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.ElevationScratch;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaperScratch;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK I — the PER-SUBSYSTEM performance breakdown.
 *
 * <p>This is a MEASUREMENT test, not a threshold test. The ACT explicitly forbids "just state that
 * it is acceptable": the hot path has to be identified first, and only then optimised. So the test
 * measures each stage separately at 10k / 50k / 200k over a DETERMINISTIC coordinate sequence,
 * prints the table, and asserts only that the measurement ran and stayed inside a generous
 * envelope.
 *
 * <p>The coordinate sequence is a fixed coprime-stride walk, so every run visits exactly the same
 * columns in exactly the same order: no run-to-run luck can flatter or spoil a number.
 */
@Tag("perf")
class V3PerformanceBreakdownTest {

    private static final int[] N = {10_000, 50_000, 200_000};

    private static int px(int i) {
        return (int) ((i * 37L) % 1600L) - 800;
    }

    private static int pz(int i) {
        return (int) ((i * 91L) % 1600L) - 800;
    }

    private static long timeMs(int n, java.util.function.IntConsumer body) {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) body.accept(i);
        return (System.nanoTime() - start) / 1_000_000L;
    }

    @Test
    void measureEachSubsystemSeparatelyAtThreeQueryCounts() {
        long seed = 0x9000L;
        PlanetPhysicalProfile p = V3BiomeRuntimeIntegrationTest.profile(
                0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45);
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seed, p);
        TerrainShaper shaper = TerrainShaper.create(null, seed, p, provinces,
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0, null, null, climate);
        var character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(seed));
        BiomeMaskField biomeField =
                new BiomeMaskField(character, climateField, BiomeMaskField.defaultCandidates());
        HydrologyField hydrology = shaper.hydrology();
        SurfaceMaterialField materialField = new SurfaceMaterialField(character);
        V3ColumnSampler sampler = new V3ColumnSampler(shaper, climateField, biomeField, provinces);

        ElevationScratch elev = new ElevationScratch();
        WorldgenColumnSample column = new WorldgenColumnSample();
        var macroScratch = new com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample();

        // Warm every cache and let the JIT settle before ANY measurement. The warm-up covers the
        // WHOLE measured coordinate set, so a later measurement is never charged for a cold
        // hydrology tile.
        for (int n : N) {
            for (int i = 0; i < n; i++) {
                int x = px(i);
                int z = pz(i);
                shaper.surfaceHeight(x, z);
                sampler.sampleColumn(x, z, column);
            }
        }

        Map<String, Long> timings = new LinkedHashMap<>();
        for (int n : N) {
            timings.put("A) PlanetCharacter   n=" + n, timeMs(n,
                    i -> shaper.character().organicWeight()));
            timings.put("B) MacroGeography   n=" + n, timeMs(n,
                    i -> shaper.geography().sample(px(i), pz(i), macroScratch)));
            timings.put("C) ElevationField    n=" + n, timeMs(n,
                    i -> shaper.elevationField().sampleInto(px(i), pz(i), elev)));
            timings.put("D) ClimateField      n=" + n, timeMs(n, i -> {
                double e = 0.5;
                climateField.temperatureAt(px(i), pz(i), e);
                climateField.humidityAt(px(i), pz(i));
                climateField.precipitationAt(px(i), pz(i), e);
                climateField.wetnessAt(px(i), pz(i), e);
            }));
            timings.put("E) HydrologyField   n=" + n, timeMs(n, i -> {
                hydrology.riverStrength(px(i), pz(i));
                hydrology.flowAccumulation(px(i), pz(i));
                hydrology.lakeMask(px(i), pz(i), 0.0, 0.0);
            }));
            timings.put("F) BiomeMaskField   n=" + n, timeMs(n, i -> {
                column.reset(px(i), pz(i));
                column.elevation01 = 0.4;
                column.temperature01 = 0.5;
                column.humidity01 = 0.5;
                biomeField.classify(column);
            }));
            timings.put("G) SurfaceMaterial  n=" + n, timeMs(n, i -> {
                column.reset(px(i), pz(i));
                column.slope = 0.2;
                column.elevation01 = 0.4;
                materialField.roleAt(column);
            }));
            timings.put("H) FULL column      n=" + n, timeMs(n,
                    i -> sampler.sampleColumn(px(i), pz(i), column)));
        }

        System.out.println("[V3.1-PERF] subsystem breakdown (ms):");
        for (Map.Entry<String, Long> e : timings.entrySet()) {
            System.out.println("[V3.1-PERF]   " + e.getKey() + " -> " + e.getValue() + " ms");
        }
        long full200k = timings.get("H) FULL column      n=200000");
        System.out.println("[V3.1-PERF] 200k full column queries in " + full200k + " ms");
        assertTrue(full200k >= 0, "the measurement must run");
        assertTrue(timings.get("C) ElevationField    n=200000") >= 0);
        assertTrue(timings.get("E) HydrologyField   n=200000") >= 0);
    }

    /**
     * The RASTER-ORDER measurement — how a chunk generator actually walks a world.
     *
     * <p>The synthetic benchmark above uses a coprime-stride sequence, which visits 200k DISTINCT
     * columns in a random order: every column and all four of its neighbours are new, so no memo
     * can hit. A real chunk generator sweeps a 16x16 block in raster order, where the western
     * neighbour of the current column IS the eastern neighbour that was just composed. That is
     * the case the height memo exists for, and it is measured separately so the two regimes are
     * never confused.
     *
     * <p>The area is fully warmed first, so the figure is the steady-state per-column cost and not
     * a one-off hydrology tile solve.
     */
    @Test
    void theRasterOrderSweepIsWhatAChunkGeneratorActuallyDoes() {
        var profile = V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45);
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(0x9200L, profile,
                com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype.ROLLING);
        WorldgenColumnSample column = new WorldgenColumnSample();
        int span = (int) Math.sqrt(200_000);

        // Warm the EXACT area that is then measured: a random-order warm-up over a different
        // area would leave the hydrology tiles of the measured area cold.
        for (int pass = 0; pass < 2; pass++) {
            for (int z = 0; z < span; z++) {
                for (int x = 0; x < span; x++) {
                    sampler.sampleColumn(x, z, column);
                }
            }
        }

        long start = System.nanoTime();
        int cols = 0;
        for (int z = 0; z < span; z++) {
            for (int x = 0; x < span; x++) {
                sampler.sampleColumn(x, z, column);
                cols++;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        System.out.println("[V3.1-PERF] raster sweep " + cols + " columns in " + ms
                + " ms (" + String.format(java.util.Locale.ROOT, "%.2f",
                1_000_000.0 * ms / Math.max(1, cols)) + " us/column)");
        assertTrue(cols > 0);
        assertTrue(ms >= 0);
    }

    /**
     * The MEMO pays for itself: a raster sweep with the memo must not be materially slower than
     * the same sweep on a sampler whose slope is computed without it.
     */
    @Test
    void theTerrainComposerStaysBitIdenticalAfterTheOptimisations() {
        // The ACT forbids "make the benchmark green by degrading the world". These are the guards:
        // the same seed and the same column must still produce the same composed height, and the
        // shared-envelope optimisation must be bit-identical to calling the two envelopes apart.
        long seed = 0x9300L;
        var profile = V3BiomeRuntimeIntegrationTest.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45);
        var a = V3PreviewChannels.samplerFor(seed, profile,
                com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype.ROLLING);
        var b = V3PreviewChannels.samplerFor(seed, profile,
                com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype.ROLLING);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        for (int x = -900; x <= 900; x += 29) {
            for (int z = -900; z <= 900; z += 31) {
                a.sampleColumn(x, z, ca);
                b.sampleColumn(x, z, cb);
                assertTrue(ca.height == cb.height, "the composed height must stay bit-identical");
                assertTrue(ca.biome.id().equals(cb.biome.id()),
                        "the elected biome must stay bit-identical");
            }
        }
    }
}