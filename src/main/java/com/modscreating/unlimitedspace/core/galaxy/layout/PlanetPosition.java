package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;

/**
 * 2D spatial position of a planet, in abstract galaxy units (GU).
 *
 * <p>Position is the absolute galactic centre of the planet: the owning star system
 * centre plus a deterministic orbital offset. It depends only on
 * (galaxySeed, system cell, orbitIndex) and on no other generation order.
 *
 * @param id     stable planet identity
 * @param seed   stable planet seed (deterministic, reused by terrain/biome/ore...)
 * @param x      galactic x coordinate (GU)
 * @param z      galactic z coordinate (GU)
 * @param orbit  stable orbit slot this planet occupies
 * @param orbitRadius  orbital distance from the parent star (GU)
 */
public record PlanetPosition(PlanetId id, long seed, double x, double z, int orbit, double orbitRadius) {

    public PlanetSeed seedObject() {
        return new PlanetSeed(seed);
    }

    public double distanceSq(double x, double z) {
        double dx = x - x();
        double dz = z - z();
        return dx * dx + dz * dz;
    }

    public double distance(GalaxyCoordinate c) {
        return Math.sqrt(distanceSq(c.x(), c.z()));
    }
}
