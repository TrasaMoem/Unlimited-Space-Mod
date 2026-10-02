package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.2 — the user's SYSTEM PATTERN tables (kept exact), the FINAL 45/20/25/10
 * probabilities, the DETERMINISTIC eligibility fallback (no artificial P4 demotion) and the
 * exhaustive system invariants of the physics-aware resolver.
 */
@Tag("audit")
class SystemHabitabilityPatternTest {

    /* ------------------------------------------------- pattern tables (1-based positions) */

    @Test
    void pattern1PositionsMatchTheUserTableExactly() {
        assertEquals(List.of(1, 2), SystemHabitability.positionsOneBased(2, 2.0));
        assertEquals(List.of(2, 3), SystemHabitability.positionsOneBased(3, 2.0));
        assertEquals(List.of(2), SystemHabitability.positionsOneBased(4, 2.0));
        assertEquals(List.of(3, 4), SystemHabitability.positionsOneBased(5, 2.0));
        assertEquals(List.of(3, 4), SystemHabitability.positionsOneBased(6, 2.0));
    }

    @Test
    void pattern2PositionsMatchTheUserTableExactly() {
        assertEquals(List.of(1), SystemHabitability.positionsOneBased(4, 4.0));
        assertEquals(List.of(1, 2), SystemHabitability.positionsOneBased(5, 4.0));
        assertEquals(List.of(2, 3), SystemHabitability.positionsOneBased(6, 4.0));
        assertEquals(List.of(2, 3), SystemHabitability.positionsOneBased(7, 4.0));
        assertEquals(List.of(2), SystemHabitability.positionsOneBased(8, 4.0));
    }

    /* ------------------------------------------------- exhaustive system invariants */

    @Test
    void exhaustiveSystemResultsRespectEveryInvariant() {
        Galaxy galaxy = Galaxy.from(911L);
        for (int s = 0; s < 4000; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            int n = system.planetCount();

            assertTrue(r.candidateOrbitIndexes().size() <= 2,
                    "at most 2 candidates, got " + r);
            assertEquals(r.candidateOrbitIndexes().stream().distinct().count(),
                    r.candidateOrbitIndexes().size(), "no duplicate candidates: " + r);
            for (int idx : r.candidateOrbitIndexes()) {
                assertTrue(idx >= 0 && idx < n, "candidate inside [0, N): " + idx + " of " + n);
            }
            if (r.candidateOrbitIndexes().size() == 2) {
                assertEquals(1, Math.abs(r.candidateOrbitIndexes().get(0)
                        - r.candidateOrbitIndexes().get(1)),
                        "two candidates MUST be adjacent in orbital order: " + r);
            }
            for (int o = 0; o < n; o++) {
                if (r.isCandidate(o)) {
                    assertNotNull(r.physicalAnswer(o), "candidate must carry a physical answer");
                    // ACT 2.2: the physics-aware resolver selects ONLY physically valid worlds.
                    assertTrue(r.isPhysicallyHabitable(o),
                            "candidate must pass the REC validator by construction: " + r);
                    assertTrue(r.isActuallyHabitable(o),
                            "candidate ∧ physical → actual (concept C): " + r);
                } else {
                    assertNull(r.physicalAnswer(o), "non-candidate must not carry an answer");
                    assertFalse(r.isActuallyHabitable(o),
                            "non-candidate can never be actually habitable");
                }
                if (r.isActuallyHabitable(o)) {
                    assertTrue(r.isCandidate(o) && r.isPhysicallyHabitable(o));
                }
            }
            if (r.pattern() == SystemHabitability.Pattern.FOUR) {
                assertTrue(r.candidateOrbitIndexes().isEmpty(), "P4 → no candidates");
            }
            if (r.pattern() == SystemHabitability.Pattern.THREE) {
                // ACT 2.2: P3 keeps a single anchor at orbitIndex 0 and never searches
                // beyond the ±2 window, but the SELECTED planet may shift to 1..2 when
                // the exact anchor is not physically valid.
                assertTrue(r.candidateOrbitIndexes().size() <= 1,
                        "P3 single anchor → at most 1 candidate: " + r);
                for (int idx : r.candidateOrbitIndexes()) {
                    assertTrue(idx >= 0 && idx <= 2,
                            "P3 must stay star-facing inside the ±2 window: " + r);
                }
                if (r.candidateOrbitIndexes().contains(0)) {
                    assertEquals(List.of(0), r.candidateOrbitIndexes(),
                            "a valid exact P3 anchor must win (distance 0): " + r);
                }
            }
        }
    }

