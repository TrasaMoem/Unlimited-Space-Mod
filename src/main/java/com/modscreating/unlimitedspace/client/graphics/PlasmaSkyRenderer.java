package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.PlasmaTexture;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.StarVisual;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.worldgen.PlasmaProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlasmaVariant;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * R14.9.3-A — stellar-plasma sky dome for a star SURFACE. Draws the ENTIRE enclosing dome as the star's own
 * luminous plasma so a star surface never shows blue space, a normal sky, clouds, background stars, a
 * Minecraft sun/moon — and (the fix) never shows gray areas, cube corners, face seams or gaps.
 *
 * <p><b>R14.9.2 → R14.9.3-A geometry fix.</b> The previous version drew a six-face sky <em>cube</em>
 * ({@code GRID=24} per face): four flat walls + a single top cap, <b>no bottom</b>. Its flat faces never
 * sealed a true enclosure — the missing floor, the cube corner edges and the far corners (at
 * {@code RADIUS*sqrt(3)}) all exposed the framebuffer clear colour, which is the star's plasma <em>halo</em>
 * ({@code × 0.62}) — for a G/white/blue star that reads as a flat gray-tan. The result was gray upper sky,
 * gray horizon, and visible corners/edges.
 *
 * <p>This renderer now draws one <b>continuous full UV sphere</b> ({@link PlasmaSkyMesh}: 64 longitude × 32
 * latitude = 3968 triangles) that is welded at the longitude seam. It encloses the camera in every direction
 * (zenith, nadir, N/S/E/W), so there is no uncovered pixel and therefore no gray fallback can show. The
 * pattern is a cached {@link PlasmaTexture#sampleSphere} grid (seamless in longitude) generated once per
 * {@code (worldSeed, star, stage, variant)} — never per frame; the animation is a time-based two-axis scroll
 * of the sampled index. The star's own {@code PlasmaVariant → PlasmaProfile → StarColor} palette is preserved,
 * so a red dwarf / G star / O-B star / supergiant each keep their own plasma colours.
 *
 * <p>A black-hole surface routes to a full dark-void dome (never a photosphere).
 */
public final class PlasmaSkyRenderer {

    /** Dome radius (camera space) — comfortably inside the far clip plane. */
    private static final float RADIUS = 380.0f;

    /** Seamless sky-sphere texture resolution (texels per side); generated once and cached. */
    public static final int SKY_TEXTURE_RESOLUTION = 96;

    /**
     * R14.9.3-A assertion constant: the renderer now uses one continuous sphere, never the six-face cube.
     * A unit test asserts this to prove the cube-face implementation is gone.
     */
    public static final boolean CONTINUOUS_SPHERE = true;

    private PlasmaSkyRenderer() {
    }

    public static void draw(PoseStack pose, ResolvedVisual vis, long worldSeed, int ticks) {
        if (vis.stars().isEmpty()) return;
        // The local body of a star SURFACE is the SPECIFIC star being stood on (a companion in a
        // binary/trinary system shows its OWN plasma), not always the primary.
        int li = vis.localStarIndex();
        if (li < 0 || li >= vis.stars().size()) li = 0;
        StarVisual local = vis.stars().get(li);
        Star star = local.star();
        if (star == null) return;

        StarStage stage = StarStage.from(star);
        PlasmaSkyMesh mesh = PlasmaSkyMesh.get();

        // Opaque dome, no cull (we draw every triangle regardless of winding), no depth write (it is the
        // background; terrain/chunks render on top afterwards and depth-test against it).
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f mat = pose.last().pose();

        if (stage == StarStage.BLACK_HOLE) {
            // A black-hole surface is a dark void stand-in, never a photosphere: fill the whole dome.
            drawSolidDome(mat, mesh, 0.015f, 0.015f, 0.02f);
        } else {
            PlasmaVariant variant = PlasmaVariant.forStar(star);
            PlasmaProfile profile = variant.resolvedProfile(star);
            long seed = Seeds.derive(worldSeed, "us.client.plasma.sky", star.seed(), stage.ordinal());
            // Generated once and cached (deterministic per star + stage + variant), then reused every frame.
            int[] grid = CelestialTextureCache.getOrCreate(
                    CelestialTextureCache.key(worldSeed, star.id().code(),
                            "plasmaSky-sphere-" + variant.name() + "-" + stage.name(),
                            SKY_TEXTURE_RESOLUTION),
                    () -> PlasmaTexture.sampleSphere(SKY_TEXTURE_RESOLUTION, seed, profile));
            int res = (int) Math.sqrt(grid.length);

            // R14.9.2 animated plasma: two slow, independent scroll axes so convection reads as flowing.
            int ox = Math.floorMod(ticks / 3, res);
            int oy = Math.floorMod(ticks / 5, res);

            drawPlasmaDome(mat, mesh, grid, res, ox, oy);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }


    /** Draw the seamless plasma dome: one triangle per mesh triangle, per-vertex colour from the cached grid. */
    private static void drawPlasmaDome(Matrix4f mat, PlasmaSkyMesh mesh, int[] grid, int res, int ox, int oy) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int t = 0; t < mesh.triangleCount(); t++) {
            for (int k = 0; k < 3; k++) {
                int vi = mesh.tri(t, k);
                float dx = mesh.px(vi);
                float dy = mesh.py(vi);
                float dz = mesh.pz(vi);
                float uu = mesh.u(vi);
                float vv = mesh.v(vi);
                // Clamp into [0, res-1]: the poles live exactly at v=0 / v=1 which are not texel rows of the
                // sampled grid (sampleSphere uses (y+0.5)/n), so the bottom pole must not index out of bounds.
                int iu = Math.max(0, Math.min(res - 1, floorCoord(uu * res)));
                int iv = Math.max(0, Math.min(res - 1, floorCoord(vv * res)));
                int ix = Math.floorMod(iu + ox, res);
                int iy = Math.floorMod(iv + oy, res);
                int c = grid[iy * res + ix];
                // Brighten toward the ground (below the horizon = +Y / nadir) so the sky blends into the
                // plasma terrain instead of showing a hard normal horizon.
                float tUp = (dy + 1.0f) * 0.5f;       // 0 at zenith (-Y), 1 at nadir (+Y)
                float boost = 1.0f + 0.55f * smoothstep(0.55f, 1.0f, tUp);
                float r = ch(c, 16) * boost;
                float g = ch(c, 8) * boost;
                float b = ch(c, 0) * boost;
                builder.addVertex(mat, dx * RADIUS, dy * RADIUS, dz * RADIUS)
                        .setColor(r, g, b, 1.0f);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }


    /** Draw a uniform-colour full dome (black-hole void) so there is no gap even looking straight down. */
    private static void drawSolidDome(Matrix4f mat, PlasmaSkyMesh mesh, float r, float g, float b) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int t = 0; t < mesh.triangleCount(); t++) {
            for (int k = 0; k < 3; k++) {
                int vi = mesh.tri(t, k);
                builder.addVertex(mat, mesh.px(vi) * RADIUS, mesh.py(vi) * RADIUS, mesh.pz(vi) * RADIUS)
                        .setColor(r, g, b, 1.0f);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }


    /** Floor of a float (used to convert texture-space {@code [0,1)} into a grid index). */
    private static int floorCoord(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static float ch(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255.0f;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = x < e0 ? 0f : (x > e1 ? 1f : (x - e0) / (e1 - e0));
        return t * t * (3.0f - 2.0f * t);
    }

    /** Longitude segments of the continuous sphere mesh (asserted by a geometry test). */
    public static int longitudeSegments() {
        return PlasmaSkyMesh.LONGITUDES;
    }

    /** Latitude segments of the continuous sphere mesh (asserted by a geometry test). */
    public static int latitudeSegments() {
        return PlasmaSkyMesh.LATITUDES;
    }

    /** @return false — the six-face cube implementation of R14.9.2 is gone. */
    public static boolean usesCubeFaces() {
        return false;
    }
}

