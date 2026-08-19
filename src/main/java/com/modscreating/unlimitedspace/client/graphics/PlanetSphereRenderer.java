package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.CelestialVisualScale;
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

    /** Cells per billboard side — coarse = visually blocky / pixelated. */
    private static final int CELLS = 6;

    /** Deterministic light direction for the day/night terminator dither. */
    private static final Vector3f LIGHT_DIR = new Vector3f(0.35f, -0.70f, 0.62f).normalize();

    private PlanetSphereRenderer() {
    }

    /**
     * Draw the orbited body as a large, dominant, square pixelated billboard reproducing the
     * Creating Space Earth Orbit placement. Leaves GL state restored (depth test on, depth mask on,
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

        float[] surface = argbToFloats(vis.surfaceColorArgb());
        float[] water = vis.waterColorArgb() == 0 ? surface : argbToFloats(vis.waterColorArgb());
        long seed = Seeds.derive(vis.worldSeed(), "us.client.planet." + vis.bodyCode(), vis.kind().ordinal());

        // Reproduce the CS renderAstralBody orientation (alpha branch): YP(-90) then XP(rotX=180),
        // then draw the square billboard in the plane y = planeY spanning x/z in [-half, half].
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(CelestialVisualScale.currentBodyRotY()));
        pose.mulPose(Axis.XP.rotationDegrees(CelestialVisualScale.currentBodyRotX()));

        Matrix4f mat = pose.last().pose();
        Vector3f center = new Vector3f(0.0f, planeY, 0.0f);
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f up = new Vector3f(0.0f, 0.0f, 1.0f);
        drawPixelBillCell(mat, center, right, up, half, surface, water,
                vis.waterBlend(), vis.iceBlend(), seed, true);
        pose.popPose();
    }


    /**
     * Draw a distant sibling body as a small square pixelated billboard at its fixed sky
     * azimuth/elevation on the dome (distant planets / moons always read as much smaller bodies).
     */
    public static void drawSibling(PoseStack pose, SiblingBody body) {
        float half = CelestialVisualScale.siblingHalfSize(body.apparentSize());
        float[] surface = argbToFloats(body.surfaceColorArgb());
        float[] water = body.waterColorArgb() == 0 ? surface : argbToFloats(body.waterColorArgb());
        long seed = Seeds.derive((long) body.hashCode() * 0x9E3779B97F4A7C15L,
                "us.client.sibling.render", body.bodyCode().hashCode());

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(body.azimuthDeg()));
        pose.mulPose(Axis.XP.rotationDegrees(body.elevationDeg()));
        pose.translate(0.0f, 0.0f, -CelestialVisualScale.SYSTEM_STAR_DISTANCE);

        // After the dome rotation the local X/Y frame is already camera-facing; body sits at the origin.
        Vector3f center = new Vector3f();
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Matrix4f mat = pose.last().pose();
        drawPixelBillCell(mat, center, right, up, half, surface, water,
                body.waterBlend(), body.iceBlend(), seed, false);
        pose.popPose();
    }

    /**
     * Draw a square, camera-facing "pixelated" billboard body: a CELLS×CELLS grid of flat-shaded cells
     * on the plane spanned by {@code (right, up)} centred at {@code center}, each cell coloured
     * deterministically (oceans / ice caps / a light dither). Depth writes are disabled so a body can
     * never become a depth occluder; GL state is restored on exit.
     */
    private static void drawPixelBillCell(Matrix4f mat, Vector3f center, Vector3f right, Vector3f up,
                                          float half,
                                          float[] surface, float[] water, float waterBlend, float iceBlend,
                                          long seed, boolean applyLight) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int u = 0; u < CELLS; u++) {
            for (int v = 0; v < CELLS; v++) {
                float x0 = -half + (float) u / CELLS * 2.0f * half;
                float x1 = -half + (float) (u + 1) / CELLS * 2.0f * half;
                float y0 = -half + (float) v / CELLS * 2.0f * half;
                float y1 = -half + (float) (v + 1) / CELLS * 2.0f * half;

                long cellSeed = Seeds.derive(seed, "cell", (long) u, (long) v);
                boolean iceCap = (v == 0 || v == CELLS - 1);
                float[] color = cellColor(surface, water, waterBlend, iceBlend, cellSeed, iceCap, applyLight);

                vertex(builder, mat, center, right, up, x0, y1, color);
                vertex(builder, mat, center, right, up, x1, y1, color);
                vertex(builder, mat, center, right, up, x1, y0, color);
                vertex(builder, mat, center, right, up, x0, y0, color);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f mat, Vector3f center,
                               Vector3f right, Vector3f up, float dx, float dy, float[] color) {
        float px = center.x + right.x * dx + up.x * dy;
        float py = center.y + right.y * dx + up.y * dy;
        float pz = center.z + right.z * dx + up.z * dy;
        builder.addVertex(mat, px, py, pz).setColor(color[0], color[1], color[2], 1.0f);
    }

    /** One deterministic, flat-shaded cell colour (surface / water / ice + per-cell light dither). */
    private static float[] cellColor(float[] surface, float[] water, float waterBlend, float iceBlend,
                                     long cellSeed, boolean iceCap, boolean applyLight) {
        float r = surface[0], g = surface[1], b = surface[2];
        if (Seeds.fraction(cellSeed, 1L) < waterBlend) {
            r = water[0]; g = water[1]; b = water[2];
        }
        if (iceBlend > 0.0f && iceCap && Seeds.fraction(cellSeed, 2L) < 0.6f * iceBlend) {
            r = r + (1.0f - r) * 0.85f * iceBlend;
            g = g + (1.0f - g) * 0.85f * iceBlend;
            b = b + (1.0f - b) * 0.9f * iceBlend;
        }
        if (applyLight) {
            float nx = (float) (Seeds.fraction(cellSeed, 6L) - 0.5) * 0.7f;
            float ny = (float) (Seeds.fraction(cellSeed, 7L) - 0.5) * 0.7f;
            float nz = (float) Math.sqrt(Math.max(0.0, 1.0 - nx * nx - ny * ny));
            float diff = Math.max(0.0f, nx * LIGHT_DIR.x + ny * LIGHT_DIR.y + nz * LIGHT_DIR.z);
            float shade = 0.30f + 0.70f * diff;
            r *= shade; g *= shade; b *= shade;
        }
        return new float[]{r, g, b};
    }

    private static float[] argbToFloats(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
        };
    }
}