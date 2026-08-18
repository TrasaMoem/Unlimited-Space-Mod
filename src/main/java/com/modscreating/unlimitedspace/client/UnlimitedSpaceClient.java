package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.client.effect.UnlimitedSpaceOrbitEffects;
import com.modscreating.unlimitedspace.client.effect.UnlimitedSpaceSurfaceEffects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Client mod-bus hooks (R12). Registers the procedural {@link
 * net.minecraft.client.renderer.DimensionSpecialEffects} implementations for the
 * US dimension-type effects keys, which the dimension-type datapacks reference.
 *
 * <p>Each effect instance resolves the destination body per-frame from the level's
 * own dimension key + world seed, so a single registration serves all procedural
 * planets, moons, asteroid fields and the legacy space dimension.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class UnlimitedSpaceClient {

    private UnlimitedSpaceClient() {
    }

    @SubscribeEvent
    public static void onRegisterDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(location("planet_orbit"), new UnlimitedSpaceOrbitEffects());
        event.register(location("planet_surface"), new UnlimitedSpaceSurfaceEffects());
        event.register(location("asteroid_field"), new UnlimitedSpaceOrbitEffects());
        event.register(location("space"), new UnlimitedSpaceOrbitEffects());
    }

    private static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, path);
    }
}