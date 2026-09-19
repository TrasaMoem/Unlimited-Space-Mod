package com.modscreating.unlimitedspace.core.worldgen.climate;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

/**
 * Weighted-rules selector for a planet's {@link ClimateArchetype} (R21).
 *
 * <pre>
 * PLANET -&gt; PHYSICAL PROFILE -&gt; CLIMATE ARCHETYPE (this) -&gt; PlanetClimateField -&gt; BIOME REGIONS
 * </pre>
 *
 * <p>Every archetype is scored against the physical profile with its affinity weights, then the
 * planet seed draws a weighted archetype — never hardcoded per planet id. Pure function of
 * {@code (planetSeed, PlanetPhysicalProfile)}.
 */
public final class ClimateArchetypeSelector {

    private static final String NS = "us.climate.archetype";
    private static final long DRAW_SLOT = 98001L;

    private ClimateArchetypeSelector() {}

    /** One scored climate candidate. */
    public record Candidate(ClimateArchetype archetype, double score) {}

    /** Score every archetype against the profile, in stable enum order. */
    public static java.util.List<Candidate> candidates(PlanetPhysicalProfile p) {
        java.util.List<Candidate> out = new java.util.ArrayList<>(ClimateArchetype.VALUES.length);
        for (ClimateArchetype a : ClimateArchetype.VALUES) {
            double s = a.score(p);
            if (s > 0.05) out.add(new Candidate(a, s));
        }
        return java.util.List.copyOf(out);
    }

    /** Canonical factory: planet seed + physical profile &rarr; climate archetype. */
    public static ClimateArchetype create(long planetSeed, PlanetPhysicalProfile profile) {
        java.util.List<Candidate> scored = candidates(profile);
        if (scored.isEmpty()) return ClimateArchetype.TEMPERATE;
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
}
