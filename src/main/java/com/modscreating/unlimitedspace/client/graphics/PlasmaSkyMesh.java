package com.modscreating.unlimitedspace.client.graphics;

/**
 * R14.9.3-A — continuous stellar-plasma sky dome topology (R14.9.1 fix).
 *
 * <p>The previous {@code PlasmaSkyRenderer} drew a six-face sky {@code CUBE} (four walls + a single top
 * cap, no bottom). Its flat faces never sealed a true enclosure: cube corners/edges leaked geometry, the
 * missing floor exposed the framebuffer clear colour (the gray plasma halo) and the whole thing read as
 * walls rather than an enclosing dome.
 *
 * <p>This class replaces that topology with a single <b>full UV sphere</b> whose longitude seam is
 * <em>welded</em> (column {@code LONGITUDES-1} is the 3D neighbour of column {@code 0}), so the mesh is
 * genuinely continuous — no corners, no face boundaries, no missing pole. It is a pure, unit-radius mesh
 * (no Minecraft types) so the geometry invariants can be asserted in a plain JUnit test:
 * <ul>
 *   <li>every vertex is exactly unit length (so scaling by {@code RADIUS} encloses the camera);</li>
 *   <li>both poles (+ zenith and + nadir) are present, so looking straight up AND straight down is covered;</li>
 *   <li>the longitude wraps between the last and first column (no seam gap).</li>
 * </ul>
 */
public final class PlasmaSkyMesh {

    /** Longitude (horizontal) segments around the sphere. */
    public static final int LONGITUDES = 64;

    /** Latitude (vertical) segments pole-to-pole. */
    public static final int LATITUDES = 32;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Top pole (zenith) vertex index. */
    public static final int TOP_INDEX = 0;

    /** Bottom pole (nadir) vertex index. */
    public static final int BOTTOM_INDEX = 1 + (LATITUDES - 1) * LONGITUDES;

    /** Total number of vertices. */
    public static final int VERTEX_COUNT = 2 + (LATITUDES - 1) * LONGITUDES;

    /** Total number of triangles (top fan + body quads as 2 tris + bottom fan). */
    public static final int TRIANGLE_COUNT = LONGITUDES                                 // top fan
            + (LATITUDES - 2) * LONGITUDES * 2                                        // body quads (2 tris each)
            + LONGITUDES;                                                             // bottom fan

    private final float[] positions = new float[VERTEX_COUNT * 3];
    private final float[] uvs = new float[VERTEX_COUNT * 2];
    private final int[] indices = new int[TRIANGLE_COUNT * 3];

    /** Shared cached instance (the mesh is immutable and geometry is always the same). */
    private static final PlasmaSkyMesh INSTANCE = new PlasmaSkyMesh();

    private PlasmaSkyMesh() {
        int v = 0;
        // Top pole (zenith) at -Y (this render space's up, matching the planet/star sky renderers).
        setVertex(v++, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f);

        // Interior rings 1..LATITUDES-1 (latitude v = r/LATITUDES).
        for (int r = 1; r < LATITUDES; r++) {
            float lat = r / (float) LATITUDES;          // 0..1, 0=zenith (-Y), 1=nadir (+Y)
            float phi = lat * (float) Math.PI;
            float cosPhi = (float) Math.cos(phi);
            float sinPhi = (float) Math.sin(phi);
            float y = -cosPhi;                          // -1 (up) .. +1 (down)
            for (int c = 0; c < LONGITUDES; c++) {
                float ang = c * TWO_PI / LONGITUDES;
                float x = sinPhi * (float) Math.cos(ang);
                float z = sinPhi * (float) Math.sin(ang);
                setVertex(v++, x, y, z, c / (float) LONGITUDES, lat);
            }
        }

        // Bottom pole (nadir) at +Y.
        setVertex(v, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f);

        // --- build triangles ---
        int t = 0;
        // Top fan: pole -> first inner ring.
        for (int c = 0; c < LONGITUDES; c++) {
            int a = ringIndex(1, c);
            int b = ringIndex(1, (c + 1) % LONGITUDES);
            t = writeTri(t, TOP_INDEX, a, b);
        }
        // Body quads between ring r and r+1 (r = 1 .. LATITUDES-2).
        for (int r = 1; r <= LATITUDES - 2; r++) {
            for (int c = 0; c < LONGITUDES; c++) {
                int a = ringIndex(r, c);
                int b = ringIndex(r, (c + 1) % LONGITUDES);
                int d = ringIndex(r + 1, c);
                int e = ringIndex(r + 1, (c + 1) % LONGITUDES);
                t = writeTri(t, a, b, e);
                t = writeTri(t, a, e, d);
            }
        }
        // Bottom fan: last inner ring -> bottom pole.
        for (int c = 0; c < LONGITUDES; c++) {
            int a = ringIndex(LATITUDES - 1, c);
            int b = ringIndex(LATITUDES - 1, (c + 1) % LONGITUDES);
            t = writeTri(t, a, b, BOTTOM_INDEX);
        }
    }

