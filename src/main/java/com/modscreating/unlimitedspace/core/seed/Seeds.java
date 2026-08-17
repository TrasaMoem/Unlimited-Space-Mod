package com.modscreating.unlimitedspace.core.seed;

/**
 * Deterministic 64-bit seed derivation with domain separation.
 *
 * <p>Single stable mechanism based on SplitMix64-style mixing plus an internal
 * FNV-1a string fold for namespace discriminators. Everything is a pure function
 * of its inputs, so results are stable across restarts, JVMs and generation order.
 *
 * <p>Remit the chain:
 * <pre>
 * WorldSeed -&gt; Seeds.galaxy -&gt; Seeds.starSystem -&gt; Seeds.planet -&gt; Seeds.subsystem
 * </pre>
 */
public final class Seeds {

    private Seeds() {}

    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x00000100000001B3L;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /**
     * Internal FNV-1a (64-bit) fold over a string (UTF-16 code units).
     * Deterministic, platform independent and deliberately NOT {@link String#hashCode()}.
     */
    static long hashString(String s) {
        long h = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    /** SplitMix64-style avalanche combine of two longs (ordered). */
    static long mix(long a, long b) {
        long h = a + GOLDEN;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 31)) + b;
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return h;
    }

    /** Derive a child seed: root + namespace discriminator + ordered arguments. */
    public static long derive(long root, String namespace, long... args) {
        long h = mix(root, hashString(namespace));
        for (long arg : args) {
            h = mix(h, arg);
        }
        return h;
    }

    /* ----- fixed-slot, order-independent random draws (all pure functions) ----- */

    /** Deterministic draw in {@code [min, max)}. */
    public static long rangeLong(long seed, long slot, long min, long max) {
        if (max <= min) return min;
        long h = mix(seed, slot);
        return min + Long.remainderUnsigned(h, max - min);
    }

    /** Deterministic draw in {@code [0, 1)} using 52 bits of the mix output. */
    public static double fraction(long seed, long slot) {
        return (mix(seed, slot) & 0x000FFFFFFFFFFFFFL) / (double) 0x000FFFFFFFFFFFFFL;
    }

    /** Deterministic draw in {@code [min, max)}. */
    public static double rangeDouble(long seed, long slot, double min, double max) {
        return min + (max - min) * fraction(seed, slot);
    }

    /* ----- convenience derivations for the galaxy chain ----- */

    public static long galaxy(long worldSeed) {
        return derive(worldSeed, "unlimitedspace.galaxy");
    }

    public static long starSystem(long galaxySeed, int index) {
        return derive(galaxySeed, "unlimitedspace.starSystem", index);
    }

    public static long planet(long starSystemSeed, int orbitIndex) {
        return derive(starSystemSeed, "unlimitedspace.planet", orbitIndex);
    }

    public static long moon(long planetSeed, int moonIndex) {
        return derive(planetSeed, "unlimitedspace.moon", moonIndex);
    }

    public static long subsystem(long planetSeed, String subsystem) {
        return derive(planetSeed, "unlimitedspace.sub." + subsystem);
    }
}
