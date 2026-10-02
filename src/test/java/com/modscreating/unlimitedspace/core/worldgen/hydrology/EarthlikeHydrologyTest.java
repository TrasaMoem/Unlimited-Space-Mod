package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 4 / STAGE 4.1 / STAGE 6: an EARTHLIKE (warm, wet) world must actually
 * have reachable hydrology that reaches the blocks - rivers and lake basins - instead of a dry
 * stone plain, and a frozen world must not get liquid rivers.
 *
 * <p>This exercises the REAL drainage pipeline (solved on the composed terrain), not a synthetic
 * mask, so it verifies producer -&gt; publication -&gt; consumer rather than a producer existing.
 */
@Tag("worldgen")
class EarthlikeHydrologyTest {

    /** The same physical profile factory the hydrology suite uses (temperature is NORMALIZED). */
    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    private static TerrainShaper earthlike(long seed) {
        return shaper(seed, profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.45));
    }

    @Test
    void aWarmWetWorldReachesRiversAndLakes() {
        for (long seed : new long[]{0xEA11L, 0xEA22L}) {
            TerrainShaper sh = earthlike(seed);
            assertTrue(sh.hydrology().allowsLiquidRivers(),
                    "a temperate wet world must permit liquid rivers");
            assertTrue(sh.hydrology().isTerrainBound(),
                    "drainage must be solved on the REAL composed terrain");

            int riverCells = 0;
            int lakeCells = 0;
            int total = 0;
            double maxCarve = 0.0;
            for (int x = -1200; x <= 1200; x += 24) {
                for (int z = -1200; z <= 1200; z += 24) {
                    total++;
                    double rs = sh.hydrology().riverStrength(x, z);
                    if (rs > 0.0) {
                        riverCells++;
                        maxCarve = Math.max(maxCarve, sh.hydrology().riverCarve(x, z, 0.5));
                    }
                    if (sh.hydrology().lakeMask(x, z, 0.4, 0.4) > 0.0) {
                        lakeCells++;
                    }
                }
            }
            assertTrue(riverCells > 0,
                    "a warm wet world must produce reachable rivers (seed=" + seed + ")");
            assertTrue(lakeCells > 0,
                    "a warm wet world must produce reachable lake basins (seed=" + seed + ")");
            assertTrue(maxCarve > 0.0 && maxCarve <= HydrologyField.RIVER_CARVE_MAX_BLOCKS + 1e-6,
                    "river incision must reach the blocks inside the -2..12 corridor: " + maxCarve);
            assertTrue(total > 0);
        }
    }

    @Test
    void diagRiverDistribution() {
        for (long seed : new long[]{0xEA11L, 0xEA22L}) {
            TerrainShaper sh = earthlike(seed);
            for (int step : new int[]{160, 64, 16, 8}) {
                int n = 0, any = 0, over5 = 0, over25 = 0, over50 = 0;
                double maxRs = 0, maxAccum = 0, sumAccum = 0;
                int lake = 0;
                double maxLake = 0;
                for (int x = -1600; x <= 1600; x += step) {
                    for (int z = -1600; z <= 1600; z += step) {
                        n++;
                        double rs = sh.hydrology().riverStrength(x, z);
                        maxRs = Math.max(maxRs, rs);
                        if (rs > 0.0) any++;
                        if (rs > 0.05) over5++;
                        if (rs > 0.25) over25++;
                        if (rs > 0.5) over50++;
                        double a = sh.hydrology().flowAccumulation(x, z);
                        maxAccum = Math.max(maxAccum, a);
                        sumAccum += a;
                        double lk = sh.hydrology().lakeMask(x, z, 0.0, 0.0);
                        if (lk > 0.0) lake++;
                        maxLake = Math.max(maxLake, lk);
                    }
                }
                System.out.println("HYDRO seed=" + Long.toHexString(seed) + " step=" + step
                        + " n=" + n
                        + " any=" + String.format("%.4f", any / (double) n)
                        + " >.05=" + String.format("%.4f", over5 / (double) n)
                        + " >.25=" + String.format("%.4f", over25 / (double) n)
                        + " >.5=" + String.format("%.4f", over50 / (double) n)
                        + " maxRs=" + String.format("%.4f", maxRs)
                        + " maxAccum=" + String.format("%.2f", maxAccum)
                        + " meanAccum=" + String.format("%.3f", sumAccum / n)
                        + " lake>0=" + String.format("%.4f", lake / (double) n)
                        + " maxLake=" + String.format("%.4f", maxLake));
            }
        }
    }

    @Test
    void aFrozenWorldGetsNoLiquidRivers() {
        TerrainShaper frozen = shaper(0xEA33L, profile(0.03, 0.5, 0.5, 0.4, 0.1, 0.3));
        assertTrue(!frozen.hydrology().allowsLiquidRivers(),
                "a near-zero-temperature world must not permit liquid rivers");
        for (int x = -600; x <= 600; x += 40) {
            assertEquals(0.0, frozen.hydrology().riverStrength(x, 33), 0.0,
                    "no liquid river may exist where the water phase forbids it");
            assertEquals(0.0, frozen.hydrology().riverCarve(x, 33, 0.5), 0.0,
                    "no river incision may exist on a frozen world");
        }
    }
}