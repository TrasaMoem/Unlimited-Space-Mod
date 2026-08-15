package com.modscreating.unlimitedspace.core.worldgen;

/**
 * Abstract surface material of a planet, deliberately NOT bound to any Minecraft
 * {@code BlockState}. The mapping {@code SurfaceMaterial -> BlockState} lives in
 * the Minecraft adapter layer ({@code worldgen/planet}), never in the core domain.
 */
public enum SurfaceMaterial {
    STONE,
    ROCK,
    SAND,
    ICE,
    BASALT,
    GRASSY,
    METALLIC;
}
