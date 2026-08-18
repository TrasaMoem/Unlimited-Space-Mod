package com.modscreating.unlimitedspace.client.graphics;

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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders celestial bodies as square, pixelated (voxel-style) cubes (R12.3 Bug #2).
 *
 * <ul>
 *   <li>{@link #drawBody}: the planet/moon being orbited. Rendered LARGE below the camera so it
 *       fills the lower half of the sky, like Creating Space's Earth orbit (but as a crisp pixel
 *       cube rather than a smooth sphere).</li>
 *   <li>{@link #drawSibling}: every other planet/moon of the system, drawn as a smaller pixel
 *       cube at its fixed sky position, with an apparent size that shrinks with distance.</li>
 * </ul>
 *
 * <p>Each face is subdivided into a coarse cell grid and every cell is flat-shaded with a
 * deterministic colour (water / ice / day-night terminator at the cell centre), so hard pixel
 * edges appear between neighbours — the square-pixel look. Face culling is disabled so the cube
 * reads correctly from any viewing angle.
 */
public final class PlanetSphereRenderer {

    /** Cells per cube face — coarse = visually blocky / pixelated. */
    private static final int CELLS = 6;

    /** Sky-dome distance (camera space) where distant sibling bodies sit. */
    private static final float SKY_DISTANCE = 480.0f;

    /** Deterministic light direction for the day/night terminator. */
    private static final Vector3f LIGHT_DIR = new Vector3f(0.35f, -0.70f, 0.62f).normalize();

    private PlanetSphereRenderer() {
    }

    /**
     * Draw the orbited body below the camera as a large pixel cube. Leaves GL state restored
     * (depth test on, depth mask on, blend off, cull back on).
     */
    public static void drawBody(PoseStack pose, ResolvedVisual vis, Camera camera) {
        if (vis.onSurface() || !vis.hasBody()) return;

        // Large: half-size scales with the body radius so it fills a big part of the lower sky.
        float half = 120.0f + 100.0f * (float) Math.min(vis.radiusProfile(), 4.0);
        double alt = Mth.clamp(camera.getPosition().y, -64.0, 320.0);
        float altFrac = (float) ((alt + 64.0) / 384.0);
        // Push the planet down (camera-space +Y is screen-up) so it hangs below the player.
        float posY = half * (1.05f + 0.35f * altFrac);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        pose.pushPose();
        pose.translate(0.0f, -posY, 0.0f);
        Matrix4f mat = pose.last().pose();

        float[] surface = argbToFloats(vis.surfaceColorArgb());
        float[] water = argbToFloats(vis.waterColorArgb());
        long seed = Seeds.derive(vis.worldSeed(), "us.client.planet." + vis.bodyCode(), vis.kind().ordinal());

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addPixelCube(builder, mat, half, surface, water, vis.waterBlend(), vis.iceBlend(), seed, true);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        pose.popPose();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Draw a distant sibling body as a small pixel cube at its fixed sky azimuth/elevation.
     * Apparent size already encodes the distance from the player (see {@link SiblingBody}).
     */
    public static void drawSibling(PoseStack pose, SiblingBody body) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(body.azimuthDeg()));
        pose.mulPose(Axis.XP.rotationDegrees(body.elevationDeg()));
        pose.translate(0.0f, 0.0f, -SKY_DISTANCE);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f mat = pose.last().pose();
        float[] surface = argbToFloats(body.surfaceColorArgb());
        float[] water = argbToFloats(body.waterColorArgb());
        long seed = Seeds.derive((long) body.hashCode() * 0x9E3779B97F4A7C15L,
                "us.client.sibling.render", body.bodyCode().hashCode());

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addPixelCube(builder, mat, body.apparentSize(), surface, water,
                body.waterBlend(), body.iceBlend(), seed, false);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /**
     * Append a pixelated cube of the given half-size to the buffer. Every face is a {@link #CELLS}
     * grid of flat quads; each cell is shaded from {@code surface} (with optional oceans and ice
     * caps) and a deterministic day/night terminator ({@code applyLight}).
     */
    private static void addPixelCube(BufferBuilder builder, Matrix4f mat, float half,
                                     float[] surface, float[] water, float waterBlend, float iceBlend,
                                     long seed, boolean applyLight) {
        float cell = 2.0f * half / CELLS;
        for (int face = 0; face < 6; face++) {
            for (int u = 0; u < CELLS; u++) {
                for (int v = 0; v < CELLS; v++) {
                    float c0 = -half + cell * u;
                    float c1 = c0 + cell;
                    float g0 = -half + cell * v;
                    float g1 = g0 + cell;

                    float[] norm = new float[3];
                                        float[][] p = cornersFor(face, c0, c1, g0, g1, half, norm);

                    long cellSeed = Seeds.derive(seed, "cell", (long) face, (long) u, (long) v);
                    float[] color = cellColor(surface, water, waterBlend, iceBlend, cellSeed,
                            norm, applyLight);

                    for (int i = 0; i < 4; i++) {
                        builder.addVertex(mat, p[i][0], p[i][1], p[i][2])
                                .setColor(color[0], color[1], color[2], 1.0f);
                    }
                }
            }
        }
    }

    /** Four corners of one grid cell on the given cube face (CCW), plus the outward normal. */
    private static float[][] cornersFor(int face, float c0, float c1, float g0, float g1,
                                        float half, float[] norm) {
        return switch (face) {
            // +X face. Previously this duplicated the +Z face (geometry on z=1), dropping
            // the +X face and leaving coincident quads on z=1 -> a wire-frame 'stick' edge-on.
            case 0 -> { norm[0] = 1; norm[1] = 0; norm[2] = 0;
                yield new float[][]{{half, g0, c0}, {half, g0, c1}, {half, g1, c1}, {half, g1, c0}}; }
            case 1 -> { norm[0] = -1; norm[1] = 0; norm[2] = 0;
                yield new float[][]{{-half, g0, c1}, {-half, g0, c0}, {-half, g1, c0}, {-half, g1, c1}}; }
            case 2 -> { norm[0] = 0; norm[1] = 1; norm[2] = 0;
                yield new float[][]{{c0, half, g0}, {c1, half, g0}, {c1, half, g1}, {c0, half, g1}}; }
            case 3 -> { norm[0] = 0; norm[1] = -1; norm[2] = 0;
                yield new float[][]{{c0, -half, g1}, {c1, -half, g1}, {c1, -half, g0}, {c0, -half, g0}}; }
            case 4 -> { norm[0] = 0; norm[1] = 0; norm[2] = 1;
                yield new float[][]{{c0, g0, half}, {c1, g0, half}, {c1, g1, half}, {c0, g1, half}}; }
            default -> { norm[0] = 0; norm[1] = 0; norm[2] = -1;
                yield new float[][]{{c1, g0, -half}, {c0, g0, -half}, {c0, g1, -half}, {c1, g1, -half}}; }
        };
    }

    /** One deterministic, flat-shaded cell colour (surface / water / ice + optional terminator). */
    private static float[] cellColor(float[] surface, float[] water, float waterBlend, float iceBlend,
                                     long cellSeed, float[] norm, boolean applyLight) {
        float oceanChance = (float) Seeds.fraction(cellSeed, 1L);
        float r = surface[0], g = surface[1], b = surface[2];
        if (oceanChance < waterBlend) {
            r = water[0];
            g = water[1];
            b = water[2];
        }
        // Ice caps on cold bodies (approximate the poles on the +/-Y faces).
        if (iceBlend > 0.0f && norm[1] != 0.0f &&
                Seeds.fraction(cellSeed, 2L) < 0.6f * iceBlend) {
            r = r + (1.0f - r) * 0.85f * iceBlend;
            g = g + (1.0f - g) * 0.85f * iceBlend;
            b = b + (1.0f - b) * 0.9f * iceBlend;
        }
        if (applyLight) {
            float diff = Math.max(0.0f, norm[0] * LIGHT_DIR.x + norm[1] * LIGHT_DIR.y + norm[2] * LIGHT_DIR.z);
            float shade = 0.30f + 0.70f * diff;
            r *= shade;
            g *= shade;
            b *= shade;
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

