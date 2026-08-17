package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic terrain-shape pattern selected per-planet from its terrain seed.
 *
 * <p>Each pattern carries octave / roughness / frequency modifiers that feed the
 * {@link com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator}, so
 * planets can diverge in SHAPE (flat -> hills -> mountains -> cratered -> ...) without
 * any per-planet hardcoding. Selection is a pure function of the planet seed + surface
 * category, hence stable across restarts/JVMs and independent of generation order.
 *
 * <p>Examples only — the catalogue is intentionally extensible; adding a new constant
 * never rewrites generators.
 */
public enum TerrainPattern {

    FLAT(1, 0.40, 1.00),
    ROLLING(2, 0.90, 1.00),
    HILLS(2, 1.20, 1.10),
    MOUNTAINS(3, 1.60, 1.30),
    PLATEAU(2, 1.00, 0.70),
    CRATERED(3, 1.10, 1.60),
    FRACTURED(3, 1.40, 1.20),
    VOLCANIC(3, 1.30, 0.90),
    ROCKY_HIGHLANDS(2, 1.00, 1.00),
    ISLANDS(3, 0.80, 1.80);

    public static final TerrainPattern[] VALUES = values();
    private static final long PATTERN_SLOT = 72001L;

    private final int octaves;
    private final double amplitudeMultiplier;
    private final double frequencyMultiplier;

    TerrainPattern(int octaves, double amplitudeMultiplier, double frequencyMultiplier) {
        this.octaves = octaves;
        this.amplitudeMultiplier = amplitudeMultiplier;
        this.frequencyMultiplier = frequencyMultiplier;
    }

    public int octaves() {
        return octaves;
    }

    public double amplitudeMultiplier() {
        return amplitudeMultiplier;
    }

    public double frequencyMultiplier() {
        return frequencyMultiplier;
    }

    /**
     * Deterministic pattern selection. Gas giants never use {@link #FLAT} (they must not
     * read as an empty void); everything else is an even draw across the full catalogue so
     * any pattern is reachable for any seed family.
     */
    public static TerrainPattern select(long planetSeed, PlanetSurface surface) {
        if (surface == PlanetSurface.GASEOUS) {
            return CRATERED;
        }
        double f = Seeds.fraction(planetSeed, PATTERN_SLOT);
        int idx = (int) Math.floor(f * VALUES.length);
        if (idx < 0) idx = 0;
        if (idx >= VALUES.length) idx = VALUES.length - 1;
        return VALUES[idx];
    }
}

