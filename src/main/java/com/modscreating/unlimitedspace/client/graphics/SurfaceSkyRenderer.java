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

        // ACT 6 section 6: the OLD geometry was a sky CUBE - one flat cap at y = -r plus four
        // flat side walls of the full cube. Two visible defects came from that topology:
        //
        //  1. a hard SEAM: the cap was tinted 0.70/0.70/0.75 of the zenith colour while the top
        //     EDGE of every wall carried the FULL zenith colour, so the two never met - a crisp
        //     colour discontinuity straight across the sky.
        //  2. the walls themselves: four perfectly planar, perfectly vertical surfaces. Seen at a
        //     shallow angle a wall reads as a flat blue SHEET with a hard silhouette, which is the
        //     reported "vertical blue wall".
        //
        // THE FIX: the artificial wall/cap geometry is REMOVED. The sky is now drawn on the SAME
        // spherical mesh basis the rest of the mod already uses for a continuous dome
        // (PlasmaSkyMesh: a full, longitude-welded UV sphere, already unit-tested for continuity),
        // scaled to RADIUS. Because the gradient is evaluated per-vertex from the vertex DIRECTION
        // instead of from "which cube face am I on", there is no cap, no face boundary and no
        // colour seam by construction - and no flat vertical surface is left to see.
        //
        // NOTHING about the celestial motion changes: this class only ever drew the static
        // background gradient. Sun, moon, star orbits and time-of-day live in the dimension
        // effects / celestial renderers and are untouched.
        drawDome(builder, mat, r, zenith, horizon);

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Draw the sky as a continuous sphere: longitude-welded quads plus two pole fans, shaded by
     * each vertex's own direction, so the gradient is a pure function of position on the sphere.
     *
     * <p>Colours: the zenith colour at the top pole, fading smoothly to the fog/horizon colour at
     * the equator, and continuing at the fog colour below it - one smooth horizon band, no
     * discontinuity.
     */
    private static void drawDome(BufferBuilder builder, Matrix4f mat, float r,
                                 float[] zenith, float[] horizon) {
        int longitudes = PlasmaSkyMesh.LONGITUDES;
        int latitudes = PlasmaSkyMesh.LATITUDES;

        // Interior rings 1..LATITUDES-1 (both poles are handled by the fans below).
        for (int ring = 1; ring < latitudes - 1; ring++) {
            float p0 = (float) ring / latitudes * (float) Math.PI;
            float p1 = (float) (ring + 1) / latitudes * (float) Math.PI;
            float y0 = -cos(p0);
            float y1 = -cos(p1);
            float rad0 = sin(p0);
            float rad1 = sin(p1);
            float[] c00 = skyColor(zenith, horizon, y0);
            float[] c10 = skyColor(zenith, horizon, y1);
            for (int c = 0; c < longitudes; c++) {
                // The LAST column wraps back onto column 0: a welded longitude seam.
                float a0 = (float) (c * 2.0 * Math.PI / longitudes);
                float a1 = (float) (((c + 1) % longitudes) * 2.0 * Math.PI / longitudes);
                float x00 = rad0 * cos(a0), z00 = rad0 * sin(a0);
                float x01 = rad0 * cos(a1), z01 = rad0 * sin(a1);
                float x10 = rad1 * cos(a0), z10 = rad1 * sin(a0);
                float x11 = rad1 * cos(a1), z11 = rad1 * sin(a1);
                builder.addVertex(mat, x00 * r, y0 * r, z00 * r).setColor(c00[0], c00[1], c00[2], 1.0f);
                builder.addVertex(mat, x01 * r, y0 * r, z01 * r).setColor(c00[0], c00[1], c00[2], 1.0f);
                builder.addVertex(mat, x11 * r, y1 * r, z11 * r).setColor(c10[0], c10[1], c10[2], 1.0f);
                builder.addVertex(mat, x10 * r, y1 * r, z10 * r).setColor(c10[0], c10[1], c10[2], 1.0f);
            }
        }

        // Top fan (the zenith pole). Its colour is the value the adjacent ring vertices use, so
        // the old 0.70 tint factor - and with it the cap/wall colour seam - is simply gone.
        float[] top = skyColor(zenith, horizon, -1.0f);
        float pTop = (float) (1.0 / latitudes * Math.PI);
        float radTop = sin(pTop);
        float yTop = -cos(pTop);
        for (int c = 0; c < longitudes; c++) {
            float a0 = (float) (c * 2.0 * Math.PI / longitudes);
            float a1 = (float) (((c + 1) % longitudes) * 2.0 * Math.PI / longitudes);
            builder.addVertex(mat, 0.0f, -r, 0.0f).setColor(top[0], top[1], top[2], 1.0f);
            builder.addVertex(mat, radTop * cos(a0) * r, yTop * r, radTop * sin(a0) * r)
                    .setColor(top[0], top[1], top[2], 1.0f);
            builder.addVertex(mat, radTop * cos(a1) * r, yTop * r, radTop * sin(a1) * r)
                    .setColor(top[0], top[1], top[2], 1.0f);
        }

        // Bottom fan (nadir): the lower hemisphere carries the horizon colour, so looking down
        // still shows sky instead of a hole in the framebuffer.
        float[] bottom = skyColor(zenith, horizon, 1.0f);
        float pBot = (float) ((latitudes - 1.0) / latitudes * Math.PI);
        float radBot = sin(pBot);
        float yBot = cos(pBot);
        for (int c = 0; c < longitudes; c++) {
            float a0 = (float) (c * 2.0 * Math.PI / longitudes);
            float a1 = (float) (((c + 1) % longitudes) * 2.0 * Math.PI / longitudes);
            builder.addVertex(mat, 0.0f, r, 0.0f).setColor(bottom[0], bottom[1], bottom[2], 1.0f);
            builder.addVertex(mat, radBot * cos(a1) * r, yBot * r, radBot * sin(a1) * r)
                    .setColor(bottom[0], bottom[1], bottom[2], 1.0f);
            builder.addVertex(mat, radBot * cos(a0) * r, yBot * r, radBot * sin(a0) * r)
                    .setColor(bottom[0], bottom[1], bottom[2], 1.0f);
        }
    }

    /**
     * The sky gradient at one sphere vertex, as a function of its Y direction only.
     *
     * <p>At the zenith pole ({@code y = -1} in this render space) the colour is exactly the planet
     * zenith colour; at the horizon ({@code y = 0}) it is exactly the fog colour; below the horizon
     * it stays at the fog colour. Because this is evaluated per VERTEX and shared by every face that
     * touches that vertex, two adjacent quads can never disagree along their shared edge - which is
     * exactly the invariant the old cube cap/wall split violated.
     */
    private static float[] skyColor(float[] zenith, float[] horizon, float y) {
        if (y >= 0.0f) {
            return new float[]{horizon[0], horizon[1], horizon[2]};
        }
        // -1 (zenith) -> the zenith colour, 0 (horizon) -> the fog colour.
        float t = y + 1.0f;                       // 1 at the zenith, 0 at the horizon
        float s = t * t * (3.0f - 2.0f * t);     // smoothstep: no visible banding
        return new float[]{
                horizon[0] + (zenith[0] - horizon[0]) * s,
                horizon[1] + (zenith[1] - horizon[1]) * s,
                horizon[2] + (zenith[2] - horizon[2]) * s,
        };
    }

    private static float cos(double a) {
        return (float) Math.cos(a);
    }

    private static float sin(double a) {
        return (float) Math.sin(a);
    }

    private static float[] argbToFloats(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
        };
    }
}
