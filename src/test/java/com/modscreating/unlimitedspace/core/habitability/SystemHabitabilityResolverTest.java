package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.2 §13 — the bounded PHYSICS-AWARE resolver (MODEL 2: ±2 window, PAIR-A with
 * single-candidate fallback). Synthetic window tests drive the pure resolver directly;
 * integration tests prove the REC contract, immutability and determinism on real systems.
 */
class SystemHabitabilityResolverTest {

    private static List<Integer> resolve(int n, List<Integer> anchors0, boolean... valid) {
        return SystemHabitabilityResolver.resolve(n, anchors0, valid);
    }

    /* ------------------------------------------------- 1–3: exact anchor & window edges */

    @Test
    void exactAnchorValidIsSelected() {
        assertEquals(List.of(3), resolve(6, List.of(3), false, false, false, true, false, false));
        assertEquals(List.of(0), resolve(3, List.of(0), true, false, false));
    }

    @Test
    void invalidAnchorFallsToTheValidNeighbor() {
        assertEquals(List.of(4), resolve(6, List.of(3), false, false, false, false, true, false));
        // Nearest valid wins when the anchor itself is dead.
        assertEquals(List.of(2), resolve(6, List.of(3), false, false, true, false, true, false));
    }

    @Test
    void candidatesBeyondThePlusMinusTwoWindowAreNeverSelected() {
        // Valid planets at distance 3+ from the anchor must be invisible to the resolver.
        assertEquals(List.of(), resolve(8, List.of(3), true, false, false, false, false, false, true, false));
        assertEquals(List.of(5), resolve(8, List.of(3), false, false, false, false, false, true, false, false));
        assertEquals(List.of(1), resolve(8, List.of(3), false, true, false, false, false, false, false, true));
    }

    /* ------------------------------------------------- 4–7: PAIR-A, fallback, nearest, tie */

    @Test
    void validAdjacentPairInsideTheWindowWins() {
        // Two-position anchor {2,3}: both members valid at the anchor → exact pair selected.
        assertEquals(List.of(2, 3), resolve(6, List.of(2, 3),
                false, false, true, true, false, false));
        // Pair shifted by one is still inside ±2 and wins over any single.
        assertEquals(List.of(3, 4), resolve(6, List.of(2, 3),
                false, false, false, true, true, false));
        assertEquals(List.of(1, 2), resolve(6, List.of(2, 3),
                false, true, true, false, false, false));
    }

    @Test
    void pairUnavailableFallsBackToTheNearestSingle() {
        assertEquals(List.of(4), resolve(6, List.of(2, 3),
                false, false, false, false, true, false));
        assertEquals(List.of(1), resolve(6, List.of(2, 3),
                false, true, false, false, false, false));
        assertEquals(List.of(), resolve(6, List.of(2, 3),
                false, false, false, false, false, false));
    }

    @Test
    void nearestValidPlanetToTheAnchorWins() {
        assertEquals(List.of(4), resolve(7, List.of(3),
                false, false, false, false, true, true, true));
        assertEquals(List.of(2), resolve(7, List.of(3),
                false, false, true, false, false, false, true));
    }

    @Test
    void equalDistanceTieBreaksToTheLowerOrbitIndex() {
        assertEquals(List.of(1), resolve(6, List.of(3),
                false, true, false, false, false, true));
        assertEquals(List.of(0), resolve(5, List.of(2),
                true, false, false, false, true));
        // Pair tie: two equally distant valid pairs inside the window → lower first orbitIndex.
        // Anchor {3,4} → window [1..6]; pairs (1,2) and (5,6) both sit at distance 2.
        assertEquals(List.of(1, 2), resolve(7, List.of(3, 4),
                false, true, true, false, false, true, true));
    }

    /* ------------------------------------------------- 8–9: P3 window & P4 emptiness */

    @Test
    void pattern3NeverSearchesBeyondThePlusMinusTwoWindow() {
        // Anchor {0}: orbit 3 is at distance 3 → invisible.
        assertEquals(List.of(0), resolve(6, List.of(0), true, false, false, false, false, false));
        assertEquals(List.of(2), resolve(6, List.of(0), false, false, true, false, false, false));
        assertEquals(List.of(), resolve(6, List.of(0), false, false, false, true, true, true));
    }

