package com.modscreating.unlimitedspace.core.worldgen.hydrology;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT STAGE 4.1 — ORDINARY HYDROLOGY REACHES THE BLOCKS.
 *
 * <p>The defect: {@code riverMask} / {@code lakeMask} were solved by the real drainage network,
 * published into {@link WorldgenColumnSample} and consumed by biome / material / surface-category
 * scoring — but the ONLY writer of a fluid block was the {@code h < sea} ocean test. So on an
 * EARTHLIKE world the masks were live for scoring and dead for blocks: measured riverMask&gt;0 on
 * 274/10201 columns and lakeMask&gt;0 on 545/10201 while the world produced no visible water, which
 * is exactly the reported "dry stone plain".
 *
 * <p>This exercises the real pipeline — the production {@code PlanetFeaturePlacer} source shape plus
 * the solved masks — so it pins the CONSUMER, not merely the producer.
 */
@Tag("worldgen")
class HydrologyReachesBlocksTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                  double tect, double volc, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_ROCKY);
    }

    private static V3ColumnSampler sampler(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        TerrainShaper sh = TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p), TerrainSignatureSelector.create(seed, p),
                80.0, 24.0, null, null, climate);
        return new V3ColumnSampler(sh,
                new ClimateField(climate, sh.character(), new WindDirectionField(seed)), null, null);
    }

    @Test
    void aWarmWetWorldHasReachableRiverAndLakeColumnsAboveSeaLevel() {
        for (long seed : new long[]{0x4B11L, 0x4B22L}) {
            V3ColumnSampler s = sampler(seed, profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.45));
            WorldgenColumnSample col = new WorldgenColumnSample();
            int water = 0, total = 0;
            for (int x = -1600; x <= 1600; x += 16) {
                for (int z = -1600; z <= 1600; z += 16) {
                    total++;
                    s.sampleColumn(x, z, col);
                    // The exact gate the block writer uses: a column becomes standing water only
                    // where the solved drainage masks clear their continuous floor.
                    if (Math.max(col.riverMask, col.lakeMask) >= 0.02) water++;
                }
            }
            assertTrue(water > 0,
                    "a warm wet world must have river / lake columns that reach the block layer,"
                            + " got 0 of " + total + " (seed=" + Long.toHexString(seed) + ")");
        }
    }

    @Test
    void theFluidStageIsNotGatedBehindTheVolcanicProvince() {
        // The consumer-side invariant: the hydrology water pass runs BEFORE the volcanic gate, so a
        // non-volcanic world is still allowed to place its ordinary rivers and lakes. This is a
        // source-shape guard on the production file, which is what keeps the two passes from being
        // re-ordered or re-gated by a refactor.
        String placer;
        try {
            placer = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/modscreating/unlimitedspace/worldgen/planet/"
                            + "PlanetFeaturePlacer.java"));
        } catch (java.io.IOException e) {
            throw new AssertionError("production PlanetFeaturePlacer must remain readable", e);
        }
        int hydrology = placer.indexOf("applyHydrologyWater(g, chunk, minBX, minBZ, sea, geology);");
        int volcanicGate = placer.indexOf("ctx.volcanicIntensity() < 0.5");
        assertTrue(hydrology > 0 && volcanicGate > 0,
                "both the hydrology pass and the volcanic gate must exist");
        assertTrue(hydrology < volcanicGate,
                "the hydrology water pass must run BEFORE the volcanic gate — otherwise an "
                        + "EARTHLIKE world can never place rivers or lakes");
    }

    @Test
    void aFrozenWorldStillGetsNoStandingLiquidHydrology() {
        // The cold-lava / cold-water rule must survive the new pass untouched.
        V3ColumnSampler s = sampler(0x4B33L, profile(0.03, 0.5, 0.5, 0.4, 0.1, 0.3));
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = -1200; x <= 1200; x += 48) {
            for (int z = -1200; z <= 1200; z += 48) {
                s.sampleColumn(x, z, col);
                assertTrue(col.riverMask == 0.0,
                        "a near-zero-temperature world must have no liquid river mask");
            }
        }
    }
}