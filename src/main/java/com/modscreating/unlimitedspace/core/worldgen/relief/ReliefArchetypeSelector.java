package com.modscreating.unlimitedspace.core.worldgen.relief;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Weighted selector for a planet's {@link ReliefArchetype} + {@link PlanetReliefProfile} (R21).
 *
 * <p>CRITICAL: the relief archetype is chosen AT PLANET LEVEL. One world is FLAT, another is
 * VERY_MOUNTAINOUS — the choice comes from the physical profile (tectonics, erosion, impacts,
 * water) plus a deterministic seed draw, never from local noise. Pure function of
 * {@code (planetSeed, PlanetPhysicalProfile)}.
 */
public final class ReliefArchetypeSelector {

    private static final String NS = "us.relief.archetype";
    private static final long DRAW_SLOT = 99001L;
    private static final long COVERAGE_SLOT = 99002L;

    private ReliefArchetypeSelector() {}

    /** One scored relief candidate. */
    public record Candidate(ReliefArchetype archetype, double score) {}

    /** Score every relief archetype against the profile, in stable enum order. */
    public static java.util.List<Candidate> candidates(PlanetPhysicalProfile p) {
        java.util.List<Candidate> out = new java.util.ArrayList<>(ReliefArchetype.VALUES.length);
        for (ReliefArchetype a : ReliefArchetype.VALUES) {
            double s = a.score(p);
            if (s > 0.05) out.add(new Candidate(a, s));
        }
        return java.util.List.copyOf(out);
    }

    /** Canonical factory: planet seed + physical profile &rarr; relief profile. */
    public static PlanetReliefProfile create(long planetSeed, PlanetPhysicalProfile profile) {
        ReliefArchetype primary = draw(planetSeed, profile);
        long seed = Seeds.derive(planetSeed, "us.relief.field.seed");
        // Planet-level mountain coverage: archetype baseline ± a small deterministic draw so
        // two planets of the same archetype still differ. Regions then modulate it locally.
        double coverage = clamp01(primary.mountainCoverage()
                + (Seeds.fraction(Seeds.derive(planetSeed, NS), COVERAGE_SLOT) - 0.5) * 0.18);
        return new PlanetReliefProfile(primary, coverage, seed);
    }

    /** Deterministic weighted draw over a scored list (pure). */
    static ReliefArchetype draw(long planetSeed, PlanetPhysicalProfile profile) {
        java.util.List<Candidate> scored = candidates(profile);
        if (scored.isEmpty()) return ReliefArchetype.ROLLING;
        double total = 0.0;
        for (Candidate c : scored) total += c.score();
        double pick = Seeds.fraction(Seeds.derive(planetSeed, NS), DRAW_SLOT) * total;
        double acc = 0.0;
        for (Candidate c : scored) {
            acc += c.score();
            if (pick < acc) return c.archetype();
        }
        return scored.get(scored.size() - 1).archetype();
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
