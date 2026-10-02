package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.7 STAGE 15 - the material mapping must keep the hot-path contract.
 *
 * <p>The variant layer runs once per column in the chunk generator, so it has to satisfy the same
 * two properties the rest of the V3 stack does: <b>zero allocation</b> per query and an <b>O(1)</b>
 * cost. This measures both, and it measures them the way {@code V3ZeroAllocationTest} does - after
 * warm-up, across a full GC, on a repeated sequence - so a one-time cost cannot be mistaken for a
 * per-query cost.
 *
 * <p>It is a MEASUREMENT test with a deliberately generous envelope. The claim is structural (no
 * allocation, no random, no string, no collection in the loop), and the printed numbers make a
 * regression visible rather than merely failing later.
 */
@Tag("perf")
class MaterialVariantPerformanceTest {

    private static final long SEED = 0x1d7e37L;

    private static PlanetPhysicalProfile iceShell() {
        return new PlanetPhysicalProfile(
                0.10, null, 0.50, 0.6, null, 0.50, 0.25, 0.5, 0.20, 0.10, 0.15,
                0.30, 0.2, 0.4, 0.3, 0.15, 0.35, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_ICE);
    }

    private static int px(int i) {
        return (int) ((i * 37L) % 1600L) - 800;
    }

    private static int pz(int i) {
        return (int) ((i * 91L) % 1600L) - 800;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    @Test
    void theMaterialVariantMappingAllocatesNothingPerColumn() {
        PlanetPhysicalProfile p = iceShell();
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(SEED, p,
                GeologicalProvinceMap.create(SEED, p),
                PlanetColorTheme.select(TemperatureBand.of(p.temperature()),
                        ClimateArchetypeSelector.create(SEED, p), SEED));
        MaterialVariantField field = MaterialVariantField.forRole(p, MaterialRole.PRIMARY_SURFACE,
                SEED, palette.materialFor(MaterialRole.PRIMARY_SURFACE));
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(SEED, p, ReliefArchetype.GLACIAL);
        WorldgenColumnSample col = new WorldgenColumnSample();

        // ---- warm-up: caches, JIT and the variant table all reach steady state ----
        long sink = 0;
        for (int i = 0; i < 100_000; i++) {
            int x = px(i);
            int z = pz(i);
            sampler.sampleColumn(x, z, col);
            sink += field.index(col, x, z);
        }
        int n = 300_000;
        for (int i = 0; i < n; i++) {
            int x = px(i);
            int z = pz(i);
            sampler.sampleColumn(x, z, col);
            sink += field.index(col, x, z);
        }
        long before = usedHeap();
        for (int i = 0; i < n; i++) {
            int x = px(i);
            int z = pz(i);
            sampler.sampleColumn(x, z, col);
            sink += field.index(col, x, z);
        }
        long growth = usedHeap() - before;
        System.out.println("[V3.7-15] variant mapping: " + growth + " bytes over " + n
                + " columns (checksum " + sink + ")");
        // A real per-column object would be >= 16 bytes; the noise floor of the harness is far
        // below that, so a 64 KB total over 300k columns can only be measurement noise.
        assertTrue(growth < 64L * 1024L,
                "the material variant mapping must not allocate per column: " + growth
                        + " bytes over " + n + " columns");
    }

    @Test
    void theMaterialVariantMappingIsCheapRelativeToTheColumnItReads() {
        PlanetPhysicalProfile p = iceShell();
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(SEED, p,
                GeologicalProvinceMap.create(SEED, p),
                PlanetColorTheme.select(TemperatureBand.of(p.temperature()),
                        ClimateArchetypeSelector.create(SEED, p), SEED));
        MaterialVariantField field = MaterialVariantField.forRole(p, MaterialRole.PRIMARY_SURFACE,
                SEED, palette.materialFor(MaterialRole.PRIMARY_SURFACE));
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(SEED, p, ReliefArchetype.GLACIAL);
        WorldgenColumnSample col = new WorldgenColumnSample();

        for (int i = 0; i < 100_000; i++) {
            sampler.sampleColumn(px(i), pz(i), col);
            field.index(col, px(i), pz(i));
        }
        int n = 400_000;
        long tColumn = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int x = px(i);
            int z = pz(i);
            sampler.sampleColumn(x, z, col);
        }
        long columnMs = (System.nanoTime() - tColumn) / 1_000_000L;
        long tVariant = System.nanoTime();
        for (int i = 0; i < n; i++) {
            field.index(col, px(i), pz(i));
        }
        long variantMs = (System.nanoTime() - tVariant) / 1_000_000L;
        System.out.println("[V3.7-15] n=" + n + " fullColumn=" + columnMs + "ms variantOnly="
                + variantMs + "ms");
        // The mapping must stay a small fraction of the column it reads, otherwise the ACT traded a
        // visual defect for a performance regression.
        assertTrue(variantMs * 4 <= Math.max(1L, columnMs),
                "the variant mapping is too expensive relative to the column: variant=" + variantMs
                        + "ms column=" + columnMs + "ms over " + n + " iterations");
    }
}
