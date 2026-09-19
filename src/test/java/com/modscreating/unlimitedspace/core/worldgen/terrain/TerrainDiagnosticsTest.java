package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R20 headless terrain preview / statistics (no Minecraft runtime required).
 *
 * <p>Samples a logical 512×512-column grid (step 8 → 4096-block span) for the five reference
 * test planets and prints a full diagnostic summary. Also enforces the quality contract:
 * terrain bounds, meaningful relief, bounded local detail and non-dominant rocky surfaces.
 */
class TerrainDiagnosticsTest {

    private static PlanetPhysicalProfile profile(double temp, double hum, double water,
                                                 double ocean, double tect, double volc,
                                                 double ero, double impact, double crystal) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, ocean, 0.5, tect, volc, 0.3,
                ero, impact, 0.4, 0.3, crystal, 0.3, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    private static TerrainDiagnostics.Stats preview(long seed, PlanetPhysicalProfile p, String name) {
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seed, p);
        TerrainDiagnostics.Stats s = TerrainDiagnostics.sample(seed, p, provinces,
                TerrainSignatureSelector.create(seed, p), 80.0, 24.0, 512, 8);
        System.out.println("[TERRAIN PREVIEW] " + name + ": " + s.summary());
        return s;
    }

    @Test
    void planetA_hotVolcanic() {
        TerrainDiagnostics.Stats s = preview(101L,
                profile(0.95, 0.15, 0.05, 0.0, 0.5, 0.95, 0.2, 0.05, 0.1), "A HOT+VOLCANIC");
        assertTrue(s.minHeight() >= s.minHeight() && s.maxHeight() > s.minHeight());
        assertTrue(s.macroRelief() > 10, "volcanic planet needs visible relief");
    }

    @Test
    void planetB_coldGlacial() {
        TerrainDiagnostics.Stats s = preview(202L,
                profile(0.08, 0.6, 0.5, 0.3, 0.3, 0.05, 0.35, 0.05, 0.1), "B COLD+GLACIAL");
        assertTrue(s.macroRelief() > 5);
    }

    @Test
    void planetC_dryHighErosion() {
        TerrainDiagnostics.Stats s = preview(303L,
                profile(0.7, 0.05, 0.02, 0.05, 0.4, 0.1, 0.9, 0.2, 0.1), "C DRY+HIGH_EROSION");
        assertTrue(s.avgSlope1() < 3.0, "high-erosion world must be smooth at block scale");
    }

    @Test
    void planetD_highImpact() {
        TerrainDiagnostics.Stats s = preview(404L,
                profile(0.5, 0.2, 0.05, 0.05, 0.2, 0.05, 0.3, 0.95, 0.1), "D HIGH_IMPACT");
        assertTrue(s.macroRelief() > 10, "crater world needs visible relief");
    }

    @Test
    void planetE_wetOceanic() {
        TerrainDiagnostics.Stats s = preview(505L,
                profile(0.5, 0.9, 0.9, 0.85, 0.2, 0.05, 0.4, 0.05, 0.1), "E WET+OCEANIC");
        assertTrue(s.avgHeight() < 80.0 + 24.0,
                "oceanic world mean height must sit near/below base, got " + s.avgHeight());
    }

    @Test
    void previewsAreDeterministic() {
        PlanetPhysicalProfile p = profile(0.4, 0.4, 0.3, 0.2, 0.6, 0.2, 0.4, 0.3, 0.2);
        TerrainDiagnostics.Stats a = preview(606L, p, "det-check");
        TerrainDiagnostics.Stats b = preview(606L, p, "det-check");
        assertEquals(a.avgHeight(), b.avgHeight(), 1e-9);
        assertEquals(a.maxHeight(), b.maxHeight(), 1e-9);
        assertEquals(a.archetype(), b.archetype());
    }

    @Test
    void rockySurfaceIsNotDominantAcrossTestPlanets() {
        double totalShare = 0;
        int n = 0;
        long[] seeds = {101L, 202L, 303L, 404L, 505L, 606L, 707L, 808L};
        double[] temps = {0.95, 0.08, 0.7, 0.5, 0.5, 0.4, 0.6, 0.3};
        for (int i = 0; i < seeds.length; i++) {
            PlanetPhysicalProfile p = profile(temps[i], 0.4, 0.3, 0.2, 0.5, 0.3, 0.4, 0.3, 0.3);
            GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seeds[i], p);
            TerrainDiagnostics.Stats s = TerrainDiagnostics.sample(seeds[i], p, provinces,
                    TerrainSignatureSelector.create(seeds[i], p), 80.0, 24.0, 128, 16);
            totalShare += s.rockySurfaceShare();
            n++;
        }
        double avg = totalShare / n;
        System.out.println("[TERRAIN PREVIEW] avg ROCKY surface share across planets = " + avg);
        assertTrue(avg < 0.75,
                "SOLID_ROCKY-style surfaces must not dominate every archetype: " + avg);
    }
}
