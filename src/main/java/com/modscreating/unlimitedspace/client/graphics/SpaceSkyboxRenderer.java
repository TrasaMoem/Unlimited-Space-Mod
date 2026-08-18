package com.modscreating.unlimitedspace.client.graphics;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Star skybox for orbit and asteroid worlds (R12.3 Bug #3).
 *
 * <p>Uses Creating Space's own {@code space_sky.png} starfield texture as a proper CUBE skybox —
 * the file is a standard 3-column × 2-row cube map (each face 384×384), exactly as
 * {@code com.rae.creatingspace.content.planets.CustomDimensionEffects.renderSpaceSky} renders it:
 * six faces of an enclosing cube, each sampling the matching 1/6 region with a per-face rotation.
 * Unlike an equirectangular projection, this has no pole distortion, so there are no black/bluish
 * stripes at the zenith/nadir and no dark cracks when the camera sweeps past a star.
 *
 * <p>A procedural layer of bright stars is scattered over the two polar caps on top, so the very
 * top and bottom of the sky are always densely starry. It is drawn first (deep background), with
 * planets/moons layered on top in the foreground.
 */
public final class SpaceSkyboxRenderer {

    /** Rim radius (camera space) of the enclosing sky box. */
    private static final float RADIUS = 400.0f;

    /** The bundled starfield texture (assets/unlimitedspace/textures/environment/space_sky.png). */
    private static final ResourceLocation SKY = ResourceLocation.fromNamespaceAndPath(
            "unlimitedspace", "textures/environment/space_sky.png");

    /** How many procedural stars to scatter into each of the two polar caps. */
    private static final int POLAR_STARS_PER_CAP = 120;

    private SpaceSkyboxRenderer() {
    }

    /**
     * Draw the starfield as a proper cube skybox, then fill the polar caps with procedural stars.
     * Leaves GL state restored (depth test on, depth mask on, blend off, cull back on).
     */
    public static void draw(PoseStack pose, long worldSeed) {
        drawSkyBox(pose);
        drawPolarStars(pose, worldSeed);
    }

    /**
     * The cube-box part: a faithful port of {@code CustomDimensionEffects.renderSpaceSky} using
     * the bundled 3×2 cube-map texture. Each face is a quad on the enclosing cube, rotated into
     * place, sampled from the correct 1/6 region of the texture — so poles are covered like every
     * other direction and there are no equirectangular artifacts.
     */
    private static void drawSkyBox(PoseStack pose) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SKY);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        for (int face = 0; face < 6; face++) {
            pose.pushPose();
            applyFaceRotation(pose, face);
            Matrix4f mat = pose.last().pose();
            float u0 = (face % 3) / 3.0f;
            float v0 = (face / 3) / 2.0f;
            float u1 = ((face % 3) + 1) / 3.0f;
            float v1 = ((face / 3) + 1) / 2.0f;
            addCubeFace(buffer, mat, u0, v0, u1, v1);
            pose.popPose();
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** The base quad of one cube face, UV'd into the matching 1/6 cell of the 3×2 starfield. */
    private static void addCubeFace(BufferBuilder buffer, Matrix4f mat,
                                    float u0, float v0, float u1, float v1) {
        float r = RADIUS;
        buffer.addVertex(mat, -r, -r, -r).setUv(u1, v1);
        buffer.addVertex(mat, -r, -r, r).setUv(u0, v1);
        buffer.addVertex(mat, -r, r, r).setUv(u0, v0);
        buffer.addVertex(mat, -r, r, -r).setUv(u1, v0);
    }

    /** Per-face rotation, from CustomDimensionEffects.renderSpaceSky. */
    private static void applyFaceRotation(PoseStack pose, int face) {
        if (face == 1) pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        else if (face == 2) pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
        else if (face == 3) pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        else if (face == 4) pose.mulPose(Axis.YP.rotationDegrees(-90.0f));
        else if (face == 5) pose.mulPose(Axis.ZP.rotationDegrees(-90.0f));
    }

    /**
     * Scatter bright procedural stars into the two polar caps so the zenith and the nadir are
     * always clearly starry. Seeded from the world seed; camera-facing billboards. BLEND is
     * enabled here; the caller restores the state.
     */
    private static void drawPolarStars(PoseStack pose, long worldSeed) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f mat = pose.last().pose();
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < POLAR_STARS_PER_CAP * 2; i++) {
            long s = Seeds.derive(worldSeed, "us.skybox.polar", i);
            int pole = (i & 1) == 0 ? 1 : -1;
            double colat = Seeds.fraction(s, 1L) * 0.58;   // within ~33 deg of the pole
            double az = Seeds.fraction(s, 2L) * Math.PI * 2.0;
            double sc = Math.sin(colat);
            double cc = Math.cos(colat);
            double dx = sc * Math.cos(az);
            double dz = sc * Math.sin(az);
            double dy = pole * cc;

            float bright = 0.5f + 0.5f * (float) Seeds.fraction(s, 3L);
            float size = 0.6f + 0.9f * (float) Seeds.fraction(s, 4L);
            float tint = (float) Math.floor(Seeds.fraction(s, 5L) * 12.0);
            float r, g, b;
            if (tint == 0) {
                r = 0.72f * bright; g = 0.80f * bright; b = bright;
            } else if (tint == 1) {
                r = bright; g = 0.75f * bright; b = 0.60f * bright;
            } else {
                r = bright; g = bright; b = bright;
            }

            addStarBillboard(builder, mat, up,
                    (float) (dx * RADIUS), (float) (dy * RADIUS), (float) (dz * RADIUS),
                    size, r, g, b, 0.9f);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Camera-facing billboard quad at the given centre (a single bright star). */
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

        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float vx = v.x * size, vy = v.y * size, vz = v.z * size;
        builder.addVertex(mat, cx - rx - vx, cy - ry - vy, cz - rz - vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx + rx - vx, cy + ry - vy, cz + rz - vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx + rx + vx, cy + ry + vy, cz + rz + vz).setColor(cr, cg, cb, ca);
        builder.addVertex(mat, cx - rx + vx, cy - ry + vy, cz - rz + vz).setColor(cr, cg, cb, ca);
    }
}