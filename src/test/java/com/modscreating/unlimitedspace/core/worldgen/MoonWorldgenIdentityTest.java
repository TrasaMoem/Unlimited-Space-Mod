package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 (I) — MOON WORLDGEN IDENTITY: a moon must consume its OWN properties, never the
 * parent planer's. Two moons of the same parent must not share a worldgen profile, and the
 * moon profile must differ from the parent profile.
 */
@Tag("worldgen")
class MoonWorldgenIdentityTest {

    private static final long WORLD_SEED = 42424242L;

    private static Planet findParentWithTwoMoons() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int s = 0; s < 400; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                Planet p = system.getPlanet(o);
                if (p.moonCount() >= 2) return p;
            }
        }
        return null;
    }

    @Test
    void twoMoonsOfTheSameParentHaveDistinctWorldgenIdentities() {
        Planet parent = findParentWithTwoMoons();
        assertNotNull(parent, "expected a planet with >= 2 moons in the sample");

        MoonWorldgenProfile m0 = MoonWorldgenProfile
                .view(MoonId.of(parent.id(), 0), WORLD_SEED);
        MoonWorldgenProfile m1 = MoonWorldgenProfile
                .view(MoonId.of(parent.id(), 1), WORLD_SEED);

        assertNotEquals(m0.worldgen(), m1.worldgen(),
                "moons of the same parent must NOT share one worldgen profile");
        assertNotEquals(m0.worldgen().properties().seed().value(),
                m1.worldgen().properties().seed().value(),
                "every moon worldgen profile consumes its OWN moon seed");
        assertNotEquals(m0.worldgen().terrainSeed(), m1.worldgen().terrainSeed());
        assertNotEquals(m0.worldgen().materialSeed(), m1.worldgen().materialSeed());
    }

    @Test
    void moonProfileIsNotTheParentProfileAndUsesMoonPhysics() {
        Planet parent = findParentWithTwoMoons();
        assertNotNull(parent);
        Moon moon = parent.moon(0);
        MoonWorldgenProfile view = MoonWorldgenProfile.view(moon.id(), WORLD_SEED);
        PlanetWorldgenProfile parentProfile =
                PlanetWorldgenProfile.from(parent.id(), WORLD_SEED);

        assertNotEquals(parentProfile.properties().seed().value(),
                view.worldgen().properties().seed().value(),
                "the moon profile must consume the MOON seed, not the parent seed");
        assertEquals(moon.properties().temperature(),
                view.worldgen().properties().temperature(), 1e-9,
                "the moon worldgen temperature is the MOON's own thermal value");
        assertEquals(moon.properties().surface(), view.worldgen().properties().surface());
        assertEquals(moon.properties().waterCoverage(),
                view.worldgen().properties().waterCoverage(), 1e-9);
        assertEquals(moon.properties().gravity(), view.worldgen().properties().gravity(), 1e-9);
    }

    @Test
    void nonHabitableMoonsProjectZeroOrganicEcologyIntensity() {
        Planet parent = findParentWithTwoMoons();
        assertNotNull(parent);
        for (int i = 0; i < parent.moonCount(); i++) {
            Moon moon = parent.moon(i);
            MoonWorldgenProfile view = MoonWorldgenProfile.view(moon.id(), WORLD_SEED);
            if (!view.actuallyHabitable()) {
                assertEquals(0.0, view.worldgen().properties().lifeLevel(), 1e-9,
                        "PHYSICS → HABITABILITY → LIFE: a non-habitable moon carries zero life");
                assertEquals(0.0, view.worldgen().properties().vegetationDensity(), 1e-9);
                assertFalse(view.life().vegetationPermitted());
                assertFalse(view.life().structureEligible());
                assertFalse(view.life().mobsEnabled());
            } else {
                assertTrue(view.worldgen().properties().lifeLevel() > 0.0);
                assertTrue(view.life().vegetationPermitted());
                assertTrue(view.life().structureEligible());
            }
        }
    }

    @Test
    void moonWorldgenViewIsDeterministic() {
        Planet parent = findParentWithTwoMoons();
        assertNotNull(parent);
        assertEquals(MoonWorldgenProfile.view(MoonId.of(parent.id(), 0), WORLD_SEED),
                MoonWorldgenProfile.view(MoonId.of(parent.id(), 0), WORLD_SEED));
    }
}
