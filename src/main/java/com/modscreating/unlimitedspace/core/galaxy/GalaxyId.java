package com.modscreating.unlimitedspace.core.galaxy;

/**
 * Stable identity of a galaxy. There is exactly one galaxy per world, seeded from
 * the world seed, so this is a singleton.
 */
public enum GalaxyId {
    INSTANCE;

    public String code() {
        return "galaxy";
    }
}
