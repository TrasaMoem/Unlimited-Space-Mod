package com.modscreating.unlimitedspace.core.seed;

/**
 * Server-side cache of the real Minecraft world seed, set once on ServerStartedEvent.
 *
 * <p>This is the SINGLE generic bridge that lets slot-parametric chunk generators / biome
 * sources resolve the real world seed at world-gen time even though a build-time datapack JSON
 * cannot freeze it (the world seed does not exist at datapack-authoring time). It is body-kind
 * agnostic: planets, moons and asteroid clusters all drive their procedural pipelines from this
 * one cache.
 *
 * <p>Design rule (R11): reuse this single mechanism, never introduce a second seed cache.
 * {@code PlanetSeedCache} remains as a thin compatibility alias delegating to this class so that
 * earlier planet code and tests stay unchanged.
 *
 * <p>Pure domain ({@link WorldSeed} / {@link GalaxySeed}) is unaffected; only the Minecraft
 * adapter layer touches this cache. A missing cache degrades to seed 0 (deterministic,
 * non-crashing) so settings-screen previews never throw.
 */
public final class CelestialSeedCache {

    private static long worldSeed = Long.MIN_VALUE; // sentinel: not yet initialised

    private CelestialSeedCache() {}

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