package com.modscreating.unlimitedspace.core.worldgen.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R21 planetary-climate tests: planet-level identity + spatially coherent climate fields.
 * A climate is NEVER {@code random(x,z)}: neighbouring thousands of blocks must agree.
 */
class PlanetClimateProfileTest {

    private static PlanetClimateProfile profile(long seed) {
        return PlanetClimateProfile.create(seed, null);
    }

    @Test
    void climateIsDeterministic() {
        PlanetClimateProfile a = profile(4242L);
        PlanetClimateProfile b = profile(4242L);
        assertEquals(a.archetype(), b.archetype());
        assertEquals(a.temperatureAt(123, -456), b.temperatureAt(123, -456), 1e-12);
        assertEquals(a.humidityAt(123, -456), b.humidityAt(123, -456), 1e-12);
        assertEquals(a.aridityAt(123, -456), b.aridityAt(123, -456), 1e-12);
    }

    @Test
    void climateIsSpatiallyCoherent() {
        // Neighbouring blocks share the same climate; climate zones are thousands of blocks.
        long seed = 777L;
        PlanetClimateProfile p = profile(seed);
        double shortDrift = 0.0, longDrift = 0.0;
        int n = 0;
        for (int i = 0; i < 4096; i += 64) {
            double t = p.temperatureAt(i, 0);
            shortDrift += Math.abs(t - p.temperatureAt(i + 64, 0));
            longDrift += Math.abs(t - p.temperatureAt(i + 2048, 0));
            n++;
        }
        shortDrift /= n;
        longDrift /= n;
        assertTrue(shortDrift < 0.05,
                "climate must not change per block: " + shortDrift);
        assertTrue(longDrift > shortDrift,
                "long-range climate variation must dominate short-range");
    }

    @Test
    void climateValuesStayBounded() {
        PlanetClimateProfile p = profile(99L);
        for (int x = -4096; x <= 4096; x += 61) {
            for (int z = -4096; z <= 4096; z += 97) {
                assertTrue(p.temperatureAt(x, z) >= 0.0 && p.temperatureAt(x, z) <= 1.0);
                assertTrue(p.humidityAt(x, z) >= 0.0 && p.humidityAt(x, z) <= 1.0);
                assertTrue(p.aridityAt(x, z) >= 0.0 && p.aridityAt(x, z) <= 1.0);
            }
        }
    }

    @Test
    void differentPlanetsHaveDifferentClimates() {
        PlanetPhysicalProfileForTest base = new PlanetPhysicalProfileForTest(0.5, 0.5, 0.4);
        boolean differ = false;
        for (long seed = 1; seed <= 60; seed++) {
            if (profile(seed * 7919L, base).archetype() != profile(1L, base).archetype()) {
                differ = true;
                break;
            }
        }
        assertTrue(differ, "different planets must be able to have different climate identities");
    }

    private static PlanetClimateProfile profile(long seed, PlanetPhysicalProfileForTest p) {
        return PlanetClimateProfile.create(seed, p.toProfile());
    }

    /** Small helper that builds a physical profile (the 23-arg ctor is unwieldy in tests). */
    private static final class PlanetPhysicalProfileForTest {
        private final double temp;
        private final double hum;
        private final double water;
        PlanetPhysicalProfileForTest(double temp, double hum, double water) {
            this.temp = temp;
            this.hum = hum;
            this.water = water;
        }
        com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile toProfile() {
            return new com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile(
                    temp, null, hum, 0.6, null, water, water * 0.5, 0.5, 0.5, 0.2, 0.3,
                    0.3, 0.1, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
        }
    }

    @Test
    void frozenArchetypesAreRecognized() {
        assertTrue(ClimateArchetype.FROZEN.isFrozenDominant());
        assertTrue(ClimateArchetype.EXTREME_COLD.isFrozenDominant());
        assertTrue(ClimateArchetype.HYPERARID.isDryDominant());
        assertFalse(ClimateArchetype.TEMPERATE.isFrozenDominant());
        assertFalse(ClimateArchetype.TEMPERATE.isDryDominant());
        assertNotNull(ClimateArchetype.OCEANIC.label());
    }
}
