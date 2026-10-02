package com.modscreating.unlimitedspace.core.worldgen.vegetation;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 (D) — the actual-habitability vegetation gate:
 * non-habitable worlds grow EXACTLY ZERO plants; on a habitable world the existing ecological
 * intensity logic still decides where plants appear, deterministically.
 */
@Tag("worldgen")
class VegetationHabitabilityGateTest {

    private static final long VEG = 918273645L;

    private static PlanetProperties props() {
        return new PlanetProperties(
                new PlanetSeed(4321L), PlanetType.ROCKY, PlanetSurface.SOLID_ROCKY,
                1.0, 1.0, 288.0, 0.6,
                AtmosphereType.MODERATE, 0.55,
                0.6, 0.3, 0.3, 0.8, 0.6, 0.2,
                PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void nonHabitableWorldsGrowExactlyZeroVegetation() {
        PlanetProperties p = props();
        for (int x = -200; x <= 200; x += 13) {
            for (int z = -200; z <= 200; z += 17) {
                for (SubBiome sub : SubBiome.VALUES) {
                    assertNull(VegetationSelector.decideEcology(VEG, p, false, sub, null,
                                    0.95, 0.0, 0.95, 0.5, x, z),
                            "non-habitable → exactly zero vegetation (sub=" + sub + ")");
                }
            }
        }
    }

    @Test
    void habitableWorldsCanGrowVegetationInCompatibleLocations() {
        PlanetProperties p = props();
        boolean grown = false;
        for (int x = -400; x <= 400 && !grown; x += 11) {
            for (int z = -400; z <= 400 && !grown; z += 7) {
                if (VegetationSelector.decideEcology(VEG, p, true, SubBiome.MEADOW, null,
                        0.9, 0.05, 0.9, 0.5, x, z) != null) {
                    grown = true;
                }
            }
        }
        assertTrue(grown, "a habitable world must be able to grow vegetation somewhere");
    }

    @Test
    void sameSeedSameCoordinatesSameResult() {
        PlanetProperties p = props();
        assertEquals(
                VegetationSelector.decideEcology(VEG, p, true, SubBiome.MEADOW, null,
                        0.9, 0.05, 0.9, 0.5, 10, 20),
                VegetationSelector.decideEcology(VEG, p, true, SubBiome.MEADOW, null,
                        0.9, 0.05, 0.9, 0.5, 10, 20));
    }
}