    @Test
    void pattern4AlwaysReturnsEmpty() {
        assertEquals(List.of(), resolve(6, List.of()));
        assertEquals(List.of(), resolve(1, List.of()));
        // Degenerate inputs never blow up.
        assertEquals(List.of(), resolve(0, List.of(0), true));
        assertEquals(List.of(), resolve(3, List.of(0)));
    }
    /* ------------------------------------------------- 14–15: structural invariants */

    @Test
    void neverMoreThanTwoAndTwoMeansAdjacent() {
        for (int n = 1; n <= 8; n++) {
            int combos = 1 << n;
            for (int mask = 0; mask < combos; mask++) {
                boolean[] valid = new boolean[n];
                for (int i = 0; i < n; i++) valid[i] = (mask & (1 << i)) != 0;
                for (int a = 0; a < n; a++) {
                    List<Integer> r = SystemHabitabilityResolver.resolve(n, List.of(a), valid);
                    assertTrue(r.size() <= 2, "max 2: " + r);
                    if (r.size() == 2) {
                        assertEquals(1, Math.abs(r.get(0) - r.get(1)), "adjacent: " + r);
                    }
                    for (int idx : r) assertTrue(valid[idx], "only valid worlds: " + idx);
                }
                if (n >= 2) {
                    List<Integer> r = SystemHabitabilityResolver.resolve(n, List.of(0, 1), valid);
                    assertTrue(r.size() <= 2, "max 2: " + r);
                    if (r.size() == 2) {
                        assertEquals(1, Math.abs(r.get(0) - r.get(1)), "adjacent: " + r);
                    }
                    for (int idx : r) assertTrue(valid[idx], "only valid worlds: " + idx);
                }
            }
        }
    }

    /* ------------------------------------------------- 10: REC contract on real systems */

    @Test
    void everySelectedCandidatePassesTheRecValidator() {
        Galaxy galaxy = Galaxy.from(4242L);
        int checked = 0;
        for (int s = 0; s < 2000; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            for (int idx : r.candidateOrbitIndexes()) {
                Planet planet = system.getPlanet(idx);
                assertTrue(HabitabilityValidator.isPhysicallyHabitable(
                                HabitabilityProfile.ofPlanet(planet.properties())),
                        "selected candidate must pass REC: " + planet.id());
                checked++;
            }
        }
        assertTrue(checked > 0, "expected selected candidates across 2000 systems, got " + checked);
    }

    /* ------------------------------------------------- 11–12: no mutation of anything */

    @Test
    void resolutionNeverMutatesPlanetPropertiesOrOrbits() {
        Galaxy galaxy = Galaxy.from(1357L);
        for (int s = 0; s < 300; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            int n = system.planetCount();
            PlanetProperties[] before = new PlanetProperties[n];
            PlanetThermal[] thermalBefore = new PlanetThermal[n];
            double[] auBefore = new double[n];
            for (int o = 0; o < n; o++) {
                before[o] = system.getPlanet(o).properties();
                thermalBefore[o] = before[o].thermal();
                auBefore[o] = thermalBefore[o].orbitAU();
            }
            SystemHabitability.of(system);   // the resolution pass under test
            for (int o = 0; o < n; o++) {
                PlanetProperties after = system.getPlanet(o).properties();
                assertEquals(before[o], after, "PlanetProperties must never be modified: orbit " + o);
                assertEquals(thermalBefore[o], after.thermal(),
                        "thermal context must never be modified: orbit " + o);
                assertEquals(auBefore[o], after.thermal().orbitAU(), 1e-12,
                        "AU must never be modified: orbit " + o);
            }
        }
    }

    /* ------------------------------------------------- 13: determinism */

    @Test
    void resolutionIsDeterministicAcrossRepeatedCalls() {
        Galaxy galaxy = Galaxy.from(8642L);
        for (int s = 0; s < 300; s++) {
            StarSystem a = galaxy.getStarSystem(galaxy.systemId(s));
            StarSystem b = galaxy.getStarSystem(galaxy.systemId(s));
            assertEquals(SystemHabitability.of(a), SystemHabitability.of(b),
                    "repeated evaluation must return the same result: system " + s);
        }
        // The pure resolver itself is order-independent too.
        boolean[] valid = {true, false, true, true, false, true};
        assertEquals(SystemHabitabilityResolver.resolve(6, List.of(2, 3), valid),
                SystemHabitabilityResolver.resolve(6, List.of(2, 3), valid));
    }
}