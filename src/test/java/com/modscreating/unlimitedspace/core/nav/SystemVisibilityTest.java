package com.modscreating.unlimitedspace.core.nav;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R22: fog-of-war visibility decision logic. The radius must stay configurable
 * so future "visibility booster" upgrades can raise it at runtime without
 * touching the decision rules.
 */
class SystemVisibilityTest {

    private static final int CURRENT = 10;
    private static final Set<Integer> VISITED = Set.of(7, 42);

    // ---- default radius -----------------------------------------------------

    @Test
    void defaultRadiusIs1600Ly() {
        assertEquals(1600.0, new SystemVisibility().radiusLy(), 1e-9);
    }

    @Test
    void systemWithinRadiusIsKnown() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.isKnown(11, CURRENT, Set.of(), 1000.0));
        assertTrue(v.isKnown(99, CURRENT, Set.of(), 1599.9));
    }

    @Test
    void systemExactlyAtRadiusIsKnown() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.isKnown(11, CURRENT, Set.of(), 1600.0));
    }

    @Test
    void systemBeyondRadiusIsUnknown() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.isKnown(11, CURRENT, Set.of(), 1600.1));
        assertFalse(v.isKnown(11, CURRENT, Set.of(), 50000.0));
    }

    @Test
    void unknownDistanceMeansUnknownSystem() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.isKnown(11, CURRENT, Set.of(), Double.NaN));
        assertFalse(v.isKnown(11, CURRENT, Set.of(), Double.POSITIVE_INFINITY));
    }

    // ---- current / visited override the radius ------------------------------

    @Test
    void currentSystemIsAlwaysKnownEvenFarAway() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.isKnown(CURRENT, CURRENT, Set.of(), Double.MAX_VALUE));
    }

    @Test
    void visitedSystemIsKnownAtAnyDistance() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.isKnown(7, CURRENT, VISITED, 90000.0));
        assertTrue(v.isKnown(42, CURRENT, VISITED, Double.NaN));
    }

    @Test
    void neverVisitedFarSystemStaysUnknown() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.isKnown(5, CURRENT, VISITED, 3000.0));
    }

    // ---- R22b: visibility != reachability ------------------------------------

    @Test
    void visitedFarSystemIsKnownButNotReachable() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.isKnown(42, CURRENT, VISITED, 90000.0));      // always visible
        assertFalse(v.canTravelTo(42, CURRENT, 90000.0));          // but no direct flight
    }

    @Test
    void reachableWhenInsideRadius() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.canTravelTo(11, CURRENT, 1600.0));
        assertFalse(v.canTravelTo(11, CURRENT, 1601.0));
    }

    @Test
    void currentSystemIsAlwaysReachable() {
        SystemVisibility v = new SystemVisibility();
        assertTrue(v.canTravelTo(CURRENT, CURRENT, Double.MAX_VALUE));
    }

    @Test
    void raisingRadiusMakesVisitedFarSystemReachableAgain() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.canTravelTo(7, CURRENT, 4000.0));
        v.setRadiusLy(5000.0);
        assertTrue(v.canTravelTo(7, CURRENT, 4000.0));
    }

    @Test
    void nullVisitedSetIsTolerated() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.isKnown(11, -1, null, 3000.0));
        assertTrue(v.isKnown(11, -1, null, 100.0));
    }

    // ---- visibility boosters: the radius is runtime-configurable ------------

    @Test
    void raisingRadiusMakesFarSystemsKnown() {
        SystemVisibility v = new SystemVisibility();
        assertFalse(v.isKnown(11, CURRENT, List.of(), 4000.0)); // unknown at 1600 ly
        v.setRadiusLy(5000.0);                                   // booster applied
        assertEquals(5000.0, v.radiusLy(), 1e-9);
        assertTrue(v.isKnown(11, CURRENT, List.of(), 4000.0));   // now visible
    }

    @Test
    void loweringRadiusHidesPreviouslyVisibleSystems() {
        SystemVisibility v = new SystemVisibility(5000.0);
        assertTrue(v.withinRadius(4000.0));
        v.setRadiusLy(2000.0);
        assertFalse(v.withinRadius(4000.0));
    }

    // ---- radius clamping ----------------------------------------------------

    @Test
    void negativeRadiusClampsToZero() {
        SystemVisibility v = new SystemVisibility(-50.0);
        assertEquals(0.0, v.radiusLy(), 1e-9);
        // only exact distance 0 (the system you stand in) passes the radius check
        assertTrue(v.withinRadius(0.0));
        assertFalse(v.withinRadius(0.1));
    }

    @Test
    void nonFiniteRadiusClampsToZero() {
        assertEquals(0.0, new SystemVisibility(Double.NaN).radiusLy(), 1e-9);
        assertEquals(0.0, new SystemVisibility(Double.POSITIVE_INFINITY).radiusLy(), 1e-9);
    }
}
