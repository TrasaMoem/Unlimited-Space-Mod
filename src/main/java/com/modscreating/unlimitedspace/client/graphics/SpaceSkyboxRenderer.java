package com.modscreating.unlimitedspace.client.graphics;

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

/**
 * Creating Space Earth Orbit skybox (R12.5 corrective phase).
 *
 * <p>Faithful port of the Creating Space 1.7.18 {@code CustomDimensionEffects.renderSpaceSky()}
 * render path: a six-face textured cube built from {@code textures/environment/space_sky.png}
 * (a 3-column × 2-row strip atlas of the six star panels), bound to
 * {@link DefaultVertexFormat#POSITION_TEX} and drawn with the exact per-face rotations, UV
 * mapping, half-size (100 blocks) and vertex order used by CS. The user explicitly rejected the
 * previous procedural 420-star field, so this texture-based skybox replaces it — background is
 * <em>only</em> this CS-style starfield + black deep space; system stars and bodies are drawn on top.
 *
 * <p>Facts taken from the decompiled bytecode (see {@code cs_full2.txt} / {@code generic_orbit_javap.txt}):
 * <ul>
 *   <li>cube half-size = 100 (face vertex plane at y = -100);</li>
 *   <li>per-face rotations: face 0 none, 1 XP+90, 2 XP-90, 3 XP+180, 4 ZP+90, 5 ZP-90;</li>
 *   <li>UV: u0 = (i%3)/3, u1 = (i%3+1)/3, v0 = (i/4%2)/2, v1 = (i/4%2+1)/2;</li>
 *   <li>GL state: blend on, depth mask off (depth test left untouched, as in CS).</li>
 * </ul>
 *
 * <p>The order of the six quads and their winding is copied verbatim, so the result matches CS
 * Earth Orbit by construction. State is restored on exit (blend off, depth mask on).
 */
public final class SpaceSkyboxRenderer {

    /** The CS space sky asset, already copied verbatim into our resources (SHA256 identical). */
    private static final ResourceLocation SPACE_SKY =
            ResourceLocation.fromNamespaceAndPath("unlimitedspace", "textures/environment/space_sky.png");

    /** Number of cube faces (CS loop bound). */
    private static final int FACES = 6;

    /** Cube half-size in blocks (CS literal 100.0). */
    private static final float HALF = 100.0f;

    /**
     * ACT 6 section 6: the known UV SEAM fix.
     *
     * <p>The six sky panels live in ONE {@code space_sky.png} atlas (3 columns x 2 rows of
     * {@value #ATLAS_COLS}x{@value #ATLAS_ROWS} cells). Sampling a cell exactly at its border makes
     * the GPU's bilinear filter - and, at a distance, its mip selection - blend in the NEIGHBOURING
     * atlas cell, so a hairline of the wrong panel is drawn along every cube edge: the reported
     * vertical seam.
     *
     * <p>The fix is the standard atlas inset: pull every UV inwards by HALF A TEXEL, so the sampled
     * footprint never reaches a cell boundary. It is a four-constant change and deliberately does
     * NOT rewrite the renderer - the CS face rotations, winding and panel order are untouched.
     */
    /** Atlas columns / rows of the sky strip. */
    private static final int ATLAS_COLS = 3;
    private static final int ATLAS_ROWS = 2;
    /** {@code space_sky.png} resolution (verified from the shipped asset). */
    private static final float ATLAS_TEXELS_U = 1152.0f;
    private static final float ATLAS_TEXELS_V = 768.0f;
    /** Half a texel, in UV units - the inset applied to every cell border. */
    private static final float UV_INSET_U = 0.5f / ATLAS_TEXELS_U;
    private static final float UV_INSET_V = 0.5f / ATLAS_TEXELS_V;

    private SpaceSkyboxRenderer() {
    }

    /**
     * Draw the CS-style textured skybox into the current sky pose space.
     * Leaves GL restored: blend off, depth mask on.
     */
    public static void draw(PoseStack pose) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SPACE_SKY);

        Tesselator tess = Tesselator.getInstance();

        for (int face = 0; face < FACES; face++) {
            pose.pushPose();
            switch (face) {
                case 1 -> pose.mulPose(Axis.XP.rotationDegrees(90.0f));
                case 2 -> pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case 3 -> pose.mulPose(Axis.XP.rotationDegrees(180.0f));
                case 4 -> pose.mulPose(Axis.ZP.rotationDegrees(90.0f));
                case 5 -> pose.mulPose(Axis.ZP.rotationDegrees(-90.0f));
                default -> { /* face 0: no rotation */ }
            }

            int col = face % ATLAS_COLS;
            int row = (face / ATLAS_COLS) % ATLAS_ROWS;
            // ACT 6: the half-texel atlas inset (see UV_INSET_U) - this is the UV seam fix.
            float u0 = col / (float) ATLAS_COLS + UV_INSET_U;
            float u1 = (col + 1) / (float) ATLAS_COLS - UV_INSET_U;
            float v0 = row / (float) ATLAS_ROWS + UV_INSET_V;
            float v1 = (row + 1) / (float) ATLAS_ROWS - UV_INSET_V;

            Matrix4f mat = pose.last().pose();
            BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            // Quad in the plane y = -HALF, spanning x/z in [-HALF, HALF] (CS renderSpaceSky order).
            b.addVertex(mat, -HALF, -HALF, -HALF).setUv(u1, v1);
            b.addVertex(mat, -HALF, -HALF, HALF).setUv(u0, v1);
            b.addVertex(mat, HALF, -HALF, HALF).setUv(u0, v0);
            b.addVertex(mat, HALF, -HALF, -HALF).setUv(u1, v0);
            BufferUploader.drawWithShader(b.buildOrThrow());

            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
