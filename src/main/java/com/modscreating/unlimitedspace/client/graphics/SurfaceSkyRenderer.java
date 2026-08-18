package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.ResolvedVisual;
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
 * Procedural sky dome for planet/moon surface worlds (R12). Draws a vertical
 * gradient: zenith uses the planet's {@code PlanetVisualProfile.skyColor}, the
 * horizon fades into the fog colour. Vanilla sky is skipped via the
 * dimension-effects {@code renderSky} override.
 */
public final class SurfaceSkyRenderer {

    /** Sky dome radius (camera space). */
    private static final float RADIUS = 380.0f;

    private SurfaceSkyRenderer() {
    }

    public static void draw(PoseStack pose, ResolvedVisual vis) {
        float[] zenith = argbToFloats(vis.skyColorArgb());
        float[] horizon = argbToFloats(vis.fogColorArgb());

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = pose.last().pose();
        float r = RADIUS;

        // Top cap (zenith, slightly darkened so the horizon feels lighter).
        addCap(builder, mat, -r, r,
                zenith[0] * 0.70f, zenith[1] * 0.70f, zenith[2] * 0.75f);

        // Four side faces: top = zenith, bottom = horizon.
        // Each face is a full square of the sky cube (x or z fixed at +/-r).
        addFace(builder, mat, r, true, zenith, horizon);    // x = +r
        addFace(builder, mat, -r, true, zenith, horizon);   // x = -r
        addFace(builder, mat, r, false, zenith, horizon);   // z = +r
        addFace(builder, mat, -r, false, zenith, horizon);  // z = -r

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** A full square face of the sky cube: top edge (y=-r) zenith, bottom (y=+r) horizon. */
    private static void addFace(BufferBuilder builder, Matrix4f mat, float fixed, boolean onXAxis,
                                float[] zenith, float[] horizon) {
        float r = RADIUS;
        if (onXAxis) {
            builder.addVertex(mat, fixed, -r, -r).setColor(zenith[0], zenith[1], zenith[2], 1.0f);
            builder.addVertex(mat, fixed, -r, r).setColor(zenith[0], zenith[1], zenith[2], 1.0f);
            builder.addVertex(mat, fixed, r, r).setColor(horizon[0], horizon[1], horizon[2], 1.0f);
            builder.addVertex(mat, fixed, r, -r).setColor(horizon[0], horizon[1], horizon[2], 1.0f);
        } else {
            builder.addVertex(mat, -r, -r, fixed).setColor(zenith[0], zenith[1], zenith[2], 1.0f);
            builder.addVertex(mat, r, -r, fixed).setColor(zenith[0], zenith[1], zenith[2], 1.0f);
            builder.addVertex(mat, r, r, fixed).setColor(horizon[0], horizon[1], horizon[2], 1.0f);
            builder.addVertex(mat, -r, r, fixed).setColor(horizon[0], horizon[1], horizon[2], 1.0f);
        }
    }

    /** Horizontal square cap at a fixed y (zenith ceiling), on the x-z plane. */
    private static void addCap(BufferBuilder builder, Matrix4f mat, float y, float r,
                               float cr, float cg, float cb) {
        builder.addVertex(mat, -r, y, -r).setColor(cr, cg, cb, 1.0f);
        builder.addVertex(mat, r, y, -r).setColor(cr, cg, cb, 1.0f);
        builder.addVertex(mat, r, y, r).setColor(cr, cg, cb, 1.0f);
        builder.addVertex(mat, -r, y, r).setColor(cr, cg, cb, 1.0f);
    }

    private static float[] argbToFloats(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
        };
    }
}