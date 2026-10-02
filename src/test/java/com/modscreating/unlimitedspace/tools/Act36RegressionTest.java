package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.hydrology.HydrologyField;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.6 REGRESSION SUITE - P0 far generation and P1 ice materials.
 *
 * <p>Every assertion is pinned to a MEASURED fact from {@link Act36MeasurementHarness}, not to an
 * invented percentage. Each test names the failure it prevents.
 */
@Tag("worldgen")
@Tag("audit")
class Act36RegressionTest {

    private static PlanetPhysicalProfile ice() {
        return Act36MeasurementHarness.profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15,
                PlanetSurface.SOLID_ICE);
    }

    private static PlanetPhysicalProfile temperate() {
        return Act36MeasurementHarness.profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45, 0.15,
                PlanetSurface.SOLID_ROCKY);
    }

    // ------------------------------------------------------------------ P0

    /**
     * A. far coordinates generate; B. negative coordinates generate; E. determinism.
     *
     * <p>Prevents: any far-coordinate overflow, precision loss or hash collapse from turning into
     * an exception or into a different world.
     */
    @Test
    void farAndNegativeCoordinatesGenerateDeterministically() {
        int[][] far = {
                {0, 0}, {1_000_000, 1_000_000}, {-1_000_000, 1_000_000},
                {1_000_000, -1_000_000}, {-1_000_000, -1_000_000},
                {30_000_000, 30_000_000}, {-30_000_000, -30_000_000},
        };
        V3ColumnSampler a = V3PreviewChannels.samplerFor(0x5EED1L, ice(), ReliefArchetype.GLACIAL);
        V3ColumnSampler b = V3PreviewChannels.samplerFor(0x5EED1L, ice(), ReliefArchetype.GLACIAL);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        for (int[] c : far) {
            for (int d = 0; d < 48; d += 12) {
                int x = c[0] + d;
                int z = c[1] - d;
                a.sampleColumn(x, z, ca);
                b.sampleColumn(x, z, cb);
                assertEquals(ca.height, cb.height, "height must be deterministic at " + x + "," + z);
                assertEquals(ca.materialRole, cb.materialRole, "role at " + x + "," + z);
                assertEquals(ca.riverMask, cb.riverMask, "river mask at " + x + "," + z);
                assertTrue(ca.elevation01 >= 0.0 && ca.elevation01 <= 1.0,
                        "a far column must still produce a valid sample at " + x + "," + z);
            }
        }
    }

    /**
     * A far column must be a REAL sample, not a constant fallback.
     *
     * <p>Prevents "far away everything reads the same", the shape of a silent generation stall that
     * still returns valid-looking numbers.
     */
    @Test
    void farTerrainIsNotCollapsedToAConstant() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0x5EED1L, ice(), ReliefArchetype.GLACIAL);
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<Integer, Boolean> seen = new TreeMap<>();
        for (int x = 1_000_000; x < 1_000_256; x += 4) {
            s.sampleColumn(x, 1_000_000, col);
            seen.put(col.height, Boolean.TRUE);
        }
        assertTrue(seen.size() > 3,
                "far terrain must vary, got " + seen.size() + " distinct heights over 256 blocks");
    }


    /**
     * C/D. the hydrology tile cache must stay BOUNDED, so flying further can never exhaust memory.
     *
     * <p>Measured before the fix: 4 097 cached tiles (~818 MB) after a straight walk to x = 1 048 576.
     */
    @Test
    void theHydrologyTileCacheIsBounded() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xBEEFL, temperate(),
                ReliefArchetype.ROLLING);
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = 0; x < 131_072; x += 8) {
            s.sampleColumn(x, 0, col);
        }
        int tiles = Act36MeasurementHarness.hydrologyTiles(s);
        assertTrue(tiles > 0, "the measurement must actually observe the cache");
        assertTrue(tiles <= HydrologyField.MAX_CACHED_TILES,
                "the tile cache must stay bounded, got " + tiles + " tiles after a long flight");
    }

    /**
     * Eviction must not change the world: a value read before and after the cache was pressured
     * must be identical, because a tile is a pure function of the planet seed and the tile index.
     */
    @Test
    void evictingATileDoesNotChangeTheWorld() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0xC0FFEEL, temperate(),
                ReliefArchetype.ROLLING);
        WorldgenColumnSample probe = new WorldgenColumnSample();
        s.sampleColumn(64, 64, probe);
        double riverBefore = probe.riverMask;
        double lakeBefore = probe.lakeMask;
        double flowBefore = probe.flowAccumulation;
        // Fly far enough to force many evictions, then come back and re-read the same column.
        for (int x = 0; x < 200_000; x += 8) {
            s.sampleColumn(x, 0, probe);
        }
        s.sampleColumn(64, 64, probe);
        assertEquals(riverBefore, probe.riverMask, "river mask must survive cache eviction");
        assertEquals(lakeBefore, probe.lakeMask, "lake mask must survive cache eviction");
        assertEquals(flowBefore, probe.flowAccumulation, "drainage must survive cache eviction");
    }

    /**
     * The regression that defines this ACT: the per-term relief decomposition must REACH the column.
     *
     * <p>Measured before the fix: {@code glacialRelief} p10/p50/p90 = 0.0 / 0.0 / 0.0 across 50 000
     * ICE columns on 5 planets, because {@code TerrainShaper.sampleInto} read only
     * {@code elevationScratch.delta()} and discarded the terms it had just computed.
     */
    @Test
    void theReliefDecompositionReachesTheColumn() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0x1CE000L, ice(), ReliefArchetype.GLACIAL);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int nonZero = 0;
        int n = 0;
        for (int x = -4000; x < 4000; x += 37) {
            for (int z = -4000; z < 4000; z += 41) {
                s.sampleColumn(x, z, col);
                n++;
                if (Math.abs(col.glacialRelief) > 0.0) nonZero++;
            }
        }
        assertTrue(n > 20_000, "the probe must be substantial, got " + n);
        assertTrue(nonZero > n / 4,
                "glacial relief must be a real per-column signal, it was non-zero on only "
                        + nonZero + " of " + n + " columns");
    }

    /**
     * H. an ICE world must not collapse to a single surface material.
     *
     * <p>Measured before the fix: 99.5% PRIMARY_SURFACE and 0.3% rock over 50 000 columns.
     */
    @Test
    void anIceWorldIsNotOneSolidMaterial() {
        Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
        Map<String, Integer> blocks = new TreeMap<>();
        int planetsChecked = 0;
        for (PlanetPhysicalProfile p : Act36MeasurementHarness.icePlanets()) {
            long seed = 0x1CE000L + planetsChecked * 0x9E37L;
            V3ColumnSampler s = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
            WorldgenColumnSample col = new WorldgenColumnSample();
            for (int i = 0; i < 4000; i++) {
                int x = (int) ((i * 397L) % 8000L) - 4000;
                int z = (int) ((i * 641L) % 8000L) - 4000;
                s.sampleColumn(x, z, col);
                MaterialRole role = col.materialRole == null
                        ? MaterialRole.PRIMARY_SURFACE : col.materialRole;
                roles.merge(role, 1, Integer::sum);
                PlanetMaterial m = MaterialCatalog.select(p, role, seed);
                blocks.merge(m == null ? "NONE" : m.blockId(), 1, Integer::sum);
            }
            planetsChecked++;
        }
        int total = roles.values().stream().mapToInt(Integer::intValue).sum();
        int frozen = roles.getOrDefault(MaterialRole.PRIMARY_SURFACE, 0);
        int rock = roles.getOrDefault(MaterialRole.MOUNTAIN, 0);
        // The world is still a cold world: the frozen majority must dominate.
        assertTrue((double) frozen / total > 0.5,
                "an ice shell must stay predominantly frozen, got " + ((double) frozen / total));
        // But it must not be a single material: the substrate has to show somewhere.
        assertTrue((double) rock / total > 0.005,
                "exposed rock must appear on a real share of an ice world, got "
                        + ((double) rock / total) + " over " + total + " columns");
        assertTrue(blocks.size() >= 3,
                "an ice world must place several distinct materials, got " + blocks.keySet());
    }

    /**
     * J. exposed rock must track the CONTINUOUS exposure signal, not a planet-type switch.
     */
    @Test
    void exposedRockTracksTheExposureSignal() {
        SurfaceMaterialField field = new SurfaceMaterialField(null);
        WorldgenColumnSample c = new WorldgenColumnSample();
        c.reset(0, 0);
        // A gentle, cold, snow-supplying plain: no exposure, so the frozen primary surface wins.
        c.slope = 0.0;
        c.elevation01 = 0.20;
        c.glacialRelief = 0.0;
        c.volcanicRelief = 0.0;
        c.temperature01 = 0.10;
        c.precipitation01 = 0.60;
        c.glacialIntensity = 0.5;
        assertEquals(MaterialRole.PRIMARY_SURFACE, field.roleAt(c),
                "gentle cold ground must read as snow, not rock");
        // The SAME column, but inside a deep glacial trough wall: now the substrate shows.
        c.glacialRelief = -18.0;
        assertEquals(MaterialRole.MOUNTAIN, field.roleAt(c),
                "a glacial trough wall must expose rock");
        assertNotEquals(field.roleAt(c), MaterialRole.PRIMARY_SURFACE);
    }

    /**
     * M. the material role must be spatially varying inside a single ICE planet.
     */
    @Test
    void materialRoleVariesSpatiallyWithinOneIcePlanet() {
        V3ColumnSampler s = V3PreviewChannels.samplerFor(0x1CE000L, ice(), ReliefArchetype.GLACIAL);
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
        for (int x = -4000; x < 4000; x += 23) {
            for (int z = -4000; z < 4000; z += 29) {
                s.sampleColumn(x, z, col);
                roles.merge(col.materialRole == null
                        ? MaterialRole.PRIMARY_SURFACE : col.materialRole, 1, Integer::sum);
            }
        }
        assertTrue(roles.size() >= 2,
                "an ice planet must resolve more than one dominant role, got " + roles);
    }
}
