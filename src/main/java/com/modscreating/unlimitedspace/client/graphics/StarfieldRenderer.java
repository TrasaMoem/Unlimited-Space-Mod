package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Deterministic procedural starfield (R12). Draws a fixed set of small billboard
 * quads on the sky dome; positions/colours derive from the world seed so the same
 * world always shows the same stars. Replaces textual space-sky sprites.
 */
public final class StarfieldRenderer {

    /** Stars drawn on the sky dome. */
    private static final int STAR_COUNT = 260;
    /** Sky dome radius in blocks (camera space). */
    private static final float RADIUS = 340.0f;

    private StarfieldRenderer() {
    }

    /**
     * Render the starfield into the current sky pose space.
     * Leaves GL state restored (depth test on, depth mask on, blend off).
     */
    public static void draw(PoseStack pose, long worldSeed) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = pose.last().pose();
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);

        for (int i = 0; i < STAR_COUNT; i++) {
            long s = Seeds.derive(worldSeed, "us.client.starfield", i);
            double theta = Seeds.fraction(s, 1L) * Math.PI * 2.0;
            double cosPhi = 2.0 * Seeds.fraction(s, 2L) - 1.0;
            double phi = Math.acos(cosPhi);
            float x = (float) (RADIUS * Math.sin(phi) * Math.cos(theta));
            float y = (float) (RADIUS * cosPhi);
            float z = (float) (RADIUS * Math.sin(phi) * Math.sin(theta));
            float bright = 0.18f + 0.82f * (float) Seeds.fraction(s, 3L);
            float size = 0.5f + 0.9f * (float) Seeds.fraction(s, 4L);

            float r;
            float g;
            float b;
            int tint = (int) Math.floor(Seeds.fraction(s, 5L) * 16.0);
            if (tint == 0) {          // ~6% blue-white
                r = 0.72f * bright; g = 0.80f * bright; b = bright;
            } else if (tint == 1) {   // ~6% warm red dwarf
                r = bright; g = 0.75f * bright; b = 0.58f * bright;
            } else {                  // white
                r = bright; g = bright; b = bright;
            }
            addStarBillboard(builder, mat, up, x, y, z, size, r, g, b, 0.9f);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Camera-facing billboard quad at the given centre. */
    private static void addStarBillboard(BufferBuilder builder, Matrix4f mat, Vector3f up,
                                         float cx, float cy, float cz, float size,
                                         float cr, float cg, float cb, float ca) {
        Vector3f dir = new Vector3f(cx, cy, cz).normalize();
        Vector3f right = new Vector3f(dir).cross(up);
        if (right.lengthSquared() < 1.0e-4f) {
            right.set(1.0f, 0.0f, 0.0f);
        }
        right.normalize();
        Vector3f v = new Vector3f(dir).cross(right).normalize();

        float rx = right.x * size;
        float ry = right.y * size;
        float rz = right.z * size;
        float vx = v.x * size;
        float vy = v.y * size;
        float vz = v.z * size;

        builder.addVertex(mat, cx - rx - vx, cy - ry - vy, cz - rz - vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx + rx - vx, cy + ry - vy, cz + rz - vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx + rx + vx, cy + ry + vy, cz + rz + vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx - rx + vx, cy - ry + vy, cz - rz + vz).setColor(cr, cg, cb, ca);
    }
}