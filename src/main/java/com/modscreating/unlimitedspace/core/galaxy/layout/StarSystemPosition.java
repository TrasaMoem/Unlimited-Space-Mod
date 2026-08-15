package com.modscreating.unlimitedspace.core.galaxy.layout;

import com.modscreating.unlimitedspace.core.seed.StarSystemSeed;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

/**
 * 2D spatial position of a star system within the galaxy, in abstract galaxy units (GU).
 *
 * <p>It is the bridge between the seed hierarchy ({@link StarSystemSeed}) and the spatial
 * layout: a pure value object, no Minecraft coupling. The x/z coordinates here are the
 * jittered centre of the system's grid cell; {@code y} (vertical spread between systems)
 * is intentionally excluded from chunk lookup and kept available via the legacy
 * {@link com.modscreating.unlimitedspace.core.galaxy.GalacticPosition} for rendering only.
 *
 * @param id   stable system identity (its grid-cell Cantor index)
 * @param seed stable system seed (deterministic, seed-driven)
 * @param x    galactic x coordinate (GU)
 * @param z    galactic z coordinate (GU)
 * @param star deterministic star description for this system
 */
public record StarSystemPosition(StarSystemId id, long seed, double x, double z, Star star) {

    public double distanceSq(double x, double z) {
        double dx = x - x();
        double dz = z - z();
        return dx * dx + dz * dz;
    }

    public double distance(GalaxyCoordinate c) {
        return Math.sqrt(distanceSq(c.x(), c.z()));
    }
}
