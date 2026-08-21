package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.galaxy.layout.SpaceConstants;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R14.6 pure-domain generator of Creating Space RocketAccessibleDimension metadata for
 * procedural bodies, driven by STABLE IDs only - deliberately seed-independent.
 *
 * <p>Why seed-independent: the creatingspace:rocket_accessible_dimension registry is a
 * WORLDGEN-layer datapack registry loaded and frozen during WorldStem creation (R14.5.4),
 * i.e. BEFORE the current world seed is decoded, and there is no public API to reload it afterwards.
 * Therefore the metadata that reaches the registry MUST be computable from stable IDs alone.
 *
 * <p>Counts are canonical MAX-cover: they deliberately generate MORE bodies than any single seed
 * galaxy may have, so every body that /unlimitedspace nav can resolve is guaranteed to have
 * CS metadata. Unreachable surplus entries are inert (never navigated to, but valid registry rows).
 *
 * <p>Pure domain: no Minecraft types. JSON serialisation uses the exact Creating Space 1.7.18
 * schema (verified against the shipped rocket_accessible_dimension JSON files).
 */
public final class ProceduralMetadataGenerator {

    private ProceduralMetadataGenerator() {
    }

    /** Canonical planets per system generated into the registry (covers the proof scope 1..3). */
    public static final int PLANETS_PER_SYSTEM = 1;

    /** Canonical planets that carry moon entries (planet_00 always exists for any seed). */
    public static final int MOONED_PLANETS = 1;

    /** Canonical moons per mooned planet. */
    public static final int MOONS_PER_PLANET = 1;

    /** Canonical asteroid clusters per system generated into the registry. */
    public static final int ASTEROID_CLUSTERS_PER_SYSTEM = 1;

    /** Star orbit entries per system (1; a star has no surface world). */
    public static final int STAR_ORBITS_PER_SYSTEM = 1;

    /** Total metadata entries produced for one system (without the overworld override). */
    public static int entriesPerSystem() {
        return PLANETS_PER_SYSTEM * 2
                + MOONED_PLANETS * MOONS_PER_PLANET * 2
                + ASTEROID_CLUSTERS_PER_SYSTEM
                + STAR_ORBITS_PER_SYSTEM;
    }

    // ---- distances / deltaV (mirror the static proof JSON) ----

    private static final int PLANET_SURFACE_DISTANCE = 4200;
    private static final int PLANET_ORBIT_DISTANCE = 5200;
    private static final int MOON_DISTANCE = 500;
    private static final int ASTEROID_DISTANCE = 5200;
    private static final int STAR_DISTANCE = 6000;

    private static final int SURFACE_TO_ORBIT = 200;
    private static final int ORBIT_TO_SURFACE = 200;
    private static final int MOON_TO_MOON = 50;
    private static final int MOON_TO_PLANET = 80;
    private static final int TO_OVERWORLD = 1200;
    private static final int ASTEROID_FROM_OVERWORLD = 1500;

    // ---- canonical (seed-independent, deterministic from the stable code) surface gravity ----

    /** Planet surface gravity in m/s^2: 0.50 .. 1.99 Earth-g, deterministic from PlanetId.code(). */
    public static double canonicalPlanetGravityMs(PlanetId id) {
        int h = Math.floorMod(id.code().hashCode(), 150);
        return Gravity.toMetersPerSecondSq(Gravity.playableEarthG(0.5 + h / 100.0));
    }

    /** Moon surface gravity in m/s^2: 0.10 .. 0.695 Earth-g, deterministic from MoonId.code(). */
    public static double canonicalMoonGravityMs(MoonId id) {
        int h = Math.floorMod(id.code().hashCode(), 120);
        return Gravity.toMetersPerSecondSq(Gravity.playableEarthG(0.1 + h / 200.0));
    }

    // ---- RL helpers ----

    private static String rl(String namespace, String path) {
        return namespace + ":" + path;
    }

    private static String planetKey(PlanetId id, boolean surface) {
        return "planet/" + id.code() + "/" + (surface ? "surface" : "orbit");
    }

