package com.modscreating.unlimitedspace.client.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R14.9.3-A — geometry invariants of the continuous stellar-plasma sky dome.
 *
 * <p>The R14.9.2 sky was a six-face CUBE (four walls + one top cap, no bottom). The fix replaces it with
 * {@link PlasmaSkyMesh}, a full UV sphere. These tests prove the mesh (and therefore the skybox) actually
 * has the properties that fix the reported bug:
 * <ul>
 *   <li>every vertex is unit length, so {@code radius = RADIUS} encloses the camera from anywhere;</li>
 *   <li>it covers the zenith AND the nadir (the cube had no bottom face, which is why looking down went gray);</li>
 *   <li>it covers all six axis directions (the enclosing-dome requirement, not a floating object);</li>
 *   <li>it is a continuous sphere, not a cube (no face seams / corners);</li>
 *   <li>it is dense enough (≥64 × 32 subdivisions) for smooth plasma with no visible facets.</li>
 * </ul>
 */
class PlasmaSkyMeshTest {

    private static final float EPS = 1e-4f;

    @Test
    void allVerticesAreExactlyUnitLength() {
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();
        // Both poles + rings built from sin/cos of exact angles; length² must be 1 within float error.
        assertTrue(mesh.minLenSq() > 1.0f - 1e-3f, "a vertex is shorter than unit length (radius != RADIUS)");
        assertTrue(mesh.maxLenSq() < 1.0f + 1e-3f, "a vertex is longer than unit length (radius != RADIUS)");
    }

    @Test
    void meshCoversZenithAndNadir() {
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();
        float zenith = mesh.angularErrorToNearest(0.0f, -1.0f, 0.0f);
        float nadir = mesh.angularErrorToNearest(0.0f, 1.0f, 0.0f);
        assertTrue(zenith < 0.15f, "zenith (-Y) not covered by the dome (" + zenith + " rad) — the cube lacked a top");
        assertTrue(nadir < 0.15f, "nadir (+Y) not covered by the dome (" + nadir + " rad) — the cube lacked a bottom face");
    }

    @Test
    void meshCoversAllSixAxisDirections() {
        // The reported bug is that the sky broke into gray when looking up, down, and into corners.
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();
        float[][] dirs = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        for (float[] d : dirs) {
            float err = mesh.angularErrorToNearest(d[0], d[1], d[2]);
            assertTrue(err < 0.15f,
                    "direction (" + d[0] + "," + d[1] + "," + d[2] + ") is not enclosed by the dome (" + err + " rad)");
        }
    }

    @Test
    void meshHasSufficientCoverageAcrossTheWholeDome() {
        // Sample a lat/long grid of directions; every one must be within a small angular distance of a vertex,
        // i.e. the mesh covers the entire viewing sphere with no gap you could see through.
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();
        double maxErr = 0.0;
        for (int v = 0; v <= 16; v++) {
            double phi = Math.PI * v / 16.0;
            for (int u = 0; u < 32; u++) {
                double theta = 2.0 * Math.PI * u / 32.0;
                float x = (float) (Math.sin(phi) * Math.cos(theta));
                float y = (float) (-Math.cos(phi));
                float z = (float) (Math.sin(phi) * Math.sin(theta));
                maxErr = Math.max(maxErr, mesh.angularErrorToNearest(x, y, z));
            }
        }
        // The mesh is 64×32; its largest cell diagonal is ≈ 0.07 rad. 0.20 gives comfortable margin.
        assertTrue(maxErr < 0.20, "a whole-dome gap remains: max angular error " + maxErr + " rad");
    }

    @Test
    void noCubeFaceImplementationRemains() {
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();
        // A 6-face cube would have 12 triangles; our 64×32 sphere has far more, and is a sphere (not a cube).
        assertTrue(mesh.isSphere(), "the sky mesh is no longer a cube");
        assertTrue(mesh.triangleCount() > 24, "a cube-class mesh would have far fewer triangles");
        assertEquals(64, PlasmaSkyMesh.LONGITUDES, "expected 64 longitude subdivisions");
        assertEquals(32, PlasmaSkyMesh.LATITUDES, "expected 32 latitude subdivisions");
    }
}
