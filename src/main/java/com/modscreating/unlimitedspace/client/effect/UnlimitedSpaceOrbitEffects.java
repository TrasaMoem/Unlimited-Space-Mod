package com.modscreating.unlimitedspace.client.effect;

import com.modscreating.unlimitedspace.client.CelestialBodyPath;
import com.modscreating.unlimitedspace.client.CelestialVisualResolver;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.SiblingBody;
import com.modscreating.unlimitedspace.client.graphics.PlanetSphereRenderer;
import com.modscreating.unlimitedspace.client.graphics.SpaceSkyboxRenderer;
import com.modscreating.unlimitedspace.client.graphics.SystemStarRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Dimension effects for procedural orbit / space worlds (R12):
 * {@code unlimitedspace:planet_orbit}, {@code unlimitedspace:asteroid_field},
 * {@code unlimitedspace:space}.
 *
 * <p>{@code renderSky} draws a procedural starfield, the actual star(s) of the
 * destination system (binary/trinary supported), and the actual procedural planet
 * (or moon) hanging below the player. Vanilla sky/clouds/rain are skipped.
 */
public class UnlimitedSpaceOrbitEffects extends DimensionSpecialEffects {

    public UnlimitedSpaceOrbitEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
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
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return false;

        PoseStack pose = new PoseStack();
        setupFog.run();
        pose.mulPose(modelViewMatrix);

        // Paint the entire sky black first so we always get deep space regardless of
        // the framebuffer's pre-clear sky/fog colour. This mirrors Creating Space's
        // GenericCelestialOrbitEffect (SkyType.NONE + a dark starfield backdrop):
        // the orbit sky must be black with stars, never a washed-out overworld dome.
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        RenderSystem.clear(16384 /* GL_COLOR_BUFFER_BIT */, Minecraft.ON_OSX);

        // R12.5 explicit layer order (CS Earth Orbit): black deep space -> Creating Space textured
        // skybox (starfield) -> system stars -> distant bodies -> current orbit body LAST (so it
        // stays dominant and nothing overwrites it). The skybox is the CS 6-face textured cube
        // ("space_sky.png"); the previous procedural 420-star field has been removed.
        SpaceSkyboxRenderer.draw(pose);
        SystemStarRenderer.drawOrbitStars(pose, vis);

        // Every other planet/moon of the system appears as a distant square-pixel body, scaled
        // by how far it is from the player (R12.3 Bug #2).
        for (SiblingBody body : vis.bodies()) {
            PlanetSphereRenderer.drawSibling(pose, body);
        }
        // The body actually being orbited hangs below the camera as a large square pixel billboard.
        if (vis.kind() == CelestialBodyPath.Kind.PLANET || vis.kind() == CelestialBodyPath.Kind.MOON) {
            PlanetSphereRenderer.drawBody(pose, vis, camera);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        return true;
    }
}