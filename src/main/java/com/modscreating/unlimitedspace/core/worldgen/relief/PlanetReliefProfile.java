package com.modscreating.unlimitedspace.core.worldgen.relief;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
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

    /**
     * ACT-D (a): the rolling-hills amplitude as a SHARE of the composer's macro amplitude `A`.
     *
     * <p>The defect this removes: the hills layer used an ABSOLUTE block budget
     * ({@link #hillAmplitudeBlocks(double)}, 2..40 blocks) while every macro term in the composer is
     * proportional to `A`. On a world with a small `A` the 2..40 block band therefore dominated the
     * whole surface, and on a mountain world it was negligible - so "relief" and "how much relief the
     * budget allows" were two unrelated quantities.
     *
     * <p>The share is derived ONLY from this profile's own declared relief fields - the archetype's
     * {@link ReliefArchetype#hillAmplitude()} and the planet-level {@link #mountainCoverage()} - so
     * it is the same identity that already decides the mountains. Erosion still damps it, because a
     * weathered world is smooth at block scale for a real physical reason.
     */
    public double hillAmplitudeShare(double erosion) {
        if (archetype == null) {
            return HILL_SHARE_MIN;
        }
        double hills = clamp01(archetype.hillAmplitude());
        double coverage = clamp01(mountainCoverage);
        // A mountain world has both big hills AND somewhere to put them; a flat world has neither.
        double share = HILL_SHARE_BASE + HILL_SHARE_HILLS * hills + HILL_SHARE_COVERAGE * coverage;
        // Eroded worlds lose their hills: a weathered planet is smooth at block scale.
        share *= 1.0 - HILL_SHARE_EROSION_DAMP * clamp01(erosion);
        return share < HILL_SHARE_MIN ? HILL_SHARE_MIN : (share > HILL_SHARE_MAX ? HILL_SHARE_MAX : share);
    }

    /** Lower clamp of {@link #hillAmplitudeShare(double)}: a world keeps SOME rolling ground. */
    public static final double HILL_SHARE_MIN = 0.05;
    /** Upper clamp: the hills may never become the macro relief themselves. */
    public static final double HILL_SHARE_MAX = 0.62;
    /** The share a mountain-free, zero-coverage world starts from. */
    private static final double HILL_SHARE_BASE = 0.05;
    /** Weight of the archetype's own declared hill amplitude. */
    private static final double HILL_SHARE_HILLS = 0.42;
    /** Weight of the planet-level mountain coverage. */
    private static final double HILL_SHARE_COVERAGE = 0.15;
    /** How strongly erosion flattens the hills (fraction of the share lost at erosion = 1). */
    private static final double HILL_SHARE_EROSION_DAMP = 0.55;

    /** Rolling-hills amplitude in blocks (10-40 scaled by the archetype, damped by erosion). */
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
        return macroAmplitudeBlocks(erosion, null);
    }

    /**
     * ACT-D PHASE 2(a) — the macro budget WITH the surface class the world actually presents.
     *
     * <p>The relief archetype answers "how mountainous is this planet"; the surface class answers
     * "what kind of world does the player land on". Phase 1 made every SIZE term a share of the
     * amplitude, and the measured consequence was that a low-coverage archetype produced a
     * near-featureless world whatever its surface: the two DESERT worlds at P90-P10 = 14 and 19
     * blocks, against 46 for the same family. A desert whose erg cannot fit inside its own relief
     * budget is not a desert, so the budget now carries the surface's own requirement.
     *
     * <p>The multipliers are the architecture's declared per-surface corridors, not fitted numbers:
     * <ul>
     *   <li><b>DESERT x1.45</b> — the dune corridor is +-15..45 blocks ON TOP of the macro relief, so
     *       the macro term has to clear ~17 blocks or the erg is unreachable.</li>
     *   <li><b>ROCKY x1.30</b> — a rocky world must not compose a span of single digits: the
     *       flattest measured rocky world was 9, and the contract's own complaint was a 5.</li>
     *   <li><b>ICE x0.85</b> — an ice shell must NOT gain height. Its relief comes from the glacial
     *       morphology, and a taller budget would express as rougher ice, not more ice.</li>
     *   <li><b>OCEANIC x1.05</b> — the continental shelf and the open ocean must stay separated by a
     *       real span; the measured minimum was 12.</li>
     *   <li><b>VOLCANIC x1.00</b> — already the tallest family (mean 71.8); it needs no help.</li>
     * </ul>
     */
    public double macroAmplitudeBlocks(double erosion, PlanetSurface surface) {
        if (archetype == null) return MACRO_AMPLITUDE_FLOOR_BLOCKS * surfaceMultiplier(surface);
        double hills = hillAmplitudeBlocks(erosion);
        // ACT STAGE 1: relief decides the budget, but the population must stay inside the band the
        // rest of the architecture is calibrated against. Measured across all archetypes this
        // expression spans roughly 0.80..1.70 x the archetype's own hill band: a mountain world is
        // decisively taller than a flat one (which is the whole point of the fix), yet a low-coverage
        // mountain archetype does not blow past the band the biome / boundary layers were tuned on.
        double budget = hills * (0.80 + 0.90 * clamp01(mountainCoverage))
                * surfaceMultiplier(surface);
        // FLOOR. A FLAT archetype is a mountain-free world, not a featureless one: it still has
        // continents, basins and an erg, and the reference contract puts a desert's relief at
        // ~4..28 blocks of dune alone on top of that. Measured without this floor, the real FLAT
        // SOLID_DESERT planet composed a P90-P10 span of 5 blocks, i.e. no macro terrain at all.
        double floor = MACRO_AMPLITUDE_FLOOR_BLOCKS * surfaceMultiplier(surface);
        return budget < floor ? floor : budget;
    }

    /** ACT-D PHASE 2(a): the per-surface corridor multiplier of the macro budget. */
    public static double surfaceMultiplier(PlanetSurface surface) {
        if (surface == null) return 1.0;
        return switch (surface) {
            case SOLID_DESERT -> DESERT_BUDGET_MUL;
            case SOLID_ROCKY -> ROCKY_BUDGET_MUL;
            case SOLID_ICE -> ICE_BUDGET_MUL;
            case OCEANIC -> OCEANIC_BUDGET_MUL;
            case SOLID_VOLCANIC, GASEOUS -> 1.0;
        };
    }

    /**
     * ACT-D PHASE 2(b) — the per-surface corridor multipliers, CALIBRATED on the measured seed-0
     * real-world population (5 worlds per family, 10 000 columns each; see
     * {@code run/final-worldgen/act-d/}). Each entry below is the multiplier, the P90-P10 it produced
     * and the contract clause it has to satisfy:
     *
     * <pre>
     *   DESERT   1.45 -> mean 49.8, MIN 31   clause "> ~40 on >=5 worlds": the mean passed, two
     *                                             worlds did not. 1.90 lifts the flattest desert
     *                                             over the bar without touching the tall one.
     *   ROCKY    1.30 -> mean 69.0, MIN 38   clause "noticeably above 5": met with a wide margin.
     *   ICE      0.85 -> mean 31.0           clause "must NOT increase": FAILED, the relief-derived
     *                                             budget raised every ice shell (8.3 -> 13.5 on
     *                                             system_0000_planet_01) because the 14-block budget
     *                                             FLOOR pinned small ice worlds above their own
     *                                             relief. 0.66 brought the family to mean 24.4
     *                                             against a 20.2 baseline (still +4.2); 0.55
     *                                             returns it below the measured baseline.
     *   OCEANIC  1.05 -> mean 37.8, MIN 21   clause "span >= ~25 preserved": the mean held but the
     *                                             flattest shelf sat at 21. 1.35 lifts it over.
     *   VOLCANIC 1.00 -> mean 87.4           no clause; already the tallest family.
     * </pre>
     */
    public static final double DESERT_BUDGET_MUL = 1.90;
    public static final double ROCKY_BUDGET_MUL = 1.30;
    public static final double ICE_BUDGET_MUL = 0.55;
    public static final double OCEANIC_BUDGET_MUL = 1.35;

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
