package com.modscreating.unlimitedspace.core.asteroids;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R12.3 Bug #3 — rocket arrival teleports the player to the *middle* of the field and lets them
 * float, with no landing pad (like Creating Space's Earth-orbit transition).
 *
 * <p>Creating Space teleports the rocket to a static {@code arrivalHeight} configured in the
 * datapack. The central {@code (0,0)} cell is kept empty so the whole central {@code (0,·,0)}
 * column is a free-air clearing at the middle of the cluster — the player simply appears there
 * floating among the surrounding asteroids, and never lands or plummets.
 */
class AsteroidArrivalTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;

    private static AsteroidFieldGeometry geometry(int system, int clusterIndex) {
        AsteroidCluster c = Galaxy.from(WORLD_SEED).getStarSystem(StarSystemId.of(system)).asteroid(clusterIndex);
        return new AsteroidFieldGeometry(c.seed().value(), c.profile());
    }

    @Test
    void arrivalConstantsPlaceThePlayerInTheMiddle() {
        int arrival = AsteroidFieldGeometry.arrivalY();
        assertEquals(45, arrival, "arrival Y must be the middle of the field band (90/2)");
        assertTrue(arrival < AsteroidFieldGeometry.maxTopY(),
                "arrival is in the middle of the field, not above every body");
    }

    @Test
    void arrivalColumnIsSafeForTheDefaultCluster() {
        AsteroidFieldGeometry g = geometry(0, 0);
        int y = AsteroidFieldGeometry.arrivalY();
        assertTrue(g.isArrivalSafe(0, y, 0),
                "default arrival column (0," + y + ",0) must be free of asteroid");
        assertEquals("minecraft:air", g.blockIdAt(0, y, 0),
                "arrival block at default column must be air");
        // R12.3 Bug #3: teleport-to-centre — the player floats, so there is NO landing pad below.
        assertEquals("minecraft:air", g.blockIdAt(0, y - 1, 0),
                "teleport-to-centre floats; there must be no landing pad under the arrival point");
    }

    @Test
    void arrivalColumnIsSafeAcrossSeedsAndClusters() {
        int y = AsteroidFieldGeometry.arrivalY();
        int[] systems = {0, 1, 2, 3, 7};
        int[] clusters = {0, 1, 2};
        for (int system : systems) {
            for (int cluster : clusters) {
                AsteroidFieldGeometry g = geometry(system, cluster);
                // The central cell is kept empty, so the central (0,·,0) column at the arrival
                // Y must always be free air — the player floats there in the middle of the cluster.
                assertTrue(g.isArrivalSafe(0, y, 0),
                        "system " + system + " cluster " + cluster + " central column at Y " + y + " not safe");
                assertEquals("minecraft:air", g.blockIdAt(0, y - 1, 0),
                        "system " + system + " cluster " + cluster + " must float (no landing pad) at centre");
            }
        }
    }

    @Test
    void spawnPointIsSafeAndDeterministic() {
        AsteroidFieldGeometry g = geometry(0, 0);
        int[] s1 = g.spawnAt();
        int[] s2 = g.spawnAt();
        assertArrayEquals(s1, s2, "spawn point must be deterministic");
        assertTrue(g.isArrivalSafe(s1[0], s1[1], s1[2]),
                "spawn point (" + s1[0] + "," + s1[1] + "," + s1[2] + ") must not be inside an asteroid");
        assertEquals("minecraft:air", g.blockIdAt(s1[0], s1[1], s1[2]),
                "spawn block must be air");
    }

    @Test
    void arrivalConstantsAreIndependentOfSeed() {
        assertEquals(AsteroidFieldGeometry.arrivalY(), geometry(0, 0).arrivalY());
        assertEquals(AsteroidFieldGeometry.maxTopY(), geometry(0, 0).maxTopY());
        assertEquals(AsteroidFieldGeometry.arrivalY(), geometry(7, 2).arrivalY());
        assertEquals(AsteroidFieldGeometry.maxTopY(), geometry(7, 2).maxTopY());
    }

    /** R12.3 — new arrival: spawn point is in the useful vertical range and outside solid geometry. */
    @Test
    void spawnPointIsInUsefulVerticalRange() {
        AsteroidFieldGeometry g = geometry(0, 0);
        int[] s1 = g.spawnAt();
        int[] s2 = g.spawnAt();
        assertArrayEquals(s1, s2, "spawn point must be deterministic");
        assertTrue(g.isArrivalSafe(s1[0], s1[1], s1[2]),
                "spawn point (" + s1[0] + "," + s1[1] + "," + s1[2] + ") must not be inside an asteroid");
        assertEquals("minecraft:air", g.blockIdAt(s1[0], s1[1], s1[2]),
                "spawn block must be air");
        int maxTop = g.maxTopY();
        assertTrue(s1[1] > 10, "spawn Y (" + s1[1] + ") must be > 10");
        assertTrue(s1[1] < maxTop, "spawn Y (" + s1[1] + ") must be < maxTop (" + maxTop + ")");
    }

    /** R12.3 — teleport-to-centre: the arrival column is free air at the mid-field Y. */
    @Test
    void arrivalColumnIsSafeAtNewMidfieldY() {
        AsteroidFieldGeometry g = geometry(0, 0);
        int arrivalY = AsteroidFieldGeometry.arrivalY();
        assertTrue(g.isArrivalSafe(0, arrivalY, 0),
                "central column at arrival Y must be safe (free air, floating)");
        assertEquals("minecraft:air", g.blockIdAt(0, arrivalY - 1, 0),
                "the centre must be free air — no landing pad below the arrival point");
        int[] spawn = g.spawnAt();
        assertTrue(g.isArrivalSafe(spawn[0], spawn[1], spawn[2]),
                "spawn column at mid-field Y must be safe");
    }
}
