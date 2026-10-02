package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R23 (T-1..T-3): the canonical temperature (PHASE 1 Kelvin) is the hard authority for the
 * climate identity and the planet's color theme. A cryogenic world can never draw a warm
 * climate or a hot-dominant palette, and the local climate field is a BOUNDED variation
 * around the planetary mean.
 */
@Tag("worldgen")
class R23ClimateCoherenceTest {

    private static PlanetPhysicalProfile profile(double temp01, double hum, double water) {
        return new PlanetPhysicalProfile(temp01, null, hum, 0.6, null,
                water, water * 0.5, 0.5, 0.5, 0.2, 0.3,
                0.3, 0.1, 0.4, 0.3, 0.2, 0.35, 0.1, 0.2, 0.5, 0.5, null, null);
    }

    @Test
    void cryogenicWorldsNeverDrawWarmClimates() {
        for (long seed = 1; seed <= 60; seed++) {
            PlanetPhysicalProfile p = profile(0.06, 0.55, 0.4);   // ~41 K cryogenic
            ClimateArchetype arch = ClimateArchetypeSelector.create(seed * 7919L, p);
            assertTrue(arch == ClimateArchetype.FROZEN || arch == ClimateArchetype.EXTREME_COLD
                            || arch == ClimateArchetype.COLD,
                    "seed " + seed + " drew a warm climate for a cryogenic world: " + arch);
        }
    }

    @Test
    void extremeHotWorldsNeverDrawFrozenClimates() {
        for (long seed = 1; seed <= 60; seed++) {
            PlanetPhysicalProfile p = profile(0.92, 0.1, 0.02);   // molten world
            ClimateArchetype arch = ClimateArchetypeSelector.create(seed * 104729L, p);
            assertFalse(arch.isFrozenDominant(),
                    "seed " + seed + " drew a frozen climate for a molten world: " + arch);
        }
    }

    @Test
    void climateSelectionIsDeterministic() {
        PlanetPhysicalProfile p = profile(0.45, 0.5, 0.4);
        assertEquals(ClimateArchetypeSelector.create(12345L, p),
                ClimateArchetypeSelector.create(12345L, p));
    }

    @Test
    void localClimateStaysNearThePlanetaryMean() {
        // Frozen world: the local temperature field is a BOUNDED variation of 0.06, so a
        // large temperate patch is impossible.
        PlanetPhysicalProfile frozen = profile(0.06, 0.55, 0.4);
        PlanetClimateProfile climate = PlanetClimateProfile.create(42L, frozen);
        int temperate = 0, total = 0;
        double sum = 0.0;
        for (int x = -6000; x <= 6000; x += 97) {
            for (int z = -6000; z <= 6000; z += 997) {
                double t = climate.temperatureAt(x, z);
                sum += t;
                if (t > 0.35) temperate++;
                total++;
            }
        }
        double mean = sum / total;
        assertEquals(frozen.temperature(), mean, 0.08,
                "the local climate must average the canonical planetary temperature");
        assertTrue(temperate <= total * 0.02,
                "a cryogenic world must not contain large temperate areas: "
                        + (100.0 * temperate / total) + "%");
    }

    @Test
    void moltenWorldsHaveNoLargeColdAreas() {
        PlanetPhysicalProfile molten = profile(0.92, 0.1, 0.02);
        PlanetClimateProfile climate = PlanetClimateProfile.create(77L, molten);
        int cold = 0, total = 0;
        for (int x = -6000; x <= 6000; x += 97) {
            for (int z = -6000; z <= 6000; z += 997) {
                if (climate.temperatureAt(x, z) < 0.65) cold++;
                total++;
            }
        }
        assertTrue(cold <= total * 0.02,
                "a molten world must not contain large cold areas: " + (100.0 * cold / total) + "%");
    }

    @Test
    void temperateWorldKeepsAHealthyVariation() {
        PlanetPhysicalProfile temperate = profile(0.48, 0.55, 0.4);
        PlanetClimateProfile climate = PlanetClimateProfile.create(99L, temperate);
        double min = 1.0, max = 0.0;
        for (int x = -6000; x <= 6000; x += 211) {
            double t = climate.temperatureAt(x, 0);
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        assertTrue(max - min >= 0.05,
                "a temperate world must keep real climate variation: " + (max - min));
    }

    @Test
    void themeFollowsTheCanonicalTemperatureBand() {
        // A cryogenic world can never dominate with a hot palette, an inferno never with ice.
        for (long seed = 1; seed <= 60; seed++) {
            PlanetPhysicalProfile frozen = profile(0.06, 0.55, 0.4);
            PlanetColorTheme coldTheme = PlanetColorTheme.select(TemperatureBand.of(frozen.temperature()),
                    ClimateArchetype.FROZEN, seed * 31L);
            assertTrue(coldTheme.fitsBand(TemperatureBand.FROZEN),
                    "seed " + seed + ": frozen world got an off-band theme " + coldTheme);

            PlanetPhysicalProfile molten = profile(0.92, 0.1, 0.02);
            PlanetColorTheme hotTheme = PlanetColorTheme.select(TemperatureBand.of(molten.temperature()),
                    ClimateArchetype.EXTREME_HOT, seed * 31L);
            assertTrue(hotTheme.fitsBand(TemperatureBand.INFERNO),
                    "seed " + seed + ": molten world got an off-band theme " + hotTheme);
        }
    }

    @Test
    void frozenBandKeepsRealVariety() {
        PlanetColorTheme a = PlanetColorTheme.select(TemperatureBand.FROZEN, ClimateArchetype.FROZEN, 7L);
        PlanetColorTheme b = PlanetColorTheme.select(TemperatureBand.FROZEN, ClimateArchetype.HYPERARID, 7777L);
        PlanetColorTheme c = PlanetColorTheme.select(TemperatureBand.FROZEN, ClimateArchetype.EXTREME_COLD, 424242L);
        assertTrue(a.fitsBand(TemperatureBand.FROZEN) && b.fitsBand(TemperatureBand.FROZEN)
                && c.fitsBand(TemperatureBand.FROZEN));
    }
}