    @Test
    void deterministicForTheSameSystemSeed() {
        Galaxy galaxy = Galaxy.from(7L);
        StarSystem a = galaxy.getStarSystem(galaxy.systemId(42));
        StarSystem b = galaxy.getStarSystem(galaxy.systemId(42));
        assertEquals(SystemHabitability.of(a), SystemHabitability.of(b));
    }

    /* ------------------------------------------------- statistics */

    @Test
    void drawnPatternDistributionMatchesTheUserProbabilities() {
        int n = 200_000;
        int[] counts = new int[4];
        for (long seed = 1; seed <= n; seed++) {
            counts[SystemHabitability.drawPattern(seed * 7919L).ordinal()]++;
        }
        // ACT 2.2 FINAL probabilities: P1 45% / P2 20% / P3 25% / P4 10%.
        assertShare(counts[0], n * 0.45, n * 0.012, "P1");
        assertShare(counts[1], n * 0.20, n * 0.012, "P2");
        assertShare(counts[2], n * 0.25, n * 0.012, "P3");
        assertShare(counts[3], n * 0.10, n * 0.012, "P4");
    }

    private static void assertShare(long value, double expected, double tolerance, String name) {
        assertTrue(Math.abs(value - expected) <= tolerance,
                name + " share " + value + " must be ~" + expected + " (+-" + tolerance + ")");
    }

    /* ------------------------------------------------- ACT 2.2 deterministic fallback */

