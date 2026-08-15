package com.modscreating.unlimitedspace.core.seed;

/**
 * Stable seed for a whole galaxy. Derived deterministically from the Minecraft
 * world seed via {@link Seeds#galaxy(long)}.
 *
 * @param value the 64-bit seed
 */
public record GalaxySeed(long value) {
}
