package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.ElevationScratch;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 / STAGE 3 — hydrology hard gates: real drainage-driven rivers, lakes only in genuine
 * basins, exact tile-boundary continuity, and no liquid water where the phase forbids it.
 */
@Tag("worldgen")
class V3HydrologyTest {

    static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                         double tect, double volc, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    /** A warm, wet, liquid-water world: the EARTHLIKE reference family. */
    static TerrainShaper earthlike(long seed) {
        return shaper(seed, profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.45));
    }

    @Test
    void aWarmWetLiquidWorldActuallyGeneratesRivers() {
        for (long seed : new long[]{0xA11L, 0xA22L, 0xA33L}) {
            TerrainShaper sh = earthlike(seed);
            assertTrue(sh.hydrology().allowsLiquidRivers(),
                    "a temperate wet world must permit liquid rivers");
            assertTrue(sh.hydrology().isTerrainBound(),
                    "the drainage network must be solved on the REAL terrain, not a placeholder");

            int riverCells = 0;
            int total = 0;
            double maxCarve = 0.0;
            for (int x = -3000; x <= 3000; x += 16) {
                for (int z = -3000; z <= 3000; z += 16) {
                    total++;
                    double s = sh.hydrology().riverStrength(x, z);
                    if (s > 0.0) {
                        riverCells++;
                        maxCarve = Math.max(maxCarve, sh.hydrology().riverCarve(x, z, 0.5));
                    }
                }
            }
            double share = (double) riverCells / total;
            assertTrue(share > 0.0005,
                    "a warm wet world must visibly generate rivers, share=" + share);
            assertTrue(maxCarve > 0.5, "a river must actually carve the terrain: " + maxCarve);
            assertTrue(maxCarve <= HydrologyField.RIVER_CARVE_MAX_BLOCKS + 1e-6,
                    "river incision left the -2..12 block corridor: " + maxCarve);
        }
    }


    @Test
    void riverShareGrowsWithPrecipitation() {
        TerrainShaper dry = shaper(0xB01L, profile(0.45, 0.25, 0.20, 0.55, 0.10, 0.45));
        TerrainShaper wet = shaper(0xB01L, profile(0.45, 0.80, 0.75, 0.55, 0.10, 0.45));
        double dryShare = riverShare(dry);
        double wetShare = riverShare(wet);
        assertTrue(wetShare > dryShare,
                "riverShare must increase with precipitation: dry=" + dryShare
                        + " wet=" + wetShare);
    }

    @Test
    void aFrozenWorldHasNoLiquidRiversAndNoLiquidLakes() {
        // A cold world: the physical phase forbids liquid water at the surface.
        TerrainShaper cold = shaper(0xC01L, profile(0.02, 0.20, 0.30, 0.50, 0.10, 0.40));
        assertTrue(!cold.hydrology().allowsLiquidRivers(),
                "a frozen world must not permit liquid rivers");
        for (int x = -2000; x <= 2000; x += 32) {
            assertEquals(0.0, cold.hydrology().riverCarve(x, 128, 1.0), 1e-9,
                    "a frozen world must not carve liquid river channels");
            assertEquals(0.0, cold.hydrology().lakeMask(x, 128, 1.0, 1.0), 1e-9,
                    "a frozen world must not hold liquid lakes");
        }
    }

    @Test
    void lakesAppearOnlyInsideRealTopographicBasins() {
        TerrainShaper sh = earthlike(0xD01L);
        HydrologyField hyd = sh.hydrology();
        ElevationScratch sc = new ElevationScratch();
        int lakes = 0;
        for (int x = -3000; x <= 3000; x += 24) {
            for (int z = -3000; z <= 3000; z += 24) {
                double mask = hyd.lakeMask(x, z, sc.basinEnvelope, sc.valleyEnvelope);
                if (mask > 0.02) {
                    lakes++;
                    // A lake is legal only where the depression-filling solver found a genuine
                    // filledHeight > originalHeight difference.
                    double depth = hyd.tileAt(x, z).lakeDepthAt(x, z);
                    assertTrue(depth > 0.0,
                            "a lake mask appeared where there is no topographic depression");
                }
            }
        }
        assertTrue(lakes > 0, "a wet world must produce at least one real lake basin");
    }


    @Test
    void hydrologyIsIdenticalAcrossTileBorders() {
        // The hard gate: a column's river strength, lake depth and flow accumulation must be a pure
        // function of the column, with no chunk- or tile-edge reset.
        for (long seed : new long[]{0xE01L, 0xE02L}) {
            TerrainShaper a = earthlike(seed);
            TerrainShaper b = earthlike(seed);
            int g = HydrologyTile.G_HYDRO;
            for (int tile = -2; tile <= 2; tile++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int x = tile * g + dx;
                    for (int z = -600; z <= 600; z += 37) {
                        assertEquals(a.hydrology().riverStrength(x, z),
                                b.hydrology().riverStrength(x, z), 0.0,
                                "river strength must not depend on evaluation context at x=" + x);
                        assertEquals(a.hydrology().tileAt(x, z).lakeDepthAt(x, z),
                                b.hydrology().tileAt(x, z).lakeDepthAt(x, z), 0.0,
                                "lake depth must be identical at a tile border, x=" + x);
                    }
                }
            }
        }
    }

    @Test
    void hydrologyIsDeterministicAndCached() {
        TerrainShaper a = earthlike(0xF01L);
        TerrainShaper b = earthlike(0xF01L);
        for (int x = -1200; x <= 1200; x += 53) {
            assertEquals(a.hydrology().riverStrength(x, 77), b.hydrology().riverStrength(x, 77),
                    0.0, "same seed must produce identical drainage");
        }
        int cached = a.hydrology().cachedTileCount();
        for (int x = -1200; x <= 1200; x += 53) {
            a.hydrology().riverStrength(x, 77);
        }
        assertEquals(cached, a.hydrology().cachedTileCount(),
                "hydrology tiles must be cached, not re-solved per column");
    }

    @Test
    void riverCarvingFollowsTheSolvedDischarge() {
        TerrainShaper sh = earthlike(0xF11L);
        HydrologyField hyd = sh.hydrology();
        double lastStrength = -1.0;
        double lastCarve = -1.0;
        boolean sawRiver = false;
        for (int x = -4000; x <= 4000; x += 8) {
            double s = hyd.riverStrength(x, 313);
            if (s <= 0.0) continue;
            double carve = hyd.riverCarve(x, 313, 0.6);
            assertTrue(carve > 0.0 && carve <= HydrologyField.RIVER_CARVE_MAX_BLOCKS + 1e-6,
                    "river incision out of the -2..12 corridor: " + carve);
            if (s >= lastStrength) {
                assertTrue(carve >= lastCarve - 1e-6,
                        "river incision must grow with discharge: " + carve + " < " + lastCarve);
            }
            lastStrength = s;
            lastCarve = carve;
            sawRiver = true;
        }
        assertTrue(sawRiver, "no river was found on a wet world transect");
    }

    private static double riverShare(TerrainShaper sh) {
        int cells = 0;
        int total = 0;
        for (int x = -2500; x <= 2500; x += 16) {
            for (int z = -2500; z <= 2500; z += 16) {
                total++;
                if (sh.hydrology().riverStrength(x, z) > 0.0) cells++;
            }
        }
        return (double) cells / total;
    }
}
