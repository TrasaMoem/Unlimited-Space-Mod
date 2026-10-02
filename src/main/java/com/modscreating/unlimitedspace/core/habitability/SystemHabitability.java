package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.StarSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ACT 2 — the deterministic SYSTEM habitability pattern (the user's gameplay rule).
 *
 * <p>Exactly 4 patterns, drawn ONCE per system from a dedicated habitability seed
 * (ACT 2.2 FINAL frequency calibration):
 *
 * <pre>
 * draw &lt; 0.45  → PATTERN 1 (middle-system anchor N/2)
 * draw &lt; 0.65  → PATTERN 2 (anchor N/4)
 * draw &lt; 0.90  → PATTERN 3 (planet closest to the star)
 * otherwise    → PATTERN 4 (no candidates — always empty)
 * </pre>
 *
 * <p><b>ACT 2.2 — deterministic eligibility fallback (NO artificial P4 demotion).</b>
 * The drawn pattern is mapped to an <i>effective</i> pattern purely as a function of N —
 * there is NO second random draw and probabilities are NEVER renormalized, so the explicit
 * P4 draw stays exactly 10%:
 *
 * <pre>
 * P1 drawn: N ≥ 2 → P1 | N &lt; 2 → P3
 * P2 drawn: N ≥ 4 → P2 | 2 ≤ N &lt; 4 → P1 | N &lt; 2 → P3
 * P3 drawn: always P3 (N ≥ 1)
 * P4 drawn: always P4
 * </pre>
 *
 * <p>The effective pattern's preferred positions then go through the bounded PHYSICS-AWARE
 * {@link SystemHabitabilityResolver} (±2 orbital slots, PAIR-A with single fallback): only
 * ALREADY GENERATED planets that pass the canonical REC {@link HabitabilityValidator} inside
 * the window can become candidates. A pattern with no physically valid planet in its window
 * yields no actual habitable planet — the resolver never moves an orbit, never edits a
 * temperature and never edits {@code PlanetProperties}; it only SELECTS.
 *
 * <p>Positions are defined FIRST as 1-based orbital positions and converted to the existing
 * 0-based orbitIndex only at the very end. Adjacency is defined on the ORBITAL SLOT ORDER
 * (orbitIndex), never on world coordinates, galaxy layout radii or numeric-id differences.
 * ACT 1 guarantees AU monotonicity by orbit slot, so position 1 is exactly the planet
 * closest to the star (no galaxy-layout coordinates involved).
 *
 * <p>Stars, black holes and asteroid fields are structurally excluded: this class only ever
 * selects among {@link StarSystem#getPlanet(int)} slots (PLANET objects).
 *
 * <p>No cycle: this class consumes ALREADY GENERATED planets; nothing in it is consumed by
 * planet generation. Deterministic from {@code systemSeed + planetCount + canonical planet
 * properties}. Pure domain: no Minecraft types.
 */
public final class SystemHabitability {

    /** The four user-defined system patterns. */
    public enum Pattern { ONE, TWO, THREE, FOUR }

    /** ACT 2.2 FINAL gameplay probabilities: P1 45% / P2 20% / P3 25% / P4 10%. */
    public static final double P_ONE_PROBABILITY = 0.45;
    public static final double P_TWO_PROBABILITY = 0.20;
    public static final double P_THREE_PROBABILITY = 0.25;

    /** Dedicated habitability seed namespace — never shares values with other subsystems. */
    private static final String HABITABILITY_NS = "unlimitedspace.habitability.pattern";
    private static final long PATTERN_SLOT = 950001L;

    /** The deterministic whole-system result, computed once and reused by every consumer. */
    public record Result(
            int planetCount,
            Pattern pattern,
            List<Integer> candidateOrbitIndexes,
            Map<Integer, HabitabilityAnswer> physicalAnswers,
            List<Integer> actuallyHabitableOrbitIndexes) {

        /** True when this orbit slot was selected as a system candidate (concept A). */
        public boolean isCandidate(int orbitIndex) {
            return candidateOrbitIndexes.contains(orbitIndex);
        }

        /** The physical validation of a candidate, or {@code null} when not a candidate. */
        public HabitabilityAnswer physicalAnswer(int orbitIndex) {
            return physicalAnswers.get(orbitIndex);
        }

        /** True when the candidate also passed the physical validation (concept B). */
        public boolean isPhysicallyHabitable(int orbitIndex) {
            HabitabilityAnswer a = physicalAnswers.get(orbitIndex);
            return a != null && a.physicallyHabitable();
        }

        /** The FINAL world verdict (concept C): candidate AND physically habitable. */
        public boolean isActuallyHabitable(int orbitIndex) {
            return actuallyHabitableOrbitIndexes.contains(orbitIndex);
        }
    }

    private SystemHabitability() {}

    /** The raw deterministic pattern draw for one system seed (exposed for statistical tests). */
    public static Pattern drawPattern(long systemSeed) {
        double p = Seeds.fraction(Seeds.derive(systemSeed, HABITABILITY_NS), PATTERN_SLOT);
        if (p < P_ONE_PROBABILITY) return Pattern.ONE;
        if (p < P_ONE_PROBABILITY + P_TWO_PROBABILITY) return Pattern.TWO;
        if (p < P_ONE_PROBABILITY + P_TWO_PROBABILITY + P_THREE_PROBABILITY) return Pattern.THREE;
        return Pattern.FOUR;
    }

    /**
     * ACT 2.2 — the DETERMINISTIC eligibility fallback of a drawn pattern for a system of N
     * planets. Pure function of (drawn, N): NO second random draw, NO renormalization, and
     * crucially NO demotion into PATTERN 4 — the explicit P4 draw remains exactly the
     * effective P4 probability. Package-visible for the exhaustive fallback tests.
     */
    static Pattern effectivePattern(Pattern drawn, int planetCount) {
        return switch (drawn) {
            case ONE -> planetCount >= 2 ? Pattern.ONE : Pattern.THREE;
            case TWO -> planetCount >= 4 ? Pattern.TWO
                    : (planetCount >= 2 ? Pattern.ONE : Pattern.THREE);
            case THREE -> planetCount >= 1 ? Pattern.THREE : Pattern.FOUR;
            case FOUR -> Pattern.FOUR;
        };
    }

    /**
     * The canonical system result. Generates the preferred candidates of the effective pattern
     * through the bounded physics-aware resolver (±2 window), validates them physically and
     * produces the actual-habitability verdicts.
     */
    public static Result of(StarSystem system) {
        int n = system.planetCount();
        Pattern effective = effectivePattern(drawPattern(system.seed()), n);

        List<Integer> positions = switch (effective) {
            case ONE -> positionsOneBased(n, 2.0);
            case TWO -> positionsOneBased(n, 4.0);
            case THREE -> List.of(1);
            case FOUR -> List.of();
        };

        // 1-based preferred positions → 0-based anchor orbit slots (defensive bounds only;
        // the eligibility fallback guarantees in-range positions for N = 1..6).
        List<Integer> anchors0 = new ArrayList<>();
        for (int position : positions) {
            int orbitIndex = position - 1;
            if (orbitIndex >= 0 && orbitIndex < n && !anchors0.contains(orbitIndex)) {
                anchors0.add(orbitIndex);
            }
        }

        // PATTERN 4 (or a degenerate anchor set): zero candidates, no validation work at all.
        if (anchors0.isEmpty()) {
            return new Result(n, effective, List.of(), Map.of(), List.of());
        }

        // Canonical REC validity of every EXISTING planet (N ≤ 6 — evaluated once, reused).
        HabitabilityAnswer[] answers = new HabitabilityAnswer[n];
        boolean[] physicallyValid = new boolean[n];
        for (int orbitIndex = 0; orbitIndex < n; orbitIndex++) {
            answers[orbitIndex] = HabitabilityValidator.validate(
                    HabitabilityProfile.ofPlanet(system.getPlanet(orbitIndex).properties()));
            physicallyValid[orbitIndex] = answers[orbitIndex].physicallyHabitable();
        }

        // Bounded physics-aware resolution: selects ONLY existing valid worlds (never edits them).
        List<Integer> candidates = SystemHabitabilityResolver.resolve(n, anchors0, physicallyValid);

        // Physical answers are recorded for the selected candidates only (existing contract:
        // a non-candidate never carries an answer). Candidates are valid BY CONSTRUCTION here;
        // the explicit actual = candidate ∧ physical check keeps concept C honest (§9).
        Map<Integer, HabitabilityAnswer> physical = new LinkedHashMap<>();
        List<Integer> actual = new ArrayList<>();
        for (int orbitIndex : candidates) {
            physical.put(orbitIndex, answers[orbitIndex]);
            if (physicallyValid[orbitIndex]) actual.add(orbitIndex);
        }
        return new Result(n, effective, List.copyOf(candidates),
                Collections.unmodifiableMap(physical), List.copyOf(actual));
    }

    /**
     * 1-based candidate positions of the user's pattern tables. Package-visible for the
     * exhaustive pattern tests.
     *
     * <p>Pattern 1 (divisor 2.0): a FRACTIONAL raw always yields two adjacent candidates
     * {@code round(raw), round(raw)+1}; an INTEGER raw yields ONE candidate when raw is even and
     * TWO adjacent candidates when raw is odd — exactly the user's table
     * (N=2→{1,2}, N=3→{2,3}, N=4→{2}, N=5→{3,4}, N=6→{3,4}).
     *
     * <p>Pattern 2 (divisor 4.0): an INTEGER raw yields one candidate at raw; a fractional raw
     * yields two adjacent candidates — exactly the user's table
     * (N=4→{1}, N=5→{1,2}, N=6→{2,3}, N=7→{2,3}, N=8→{2}).
     */
    static List<Integer> positionsOneBased(int n, double divisor) {
        double raw = n / divisor;
        if (raw == Math.floor(raw)) {
            int k = (int) raw;
            if (divisor == 2.0 && k % 2 != 0) {
                return List.of(k, k + 1);          // Pattern 1: odd integer raw → adjacent pair
            }
            return List.of(k);                     // even integer raw (P1) / any integer raw (P2)
        }
        int r = (int) Math.round(raw);             // half-up rounding of the fractional raw
        return List.of(r, r + 1);
    }
}
