package com.modscreating.unlimitedspace.core.physics;

/**
 * Pure-domain gravity / arrival helpers shared by generation, arrival and rendering.
 *
 * <p>Two unit systems coexist and must never be confused:
 * <ul>
 *   <li><b>Earth-g (domain)</b> — surface/moon generation works in this unit, where {@code 1.0}
 *       == Earth surface gravity (≈ 9.81 m/s²). Natural variation (0.05 … 4.0) is preserved.</li>
 *   <li><b>m/s² (Creating Space)</b> — the unit {@code RocketAccessibleDimension#gravity()} consumes
 *       via its {@code entity.gravity} mixins. CS sample data confirms this: {@code overworld=9.81},
 *       {@code the_moon=1.6}, {@code mars=3.71}, {@code venus=1.6}. The datapack must therefore author
 *       surface/moon gravity in m/s² (Earth-g × 9.81).</li>
 * </ul>
 *
 * <p>R14.5.1: ORBIT gravity is a single, fixed source of truth — {@link #CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ}.
 * It is exactly the value Creating Space's own datapack uses for {@code earth_orbit} / {@code mars_orbit} /
 * {@code moon_orbit} ({@code gravity: 0}, i.e. {@code CSDimensionUtil.isOrbit == (gravity == 0)}).
 * Every procedural orbit (planet, moon) and every weightless asteroid field carries this same value.
 * Orbit/field gravity is deliberately INDEPENDENT of the celestial body's surface gravity — it must never
 * be derived from {@code PlanetProperties/MoonProperties/StarProperties}.
 *
 * <p>Surface worlds, in contrast, always carry a positive, playable gravity in m/s², clamped to at
 * least {@link #MIN_PLAYABLE_GRAVITY_EARTH_G} so a surface can never collapse to zero/unplayable.
 *
 * <p>No Minecraft types are referenced — fully unit-testable.
 */
public final class Gravity {

    private Gravity() {
    }

    /**
     * EXACT Creating Space orbit gravity in m/s². Verified against the installed CS 1.7.18 datapack
     * ({@code creatingspace:earth_orbit} / {@code mars_orbit} / {@code moon_orbit} all carry
     * {@code gravity: 0}) and against the decompiled {@code CSDimensionUtil.isOrbit == (gravity == 0)}.
     * Used by every procedural orbit and every zero-g asteroid field. Independent of body surface gravity.
     */
    public static final double CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ = 0.0;

    /**
     * EXACT Creating Space orbit arrival height. Verified against the installed CS 1.7.18 datapack:
     * {@code earth_orbit} / {@code mars_orbit} / {@code moon_orbit} all use {@code arrivalHeight: 64}.
     * The player/rocket is placed directly at this Y (no descent — orbit movement is weightless).
     */
    public static final int CS_ORBIT_ARRIVAL_HEIGHT = 64;

    /**
     * EXACT Creating Space surface arrival height. Verified against the installed CS 1.7.18 datapack:
     * {@code venus} / {@code mars} / {@code the_moon} / {@code overworld} all use {@code arrivalHeight: 200}.
     * The player/rocket starts high above the terrain and descends (surface gravity &gt; 0 pulls it down) —
     * the standard Creating Space planetary landing used by Venus/Mars/Earth.
     */
    public static final int CS_SURFACE_ARRIVAL_HEIGHT = 200;

    /**
     * Backwards-compatible alias for the CS orbit gravity (R12.2/12.3 era name). Exactly
     * {@link #CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ}.
     */
    public static final double MIN_ORBIT_GRAVITY_METERS_PER_SECOND_SQ =
            CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ;

    /**
     * Minimum usable surface gravity, in Earth-g. Chosen as the largest value that still matches an
     * actual CS body (CS Moon = 1.62 m/s² = 0.165 g) while remaining below the accepted low-gravity
     * variation so genuinely low worlds are not collapsed to Earth gravity.
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
     * Clamp a generated surface/moon gravity to the playable floor. Zero / negative values (which
     * can never legitimately occur for a surface world) are lifted to the floor; genuine small values
     * such as {@code 0.05} are left untouched so variation survives.
     */
    public static double playableEarthG(double earthG) {
        if (Double.isNaN(earthG) || earthG < MIN_PLAYABLE_GRAVITY_EARTH_G) {
            return MIN_PLAYABLE_GRAVITY_EARTH_G;
        }
        return earthG;
    }

    /**
     * Surface / moon worlds: gravity must be positive and playable in m/s².
     */
    public static boolean isPlayableMetersPerSecondSq(double gravityMs) {
        return gravityMs > 0.0 && gravityMs >= toMetersPerSecondSq(MIN_PLAYABLE_GRAVITY_EARTH_G) - 1e-9;
    }

    /**
     * A valid orbit / zero-g field gravity in m/s². Matches Creating Space's Earth-orbit value
     * (zero-g orbital flight), so {@code 0} is accepted but negative values are not.
     */
    public static boolean isOrbitCompatibleGravity(double gravityMs) {
        return gravityMs >= 0.0;
    }

    /**
     * R14.5.1 REQ 4/9: asteroid fields are WEIGHTLESS. Returns the exact Creating Space orbit
     * gravity ({@code 0}) so the field plays as a weightless space field (direct centered arrival,
     * zero-g manoeuvring via the rocket thrusters). The CS travel entry must keep {@code orbitedBody}
     * pointing at a real, always-loaded dimension so the zero-g orbit-drop fallback never NPEs.
     */
    public static double asteroidGravityMetersPerSecondSq() {
        return CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ;
    }

    // ---------------------------------------------------------------- R14.9.3-D star-surface gravity

    /**
     * Real solar surface gravity in Earth-g ({@code 274 m/s² / 9.81 m/s²}). Anchor of the
     * physical {@code g ∝ M / R²} star-surface formula.
     */
    public static final double SUN_SURFACE_GRAVITY_EARTH_G = 274.0 / EARTH_G_TO_METERS_PER_SECOND_SQ;

    /** Controlled star-surface gravity range, in Earth-g. Every normal star surface is VERY HIGH. */
    public static final double MIN_STAR_SURFACE_GRAVITY_EARTH_G = 25.0;
    public static final double MAX_STAR_SURFACE_GRAVITY_EARTH_G = 75.0;

    /**
     * R14.9.3-D: the ONE star-surface gravity formula (no second model). Physically derived from
     * the star's own seed-generated data: mass in solar masses (from the luminosity relation) and
     * radius in solar radii via {@code g = g_sun · M / R²}, clamped into a controlled very-high
     * band [25g .. 75g] — always far above ordinary planet gravity (natural planet range is
     * 0.05..4.0 g) yet never so extreme that movement becomes unusable. Pure and deterministic.
     *
     * @param massSolar   star mass in solar masses
     * @param radiusSolar star radius in solar radii
     */
    public static double starSurfaceGravityEarthG(double massSolar, double radiusSolar) {
        if (!(massSolar > 0.0) || !(radiusSolar > 0.0)) {
            return MIN_STAR_SURFACE_GRAVITY_EARTH_G;
        }
        double raw = SUN_SURFACE_GRAVITY_EARTH_G * (massSolar / (radiusSolar * radiusSolar));
        return Math.clamp(raw, MIN_STAR_SURFACE_GRAVITY_EARTH_G, MAX_STAR_SURFACE_GRAVITY_EARTH_G);
    }
}