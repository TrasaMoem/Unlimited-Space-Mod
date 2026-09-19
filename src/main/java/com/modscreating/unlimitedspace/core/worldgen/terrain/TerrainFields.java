package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic shaping-field primitives (R17 terrain-diversity stage).
 *
 * <p>All functions are pure, allocation-free and cheap (a handful of {@link Seeds} hash mixes
 * per sample), so they can be evaluated per column inside the chunk-generation loop. Every
 * field is continuous in world space — no cell boundaries, no vertical walls.
 */
final class TerrainFields {

    private TerrainFields() {}

    /** Ridged noise in [0,1]: ridges are the smooth contour lines of the underlying field. */
    static double ridged(long seed, int x, int z, double frequency, int octave) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        double n = lerp(a, b, tz) * 2.0 - 1.0;
        return 1.0 - Math.abs(n);
    }

    /** Domain-warped sample: shifts the sample point by a low-frequency noise for organic flow. */
    static double warped(long seed, int x, int z, double frequency, int octave, double warp) {
        double wx = x + warp * 96.0 * ridged(seed, x, z, frequency * 0.5, octave + 7);
        double wz = z + warp * 96.0 * ridged(seed + 0x51L, x, z, frequency * 0.5, octave + 11);
        // R20 fix: the warp offset is in BLOCKS, but the sample must still be scaled by the
        // frequency — sampling at the raw block coordinate made this a 1-block white noise
        // (the legacy "potato field"), no matter which frequency the caller asked for.
        double sx = wx * frequency;
        double sz = wz * frequency;
        int x0 = floor(sx), z0 = floor(sz);
        double tx = smoothstep(sx - x0);
        double tz = smoothstep(sz - z0);
        double v00 = corner(seed, x0, z0, octave);
        double v10 = corner(seed, x0 + 1, z0, octave);
        double v01 = corner(seed, x0, z0 + 1, octave);
        double v11 = corner(seed, x0 + 1, z0 + 1, octave);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    /**
     * Deterministic crater deformation at a world column.
     *
     * @param seed     planet-scoped crater field seed
     * @param cellSize crater field cell size (radius always &lt;= cellSize, so a 3x3
     *                 neighbourhood scan covers every influencing crater)
     * @param density  crater field density in [0,1]
     * @return signed deformation in blocks (negative = depression, positive = raised rim)
     */
    static double craterDeform(long seed, int x, int z, int cellSize, double density, double amplitude) {
        if (density <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deform += craterAt(seed, cx + dx, cz + dz, cellSize, density, amplitude, x, z);
            }
        }
        return deform;
    }

    private static double craterAt(long seed, int cx, int cz, int cellSize, double density,
                                   double amplitude, int x, int z) {
        long h = Seeds.derive(seed, "us.terrain.crater", cx, cz);
        if (Seeds.fraction(h, 0) > density * 0.55) return 0.0;   // sparse; never a blanket
        double fx = Seeds.fraction(h, 1);
        double fz = Seeds.fraction(h, 2);
        // R20 crater tiers: MICRO..MEGA. Size is drawn cubically, so most craters are small
        // and a few are huge geological events (radius up to ~0.64 cell = ~410 blocks @640).
        double f3 = Seeds.fraction(h, 3);
        double radius = 55.0 + cellSize * 0.55 * f3 * f3 * f3;
        double depth = amplitude * (0.10 + 0.55 * Seeds.fraction(h, 4)) * (0.5 + 0.5 * f3);
        double centerX = (cx + fx) * cellSize;
        double centerZ = (cz + fz) * cellSize;
        double d = Math.hypot(x - centerX, z - centerZ) / radius;
        if (d >= 1.35) return 0.0;

        if (d < 0.82) {
            double t = 1.0 - (d / 0.82);
            return -depth * t * t * (3.0 - 2.0 * t);
        }
        // R20: continuous rim + ejecta (no discontinuity at the bowl/rim boundary — a hard
        // step here used to read as a single-block wall).
        if (d < 1.0) {
            double u = (d - 0.82) / 0.18;
            return depth * 0.28 * Math.sin(Math.PI * u);
        }
        double e = (d - 1.0) / 0.35;
        return depth * 0.12 * Math.sin(Math.PI * e);
    }

    /** Sparse volcanic cone / caldera deformation (inner depression, positive flanks). */
    static double volcanicDeform(long seed, int x, int z, int cellSize, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        // 3x3: a cone radius may reach 0.6*cellSize, so cones centred in NEIGHBOURING cells can
        // still touch this column. Scanning only 2x2 dropped them at cell borders (a step).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deform += coneAt(seed, cx + dx, cz + dz, cellSize, strength, amplitude, x, z);
            }
        }
        return deform;
    }

    private static double coneAt(long seed, int cx, int cz, int cellSize, double strength,
                                 double amplitude, int x, int z) {
        long h = Seeds.derive(seed, "us.terrain.cone", cx, cz);
        double roll = Seeds.fraction(h, 0);
        double cut = strength * 0.30;
        if (roll >= cut) return 0.0;
        // R20: fade the cone in as the local strength rises past the cell's roll. A hard
        // threshold made a full-height cone POP into existence when a province border ramped
        // the strength — a 30+ block single-column wall.
        double fade = smoothstep01((cut - roll) / (0.40 * strength));
        double fx = Seeds.fraction(h, 1);
        double fz = Seeds.fraction(h, 2);
        // R20: volcanic cones are RARE and LARGE — dominant strato-cones, not many little bumps.
        double f3 = Seeds.fraction(h, 3);
        double radius = cellSize * (0.22 + 0.38 * f3 * f3);
        double height = amplitude * (0.6 + 0.8 * Seeds.fraction(h, 4)) * (0.6 + 0.6 * f3);
        double centerX = (cx + fx) * cellSize;
        double centerZ = (cz + fz) * cellSize;
        double d = Math.hypot(x - centerX, z - centerZ) / radius;
        if (d >= 1.0) return 0.0;

        double cone = height * (1.0 - d) * (1.0 - d);
        if (d < 0.30) {
            double t = 1.0 - d / 0.30;
            cone -= height * 0.45 * t * t;
        }
        return cone * fade;
    }

    /** Wind-formed dune ridges scaled by {@code strength}. */
    static double duneField(long seed, int x, int z, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        double ridge = ridged(seed, x, z, 0.045, 21);
        double shaped = Math.pow(ridge, 3.0);
        return strength * amplitude * 0.35 * shaped;
    }

    /**
     * Deterministic lava-channel carving (R18): a domain-warped ridge-line carves a long,
     * winding channel across the surface. Negative-only, so it never punctures the terrain
     * bounds; strength scales with the planet/volcanic intensity.
     */
    static double lavaChannel(long seed, int x, int z, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        // Domain-warped sample keeps the channel wavy (never gridded / never rectangular).
        double sampled = warped(seed, x, z, 0.008, 24, 1.0);
        double ridgeLine = 1.0 - Math.abs(sampled * 2.0 - 1.0);
        if (ridgeLine < 0.90) return 0.0;
        double channel = Math.pow((ridgeLine - 0.90) / 0.10, 1.6);
        return -strength * amplitude * 0.55 * channel;
    }

    /** Sparse localized spires (crystal provinces): sharp but smooth narrow peaks. */
    static double spireField(long seed, int x, int z, int cellSize, double strength, double amplitude) {
        if (strength <= 0.0) return 0.0;
        int cx = Math.floorDiv(x, cellSize);
        int cz = Math.floorDiv(z, cellSize);
        double deform = 0.0;
        // 3x3 neighbourhood (spire radius may reach beyond its own cell edge — see cones).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Seeds.derive(seed, "us.terrain.spire", cx + dx, cz + dz);
                double roll = Seeds.fraction(h, 0);
                double cut = strength * 0.22;
                if (roll >= cut) continue;
                // R20: same continuous fade-in as cones — no popping at province borders.
                double fade = smoothstep01((cut - roll) / (0.40 * strength));
                double fx = Seeds.fraction(h, 1);
                double fz = Seeds.fraction(h, 2);
                double radius = 10.0 + 22.0 * Seeds.fraction(h, 3);
                double height = amplitude * (0.3 + 0.5 * Seeds.fraction(h, 4));
                double centerX = (cx + dx + fx) * cellSize;
                double centerZ = (cz + dz + fz) * cellSize;
                double d = Math.hypot(x - centerX, z - centerZ) / radius;
                if (d < 1.0) {
                    double t = 1.0 - d;
                    deform += height * Math.pow(t, 1.6) * fade;
                }
            }
        }
        return deform;
    }

    // ------------------------------------------------------------------ small helpers

    private static double corner(long seed, int cx, int cz, int octave) {
        long h = Seeds.derive(seed, "us.terrain.field", cx, cz, octave);
        return Seeds.fraction(h, 0);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Clamped smoothstep: 0 below 0, 1 above 1 (used for continuous feature fade-ins). */
    private static double smoothstep01(double t) {
        double c = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return c * c * (3.0 - 2.0 * c);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
