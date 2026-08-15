package com.modscreating.unlimitedspace.core.seed;

/**
 * Stable seed for a single star system. Derived from the galaxy seed and the
 * system's fixed index via {@link Seeds#starSystem(long, int)}.
 *
 * @param value the 64-bit seed
 */
public record StarSystemSeed(long value) {
}
