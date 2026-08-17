package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Deterministic composite terrain profile for one planet (R8).
 *
 * <p>Composes a primary {@link TerrainPattern} (and an optional secondary
 * pattern + blend weight for future terrain combination) with the per-planet
 * baseHeight / amplitude / frequency shaping consumed by
 * {@link com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator}.
 *
 * <p>Pure domain: no Minecraft types.
 */
public record TerrainProfile(
        TerrainPattern primaryPattern,
        TerrainPattern secondaryPattern,
        double blend,
        double baseHeight,
        double amplitude,
        double frequency,
        int octaveCount
) {

    public static TerrainProfile from(long planetSeed, PlanetProperties p) {
        double baseHeight = 64.0 + p.generationParameters().baseHeight() * 24.0;
        double rawAmp = (4.0 + p.terrainRoughness() * 28.0) * (1.0 - 0.4 * p.erosion());
        double frequency = 0.01 + p.generationParameters().terrainFrequency() * 0.02;

        TerrainPattern primary = TerrainPattern.select(planetSeed, p.surface());

        long secondarySeed = Seeds.derive(planetSeed, "us.terrain.secondary");
        TerrainPattern secondary;
        PlanetSurface surface = p.surface();
        if (surface == PlanetSurface.GASEOUS) {
            secondary = TerrainPattern.FLAT;
        } else {
            double f = Seeds.fraction(secondarySeed, 72002L);
            secondary = TerrainPattern.VALUES[(int) Math.floor(f * TerrainPattern.VALUES.length)];
            if (secondary == primary) {
                int next = (primary.ordinal() + 1) % TerrainPattern.VALUES.length;
                secondary = TerrainPattern.VALUES[next];
            }
        }

        double blend = Seeds.fraction(Seeds.derive(planetSeed, "us.terrain.blend"), 72003L);

        double amp = rawAmp * primary.amplitudeMultiplier();
        double freq = frequency * primary.frequencyMultiplier();
        int octaves = primary.octaves();

        return new TerrainProfile(primary, secondary, blend,
                baseHeight, amp, freq, octaves);
    }

    /** Convenience alias for the terrain subsystem seed. */
    public long terrainSeed() {
        // Caller (PlanetGenerationProfile) carries the planetSeed; this is a stable
        // helper used only by adapters that still hold the raw long.
        return 0L;
    }
}