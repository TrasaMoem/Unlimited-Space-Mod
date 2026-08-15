package com.modscreating.unlimitedspace.core.planets;

import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * Stable identity of a planet, made of the owning star system + a fixed orbit
 * index. A value object, independent of the display name and of the number of
 * planets generated so far.
 *
 * @param system     owning star system
 * @param orbitIndex stable orbit slot
 */
public record PlanetId(StarSystemId system, int orbitIndex) {

    public PlanetId {
        if (orbitIndex < 0) throw new IllegalArgumentException("orbitIndex must be >= 0");
    }

    public static PlanetId of(StarSystemId system, int orbitIndex) {
        return new PlanetId(system, orbitIndex);
    }

    public String code() {
        return system.code() + "_planet_" + String.format("%02d", orbitIndex);
    }

    @Override
    public String toString() {
        return code();
    }
}
