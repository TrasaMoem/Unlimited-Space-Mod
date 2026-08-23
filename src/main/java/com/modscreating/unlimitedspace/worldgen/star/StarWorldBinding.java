package com.modscreating.unlimitedspace.worldgen.star;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Reusable binding seam that maps a {@link StarSystemId} + {@link WorldKind} to concrete
 * Minecraft identifiers for its star orbit world.
 *
 * <p>R14.5.1: a star has NO surface world (a star cannot be landed on), so the only playable
 * star destination is the orbit. Dimension identity is stable and deterministic, derived only
 * from the system code: {@code star/<system-code>/orbit} under {@code unlimitedspace:}. This can
 * never collide with planet (…/planet/…), moon (…/moon/…) or asteroid (…/asteroid/…) dimensions.
 *
 * <p>This mirrors {@code PlanetWorldBinding}/{@code MoonWorldBinding} but collapses to the single
 * orbit kind, exactly as {@code AsteroidWorldBinding} collapses to a single field world.
 */
public final class StarWorldBinding {

    private StarWorldBinding() {
    }

    /** {@code unlimitedspace:star/<star-code>/<surface|orbit>}. */
    public static ResourceLocation location(StarId starId, WorldKind kind) {
        return ResourceLocation.fromNamespaceAndPath(
                UnlimitedSpace.MODID, locationPath(starId, kind));
    }

    /** Primary-star convenience overload (index 0), backward compatible with the R14.9.1 single-arg form. */
    public static ResourceLocation location(StarSystemId systemId, WorldKind kind) {
        return location(new StarId(systemId, 0), kind);
    }

    /**
     * Pure-domain (no Minecraft types) projection of {@link #location(StarId, WorldKind)}:
     * the deterministic path segment backing the star world dimension identity, asserted in tests
     * without a live server.
     */
    public static String locationPath(StarId starId, WorldKind kind) {
        return "star/" + starId.code() + "/" + kind.name().toLowerCase();
    }

    public static String locationPath(StarSystemId systemId, WorldKind kind) {
        return locationPath(new StarId(systemId, 0), kind);
    }

    public static ResourceKey<Level> level(StarId starId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION, location(starId, kind));
    }

    public static ResourceKey<LevelStem> levelStem(StarId starId, WorldKind kind) {
        return ResourceKey.create(Registries.LEVEL_STEM, location(starId, kind));
    }

    public static ResourceKey<DimensionType> dimensionType(StarId starId, WorldKind kind) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, location(starId, kind));
    }
}