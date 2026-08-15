package com.modscreating.unlimitedspace.core.galaxy.layout;

/**
 * Coordinate in abstract galaxy units (GU) used as the lookup key for the spatial index.
 *
 * <p>It is a pure domain value (no {@code BlockPos}/{@code ChunkPos}): the Minecraft-side
 * adapter (Phase 6) converts chunk/block coordinates into a GalaxyCoordinate by dividing
 * by {@link SpaceConstants#BLOCKS_PER_GALAXY_UNIT}. It may be negative because the galaxy
 * is centred at the origin and Minecraft-space uses signed coordinates.
 *
 * @param x coordinate in GU (axis perpendicular to the galactic plane diagonal; here Z)
 * @param z coordinate in GU
 */
public record GalaxyCoordinate(double x, double z) {

    public static GalaxyCoordinate of(double x, double z) {
        return new GalaxyCoordinate(x, z);
    }

    public double distanceSq(double x, double z) {
        double dx = x - x();
        double dz = z - z();
        return dx * dx + dz * dz;
    }

    public double distance(double x, double z) {
        return Math.sqrt(distanceSq(x, z));
    }
}
