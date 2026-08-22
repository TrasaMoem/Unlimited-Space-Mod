package com.modscreating.unlimitedspace.client.graphics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bounded client-side cache of pre-generated celestial sprites (R14.7).
 *
 * <p>A body's 64x64+ sprite is derived solely from its stable identity + seed, so it is regenerated
 * once and reused every frame instead of being re-sampled per-frame (which would be a per-frame CPU
 * cost across a whole star system). The cache is an LRU with a hard capacity, so visiting thousands
 * of procedural systems can never leak unbounded textures. {@link #clear()} is wired into
 * {@link com.modscreating.unlimitedspace.client.CelestialVisualResolver#clearCache()} so a world
 * switch / dimension unload releases everything.
 */
public final class CelestialTextureCache {

    /** Hard cap so the cache can't grow without bound as the player visits many systems. */
    private static final int CAPACITY = 48;

    private static final Map<String, int[]> CACHE = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > CAPACITY;
        }
    };

    private CelestialTextureCache() {
    }

    /**
     * Stable cache key for one body's sprite.
     *
     * @param worldSeed  world seed (isolates identical codes across different saves)
     * @param bodyCode   stable body code (planet/moon), e.g. {@code system_0000_planet_02}
     * @param kind       body / star kind discriminator
     * @param resolution sprite side length
     */
    public static String key(long worldSeed, String bodyCode, String kind, int resolution) {
        return worldSeed + "|" + kind + "|" + bodyCode + "|" + resolution;
    }

    /** Return the cached sprite for {@code key}, generating and caching it on first use. */
    public static int[] getOrCreate(String key, Supplier<int[]> generator) {
        int[] cached = CACHE.get(key);
        if (cached != null) return cached;
        int[] generated = generator.get();
        if (generated != null) {
            CACHE.put(key, generated);
        }
        return generated;
    }

    /** Drop all cached sprites (world switch / dimension unloading). */
    public static void clear() {
        CACHE.clear();
    }
}
