package com.modscreating.unlimitedspace.core.galaxy.layout;

/**
 * Tunable constants for the galaxy layout (Phase 5). Pure domain values; no Minecraft
 * coupling. Intentionально minimal so the layout can evolve via {@link WorldgenVersion}.
 */
public final class SpaceConstants {

    private SpaceConstants() {}

    /**
     * Number of Minecraft blocks that correspond to one abstract galaxy unit (GU).
     * Picked so the default galaxy (radius 100 GU) spans roughly 25600 blocks (~1600
 * chunks) on a side, leaving room for the spatial hash without overflowing int indices.
     */
    public static final double BLOCKS_PER_GALAXY_UNIT = 256.0;

    /**
     * Hard upper bound on planets per star system for Phase 5 (keeps per-system work
     * constant and independent of galaxy size). Each cell owns exactly one system.
     */
    public static final int MAX_PLANETS_PER_SYSTEM = 6;

    /**
     * Per-axis jitter envelope as a fraction of a cell. Strictly less than 0.5 so a
     * system can never cross its cell border, which keeps (a) planets inside the star
     * cell and (b) minimum centre-to-centre distance between adjacent systems positive.
     */
    public static final double JITTER_FRACTION = 0.25;
}
