package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Reusable binding seam that maps a {@link MoonId} + {@link WorldKind}
 * (surface or orbit) to concrete Minecraft identifiers.
 *
 * <p>Dimension identity is stable and deterministic, derived only from the moon code and
 * the world kind: {@code moon/<moon-code>/<surface|orbit>} under {@code unlimitedspace:}.
 * One class serves every moon — no per-moon copies. This is the Minecraft-side mirror of
 * the pure-domain {@link com.modscreating.unlimitedspace.core.destination.MoonWorldDestination}.
 */
public final class MoonWorldBinding {

    private MoonWorldBinding() {}

    /** {@code unlimitedspace:moon/<moon-code>/<surface|orbit>} */
    public static ResourceLocation location(MoonId moonId, WorldKind kind) {
        String code = moonId.code();
        return ResourceLocation.fromNamespaceAndPath(
                UnlimitedSpace.MODID,
                "moon/" + code + "/" + kind.name().toLowerCase());
    }

    public static ResourceKey<Level> level(MoonId moonId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION, location(moonId, kind));
    }

    public static ResourceKey<LevelStem> levelStem(MoonId moonId, WorldKind kind) {
        return ResourceKey.create(Registries.LEVEL_STEM, location(moonId, kind));
    }

    public static ResourceKey<DimensionType> dimensionType(MoonId moonId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, location(moonId, kind));
    }
}