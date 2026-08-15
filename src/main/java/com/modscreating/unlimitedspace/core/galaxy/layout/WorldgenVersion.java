package com.modscreating.unlimitedspace.core.galaxy.layout;

/**
 * Discrete version tag for the galaxy layout &amp; worldgen pipeline.
 *
 * <p>Placed as a standalone concept (rather than inside {@code GalaxyParameters}) so a
 * future phase can swap the placement algorithm while keeping the configuration shape
 * stable. A {@link GalaxyLayout} always reports its {@link #version()};
 * persistence/migration (Phase 5 limitation: deliberately NOT implemented here) will
 * later compare this tag against a stored value to decide regen vs. preserve.
 */
public enum WorldgenVersion {
    /**
     * V1: deterministic grid-cell placement.
     * <ul>
     *   <li>systems live on a fixed cell grid derived from GalaxyParameters;</li>
     *   <li>each cell maps to a stable int index via zigzag-Cantor pairing;</li>
     *   <li>positions use seed-dependent jitter inside the cell.</li>
     * </ul>
     */
    V1_GRID("grid-cell placement with a deterministic spatial hash");

    private final String description;

    WorldgenVersion(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
