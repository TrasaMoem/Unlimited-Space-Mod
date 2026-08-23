package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * R14.9.3-C — deterministic, coherent star-surface block composition (pure domain, no Minecraft types).
 *
 * <p>Given a star's resolved {@link StarSurfaceBlock} family ({@link StarSurfaceBlockFamily#forStar}), this
 * picks a block for a given world column {@code (x, z)} using smooth value noise. The noise is COHERENT — a
 * low-frequency "region" field creates large plasma regions, a higher-frequency "detail" field blends between
 * neighbours, a "hotspot" field forces the bright members and a "cool" field forces the dark depression —
 * so the surface looks like natural plasma regions with transitions, NOT a random 1x1 checkerboard.
 *
 * <p>Everything is a pure function of {@code (worldSeed, systemIndex, starIndex, x, z, y)}, deterministic
 * across restarts and chunk orders. The Minecraft chunk generator maps the returned registry path to a
 * concrete {@code BlockState}; this class never touches a {@code BlockState}.
 */
public final class StarSurfaceComposer {

    private StarSurfaceComposer() {
    }

    /**
     * Pick the surface-layer block registry path for one column.
     *
     * @param surfaceLayer true for the top exposed layer; false for interior (sub-surface) fill
     */
    public static String surfaceBlock(long worldSeed, int systemIndex, int starIndex,
                                      float x, float z, boolean surfaceLayer,
                                      List<StarSurfaceBlock> family) {
        if (family == null || family.isEmpty()) return "dark_red_plasma";
        long s = Seeds.derive(worldSeed, "us.star.blocks", systemIndex, starIndex);

        float region = vnoise(Seeds.derive(s, "region"), x * 0.020f, z * 0.020f);
        float detail = vnoise(Seeds.derive(s, "detail"), x * 0.070f, z * 0.070f);
        float hotspot = vnoise(Seeds.derive(s, "hotspot"), x * 0.043f, z * 0.043f);
        float cool = vnoise(Seeds.derive(s, "cool"), x * 0.031f, z * 0.031f);

        // Hotspot flare: the most emissive member (white-hot) wins on bright sharp highs ...
        if (hotspot > 0.72f) {
            StarSurfaceBlock brightest = brightest(family);
            return brightest.registryPath();
        }
        // Cool depression: the least emissive member (dark plasma) wins on strong lows.
        if (cool < 0.18f) {
            return darkest(family).registryPath();
        }

        List<StarSurfaceBlock> candidates = surfaceLayer ? family : interior(family);
        // A smooth scalar over [0,1) mapped through the weighted CDF -> coherent regions + transitions.
        float sel = clamp01(0.45f * region + 0.55f * detail);
        return weightedPick(candidates, sel).registryPath();
    }

    /**
     * R14.9.3-C: the TOP exposed layer of a star is ALWAYS one of the two LIGHTEST plasma blocks
     * (magenta / scarlet), in a coherent ~50/50 mini-biome split (never a checkerboard). The exact
     * temperature tint of the star still drives which other blocks sit just below, but the surface
     * itself stays the two brightest members by construction.
     */
    public static String topSurfaceBlock(long worldSeed, int systemIndex, int starIndex,
                                         float x, float z, List<StarSurfaceBlock> family) {
        String magenta = StarSurfaceBlockFamily.SURFACE_TIER_PATHS.get(0);
        String scarlet = StarSurfaceBlockFamily.SURFACE_TIER_PATHS.get(1);
        if (family == null || family.isEmpty()) return magenta;
        // A resolved family always contains both, but never fabricate a member the domain lacks.
        boolean hasMagenta = hasPath(family, magenta);
        boolean hasScarlet = hasPath(family, scarlet);
        if (!hasMagenta && !hasScarlet) {
            return family.get(0).registryPath();
        }
        long s = Seeds.derive(worldSeed, "us.star.blocks", systemIndex, starIndex);
        // Two coherent, equal-weight bands -> ~50/50 coverage with natural regions (no checkerboard).
        float sel = clamp01(0.5f * vnoise(Seeds.derive(s, "surfaceA"), x * 0.030f, z * 0.030f)
                + 0.5f * vnoise(Seeds.derive(s, "surfaceB"), x * 0.080f, z * 0.080f));
        if (sel < 0.5f) return hasMagenta ? magenta : scarlet;
        return hasScarlet ? scarlet : magenta;
    }

