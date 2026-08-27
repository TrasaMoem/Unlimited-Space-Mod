package com.modscreating.unlimitedspace.client.nav;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R23: a REAL rotatable 3D miniature of the ASSEMBLED rocket for the INFO tab.
 *
 * <p>Every visible block of the rocket's Create contraption is rendered through the
 * vanilla block-model pipeline ({@code BlockRenderDispatcher#renderSingleBlock}) into
 * the GUI pose stack, fitted into the panel box and orbited by drag deltas. No
 * placeholder art: the render is the player's actual ship, block for block (Create's
 * special contraption actors are not part of the static block map and are skipped).
 */
public final class RocketMiniRenderer {

    private RocketMiniRenderer() {}

    /**
     * @param g        the screen's GuiGraphics (its pose may already be translated)
     * @param rocket   the bound, assembled rocket entity (client side)
     * @param px       projection box left (GUI coords)
     * @param py       projection box top (GUI coords, already includes scroll offset
     *                 if the caller renders inside a translated panel pose)
     * @param pw       projection box width
     * @param ph       projection box height
     * @param yawDeg   horizontal orbit angle in degrees
     * @param pitchDeg vertical orbit angle in degrees (clamped to +-89)
     * @param zoom     scale multiplier (1 = fit the box)
     */
    public static void render(GuiGraphics g, RocketContraptionEntity rocket,
                              int px, int py, int pw, int ph,
                              float yawDeg, float pitchDeg, float zoom) {
        Contraption contraption = rocket.getContraption();
        if (contraption == null || contraption.getBlocks().isEmpty()) return;

        // bounding box of the REAL contraption blocks (skip air/structure-void filler)
        var blocks = contraption.getBlocks();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var entry : blocks.entrySet()) {
            BlockState state = entry.getValue().state();
            if (state == null || state.isAir()) continue;
            BlockPos p = entry.getKey();
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        if (maxX < minX) return; // nothing visible (all air)

        float sizeX = maxX - minX + 1;
        float sizeY = maxY - minY + 1;
        float sizeZ = maxZ - minZ + 1;
        float maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));
        float s = Math.min(pw, ph) * 0.82f / maxDim * Mth.clamp(zoom, 0.3f, 3f);

        Minecraft mc = Minecraft.getInstance();
        var buffer = mc.renderBuffers().bufferSource();
        // The GUI ortho projection shares the main target with the (perspective) world
        // render; stale world depth would clip the miniature, so clear it first. Safe:
        // vanilla GUI drawing (fills/text/widgets) does not depth-test.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        PoseStack pose = g.pose();
        pose.pushPose();
        // orbit the CAMERA around the ship: centre -> scale -> pitch -> yaw -> de-centre
        pose.translate(px + pw / 2.0f, py + ph / 2.0f, 200.0f); // z above panel fills
        pose.scale(s, -s, s); // GUI Y grows downward -> flip so the nose points up
        pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitchDeg, -89f, 89f)));
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        pose.translate(-(minX + maxX + 1) / 2.0f,
                -(minY + maxY + 1) / 2.0f,
                -(minZ + maxZ + 1) / 2.0f);

        for (var entry : blocks.entrySet()) {
            BlockState state = entry.getValue().state();
            if (state == null || state.isAir()) continue;
            BlockPos p = entry.getKey();
            pose.pushPose();
            pose.translate(p.getX(), p.getY(), p.getZ());
            try {
                mc.getBlockRenderer().renderSingleBlock(state, pose, buffer,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } catch (Throwable ignored) {
                // one broken/special block model must never kill the whole UI
            }
            pose.popPose();
        }
        buffer.endBatch();
        pose.popPose();
    }
}