package com.modscreating.unlimitedspace.client.effect;

import com.modscreating.unlimitedspace.client.CelestialVisualResolver;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.graphics.SurfaceSkyRenderer;
import com.modscreating.unlimitedspace.client.graphics.SystemStarRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Dimension effects for procedural planet/moon surface worlds (R12):
 * {@code unlimitedspace:planet_surface}.
 *
 * <p>{@code renderSky} draws a procedural sky gradient from
 * {@code PlanetVisualProfile.skyColor}/{@code fogColor} plus the actual star(s)
 * of the destination system along a time-of-day arc. Cloud rendering is disabled;
 * fog and water visual data are applied by the client event handlers.
 */
public class UnlimitedSpaceSurfaceEffects extends DimensionSpecialEffects {

    public UnlimitedSpaceSurfaceEffects() {
        super(192.0f, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(
                (double) (brightness * 0.94F + 0.06F),
                (double) (brightness * 0.94F + 0.06F),
                (double) (brightness * 0.91F + 0.09F));
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ,
                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     LightTexture lightTexture, double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return false;

        PoseStack pose = new PoseStack();
        setupFog.run();
        pose.mulPose(modelViewMatrix);

        SurfaceSkyRenderer.draw(pose, vis);
        SystemStarRenderer.drawSurfaceStars(pose, vis, level, partialTick);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        return true;
    }
}