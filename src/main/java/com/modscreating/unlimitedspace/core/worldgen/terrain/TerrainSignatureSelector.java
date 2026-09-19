package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted-rules selector for a planet's {@link TerrainSignature} (R17 terrain-diversity stage).
 *
 * <p>Implements the "planet signature" requirement without hardcoding planet identities: every
 * morphology is scored against the physical profile with its affinity weights, then the planet
 * seed draws a weighted primary + secondary pair among the scored candidates.
 *
 * <p>Rules the system expresses (never hardcoded per planet):
 * <pre>
 * HIGH_TECTONIC        -&gt; mountains / ridges
 * HIGH_VOLCANIC        -&gt; volcanic terrain
 * HIGH_EROSION + DRY   -&gt; canyons / badlands
 * HIGH_WATER           -&gt; basins / lowlands
 * HIGH_IMPACT          -&gt; cratered
 * LOW_TEMPERATURE      -&gt; glacial
 * DRY + LOW_WATER      -&gt; dunes
 * </pre>
 *
 * <p>Pure function of {@code (planetSeed, PlanetPhysicalProfile)}.
 */
public final class TerrainSignatureSelector {

    private static final String NS = "us.terrain.signature";
    private static final long DRAW_SLOT = 95001L;
    private static final long SECONDARY_SLOT = 95002L;
    private static final long BLEND_SLOT = 95003L;

    /** Minimum score for a morphology to be reachable (keeps unsuitable shapes out). */
    private static final double SCORE_FLOOR = 0.12;

    private TerrainSignatureSelector() {}

    /** One scored morphology candidate. */
    public record Candidate(TerrainMorphology morphology, double score) {}

    /** Score every morphology against the profile, in stable enum order. */
    public static List<Candidate> candidates(PlanetPhysicalProfile p) {
        List<Candidate> out = new ArrayList<>(TerrainMorphology.VALUES.length);
        for (TerrainMorphology m : TerrainMorphology.VALUES) {
            double s = m.score(p);
            if (s > SCORE_FLOOR) out.add(new Candidate(m, s));
        }
        return List.copyOf(out);
    }

    /** Canonical factory: planet seed + physical profile &rarr; terrain signature. */
    public static TerrainSignature create(long planetSeed, PlanetPhysicalProfile profile) {
        List<Candidate> scored = candidates(profile);
        if (scored.isEmpty()) {
            // Degenerate profile: fall back to gentle terrain, still deterministic.
            return new TerrainSignature(TerrainMorphology.PLAINS, TerrainMorphology.FLAT,
                    0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5);
        }

        long seed = Seeds.derive(planetSeed, NS);
        TerrainMorphology primary = weightedDraw(scored, Seeds.fraction(seed, DRAW_SLOT));

        // Secondary: same weighting but the primary is excluded, so the blend adds variety.
        List<Candidate> rest = new ArrayList<>(scored.size());
        for (Candidate c : scored) {
            if (c.morphology() != primary) rest.add(c);
        }
        TerrainMorphology secondary = rest.isEmpty() ? primary
                : weightedDraw(rest, Seeds.fraction(seed, SECONDARY_SLOT));
        double blend = 0.25 + 0.5 * Seeds.fraction(seed, BLEND_SLOT);

        return new TerrainSignature(
                primary,
                secondary,
                blend,
                ridgeStrength(profile, primary, secondary),
                canyonStrength(profile, primary, secondary),
                profile.impactFrequency(),
                profile.volcanicActivity(),
                duneStrength(profile, primary, secondary),
                profile.tectonicActivity() * 0.5 + profile.erosion() * 0.25);
    }

    /** Deterministic weighted draw over a scored list (pure). */
    static TerrainMorphology weightedDraw(List<Candidate> scored, double pick) {
        double total = 0.0;
        for (Candidate c : scored) total += c.score();
        double target = clamp01(pick) * total;
        double acc = 0.0;
        for (Candidate c : scored) {
            acc += c.score();
            if (target < acc) return c.morphology();
        }
        return scored.get(scored.size() - 1).morphology();
    }

    // ---------------------------------------------------------------- feature strengths

    private static double ridgeStrength(PlanetPhysicalProfile p,
                                        TerrainMorphology primary, TerrainMorphology secondary) {
        double tectonic = p.tectonicActivity();
        double young = 1.0 - p.erosion();              // young geology keeps sharp relief
        double relief = primary.isReliefDominated() ? 1.0
                : secondary.isReliefDominated() ? 0.7 : 0.4;
        return clamp01(0.25 + 0.75 * (tectonic * young) * relief);
    }

    private static double canyonStrength(PlanetPhysicalProfile p,
                                         TerrainMorphology primary, TerrainMorphology secondary) {
        double dryness = 1.0 - p.humidity();
        boolean canyonMorphology = primary == TerrainMorphology.CANYON
                || primary == TerrainMorphology.BADLANDS
                || secondary == TerrainMorphology.CANYON
                || secondary == TerrainMorphology.BADLANDS;
        double base = p.erosion() * dryness * (1.0 - p.waterAbundance());
        return clamp01(base * (canyonMorphology ? 1.0 : 0.4));
    }

    private static double duneStrength(PlanetPhysicalProfile p,
                                       TerrainMorphology primary, TerrainMorphology secondary) {
        boolean duneMorphology = primary == TerrainMorphology.DUNES
                || secondary == TerrainMorphology.DUNES;
        if (!duneMorphology) return 0.0;
        double dryness = 1.0 - p.humidity();
        return clamp01(dryness * (1.0 - p.waterAbundance()));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
