package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialVisualScale;
import com.modscreating.unlimitedspace.client.PlanetPixelTexture;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.SiblingBody;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders celestial bodies as square, pixelated billboards (R12.4 / R12.5 corrective passes).
 *
 * <p>The orbited body (R12.5) is placed with the exact Creating Space Earth Orbit geometry that
 * {@code EarthOrbitEffects.renderAdditionalBody()} / {@code GenericCelestialOrbitEffect.renderAstralBody()}
 * use: a large square billboard (CS half-size 150 blocks) drawn in the camera-fixed sky pose at the
 * plane {@code y = CS_BODY_Y_BASE + 40*(alt+64)/448}, oriented by {@code Axis.YP(-90)} then
 * {@code Axis.XP(180)}. This reproduces CS Earth by construction (large, dominant, below the player,
 * never drifting as the camera rotates) while filling the billboard with the procedural planet's own
 * colours instead of the static {@code earth.png}.
 *
 * <p>Distant sibling bodies are drawn as small square-pixel bodies at fixed sky azimuth/elevation,
 * so they always read as much smaller than the orbited body.
 *
 * <p>Depth writes are always off and GL state is fully restored, so a body can never occlude stars
 * (Bug A) or interfere with the ordinary pick/highlight pass.
 */
public final class PlanetSphereRenderer {

    /** Texture grid side for the current (dominant) orbit body — square low-res with real detail. */
    private static final int BODY_RESOLUTION = 16;

    /** Deterministic light direction for a mild day/night terminator dither. */
    private static final Vector3f LIGHT_DIR = new Vector3f(0.35f, -0.70f, 0.62f).normalize();

    private PlanetSphereRenderer() {
    }

    /**
     * Draw the orbited body as a large, dominant, square pixelated billboard reproducing the
     * Creating Space Earth Orbit placement. Fills it with a deterministic material-derived pixel
     * texture ({@link PlanetPixelTexture}). Leaves GL state restored (depth test on, depth mask on,
     * blend off, cull back on).
     */
    public static void drawBody(PoseStack pose, ResolvedVisual vis, Camera camera) {
        if (vis.onSurface() || !vis.hasBody()) return;

        float half = CelestialVisualScale.currentBodyHalf();

        // CS Earth Orbit placement: uses camera.getEntity().getOnPos().getY() for the altitude.
        double playerY;
        if (camera.getEntity() != null && camera.getEntity().getOnPos() != null) {
            playerY = camera.getEntity().getOnPos().getY();
        } else {
            playerY = camera.getPosition().y;
        }
        float planeY = CelestialVisualScale.currentBodyPlaneY(playerY);

        long seed = Seeds.derive(vis.worldSeed(), "us.client.planet." + vis.bodyCode(), vis.kind().ordinal());
        int[] tex = PlanetPixelTexture.sample(BODY_RESOLUTION, seed,
                vis.surfaceColorArgb(),
                vis.waterColorArgb() == 0 ? vis.surfaceColorArgb() : vis.waterColorArgb(),
                vis.waterBlend(), vis.iceBlend());

        // Reproduce the CS renderAstralBody orientation (alpha branch): YP(-90) then XP(rotX=180),
        // then draw the square billboard in the plane y = planeY spanning x/z in [-half, half].
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(CelestialVisualScale.currentBodyRotY()));
        pose.mulPose(Axis.XP.rotationDegrees(CelestialVisualScale.currentBodyRotX()));

        Matrix4f mat = pose.last().pose();
        Vector3f center = new Vector3f(0.0f, planeY, 0.0f);
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f up = new Vector3f(0.0f, 0.0f, 1.0f);
        drawPixelBody(mat, center, right, up, half, tex, BODY_RESOLUTION);
        pose.popPose();
    }

    /**
     * Draw a distant sibling body as a small square pixelated billboard at its fixed sky
     * azimuth/elevation on the dome. Its texture comes from the body's own material colours
     * (planet or moon), independent of any parent.
     */
    public static void drawSibling(PoseStack pose, SiblingBody body) {
        float half = CelestialVisualScale.siblingHalfSize(body.apparentSize());
        int res = half >= 16f ? 16 : (half >= 10f ? 12 : 8);
        long seed = Seeds.derive((long) body.hashCode() * 0x9E3779B97F4A7C15L,
                "us.client.sibling.render", body.bodyCode().hashCode());
        int water = body.waterColorArgb() == 0 ? body.surfaceColorArgb() : body.waterColorArgb();
        int[] tex = PlanetPixelTexture.sample(res, seed, body.surfaceColorArgb(), water,
                body.waterBlend(), body.iceBlend());

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(body.azimuthDeg()));
        pose.mulPose(Axis.XP.rotationDegrees(body.elevationDeg()));
        pose.translate(0.0f, 0.0f, -CelestialVisualScale.SYSTEM_STAR_DISTANCE);

        // After the dome rotation the local X/Y frame is already camera-facing; body sits at the origin.
        Vector3f center = new Vector3f();
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Matrix4f mat = pose.last().pose();
        drawPixelBody(mat, center, right, up, half, tex, res);
        pose.popPose();
    }

    /**
     * Draw a square, camera-facing pixelated billboard from a precomputed material texture
     * ({@code res*res} texels) on the plane spanned by {@code (right, up)} centred at {@code center}.
     * Nearest-neighbour (each texel = one cell) with a gentle horizontal terminator. Depth writes are
     * disabled so a body can never become a depth occluder; GL state is restored on exit.
     */
    private static void drawPixelBody(Matrix4f mat, Vector3f center, Vector3f right, Vector3f up,
                                      float half, int[] tex, int res) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cell = 2.0f * half / res;
        for (int v = 0; v < res; v++) {
            for (int u = 0; u < res; u++) {
                float x0 = -half + u * cell;
                float x1 = x0 + cell;
                float y0 = -half + v * cell;
                float y1 = y0 + cell;

                int c = tex[v * res + u];
                float r = ((c >> 16) & 0xFF) / 255.0f;
                float g = ((c >> 8) & 0xFF) / 255.0f;
                float b = (c & 0xFF) / 255.0f;

                // gentle horizontal terminator so the disk reads lit on the sun side
                float sh = Math.min(1.0f, 0.82f + 0.18f * ((float) u / Math.max(1, res - 1)));
                r *= sh; g *= sh; b *= sh;

                vertex(builder, mat, center, right, up, x0, y1, r, g, b);
                vertex(builder, mat, center, right, up, x1, y1, r, g, b);
                vertex(builder, mat, center, right, up, x1, y0, r, g, b);
                vertex(builder, mat, center, right, up, x0, y0, r, g, b);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f mat, Vector3f center,
                               Vector3f right, Vector3f up, float dx, float dy,
                               float r, float g, float b) {
        float px = center.x + right.x * dx + up.x * dy;
        float py = center.y + right.y * dx + up.y * dy;
        float pz = center.z + right.z * dx + up.z * dy;
        builder.addVertex(mat, px, py, pz).setColor(r, g, b, 1.0f);
    }
}