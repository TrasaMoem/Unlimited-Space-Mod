package com.modscreating.unlimitedspace.core.worldgen.geology;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic geological-province classifier (R16 planet-diversity foundation).
 *
 * <p>Pure function of {@code (provinceSeed, PlanetPhysicalProfile, regionNoise, elevation01)}
 * &rarr; {@link GeologicalProvince}. The classification is deliberately cheap (a handful of
 * comparisons per column) and never allocates in the hot path.
 *
 * <p>Provinces are LARGE: {@link GeologicalProvinceMap} evaluates a smooth value-noise field on
 * a 192-block cell grid before calling {@link #classify}, so a region reads as a recognisable
 * area (12+ chunks across) rather than block-sized noise.
 */
public final class GeologicalProvinceSelector {

    /** Cell size of the province noise field, in blocks (large enough to read as a region). */
    public static final int CELL_SIZE = 192;

    private static final String NS = "us.geology.province";
    private static final long NOISE_SLOT = 93001L;

    private GeologicalProvinceSelector() {}

    /** One weighted, reachable province for a planet. */
    public record Weight(GeologicalProvince province, double weight) {}

    /**
     * Reachable provinces with their relative weights, derived from the physical profile.
     * PLAINS is always present (universal fallback); every other province must be geologically
     * admissible on this planet. Deterministic and pure.
     */
    public static List<Weight> weights(PlanetPhysicalProfile p) {
        if (p == null) {
            return List.of(new Weight(GeologicalProvince.PLAINS, 1.0));
        }
        List<Weight> out = new ArrayList<>();
        add(out, GeologicalProvince.VOLCANIC, pow(p.volcanicActivity(), 1.5) * 3.0);
        add(out, GeologicalProvince.GEOTHERMAL, pow(p.geothermalFlux(), 1.4) * 2.2);
        add(out, GeologicalProvince.GLACIAL, pow(1.0 - p.temperature(), 2.0) * 3.0);
        add(out, GeologicalProvince.CRYSTAL, pow(p.crystalAbundance(), 1.3) * 2.5);
        add(out, GeologicalProvince.CRATER,
                p.impactFrequency() * 2.5 + (1.0 - p.atmosphericDensity()));
        add(out, GeologicalProvince.MOUNTAIN, p.tectonicActivity() * 2.0 + p.erosion() * 0.8);
        add(out, GeologicalProvince.CANYON,
                p.erosion() * 2.2 * (1.0 - p.waterAbundance()));
        add(out, GeologicalProvince.BASIN,
                (1.0 - p.tectonicActivity()) * 1.5 + p.waterAbundance());
        add(out, GeologicalProvince.SALT,
                p.waterAbundance() * (1.0 - p.humidity()) * 2.0);
        add(out, GeologicalProvince.PLAINS, 1.0);
        return List.copyOf(out);
    }

    /** Cumulative-weight lookup over a stable province order (deterministic, no allocation). */
    public static GeologicalProvince classify(PlanetPhysicalProfile profile,
                                              List<Weight> weights,
                                              double regionNoise, double elevation01) {
        double biased = biasedNoise(regionNoise, elevation01);

        double total = 0.0;
        for (Weight w : weights) total += w.weight();
        double target = biased * total;
        double acc = 0.0;
        for (Weight w : weights) {
            acc += w.weight();
            if (target < acc) return w.province();
        }
        return GeologicalProvince.PLAINS;
    }

    /** Smooth province field value in [0,1) for a world coordinate. */
    public static double regionNoise(long provinceSeed, int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        double fx = (x - cx * (double) CELL_SIZE) / CELL_SIZE;
        double fz = (z - cz * (double) CELL_SIZE) / CELL_SIZE;
        double tx = smoothstep(fx);
        double tz = smoothstep(fz);
        double v00 = corner(provinceSeed, cx, cz);
        double v10 = corner(provinceSeed, cx + 1, cz);
        double v01 = corner(provinceSeed, cx, cz + 1);
        double v11 = corner(provinceSeed, cx + 1, cz + 1);
        double a = lerp(v00, v10, tx);
        double b = lerp(v01, v11, tx);
        return lerp(a, b, tz);
    }

    private static double corner(long provinceSeed, int cx, int cz) {
        long h = Seeds.derive(provinceSeed, NS, cx, cz);
        return Seeds.fraction(h, NOISE_SLOT);
    }

    private static void add(List<Weight> out, GeologicalProvince province, double weight) {
        if (weight <= 0.0) return;
        out.add(new Weight(province, weight));
    }

    private static double pow(double v, double exp) {
        double c = v;
        if (c < 0.0) c = 0.0;
        if (c > 1.0) c = 1.0;
        return Math.pow(c, exp);
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Unified per-column classification: one source of truth for terrain/material/feature
     *  consumers. Returns a {@link GeologicalProvinceContext} with province + dominance. */
    public static GeologicalProvinceContext contextAt(long provinceSeed,
                                                      List<Weight> weights,
                                                      PlanetPhysicalProfile profile,
                                                      int x, int z, double elevation01) {
        if (profile == null || weights.isEmpty()) {
            return GeologicalProvinceContext.neutral(profile);
        }
        double noise = regionNoise(provinceSeed, x, z);
        GeologicalProvince province = classify(profile, weights, noise, elevation01);
        double strength = strengthOf(weights, province);
        return new GeologicalProvinceContext(province, strength,
                borderFade(weights, biasedNoise(noise, elevation01), province), profile);
    }

    /** Elevation-refined province noise (shared by {@link #classify} and the border fade). */
    static double biasedNoise(double noise, double elevation01) {
        double n = noise;
        if (n < 0.0) n = 0.0;
        if (n > 0.999) n = 0.999;
        // Elevation refinement: high ground favours relief provinces, low ground favours basins.
        double biased = n;
        if (elevation01 > 0.62) {
            biased = n * 0.55 + 0.45 * (n + 0.35);
        } else if (elevation01 < 0.25) {
            biased = n * 0.55 + 0.45 * (n - 0.35);
        }
        if (biased < 0.0) biased = 0.0;
        if (biased > 0.999) biased = 0.999;
        return biased;
    }

    /** How far the province-noise sits from the boundary of the chosen province, 0..1. */
    private static final double BORDER_FADE = 0.25;

    /**
     * R20: spatial confidence of the chosen province — 0 exactly AT a province border and 1
     * well inside. Province weights are planet constants, so the province LABEL flips abruptly
     * where the noise crosses a cumulative-weight threshold; any amplitude gated by the label
     * would step. This measures the margin to that threshold (in noise units) and smooths it,
     * giving continuous, coordinate-dependent province influence with no walls.
     */
    static double borderFade(List<Weight> weights, double biased, GeologicalProvince province) {
        double total = 0.0;
        for (Weight w : weights) total += w.weight();
        if (total <= 0.0) return 1.0;
        double target = biased * total;
        double acc = 0.0;
        for (Weight w : weights) {
            double next = acc + w.weight();
            if (w.province() == province && target < next) {
                double margin = Math.min(target - acc, next - target) / total;
                return smoothstep(clamp01(margin / BORDER_FADE));
            }
            acc = next;
        }
        return 1.0;
    }

    /** Relative dominance of a province within the reachable set (0..1, deterministic). */
    static double strengthOf(List<Weight> weights, GeologicalProvince province) {
        double max = 0.0, chosen = 0.0;
        for (Weight w : weights) {
            if (w.weight() > max) max = w.weight();
            if (w.province() == province) chosen = w.weight();
        }
        if (max <= 0.0) return 1.0;
        return clamp01(chosen / max);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
