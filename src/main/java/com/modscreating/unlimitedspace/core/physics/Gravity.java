package com.modscreating.unlimitedspace.core.physics;

/**
 * Pure-domain gravity helpers shared by generation, arrival and rendering (R12.2, Bug #1).
 *
 * <p>Two unit systems coexist and must never be confused:
 * <ul>
 *   <li><b>Earth-g (domain)</b> — the unit used by {@link
 *       com.modscreating.unlimitedspace.core.planets.PlanetProperties} and
 *       {@link com.modscreating.unlimitedspace.core.planets.MoonProperties}, where {@code 1.0}
 *       == Earth surface gravity (≈ 9.81 m/s²). Planet/moon generation works in this unit so
 *       natural variation (0.05 … 4.0) is preserved.</li>
 *   <li><b>m/s² (Creating Space)</b> — the unit that {@code
 *       com.raecraft.creatingspace.api.planets.RocketAccessibleDimension#gravity()} consumes
 *       via its {@code entity.gravity} mixins. CS sample data confirms this:
 *       {@code overworld=9.81}, {@code the_moon=1.6}, {@code mars=3.71}, {@code venus=1.6}.
 *       CS therefore treats our JSON {@code "gravity"} as a physical acceleration in m/s².</li>
 * </ul>
 *
 * <p>The original Bug #1 ("some bodies have zero / unusably small gravity") was caused by the
 * surface &amp; asteroid {@code rocket_accessible_dimension} JSONs storing <em>Earth-g</em>
 * values ({@code 0.99}, {@code 0.16}, {@code 0.05}) while CS interpreted them as m/s² — a moon
 * arriving at {@code 0.16 m/s²} ≈ 0.016 g with near-weightless movement. The fix: surface /
 * asteroid gravity is authored in m/s² (Earth-g × 9.81).
 *
  * <p>R12.3 Bug #1 is resolved 1:1 against Creating Space: every orbit / asteroid-field worldspace
 * carries {@code gravity: 0}, exactly the value CS itself uses for {@code earth_orbit} (and
 * {@code mars_orbit}, {@code moon_orbit}). The player floats in the middle of the destination and
 * manoeuvres entirely with the rocket thrusters — CS's own thruster model does not depend on the
 * dimension gravity, so zero-g orbital flight is fully playable (no artificial gravity floor is
 * injected into orbits). Surface worlds, in contrast, must always be walkable: their gravity is
 * authored in m/s² and clamped to at least {@link #MIN_PLAYABLE_GRAVITY_EARTH_G} so a surface can
 * never collapse to zero/unplayable.
 *
 * <p>No Minecraft types are referenced — fully unit-testable.
 */
public final class Gravity {

    private Gravity() {
    }

    /**
     * Gravity (in m/s², the unit CS consumes) applied to every orbit / asteroid-field destination.
     * It is EXACTLY the value Creating Space uses for the Earth orbit — zero-g orbital flight
     * ({@code creatingspace:earth_orbit} carries {@code gravity: 0}). The player teleports to the
     * centre of the orbit/field and floats, manoeuvring entirely with the rocket thrusters.
     */
    public static final double MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ = 0.0;

    /**
     * Minimum usable surface gravity, in Earth-g. Chosen as the largest value that still
     * matches an actual CS body (CS Moon = 1.62 m/s² = 0.165 g) while remaining below the
     * asteroid value (0.05 g, used by the user's accepted variation list) so that natural
     * low-gravity worlds are not collapsed to Earth gravity. Bodies below this are not
     * genuinely "zero-g" — they are simply unplayably floaty in vanilla movement.
     */
    public static final double MIN_PLAYABLE_GRAVITY_EARTH_G = 0.05;

    /** Exact conversion factor: 1 Earth-g == 9.81 m/s². */
    public static final double EARTH_G_TO_METERS_PER_SECOND_SQ = 9.81;

    /**
     * Convert an Earth-g value (the domain generator unit) to the m/s² value that belongs in a
     * CS {@code RocketAccessibleDimension} JSON for a surface / playable world.
     */
    public static double toMetersPerSecondSq(double earthG) {
        return earthG * EARTH_G_TO_METERS_PER_SECOND_SQ;
    }

    /**
     * Clamp a generated surface/moon gravity to the playable floor. Zero / negative values
     * (which can never legitimately occur for a surface world) are lifted to the floor;
     * genuine small values such as {@code 0.05} are left untouched so variation survives.
     */
    public static double playableEarthG(double earthG) {
        if (Double.isNaN(earthG) || earthG < MIN_PLAYABLE_GRAVITY_EARTH_G) {
            return MIN_PLAYABLE_GRAVITY_EARTH_G;
        }
        return earthG;
    }

    /**
     * Surface / moon / asteroid worlds: gravity must be positive and playable in m/s².
     */
    public static boolean isPlayableMetersPerSecondSq(double gravityMs) {
        return gravityMs > 0.0 && gravityMs >= toMetersPerSecondSq(MIN_PLAYABLE_GRAVITY_EARTH_G) - 1e-9;
    }

    /**
     * A valid orbit / asteroid-field gravity in m/s². Matches Creating Space's Earth-orbit value
     * (zero-g orbital flight), so {@code 0} is accepted but negative values are not.
     */
    public static boolean isOrbitCompatibleGravity(double gravityMs) {
        return gravityMs >= 0.0;
    }
}
