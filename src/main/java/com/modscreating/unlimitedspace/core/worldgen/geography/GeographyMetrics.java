package com.modscreating.unlimitedspace.core.worldgen.geography;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WORLDGEN V2 — the measurable properties of a generated macro geography.
 *
 * <h2>What these metrics are FOR</h2>
 * They detect the failure modes the old system kept hiding behind impossible gates:
 * giant straight walls, tiny pathological islands, a lattice imprint, overly weak province
 * identity and excessive transition blur.
 *
 * <h2>What these metrics are NOT</h2>
 * The old gates {@code 3-region p10 >= 5312} and {@code maxStraight <= 256} are RETIRED and
 * are NOT restored. They were demonstrated to conflict with legitimate geometry: forcing every
 * macro body above 5312 blocks and every straight run below 256 blocks distorts the topology
 * instead of describing it. The metrics below are descriptive; the tests assert RELATIONSHIPS
 * (for example a boundary-delta versus local-delta ratio) rather than absolute magic numbers.
 *
 * <p>Pure domain: no Minecraft types. Deterministic for a given geography and scan window.
 *
 * @param runP10/median/p90/max province run lengths along the transects, in blocks
 * @param areaCv coefficient of variation of the sampled area per lattice cell
 * @param isolatedFragments cells owning an absurdly small share of the scan (SITE key)
 * @param shortAbaEvents scale-aware A-B-A flakes, keyed by SITE identity
 * @param archetypeIsolated the same tiny-cell count keyed by ARCHETYPE identity
 * @param archetypeShortAba the same scale-aware A-B-A count keyed by ARCHETYPE identity
 * @param dominantCount the number of distinct archetypes actually observed
 * @param orientationEntropy normalized Shannon entropy of the border-direction histogram in
 *                          [0,1]; 1 = every direction equally represented, ~0 = parallel walls
 * @param straightRunP90 mean length of long single-cell runs, in blocks (0 = none)
 * @param snapShare share of columns at full core identity (high is GOOD)
 * @param moveShare share of columns whose OWNER SITE differs between the warped and the
 *                  unwarped evaluation of the SAME lattice — the honest warp-deformation measure
 * @param archetypeMoveShare the same comparison keyed by archetype rather than by site
 * @param transitionWidthP50 TRUE median measured transition band width in blocks: for every
 *                  column inside the band, the full local width of the band is
 *                  {@code 2 * |signedBoundaryDistance|}, and the MEDIAN of those per-column
 *                  widths is reported. It is not derived from a mean weight.
 * @param transitionWidthP50Mean the arithmetic mean of the same per-column widths, reported
 *                  alongside the median purely as a distribution sanity companion
 * @param coreShare mean attribute-blend purity (1 = pure single-site core)
 * @param meanNeighbourCount mean number of distinct border partners per column
 * @param meanCurvature mean normalised border-direction change per sample step
 * @param attributeGradientP99 the 99th percentile of the continuous attribute gradient magnitude
 *                  over the scan, per 100 blocks — the direct measure of whether a sharper blend
 *                  kernel introduced a gradient spike
 * @param samples/transects/cellSize the scan provenance
 */
