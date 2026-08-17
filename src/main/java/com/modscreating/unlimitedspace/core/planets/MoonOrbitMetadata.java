package com.modscreating.unlimitedspace.core.planets;

/**
 * Deterministic orbital metadata of a moon around its parent planet.
 *
 * <p>Not n-body physics — simply stable metadata for future system visualisation,
 * Galaxy Map, Creating Space travel distances and orbit rendering.
 *
 * @param moonIndex       per-planet moon index (>= 0), the identity-relevant slot
 * @param orbitalOrder    orbital slot/order (1-based; 1 = closest to planet)
 * @param relativeDistance relative orbital distance in [0,1] (normalised)
 * @param eccentricity    orbital eccentricity placeholder in [0,1)
 * @param inclination     orbital inclination placeholder in radians [0, pi]
 */
public record MoonOrbitMetadata(
        int moonIndex,
        int orbitalOrder,
        double relativeDistance,
        double eccentricity,
        double inclination) {

    public MoonOrbitMetadata {
        if (moonIndex < 0) throw new IllegalArgumentException("moonIndex must be >= 0");
        if (orbitalOrder < 1) throw new IllegalArgumentException("orbitalOrder must be >= 1");
        if (eccentricity < 0.0 || eccentricity >= 1.0)
            throw new IllegalArgumentException("eccentricity must be in [0,1)");
        if (inclination < 0.0 || inclination > Math.PI)
            throw new IllegalArgumentException("inclination must be in [0,pi]");
    }
}