    /**
     * R14.9.3-C: the sub-surface block for the given depth fraction {@code [0,1]} (0 = just below the
     * surface, 1 = at bedrock). Deeper → darker, harder plasma: the brightest remaining member sits
     * right under the surface and {@code dark_red_plasma} sits at the very bottom.
     */
    public static String subsurfaceBlockByDepth(long worldSeed, int systemIndex, int starIndex,
                                               float x, float z, float depthFraction,
                                               List<StarSurfaceBlock> family) {
        if (family == null || family.isEmpty()) {
            return StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.get(
                    StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.size() - 1);
        }
        // Vary the block slightly with position for natural depth bands, but the depth fraction dominates.
        long s = Seeds.derive(worldSeed, "us.star.blocks", systemIndex, starIndex);
        float jitter = (vnoise(Seeds.derive(s, "depth"), x * 0.020f, z * 0.020f) - 0.5f)
                / (StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.size() - 1f);
        float d = clamp01(depthFraction * 0.90f + 0.05f + jitter);
        int idx = Math.min(StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.size() - 1,
                (int) (d * StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.size()));
        return StarSurfaceBlockFamily.SUBSURFACE_TIER_PATHS.get(idx);
    }

    /** The most emissive candidate (surface hotspot member). */
    private static StarSurfaceBlock brightest(List<StarSurfaceBlock> family) {
        StarSurfaceBlock best = family.get(0);
        for (StarSurfaceBlock b : family) {
            if (b.emissive() > best.emissive()) best = b;
        }
        return best;
    }

    /** The least emissive candidate (cool dark member). */
    private static StarSurfaceBlock darkest(List<StarSurfaceBlock> family) {
        StarSurfaceBlock best = family.get(0);
        for (StarSurfaceBlock b : family) {
            if (b.emissive() < best.emissive()) best = b;
        }
        return best;
    }

    /** Interior (sub-surface) members: the cool, dense, non-flare blocks only. */
    private static List<StarSurfaceBlock> interior(List<StarSurfaceBlock> family) {
        List<StarSurfaceBlock> out = new ArrayList<>(family.size());
        for (StarSurfaceBlock b : family) {
            if (!b.isSurfaceLike()) out.add(b);
        }
        if (out.isEmpty()) out.addAll(family);
        return out;
    }

    private static boolean hasPath(List<StarSurfaceBlock> family, String path) {
        for (StarSurfaceBlock b : family) {
            if (b.registryPath().equals(path)) return true;
        }
        return false;
    }

    /** Map a smooth scalar through the normalised weighted CDF and return the chosen block. */
    private static StarSurfaceBlock weightedPick(List<StarSurfaceBlock> blocks, float sel) {
        double total = 0.0;
        for (StarSurfaceBlock b : blocks) total += b.weight();
        if (total <= 0.0) return blocks.get(0);
        double target = sel * total;
        double acc = 0.0;
        for (StarSurfaceBlock b : blocks) {
            acc += b.weight();
            if (target <= acc || acc >= total - 1e-12) return b;
        }
        return blocks.get(blocks.size() - 1);
    }

    // ----------------------------------------------------------------- deterministic value noise

    private static float vnoise(long seed, float x, float z) {
        int x0 = floor(x);
        int z0 = floor(z);
        float tx = x - x0;
        float tz = z - z0;
        float u = smooth(tx);
        float v = smooth(tz);
        float a = hash01(seed, x0, z0);
        float b = hash01(seed, x0 + 1, z0);
        float c = hash01(seed, x0, z0 + 1);
        float d = hash01(seed, x0 + 1, z0 + 1);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    private static float hash01(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xD1B54A32D192ED03L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 31));
        return (h & 0xFFFFFFL) / (float) 0x1000000L;
    }

    private static float smooth(float t) { return t * t * (3.0f - 2.0f * t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int floor(float v) { int i = (int) v; return v < i ? i - 1 : i; }
}