public record GeographyMetrics(
        double runP10, double runMedian, double runP90, double runMax,
        double areaCv,
        int isolatedFragments, int shortAbaEvents,
        int archetypeIsolated, int archetypeShortAba,
        int dominantCount,
        double orientationEntropy, double straightRunP90,
        double snapShare, double moveShare, double archetypeMoveShare,
        double transitionWidthP50, double transitionWidthP50Mean, double coreShare,
        double meanNeighbourCount, double meanCurvature,
        double attributeGradientP99,
        int samples, int transects, int cellSize) {

    /** Number of orientation bins (10 degrees each). */
    public static final int ORIENTATION_BINS = 36;

    /** A column label plus the raw (unwarped) lattice cell, captured by one sample. */
    private record Cell(int archetype, int warpedCell, int rawCell) {}

    /**
     * Measure a geography over a deterministic grid of transects.
     *
     * @param geography the geography to measure
     * @param halfSpan  half-width of the scanned square, in blocks
     * @param step      the sampling step along a transect, in blocks
     * @param transects the number of transects
     */
    public static GeographyMetrics measure(MacroGeography geography, int halfSpan,
                                            int step, int transects) {
        int span = Math.max(1, 2 * halfSpan);
        int stride = Math.max(1, step);
        int perTransect = span / stride + 1;
        int tCount = Math.max(1, transects);
        int tStride = Math.max(1, span / tCount);
        int total = perTransect * tCount;

        int[] archetype = new int[total];
        int[] warpedCell = new int[total];
        int[] unwarpedCell = new int[total];
        int[] unwarpedArchetype = new int[total];
        double[] transition = new double[total];
        double[] core = new double[total];
        double[] absBoundary = new double[total];
        double[] hill = new double[total];

        MacroSample s = new MacroSample();
        MacroSample u = new MacroSample();
        int k = 0;
        for (int t = 0; t < tCount; t++) {
            int z = -halfSpan + t * tStride;
            for (int i = 0; i < perTransect; i++) {
                int x = -halfSpan + i * stride;
                geography.sample(x, z, s);
                // The honest warp-deformation control: the SAME lattice, the SAME rotation and the
                // SAME Voronoi rule, with only the bounded warp removed. Comparing against the raw
                // unrotated cell index would measure the rotation and the site jitter instead.
                geography.sampleUnwarped(x, z, u);
                archetype[k] = s.provinceId;
                warpedCell[k] = s.primaryCellX * 4096 + s.primaryCellZ;
                unwarpedCell[k] = u.primaryCellX * 4096 + u.primaryCellZ;
                unwarpedArchetype[k] = u.provinceId;
                transition[k] = s.transitionWeight;
                core[k] = s.coreShare;
                absBoundary[k] = Math.abs(s.signedBoundaryDistance);
                hill[k] = s.hillMultiplier;
                k++;
            }
        }
        return analyse(archetype, warpedCell, unwarpedCell, unwarpedArchetype,
                transition, core, absBoundary, hill,
                perTransect, tCount, stride, tStride);
    }

    private static GeographyMetrics analyse(int[] archetype, int[] warpedCell,
                                            int[] unwarpedCell, int[] unwarpedArchetype,
                                            double[] transition, double[] core,
                                            double[] absBoundary, double[] hill,
                                            int perTransect, int tCount,
                                            int stride, int tStride) {
        int total = archetype.length;
        resetOrientation();

        // ---- run lengths along every transect
        double maxRun = 0.0;
        double straightSum = 0.0;
        int straightCount = 0;
        double[] runs = new double[Math.max(16, total / 2)];
        int runCount = 0;
        int[] crossings = new int[perTransect + 1];
        for (int t = 0; t < tCount; t++) {
            int base = t * perTransect;
            int cross = 0;
            int i = 0;
            while (i < perTransect) {
                int id = archetype[base + i];
                int j = i;
                boolean sameCell = true;
                while (j < perTransect && archetype[base + j] == id) {
                    if (warpedCell[base + j] != warpedCell[base + i]) sameCell = false;
                    j++;
                }
                double len = (j - i) * (double) stride;
                if (runCount < runs.length) runs[runCount++] = len;
                if (len > maxRun) maxRun = len;
                if (sameCell && len > 3.0 * MacroSiteLattice.CELL_SIZE) {
                    straightSum += len;
                    straightCount++;
                }
                // Pathological A-B-A is a THIN sliver, not any repeated neighbour. A genuine
                // Voronoi saddle legitimately puts a narrow wedge of a third archetype between
                // two lobes of the same one, and that wedge is a real geometric feature whose
                // width is set by the site spacing. What the old guard/cap chains produced was
                // wedges far THINNER than the lattice can justify, so the scale-aware test is
                // a wedge narrower than 1/8 of a macro cell.
                if (j < perTransect && cross < crossings.length) crossings[cross++] = j;
                i = j;
            }
            accumulateOrientation(crossings, cross, tStride, stride);
        }
        double[] sorted = Arrays.copyOf(runs, runCount);
        Arrays.sort(sorted);
        double runP10 = percentile(sorted, 0.10);
        double runMedian = percentile(sorted, 0.50);
        double runP90 = percentile(sorted, 0.90);
        double straightP90 = straightCount == 0 ? 0.0 : straightSum / straightCount;

        // ---- area CV per distinct warped lattice cell
        Map<Integer, Integer> cellArea = new HashMap<>();
        for (int i = 0; i < total; i++) cellArea.merge(warpedCell[i], 1, Integer::sum);
        double mean = 0.0;
        for (int v : cellArea.values()) mean += v;
        mean /= Math.max(1, cellArea.size());
        double var = 0.0;
        for (int v : cellArea.values()) var += (v - mean) * (v - mean);
        var /= Math.max(1, cellArea.size());
        double areaCv = mean <= 0.0 ? 0.0 : Math.sqrt(var) / mean;

        // ---- isolated fragments, defined SCALE-AWARDS, reported SEPARATELY for the two
        // distinct notions of "same province". They answer different questions and conflating
        // them hides defects:
        //   * SITE key      — a cell whose own sampled area is a tiny fraction of the MEDIAN
        //     cell area. A cell clipped by the scan border legitimately owns less area, so
        //     comparing against the median is what separates a real speck from a border cell.
        //   * ARCHETYPE key — a connected label that appears only in scattered specks. A whole
        //     archetype may legitimately own a small total area, so the threshold is relative
        //     to the median ARCHETYPE area, not to a single cell.
        int[] areas = areasOf(cellArea);
        double medianArea = median(areas);
        int isolated = 0;
        if (medianArea > 0.0) {
            for (int v : cellArea.values()) {
                if (v < medianArea * 0.05) isolated++;
            }
        }
        Map<Integer, Integer> archetypeArea = new HashMap<>();
        for (int i = 0; i < total; i++) archetypeArea.merge(archetype[i], 1, Integer::sum);
        double medianArchetypeArea = median(areasOf(archetypeArea));
        int archetypeIsolated = 0;
        if (medianArchetypeArea > 0.0) {
            for (int v : archetypeArea.values()) {
                if (v < medianArchetypeArea * 0.05) archetypeIsolated++;
            }
        }

        // ---- scale-aware A-B-A, likewise computed under BOTH keys.
        int siteAba = abaEvents(archetype, perTransect, tCount, stride, warpedCell);
        int archetypeAba = abaEvents(archetype, perTransect, tCount, stride, null);

        Set<Integer> distinct = new HashSet<>();
        for (int i = 0; i < total; i++) distinct.add(archetype[i]);

        double transSum = 0.0, coreSum = 0.0;
        double moveSite = 0.0, moveArch = 0.0;
        int inTransition = 0;
        for (int i = 0; i < total; i++) {
            transSum += transition[i];
            coreSum += core[i];
            if (warpedCell[i] != unwarpedCell[i]) moveSite += 1.0;
            if (archetype[i] != unwarpedArchetype[i]) moveArch += 1.0;
            if (transition[i] < MacroGeography.TRANSITION_CORE_THRESHOLD) inTransition++;
        }
        double meanTransition = transSum / Math.max(1, total);
        double coreShare = coreSum / Math.max(1, total);
        double snapShare = 1.0 - (double) inTransition / Math.max(1, total);
        double moveShare = moveSite / Math.max(1, total);
        double archetypeMoveShare = moveArch / Math.max(1, total);

        // ---- the TRUE median transition band width. The band around a bisector is symmetric and
        // its local half-width at a column is exactly |signedBoundaryDistance|, so the full local
        // width of the band containing that column is 2 * |signedBoundaryDistance|. The median of
        // those per-column widths is a genuine order statistic of the measured distance field.
        //
        // The previous implementation multiplied the MEAN transitionWeight by 2 * HALF_WIDTH,
        // which is not a median of anything: it is a mean of a saturating weight, so it reported
        // a number shaped by the ramp rather than by the geometry, and it moved when H moved even
        // though the distance field had not changed at all.
        double[] bandWidths = new double[total];
        int bandCount = 0;
        double widthSum = 0.0;
        for (int i = 0; i < total; i++) {
            if (transition[i] < MacroGeography.TRANSITION_CORE_THRESHOLD) {
                double w = 2.0 * absBoundary[i];
                bandWidths[bandCount++] = w;
                widthSum += w;
            }
        }
        double[] sortedWidths = Arrays.copyOf(bandWidths, bandCount);
        Arrays.sort(sortedWidths);
        double transitionWidthP50 = percentile(sortedWidths, 0.50);
        double transitionWidthP50Mean = bandCount == 0 ? 0.0 : widthSum / bandCount;

        // ---- attribute gradient: the direct guard against a sharper blend kernel introducing a
        // gradient spike. Measured along the transects from the CONTINUOUS hillMultiplier, so it
        // reflects exactly what the terrain composer reads. Reported per 100 blocks so the number
        // is independent of the sampling step.
        double[] gradients = new double[Math.max(1, tCount * Math.max(1, perTransect - 1))];
        int gradCount = 0;
        for (int t = 0; t < tCount; t++) {
            int base = t * perTransect;
            for (int i = 1; i < perTransect; i++) {
                gradients[gradCount++] = Math.abs(hill[base + i] - hill[base + i - 1]) * 100.0 / stride;
            }
        }
        double[] sortedGrad = Arrays.copyOf(gradients, gradCount);
        Arrays.sort(sortedGrad);
        double attributeGradientP99 = percentile(sortedGrad, 0.99);

        // ---- mean number of border crossings per transect
        double meanNeighbours = 0.0;
        for (int t = 0; t < tCount; t++) {
            int base = t * perTransect;
            int c = 0;
            for (int i = 1; i < perTransect; i++) {
                if (archetype[base + i] != archetype[base + i - 1]) c++;
            }
            meanNeighbours += c;
        }
        meanNeighbours /= Math.max(1, tCount);
        int segments = ANGLE_STATE.get()[0];
        double curvature;
        if (segments <= 1) {
            curvature = 0.0;
        } else {
            // Mean absolute direction change per segment, normalised to [0,1] by pi.
            curvature = (ANGLE_STATE.get()[1] / 1.0e6) / ((segments - 1.0) * Math.PI);
        }

        return new GeographyMetrics(runP10, runMedian, runP90, maxRun, areaCv,
                isolated, siteAba, archetypeIsolated, archetypeAba, distinct.size(),
                normalizedEntropy(), straightP90,
                snapShare, moveShare, archetypeMoveShare,
                transitionWidthP50, transitionWidthP50Mean, coreShare,
                meanNeighbours, curvature, attributeGradientP99,
                total, tCount, stride);
    }

    /** The values of a counting map, as a primitive array. */
    private static int[] areasOf(Map<Integer, Integer> map) {
        int[] out = new int[map.size()];
        int i = 0;
        for (int v : map.values()) out[i++] = v;
        return out;
    }

    /** Median of an array; the array is sorted in place by this call. */
    private static double median(int[] values) {
        if (values.length == 0) return 0.0;
        Arrays.sort(values);
        return values[values.length / 2];
    }

    /**
     * Scale-aware A-B-A count along the transects.
     *
     * <p>A pathological A-B-A is a THIN sliver, not any repeated neighbour. A genuine Voronoi
     * saddle legitimately puts a narrow wedge of a third archetype between two lobes of the same
     * one, and that wedge is a real geometric feature whose width is set by the site spacing.
     * What the old guard/cap chains produced was wedges far THINNER than the lattice can justify,
     * so the scale-aware threshold is a wedge narrower than 1/8 of a macro cell.
     *
     * @param siteKey when non-null, a run counts as A-B-A only if the flanking lobes belong to
     *                the SAME SITE as well; when null the archetype label alone decides
     */
    private static int abaEvents(int[] archetype, int perTransect, int tCount, int stride,
                                 int[] siteKey) {
        int events = 0;
        for (int t = 0; t < tCount; t++) {
            int base = t * perTransect;
            int i = 0;
            while (i < perTransect) {
                int id = archetype[base + i];
                int j = i;
                while (j < perTransect && archetype[base + j] == id) j++;
                double len = (j - i) * (double) stride;
                if (i > 0 && j < perTransect && archetype[base + i - 1] == archetype[base + j]
                        && archetype[base + i - 1] != id
                        && len < MacroSiteLattice.CELL_SIZE / 8.0
                        && (siteKey == null
                            || siteKey[base + i - 1] == siteKey[base + j])) {
                    events++;
                }
                i = j;
            }
        }
        return events;
    }
    // ---------------------------------------------------------------- orientation / curvature

    // These are method-local scratch buffers. GeographyMetrics is used from single-threaded
    // analysis code (tests, diagnostics, preview), never from the chunk hot path, so a
    // per-call buffer holder is safe. The CHUNK GENERATOR never calls into this class.
    private static final ThreadLocal<double[]> ANGLE_BINS = ThreadLocal.withInitial(
            () -> new double[ORIENTATION_BINS]);
    private static final ThreadLocal<int[]> ANGLE_STATE =
            ThreadLocal.withInitial(() -> new int[3]);

    private static void resetOrientation() {
        java.util.Arrays.fill(ANGLE_BINS.get(), 0.0);
        int[] st = ANGLE_STATE.get();
        st[0] = 0;   // total segments
        st[1] = 0;   // curvature sum (x1e6)
        st[2] = -1;  // previous angle (x1e6), -1 = none
    }

    /**
     * Accumulate the direction histogram of one transect's border segments.
     *
     * <p>Consecutive archetype crossings on a transect approximate a border polyline: the
     * segment between crossing {@code i} and crossing {@code i+1} has a direction
     * {@code (dx, dz) = (x_{i+1} - x_i, tStride)}. Binning those directions measures whether
     * the borders run in every direction (organic) or collapse into a few (a lattice imprint
     * or a giant straight wall).
     */
    private static void accumulateOrientation(int[] crossings, int count,
                                              int tStride, int stride) {
        double[] bins = ANGLE_BINS.get();
        int[] st = ANGLE_STATE.get();
        for (int i = 1; i < count; i++) {
            double dx = (crossings[i] - crossings[i - 1]) * (double) stride;
            double dz = (double) tStride;
            if (dx == 0.0 && dz == 0.0) continue;
            double ang = Math.atan2(dz, dx);            // (-pi, pi]
            if (ang < 0.0) ang += Math.PI;              // undirected: a border has no arrow
            int bin = (int) (ang / Math.PI * ORIENTATION_BINS);
            if (bin < 0) bin = 0;
            if (bin >= ORIENTATION_BINS) bin = ORIENTATION_BINS - 1;
            bins[bin] += 1.0;
            st[0]++;
            int cur = (int) (ang * 1.0e6);
            if (st[2] >= 0) {
                int d = cur - st[2];
                // Wrap into (-pi, pi] so the seam does not read as a huge curvature spike.
                if (d > 1_570_796) d -= 3_141_592;
                if (d < -1_570_796) d += 3_141_592;
                st[1] += Math.abs(d);
            }
            st[2] = cur;
        }
    }

    /** Normalized Shannon entropy of the direction histogram, in [0,1]. */
    private static double normalizedEntropy() {
        double[] bins = ANGLE_BINS.get();
        int total = ANGLE_STATE.get()[0];
        if (total <= 0) return 0.0;
        double h = 0.0;
        for (double b : bins) {
            if (b <= 0.0) continue;
            double p = b / total;
            h -= p * Math.log(p);
        }
        return h / Math.log(ORIENTATION_BINS);
    }

    /** The measured curvature sum of the last analysis (diagnostics only). */
    static double rawCurvatureSum() {
        return ANGLE_STATE.get()[1];
    }

    /** The measured segment count of the last analysis (diagnostics only). */
    static int rawSegmentCount() {
        return ANGLE_STATE.get()[0];
    }

    private static double percentile(double[] sorted, double q) {
        if (sorted.length == 0) return 0.0;
        int i = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }
}
