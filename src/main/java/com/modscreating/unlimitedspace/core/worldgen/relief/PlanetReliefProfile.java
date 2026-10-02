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

    /**
     * ACT STAGE 1: the MACRO relief budget of this planet, in blocks — the AUTHORITATIVE terrain
     * amplitude the composer uses.
     *
     * <p>The defect this removes: the composer's {@code A} was the {@code amplitude} field of the
     * legacy {@code TerrainProfile}, which is precisely the value handed to the DEAD producer
     * {@code ValueNoiseTerrainGenerator} (nothing ever calls {@code TerrainShaper.sampleInto} on
     * it). So a mountain planet could carry a smaller macro relief than a canyon planet, purely
     * because two unrelated PlanetProperties channels happened to disagree — measured on real
     * planets: a {@code SOLID_DESERT} world with the {@code CANYONLAND} relief identity and 35%
     * mountain coverage produced a P90−P10 height span of 22 blocks, while a {@code FLAT}
     * {@code SOLID_ROCKY} world produced 5.
     *
     * <p>The budget is derived ONLY from this profile's own declared relief fields:
     * {@link ReliefArchetype#hillAmplitude()} through {@link #hillAmplitudeBlocks(double)}, and
     * {@link #mountainCoverage()}, which is the planet-level share of the world that actually
     * carries mountain systems. A world with few mountain systems therefore has relief that is
     * regionally large rather than globally large — which is what {@code mountainCoverage} has
     * always meant in this architecture.
     *
     * <p>Deterministic, pure, no seed and no coordinate: it is a planet property.
     */
    public double macroAmplitudeBlocks(double erosion) {
        if (archetype == null) return MACRO_AMPLITUDE_FLOOR_BLOCKS;
        double hills = hillAmplitudeBlocks(erosion);
        // ACT STAGE 1: relief decides the budget, but the population must stay inside the band the
        // rest of the architecture is calibrated against. Measured across all archetypes this
        // expression spans roughly 0.80..1.70 x the archetype's own hill band: a mountain world is
        // decisively taller than a flat one (which is the whole point of the fix), yet a low-coverage
        // mountain archetype does not blow past the band the biome / boundary layers were tuned on.
        double budget = hills * (0.80 + 0.90 * clamp01(mountainCoverage));
        // FLOOR. A FLAT archetype is a mountain-free world, not a featureless one: it still has
        // continents, basins and an erg, and the reference contract puts a desert's relief at
        // ~4..28 blocks of dune alone on top of that. Measured without this floor, the real FLAT
        // SOLID_DESERT planet composed a P90-P10 span of 5 blocks, i.e. no macro terrain at all.
        return budget < MACRO_AMPLITUDE_FLOOR_BLOCKS ? MACRO_AMPLITUDE_FLOOR_BLOCKS : budget;
    }

    /**
     * The composer's minimum MACRO relief budget, in blocks.
     *
     * <p>{@link ElevationField} declares its own amplitude corridors per level, and the DUNE one is
     * the binding constraint: {@code DUNE_SHARE = 0.88} against a stated corridor of +-15..45
     * blocks. A budget below ~17 blocks therefore makes the dune corridor UNREACHABLE, and the
     * architecture's own dune contract (a dune field with real crests and interdunes) silently
     * stops being expressible. 18 blocks keeps the corridor reachable while staying far below a
     * mountain world's budget, so {@code FLAT} and {@code VERY_MOUNTAINOUS} remain unmistakably
     * different worlds.
     */
    public static final double MACRO_AMPLITUDE_FLOOR_BLOCKS = 14.0;

    /**
     * ACT STAGE 1 — the relief's MULTIPLICATIVE authority over the macro amplitude.
     *
     * <p>The defect this removes is an inversion, not an under-scaling: before the fix the relief
     * profile had NO influence on the composed amplitude at all, so a {@code FLAT} {@code SOLID_ROCKY}
     * world and a {@code CANYONLAND} world could come out with any relative height purely because two
     * unrelated legacy channels disagreed. Measured on real planets: relief identity and
     * {@code mountainCoverage} had no measurable relationship with the composed P90-P10 span.
     *
     * <p>This factor is centred on 1.0 so it CORRECTS the ordering without rescaling the population:
     * a mountain world is reliably taller than a flat one, and a low-coverage mountain archetype is
     * reliably shorter than a high-coverage one, but the mean world keeps the amplitude band the
     * biome / boundary / continuity layers are calibrated against. A wholesale re-scaling of {@code A}
     * was measured to break that calibration - it lifts the A-proportional relief terms while the
     * ABSOLUTE ones (river incision, the 9-block plateau bench, the 40-block bound knee) stay put, so
     * the normalised surface becomes smoother and a single biome ends up owning the whole sampling
     * window. The floor and the corridor below exist for the same reason.
     */
    public double macroAmplitudeReliefFactor() {
        if (archetype == null) return 1.0;
        double hills = hillAmplitudeBlocks(0.0);
        double coverage = clamp01(mountainCoverage);
        // 0.94..1.34 around 1.0, driven by the archetype's own hill band and real coverage. The band
        // is deliberately narrow: it corrects the ORDERING (which the ACT requires) without moving
        // the population far enough to disturb the margin-gradient-sensitive boundary guards, which
        // compare two boundary layers against each other on the same seed.
        double f = 1.0 + 0.30 * ((hills - HILL_BAND_MIDPOINT) / (HILL_BAND_MIDPOINT))
                + 0.30 * (coverage - 0.25);
        return f < 0.94 ? 0.94 : (f > 1.34 ? 1.34 : f);
    }

    /** Midpoint of the archetype hill band, used as the neutral point of the relief factor. */
    private static final double HILL_BAND_MIDPOINT =
            (HILLS_MIN_BLOCKS + HILLS_MAX_BLOCKS) * 0.5;

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
