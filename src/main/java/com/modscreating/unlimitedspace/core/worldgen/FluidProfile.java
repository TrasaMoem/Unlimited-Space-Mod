package com.modscreating.unlimitedspace.core.worldgen;

/**
 * Abstract fluid profile of a planet surface. Pure data; the mapping to a concrete
 * fluid (e.g. Minecraft water) is done in the Minecraft adapter layer.
 */
public enum FluidProfile {
    NONE,
    WATER;
}
