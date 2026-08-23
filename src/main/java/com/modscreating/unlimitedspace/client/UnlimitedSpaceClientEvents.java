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
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!(level.effects() instanceof UnlimitedSpaceSurfaceEffects)
                && !(level.effects() instanceof StarSurfaceEffects)) return;

        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return;

        int fog = vis.fogColorArgb();
        event.setRed(((fog >> 16) & 0xFF) / 255.0f);
        event.setGreen(((fog >> 8) & 0xFF) / 255.0f);
        event.setBlue((fog & 0xFF) / 255.0f);
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