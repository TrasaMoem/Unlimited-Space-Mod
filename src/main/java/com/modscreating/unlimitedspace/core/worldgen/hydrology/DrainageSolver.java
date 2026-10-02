package com.modscreating.unlimitedspace.core.worldgen.hydrology;

/**
 * Deterministic hydrological solver: pit filling, D8 routing, flow accumulation, rivers, lakes.
 *
 * <p>Pipeline (the V3 spec order):
 * <ol>
 *   <li><b>Priority-flood pit filling</b> — every cell is raised to the level from which water
 *       standing in it could escape to the window border:</li>
 * </ol>
 *
 * <pre>
 *   filled[i] = min over neighbours j of max( h[i], filled[j] ),   border cells: filled = h
 * </pre>
 *
 * <p>This is a shortest-path problem over a monotone (min, max) semiring, solved exactly with a
 * min-heap seeded from the border — the classic priority flood.
 * <ol start="2">
 *   <li><b>D8 routing</b> — every cell drains toward its LOWEST filled neighbour. Using the lowest
 *       neighbour (rather than requiring a strictly lower one) is what keeps the network connected
 *       across the level lake floors that a priority flood inevitably creates.</li>
 *   <li><b>Flow accumulation</b> — discharge summed downstream, weighted by precipitation, with
 *       cells processed in descending filled elevation (a valid topological order).</li>
 *   <li><b>River mask</b> from the discharge, and <b>lake depth = filled - original</b>, so every
 *       lake is a genuine topographic depression — never a drawn circle or a repeated template.</li>
 * </ol>
 *
 * <p>PERFORMANCE & DETERMINISM. The solver runs on the {@link HydrologyTile#GRID} square drainage
 * lattice and allocates nothing per cell: the heap and the ordering array are primitive, reused
 * across solves, and every comparison is a total order on {@code (level, index)}, so the whole
 * solve is bit-exact reproducible for a given input.
 */
public final class DrainageSolver {

    // D8 8-neighborhood offsets: N, NE, E, SE, S, SW, W, NW
    public static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    public static final int[] DZ = {-1, -1, 0, 1, 1, 1, 0, -1};
    public static final double[] DIST = {
            1.0, 1.41421356, 1.0, 1.41421356, 1.0, 1.41421356, 1.0, 1.41421356
    };

    /** Elevation quantisation of the heap key (1/64 block — far below the vertical budget). */
    private static final float QUANT = 64.0f;
    private static final int QUANT_BIAS = 1 << 24;

    /** Reusable primitive buffers, so repeated tile solves allocate nothing after the first. */
    private static final ThreadLocal<long[]> HEAP = ThreadLocal.withInitial(() -> new long[0]);
    private static final ThreadLocal<long[]> ORDER = ThreadLocal.withInitial(() -> new long[0]);

    private DrainageSolver() {}

