package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetWorldgenProfileTest {

    private static final Galaxy GALAXY = Galaxy.from(777L);

    private static Planet findType(PlanetType type) {
        for (int s = 0; s < 60; s++) {
            for (int o = 0; o < 20; o++) {
                Planet p = GALAXY.getStarSystem(GALAXY.systemId(s)).getPlanet(o);
                if (p.properties().type() == type) return p;
            }
        }
        throw new AssertionError("no " + type + " planet found in sample");
    }

    @Test
    void fromPlanetIsDeterministic() {
        Planet p = findType(PlanetType.ROCKY);
        PlanetWorldgenProfile a = PlanetWorldgenProfile.from(p);
        PlanetWorldgenProfile b = PlanetWorldgenProfile.from(p);
        assertEquals(a, b);
        assertEquals(p.id(), a.planetId());
        assertEquals(p.properties().terrainSeed(), a.terrainSeed());
    }

    @Test
    void rockySurfaceMapsToStoneSurfaceAndRockSubsurface() {
        Planet p = findType(PlanetType.ROCKY);
        PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
        assertEquals(SurfaceMaterial.STONE, prof.surfaceMaterial());
        assertEquals(SurfaceMaterial.ROCK, prof.subsurfaceMaterial());
        assertNotEquals(FluidProfile.NONE, prof.fluid());
    }

    @Test
    void gasGiantHasNoWaterAndMetallicSurface() {
        Planet p = findType(PlanetType.GAS_GIANT);
        PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
        assertFalse(prof.hasWater());
        assertEquals(FluidProfile.NONE, prof.fluid());
        assertEquals(SurfaceMaterial.METALLIC, prof.surfaceMaterial());
    }

    @Test
    void waterFollowsCoverage() {
        Planet p = findType(PlanetType.ROCKY);
        if (p.properties().waterCoverage() > 0.01) {
            assertTrue(PlanetWorldgenProfile.from(p).hasWater());
        }
    }

    @Test
    void baseHeightAndAmplitudeAreSane() {
        Planet p = findType(PlanetType.ROCKY);
        PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
        assertTrue(prof.baseHeight() > 20.0 && prof.baseHeight() < 120.0, "baseHeight " + prof.baseHeight());
        assertTrue(prof.amplitude() >= 0.0 && prof.amplitude() <= 40.0, "amplitude " + prof.amplitude());
        assertTrue(prof.frequency() > 0.0);
    }

    @Test
    void factoryProducesGeneratorWithSameSeed() {
        Planet p = findType(PlanetType.ROCKY);
        PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
        TerrainGenerator tg = TerrainGenerators.from(prof);
        assertEquals(prof.terrainSeed(), tg.seed());
    }
}
