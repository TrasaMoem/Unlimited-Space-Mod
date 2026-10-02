package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3 / STAGE 2 — the ICE, VOLCANIC and relief-sign hard gates.
 *
 * <p>The reference families are not presets: each test builds a world whose CHARACTER drives the
 * outcome, and asserts the observable landform signature the reference set demands.
 */
@Tag("worldgen")
@Tag("audit")
class V3GlacialAndVolcanicWorldTest {

    static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                         double tect, double volc, double geo, double ero) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    static TerrainShaper shaper(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        return TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p),
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0,
                null, null, climate);
    }

    /** A cold, icy, tectonically active world: the ICE / GLACIAL reference family. */
    static TerrainShaper glacialWorld(long seed) {
        return shaper(seed, profile(0.03, 0.35, 0.55, 0.60, 0.10, 0.30, 0.55));
    }

    /** A hot, volcanic, tectonically active world: the VOLCANIC / ASH reference family. */
    static TerrainShaper volcanicWorld(long seed) {
        return shaper(seed, profile(0.75, 0.25, 0.15, 0.80, 0.85, 0.80, 0.40));
    }


    @Test
    void aGlacialWorldKeepsMountainsAndGlacialTroughs() {
        for (long seed : new long[]{0x5100L, 0x5200L}) {
            TerrainShaper sh = glacialWorld(seed);
            assertTrue(sh.character().weights().glacialWeight() > 0.6,
                    "the cold world must actually be glacial: "
                            + sh.character().weights().glacialWeight());

            ElevationScratch sc = new ElevationScratch();
            double minTrough = 0.0;
            double maxMountain = 0.0;
            double minHeight = Double.MAX_VALUE;
            double maxHeight = -Double.MAX_VALUE;
            for (int x = -6000; x <= 6000; x += 40) {
                sh.elevationField().sampleInto(x, 313, sc);
                minTrough = Math.min(minTrough, sc.glacialRelief);
                maxMountain = Math.max(maxMountain, sc.mountainEnvelope);
                minHeight = Math.min(minHeight, sc.height);
                maxHeight = Math.max(maxHeight, sc.height);
            }
            // Cold worlds must NOT become flat white fields: mountains stay active.
            assertTrue(maxMountain > 0.45,
                    "a cold world must keep active mountain belts: " + maxMountain);
            // Glacial troughs are genuine negative relief.
            assertTrue(minTrough < -2.0,
                    "a glacial world must carve U-shaped troughs: " + minTrough);
            assertTrue(maxHeight - minHeight > 50.0,
                    "a glacial world lost its macro relief: " + (maxHeight - minHeight));
        }
    }

    @Test
    void aVolcanicWorldHasStrongReliefMountainsAndCalderas() {
        for (long seed : new long[]{0x5300L, 0x5400L}) {
            TerrainShaper sh = volcanicWorld(seed);
            assertTrue(sh.character().weights().volcanicWeight() > 0.6,
                    "the hot volcanic world must actually be volcanic: "
                            + sh.character().weights().volcanicWeight());

            ElevationScratch sc = new ElevationScratch();
            double maxVolc = -Double.MAX_VALUE;
            double minVolc = Double.MAX_VALUE;
            double maxMountain = 0.0;
            double minHeight = Double.MAX_VALUE;
            double maxHeight = -Double.MAX_VALUE;
            // Scan an AREA, not a single transect, and finely enough to actually land inside a cone:
            // a cone is a large, radially symmetric, RARE feature whose summit crater is only a few
            // dozen blocks across, so a coarse grid walks straight past every caldera.
            for (int x = -4000; x <= 4000; x += 32) {
                for (int z = -4000; z <= 4000; z += 32) {
                    sh.elevationField().sampleInto(x, z, sc);
                    maxVolc = Math.max(maxVolc, sc.volcanicRelief);
                    minVolc = Math.min(minVolc, sc.volcanicRelief);
                    maxMountain = Math.max(maxMountain, sc.mountainEnvelope);
                    minHeight = Math.min(minHeight, sc.height);
                    maxHeight = Math.max(maxHeight, sc.height);
                }
            }
            assertTrue(maxMountain > 0.35,
                    "a volcanic world must carry mountain/volcanic centres: " + maxMountain);
            // Volcanic relief must include BOTH a raised edifice and a collapsed centre: the
            // reference set is cones AND calderas, not a single monotone bulge.
            assertTrue(maxVolc > 5.0, "volcanic edifices must rise: " + maxVolc);
            assertTrue(minVolc < -1.0, "caldera floors must collapse: " + minVolc);
            assertTrue(maxHeight - minHeight > 60.0,
                    "a volcanic world lost its macro relief: " + (maxHeight - minHeight));
        }
    }

    @Test
    void bothPositiveAndNegativeReliefExistOnEveryFamily() {
        // A world built only as the inverse of positive noise has no basins, no troughs and no
        // valleys; the V3 spec requires an INDEPENDENT negative-relief budget.
        TerrainShaper[] worlds = {
                glacialWorld(0x5500L), volcanicWorld(0x5600L),
                shaper(0x5700L, profile(0.45, 0.65, 0.60, 0.55, 0.10, 0.30, 0.45)),
                shaper(0x5800L, profile(0.85, 0.10, 0.04, 0.35, 0.10, 0.30, 0.70))
        };
        for (TerrainShaper sh : worlds) {
            int positive = 0;
            int negative = 0;
            int total = 0;
            // Several PARALLEL LINES: a single transect can legitimately run along a basin floor and
            // never turn upward, which says nothing about whether the world has positive relief.
            for (int line = 0; line < 3; line++) {
                int z = 401 + line * 1301;
                int prev = sh.surfaceHeight(-4000, z);
                for (int x = -3999; x <= 4000; x++) {
                    int h = sh.surfaceHeight(x, z);
                    int d = h - prev;
                    if (d > 0) positive++;
                    if (d < 0) negative++;
                    total++;
                    prev = h;
                }
            }
            assertTrue(positive > total / 20,
                    "a world must have real positive relief, got " + positive + "/" + total);
            assertTrue(negative > total / 20,
                    "a world must have real negative relief (valleys/basins), got "
                            + negative + "/" + total);
        }
    }

    @Test
    void terrainAmplitudeVariesSpatially() {
        // A single global amplitude multiplier would make every transect identical in range. The V3
        // amplitude targets are per-level budgets, so different regions must differ.
        TerrainShaper sh = volcanicWorld(0x5900L);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = -8000; x <= 8000; x += 20) {
            int h = sh.surfaceHeight(x, 655);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertTrue(max - min > 60.0,
                "terrain amplitude must vary spatially, not be one global constant: span="
                        + (max - min));
    }
}
