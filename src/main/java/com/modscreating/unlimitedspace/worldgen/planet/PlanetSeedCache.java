package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;

/**
 * Backward-compatible alias for the generic world-seed bridge {@link CelestialSeedCache}.
 *
 * <p>R11 renamed the shared bridge to be body-kind agnostic (planets + moons + asteroid
 * clusters). This class is retained as a thin compatibility delegate so existing planet code
 * and its regression tests keep working unchanged. No state lives here.
 */
public final class PlanetSeedCache {

    private PlanetSeedCache() {}

    public static void set(long seed) {
        CelestialSeedCache.set(seed);
    }

    public static long get() {
        return CelestialSeedCache.get();
    }

    public static boolean isSet() {
        return CelestialSeedCache.isSet();
    }
}
