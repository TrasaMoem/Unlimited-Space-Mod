package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetPropertyGeneratorTest {

    private static final Galaxy GALAXY = Galaxy.from(256L);

    private static Planet planet(int system, int orbit) {
        return GALAXY.getStarSystem(GALAXY.systemId(system)).getPlanet(orbit);
    }

    @Test
    void pickTypeAlwaysReturnsValidEnumValue() {
        for (long seed = 0; seed < 200; seed++) {
            PlanetType picked = PlanetPropertyGenerator.pickType(seed);
            assertTrue(picked.ordinal() >= 0 && picked.ordinal() < PlanetType.values().length);
        }
    }

    @Test
    void generatedPlanetIsDeterministic() {
        Planet a = planet(3, 2);
        Planet b = planet(3, 2);
        assertEquals(a, b);
        assertEquals(a.definition(), b.definition());
        assertEquals(a.properties(), b.properties());
    }

    @Test
    void planetSeedIsOrbitSpecific() {
        long s0 = GALAXY.getStarSystem(GALAXY.systemId(3)).planetSeed(0);
        long s1 = GALAXY.getStarSystem(GALAXY.systemId(3)).planetSeed(1);
        assertNotEquals(s0, s1);
        assertEquals(Seeds.planet(GALAXY.starSystemSeed(3), 1), s1);
    }

    @Test
    void temperatureStaysWithinTypeRange() {
        for (int s = 0; s < 40; s++) {
            for (int o = 0; o < 15; o++) {
                PlanetProperties p = planet(s, o).properties();
                assertTrue(p.temperature() >= p.type().temperatureMinK()
                                && p.temperature() <= p.type().temperatureMaxK(),
                        "temp " + p.temperature() + " outside " + p.type());
            }
        }
    }

    @Test
    void allPercentagesAreClampedToZeroOne() {
        for (int s = 0; s < 30; s++) {
            for (int o = 0; o < 12; o++) {
                PlanetProperties p = planet(s, o).properties();
                assertTrue(in01(p.humidity()), "humidity " + p.humidity());
                assertTrue(in01(p.atmosphericDensity()), "density " + p.atmosphericDensity());
                assertTrue(in01(p.waterCoverage()), "water " + p.waterCoverage());
                assertTrue(in01(p.terrainRoughness()), "roughness " + p.terrainRoughness());
                assertTrue(in01(p.erosion()), "erosion " + p.erosion());
                assertTrue(in01(p.vegetationDensity()), "veg " + p.vegetationDensity());
                assertTrue(in01(p.lifeLevel()), "life " + p.lifeLevel());
                assertTrue(in01(p.geologicalActivity()), "geo " + p.geologicalActivity());
            }
        }
    }

        private static boolean in01(double v) {
        return v >= 0.0 && v <= 1.0;
    }

    @Test
    void radiusAndGravityAreWithinTypeRanges() {
        for (int s = 0; s < 40; s++) {
            for (int o = 0; o < 10; o++) {
                PlanetProperties p = planet(s, o).properties();
                assertTrue(p.radiusProfile() >= p.type().radiusMin()
                                && p.radiusProfile() <= p.type().radiusMax(),
                        "radius " + p.radiusProfile() + " for " + p.type());
                assertTrue(p.gravity() >= p.type().gravityMin()
                                && p.gravity() <= p.type().gravityMax(),
                        "gravity " + p.gravity() + " for " + p.type());
            }
        }
    }

    @Test
    void gasGiantsHaveNoSurfaceWaterOrLife() {
        Planet found = null;
        for (int s = 0; s < 30 && found == null; s++) {
            for (int o = 0; o < 20; o++) {
                Planet p = planet(s, o);
                if (p.properties().type() == PlanetType.GAS_GIANT) {
                    found = p;
                    break;
                }
            }
        }
        assertNotNull(found, "expected at least one gas giant across the sample");
        PlanetProperties p = found.properties();
        assertEquals(PlanetSurface.GASEOUS, p.surface());
        assertEquals(0.0, p.waterCoverage());
        assertEquals(0.0, p.lifeLevel(), 1e-9);
        assertEquals(0.0, p.terrainRoughness(), 1e-9);
        assertEquals(AtmosphereType.GASEOUS, p.atmosphere());
    }

    @Test
    void habitablePlanetsHavePositiveLifeAndVegetation() {
        Planet found = null;
        for (int s = 0; s < 60 && found == null; s++) {
            for (int o = 0; o < 15; o++) {
                Planet p = planet(s, o);
                if (p.properties().isHabitable()) {
                    found = p;
                    break;
                }
            }
        }
        assertNotNull(found, "expected at least one habitable planet across the sample");
        PlanetProperties p = found.properties();
        assertTrue(p.lifeLevel() > 0.0, "habitable planet should have life");
        assertTrue(p.vegetationDensity() > 0.0, "habitable planet should have vegetation");
        assertNotEquals(AtmosphereType.NONE, p.atmosphere());
    }

    @Test
    void subsystemSeedsAreDistinctPerDomain() {
        PlanetProperties p = planet(5, 3).properties();
        long t = p.terrainSeed(), b = p.biomeSeed(), o = p.oreSeed(),
                st = p.structureSeed(), v = p.vegetationSeed(), m = p.materialSeed();
                assertEquals(Seeds.subsystem(p.seed().value(), "terrain"), t);
        assertTrue(t != b && t != o && t != st && t != v && t != m,
                "terrain seed must differ from other subsystem seeds");
    }

    @Test
    void gasGiantDefinitionUsesGasGiantType() {
        Planet found = null;
        for (int s = 0; s < 30 && found == null; s++) {
            for (int o = 0; o < 20; o++) {
                Planet p = planet(s, o);
                if (p.properties().type() == PlanetType.GAS_GIANT) {
                    found = p;
                    break;
                }
            }
        }
        if (found != null) {
            assertEquals(PlanetType.GAS_GIANT, found.definition().type());
            assertEquals(found.definition().seed(), found.properties().seed());
        }
    }
}

