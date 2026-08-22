package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.StarStage;
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

    public static String starKey(StarSystemId id, boolean surface) {
        return "star/" + id.code() + "/" + (surface ? "surface" : "orbit");
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

    // ---- deltaV (mirror the static proof JSON) ----

    private static final int SURFACE_TO_ORBIT = 200;
    private static final int ORBIT_TO_SURFACE = 200;
    private static final int MOON_TO_MOON = 50;
    private static final int MOON_TO_PLANET = 80;
    private static final int TO_OVERWORLD = 1200;
    private static final int ASTEROID_FROM_OVERWORLD = 1500;

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
                adj(orbit, SURFACE_TO_ORBIT));
    }

    // ================================================================ PLANET ORBIT

    public static ProceduralRocketAccessibleDimension planetOrbit(Planet planet, String namespace) {
        PlanetId id = planet.id();
        String surface = rl(namespace, planetKey(id, true));
        String orbit = rl(namespace, planetKey(id, false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, ORBIT_TO_SURFACE);
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
                adj(orbit, MOON_TO_MOON));
    }

    // ================================================================ MOON ORBIT

    public static ProceduralRocketAccessibleDimension moonOrbit(Planet parent, Moon moon, String namespace) {
        MoonId id = moon.id();
        String surface = rl(namespace, moonKey(id, true));
        String orbit = rl(namespace, moonKey(id, false));
        String planetOrbit = rl(namespace, planetKey(parent.id(), false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, MOON_TO_MOON);
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
    public static ProceduralRocketAccessibleDimension starSurface(StarSystem system, String namespace) {
        StarWorldgenProfile prof = StarWorldgenProfile.from(system);
        String surface = rl(namespace, starKey(system.id(), true));
        String orbit = rl(namespace, starKey(system.id(), false));
        boolean blackHole = prof.blackHole();
        int arrival = blackHole ? Gravity.CS_ORBIT_ARRIVAL_HEIGHT : Gravity.CS_SURFACE_ARRIVAL_HEIGHT;
        double gravity = blackHole ? Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ
                : Gravity.toMetersPerSecondSq(starSurfaceGravityEarthG(system));
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
     * Deterministic, playable Earth-g for a molten star surface, keyed only by the star's stage
     * (no {@code Random}). The star model carries no gravity field, so this is the single
     * domain-derived stage mapping for the surface landing.
     */
    public static double starSurfaceGravityEarthG(StarSystem system) {
        StarStage stage = StarStage.from(system.star());
        return switch (stage) {
            case RED_DWARF -> 0.85;
            case BLUE_DWARF -> 1.05;
            case MAIN_SEQUENCE -> 1.0;
            case GIANT -> 1.3;
            case SUPERGIANT -> 1.55;
            case WHITE_DWARF -> 1.15;
            case NEUTRON_STAR -> 2.0;
            case BLACK_HOLE -> 0.05;
            case SUPERNOVA -> 1.25;
        };
    }

    // ================================================================ STAR ORBIT

    /**
     * Zero-g star orbit. With a star surface world now available, {@code orbitedBody} is the star's
     * own molten surface (a planet orbit falls to its planet's surface; by analogy the star orbit
     * falls to the star's surface), and the orbit adjacency reaches that surface + overworld.
     */
    public static ProceduralRocketAccessibleDimension starOrbit(StarSystem system, String namespace) {
        String orbit = rl(namespace, starKey(system.id(), false));
        String surface = rl(namespace, starKey(system.id(), true));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, ORBIT_TO_SURFACE);
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