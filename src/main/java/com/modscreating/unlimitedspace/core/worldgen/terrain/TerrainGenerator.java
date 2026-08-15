package com.modscreating.unlimitedspace.core.worldgen.terrain;

/**
 * Deterministic terrain generator for a single planet. Pure domain; no Minecraft
 * types. A {@code height} is a pure function of the planet's terrain seed and the
 * column coordinates, so results are stable across restarts/JVMs.
 *
 * <p>The interface is deliberately small and kept open to future evolution without
 * rewriting consumers: later phases may add continentalness/erosion factors,
 * peaks/valleys and 3D density as additional methods on specialised
 * implementations.
 */
public interface TerrainGenerator {

    /**
     * World-space surface elevation (Y) for a column.
     *
     * @param x block x
     * @param z block z
     * @return the surface height for the column
     */
    double height(int x, int z);

    /** The deterministic terrain seed this generator was built from. */
    long seed();
}
