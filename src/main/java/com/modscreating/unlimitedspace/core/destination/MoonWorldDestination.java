package com.modscreating.unlimitedspace.core.destination;

import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.seed.MoonSeed;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic, pure-domain identity of a real world that belongs to a moon.
 *
 * <p>Mirror of {@link WorldDestination} for the moon level of the hierarchy: a moon
 * conceptually has a {@link #SURFACE} world (where the player walks) and an
 * {@link #ORBIT} world (a travel/staging area), each with its own deterministic seed.
 * Imports <strong>no</strong> Minecraft types; the mapping to
 * {@code ResourceLocation}/{@code ResourceKey<LevelStem>} and to a Creating Space
 * destination record is the adapter-layer responsibility.
 *
 * @param moon      the owning moon
 * @param seed      the owning moon's stable seed
 * @param worldKind whether this is the moon's surface or orbit world
 * @param worldSeed deterministic seed for this specific world
 */
public record MoonWorldDestination(
        MoonId moon,
        MoonSeed seed,
        WorldKind worldKind,
        long worldSeed) {

    public static MoonWorldDestination moonSurface(MoonId moon, MoonSeed seed) {
        return new MoonWorldDestination(moon, seed, WorldKind.SURFACE,
                deriveWorldSeed(seed.value(), WorldKind.SURFACE));
    }

    public static MoonWorldDestination moonOrbit(MoonId moon, MoonSeed seed) {
        return new MoonWorldDestination(moon, seed, WorldKind.ORBIT,
                deriveWorldSeed(seed.value(), WorldKind.ORBIT));
    }

    /** The owning body kind is always {@link BodyKind#MOON}. */
    public BodyKind bodyKind() {
        return BodyKind.MOON;
    }

    /**
     * Stable destination code that an adapter can turn into dimension/registry keys,
     * e.g. {@code system_0004_planet_01_moon_00_surface}.
     */
    public String code() {
        return moon.code() + "_" + worldKind.name().toLowerCase();
    }

    /**
     * Deterministic per-world seed, namespace-separated so that changing e.g. the
     * terrain algorithm never reshuffles the world identity.
     */
    private static long deriveWorldSeed(long moonSeed, WorldKind kind) {
        return Seeds.derive(moonSeed, "unlimitedspace.dest.world." + kind.name().toLowerCase());
    }
}
