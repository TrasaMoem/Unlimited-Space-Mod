package com.modscreating.unlimitedspace.core.destination;

/**
 * Kind of celestial body that owns a {@link WorldDestination}.
 *
 * <p>Pure domain data (no Minecraft coupling). Extensible so that the corrected
 * architecture (a planet, a moon and an asteroid cluster each being its own world)
 * can be built on a single, stable mechanism instead of one-off classes.
 */
public enum BodyKind {
    /** A classical rocky/gaseous planet. */
    PLANET,
    /** A moon orbiting a planet. */
    MOON,
    /** An independent cluster of asteroid bodies (no orbit destination). */
    ASTEROID_CLUSTER
}