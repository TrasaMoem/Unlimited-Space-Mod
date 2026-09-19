package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 headless PLANET SUMMARY: the quality gate that catches bad generation WITHOUT launching
 * Minecraft. Prints the summary for the reference planets and asserts the measurable
 * geography contracts (biome scale, material scale, relief composition).
 */
class R21PlanetSummaryTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double ero,
                                                 double impact, double crystal) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, 0.3,
                ero, impact, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainDiagnostics.Stats summary(long seed, PlanetPhysicalProfile p,
                                                    ReliefArchetype relief, String name) {
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seed, p);
        TerrainDiagnostics.Stats s = TerrainDiagnostics.sample(seed, p, provinces,
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0, 256, 8,
                new PlanetReliefProfile(relief, relief.mountainCoverage(), seed), regionMap(seed, p));
        System.out.println("==== " + name + " ====");
        System.out.println(s.planetSummary());
        System.out.println(s.summary());
        return s;
    }

    private static BiomeRegionMap regionMap(long seed, PlanetPhysicalProfile p) {
        return BiomeRegionMap.create(
                com.modscreating.unlimitedspace.core.seed.Seeds.derive(seed, "us.biome.regions"),
                p.temperature(), p.humidity(), p.crystalAbundance(), p.volcanicActivity(),
                p.impactFrequency(), p.tectonicActivity());
    }

    @Test
    void flatWorldSummary() {
        TerrainDiagnostics.Stats s = summary(1111L, profile(0.45, 0.5, 0.4, 0.2, 0.05, 0.3, 0.05, 0.1),
                ReliefArchetype.FLAT, "FLAT / TEMPERATE");
        assertTrue(s.mountainCoverage() < 0.05, "FLAT world must be mountain-free: " + s.mountainCoverage());
        assertTrue(s.biomeMedianDiameter() >= 300.0, "biomes must be large: " + s.biomeMedianDiameter());
        assertTrue(s.materialMedianPatch() >= 120.0, "material patches must be regional: " + s.materialMedianPatch());
    }

    @Test
    void mountainWorldSummary() {
        TerrainDiagnostics.Stats s = summary(2222L, profile(0.35, 0.45, 0.3, 0.85, 0.1, 0.2, 0.1, 0.15),
                ReliefArchetype.MOUNTAINOUS, "MOUNTAINOUS / COLD-TEMPERATE");
        assertTrue(s.mountainCoverage() > 0.10, "mountain world needs mountains: " + s.mountainCoverage());
        assertTrue(s.macroRelief() > 20.0, "mountain world needs real relief: " + s.macroRelief());
        assertTrue(s.biomeChangesPer1000() < 4.0, "biomes must not flip: " + s.biomeChangesPer1000());
    }

    @Test
    void hotDryWorldSummary() {
        TerrainDiagnostics.Stats s = summary(3333L, profile(0.9, 0.08, 0.02, 0.4, 0.3, 0.45, 0.2, 0.05),
                ReliefArchetype.CANYONLAND, "HOT / ARID");
        assertTrue(s.biomeMedianDiameter() >= 300.0);
        System.out.printf("[R21] hot world rockyShare=%.2f%n", s.rockySurfaceShare());
    }

    @Test
    void coldFrozenWorldSummary() {
        TerrainDiagnostics.Stats s = summary(4444L, profile(0.06, 0.6, 0.5, 0.45, 0.05, 0.2, 0.1, 0.2),
                ReliefArchetype.GLACIAL, "COLD / GLACIAL");
        assertTrue(s.rockySurfaceShare() < 0.4, "a frozen world must not be bare rock: "
                + s.rockySurfaceShare());
    }

    @Test
    void summariesAreDeterministic() {
        PlanetPhysicalProfile p = profile(0.5, 0.5, 0.4, 0.5, 0.2, 0.3, 0.1, 0.2);
        TerrainDiagnostics.Stats a = summary(6666L, p, ReliefArchetype.ROLLING, "det-a");
        TerrainDiagnostics.Stats b = summary(6666L, p, ReliefArchetype.ROLLING, "det-b");
        assertEquals(a.avgHeight(), b.avgHeight(), 1e-9);
        assertEquals(a.biomeMedianDiameter(), b.biomeMedianDiameter(), 1e-9);
        assertEquals(a.mountainCoverage(), b.mountainCoverage(), 1e-12);
        assertNotNull(a.identity());
        assertTrue(a.identity().contains("climate="));
        assertTrue(a.identity().contains("relief="));
    }

    @Test
    void terrainIsNeverALandscapeOfNoise() {
        // The "potato field" regression guard at planet scale: macro slope must stay far
        // below the geographic relief on every reference world.
        PlanetPhysicalProfile p = profile(0.5, 0.5, 0.4, 0.6, 0.2, 0.3, 0.1, 0.2);
        TerrainDiagnostics.Stats s = summary(7777L, p, ReliefArchetype.MIXED, "MIXED");
        assertTrue(s.avgSlope64() < s.macroRelief() * 0.35,
                "64-block steps change height too much relative to relief: " + s.avgSlope64()
                        + " vs relief " + s.macroRelief());
    }
}
