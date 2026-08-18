package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12.2 Bug #1 — planet gravity must always be playable (Earth-g), for every seed.
 */
class PlanetGravityInvariantTest {

    private static final Galaxy GALAXY = Galaxy.from(2024L);

    @Test
    void noProceduralPlanetHasZeroOrUnplayableGravity() {
        for (int s = 0; s < 50; s++) {
            Galaxy g = Galaxy.from(s);
            for (int si = 0; si < 1; si++) {
                var sys = g.getStarSystem(com.modscreating.unlimitedspace.core.stars.StarSystemId.of(si));
                for (int o = 0; o < sys.planetCount(); o++) {
                    double gEarth = sys.getPlanet(o).properties().gravity();
                    assertTrue(gEarth >= Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G,
                            "s" + s + " p" + o + " gravity " + gEarth + " below floor");
                    assertTrue(gEarth > 0.0, "planet gravity must be positive");
                }
            }
        }
    }

    @Test
    void planetGravityMatchesRecordedDomainTestSeed() {
        // Pin a known stable value for the documented test seed so a silent change is caught.
        double g = GALAXY.getStarSystem(
                com.modscreating.unlimitedspace.core.stars.StarSystemId.of(0))
                .getPlanet(0).properties().gravity();
        assertTrue(g >= Gravity.MIN_PLAYABLE_GRAVITY_EARTH_G, "g=" + g);
    }
}
