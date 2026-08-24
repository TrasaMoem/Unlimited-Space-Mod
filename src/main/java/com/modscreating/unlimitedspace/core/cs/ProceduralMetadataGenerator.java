package com.modscreating.unlimitedspace.core.cs;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R14.6.2 pure-domain generator of Creating Space RocketAccessibleDimension metadata for
 * procedural bodies, driven by the ACTUAL SEED-DERIVED domain state (Galaxy/Planet/Moon/
 * AsteroidCluster/StarSystem).
 *
 * <p>THE SINGLE SOURCE OF TRUTH for every gravity value is the procedural domain:
 * {@code Planet.properties().gravity()} and {@code Moon.properties().gravity()} (both in
 * Earth-g, converted to m/s² via {@link Gravity}). There is deliberately NO second,
 * seed-independent gravity formula (the old {@code canonicalPlanetGravityMs(id)} hash model
 * was removed in R14.6.2). The same {@code WorldSeed + system index} always yields the same
 * metadata, and a different world seed may yield a different gravity for the same body
 * (seed-awareness).
 *
 * <p>Coverage is FULL per system: every canonical celestial object that /unlimitedspace nav
 * can resolve (all planets, all moons, all asteroid clusters, the star orbit) receives CS
 * metadata. There is no arbitrary "planet_00 only" coverage limit. Object kinds are read from
 * the canonical object model, never assumed from a numeric index.
 *
 * <p>Per-body construction delegates to {@link ProceduralRocketAccessibleDimensionFactory}
 * so there is exactly one metadata builder for the whole pipeline.
 *
 * <p>Pure domain: no Minecraft types. JSON serialisation uses the exact Creating Space 1.7.18
 * schema (verified against the shipped rocket_accessible_dimension JSON files).
 */
public final class ProceduralMetadataGenerator {

    private ProceduralMetadataGenerator() {
    }

    // ---- distances / deltaV (mirror the static proof JSON) ----

    private static final int EARTH_ORBIT_DISTANCE = 1500;
    private static final int TO_OVERWORLD = 1200;
    private static final int ASTEROID_FROM_OVERWORLD = 1500;

    // ================================================================ whole-scope generation

