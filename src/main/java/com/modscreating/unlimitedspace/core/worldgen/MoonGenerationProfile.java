package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.MoonType;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Canonical root of procedural moon world generation (R10).
 *
 * <p>Composes moon-specific sub-profiles, all derived deterministically from the
 * moon's own seed — <em>not</em> from a scaled-down parent planet profile.
 *
 * <pre>
 * MoonSeed ── MoonProperties ── MoonGenerationProfile (this)
 *     ├── terrain   (baseHeight / amplitude / frequency)
 *     ├── water     (coverage-aware sea level)
 *     └── visual    (sky / surface tint by type)
 * </pre>
 *
 * <p>Pure domain: no Minecraft/NeoForge types. Sub-profiles for biome / material /
 * environment are reused from the shared planet infrastructure where inputs come
 * from {@link MoonSeed}/{@link MoonProperties}.
 */
public record MoonGenerationProfile(
        com.modscreating.unlimitedspace.core.planets.MoonId moonId,
        long moonSeed,
        MoonProperties properties,
        double baseHeight,
        double amplitude,
        double frequency,
        double seaLevel,
        boolean hasWater,
        int surfaceColorArgb
) {

    /**
     * Canonical factory: build the complete moon generation profile from a {@link Moon}.
     */
    public static MoonGenerationProfile from(Moon moon) {
        return from(moon.properties());
    }

    /**
     * Canonical factory: build the complete profile from the moon's properties.
     */
    public static MoonGenerationProfile from(MoonProperties p) {
        long s = p.seed().value();
        MoonType type = p.type();

        double baseHeight = 56.0 + 24.0 * Seeds.fraction(s, 101L);
        double amplitude = 6.0 + 18.0 * Seeds.fraction(s, 102L) * (1.0 - 0.4 * p.erosion());
        double frequency = 0.012 + 0.02 * Seeds.fraction(s, 103L);

        boolean hasWater = p.surface() != com.modscreating.unlimitedspace.core.planets.PlanetSurface.GASEOUS
                && p.waterCoverage() > 0.01;
        double seaLevel = hasWater
                ? baseHeight + amplitude * (2.0 * p.waterCoverage() - 1.0)
                : baseHeight;

        int surfaceColor = surfaceColorArgb(type);

        return new MoonGenerationProfile(p.id(), s, p,
                baseHeight, amplitude, frequency, seaLevel, hasWater, surfaceColor);
    }

    /** Deterministic surface tint (ARGB) derived from moon type + seed. */
    public static int surfaceColorArgb(MoonType type) {
        int base = switch (type) {
            case ICE -> 0xFFE8F2FF;
            case METALLIC -> 0xFFB9BFC8;
            case VOLCANIC -> 0xFF4A2B21;
            case DESERT -> 0xFFD9C08C;
            case OCEANIC -> 0xFF2E5FA3;
            case CRATERED -> 0xFF9A9A8E;
            case BARREN, ROCKY -> 0xFF8F8C84;
        };
        return 0xFF000000 | base;
    }
}
