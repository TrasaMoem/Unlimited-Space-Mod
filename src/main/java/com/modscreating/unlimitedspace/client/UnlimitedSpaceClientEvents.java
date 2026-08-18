package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.client.effect.UnlimitedSpaceSurfaceEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        if (!(level.effects() instanceof UnlimitedSpaceSurfaceEffects)) return;

        ResolvedVisual vis = CelestialVisualResolver.resolve(level);
        if (vis == null) return;

        int fog = vis.fogColorArgb();
        event.setRed(((fog >> 16) & 0xFF) / 255.0f);
        event.setGreen(((fog >> 8) & 0xFF) / 255.0f);
        event.setBlue((fog & 0xFF) / 255.0f);
    }
}