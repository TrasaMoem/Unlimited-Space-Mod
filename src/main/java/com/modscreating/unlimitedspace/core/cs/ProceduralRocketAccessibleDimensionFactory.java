package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.StarWorldgenProfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-domain factory that turns existing celestial domain metadata (Planet/Moon/AsteroidCluster/
 * StarSystem) into Creating Space {@link ProceduralRocketAccessibleDimension} definitions.
 *
 * <p>R14.5.3: The metadata is what Creating Space needs to build its {@code travelMap} from the
 * {@code creatingspace:rocket_accessible_dimension} datapack registry BEFORE any world is created.
 * Creating definitions here NEVER creates a {@code ServerLevel}/{@code ChunkGenerator}; world
 * materialisation stays with DynamicDimensions, on demand, at travel time.
 *
 * <p>All methods are pure and deterministic — the same {@code WorldSeed + stable id} always yields
 * the same definition. No {@code Random}/UUID. Creating Space semantics mirrored here:
 * <ul>
 *   <li>orbit gravity = {@code 0} (CS {@code isOrbit == (gravity == 0)}), arrival 64;</li>
 *   <li>surface: positive gravity in m/s² + exact CS surface arrival ({@code Gravity#CS_SURFACE_ARRIVAL_HEIGHT});</li>
 *   <li>orbitedBody of an orbit = its own surface (par the static {@code system_0000_*} proof);</li>
 *   <li>asteroid = weightless field ({@link AsteroidFieldGeometry#arrivalY()}, orbitedBody=overworld).</li>
 * </ul>
 */
public final class ProceduralRocketAccessibleDimensionFactory {

    private ProceduralRocketAccessibleDimensionFactory() {
    }

    // ---- key scheme (mirrors PlanetWorldBinding/MoonWorldBinding/AsteroidWorldBinding/StarWorldBinding) ----

    public static String planetKey(PlanetId id, boolean surface) {
        return "planet/" + id.code() + "/" + (surface ? "surface" : "orbit");
    }

    public static String moonKey(MoonId id, boolean surface) {
        return "moon/" + id.code() + "/" + (surface ? "surface" : "orbit");
    }

    public static String asteroidKey(String clusterCode) {
        return "asteroid/" + clusterCode;
    }

    /** R14.9.2: a star surface/orbit key carries the star's UNIQUE {@link StarId} (primary &#8594; {@code system_XXXX}, companion &#8594; {@code system_XXXX_star_YY}). */
    public static String starKey(StarId id, boolean surface) {
        return "star/" + id.code() + "/" + (surface ? "surface" : "orbit");
    }

    /** Primary-star convenience overload (index 0), backward compatible with the single-star form. */
    public static String starKey(StarSystemId id, boolean surface) {
        return starKey(new StarId(id, 0), surface);
    }

    /** Backwards-compatible single-arg form for the star orbit key (R14.5.1 era). */
    public static String starKey(StarSystemId id) {
        return starKey(id, false);
    }

    private static String rl(String namespace, String path) {
        return namespace + ":" + path;
    }

    private static Map<String, Integer> adj(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    // ---- distances (mirror the static proof JSON values) ----

    private static final int PLANET_SURFACE_DISTANCE = 4200;
    private static final int PLANET_ORBIT_DISTANCE = 5200;
    private static final int MOON_DISTANCE = 500;
    private static final int ASTEROID_DISTANCE = 5200;
    private static final int STAR_DISTANCE = 6000;

    // ---- deltaV (R15.3: scaled by body properties instead of flat constants) ----

    private static final int SURFACE_TO_ORBIT = 200;
    private static final int ORBIT_TO_SURFACE = 200;
    private static final int MOON_TO_MOON = 50;
    private static final int MOON_TO_PLANET = 80;
    private static final int TO_OVERWORLD = 1200;
    private static final int ASTEROID_FROM_OVERWORLD = 1500;

    /**
     * R15.3: heavier planets are harder to escape -> higher surface-orbit deltaV.
     * Deterministic from the planet's own seed-aware gravity (Earth-g).
     */
    private static int surfaceOrbitDeltaV(Planet planet) {
        double g = Gravity.playableEarthG(planet.properties().gravity());
        return Math.max(60, (int) Math.round(SURFACE_TO_ORBIT * Math.max(0.3, g)));
    }

    /** R15.3: landing on a body always costs MORE than reaching its orbit (clamped). */
    private static int descentDeltaV(double gravityEarthG) {
        return Math.min(600, Math.max(60,
                (int) Math.round(ORBIT_TO_SURFACE * Math.max(0.3, gravityEarthG))));
    }

    /** R15.3: moons likewise — low-gravity moons stay cheap, heavy ones cost more. */
    private static int moonSurfaceOrbitDeltaV(Moon moon) {
        double g = Gravity.playableEarthG(moon.properties().gravity());
        return Math.max(20, (int) Math.round(MOON_TO_MOON * Math.max(0.3, g)));
    }

    // ================================================================ PLANET SURFACE

    public static ProceduralRocketAccessibleDimension planetSurface(Planet planet, String namespace) {
        PlanetId id = planet.id();
        double gravityMs = Gravity.toMetersPerSecondSq(Gravity.playableEarthG(planet.properties().gravity()));
        String key = rl(namespace, planetKey(id, true));
        String orbit = rl(namespace, planetKey(id, false));
        return new ProceduralRocketAccessibleDimension(
                key,
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                gravityMs,
                "sun",
                PLANET_SURFACE_DISTANCE,
                adj(orbit, surfaceOrbitDeltaV(planet)));
    }

    // ================================================================ PLANET ORBIT

    public static ProceduralRocketAccessibleDimension planetOrbit(Planet planet, String namespace) {
        PlanetId id = planet.id();
        String surface = rl(namespace, planetKey(id, true));
        String orbit = rl(namespace, planetKey(id, false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, surfaceOrbitDeltaV(planet));
        if (planet.moonCount() > 0) {
            String moonOrbit = rl(namespace, moonKey(planet.moon(0).id(), false));
            a.put(moonOrbit, MOON_TO_PLANET);
        }
        a.put("minecraft:overworld", TO_OVERWORLD);
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                surface,
                PLANET_ORBIT_DISTANCE,
                a);
    }

    // ================================================================ MOON SURFACE

    public static ProceduralRocketAccessibleDimension moonSurface(Planet parent, Moon moon, String namespace) {
        MoonId id = moon.id();
        double gravityMs = Gravity.toMetersPerSecondSq(Gravity.playableEarthG(moon.properties().gravity()));
        String surface = rl(namespace, moonKey(id, true));
        String orbit = rl(namespace, moonKey(id, false));
        String parentSurface = rl(namespace, planetKey(parent.id(), true));
        return new ProceduralRocketAccessibleDimension(
                surface,
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                gravityMs,
                parentSurface,
                MOON_DISTANCE,
                adj(orbit, moonSurfaceOrbitDeltaV(moon)));
    }

    // ================================================================ MOON ORBIT

    public static ProceduralRocketAccessibleDimension moonOrbit(Planet parent, Moon moon, String namespace) {
        MoonId id = moon.id();
        String surface = rl(namespace, moonKey(id, true));
        String orbit = rl(namespace, moonKey(id, false));
        String planetOrbit = rl(namespace, planetKey(parent.id(), false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, moonSurfaceOrbitDeltaV(moon));
        a.put(planetOrbit, MOON_TO_PLANET);
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                surface,
                MOON_DISTANCE,
                a);
    }

    // ================================================================ ASTEROID

    public static ProceduralRocketAccessibleDimension asteroid(AsteroidCluster cluster, String namespace) {
        String key = rl(namespace, asteroidKey(cluster.id().code()));
        return new ProceduralRocketAccessibleDimension(
                key,
                AsteroidFieldGeometry.arrivalY(),
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                "minecraft:overworld",
                ASTEROID_DISTANCE,
                adj("minecraft:overworld", ASTEROID_FROM_OVERWORLD));
    }

    // ================================================================ STAR SURFACE

    /**
     * R14.9: the star's molten/plasma surface is now a playable surface world. Gravity &gt; 0 gives
     * the standard CS sky-descent landing (arrival = CS surface height, 200) so the player/rocket
     * descends onto the plasma plane. A black hole is physically forbidden from having a solid
     * surface (see {@link StarWorldgenProfile}), so it is routed to a ZERO-G void stand-in with a
     * direct weightless arrival (64) and {@code minecraft:overworld} as a real, always-loaded
     * orbitedBody (the zero-g orbit-drop fallback guard). {@code orbitedBody} for a normal star
     * surface is the star's own orbit (the zero-g region you rise into), a real dimension.
     */
    /** Primary-star convenience overload (index 0), backward compatible. */
    public static ProceduralRocketAccessibleDimension starSurface(StarSystem system, String namespace) {
        return starSurface(system, system.star(), namespace);
    }

    /** R14.9.2: surface metadata for a SPECIFIC star (possible companion), keyed by its unique {@link StarId}. */
    public static ProceduralRocketAccessibleDimension starSurface(StarSystem system, Star star, String namespace) {
        StarId id = star.id();
        StarWorldgenProfile prof = StarWorldgenProfile.from(system, star);
        String surface = rl(namespace, starKey(id, true));
        String orbit = rl(namespace, starKey(id, false));
        boolean blackHole = prof.blackHole();
        int arrival = blackHole ? Gravity.CS_ORBIT_ARRIVAL_HEIGHT : Gravity.CS_SURFACE_ARRIVAL_HEIGHT;
        double gravity = blackHole ? Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ
                : Gravity.toMetersPerSecondSq(starSurfaceGravityEarthG(star));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(orbit, SURFACE_TO_ORBIT);
        if (blackHole) {
            a.put("minecraft:overworld", TO_OVERWORLD);
        }
        return new ProceduralRocketAccessibleDimension(
                surface, arrival, gravity,
                blackHole ? "minecraft:overworld" : orbit,
                STAR_DISTANCE, a);
    }

    /**
     * R14.9.3-D: authoritative star-surface gravity for a SPECIFIC star (possible companion).
     *
     * <p>Single domain formula: physically derived from the star's own seed-generated data via
     * {@code g ∝ mass / radius²} (mass from the luminosity relation, radius = {@code size()}),
     * anchored to the Sun (~27.9 g) and clamped to the controlled very-high range [25g..75g]
     * in {@link Gravity#starSurfaceGravityEarthG(double, double)}. Deterministic per star;
     * no Random, no second hidden model. Black holes never reach here (the caller short-circuits
     * them to orbit gravity 0).
     */
    /** Primary-star convenience overload (index 0), backward compatible. */
    public static double starSurfaceGravityEarthG(StarSystem system) {
        return starSurfaceGravityEarthG(system.star());
    }

    public static double starSurfaceGravityEarthG(Star star) {
        return Gravity.starSurfaceGravityEarthG(star.massSolar(), star.size());
    }

    // ================================================================ STAR ORBIT

    /**
     * Zero-g star orbit. With a star surface world now available, {@code orbitedBody} is the star's
     * own molten surface (a planet orbit falls to its planet's surface; by analogy the star orbit
     * falls to the star's surface), and the orbit adjacency reaches that surface + overworld.
     */
    /** Primary-star convenience overload (index 0), backward compatible. */
    public static ProceduralRocketAccessibleDimension starOrbit(StarSystem system, String namespace) {
        return starOrbit(system, system.star(), namespace);
    }

    /** R14.9.2: orbit metadata for a SPECIFIC star (possible companion), keyed by its unique {@link StarId}. */
    public static ProceduralRocketAccessibleDimension starOrbit(StarSystem system, Star star, String namespace) {
        StarId id = star.id();
        String orbit = rl(namespace, starKey(id, false));
        String surface = rl(namespace, starKey(id, true));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, descentDeltaV(starSurfaceGravityEarthG(star)));
        a.put("minecraft:overworld", TO_OVERWORLD);
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                surface,
                STAR_DISTANCE,
                a);
    }
}