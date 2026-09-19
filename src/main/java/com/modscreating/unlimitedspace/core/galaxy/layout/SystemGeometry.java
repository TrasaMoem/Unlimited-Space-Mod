package com.modscreating.unlimitedspace.core.galaxy.layout;

/**
 * R42: deterministic INTRA-SYSTEM geometry in light-years.
 *
 * <p>Sets a canonical position for every body of a star system (star, planets, moons,
 * asteroid clusters and the real Sol bodies) so trips can be priced BODY-TO-BODY:
 * planet -> planet, planet -> moon, body -> its orbit, star -> planets, etc.
 * Together with the system-to-system distance of {@link GalaxyMapModel} this gives
 * the full trip length used by the fuel planner (cruise dV) and by the UI read-outs.</p>
 *
 * <p>Scale: the system diameter is deliberately SMALL (~6 ly across, planets in the
 * 0.2..1 ly band) compared to the inter-system distances (hundreds..thousands of ly on
 * the main map). This keeps intra-system hops cheap so the future engine tier
 * progression has a meaningful low end.</p>
 *
 * <p>Pure domain: no Minecraft types, fully deterministic from
 * {@code (worldSeed, systemIndex, body key)}. An orbit shares the position of its body
 * (orbital insertion happens AT the body), so surface <-> orbit stays a short hop.</p>
 */
public final class SystemGeometry {

    private SystemGeometry() {}

    /** Radius of a star system in light-years (diameter = 2 * this). Small by design. */
    public static final double SYSTEM_RADIUS_LY = 3.0;

    private static final double PLANET_R_MIN = 0.08; // fraction of SYSTEM_RADIUS_LY
    private static final double PLANET_R_STEP = 0.09;

    /**
     * Intra-system position offset [x, z] of the body identified by {@code dimPath}
     * (a CS dimension key path like {@code planet/system_0042_planet_01/surface},
     * {@code moon/system_0042_planet_01_moon_00/orbit}, {@code star/system_0042/surface},
     * {@code asteroid/system_0042_asteroid_00}), relative to the system centre, in ly.
     * Sol bodies ({@code overworld}, {@code the_moon}, {@code mars}, {@code venus},
     * {@code earth_orbit}) use a fixed canonical layout.
     */
    public static double[] bodyOffsetLy(long worldSeed, int systemIndex, String dimPath) {
        String p = dimPath == null ? "" : dimPath;
        // strip the surface/orbit suffix - the orbit of a body sits AT the body
        String body = p;
        if (body.endsWith("/surface") || body.endsWith("/orbit")) {
            body = body.substring(0, body.lastIndexOf('/'));
        }
        if (systemIndex == GalaxyMapModel.SOL_SYSTEM_INDEX || body.indexOf("system_") < 0) {
            return solOffset(body);
        }
        long s = mix(worldSeed ^ (systemIndex * 0x9E3779B97F4A7C15L));
        double jitter = systemJitterDeg(s);
        if (body.contains("_moon_")) {
            int moonIdx = lastIdx(body, "_moon_");
            int planetIdx = lastIdx(body, "_planet_");
            double[] parent = planetPos(jitter, planetIdx);
            return offset(parent, 0.01 + 0.012 * moonIdx, jitter + moonIdx * 151.0);
        }
        if (body.contains("_planet_")) {
            return planetPos(jitter, lastIdx(body, "_planet_"));
        }
        if (body.contains("_asteroid_")) {
            int idx = lastIdx(body, "_asteroid_");
            return polar(SYSTEM_RADIUS_LY * (0.30 + 0.06 * (idx % 9)), jitter + idx * 121.0);
        }
        if (body.contains("_star_")) {
            int idx = lastIdx(body, "_star_");
            return polar(SYSTEM_RADIUS_LY * 0.25, jitter + idx * 140.0);
        }
        // primary star (body == "system_XXXX") or unknown: the system centre
        return new double[]{0, 0};
    }

    // ---- Sol (system index -2): fixed canonical layout, matched to real CS distances ----

    private static double[] solOffset(String path) {
        // Earth with the Moon nearby; Venus inside, Mars outside Earth's orbit.
        return switch (path) {
            case "overworld", "earth_orbit" -> polar(1.00, 200.0);
            case "the_moon" -> offset(polar(1.00, 200.0), 0.03, 35.0);
            case "venus" -> polar(0.65, 320.0);
            case "mars" -> polar(1.55, 40.0);
            default -> new double[]{0, 0}; // the sun / unknown: system centre
        };
    }

    /** Deterministic orbit position of the Nth planet: radius grows with the orbit slot. */
    private static double[] planetPos(double jitterDeg, int planetIdx) {
        double r = SYSTEM_RADIUS_LY * (PLANET_R_MIN + PLANET_R_STEP * (planetIdx % 9));
        return polar(r, jitterDeg + planetIdx * 137.5);
    }

    // ---- small deterministic helpers ----

    private static double systemJitterDeg(long s) {
        return Math.floorMod(s, 3600L) / 10.0;
    }

    private static double[] polar(double r, double deg) {
        double a = Math.toRadians(deg);
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }

    private static double[] offset(double[] base, double r, double deg) {
        double[] d = polar(r, deg);
        return new double[]{base[0] + d[0], base[1] + d[1]};
    }

    /** int after the LAST occurrence of {@code marker} (self-terminating digit run). */
    private static int lastIdx(String s, String marker) {
        int i = s.lastIndexOf(marker);
        if (i < 0) return 0;
        return digitsAt(s, i + marker.length());
    }

    private static int digitsAt(String s, int from) {
        int n = 0;
        boolean any = false;
        while (from < s.length() && Character.isDigit(s.charAt(from))) {
            n = n * 10 + (s.charAt(from) - '0');
            from++;
            any = true;
        }
        return any ? n : 0;
    }

    /** SplitMix64-style finalizer - cheap deterministic seed mixing. */
    private static long mix(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}