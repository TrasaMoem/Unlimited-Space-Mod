package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted selector for a planet's {@link TerrainArchetype} (R20).
 *
 * <p>Implements "planet archetypes without hardcoded planet identities": every archetype is
 * scored against the physical profile, then the planet seed draws a weighted <b>primary</b>
 * archetype plus a <b>secondary modifier</b> archetype. The blended grammar
 * ({@code blended()}) is what the terrain composer consumes, so e.g.
 * {@code DESERT_WORLD + HIGH_TECTONICS} naturally yields giant desert ranges with canyons.
 *
 * <p>Pure function of {@code (planetSeed, PlanetPhysicalProfile)} — deterministic.
 */
public final class TerrainArchetypeSelector {

    private static final String NS = "us.terrain.archetype";
    private static final long DRAW_SLOT = 97001L;
    private static final long SECONDARY_SLOT = 97002L;
    private static final long BLEND_SLOT = 97003L;

    private static final double SCORE_FLOOR = 0.10;

    private TerrainArchetypeSelector() {}

    /** One scored archetype candidate. */
    public record Candidate(TerrainArchetype archetype, double score) {}

    /** Score every archetype against the profile, in stable enum order. */
    public static List<Candidate> candidates(PlanetPhysicalProfile p) {
        List<Candidate> out = new ArrayList<>(TerrainArchetype.VALUES.length);
        for (TerrainArchetype a : TerrainArchetype.VALUES) {
            double s = a.score(p);
            if (s > SCORE_FLOOR) out.add(new Candidate(a, s));
        }
        return List.copyOf(out);
    }

    /** The selected archetype pair (primary + secondary modifier). Immutable value. */
    public record ArchetypePair(TerrainArchetype primary, TerrainArchetype secondary, double blend) {

        public ArchetypePair {
            if (primary == null) primary = TerrainArchetype.CONTINENTAL;
            if (secondary == null) secondary = primary;
            blend = clamp01(blend);
        }

        /** Grammar blended from primary + secondary (secondary acts as a modifier). */
        public TerrainArchetypeGrammar blended() {
            return new TerrainArchetypeGrammar(
                    lerp(primary.continentalBias(), secondary.continentalBias(), blend * 0.6),
                    lerp(primary.mountainStrength(), secondary.mountainStrength(), blend),
                    lerp(primary.erosionBias(), secondary.erosionBias(), blend * 0.6),
                    lerp(primary.valleyStrength(), secondary.valleyStrength(), blend * 0.7),
                    lerp(primary.basinStrength(), secondary.basinStrength(), blend * 0.7),
                    lerp(primary.craterFrequencyMul(), secondary.craterFrequencyMul(), blend * 0.5),
                    lerp(primary.volcanicStrengthMul(), secondary.volcanicStrengthMul(), blend * 0.6),
                    lerp(primary.plateauTendency(), secondary.plateauTendency(), blend * 0.7),
                    lerp(primary.localDetailFactor(), secondary.localDetailFactor(), blend * 0.5));
        }

        public String summary() {
            return primary + (blend > 0.05
                    ? "+" + secondary + "(" + String.format(java.util.Locale.ROOT, "%.2f", blend) + ")"
                    : "");
        }

        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    }

    /** Blended archetype grammar (immutable, values clamped to sane ranges). */
    public record TerrainArchetypeGrammar(
            double continentalBias, double mountainStrength, double erosionBias,
            double valleyStrength, double basinStrength, double craterFrequencyMul,
            double volcanicStrengthMul, double plateauTendency, double localDetailFactor) {

        public TerrainArchetypeGrammar {
            continentalBias = clamp01(continentalBias);
            mountainStrength = clamp01(mountainStrength);
            erosionBias = clamp01(erosionBias);
            valleyStrength = clamp01(valleyStrength);
            basinStrength = clamp01(basinStrength);
            craterFrequencyMul = clampRange(craterFrequencyMul, 0.0, 2.5);
            volcanicStrengthMul = clampRange(volcanicStrengthMul, 0.0, 2.0);
            plateauTendency = clamp01(plateauTendency);
            localDetailFactor = clampRange(localDetailFactor, 0.04, 0.18);
        }
    }

    /** Canonical factory: planet seed + physical profile &rarr; archetype pair. */
    public static ArchetypePair create(long planetSeed, PlanetPhysicalProfile profile) {
        List<Candidate> scored = candidates(profile);
        if (scored.isEmpty()) {
            return new ArchetypePair(TerrainArchetype.CONTINENTAL, TerrainArchetype.PLAINS_WORLD, 0.3);
        }
        long seed = Seeds.derive(planetSeed, NS);
        TerrainArchetype primary = weightedDraw(scored, Seeds.fraction(seed, DRAW_SLOT));

        List<Candidate> rest = new ArrayList<>(scored.size());
        for (Candidate c : scored) if (c.archetype() != primary) rest.add(c);
        TerrainArchetype secondary = rest.isEmpty() ? primary
                : weightedDraw(rest, Seeds.fraction(seed, SECONDARY_SLOT));
        double blend = 0.15 + 0.45 * Seeds.fraction(seed, BLEND_SLOT);
        return new ArchetypePair(primary, secondary, blend);
    }

    /** Deterministic weighted draw over a scored list (pure). */
    static TerrainArchetype weightedDraw(List<Candidate> scored, double pick) {
        double total = 0.0;
        for (Candidate c : scored) total += c.score();
        double target = clamp01(pick) * total;
        double acc = 0.0;
        for (Candidate c : scored) {
            acc += c.score();
            if (target < acc) return c.archetype();
        }
        return scored.get(scored.size() - 1).archetype();
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    private static double clampRange(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
