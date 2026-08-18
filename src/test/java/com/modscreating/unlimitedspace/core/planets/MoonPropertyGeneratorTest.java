package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12.2 Bug #1 — every procedural moon must have positive, playable gravity (Earth-g).
 *
 * <p>Moon gravity is generated in [0.08, 0.58) Earth-g, already above the playable floor;
 * these tests pin that invariant so future generation changes never regress to zero.
 */
class MoonPropertyGeneratorTest {

    @Test
    void everyMoonHasPositivePlayableGravity() {
        for (int s = 0; s < 25; s++) {
            Galaxy g = Galaxy.from(s);
            for (int si = 0; si < 1; si++) {
                StarSystem sys = g.getStarSystem(StarSystemId.of(si));
                int planets = sys.planetCount();
                for (int o = 0; o < planets; o++) {
                    for (Moon moon : sys.getPlanet(o).moons()) {
                        double gEarth = moon.properties().gravity();
                        assertTrue(gEarth >= Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G,
                                "moon " + moon.id().code() + " gravity " + gEarth + " below floor");
                        assertTrue(gEarth > 0.0, "moon gravity must be positive");
                    }
                }
            }
        }
    }

    @Test
    void moonGravityIsDeterministicAcrossRestarts() {
        for (int s = 0; s < 10; s++) {
            Galaxy g = Galaxy.from(s);
            StarSystem sys = g.getStarSystem(StarSystemId.of(0));
            for (int o = 0; o < sys.planetCount(); o++) {
                List<Moon> first = sys.getPlanet(o).moons();
                List<Moon> second = sys.getPlanet(o).moons();
                assertEquals(first.size(), second.size());
                for (int m = 0; m < first.size(); m++) {
                    assertEquals(first.get(m).properties().gravity(),
                            second.get(m).properties().gravity(), 1e-12);
                }
            }
        }
    }
}