    /**
     * Generate the complete seed-aware metadata for systems {@code [0..systemCount)}.
     * Every canonical celestial object of every system in scope is covered. Never touches a
     * ServerLevel; pure metadata only. No duplicate keys.
     */
    public static List<ProceduralRocketAccessibleDimension> generate(long worldSeed, int systemCount, String namespace) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        List<ProceduralRocketAccessibleDimension> out =
                new ArrayList<>(systemCount * 8);
        for (int s = 0; s < systemCount; s++) {
            out.addAll(generateForSystem(galaxy, s, namespace));
        }
        return out;
    }

    /** Generate the complete seed-aware metadata for ONE system (used for on-demand expansion). */
    public static List<ProceduralRocketAccessibleDimension> generateForSystem(long worldSeed, int systemIndex, String namespace) {
        return generateForSystem(Galaxy.from(worldSeed), systemIndex, namespace);
    }

    /**
     * Generate the complete seed-aware metadata for ONE system from an existing {@link Galaxy}.
     * The canonical object list of the system is authoritative: all planets (surface+orbit),
     * all of each planet's moons (surface+orbit), all asteroid clusters, and EVERY star
     * (surface + orbit) — full multi-star coverage, not just the primary.
     */
    public static List<ProceduralRocketAccessibleDimension> generateForSystem(Galaxy galaxy, int systemIndex, String namespace) {
        StarSystem system = galaxy.getStarSystem(StarSystemId.of(systemIndex));
        List<ProceduralRocketAccessibleDimension> out = new ArrayList<>(32);
        int planets = system.planetCount();
        for (int p = 0; p < planets; p++) {
            var planet = system.getPlanet(p);
            out.add(ProceduralRocketAccessibleDimensionFactory.planetSurface(planet, namespace));
            out.add(ProceduralRocketAccessibleDimensionFactory.planetOrbit(planet, namespace));
            for (var moon : planet.moons()) {
                out.add(ProceduralRocketAccessibleDimensionFactory.moonSurface(planet, moon, namespace));
                out.add(ProceduralRocketAccessibleDimensionFactory.moonOrbit(planet, moon, namespace));
            }
        }
        int clusters = system.asteroidClusterCount();
        for (int a = 0; a < clusters; a++) {
            out.add(ProceduralRocketAccessibleDimensionFactory.asteroid(system.asteroid(a), namespace));
        }
        // R14.9.3-B FIX: a system may hold multiple stars (binary / trinary). CS metadata must be emitted for
        // EVERY canonical star — primary (index 0, key star/system_XXXX) AND each companion (index >= 1, key
        // star/system_XXXX_star_YY) — not just the primary. Previously only the primary star's surface+orbit
        // were generated, so navigating to a companion (e.g. system_0958_star_01/surface) failed with
        // "Destination exists ... but is not currently registered as a playable Minecraft world / CS runtime
        // metadata missing". Each star uses the same seed-aware per-star pipeline (own gravity, arrival,
        // orbitedBody and adjacency), so this scales to arbitrary indices with NO hard-coded special case.
        for (var star : system.stars()) {
            out.add(ProceduralRocketAccessibleDimensionFactory.starSurface(system, star, namespace));
            out.add(ProceduralRocketAccessibleDimensionFactory.starOrbit(system, star, namespace));
        }
        return out;
    }

    /**
     * The minecraft:overworld override that routes the launch dimension to every generated
     * procedural destination. Preserves the exact proof overworld CS semantics (arrival 200, gravity
     * 9.81, orbitedBody sun, distance 1500, earth_orbit edge) and adds the procedural edges.
     */
    public static ProceduralRocketAccessibleDimension overworld(List<ProceduralRocketAccessibleDimension> entries) {
        return overworld(entries, Long.MIN_VALUE);
    }

    /**
     * R15.4: seed-aware overload. Every procedural orbit/asteroid edge from the overworld now
     * carries a DISTANCE SURCHARGE proportional to the system's map distance from the Sol anchor,
     * so flying to a far system costs visibly more fuel than a near one (Tsiolkovsky turns the
     * higher route cost into higher required fuel). Deterministic: same seed -> same weights.
     */
    public static ProceduralRocketAccessibleDimension overworld(List<ProceduralRocketAccessibleDimension> entries, long worldSeed) {
        GalaxyMapModel map = null;
        double radius = 0;
        if (worldSeed != Long.MIN_VALUE) {
            try {
                map = GalaxyMapModel.from(worldSeed);
                radius = map.layout().galaxyRadiusGu();
            } catch (Throwable ignored) {
                map = null;
            }
        }
        GalaxyMapModel mapModel = map;
        double galaxyRadius = radius;
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put("creatingspace:earth_orbit", EARTH_ORBIT_DISTANCE);
        for (ProceduralRocketAccessibleDimension e : entries) {
            String key = e.key();
            int surcharge = 0;
            if (mapModel != null) {
                int si = GalaxyMapModel.systemIndexFromKey(key);
                if (si >= 0) {
                    var pos = mapModel.systemByIndex(si);
                    if (pos != null) {
                        surcharge = GalaxyMapModel.solSurcharge(pos.x(), pos.z(), galaxyRadius);
                    }
                }
            }
            // R15.3: NO direct overworld->surface edges. A body's surface is reachable only
            // through its own orbit (+ descent deltaV), which GUARANTEES that flying to a
            // planet/star/moon SURFACE always costs more than flying to its ORBIT.
            if (key.contains("/orbit")) {
                // R15.3: deterministic per-body variation + R15.4 distance surcharge.
                a.put(key, TO_OVERWORLD + (Math.abs(key.hashCode()) % 13) * 60 + surcharge);
            } else if (key.contains(":asteroid/")) {
                a.put(key, ASTEROID_FROM_OVERWORLD + (Math.abs(key.hashCode()) % 6) * 100 + surcharge);
            }
        }
        return new ProceduralRocketAccessibleDimension(
                "minecraft:overworld",
                Gravity.CS_SURFACE_ARRIVAL_HEIGHT,
                Gravity.EARTH_G_TO_METERS_PER_SECOND_SQ,
                "sun",
                EARTH_ORBIT_DISTANCE,
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