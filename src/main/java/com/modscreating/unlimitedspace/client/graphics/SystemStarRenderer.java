package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.StarVisual;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Renders the visible star(s)/sun(s) of the current system (R12).
 * <ul>
 *   <li>Orbit worlds: fixed azimuth/elevation per star — binary/trinary systems
 *       show several distinct suns;</li>
 *   <li>Surface worlds: stars travel a time-of-day arc (like the vanilla sun), with
 *       their own azimuth spread and sun-tint colour mixing;</li>
 *   <li>Black holes are rendered as a dark core with a faint accretion ring instead
 *       of a glowing sun.</li>
 * </ul>
 */
public final class SystemStarRenderer {

    /** Stars sit on this sky-dome distance in camera space. */
    private static final float STAR_DISTANCE = 520.0f;

    private SystemStarRenderer() {
    }

    /** Orbit-style: each star stays at its deterministic fixed sky position. */
    public static void drawOrbitStars(PoseStack pose, ResolvedVisual vis) {
        for (StarVisual sv : vis.stars()) {
            drawDisc(pose, sv, sv.azimuthDeg(), sv.elevationDeg(), vis.sunTintArgb(), 0.6f);
        }
    }

    /** Surface-style: stars follow the time-of-day arc; azimuth is a fixed offset. */
    public static void drawSurfaceStars(PoseStack pose, ResolvedVisual vis, ClientLevel level, float partialTick) {
        float t = level.getTimeOfDay(partialTick) * (float) (Math.PI * 2.0);
        float ex = (float) Math.sin(t);
        float ey = -(float) Math.cos(t);
        float elevation = (float) Math.toDegrees(Math.atan2(ey, ex));
        for (StarVisual sv : vis.stars()) {
            float az = sv.azimuthDeg() + 90.0f;
            drawDisc(pose, sv, az, elevation, vis.sunTintArgb(), 0.85f);
        }
    }

/** Orbit-style: draw the parent planet of a moon as a large coloured disc. */
    public static void drawParentPlanet(PoseStack pose, ResolvedVisual vis) {
        if (vis.kind() != com.modscreating.unlimitedspace.client.CelestialBodyPath.Kind.MOON) return;
        if (vis.parentDiscArgb() == 0) return;
        float az = 160.0f + (float) (Seeds.fraction(vis.worldSeed(), 106000L) - 0.5) * 90.0f;
        float el = 25.0f + (float) Seeds.fraction(vis.worldSeed(), 106001L) * 20.0f;
        StarVisual disc = new StarVisual(null, 0, vis.parentDiscArgb(), az, el, 22.0f, false);
        drawDisc(pose, disc, az, el, 0, 0.0f);
    }
    /**
     * Draw one star: additive glow + bright core (or dark core + accretion ring for
     * black holes). {@code tintWeight} mixes the procedural sun tint into the star's
     * spectral colour.
     */
    private static void drawDisc(PoseStack pose, StarVisual sv, float azimuthDeg, float elevationDeg,
                                 int sunTintArgb, float tintWeight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(azimuthDeg));
        pose.mulPose(Axis.XP.rotationDegrees(elevationDeg));
        pose.translate(0.0f, 0.0f, -STAR_DISTANCE);

        Matrix4f mat = pose.last().pose();
        float r = Math.max(sv.apparentRadius(), 4.0f);
        float cr = sv.red();
        float cg = sv.green();
        float cb = sv.blue();

        // Blend the procedural sun tint into the star colour (R12 §16).
        float tintR = ((sunTintArgb >> 16) & 0xFF) / 255.0f;
        float tintG = ((sunTintArgb >> 8) & 0xFF) / 255.0f;
        float tintB = (sunTintArgb & 0xFF) / 255.0f;
        cr = cr * (1.0f - tintWeight) + tintR * tintWeight;
        cg = cg * (1.0f - tintWeight) + tintG * tintWeight;
        cb = cb * (1.0f - tintWeight) + tintB * tintWeight;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        Tesselator tess = Tesselator.getInstance();

        if (sv.blackHole()) {
            drawBlackHole(tess, mat, r);
        } else {
            // soft additive glow
            BufferBuilder glow = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            addQuad(glow, mat, r * 3.4f, cr, cg, cb, 0.18f);
            BufferUploader.drawWithShader(glow.buildOrThrow());
            // bright core
            BufferBuilder core = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            addQuad(core, mat, r, 1.0f, Math.max(0.92f, cg), Math.max(0.85f, cb), 1.0f);
            BufferUploader.drawWithShader(core.buildOrThrow());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** Black holes: dark core + faint violet accretion ring — never a yellow sun. */
    private static void drawBlackHole(Tesselator tess, Matrix4f mat, float radius) {
        BufferBuilder core = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addQuad(core, mat, radius, 0.02f, 0.01f, 0.02f, 0.95f);
        BufferUploader.drawWithShader(core.buildOrThrow());

        // thin accretion ring, additive
        BufferBuilder ring = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addRingQuad(ring, mat, radius * 1.9f, 0.55f, 0.55f, 0.9f, 0.20f);
        BufferUploader.drawWithShader(ring.buildOrThrow());
    }

    private static void addQuad(BufferBuilder builder, Matrix4f mat, float size,
                                float r, float g, float b, float a) {
        float h = size;
        builder.addVertex(mat, -h, -h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, h, -h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, h, h, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, -h, h, 0.0f).setColor(r, g, b, a);
    }

    private static void addRingQuad(BufferBuilder builder, Matrix4f mat, float radius,
                                    float r, float g, float b, float a) {
        float inner = radius * 0.55f;
        float outer = radius;
        builder.addVertex(mat, -outer, -inner, 0.0f).setColor(r, g, b, a * 0.4f);
        builder.addVertex(mat, outer, -inner, 0.0f).setColor(r, g, b, a * 0.4f);
        builder.addVertex(mat, outer, inner, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, -outer, inner, 0.0f).setColor(r, g, b, a);
    }
}