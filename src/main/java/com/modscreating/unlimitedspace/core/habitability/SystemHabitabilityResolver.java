package com.modscreating.unlimitedspace.core.habitability;

import java.util.List;

/**
 * ACT 2.2 — bounded PHYSICS-AWARE candidate resolution (the recommended MODEL 2).
 *
 * <p>The system PATTERN produces preferred anchor positions (see
 * {@link SystemHabitability#positionsOneBased}); THIS resolver turns those anchors into the
 * actual candidate orbit indexes using the canonical physical validity of the ALREADY
 * GENERATED planets:
 *
 * <pre>
 * window = ±2 orbital slots around every anchor of the effective pattern
 * PAIR-A (only when the pattern's original semantics produce TWO positions):
 *     nearest physically valid ADJACENT pair to the anchor inside the window
 *         └─ none found ──► single fallback
 * single (always, as the fallback and the sole mode of single-position patterns):
 *     physically valid planet nearest to the anchor inside the window
 *         └─ none found ──► empty (no actual habitable planet for this pattern)
 * </pre>
 *
 * <p>Hard rules (ACT 2.2 §4/§5/§10):
 * <ul>
 *   <li>window is EXACTLY ±2 orbital slots — never a global search (P3 keeps its
 *       "star-facing" meaning: it can only ever reach orbitIndex ≤ 2);</li>
 *   <li>never returns more than 2 candidates, and 2 candidates are always adjacent
 *       ({@code |a - b| == 1});</li>
 *   <li>every returned index is a REAL, existing orbit slot that already possesses generated
 *       physical data AND passes the canonical REC {@link HabitabilityValidator};</li>
 *   <li>the resolver only SELECTS — it never modifies AU, temperature, orbit or
 *       {@code PlanetProperties} of anything;</li>
 *   <li>no randomness of any kind: tie-break is always the LOWER orbitIndex, so the result is
 *       independent of call order and reproducible across repeated evaluations;</li>
 *   <li>PATTERN 4 passes an empty anchor list and always resolves to an empty result.</li>
 * </ul>
 *
 * <p>Pure domain: no Minecraft types. Package-private — only {@link SystemHabitability} and
 * the same-package tests drive it.
 */
final class SystemHabitabilityResolver {

    /** Bounded resolution window: ±2 orbital slots around every pattern anchor. */
    static final int WINDOW = 2;

    private SystemHabitabilityResolver() {}

    /**
     * Resolve the actual candidate orbit indexes of one system.
     *
     * @param planetCount     number of EXISTING planets N (≥ 0)
     * @param anchors0        preferred 0-based orbit positions of the effective pattern
     *                        (size 1 or 2 adjacent positions; empty = PATTERN 4)
     * @param physicallyValid canonical REC verdict per orbit slot ({@code length ≥ planetCount})
     * @return an ascending list of 0, 1 or 2 orbit indexes. Every returned index is physically
     *         valid; two indexes are always adjacent; empty when no physically valid planet
     *         exists inside the ±2 window.
     */
    static List<Integer> resolve(int planetCount, List<Integer> anchors0, boolean[] physicallyValid) {
        if (planetCount <= 0 || anchors0 == null || anchors0.isEmpty()
                || physicallyValid == null || physicallyValid.length < planetCount) {
            return List.of();
        }

        // ---- single candidate: physically valid planet nearest to the anchor set, within ±2 ----
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < planetCount; i++) {
            if (!physicallyValid[i]) continue;
            int d = distanceToAnchors(i, anchors0);
            if (d > WINDOW) continue;                       // bounded window — never global
            if (best < 0 || d < bestDist || (d == bestDist && i < best)) {
                best = i;
                bestDist = d;
            }
        }

        // ---- PAIR-A: only for patterns whose ORIGINAL semantics produce two positions ----
        if (anchors0.size() == 2) {
            int firstAnchor = Math.min(anchors0.get(0), anchors0.get(1));
            int pairStart = -1;
            int pairDist = Integer.MAX_VALUE;
            for (int i = 0; i + 1 < planetCount; i++) {
                if (!physicallyValid[i] || !physicallyValid[i + 1]) continue;
                // both members must sit inside the ±2 window of the anchor pair
                if (distanceToAnchors(i, anchors0) > WINDOW
                        || distanceToAnchors(i + 1, anchors0) > WINDOW) continue;
                // displacement of the PAIR from the original anchor (first-member shift)
                int d = Math.abs(i - firstAnchor);
                if (pairStart < 0 || d < pairDist || (d == pairDist && i < pairStart)) {
                    pairStart = i;
                    pairDist = d;
                }
            }
            if (pairStart >= 0) return List.of(pairStart, pairStart + 1);   // pair wins over single
        }

        return best < 0 ? List.of() : List.of(best);
    }

    /** Distance of an orbit slot to the NEAREST anchor of the pattern (0-based). */
    private static int distanceToAnchors(int orbitIndex, List<Integer> anchors0) {
        int d = Integer.MAX_VALUE;
        for (int a : anchors0) {
            d = Math.min(d, Math.abs(orbitIndex - a));
        }
        return d;
    }
}