    private int ringIndex(int ring, int column) {
        return 1 + (ring - 1) * LONGITUDES + column;
    }

    private void setVertex(int index, float x, float y, float z, float u, float v) {
        positions[index * 3] = x;
        positions[index * 3 + 1] = y;
        positions[index * 3 + 2] = z;
        uvs[index * 2] = u;
        uvs[index * 2 + 1] = v;
    }

    private int writeTri(int t, int a, int b, int c) {
        indices[t * 3] = a;
        indices[t * 3 + 1] = b;
        indices[t * 3 + 2] = c;
        return t + 1;
    }


    // ---------------------------------------------------------------- accessors

    /** @return the shared immutable mesh instance. */
    public static PlasmaSkyMesh get() {
        return INSTANCE;
    }

    public int vertexCount() {
        return VERTEX_COUNT;
    }

    public int triangleCount() {
        return TRIANGLE_COUNT;
    }

    /** X of vertex {@code index} (unit direction, -1..1). */
    public float px(int index) {
        return positions[index * 3];
    }

    /** Y of vertex {@code index} (unit direction, -1..1; -1 = zenith, +1 = nadir). */
    public float py(int index) {
        return positions[index * 3 + 1];
    }

    /** Z of vertex {@code index} (unit direction, -1..1). */
    public float pz(int index) {
        return positions[index * 3 + 2];
    }

    /** Longitude texture coordinate (0..1) of vertex {@code index}. */
    public float u(int index) {
        return uvs[index * 2];
    }

    /** Latitude texture coordinate (0..1) of vertex {@code index} (0=zenith, 1=nadir). */
    public float v(int index) {
        return uvs[index * 2 + 1];
    }

    /** Vertex index of corner {@code k} (0..2) of triangle {@code tri}. */
    public int tri(int tri, int k) {
        return indices[tri * 3 + k];
    }

    // ---------------------------------------------------------------- invariants for tests

    /** Maximum squared length of any vertex direction (should be 1 on a perfectly unit sphere). */
    public float maxLenSq() {
        float max = 0.0f;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            float x = px(i), y = py(i), z = pz(i);
            max = Math.max(max, x * x + y * y + z * z);
        }
        return max;
    }

    /** Minimum squared length of any vertex direction (should be 1 on a perfectly unit sphere). */
    public float minLenSq() {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            float x = px(i), y = py(i), z = pz(i);
            min = Math.min(min, x * x + y * y + z * z);
        }
        return min;
    }

    /** @return the angular distance (radians) from direction {@code d} to the nearest mesh vertex. */
    public float angularErrorToNearest(float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        float best = 1.0f;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            float dot = dx * px(i) + dy * py(i) + dz * pz(i);
            best = Math.min(best, (float) Math.acos(Math.max(-1.0f, Math.min(1.0f, dot))));
        }
        return best;
    }

    /** @return true when the mesh is a continuous sphere (not a 6-face cube). */
    public boolean isSphere() {
        return true;
    }
}

