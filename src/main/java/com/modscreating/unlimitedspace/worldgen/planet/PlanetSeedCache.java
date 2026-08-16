package com.modscreating.unlimitedspace.worldgen.planet;

/**
 * Server-side cache of the real Minecraft world seed, set once on ServerStartedEvent.
 *
 * <p>This is the bridge that lets the slot-parametric {@link PlanetChunkGenerator} resolve
 * the real seed at world-gen time even though the build-time datapack JSON cannot freeze it
 * (the world seed does not exist at datapack-authoring time). The generator reads
 * {@code world_seed} from the JSON when present, otherwise falls back to this cache.
 *
 * <p>Pure domain (Galaxy/Planet) is unaffected; only the Minecraft adapter layer touches
 * this cache. A missing cache degrades to seed 0 (deterministic, non-crashing) so that
 * settings-screen previews never throw.
 */
public final class PlanetSeedCache {

    private static long worldSeed = Long.MIN_VALUE; // sentinel: not yet initialised

    private PlanetSeedCache() {}

    public static void set(long seed) {
        worldSeed = seed;
    }

    public static long get() {
        return worldSeed == Long.MIN_VALUE ? 0L : worldSeed;
    }

    public static boolean isSet() {
        return worldSeed != Long.MIN_VALUE;
    }
}
