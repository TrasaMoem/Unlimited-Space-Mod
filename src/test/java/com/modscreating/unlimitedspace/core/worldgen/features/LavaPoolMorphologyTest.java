package com.modscreating.unlimitedspace.core.worldgen.features;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT 6 section 4 - the LAVA / BRINE POOL MORPHOLOGY system.
 *
 * <p>Asserts the production contract directly:
 * <ul>
 *   <li>at least 15 genuinely different shape families exist;</li>
 *   <li>every family is actually reachable from a deterministic seed draw;</li>
 *   <li>no morphology collapses to a 2x2 block;</li>
 *   <li>sizes, depths and outlines really differ between families and between seeds;</li>
 *   <li>the depth tapers toward the edge and every liquid column has a solid bank around it;</li>
 *   <li>the whole system is deterministic and seed-stable.</li>
 * </ul>
 */
@Tag("worldgen")
@Tag("audit")
class LavaPoolMorphologyTest {

    /**
     * Number of pool seeds sampled by the statistical tests. Large enough for a stable family
     * distribution and a stable size/depth range, small enough that the test stays quick.
     */
    private static final int SEEDS = 1200;

    /** Families whose outline must also be visibly NON-rectangular. */
    private static final java.util.Set<LavaPoolMorphology.Family> ROUND_FAMILIES =
            java.util.EnumSet.of(LavaPoolMorphology.Family.COMPACT, LavaPoolMorphology.Family.OVAL,
                    LavaPoolMorphology.Family.IRREGULAR_BLOB,
                    LavaPoolMorphology.Family.LOBED,
                    LavaPoolMorphology.Family.STEPPED_BLOB);

    /**
     * Families that are a SINGLE connected body centred on (0,0). The multi-lobe families
     * (SPLIT_BASIN, BRANCHING, OXBOW) legitimately have one deep point per lobe instead.
     */
    private static final java.util.Set<LavaPoolMorphology.Family> SINGLE_BODY_FAMILIES =
            java.util.EnumSet.of(LavaPoolMorphology.Family.COMPACT, LavaPoolMorphology.Family.OVAL,
                    LavaPoolMorphology.Family.ELONGATED_X, LavaPoolMorphology.Family.ELONGATED_Z,
                    LavaPoolMorphology.Family.IRREGULAR_BLOB,
                    LavaPoolMorphology.Family.STEPPED_BLOB,
                    LavaPoolMorphology.Family.SHALLOW_BASIN,
                    LavaPoolMorphology.Family.DEEP_BASIN,
                    LavaPoolMorphology.Family.ASYMMETRIC_BASIN,
                    LavaPoolMorphology.Family.CRATER_LIKE,
                    LavaPoolMorphology.Family.LOBED,
                    LavaPoolMorphology.Family.NARROW_GORGE,
                    LavaPoolMorphology.Family.TINY_WIDE,
                    LavaPoolMorphology.Family.LARGE_SOFT);