    @Test
    void eligibilityFallbackIsExactlyTheAct22TableAndNeverDemotesToP4() {
        // P1 drawn: N ≥ 2 stays P1; N < 2 falls back to P3 — NEVER to P4.
        assertEquals(SystemHabitability.Pattern.ONE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.ONE, 2));
        assertEquals(SystemHabitability.Pattern.ONE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.ONE, 6));
        assertEquals(SystemHabitability.Pattern.THREE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.ONE, 1));
        // P2 drawn: N ≥ 4 stays P2; 2 ≤ N < 4 falls back to P1; N < 2 falls back to P3.
        assertEquals(SystemHabitability.Pattern.TWO,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, 4));
        assertEquals(SystemHabitability.Pattern.TWO,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, 6));
        assertEquals(SystemHabitability.Pattern.ONE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, 2));
        assertEquals(SystemHabitability.Pattern.ONE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, 3));
        assertEquals(SystemHabitability.Pattern.THREE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, 1));
        // P3 / P4 never change.
        assertEquals(SystemHabitability.Pattern.THREE,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.THREE, 1));
        assertEquals(SystemHabitability.Pattern.FOUR,
                SystemHabitability.effectivePattern(SystemHabitability.Pattern.FOUR, 6));
        // The fallback NEVER demotes anything into P4 — effective P4 == explicit P4 draw.
        for (int n = 1; n <= 6; n++) {
            assertNotEquals(SystemHabitability.Pattern.FOUR,
                    SystemHabitability.effectivePattern(SystemHabitability.Pattern.ONE, n),
                    "P1 must never become P4, N=" + n);
            assertNotEquals(SystemHabitability.Pattern.FOUR,
                    SystemHabitability.effectivePattern(SystemHabitability.Pattern.TWO, n),
                    "P2 must never become P4, N=" + n);
            assertNotEquals(SystemHabitability.Pattern.FOUR,
                    SystemHabitability.effectivePattern(SystemHabitability.Pattern.THREE, n),
                    "P3 must never become P4, N=" + n);
        }
    }

    @Test
    void singlePlanetSystemsFallBackToP3OnAP1OrP2Draw() {
        Galaxy galaxy = Galaxy.from(5150L);
        int foundP1 = 0;
        int foundP2 = 0;
        for (int s = 0; s < 30_000 && (foundP1 < 3 || foundP2 < 3); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            if (system.planetCount() != 1) continue;
            SystemHabitability.Pattern drawn = SystemHabitability.drawPattern(system.seed());
            SystemHabitability.Result r = SystemHabitability.of(system);
            if (drawn == SystemHabitability.Pattern.ONE) {
                foundP1++;
                assertEquals(SystemHabitability.Pattern.THREE, r.pattern(),
                        "N=1 + P1 draw must fall back to P3 (never P4)");
            } else if (drawn == SystemHabitability.Pattern.TWO) {
                foundP2++;
                assertEquals(SystemHabitability.Pattern.THREE, r.pattern(),
                        "N=1 + P2 draw must fall back to P3 (never P4)");
            }
        }
        assertTrue(foundP1 >= 3, "expected N=1 systems with a P1 draw, got " + foundP1);
        assertTrue(foundP2 >= 3, "expected N=1 systems with a P2 draw, got " + foundP2);
    }

    @Test
    void twoAndThreePlanetSystemsFallBackFromP2ToP1() {
        Galaxy galaxy = Galaxy.from(6060L);
        int found = 0;
        for (int s = 0; s < 30_000 && found < 6; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            int n = system.planetCount();
            if (n != 2 && n != 3) continue;
            if (SystemHabitability.drawPattern(system.seed()) != SystemHabitability.Pattern.TWO) {
                continue;
            }
            found++;
            SystemHabitability.Result r = SystemHabitability.of(system);
            assertEquals(SystemHabitability.Pattern.ONE, r.pattern(),
                    "N=" + n + " + P2 draw must fall back to P1 (never P4)");
        }
        assertTrue(found >= 6, "expected N=2/3 systems with a P2 draw, got " + found);
    }

    @Test
    void eligibleSystemsKeepTheirDrawnP1OrP2() {
        Galaxy galaxy = Galaxy.from(7070L);
        int foundP1 = 0;
        int foundP2 = 0;
        for (int s = 0; s < 30_000 && (foundP1 < 3 || foundP2 < 3); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            if (system.planetCount() < 4) continue;
            SystemHabitability.Pattern drawn = SystemHabitability.drawPattern(system.seed());
            SystemHabitability.Result r = SystemHabitability.of(system);
            if (drawn == SystemHabitability.Pattern.ONE) {
                foundP1++;
                assertEquals(SystemHabitability.Pattern.ONE, r.pattern(),
                        "N ≥ 4 + P1 draw must stay P1");
            } else if (drawn == SystemHabitability.Pattern.TWO) {
                foundP2++;
                assertEquals(SystemHabitability.Pattern.TWO, r.pattern(),
                        "N ≥ 4 + P2 draw must stay P2");
            }
        }
        assertTrue(foundP1 >= 3, "expected eligible P1 systems, got " + foundP1);
        assertTrue(foundP2 >= 3, "expected eligible P2 systems, got " + foundP2);
    }

    @Test
    void explicitP4DrawAlwaysProducesZeroCandidates() {
        Galaxy galaxy = Galaxy.from(8080L);
        int found = 0;
        for (int s = 0; s < 30_000 && found < 5; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            if (SystemHabitability.drawPattern(system.seed()) != SystemHabitability.Pattern.FOUR) {
                continue;
            }
            found++;
            SystemHabitability.Result r = SystemHabitability.of(system);
            assertEquals(SystemHabitability.Pattern.FOUR, r.pattern());
            assertTrue(r.candidateOrbitIndexes().isEmpty(), "P4 → zero candidates: " + r);
            assertTrue(r.actuallyHabitableOrbitIndexes().isEmpty(), "P4 → zero actual: " + r);
        }
        assertTrue(found >= 5, "expected explicit P4 draws in the sample, got " + found);
    }
}
