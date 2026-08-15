package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic spatial grid that maps between galaxy-space coordinates and star-system
 * identities. Package-private helper of {@link GalaxyLayout} / {@link GalaxySpatialIndex}.
 *
 * <p>Why a grid: star systems are placed once per cell, so the cell coordinate is the
 * canonical identity of a system. Mapping cell&lt;-&gt;index is a fixed, invertible
 * zigzag-Cantor pairing &mdash; O(1) both ways, order-independent, no global random.
 * System placement itself (jitter) is a pure function of the seed via {@link Seeds}.
 */
final class SpatialGrid {

    /** Size of one cell in galaxy units. Density &rarr; 1 GU&sup2; holds ~starDensity systems. */
    final double cellSize;
    private final double halfCell;
    private final int radiusCells;
    /** Bounding radius of the whole galaxy, in GU (max distance from origin to a system). */
    final double galaxyRadiusGu;

    SpatialGrid(GalaxyParameters params) {
        // cell area = pi / starDensity  => systems per GU&sup2; == starDensity (exactly).
        this.cellSize = Math.sqrt(Math.PI / params.starDensity());
        this.halfCell = cellSize * 0.5;
        this.radiusCells = Math.max(1, (int) Math.ceil(params.radius() / cellSize));
        this.galaxyRadiusGu = radiusCells * cellSize;
    }

    int cx(GalaxyCoordinate c) { return (int) Math.floor(c.x() / cellSize); }
    int cz(GalaxyCoordinate c) { return (int) Math.floor(c.z() / cellSize); }
    double cellCenter(int cell) { return cell * cellSize + halfCell; }
    double jitter(long systemSeed, long slot) {
        return (Seeds.fraction(systemSeed, slot) - 0.5) * cellSize * SpaceConstants.JITTER_FRACTION;
    }
    double cellSize() { return cellSize; }
    int radiusCells() { return radiusCells; }
    double galaxyRadiusGu() { return galaxyRadiusGu; }
    double minSeparationGu() {
        // adjacent cells: cellSize - 2*(max per-axis jitter) = cellSize*(1-2*JITTER_FRACTION)
        return cellSize * (1.0 - 2.0 * SpaceConstants.JITTER_FRACTION);
    }

    /** True if the cell lies inside the populated galaxy disc (centred at the origin). */
    boolean inDisc(int cx, int cz) {
        long r = radiusCells;
        return (long) cx * cx + (long) cz * cz <= r * r;
    }

    /** Encode a (possibly negative) cell into its stable, non-negative long index (zigzag &rarr; Cantor). */
    long indexOfCell(int cx, int cz) {
        long a = zigzag(cx), b = zigzag(cz);
        long s = a + b;
        return (s * (s + 1)) / 2 + b;
    }

    /** Invert {@link #indexOfCell(int, int)} back to the cell coordinates. */
    static int[] cellOfIndex(long index) {
        long w = (long) Math.floor((Math.sqrt(8.0 * index + 1.0) - 1.0) / 2.0);
        long t = (w * (w + 1)) / 2;
        long b = index - t;
        long a = w - b;
        return new int[] { unzag(a), unzag(b) };
    }

    private static long zigzag(int n) {
        return n >= 0 ? 2L * n : -2L * n - 1L;
    }

    private static int unzag(long z) {
        return z % 2 == 0 ? (int) (z / 2) : (int) (-(z + 1) / 2);
    }
}