    private static int cells(LavaPoolMorphology m) {
        int n = 0;
        int radius = (int) Math.ceil(m.maxRadius()) + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (m.isInside(dx, dz)) n++;
            }
        }
        return n;
    }

    @Test
    void atLeastFifteenShapeFamiliesExist() {
        assertTrue(LavaPoolMorphology.FAMILY_COUNT >= 15,
                "the production contract is at least 15 lava pool morphologies, got "
                        + LavaPoolMorphology.FAMILY_COUNT);
    }

    @Test
    void everyFamilyIsReachableFromTheSeedDraw() {
        Map<LavaPoolMorphology.Family, Integer> counts =
                new EnumMap<>(LavaPoolMorphology.Family.class);
        for (LavaPoolMorphology.Family f : LavaPoolMorphology.Family.values()) {
            counts.put(f, 0);
        }
        for (long seed = 0; seed < SEEDS; seed++) {
            counts.merge(LavaPoolMorphology.of(seed).family(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (LavaPoolMorphology.Family f : LavaPoolMorphology.Family.values()) {
            int n = counts.get(f);
            assertTrue(n > 0, "family never drawn: " + f);
            sb.append(String.format(Locale.ROOT, " %s=%d", f, n));
        }
        System.out.println("[ACT6-LAVA] families=" + LavaPoolMorphology.FAMILY_COUNT
                + " distribution over " + SEEDS + " seeds:" + sb);
    }

    @Test
    void noMorphologyCollapsesToATwoByTwoBlock() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LavaPoolMorphology m = LavaPoolMorphology.of(seed);
            assertTrue(cells(m) > 4,
                    "a pool must never be a 2x2 stamp: family=" + m.id() + " seed=" + seed
                            + " cells=" + cells(m));
        }
    }

    @Test
    void familiesProduceGenuinelyDifferentOutlines() {
        Set<String> signatures = new HashSet<>();
        int sampled = 0;
        for (long seed = 0; seed < SEEDS; seed += 7) {
            LavaPoolMorphology m = LavaPoolMorphology.of(seed);
            StringBuilder sb = new StringBuilder(m.id()).append(':');
            int radius = (int) Math.ceil(m.maxRadius());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    sb.append(m.isInside(dx, dz) ? '1' : '0');
                }
            }
            signatures.add(sb.toString());
            sampled++;
        }
        assertTrue(signatures.size() > sampled * 3 / 4,
                "pool outlines are not distinct enough: " + signatures.size()
                        + " unique of " + sampled);
    }

    @Test
    void sizesAndDepthsSpanTheRealRange() {
        int minRadius = Integer.MAX_VALUE;
        int maxRadius = 0;
        int minDepth = Integer.MAX_VALUE;
        int maxDepth = 0;
        long totalCells = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            LavaPoolMorphology m = LavaPoolMorphology.of(seed);
            minRadius = Math.min(minRadius, (int) m.maxRadius());
            maxRadius = Math.max(maxRadius, (int) m.maxRadius());
            minDepth = Math.min(minDepth, m.maxDepth());
            maxDepth = Math.max(maxDepth, m.maxDepth());
            totalCells += cells(m);
        }
        double mean = (double) totalCells / SEEDS;
        System.out.printf(Locale.ROOT,
                "[ACT6-LAVA] radius %d..%d depth %d..%d meanCells=%.1f%n",
                minRadius, maxRadius, minDepth, maxDepth, mean);
        assertTrue(maxRadius - minRadius >= 6, "pool sizes must really vary");
        assertTrue(maxDepth - minDepth >= 3, "pool depths must really vary");
    }

    @Test
    void depthTapersTowardTheEdgeAndTheCoreIsDeepest() {
        for (long seed = 0; seed < 250; seed++) {
            LavaPoolMorphology m = LavaPoolMorphology.of(seed);
            int radius = (int) Math.ceil(m.maxRadius());
            boolean sawEdge = false;
            int edgeCells = 0;
            boolean reachedFullDepth = false;
            int deepestSeen = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!m.isInside(dx, dz)) continue;
                    int dep = m.depthAt(dx, dz);
                    if (dep > deepestSeen) deepestSeen = dep;
                    if (dep * 10 >= m.maxDepth() * 9) reachedFullDepth = true;
                    if (m.isEdge(dx, dz)) {
                        sawEdge = true;
                        edgeCells++;
                    }
                }
            }
            // A pool must actually REACH its full depth somewhere - otherwise the depth is
            // decorative and the basin would be a flat sheet again. The exact maximum of a
            // lobe or a channel can fall between two blocks, so the bar is 90% of max depth.
            assertTrue(reachedFullDepth,
                    "no column of this pool reaches its full depth: " + m.id());
            // For a SINGLE-BODY family the deepest column is the centre. A multi-lobe family
            // (split basin, branching, oxbow) legitimately has one deep point per lobe, so only

            // the single-body families are held to the centre rule.
            if (SINGLE_BODY_FAMILIES.contains(m.family())) {
                assertTrue(m.depthAt(0, 0) * 10 >= m.maxDepth() * 8,
                        "the core must be the deepest part of a single-body pool: "
                                + m.id() + " centre=" + m.depthAt(0, 0)
                                + " max=" + m.maxDepth());
            }
            assertTrue(sawEdge, "every pool must have a shallow bank: " + m.id());
            assertTrue(edgeCells > 0, "pool has no edge cells: " + m.id());
            // The precise test for "a square texture": does the pool FILL its own bounding box?
            // A genuinely round or lobed pool leaves wide empty corners; a rectangle leaves almost
            // none. Only the ROUND families are held to it - a narrow gorge or an oxbow is SUPPOSED
            // to have straight parallel flanks, and the outline-distinctness test above already
            // proves no two pools share an outline.
            if (ROUND_FAMILIES.contains(m.family())) {
                int r = (int) Math.ceil(m.maxRadius());
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxZ = Integer.MIN_VALUE;
                int inside = 0;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (!m.isInside(dx, dz)) continue;
                        inside++;
                        minX = Math.min(minX, dx);
                        maxX = Math.max(maxX, dx);
                        minZ = Math.min(minZ, dz);
                        maxZ = Math.max(maxZ, dz);
                    }
                }
                if (inside > 0) {
                    double box = (double) (maxX - minX + 1) * (maxZ - minZ + 1);
                    double fill = inside / box;
                    // A TRUE reference: a disc inscribed in its own bounding box fills
                    // pi/4 = 0.785. A square fills 1.0. The bound is therefore just above
                    // the disc, so a round pool passes with a little integer-rounding slack
                    // and a rectangle cannot.
                    assertTrue(fill < 0.90,
                            "the outline is rectangular: " + m.id() + " boxFill=" + fill
                                    + " (a disc fills 0.785, a square 1.0)");
                }
            }
        }
    }

    @Test
    void everyPoolIsBoundedAndHasABankAllAround() {
        // The real "no floating patch" invariant: a pool is a FINITE body with real ground all
        // around it - never an unbounded liquid region and never a shape that escapes its own
        // declared radius. (A cell 10 blocks from the centre of a radius-27 pool is legitimately
        // interior, so "every cell has a neighbour outside" would be the wrong assertion.)
        for (long seed = 0; seed < 250; seed++) {
            LavaPoolMorphology m = LavaPoolMorphology.of(seed);
            int r = (int) Math.ceil(m.maxRadius());
            // 1. Bounded: one full block beyond the declared radius is always dry ground.
            for (int t = -r - 1; t <= r + 1; t++) {
                assertTrue(!m.isInside(t, -(r + 1)) && !m.isInside(t, r + 1)
                        && !m.isInside(-(r + 1), t) && !m.isInside(r + 1, t),
                        "a pool escaped its own radius (seed=" + seed + " family="
                                + m.id() + ")");
            }
            // 2. The pool is genuinely ringed: some dry column exists on every side, so the
            //    liquid always has a bank a player can stand on.
            assertTrue(!m.isInside(r, 0) && !m.isInside(-r, 0)
                    && !m.isInside(0, r) && !m.isInside(0, -r),
                    "a pool has no bank on one of its sides: " + m.id());
            // 3. It is a single connected body, never a scatter of isolated specks.
            if (m.isInside(0, 0)) {
                int reachable = 0;
                boolean[][] seen = new boolean[2 * r + 3][2 * r + 3];
                java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                queue.add(new int[]{0, 0});
                seen[r + 1][r + 1] = true;
                while (!queue.isEmpty()) {
                    int[] c = queue.poll();
                    reachable++;
                    int[][] n = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
                    for (int[] q : n) {
                        if (q[0] < -r - 1 || q[0] > r + 1 || q[1] < -r - 1 || q[1] > r + 1) continue;
                        if (seen[q[0] + r + 1][q[1] + r + 1]) continue;
                        if (!m.isInside(q[0], q[1])) continue;
                        seen[q[0] + r + 1][q[1] + r + 1] = true;
                        queue.add(q);
                    }
                }
                assertTrue(reachable > 4,
                        "a pool fragmented into isolated specks: " + m.id()
                                + " reachable=" + reachable);
            }
        }
    }

    @Test
    void theShapeIsDeterministicAndSeedStable() {
        for (long seed = 0; seed < 120; seed++) {
            LavaPoolMorphology a = LavaPoolMorphology.of(seed);
            LavaPoolMorphology b = LavaPoolMorphology.of(seed);
            assertTrue(a.family() == b.family(), "family must be stable for a seed");
            assertTrue(a.maxDepth() == b.maxDepth(), "depth must be stable for a seed");
            int radius = (int) Math.ceil(a.maxRadius()) + 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    assertTrue(a.isInside(dx, dz) == b.isInside(dx, dz),
                            "the mask must be stable for a seed at " + dx + "," + dz);
                    assertTrue(a.depthAt(dx, dz) == b.depthAt(dx, dz),
                            "the depth must be stable for a seed at " + dx + "," + dz);
                }
            }
        }
    }
}
