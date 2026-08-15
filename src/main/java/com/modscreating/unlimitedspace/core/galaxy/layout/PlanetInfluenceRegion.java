package com.modscreating.unlimitedspace.core.galaxy.layout;

/**
 * Mathematical model of the region of (Minecraft) space that belongs to one planet.
 *
 * <p>For Phase 5 this is a pure-shape record (a circle in the x/z plane, expressed in
 * abstract galaxy units). It is deliberately independent from any chunk generator:
 * the question it answers is \&quot;does this GalaxyCoordinate correspond to this planet\&quot;.
 *
 * <p>The architecture is open: future phases layer additional concentric radii
 * (atmosphere / gravity / landing / transition) by extending this record, without
 * changing the lookup algorithm.
 *
 * @param planet             the planet this region belongs to
 * @param radiusGu           primary influence radius: the surface-generating disc
 * @param atmosphereRadiusGu outer radius of the atmosphere shell (future use)
 * @param gravityRadiusGu    radius at which a planet can still be landed on (future use)
 * @param landingRadiusGu    inner core radius / transition boundary (future use)
 */
public record PlanetInfluenceRegion(PlanetPosition planet,
                                    double radiusGu,
                                    double atmosphereRadiusGu,
                                    double gravityRadiusGu,
                                    double landingRadiusGu) {

    /** Build the region with derived concentric radii from the surface radius. */
    public static PlanetInfluenceRegion of(PlanetPosition planet, double radiusGu) {
        return new PlanetInfluenceRegion(
                planet,
                radiusGu,
                radiusGu * 1.6, // atmosphere shell (future)
                radiusGu * 0.9, // gravity well (future)
                radiusGu * 0.35 // transition / landing (future)
        );
    }

    /** Whether the coordinate lies within the planet surface disc. */
    public boolean contains(GalaxyCoordinate c) {
        return planet.distanceSq(c.x(), c.z()) <= radiusGu * radiusGu;
    }

    public double distanceSq(GalaxyCoordinate c) {
        return planet.distanceSq(c.x(), c.z());
    }

    public double distance(GalaxyCoordinate c) {
        return Math.sqrt(distanceSq(c));
    }
}
