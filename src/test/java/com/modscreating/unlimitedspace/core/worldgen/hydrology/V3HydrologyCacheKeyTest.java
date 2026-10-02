package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.1 / TASK M — the hydrology cache key must never collide across worlds.
 *
 * <p>The drain tile cache is keyed by tile X/Z, so two INDEPENDENT hydrology fields have to be
 * independent objects; if one field could ever be handed another's tile, two planets would
 * silently share a drainage solution. These tests pin that contract from three sides: the
 * behaviour (two worlds genuinely diverge), the key composition (resolution / halo / step), and
 * the immutability of a solved tile against the scratch that produced it.
 */
@Tag("worldgen")
class V3HydrologyCacheKeyTest {

    private static PlanetPhysicalProfile profile() {
        return new PlanetPhysicalProfile(
                0.45, null, 0.65, 0.6, null, 0.60, 0.30, 0.5, 0.55, 0.10, 0.30,
                0.45, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void twoDifferentWorldsNeverShareADrainageSolution() {
        var a = V3PreviewChannels.samplerFor(0xC0FFEE, profile(), ReliefArchetype.ROLLING);
        var b = V3PreviewChannels.samplerFor(0xBEEF, profile(), ReliefArchetype.ROLLING);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        int differences = 0;
        int compared = 0;
        for (int x = -600; x <= 600; x += 37) {
            for (int z = -600; z <= 600; z += 41) {
                a.sampleColumn(x, z, ca);
                b.sampleColumn(x, z, cb);
                // A single cell at the head of a hill carries the same unit discharge on any
                // world, so the contract is about the SOLVED FIELD, not about one cell.
                if (ca.flowAccumulation != cb.flowAccumulation
                        || ca.riverMask != cb.riverMask
                        || ca.lakeMask != cb.lakeMask) {
                    differences++;
                }
                compared++;
            }
        }
        assertTrue(compared > 400, "the comparison must be substantial");
        assertTrue(differences > compared / 4,
                "two independent worlds must not share a drainage solution: only "
                        + differences + " of " + compared + " columns differed");
    }

    @Test
    void theTileGeometryIsTheOneTheContractFixes() {
        // G_HYDRO = 512 and H_HALO = 256 are part of the V3 contract, and the solved window must
        // be exactly G_HYDRO + 2 * H_HALO on a side. Changing any of them would silently
        // invalidate every cached tile, so the numbers are pinned here.
        assertEquals(512, HydrologyTile.G_HYDRO);
        assertEquals(256, HydrologyTile.H_HALO);
        assertEquals(1024, HydrologyTile.TOTAL_SIZE);
        assertEquals(HydrologyTile.TOTAL_SIZE / HydrologyTile.GRID_STEP + 1, HydrologyTile.GRID);
    }

    @Test
    void hydrologyConsumesTheRealV3ElevationAndNotASyntheticPlaceholder() {
        // The drain solver must see the REAL composed terrain. A river network that ignores the
        // relief is the single most visible worldgen failure, so this asserts a real, resolvable
        // river share on a wet world.
        var sampler = V3PreviewChannels.samplerFor(0xF001, profile(), ReliefArchetype.ROLLING);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int riverColumns = 0;
        int strongChannels = 0;
        int total = 0;
        for (int x = -900; x <= 900; x += 12) {
            for (int z = -900; z <= 900; z += 12) {
                sampler.sampleColumn(x, z, col);
                total++;
                if (col.riverMask > 0.0) riverColumns++;
                if (col.riverMask > 0.2) strongChannels++;
            }
        }
        assertTrue(riverColumns > 0, "a wet world must produce real rivers");
        assertTrue((double) riverColumns / total > 0.0005,
                "a wet world must have a real river share, got "
                        + ((double) riverColumns / total));
        assertTrue(strongChannels > 0,
                "the river mask must be resolvable on the real composed surface");
    }

    @Test
    void aSolvedTileIsImmutableSoAReusedScratchCannotPoisonIt() {
        // DrainageSolver writes into a reusable scratch and HydrologyTile COPIES the result out.
        // If the copy were missing, a later solve would silently overwrite an already-cached
        // tile, which is exactly the "cache a degraded context permanently" failure mode.
        HydrologyScratch scratch = new HydrologyScratch(HydrologyTile.GRID);
        int size = HydrologyTile.GRID;
        for (int i = 0; i < size * size; i++) {
            scratch.elevation[i] = 100.0f + (i % 97);
        }
        DrainageSolver.solve(scratch, size, 0.6, HydrologyField.RIVER_ACCUM_THRESHOLD);
        HydrologyTile tile = new HydrologyTile(0, 0, scratch);
        float first = tile.flowAccumulationAt(0, 0);
        for (int i = 0; i < size * size; i++) {
            scratch.elevation[i] = -50.0f - (i % 31);
        }
        DrainageSolver.solve(scratch, size, 0.6, HydrologyField.RIVER_ACCUM_THRESHOLD);
        assertEquals(first, tile.flowAccumulationAt(0, 0), 0.0f,
                "a cached tile must be immune to a later solve that reuses the scratch");
    }
}