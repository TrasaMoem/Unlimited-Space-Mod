package com.modscreating.unlimitedspace.client.effect;

import com.modscreating.unlimitedspace.client.CelestialVisualResolver;
import com.modscreating.unlimitedspace.client.ResolvedVisual;
import com.modscreating.unlimitedspace.client.graphics.PlasmaSkyRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Dedicated dimension effects for a star SURFACE (R14.9.1): {@code unlimitedspace:star_surface}.
 *
 * <p>Unlike {@link UnlimitedSpaceSurfaceEffects} (a planet/moon surface, which keeps a procedural colour
 * sky + a time-of-day arc), a star surface has NO normal sky concept. The player is INSIDE the photosphere,
 * so the whole dome is filled with the star's own luminous plasma (multi-scale convection cells, hotspots,
 * turbulence). Clouds, vanilla sun/moon, background stars and blue space are all suppressed. A black hole
 * surface routes to a dark void (never a photosphere). The fog is forced to a constant bright plasma colour
 * so there is no "night" and the surface reads self-illuminated.
 */
public class StarSurfaceEffects extends DimensionSpecialEffects {

    /** Registry key referenced by {@code data/.../dimension_type/procedural_star_surface.json}. */
    public static final String EFFECT_KEY = StarEffects.STAR_SURFACE;

    public StarSurfaceEffects() {
        // SkyType.NONE: the star surface has NO normal skybox — the whole dome is the star's own plasma, and
        // the vanilla sun/moon/background stars must never render on top of it (unlike the planet surface,
        // which is SkyType.NORMAL and keeps a day/night arc). hasSkyLight=true keeps the world fully lit so
        // the plasma reads self-illuminated; there is no "night" because the fog is a constant bright plasma.
        super(192.0f, true, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // No day/night on a star surface: the atmosphere is always the same bright plasma, so the fog is
        // returned unchanged rather than being darkened by the time-of-day brightness.
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
    public boolean tickRain(ClientLevel level, int ticks, net.minecraft.client.Camera camera) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             net.minecraft.client.Camera camera, Matrix4f projectionMatrix,
                             boolean isFoggy, Runnable setupFog) {
        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return false;

        PoseStack pose = new PoseStack();
        setupFog.run();
        pose.mulPose(modelViewMatrix);

        // Clear the framebuffer to the SATURATED plasma sky colour before drawing the dome. R14.9.3-A: the
        // old fog colour is the star's plasma × 0.62 (the outer halo), which for a G/white/blue star reads as
        // a flat gray-tan — exactly the gray that bled through gaps in the old cube. The plasma sky colour is
        // the star's own saturated temperature colour (never gray), so even if any pixel slipped through it
        // would be plasma, not gray. (Black holes use a near-black void, which is correct.)
        int bg = vis.skyColorArgb();
        RenderSystem.clearColor(ch(bg, 16), ch(bg, 8), ch(bg, 0), 1.0f);
        RenderSystem.clear(16384 /* GL_COLOR_BUFFER_BIT */, Minecraft.ON_OSX);

        // The whole dome is the star's own plasma (or dark void for a black hole). R14.9.2: pass the
        // game tick so the plasma churns (convection animation) rather than being a static skybox.
        PlasmaSkyRenderer.draw(pose, vis, vis.worldSeed(), (int) level.getGameTime());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        return true;
    }

    private static float ch(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255.0f;
    }
}
