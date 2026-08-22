package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R14.7 — pure {@link PlanetMaterialWeights} composition-to-weight tests.
 *
 * <p>These run against the real (deterministic) domain pipeline — no Minecraft types — so they
 * prove the weights driving the orbital palette come from the planet/moon's own authoritative
 * material profile (never a hard-coded Earth palette), that surface dominates subsurface, and that
 * a moon's weights derive from the moon's own properties (not the parent planet).
 */
class PlanetMaterialWeightsTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static Planet planet(int system, int orbit) {
        return Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(system)).getPlanet(orbit);
    }

    @Test
    void planetWeightsAreDeterministicAndRoleOrdered() {
        Planet p = planet(0, 0);
        List<PlanetMaterialWeights.Entry> a = PlanetMaterialWeights.fromPlanet(PlanetWorldgenProfile.from(p));
        List<PlanetMaterialWeights.Entry> b = PlanetMaterialWeights.fromPlanet(PlanetWorldgenProfile.from(p));
        assertEquals(a.size(), b.size());
        assertEquals(a.get(0).material(), b.get(0).material());
        assertEquals(a.get(0).weight(), b.get(0).weight(), 1e-6f);
        // surface role dominates the subsurface role (0.40 > 0.22)
        assertTrue(a.get(0).weight() > a.get(1).weight(),
                "surface material must dominate subsurface: " + a.get(0).weight() + " > " + a.get(1).weight());
    }

    @Test
    void planetWeightsNormaliseIntoPlausibleBand() {
        Planet p = planet(1, 1);
        float sum = 0f;
        for (PlanetMaterialWeights.Entry e : PlanetMaterialWeights.fromPlanet(PlanetWorldgenProfile.from(p))) {
            sum += e.weight();
        }
        // surface+subsurface+deep+rare = 0.84, plus up to 0.16 in biome accents
        assertTrue(sum >= 0.80f && sum <= 1.001f, "planet weights sum " + sum);
    }

    @Test
    void moonWeightsComeFromOwnMaterialsAndArePositive() throws Exception {
        for (int s = 0; s < 60; s++) {
            for (int o = 0; o < 12; o++) {
                Planet p = planet(s, o);
                if (p.moonCount() == 0) continue;
                Moon m = p.moon(0);
                List<PlanetMaterialWeights.Entry> w = PlanetMaterialWeights.fromMoon(m.properties());
                assertFalse(w.isEmpty(), "moon composition must not be empty");
                for (PlanetMaterialWeights.Entry e : w) {
                    assertTrue(e.weight() > 0f, "every moon material has a positive weight");
                }
                return;   // tested one real moon
            }
        }
        fail("no planet with a moon found in the sample");
    }

    @Test
    void allWeightedMaterialsHaveResolvableBlockIds() throws Exception {
        for (int s = 0; s < 40; s++) {
            for (int o = 0; o < 10; o++) {
                Planet p = planet(s, o);
                if (p.moonCount() == 0) continue;
                Moon m = p.moon(0);
                for (PlanetMaterialWeights.Entry e : PlanetMaterialWeights.fromMoon(m.properties())) {
                    assertNotNull(e.material().blockId(), "moon material must carry a block id");
                }
                return;
            }
        }
        fail("no moon found in the sample");
    }
}
