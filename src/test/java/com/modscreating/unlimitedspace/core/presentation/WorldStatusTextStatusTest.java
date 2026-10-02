package com.modscreating.unlimitedspace.core.presentation;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.HabitabilityProfile;
import com.modscreating.unlimitedspace.core.habitability.HabitabilityValidator;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.3 — the navigation/UI STATUS semantics.
 *
 * <p>The UI must clearly distinguish:
 * <ul>
 *   <li>{@code Bio Potential} — the legacy {@code PlanetProperties.lifeLevel()} lottery (useful,
 *       but NOT a habitability flag; it may stay high on a STERILE world),</li>
 *   <li>{@code Habitability} — the ACTUAL verdict from the canonical system selection
 *       ({@code SystemHabitability.isActuallyHabitable}) and, for moons,
 *       {@code MoonHabitability.of(...).actuallyHabitable()}.</li>
 * </ul>
 *
 * <p>Physical suitability alone never displays HABITABLE; P4 worlds stay STERILE; an actually
 * selected P1/P2/P3 world displays HABITABLE; a moon is HABITABLE exactly when its own canonical
 * moon result says so.
 */
class WorldStatusTextStatusTest {

    /** The ACT 2 report world seed (deterministic reference galaxy). */
    private static final long WORLD_SEED = 20260000L;

    @Test
    void habitabilityTextIsExactlyTheTwoCanonicalWords() {
        assertEquals(WorldStatusText.HABITABLE, WorldStatusText.habitabilityText(true));
        assertEquals(WorldStatusText.STERILE, WorldStatusText.habitabilityText(false));
        assertEquals("HABITABLE", WorldStatusText.habitabilityText(true));
        assertEquals("STERILE", WorldStatusText.habitabilityText(false));
    }

    @Test
    void bioPotentialReadsTheLegacyLifeLevel() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int checked = 0;
        int nonZero = 0;
        for (int s = 0; s < 20; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            for (int o = 0; o < system.planetCount(); o++) {
                PlanetProperties p = system.getPlanet(o).properties();
                assertEquals(String.format(Locale.ROOT, "%.0f%%", p.lifeLevel() * 100.0),
                        WorldStatusText.bioPotentialText(p),
                        "Bio Potential must be read from PlanetProperties.lifeLevel()");
                assertTrue(WorldStatusText.bioPotentialText(p).endsWith("%"));
                if (p.lifeLevel() > 0.0) nonZero++;
                checked++;
            }
        }
        assertTrue(checked > 0, "the sample must cover real planets");
        assertTrue(nonZero > 0, "some planets carry a non-zero bio potential");
    }

    @Test
    void planetHabitabilityIsTheCanonicalSystemSelection() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int actualCount = 0;
        int sterileCount = 0;
        for (int s = 0; s < 60 && actualCount < 3; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                boolean expected = result.isActuallyHabitable(o);
                assertEquals(expected, WorldStatusText.planetActuallyHabitable(WORLD_SEED, planet),
                        "planet " + planet.id()
                                + " must read SystemHabitability.isActuallyHabitable()");
                assertEquals(expected ? WorldStatusText.HABITABLE : WorldStatusText.STERILE,
                        WorldStatusText.habitabilityText(expected));
                if (expected) actualCount++;
                else sterileCount++;
            }
        }
        assertTrue(actualCount >= 1,
                "expected at least one actually habitable P1/P2/P3 world in the sample");
        assertTrue(sterileCount > 0, "everything the system did not select stays STERILE");
    }

    @Test
    void physicalSuitabilityAloneNeverDisplaysHabitable() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        boolean found = false;
        for (int s = 0; s < 800 && !found; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                if (result.isActuallyHabitable(o)) continue;
                boolean physical = HabitabilityValidator.isPhysicallyHabitable(
                        HabitabilityProfile.ofPlanet(planet.properties()));
                if (!physical) continue;
                // PHYSICALLY SUITABLE BUT NOT SELECTED: the UI must never say HABITABLE.
                boolean actual = WorldStatusText.planetActuallyHabitable(WORLD_SEED, planet);
                assertFalse(actual, "physical eligibility is NOT actual habitability");
                assertEquals(WorldStatusText.STERILE, WorldStatusText.habitabilityText(actual));
                found = true;
                break;
            }
        }
        assertTrue(found, "expected a physically suitable but unselected world in the sample");
    }

    @Test
    void p4WorldsStaySterileEvenWithBioPotential() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int p4Systems = 0;
        boolean found = false;
        for (int s = 0; s < 2000 && !found; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            if (result.pattern() != SystemHabitability.Pattern.FOUR) continue;
            p4Systems++;
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                assertFalse(result.isActuallyHabitable(o), "P4 never selects a world");
                assertFalse(WorldStatusText.planetActuallyHabitable(WORLD_SEED, planet),
                        "a P4 world must be STERILE: " + planet.id());
                PlanetProperties props = planet.properties();
                if (props.lifeLevel() > 0.0) {
                    // The label is STERILE, but Bio Potential keeps the legacy value —
                    // it is NOT forced to 0% just because the world is sterile.
                    assertNotEquals("0%", WorldStatusText.bioPotentialText(props),
                            "bio potential must not be forced to zero on sterile worlds");
                    found = true;
                }
            }
        }
        assertTrue(p4Systems > 0, "expected P4 systems in the sample");
        assertTrue(found, "expected a P4 world with STERILE habitability and non-zero bio potential");
    }

    @Test
    void moonHabitabilityIsTheCanonicalMoonResultNeverParentOrPhysicsAlone() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int moons = 0;
        boolean foundActualMoon = false;
        boolean foundParentHabitableButMoonSterile = false;
        boolean foundPhysicallyHabitableButSterile = false;
        for (int s = 0; s < 3000
                && !(foundActualMoon && foundParentHabitableButMoonSterile
                        && foundPhysicallyHabitableButSterile); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet parent = system.getPlanet(o);
                boolean parentActual = result.isActuallyHabitable(o);
                for (Moon moon : parent.moons()) {
                    MoonHabitability.MoonResult moonResult =
                            MoonHabitability.of(result, parent.id(), moon);
                    boolean expected = moonResult.actuallyHabitable();
                    assertEquals(expected,
                            WorldStatusText.moonActuallyHabitable(WORLD_SEED, parent, moon),
                            "moon " + moon.id()
                                    + " must read MoonHabitability.actuallyHabitable()");
                    assertEquals(expected ? WorldStatusText.HABITABLE : WorldStatusText.STERILE,
                            WorldStatusText.habitabilityText(expected));
                    moons++;
                    if (expected) foundActualMoon = true;
                    if (parentActual && !expected) foundParentHabitableButMoonSterile = true;
                    if (!expected && moon.properties().isHabitable()) {
                        foundPhysicallyHabitableButSterile = true;
                    }
                }
            }
        }
        assertTrue(moons > 0, "the sample must cover real moons");
        assertTrue(foundActualMoon,
                "expected at least one actually habitable moon in the sample");
        assertTrue(foundParentHabitableButMoonSterile,
                "a moon of a habitable parent may stay STERILE (never derived from the parent alone)");
        assertTrue(foundPhysicallyHabitableButSterile,
                "a physically suitable moon not selected by the 30% lottery must stay STERILE");
    }
}
