package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.1 — MOON habitability: parent gate, per-moon independent 30% lottery, the shared
 * canonical physical validation and strict determinism.
 */
class MoonHabitabilityTest {

    @Test
    void nonHabitableParentsNeverProduceHabitableMoons() {
        Galaxy galaxy = Galaxy.from(31337L);
        int checkedMoons = 0;
        for (int s = 0; s < 2500 && checkedMoons < 400; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                if (r.isActuallyHabitable(o) || planet.moonCount() == 0) continue;
                for (Moon moon : planet.moons()) {
                    MoonHabitability.MoonResult mr = MoonHabitability.of(r, planet.id(), moon);
                    assertFalse(mr.parentHabitable(), "parent is not actually habitable");
                    assertFalse(mr.lotterySelected(), "no lottery is even run for an ineligible parent");
                    assertFalse(mr.actuallyHabitable(),
                            "a moon around a non-habitable planet is NEVER habitable (parent gate)");
                    assertFalse(mr.life().mobsEnabled());
                    checkedMoons++;
                }
            }
        }
        assertTrue(checkedMoons > 0, "expected moons around non-habitable planets in the sample");
    }

    @Test
    void moonsOfHabitableParentsHaveAnIndependentThirtyPercentLottery() {
        Galaxy galaxy = Galaxy.from(20250922L);
        int moons = 0;
        int selected = 0;
        int actual = 0;
        for (int s = 0; s < 12_000 && moons < 600; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                if (!r.isActuallyHabitable(o)) continue;
                for (Moon moon : planet.moons()) {
                    MoonHabitability.MoonResult mr = MoonHabitability.of(r, planet.id(), moon);
                    moons++;
                    if (mr.lotterySelected()) selected++;
                    if (mr.actuallyHabitable()) actual++;
                    // Invariant: actual = lottery ∧ physical — an invalid moon is never forced.
                    assertEquals(mr.lotterySelected() && mr.physical().physicallyHabitable(),
                            mr.actuallyHabitable(),
                            "actual habitability must be lottery ∧ physical validation");
                }
            }
        }
        assertTrue(moons >= 100, "expected enough moons of habitable parents, got " + moons);
        // ACT 2.1: the LOTTERY itself is ~30% (actual habitability stays much lower because
        // the strict physical validation is deliberately unchanged).
        double share = selected / (double) moons;
        assertTrue(Math.abs(share - 0.30) < 0.10,
                "moon candidate lottery must be ~30% per moon, got " + share);
    }

    @Test
    void sameSeedSameResult() {
        Galaxy galaxy = Galaxy.from(5L);
        StarSystem a = galaxy.getStarSystem(galaxy.systemId(11));
        StarSystem b = galaxy.getStarSystem(galaxy.systemId(11));
        SystemHabitability.Result ra = SystemHabitability.of(a);
        SystemHabitability.Result rb = SystemHabitability.of(b);
        for (int o = 0; o < a.planetCount(); o++) {
            Planet planet = a.getPlanet(o);
            for (Moon moon : planet.moons()) {
                assertEquals(MoonHabitability.of(ra, planet.id(), moon),
                        MoonHabitability.of(rb, planet.id(), moon));
            }
        }
    }
}
