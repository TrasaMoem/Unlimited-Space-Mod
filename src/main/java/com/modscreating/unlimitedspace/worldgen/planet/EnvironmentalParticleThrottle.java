package com.modscreating.unlimitedspace.worldgen.planet;

/**
 * ACT 6 section 7: the per-EMITTER particle throttle shared by the local block emitters.
 *
 * <p>WHY THIS EXISTS. {@code Block#animateTick} is called by the CLIENT once per TICK for EVERY
 * visible animated block. The two production emitters each rolled a large per-tick probability:
 * a geothermal vent fired a plume on 30% of all ticks, and a luminite rock on 5-20%. That is fine
 * for one block standing alone, but it is a PER-BLOCK rate, so N emitters in view produced N times
 * the particle count: the screen filled with a shimmering haze, and a vent field or a luminite vein
 * turned into a permanent particle storm. The {@code PlanetAmbientDirector} budget was never the
 * cause - it is a single global token bucket capped at {@code MAX_FILL_RATE_PER_TICK} particles per
 * tick for the WHOLE world.
 *
 * <p>THE FIX is per-emitter throttling with two gates, applied before any particle is spawned:
 * <ol>
 *   <li>a DISTANCE / VISIBILITY gate - an emitter that is far from every player is silent;</li>
 *   <li>a DETERMINISTIC per-position COOLDOWN - each emitter position may fire only once every
 *       {@code intervalTicks} ticks, so a field of N emitters contributes at most
 *       {@code N * 20 / intervalTicks} particles per second, no matter how the rolls fall.</li>
 * </ol>
 * Rare BURSTS are preserved: the cooldown opens a short window in which a small number of
 * particles may be emitted at once, so an emitter still reads as an occasional distinct event
 * rather than as a permanent, uniform shimmer.
 *
 * <p>The cooldown is a PURE function of {@code (position, gameTime, interval)} - no per-block
 * state, no map, no allocation, and stable across restarts.
 */
public final class EnvironmentalParticleThrottle {

    /**
     * Blocks beyond this distance from EVERY player produce nothing. A local emitter must be
     * seen to matter, and an emitter the player cannot see is pure cost.
     */
    public static final double MAX_VISIBLE_DISTANCE = 28.0;

    private EnvironmentalParticleThrottle() {}

    /**
     * The distance / visibility gate.
     *
     * @param nearestPlayerDistanceSq squared distance to the closest player, or
     *                               {@link Double#POSITIVE_INFINITY} when there is no player
     * @return true when this emitter is close enough to be worth animating
     */
    public static boolean visible(double nearestPlayerDistanceSq) {
        return nearestPlayerDistanceSq < MAX_VISIBLE_DISTANCE * MAX_VISIBLE_DISTANCE;
    }

    /**
     * The deterministic per-emitter cooldown.
     *
     * <p>Each emitter position is assigned a fixed PHASE in {@code [0, intervalTicks)} derived from
     * its own hash, and it fires exactly when {@code (gameTime + phase) % intervalTicks == 0}. That
     * is a proof of the budget, not a statistical claim:
     * <ul>
     *   <li>an emitter fires EXACTLY {@code ticks / intervalTicks} times, never more - the hash can
     *       only move WHICH tick, never add one;</li>
     *   <li>neighbouring emitters get different phases, so a whole field does not pulse in
     *       lockstep (the {@code posHash} is mixed, not taken modulo the interval);</li>
     *   <li>it is still a pure function of {@code (position, gameTime, interval)} - no per-block
     *       state, no map, no allocation, and stable across restarts.</li>
     * </ul>
     *
     * <p>An earlier version rolled a hash of the tick and asked for a zero residue. That is
     * uniform only in the average: measured 74 emissions/minute per emitter against a theoretical
     * 50, because the product of consecutive position hashes has poorly distributed low bits.
     *
     * @return true when this emitter may emit on this tick
     */
    public static boolean cooldownElapsed(long posHash, long gameTime, int intervalTicks) {
        if (intervalTicks <= 1) return true;
        long phase = Long.remainderUnsigned(mix(posHash), (long) intervalTicks);
        return Long.remainderUnsigned(gameTime + phase, (long) intervalTicks) == 0L;
    }

    /** SplitMix64-style avalanche of a position hash (this is what de-synchronises neighbours). */
    private static long mix(long posHash) {
        long h = posHash;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
