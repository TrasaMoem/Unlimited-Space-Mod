package com.modscreating.unlimitedspace.core.worldgen.surface;

import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainArchetype;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 composite surface-category tests. THE contract: SurfaceCategory is a COMPOSITE of
 * planet climate + planet relief + geology, and ROCKY (the SOLID_ROCKY-style answer) is a
 * genuine last resort — measured over 1200 deterministic planet profiles.
 */
class SurfaceCategoryCompositeTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double tect, double volc, double cry,
                                                 double organic) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.6, 0.5, tect, volc, 0.3,
                0.35, 0.1, 0.4, 0.3, cry, organic, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void compositeStorytellingCombos() {
        PlanetPhysicalProfile cold = profile(0.10, 0.6, 0.5, 0.6, 0.05, 0.1, 0.1);
        PlanetPhysicalProfile hot = profile(0.90, 0.15, 0.05, 0.4, 0.9, 0.05, 0.05);
        PlanetPhysicalProfile dry = profile(0.65, 0.08, 0.02, 0.4, 0.1, 0.05, 0.05);
        PlanetPhysicalProfile wet = profile(0.50, 0.85, 0.8, 0.2, 0.05, 0.1, 0.4);

        // COLD + MOUNTAIN (+ glacial climate) → frozen rock, never bare ROCKY.
        SurfaceCategory a = SurfaceCategorySelector.classify(cold, TerrainArchetype.MOUNTAIN_WORLD,
                GeologicalProvince.MOUNTAIN, ClimateArchetype.FROZEN, ReliefArchetype.MOUNTAINOUS, 0.8);
        assertTrue(a == SurfaceCategory.FROZEN || a == SurfaceCategory.GLACIAL, "got " + a);
        // HOT + VOLCANIC → volcanic/ashen.
        SurfaceCategory b = SurfaceCategorySelector.classify(hot, TerrainArchetype.VOLCANIC_WORLD,
                GeologicalProvince.VOLCANIC, ClimateArchetype.EXTREME_HOT,
                ReliefArchetype.VOLCANIC, 0.7);
        assertTrue(b == SurfaceCategory.VOLCANIC || b == SurfaceCategory.ASHEN, "got " + b);
        // DRY + CANYON → dusty/sandy badland.
        SurfaceCategory c = SurfaceCategorySelector.classify(dry, TerrainArchetype.DESERT_WORLD,
                GeologicalProvince.CANYON, ClimateArchetype.HYPERARID, ReliefArchetype.CANYONLAND, 0.4);
        assertTrue(c == SurfaceCategory.DUSTY || c == SurfaceCategory.SANDY, "got " + c);
        // WET + BASIN → sedimentary / muddy.
        SurfaceCategory d = SurfaceCategorySelector.classify(wet, TerrainArchetype.BASIN_WORLD,
                GeologicalProvince.BASIN, ClimateArchetype.OCEANIC, ReliefArchetype.BASIN_RICH, 0.2);
        assertTrue(d == SurfaceCategory.SEDIMENTARY || d == SurfaceCategory.MUDDY, "got " + d);
        // High crystal → crystalline.
        PlanetPhysicalProfile crystal = profile(0.5, 0.4, 0.2, 0.4, 0.1, 0.8, 0.1);
        assertEquals(SurfaceCategory.CRYSTALLINE, SurfaceCategorySelector.classify(crystal,
                TerrainArchetype.STRANGE_WORLD, GeologicalProvince.CRYSTAL,
                ClimateArchetype.TEMPERATE, ReliefArchetype.MIXED, 0.5));
    }
// PART2

    @Test
    void rockyIsARareLastResortAcross1200Planets() {
        Map<SurfaceCategory, Integer> counts = new EnumMap<>(SurfaceCategory.class);
        int total = 0;
        for (int i = 0; i < 1200; i++) {
            long seed = 1000 + i;
            // Deterministic pseudo-profile spread across the whole climate space.
            double t = frac(seed, 1);
            double h = frac(seed, 2);
            double w = frac(seed, 3);
            double tect = frac(seed, 4);
            double volc = frac(seed, 5);
            double cry = frac(seed, 6);
            double org = frac(seed, 7);
            PlanetPhysicalProfile p = profile(t, h, w, tect, volc, cry, org);
            ClimateArchetype climate = bestClimate(p);
            ReliefArchetype relief = bestRelief(p);
            GeologicalProvince province = GeologicalProvince.VALUES[i % GeologicalProvince.VALUES.length];
            double elevation = frac(seed, 8);
            SurfaceCategory c = SurfaceCategorySelector.classify(p, TerrainArchetype.CONTINENTAL,
                    province, climate, relief, elevation);
            counts.merge(c, 1, Integer::sum);
            total++;
        }
        assertEquals(1200, total);
        double rocky = counts.getOrDefault(SurfaceCategory.ROCKY, 0) / (double) total;
        System.out.printf("[R21 SURFACE] distribution over %d planets: %s  ROCKY share=%.3f%n",
                total, counts, rocky);
        // ROCKY must be a rare last resort, not the default answer (the SOLID_ROCKY bug).
        assertTrue(rocky < 0.25, "ROCKY must not dominate: " + rocky);
        // And the composite must actually produce a variety of surface identities.
        assertTrue(counts.size() >= 5, "surface identity variety too low: " + counts.size());
    }

    private static double frac(long seed, int slot) {
        return com.modscreating.unlimitedspace.core.seed.Seeds.fraction(
                com.modscreating.unlimitedspace.core.seed.Seeds.derive(seed, "us.test.profile"),
                slot);
    }

    private static ClimateArchetype bestClimate(PlanetPhysicalProfile p) {
        ClimateArchetype best = ClimateArchetype.TEMPERATE;
        double bestScore = -1;
        for (ClimateArchetype a : ClimateArchetype.VALUES) {
            double s = a.score(p);
            if (s > bestScore) {
                bestScore = s;
                best = a;
            }
        }
        return best;
    }

    private static ReliefArchetype bestRelief(PlanetPhysicalProfile p) {
        ReliefArchetype best = ReliefArchetype.ROLLING;
        double bestScore = -1;
        for (ReliefArchetype a : ReliefArchetype.VALUES) {
            double s = a.score(p);
            if (s > bestScore) {
                bestScore = s;
                best = a;
            }
        }
        return best;
    }
}