    /**
     * Solve hydrology over a square drainage grid of side {@code size} into the scratch buffers.
     *
     * @param scratch scratch buffers (at least {@code size * size} cells each)
     * @param size grid side (HydrologyTile.GRID)
     * @param precipitation continuous precipitation weight in [0, 1]
     * @param minRiverAccum flow-accumulation threshold that turns a cell into a river
     */
    public static void solve(HydrologyScratch scratch, int size, double precipitation,
                             double minRiverAccum) {
        int n = size * size;
        float[] h = scratch.elevation;
        float[] filled = scratch.filled;
        float[] flow = scratch.flowAccumulation;
        float[] river = scratch.riverStrength;
        float[] lake = scratch.lakeDepth;
        byte[] dir = scratch.drainageDir;

        // Heap capacity: every cell can be pushed more than once (a classic priority flood
        // re-pushes a cell whenever a better spill level is found), so the buffer is sized for the
        // worst case rather than for the cell count.
        long[] heap = fitHeap(HEAP.get(), 4 * n + 8 * size + 16);
        long[] order = fitOrder(ORDER.get(), n);

        // ---------------- 1. PRIORITY-FLOOD PIT FILLING ----------------
        java.util.Arrays.fill(filled, Float.POSITIVE_INFINITY);
        int heapSize = 0;
        // Seed every border cell at its own elevation: water at the border leaves the window.
        for (int x = 0; x < size; x++) {
            heap[heapSize++] = pack(h[x], x);
            int bottom = (size - 1) * size + x;
            heap[heapSize++] = pack(h[bottom], bottom);
        }
        for (int z = 1; z < size - 1; z++) {
            int left = z * size;
            heap[heapSize++] = pack(h[left], left);
            heap[heapSize++] = pack(h[left + size - 1], left + size - 1);
        }
        heapSize = heapify(heap, heapSize);
        while (heapSize > 0) {
            long top = heap[0];
            heap[0] = heap[--heapSize];
            heapSize = siftDown(heap, heapSize);
            int idx = (int) top;
            float level = unpackLevel(top);
            filled[idx] = level;
            int cx = idx % size;
            int cz = idx / size;
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d];
                int nz = cz + DZ[d];
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                int nIdx = nz * size + nx;
                float candidate = Math.max(h[nIdx], level);
                if (candidate < filled[nIdx]) {
                    filled[nIdx] = candidate;
                    heap[heapSize++] = pack(candidate, nIdx);
                    // The new element sits at heapSize - 1, so siftUp must be given the FULL size.
                    heapSize = siftUp(heap, heapSize);
                }
            }
        }

        // ---------------- 2. LAKE DEPTH + SEED DISCHARGE ----------------
        // lakeDepth = filledHeight - originalHeight: a lake exists ONLY where the priority flood
        // found a genuine depression, so a lake can never be a drawn circle or a repeated template.
        // Every cell also starts with its own share of precipitation, which is the discharge source
        // term that the accumulation below sums downstream.
        float seed = (float) Math.max(0.05, precipitation);
        for (int i = 0; i < n; i++) {
            float f = filled[i];
            if (!Float.isFinite(f)) {
                // A cell the flood could not reach (fully enclosed window): treat it as an outlet.
                f = h[i];
                filled[i] = f;
            }
            lake[i] = Math.max(0.0f, f - h[i]);
            flow[i] = seed;
        }

        // ---------------- 3. D8 ROUTING (deterministic) ----------------
        // Every cell drains toward its LOWEST filled neighbour. Where the priority flood has left a
        // perfectly LEVEL surface (a filled lake floor, a basin outlet), no neighbour is lower, so a
        // strict-descent rule would dead-end the flow and the discharge would never accumulate —
        // cutting the network into fragments. On such a plateau the flow is therefore routed to the
        // equal-height neighbour that is CLOSEST TO THE WINDOW BORDER. That is monotone decreasing
        // in the distance to the border, so it is well defined, terminates at an outlet, and can
        // never form a cycle.
        for (int z = 0; z < size; z++) {
            int row = z * size;
            for (int x = 0; x < size; x++) {
                int idx = row + x;
                float curH = filled[idx];
                float best = Float.POSITIVE_INFINITY;
                byte bestDir = -1;
                byte plateauDir = -1;
                int bestBorder = borderDistance(x, z, size);
                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d];
                    int nz = z + DZ[d];
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                    float nh = filled[nz * size + nx];
                    // Strict <: on a tie the FIRST neighbour wins, so routing is fully deterministic.
                    if (nh < best) {
                        best = nh;
                        bestDir = (byte) d;
                    }
                    if (nh <= curH) {
                        int bd = borderDistance(nx, nz, size);
                        if (bd < bestBorder) {
                            bestBorder = bd;
                            plateauDir = (byte) d;
                        }
                    }
                }
                if (best < curH) {
                    dir[idx] = bestDir;              // normal steepest descent
                } else {
                    dir[idx] = plateauDir;           // level floor: spill toward the border
                }
            }
        }

        // ---------------- 4. FLOW ACCUMULATION (descending, no boxing) ----------------
        // Descending filled elevation is a valid topological order for D8, so one pass suffices.
        for (int i = 0; i < n; i++) {
            order[i] = pack(-filled[i], i);
        }
        java.util.Arrays.sort(order);
        for (int k = 0; k < n; k++) {
            int idx = (int) order[k];
            int d = dir[idx];
            if (d < 0) continue;
            int cx = idx % size;
            int cz = idx / size;
            int tx = cx + DX[d];
            int tz = cz + DZ[d];
            if (tx < 0 || tx >= size || tz < 0 || tz >= size) continue;
            flow[tz * size + tx] += flow[idx];
        }

        // ---------------- 5. RIVER MASK FROM DISCHARGE ----------------
        float threshold = (float) Math.max(10.0, minRiverAccum);
        for (int i = 0; i < n; i++) {
            float f = flow[i];
            river[i] = f >= threshold
                    ? Math.min(1.0f, (f - threshold) / (threshold * 10.0f))
                    : 0.0f;
        }
    }

    /** Grow the reusable heap buffer to at least {@code n} entries. */
    private static long[] fitHeap(long[] buf, int n) {
        if (buf.length >= n) return buf;
        long[] bigger = new long[n];
        HEAP.set(bigger);
        return bigger;
    }

    /** Grow the reusable ordering buffer to at least {@code n} entries. */
    private static long[] fitOrder(long[] buf, int n) {
        if (buf.length >= n) return buf;
        long[] bigger = new long[n];
        ORDER.set(bigger);
        return bigger;
    }

    /** Build a min-heap from the unsorted prefix of {@code heap}; returns the heap size. */
    private static int heapify(long[] heap, int size) {
        for (int i = (size >>> 1) - 1; i >= 0; i--) {
            size = siftDownAt(heap, size, i);
        }
        return size;
    }

    /** Sift the element at {@code i} down; returns the (unchanged) heap size. */
    private static int siftDown(long[] heap, int size) {
        return siftDownAt(heap, size, 0);
    }

    private static int siftDownAt(long[] heap, int size, int i) {
        while (true) {
            int l = 2 * i + 1;
            int r = l + 1;
            int small = i;
            if (l < size && heap[l] < heap[small]) small = l;
            if (r < size && heap[r] < heap[small]) small = r;
            if (small == i) break;
            long tmp = heap[small];
            heap[small] = heap[i];
            heap[i] = tmp;
            i = small;
        }
        return size;
    }

    /** Move the element at {@code i} to its place; returns the new heap size. */
    private static int siftUp(long[] heap, int size) {
        int i = size - 1;
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (heap[parent] <= heap[i]) break;
            long tmp = heap[parent];
            heap[parent] = heap[i];
            heap[i] = tmp;
            i = parent;
        }
        return size;
    }

    /** Distance (in cells) from a grid position to the window border. */
    private static int borderDistance(int x, int z, int size) {
        int dx = Math.min(x, size - 1 - x);
        int dz = Math.min(z, size - 1 - z);
        return dx < dz ? dx : dz;
    }

    /**
     * Pack a float level and a cell index into one long: the high 32 bits are the level quantised
     * and biased into the positive int range, the low 32 bits the index. The induced order is total
     * (level first, then index), which is what makes the heap pop order — and the whole solve —
     * deterministic and free of ties.
     */
    private static long pack(float level, int index) {
        int q = (int) (level * QUANT) + QUANT_BIAS;
        return (((long) q) << 32) | (index & 0xFFFFFFFFL);
    }

    /** Recover the float level encoded in a heap key. */
    private static float unpackLevel(long key) {
        return ((int) (key >>> 32) - QUANT_BIAS) / QUANT;
    }
}
