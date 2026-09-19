package com.modscreating.unlimitedspace.core.worldgen.relief;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * The planet's relief subsystem (R21): archetype + mountain coverage + spatial field seed.
 *
 * <p>The archetype answers "is this world flat or a mountain world" ONCE per planet;
 * {@code mountainCoverage} is the explicit planet parameter (0 = no mountains at all, 1 =
 * dominant mountain world). Biome regions may then modulate coverage LOCALLY (a mountain
 * region multiplies it up, a plains region multiplies it down) — so one planet can carry a
 * huge mountain belt next to an enormous plain.
 *
 * <p>Pure domain, deterministic from {@code (planetSeed, physical profile)}.
 *
 * @param archetype        the planet's relief identity
 * @param mountainCoverage planet-level mountain share in [0,1]
 * @param reliefSeed       subsystem seed of the spatial relief fields
 */
public record PlanetReliefProfile(ReliefArchetype archetype, double mountainCoverage,
                                  long reliefSeed) {

    /** Wavelengths (blocks) of the relief fields — every layer is far above local detail. */
    public static final double HILL_WAVELENGTH = 220.0;
    public static final double HILLS_MIN_BLOCKS = 2.0;
    public static final double HILLS_MAX_BLOCKS = 40.0;

    /** Canonical factory: planet seed + physical profile &rarr; relief profile. */
    public static PlanetReliefProfile create(long planetSeed, PlanetPhysicalProfile physical) {
        return ReliefArchetypeSelector.create(planetSeed, physical);
    }

    /** Convenience seed accessor. */
    public long seed() {
        return reliefSeed;
    }

    /** Rolling-hills amplitude in blocks (10–40 scaled by the archetype, damped by erosion). */
    public double hillAmplitudeBlocks(double erosion) {
        double base = HILLS_MIN_BLOCKS
                + (HILLS_MAX_BLOCKS - HILLS_MIN_BLOCKS) * archetype.hillAmplitude();
        // Eroded worlds lose their hills: a weathered planet is smooth at block scale.
        double damp = 1.0 - 0.55 * clamp01(erosion);
        return base * damp;
    }

    /** Debug label, e.g. {@code VERY_MOUNTAINOUS}. */
    public String label() {
        return archetype == null ? "?" : archetype.name();
    }

    /** Stable feature seed for relief-driven subsystems (diagnostics). */
    public long featureSeed() {
        return Seeds.derive(reliefSeed, "us.relief.features");
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
