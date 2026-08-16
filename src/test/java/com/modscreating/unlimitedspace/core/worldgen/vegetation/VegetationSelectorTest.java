package com.modscreating.unlimitedspace.core.worldgen.vegetation;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VegetationSelectorTest {

    private static final long VEG = 555111L;

    private static PlanetProperties props(double vegDensity, PlanetSurface surface) {
        PlanetType type = surface == PlanetSurface.GASEOUS ? PlanetType.GAS_GIANT : PlanetType.ROCKY;
        return new PlanetProperties(
                new PlanetSeed(1234L), type, surface,
                1.0, 1.0, 285.0, 0.5,
                AtmosphereType.TRACE, 0.5,
                0.3, 0.3, 0.3, vegDensity, 0.5, 0.2,
                PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void sameSeedSameCoordinatesSameResult() {
        PlanetProperties p = props(0.8, PlanetSurface.SOLID_ROCKY);
        PlanetBiome b = PlanetBiome.HOT_DRY;
        assertEquals(VegetationSelector.decide(VEG, p, b, 10, 20),
                VegetationSelector.decide(VEG, p, b, 10, 20));
        assertEquals(VegetationSelector.decide(VEG, p, b, -5, -7),
                VegetationSelector.decide(VEG, p, b, -5, -7));
    }

    @Test
    void differentCoordinatesCanChangePlacement() {
        PlanetProperties p = props(0.8, PlanetSurface.SOLID_ROCKY);
        boolean present = false, absent = false;
        for (int x = -500; x <= 500; x += 11) {
            for (int z = -500; z <= 500; z += 13) {
                PlanetBiome b = PlanetBiomeSelector.select(p.biomeSeed(), x, z);
                boolean has = VegetationSelector.decide(VEG, p, b, x, z) != null;
                present |= has;
                absent |= !has;
            }
        }
        assertTrue(present, "vegetation should appear somewhere on a land planet");
        assertTrue(absent, "vegetation should be absent somewhere (sparse, not blanket)");
    }

    @Test
    void differentPlanetSeedsDifferSomewhere() {
        PlanetProperties p = props(0.8, PlanetSurface.SOLID_ROCKY);
        boolean different = false;
        outer:
        for (int x = 0; x < 2000; x += 7) {
            PlanetBiome b = PlanetBiomeSelector.select(p.biomeSeed(), x, 0);
            PlantDefinition a = VegetationSelector.decide(VEG, p, b, x, 0);
            PlantDefinition c = VegetationSelector.decide(VEG + 1, p, b, x, 0);
            if (a == null && c != null || a != null && !a.equals(c)) {
                different = true;
                break outer;
            }
        }
        assertTrue(different, "different vegetation seeds should diverge somewhere");
    }

    @Test
    void oceanBiomeNeverGetsVegetation() {
        PlanetProperties p = props(0.9, PlanetSurface.SOLID_ROCKY);
        for (int i = 0; i < 200; i++) {
            assertNull(VegetationSelector.decide(VEG, p, PlanetBiome.OCEAN, i, i * 3));
        }
    }

    @Test
    void deepSpaceLikeSurfacesAreVegetationFree() {
        assertNull(VegetationSelector.decide(VEG, props(0.8, PlanetSurface.OCEANIC),
                PlanetBiome.WARM_WET, 5, 5));
        assertNull(VegetationSelector.decide(VEG, props(0.8, PlanetSurface.GASEOUS),
                PlanetBiome.WARM_WET, 5, 5));
    }

    @Test
    void densityStaysBounded() {
        PlanetProperties p = props(0.8, PlanetSurface.SOLID_ROCKY);
        double d = VegetationSelector.density(p, PlanetBiome.WARM_WET);
        assertTrue(d >= 0.0 && d <= 1.0);
        assertTrue(VegetationSelector.density(p, PlanetBiome.OCEAN) == 0.0);
    }

    @Test
    void generationOrderDoesNotMatter() {
        // Same function evaluated in a different call order must yield the same map.
        PlanetProperties p = props(0.8, PlanetSurface.SOLID_ROCKY);
        int[][] coords = {{3, 7}, {-5, 2}, {12, -9}, {0, 0}, {8, 8}};
        Map<String, PlantDefinition> base = new HashMap<>();
        for (int[] c : coords) {
            base.put(c[0] + "," + c[1],
                    VegetationSelector.decide(VEG, p, PlanetBiomeSelector.select(p.biomeSeed(), c[0], c[1]), c[0], c[1]));
        }
        Map<String, PlantDefinition> reverse = new HashMap<>();
        for (int i = coords.length - 1; i >= 0; i--) {
            int[] c = coords[i];
            reverse.put(c[0] + "," + c[1],
                    VegetationSelector.decide(VEG, p, PlanetBiomeSelector.select(p.biomeSeed(), c[0], c[1]), c[0], c[1]));
        }
        assertEquals(base.size(), reverse.size());
        base.forEach((k, v) -> assertEquals(v, reverse.get(k), "coordinate " + k + " must match regardless of call order"));
    }
}