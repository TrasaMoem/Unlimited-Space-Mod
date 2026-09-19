package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.client.effect.StarSurfaceEffects;
import com.modscreating.unlimitedspace.client.effect.UnlimitedSpaceSurfaceEffects;
import com.modscreating.unlimitedspace.worldgen.star.StarSurfacePhysicsGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client NeoForge-bus hooks (R12). Currently applies the procedural fog colour
 * ({@code PlanetVisualProfile.fogColor} / {@code MoonSkyProfile.fogColor}) on
 * planet/moon surface worlds. Everything is scoped to the US surface dimension
 * effects, so vanilla/Creating Space dimensions are unaffected.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, value = Dist.CLIENT)
public final class UnlimitedSpaceClientEvents {

    private UnlimitedSpaceClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // R59: drives the delayed post-assembly navigation-menu reopen (see R15NavClient)
        com.modscreating.unlimitedspace.client.nav.R15NavClient
                .clientTick(Minecraft.getInstance());
        // R19 ambient life: sparse province-aware particles on US planet surfaces.
        com.modscreating.unlimitedspace.client.ambient.PlanetAmbientParticles.clientTick(
                Minecraft.getInstance().level, Minecraft.getInstance().player);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!(level.effects() instanceof UnlimitedSpaceSurfaceEffects)
                && !(level.effects() instanceof StarSurfaceEffects)) return;

        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return;

        float r = ((vis.fogColorArgb() >> 16) & 0xFF) / 255.0f;
        float g = ((vis.fogColorArgb() >> 8) & 0xFF) / 255.0f;
        float b = (vis.fogColorArgb() & 0xFF) / 255.0f;

        // R19: the atmosphere profile is an ADDITIONAL parameter source on this SAME hook -
        // a subtle dust/haze tint blended into the procedural fog colour (no second fog system).
        com.modscreating.unlimitedspace.client.ambient.PlanetAmbientEnvironment env =
                com.modscreating.unlimitedspace.client.ambient.PlanetAmbientEnvironment.resolve(level);
        if (env != null && env.atmosphere() != null) {
            com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile atmo = env.atmosphere();
            double dust = Math.min(1.0, 0.6 * atmo.dustiness() + 0.4 * atmo.haze());
            double k = Math.min(0.25, 0.6 * dust);
            float dr = atmo.colorTemp() == com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile.ColorTemp.WARM ? 0.92f
                    : atmo.colorTemp() == com.modscreating.unlimitedspace.core.worldgen.fluids.AtmosphereProfile.ColorTemp.COOL ? 0.72f : 0.82f;
            float dg = 0.78f;
            float db = 0.70f;
            r += (float) ((dr - r) * k);
            g += (float) ((dg - g) * k);
            b += (float) ((db - b) * k);
        }

        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    /**
     * R14.9.3-E follow-up: RUBY FLAME. When the player is burning ON A STAR SURFACE (ignited by the
     * plasma blocks), a translucent ruby-red tint is drawn over the vanilla fire overlay, so the
     * flames "on you" read as deep ruby instead of vanilla orange. Scoped strictly to star surfaces;
     * normal fire everywhere else keeps its vanilla colour.
     */
    /** The vanilla fire overlay GUI layer id ({@code minecraft:fire}). */
    private static final ResourceLocation FIRE_LAYER = ResourceLocation.withDefaultNamespace("fire");

    @SubscribeEvent
    public static void onRenderFireLayerRubyTint(RenderGuiLayerEvent.Post event) {
        if (!FIRE_LAYER.equals(event.getName())) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (!player.isOnFire()) return;
        // Ruby flame only on star SURFACES (the plasma ignited you there).
        if (!(level.effects() instanceof StarSurfaceEffects)) return;
        if (!StarSurfacePhysicsGuard.isStarSurfaceWorld(level)) return;

        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), RUBY_FLAME_TINT);
    }

    /** Translucent ruby overlay colour drawn over the fire layer (ARGB). */
    private static final int RUBY_FLAME_TINT = 0x5AC01038;
}