package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic surface tint of a planet (R12, visual layer).
 *
 * <p>Maps the semantic {@link PlanetSurface} category + climate into a pure ARGB
 * colour that a client renderer uses for the procedural planet body. Mirrors the
 * parallel moon helper {@code MoonGenerationProfile#surfaceColorArgb}. No Minecraft
 * types; {@code (PlanetId seed, properties)} always yields the same colour.
 */
public final class PlanetSurfaceColor {

    private static final long SURFACE_JITTER_SLOT = 98001L;

    private PlanetSurfaceColor() {
    }

    public static int surfaceColorArgb(PlanetProperties p) {
        long seed = p.seed().value();
        double jitter = (Seeds.fraction(seed, SURFACE_JITTER_SLOT) - 0.5) * 26.0;

        int base = switch (p.surface()) {
            case SOLID_ROCKY -> rockyTint(p);
            case SOLID_ICE -> 0xFFDCE9F6;
            case SOLID_DESERT -> 0xFFD9B983;
            case SOLID_VOLCANIC -> 0xFF4A2B21;
            case OCEANIC -> 0xFF2E5FA3;
            case GASEOUS -> gasGiantTint(p);
        };

        return tint(base, (int) Math.round(jitter));
    }

    /** Rocky base: cold → bluish-grey, temperate → brownish-grey, hot → rust/red. */
    private static int rockyTint(PlanetProperties p) {
        double t = temp01(p);
        if (t < 0.35) return 0xFF8B8FA0;   // cold twilight grey
        if (t < 0.65) return 0xFF9A8F80;   // temperate clay/tan
        return 0xFF8C5A44;                 // hot rust
    }

    /** Gas giants: cold → pale cream, warm → tan/amber, hot → orange-brown. */
    private static int gasGiantTint(PlanetProperties p) {
        double t = temp01(p);
        if (t < 0.3) return 0xFFD9CFBC;
        if (t < 0.6) return 0xFFC9A17A;
        if (t < 0.8) return 0xFFB0815A;
        return 0xFF95452E;
    }

    private static double temp01(PlanetProperties p) {
        double v = (p.temperature() - 120.0) / 780.0;
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int tint(int argb, int noise) {
        int r = clamp8(((argb >> 16) & 0xFF) + noise);
        int g = clamp8(((argb >> 8) & 0xFF) + noise / 2);
        int b = clamp8((argb & 0xFF) + noise / 3);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}