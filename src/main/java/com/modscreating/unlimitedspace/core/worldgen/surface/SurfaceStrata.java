package com.modscreating.unlimitedspace.core.worldgen.surface;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * PHASE 8: SURFACE STRATA depths of one planet.
 *
 * <p>The vertical material layering (surface / subsurface / deep) is no longer hardcoded:
 * it depends on the planet's surface category and climate —
 * <pre>
 * HOT DRY    — thin dust/sand over sediment over rock
 * TEMPERATE  — soil/rock over sediment over rock
 * GLACIAL    — thicker frost/snow cover over froststone
 * VOLCANIC   — thin ash/cinder crust over basalt
 * </pre>
 * Target bands: surface 1–4 blocks, subsurface 4–12, deep everything below. Adapted per
 * profile, never quantized per biome. Pure domain, deterministic.
 */
public record SurfaceStrata(int surfaceDepth, int subsurfaceDepth) {

    /** Canonical strata of a planet (pure function of seed + physical profile). */
    public static SurfaceStrata of(long planetSeed, PlanetPhysicalProfile p) {
        if (p == null) return new SurfaceStrata(2, 8);
        long s = Seeds.derive(planetSeed, "us.strata");
        double f1 = Seeds.fraction(s, 1L);
        double f2 = Seeds.fraction(s, 2L);
        boolean cold = p.isColdWorld();
        boolean hot = p.isHotWorld();
        boolean volcanic = p.volcanicActivity() > 0.55;
        // Surface cover: 1 block on fresh volcanic crust, up to 4 on frost / soil-rich worlds.
        double coverBase = cold ? 2.2 : hot ? 0.6 : 1.2;
        if (volcanic) coverBase = Math.min(coverBase, 1.0);
        int surface = 1 + (int) Math.round(Math.min(3.0, coverBase * (0.6 + 0.8 * f1)));
        // Subsurface regolith: 4–12 blocks; eroded old worlds carry more sediment.
        int subsurface = 4 + (int) Math.round(8.0 * Math.min(1.0,
                0.35 * p.relativeAge() + 0.35 * p.erosion() + 0.30 * f2));
        return new SurfaceStrata(
                Math.max(1, Math.min(4, surface)),
                Math.max(4, Math.min(12, subsurface)));
    }

    /** Total depth of the weathered cover (surface + subsurface). */
    public int coverDepth() {
        return surfaceDepth + subsurfaceDepth;
    }

    /** Block role for a depth below the surface (0 = top surface block). */
    public Role roleAt(int depthBelowSurface) {
        if (depthBelowSurface < surfaceDepth) return Role.SURFACE;
        if (depthBelowSurface < coverDepth()) return Role.SUBSURFACE;
        return Role.DEEP;
    }

    /** Stratum role. */
    public enum Role { SURFACE, SUBSURFACE, DEEP }
}
