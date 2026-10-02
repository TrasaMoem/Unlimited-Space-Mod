package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceSelector;
import com.modscreating.unlimitedspace.core.worldgen.geology.ProvinceField;

import java.util.List;

/**
 * WORLDGEN V2 — the per-province terrain modifier field, driven by CONTINUOUS weights.
 *
 * <h2>What this class is forbidden to do</h2>
 * <pre>
 *   if (province == X) height += ...          // FORBIDDEN — a step function
 *   switch (province) { case X: amp = 1.7; }   // FORBIDDEN — a step function
 * </pre>
 *
 * <p>Instead, the modifier is the weighted average of the per-province shape grammar using the
 * SAME continuous weights the geology layer produces. The result is continuous by construction:
 * as a column crosses a province border the weights change continuously, so every modifier
 * changes continuously, so the height changes continuously. There is no correction pass, no
 * guard and no anchor — the continuity is structural.
 *
 * <p>Pure domain, allocation-free in the hot path (one caller-owned scratch buffer).
 */
public final class ProvinceTerrainModifier {

    /** Per-column modifier sample (immutable). */
    public record Sample(double uplift, double bias, double roughness, double carve, double dune) {

        /** The shared neutral sample — the common case. */
        public static final Sample NEUTRAL = new Sample(1.0, 0.0, 1.0, 1.0, 0.0);

        /** True when the sample is the shared neutral instance (a fast path in the shaper). */
        public boolean isNeutral() {
            return this == NEUTRAL;
        }
    }

    /** The immutable per-province shape grammar. */
    public record Grammar(double uplift, double bias, double roughness, double carve, double dune) {}

    private final GeologicalProvinceMap map;
    private final double maxUplift;
    private final double maxBias;
    private final Grammar[] grammar;
    /** Caller-supplied scratch for the continuous weight vector (reused, never reallocated). */
    private final double[] scratch;

    private ProvinceTerrainModifier(GeologicalProvinceMap map, double maxUplift, double maxBias) {
        this.map = map;
        this.maxUplift = maxUplift;
        this.maxBias = maxBias;
        this.grammar = new Grammar[map.weights().size()];
        for (int i = 0; i < grammar.length; i++) {
            grammar[i] = staticFor(map.weights().get(i).province());
        }
        this.scratch = new double[map.weights().size()];
    }

    /** Canonical factory: planet seed + province map + signature &rarr; modifier field. */
    public static ProvinceTerrainModifier create(long planetSeed, GeologicalProvinceMap map,
                                                  TerrainSignature signature) {
        Seeds.derive(planetSeed, "us.terrain.province"); // deterministic derivation slot
        double sigAmp = signature == null ? 1.0 : signature.amplitudeMul();
        return new ProvinceTerrainModifier(map,
                0.7 + 0.6 * Math.min(1.6, sigAmp),
                4.0 + 6.0 * Math.min(1.8, sigAmp));
    }

    /**
     * The CONTINUOUS modifiers at a column: the weighted average of the per-province grammar
     * over the continuous province weights. There is no integer label anywhere in this path.
     */
    public Sample sample(int x, int z) {
        List<GeologicalProvinceSelector.Weight> table = map.weights();
        ProvinceField.weightsAt(map.mediumSeed(), table, x, z, scratch);
        double up = 0.0, bias = 0.0, rough = 0.0, carve = 0.0, dune = 0.0;
        for (int i = 0; i < table.size() && i < grammar.length; i++) {
            double w = scratch[i];
            if (w <= 0.0) continue;
            Grammar g = grammar[i];
            up += w * g.uplift();
            bias += w * g.bias();
            rough += w * g.roughness();
            carve += w * g.carve();
            dune += w * g.dune();
        }
        // The label-FREE micro texture adds local surface character only. Because it is a smooth
        // continuous field with no identity, it can never flip a major terrain category.
        double microTexture = 0.85 + 0.30 * map.microTextureAt(x, z);
        return new Sample(
                clampRange(up, 0.4, maxUplift),
                clampRange(bias, -maxBias, maxBias),
                clampRange(rough * microTexture, 0.4, 2.2),
                clampRange(carve, 0.0, 2.0),
                clampRange(dune, 0.0, 1.0));
    }

    /**
     * The static per-province shape grammar. This table is DATA, not control flow: a column
     * never "selects" a row, it blends all of them by weight.
     */
    public static Grammar staticFor(GeologicalProvince province) {
        return switch (province) {
            case VOLCANIC -> new Grammar(1.45, 5.0, 1.5, 0.9, 0.0);
            case MOUNTAIN -> new Grammar(1.70, 9.0, 1.3, 0.8, 0.0);
            case CRATER -> new Grammar(1.00, 0.0, 1.1, 1.0, 0.4);
            case BASIN -> new Grammar(0.65, -8.0, 0.7, 0.8, 0.3);
            case PLAINS -> new Grammar(0.75, 0.0, 0.65, 1.0, 0.0);
            case CANYON -> new Grammar(1.00, -2.0, 1.1, 2.0, 1.2);
            case CRYSTAL -> new Grammar(1.55, 3.0, 1.6, 0.8, 0.0);
            case SALT -> new Grammar(0.50, -3.0, 0.45, 1.0, 0.8);
            case GLACIAL -> new Grammar(1.15, 1.0, 0.8, 0.6, 0.0);
            case GEOTHERMAL -> new Grammar(1.25, 2.0, 1.45, 0.9, 0.0);
        };
    }

    private static double clampRange(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}