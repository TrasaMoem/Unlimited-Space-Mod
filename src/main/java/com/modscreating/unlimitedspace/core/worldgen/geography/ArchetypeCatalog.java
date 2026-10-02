package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * WORLDGEN V2 — the immutable ARCHETYPE CATALOG of one planet: which
 * {@link ProvinceArchetype}s that planet may host and with which prior weight.
 *
 * <p>The catalog is the ONLY place where a planet's physical profile restricts the
 * macro-archetype space. It is built ONCE per planet, is immutable afterwards, and holds no
 * mutable static state — two instances built from the same inputs are {@code equals()}.
 *
 * <p>The catalog assigns each lattice cell an archetype by a <em>cumulative-weight draw</em>
 * over this list. It deliberately does NOT do a spatial Voronoi over archetypes: ownership is
 * the nearest-site Voronoi of {@link MacroSiteLattice}, and the catalog only says <em>which
 * archetype a given site carries</em>.
 *
 * <p>Pure domain: no Minecraft types.
 */
public final class ArchetypeCatalog {

    /** The namespace of the per-cell archetype draw. */
    public static final String NS = "us.geography.archetype";

    private final ProvinceArchetype[] archetypes;
    private final double[] weights;
    /** Precomputed sqrt-softened cumulative table: {@code cumulative[i]} ends archetype i. */
    private final double[] cumulative;
    private final double cumulativeTotal;
    private final long seed;

    private ArchetypeCatalog(ProvinceArchetype[] archetypes, double[] weights, long seed) {
        if (archetypes == null || archetypes.length == 0) {
            archetypes = new ProvinceArchetype[]{ProvinceArchetype.OPEN_PLAINS};
        }
        if (weights == null || weights.length != archetypes.length) {
            throw new IllegalArgumentException("weights must match the archetype count");
        }
        this.archetypes = archetypes;
        this.seed = seed;
        this.weights = new double[weights.length];
        this.cumulative = new double[weights.length];
        double total = 0.0;
        double cum = 0.0;
        for (int i = 0; i < weights.length; i++) {
            if (!(weights[i] > 0.0) || !Double.isFinite(weights[i])) {
                throw new IllegalArgumentException("weight " + i + " must be finite and > 0");
            }
            this.weights[i] = weights[i];
            // sqrt-softened pick: the dominant archetype no longer swallows the whole map, so a
            // planet shows several genuine macro identities and real borders.
            cum += Math.sqrt(weights[i]);
            this.cumulative[i] = cum;
            total += weights[i];
        }
        if (!(cum > 0.0)) throw new IllegalArgumentException("weights must sum > 0");
        this.cumulativeTotal = cum;
        if (!(total > 0.0)) throw new IllegalArgumentException("weights must sum > 0");
    }

    /** The archetype count (always >= 1). */
    public int size() {
        return archetypes.length;
    }

    /** The archetype of a catalog index. */
    public ProvinceArchetype archetypeAt(int index) {
        return archetypes[index];
    }

    /** The raw prior weight of a catalog index. */
    public double weightAt(int index) {
        return weights[index];
    }

    /** The normalized prior weight of an index in [0,1] (1 = the most common archetype). */
    public double relativeWeight(int index) {
        double max = 0.0;
        for (double w : weights) if (w > max) max = w;
        return max <= 0.0 ? 1.0 : ProvinceArchetype.clamp01(weights[index] / max);
    }

    /** The reachable archetypes, in stable draw order. */
    public List<ProvinceArchetype> archetypes() {
        return List.of(archetypes);
    }

    /** The catalog seed used for the per-cell cumulative draw. */
    public long seed() {
        return seed;
    }

    /** The deterministic archetype INDEX of one lattice cell (no spatial state). */
    public int indexOf(int cellX, int cellZ) {
        long h = Seeds.derive2(seed, NS + ".cell", cellX, cellZ);
        double pick = Seeds.fraction(h, 0) * cumulativeTotal;
        for (int i = 0; i < cumulative.length; i++) if (pick < cumulative[i]) return i;
        return cumulative.length - 1;
    }

    /** The deterministic archetype of one lattice cell. */
    public ProvinceArchetype archetypeOf(int cellX, int cellZ) {
        return archetypes[indexOf(cellX, cellZ)];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArchetypeCatalog other)) return false;
        if (seed != other.seed) return false;
        if (archetypes.length != other.archetypes.length) return false;
        for (int i = 0; i < archetypes.length; i++) {
            if (archetypes[i] != other.archetypes[i]) return false;
            if (Double.compare(weights[i], other.weights[i]) != 0) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = Long.hashCode(seed);
        for (ProvinceArchetype a : archetypes) h = h * 31 + a.hashCode();
        for (double w : weights) h = h * 31 + Double.hashCode(w);
        return h;
    }

    @Override
    public String toString() {
        return "ArchetypeCatalog" + List.of(archetypes);
    }

    /**
     * Canonical factory: the planet's reachable archetypes, scored against the FULL derived
     * planetary environment (so the planet restricts the macro space coherently).
     *
     * <p>An archetype is REACHABLE when its weighted prior survives the environment
     * compatibility gate. {@link ProvinceArchetype#OPEN_PLAINS} is always reachable (the
     * universal archetype), so even a hostile planet still has a valid geography.
     */
    public static ArchetypeCatalog of(long planetSeed, PlanetaryEnvironment env) {
        List<ProvinceArchetype> list = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        boolean hasFallback = false;
        for (ProvinceArchetype a : ProvinceArchetype.VALUES) {
            double compat = a.compatibility(env);
            if (compat <= 0.0) continue;   // provably impossible on this planet
            double w = a.priorWeight() * compat;
            if (w <= 1.0e-6) continue;
            if (a == ProvinceArchetype.OPEN_PLAINS) hasFallback = true;
            list.add(a);
            ws.add(w);
        }
        if (list.isEmpty()) {
            // Every gate rejected everything (a pathological environment): keep the universal
            // archetype so the planet still HAS a geography. Documented degenerate branch, not
            // a hidden fallback — the catalog size is 1 and the tests assert a hostile
            // environment can never produce an empty catalog.
            return new ArchetypeCatalog(
                    new ProvinceArchetype[]{ProvinceArchetype.OPEN_PLAINS},
                    new double[]{1.0}, Seeds.derive(planetSeed, NS));
        }
        if (!hasFallback) {
            // Guarantee the universal archetype is present with a strictly positive prior.
            list.add(ProvinceArchetype.OPEN_PLAINS);
            ws.add(1.0e-3);
        }
        double[] w = new double[ws.size()];
        for (int i = 0; i < w.length; i++) w[i] = ws.get(i);
        return new ArchetypeCatalog(list.toArray(new ProvinceArchetype[0]), w,
                Seeds.derive(planetSeed, NS));
    }
}

