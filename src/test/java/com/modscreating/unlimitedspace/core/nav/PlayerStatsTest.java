package com.modscreating.unlimitedspace.core.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R22g: lifetime exploration statistics - recording and persistence round-trip. */
class PlayerStatsTest {

    @Test
    void emptyStats() {
        PlayerStats ps = new PlayerStats();
        assertEquals(0, ps.trips());
        assertEquals(0.0, ps.lyTraveled(), 1e-9);
        assertEquals(0.0, ps.fuelSpentKg(), 1e-9);
        assertEquals(0, ps.planetsVisited());
        assertEquals(0, ps.moonsVisited());
        assertEquals(0, ps.systemsVisitedCount());
    }

    @Test
    void recordTripAccumulates() {
        PlayerStats ps = new PlayerStats();
        ps.recordTrip(10, 1500.0, 500.0, false);   // planet surface
        ps.recordTrip(20, 3000.0, 1200.5, true);   // moon orbit
        assertEquals(2, ps.trips());
        assertEquals(4500.0, ps.lyTraveled(), 1e-9);
        assertEquals(1700.5, ps.fuelSpentKg(), 1e-9);
        assertEquals(1, ps.planetsVisited());
        assertEquals(1, ps.moonsVisited());
        assertEquals(2, ps.systemsVisitedCount());
        assertTrue(ps.hasVisited(10));
        assertTrue(ps.hasVisited(20));
        assertFalse(ps.hasVisited(30));
    }

    @Test
    void sameSystemCountsOnceInSetButEveryTrip() {
        PlayerStats ps = new PlayerStats();
        ps.recordTrip(7, 100.0, 50.0, false);
        ps.recordTrip(7, 200.0, 60.0, true);
        assertEquals(2, ps.trips());
        assertEquals(1, ps.systemsVisitedCount()); // set semantics
        assertEquals(300.0, ps.lyTraveled(), 1e-9);
    }

    @Test
    void unknownDistanceAndFuelAreIgnored() {
        PlayerStats ps = new PlayerStats();
        ps.recordTrip(5, Double.NaN, -3.0, false);
        assertEquals(1, ps.trips());
        assertEquals(0.0, ps.lyTraveled(), 1e-9);
        assertEquals(0.0, ps.fuelSpentKg(), 1e-9);
    }

    @Test
    void serializeRoundTrip() {
        PlayerStats ps = new PlayerStats();
        ps.recordTrip(-2, 1600.0, 800.25, false); // Sol sentinel index
        ps.recordTrip(424242, 99999.5, 1234.0, true);
        PlayerStats copy = PlayerStats.deserialize(ps.serialize());
        assertEquals(ps.trips(), copy.trips());
        assertEquals(ps.lyTraveled(), copy.lyTraveled(), 1e-9);
        assertEquals(ps.fuelSpentKg(), copy.fuelSpentKg(), 1e-9);
        assertEquals(ps.planetsVisited(), copy.planetsVisited());
        assertEquals(ps.moonsVisited(), copy.moonsVisited());
        assertEquals(ps.systemsVisitedCount(), copy.systemsVisitedCount());
        assertTrue(copy.hasVisited(-2));
        assertTrue(copy.hasVisited(424242));
    }

    @Test
    void deserializeToleratesGarbage() {
        PlayerStats ps = PlayerStats.deserialize("ps|trips|abc\njunk\nps|ly|12.5\n");
        assertEquals(0, ps.trips());
        assertEquals(12.5, ps.lyTraveled(), 1e-9);
        assertEquals(0, ps.systemsVisitedCount());
        assertEquals(0, PlayerStats.deserialize(null).trips());
        assertEquals(0, PlayerStats.deserialize("").moonsVisited());
    }
}
