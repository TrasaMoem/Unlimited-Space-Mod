package com.modscreating.unlimitedspace.core.worldgen.surface;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R20 surface-category tests: profile+archetype+province → coherent surface identity;
 * SOLID_ROCKY is no longer the de-facto universal surface.
 */
class SurfaceCategoryTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double cry,
                                                 double organic) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, 0.3, 0.5, tect, volc, 0.3,
                0.4, 0.1, 0.4, 0.3, cry, organic, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void hotDryDesertGivesDustyOrSandy() {
        PlanetPhysicalProfile p = profile(0.9, 0.05, 0.02, 0.3, 0.05, 0.1, 0.05);
        SurfaceCategory c = SurfaceCategorySelector.classify(p, TerrainArchetype.DESERT_WORLD,
                GeologicalProvince.PLAINS);
        assertTrue(c == SurfaceCategory.SANDY || c == SurfaceCategory.DUSTY,
                "hot+dry+desert must be DUSTY/SANDY, got " + c);
    }

    @Test
    void coldWetGivesFrozenOrGlacial() {
        PlanetPhysicalProfile p = profile(0.1, 0.8, 0.7, 0.2, 0.02, 0.05, 0.05);
        SurfaceCategory c = SurfaceCategorySelector.classify(p, TerrainArchetype.GLACIAL_WORLD,
                GeologicalProvince.PLAINS);
        assertTrue(c == SurfaceCategory.FROZEN || c == SurfaceCategory.GLACIAL,
                "cold+wet must be FROZEN/GLACIAL, got " + c);
    }

    @Test
    void volcanicProvinceGivesVolcanicOrAshen() {
        PlanetPhysicalProfile p = profile(0.5, 0.3, 0.1, 0.5, 0.9, 0.05, 0.05);
        SurfaceCategory c = SurfaceCategorySelector.classify(p, TerrainArchetype.VOLCANIC_WORLD,
                GeologicalProvince.VOLCANIC);
        assertTrue(c == SurfaceCategory.VOLCANIC || c == SurfaceCategory.ASHEN, "got " + c);
    }

    @Test
    void crystalRichGivesCrystalline() {
        PlanetPhysicalProfile p = profile(0.5, 0.4, 0.2, 0.4, 0.1, 0.8, 0.05);
        assertEquals(SurfaceCategory.CRYSTALLINE,
                SurfaceCategorySelector.classify(p, TerrainArchetype.STRANGE_WORLD,
                        GeologicalProvince.CRYSTAL));
    }

    @Test
    void wetBasinGivesSedimentary() {
        PlanetPhysicalProfile p = profile(0.5, 0.8, 0.7, 0.2, 0.05, 0.05, 0.3);
        assertEquals(SurfaceCategory.SEDIMENTARY,
                SurfaceCategorySelector.classify(p, TerrainArchetype.BASIN_WORLD,
                        GeologicalProvince.BASIN));
    }

    @Test
    void rockyIsNotUniversal() {
        // Across diverse planets and provinces, plain ROCKY must NOT dominate everywhere —
        // the old SOLID_ROCKY-dominance bug.
        int rocky = 0, total = 0;
        double[] temps = {0.1, 0.3, 0.5, 0.7, 0.9};
        double[] hums = {0.1, 0.4, 0.8};
        GeologicalProvince[] provs = {GeologicalProvince.PLAINS, GeologicalProvince.BASIN,
                GeologicalProvince.CANYON, GeologicalProvince.CRATER, GeologicalProvince.SALT,
                GeologicalProvince.VOLCANIC, GeologicalProvince.GLACIAL};
        TerrainArchetype[] archs = {TerrainArchetype.CONTINENTAL, TerrainArchetype.DESERT_WORLD,
                TerrainArchetype.GLACIAL_WORLD, TerrainArchetype.VOLCANIC_WORLD,
                TerrainArchetype.OCEAN_WORLD};
        for (double t : temps) {
            for (double h : hums) {
                PlanetPhysicalProfile p = profile(t, h, 1 - h, 0.4, 0.3, 0.3, 0.3);
                for (GeologicalProvince prov : provs) {
                    for (TerrainArchetype a : archs) {
                        if (SurfaceCategorySelector.classify(p, a, prov) == SurfaceCategory.ROCKY) {
                            rocky++;
                        }
                        total++;
                    }
                }
            }
        }
        double share = (double) rocky / total;
        assertTrue(share < 0.35, "ROCKY must not dominate surface identity: " + share);
    }

    @Test
    void classificationIsDeterministic() {
        PlanetPhysicalProfile p = profile(0.4, 0.5, 0.5, 0.5, 0.2, 0.2, 0.4);
        assertEquals(
                SurfaceCategorySelector.classify(p, TerrainArchetype.CONTINENTAL, GeologicalProvince.PLAINS),
                SurfaceCategorySelector.classify(p, TerrainArchetype.CONTINENTAL, GeologicalProvince.PLAINS));
    }
}
