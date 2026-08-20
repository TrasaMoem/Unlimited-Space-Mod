package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

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

    public static String starKey(StarSystemId id) {
        return "star/" + id.code() + "/orbit";
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

    // ================================================================ STAR ORBIT

    public static ProceduralRocketAccessibleDimension starOrbit(StarSystem system, String namespace) {
        String orbit = rl(namespace, starKey(system.id()));
        String orbitedBody;
        Map<String, Integer> a = new LinkedHashMap<>();
        if (system.planetCount() > 0) {
            orbitedBody = rl(namespace, planetKey(PlanetId.of(system.id(), 0), true));
            a.put(orbitedBody, TO_OVERWORLD);
            a.put("minecraft:overworld", TO_OVERWORLD);
        } else {
            orbitedBody = "minecraft:overworld";
            a.put("minecraft:overworld", TO_OVERWORLD);
        }
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                orbitedBody,
                STAR_DISTANCE,
                a);
    }
}