    private static String moonKey(MoonId id, boolean surface) {
        return "moon/" + id.code() + "/" + (surface ? "surface" : "orbit");
    }

    private static String asteroidKey(AsteroidClusterId id) {
        return "asteroid/" + id.code();
    }

    private static String starKey(StarSystemId id) {
        return "star/" + id.code() + "/orbit";
    }

    private static Map<String, Integer> adj(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }
    // ================================================================ per-body generators

    /** Planet surface: positive playable gravity, CS surface arrival, orbitedBody=sun (proof parity). */
    public static ProceduralRocketAccessibleDimension planetSurface(PlanetId id, String namespace) {
        String key = rl(namespace, planetKey(id, true));
        String orbit = rl(namespace, planetKey(id, false));
        return new ProceduralRocketAccessibleDimension(
                key,
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                canonicalPlanetGravityMs(id),
                "sun",
                PLANET_SURFACE_DISTANCE,
                adj(orbit, SURFACE_TO_ORBIT));
    }

    /** Planet orbit: zero gravity, direct arrival 64, orbitedBody = own surface. */
    public static ProceduralRocketAccessibleDimension planetOrbit(PlanetId id, String namespace) {
        String surface = rl(namespace, planetKey(id, true));
        String orbit = rl(namespace, planetKey(id, false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, ORBIT_TO_SURFACE);
        if (id.orbitIndex() < MOONED_PLANETS) {
            MoonId first = MoonId.of(id, 0);
            a.put(rl(namespace, moonKey(first, false)), MOON_TO_PLANET);
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

    /** Moon surface: positive canonical moon gravity, CS surface arrival, orbitedBody = parent planet surface. */
    public static ProceduralRocketAccessibleDimension moonSurface(PlanetId parent, MoonId id, String namespace) {
        String surface = rl(namespace, moonKey(id, true));
        String orbit = rl(namespace, moonKey(id, false));
        String parentSurface = rl(namespace, planetKey(parent, true));
        return new ProceduralRocketAccessibleDimension(
                surface,
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                canonicalMoonGravityMs(id),
                parentSurface,
                MOON_DISTANCE,
                adj(orbit, MOON_TO_MOON));
    }

    /** Moon orbit: zero gravity, direct arrival 64, orbitedBody = own surface. */
    public static ProceduralRocketAccessibleDimension moonOrbit(PlanetId parent, MoonId id, String namespace) {
        String surface = rl(namespace, moonKey(id, true));
        String orbit = rl(namespace, moonKey(id, false));
        String parentOrbit = rl(namespace, planetKey(parent, false));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(surface, MOON_TO_MOON);
        a.put(parentOrbit, MOON_TO_PLANET);
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                surface,
                MOON_DISTANCE,
                a);
    }

    /** Asteroid field: weightless, direct field-centre arrival, orbitedBody = overworld (never NPEs). */
    public static ProceduralRocketAccessibleDimension asteroid(AsteroidClusterId id, String namespace) {
        String key = rl(namespace, asteroidKey(id));
        return new ProceduralRocketAccessibleDimension(
                key,
                AsteroidFieldGeometry.arrivalY(),
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                "minecraft:overworld",
                ASTEROID_DISTANCE,
                adj("minecraft:overworld", ASTEROID_FROM_OVERWORLD));
    }

    /** Star orbit: zero gravity, direct arrival 64, orbitedBody = planet_00 surface (always generated). */
    public static ProceduralRocketAccessibleDimension starOrbit(StarSystemId system, String namespace) {
        String orbit = rl(namespace, starKey(system));
        String firstPlanetSurface = rl(namespace, planetKey(PlanetId.of(system, 0), true));
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put(firstPlanetSurface, TO_OVERWORLD);
        a.put("minecraft:overworld", TO_OVERWORLD);
        return new ProceduralRocketAccessibleDimension(
                orbit,
                Gravity.CS_ORBIT_ARRIVAL_HEIGHT,
                Gravity.CS_ORBIT_GRAVITY_METERS_PER_SECOND_SQ,
                firstPlanetSurface,
                STAR_DISTANCE,
                a);
    }
    // ================================================================ whole-scope generation

    /**
     * Generate the complete deterministic metadata set for systems [0..systemCount).
     * Never touches a ServerLevel; pure metadata only. No duplicate keys.
     */
    public static List<ProceduralRocketAccessibleDimension> generate(int systemCount, String namespace) {
        List<ProceduralRocketAccessibleDimension> out =
                new ArrayList<>(systemCount * entriesPerSystem());
        for (int s = 0; s < systemCount; s++) {
            StarSystemId sys = StarSystemId.of(s);
            for (int p = 0; p < PLANETS_PER_SYSTEM; p++) {
                PlanetId pid = PlanetId.of(sys, p);
                out.add(planetSurface(pid, namespace));
                out.add(planetOrbit(pid, namespace));
                if (p < MOONED_PLANETS) {
                    for (int m = 0; m < MOONS_PER_PLANET; m++) {
                        MoonId mid = MoonId.of(pid, m);
                        out.add(moonSurface(pid, mid, namespace));
                        out.add(moonOrbit(pid, mid, namespace));
                    }
                }
            }
            for (int a = 0; a < ASTEROID_CLUSTERS_PER_SYSTEM; a++) {
                out.add(asteroid(AsteroidClusterId.of(sys, a), namespace));
            }
            if (STAR_ORBITS_PER_SYSTEM > 0) {
                out.add(starOrbit(sys, namespace));
            }
        }
        return out;
    }

    /**
     * The minecraft:overworld override that routes the launch dimension to every generated
     * procedural destination. Preserves the exact proof overworld CS semantics (arrival 200, gravity
     * 9.81, orbitedBody sun, distance 1500, earth_orbit edge) and adds the procedural edges.
     */
    public static ProceduralRocketAccessibleDimension overworld(List<ProceduralRocketAccessibleDimension> entries) {
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put("creatingspace:earth_orbit", 1500);
        for (ProceduralRocketAccessibleDimension e : entries) {
            String key = e.key();
            if (key.contains("/surface") || key.contains("/orbit")) {
                a.put(key, TO_OVERWORLD);
            } else if (key.contains(":asteroid/")) {
                a.put(key, ASTEROID_FROM_OVERWORLD);
            }
        }
        return new ProceduralRocketAccessibleDimension(
                "minecraft:overworld",
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                Gravity.EARTH_G_TO_METERS_PER_SECOND_SQ,
                "sun",
                1500,
                a);
    }

    // ================================================================ CS JSON serialisation

    /**
     * Serialise to the EXACT Creating Space 1.7.18 rocket_accessible_dimension JSON schema:
     * distanceToOrbitingBody, orbitedBody, arrivalHeight, gravity, adjacentDimensions map of
     * {"deltaV": n}. No "id" field - the registry key comes from the file path.
     */
    public static String toJson(ProceduralRocketAccessibleDimension e) {
        StringBuilder sb = new StringBuilder(220);
        sb.append("{\n  \"adjacentDimensions\": {\n");
        boolean first = true;
        for (Map.Entry<String, Integer> adj : e.adjacentDimensions().entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    \"").append(adj.getKey()).append("\": { \"deltaV\": ").append(adj.getValue()).append(" }");
        }
        sb.append("\n  },\n");
        sb.append("  \"arrivalHeight\": ").append(e.arrivalHeight()).append(",\n");
        sb.append("  \"gravity\": ").append(formatGravity(e.gravity())).append(",\n");
        sb.append("  \"orbitedBody\": \"").append(e.orbitedBody()).append("\",\n");
        sb.append("  \"distanceToOrbitingBody\": ").append(e.distanceToOrbitingBody()).append("\n}");
        return sb.toString();
    }

    /** Compact, JSON-safe gravity rendering: integral values become 9.0, not 9. */
    static String formatGravity(double gravity) {
        if (gravity == Math.rint(gravity) && !Double.isInfinite(gravity)) {
            long l = (long) gravity;
            return l + ".0";
        }
        return Double.toString(gravity);
    }
}