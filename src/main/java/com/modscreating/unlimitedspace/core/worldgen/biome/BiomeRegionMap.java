package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.seed.Seeds;

import java.util.ArrayList;
import java.util.List;

/**
 * Large-scale biome region map of one planet (R21).
 *
 * <p>Replaces the old 64-block biome lottery. Regions are a warped cellular (Voronoi-like)
 * field with a cell size of ~1400 blocks, so ONE REGION occupies a major area of the planet
 * and boundaries are RARE and BIG:
 *
 * <pre>
 * PLANETARY CLIMATE (thousands of blocks)
 *   -&gt; BIOME REGION  (cell ~1400 blocks, jittered centers, warped borders)
 *      -&gt; TRANSITION ZONE (100–400 blocks, smooth weight toward the neighbour region)
 *         -&gt; LOCAL LANDFORMS (provinces / rare features)
 * </pre>
 *
 * <p>Sampling returns a {@link Context} with the primary region, the neighbouring (secondary)
 * region and a transition weight in [0,0.5] — 0 deep inside a region, 0.5 exactly on the
 * border. Consumers blend modifiers with the weight instead of switching labels.
 *
 * <p>Pure domain: no Minecraft types; deterministic pure function of
 * {@code (regionSeed, planet identity, coordinates)}; allocation-free in the hot path.
 */
public final class BiomeRegionMap {

    /** Cell size of the region field, in blocks (one region ≈ hundreds of chunks). */
    public static final int CELL_SIZE = 1400;
    /**
     * Attribute blend radius in blocks. It is deliberately SMALLER than the minimum distance
     * to any cell centre excluded by the 5×5 window, so the blend is continuous everywhere:
     * with the jitter clamped to [0.15, 0.85] the nearest excluded centre is at least
     * {@code (1.15 * CELL_SIZE)} away.
     */
    private static final double BLEND_RADIUS = CELL_SIZE * 0.75;
    /** Jitter band of cell centres (keeps centres away from cell edges → window-safe). */
    private static final double JITTER_MIN = 0.15;
    private static final double JITTER_SPAN = 0.70;

    private static final String NS = "us.biome.region";

    private final long regionSeed;
    private final List<PlanetBiomeRegion> reachable;
    private final double[] weights;
    private final double weightTotal;

    private BiomeRegionMap(long regionSeed, List<PlanetBiomeRegion> reachable, double[] weights) {
        this.regionSeed = regionSeed;
        this.reachable = List.copyOf(reachable);
        this.weights = weights;
        double t = 0.0;
        for (double w : weights) t += w;
        this.weightTotal = t;
    }

    /** One scored region candidate. */
    private record Candidate(PlanetBiomeRegion region, double weight) {}

    /**
     * Canonical factory: the planet's reachable regions.
     *
     * @param regionSeed       planet-scoped region seed
     * @param planetTemperature normalized planet temperature in [0,1]
     * @param planetHumidity   normalized planet humidity in [0,1]
     * @param crystalAbundance crystal-forming tendency in [0,1]
     * @param volcanicActivity volcanic output in [0,1]
     * @param impactFrequency  impact dominance in [0,1]
     * @param tectonicActivity tectonic intensity in [0,1]
     */
    public static BiomeRegionMap create(long regionSeed, double planetTemperature,
                                        double planetHumidity, double crystalAbundance,
                                        double volcanicActivity, double impactFrequency,
                                        double tectonicActivity) {
        List<Candidate> scored = new ArrayList<>();
        for (PlanetBiomeRegion r : PlanetBiomeRegion.VALUES) {
            double w = availability(r, planetTemperature, planetHumidity, crystalAbundance,
                    volcanicActivity, impactFrequency, tectonicActivity)
                    * r.priorWeight();
            if (w > 0.0) scored.add(new Candidate(r, w));
        }
        if (scored.isEmpty()) {
            scored.add(new Candidate(PlanetBiomeRegion.OPEN_PLAINS, 1.0));
        }
        List<PlanetBiomeRegion> regions = new ArrayList<>(scored.size());
        double[] weights = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            regions.add(scored.get(i).region());
            weights[i] = scored.get(i).weight();
        }
        return new BiomeRegionMap(Seeds.derive(regionSeed, NS), regions, weights);
    }

    /** All reachable regions on this planet, weighted order preserved. */
    public List<PlanetBiomeRegion> regions() {
        return reachable;
    }

    /** True when the planet can host the region type at all (before prior weighting). */
    private static double availability(PlanetBiomeRegion r, double temp, double hum,
                                       double crystal, double volcanic, double impact,
                                       double tectonic) {
        return switch (r) {
            case OPEN_PLAINS, ROLLING_COUNTRY -> 1.0;
            case HIGHLANDS -> clamp01(0.4 + 0.8 * tectonic);
            case BASIN_LOWLANDS -> clamp01(0.3 + 0.9 * hum);
            case FROZEN_EXPANSE -> temp < 0.42 ? 1.0 : clamp01(1.2 * (0.42 - temp) / 0.42);
            case DRY_MARSHLESS_BADLANDS -> clamp01(1.0 - hum) * 1.2;
            case SALT_FLATS -> clamp01(hum * (1.0 - hum) * 4.0);
            case CRYSTAL_FIELDS -> clamp01((crystal - 0.15) * 3.0);
            case GEOTHERMAL_FIELDS -> clamp01(volcanic * 2.2);
            case VOLCANIC_FIELDS -> clamp01(volcanic * 2.6);
            case GIANT_IMPACT_REGION -> clamp01((impact - 0.35) * 3.0);
            case LUMINOUS_TERRAIN -> clamp01((crystal - 0.30) * 2.0) * 0.6;
            case EXOTIC_ANOMALY -> clamp01((tectonic + crystal) * 0.9) * 0.5;
        };
    }

    /** Deterministic weighted region draw for one cell (pure). */
    private int cellIndex(int cellX, int cellZ) {
        long h = Seeds.derive(regionSeed, NS + ".cell", cellX, cellZ);
        double pick = Seeds.fraction(h, 0) * weightTotal;
        double acc = 0.0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (pick < acc) return i;
        }
        return weights.length - 1;
    }

    /** Region of a single field cell (pure). */
    public PlanetBiomeRegion regionOfCell(int cellX, int cellZ) {
        return reachable.get(cellIndex(cellX, cellZ));
    }

    /** Relative weight of a region within the planet's reachable set (0..1). */
    public double strengthOf(PlanetBiomeRegion region) {
        double max = 0.0, chosen = 0.0;
        for (int i = 0; i < reachable.size(); i++) {
            if (reachable.get(i) == region) {
                chosen = weights[i];
            }
            if (weights[i] > max) max = weights[i];
        }
        return max <= 0.0 ? 1.0 : clamp01(chosen / max);
    }

    /**
     * Unified per-column region context. The neighbourhood is a 3×3 block of cells and the
     * region ATTRIBUTES are an inverse-distance blend — continuous by construction, so a
     * region border can never step a terrain/homeostat multiplier (the R21 2×2 window did
     * exactly that: cell centres entered/left the window at a cell boundary and the hill
     * multiplier jumped 0.95 in one block). The region LABEL is simply the heaviest cell.
     */
    public Context contextAt(int x, int z) {
        int cx = Math.floorDiv(x, CELL_SIZE);
        int cz = Math.floorDiv(z, CELL_SIZE);
        double w1 = -1.0, w2 = -1.0;
        int i1 = 0, i2 = 0;
        double sum = 0.0, tBias = 0.0, hBias = 0.0, hill = 0.0, mountain = 0.0, veg = 0.0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ccx = cx + dx;
                int ccz = cz + dz;
                long h = Seeds.derive(regionSeed, NS + ".jitter", ccx, ccz);
                double qx = (ccx + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 1)) * (double) CELL_SIZE;
                double qz = (ccz + JITTER_MIN + JITTER_SPAN * Seeds.fraction(h, 2)) * (double) CELL_SIZE;
                double dist = Math.hypot(x - qx, z - qz);
                double w = Math.max(0.0, 1.0 - dist / BLEND_RADIUS);
                if (w <= 1.0e-9) continue;
                int idx = cellIndex(ccx, ccz);
                PlanetBiomeRegion r = reachable.get(idx);
                tBias += w * r.temperatureBias();
                hBias += w * r.humidityBias();
                hill += w * r.hillMultiplier();
                mountain += w * r.mountainMultiplier();
                veg += w * r.vegetationBias();
                sum += w;
                if (w > w1) {
                    w2 = w1;
                    i2 = i1;
                    w1 = w;
                    i1 = idx;
                } else if (w > w2) {
                    w2 = w;
                    i2 = idx;
                }
            }
        }
        if (sum <= 1.0e-9) {
            PlanetBiomeRegion r = reachable.get(0);
            return Context.single(r, strengthOf(r));
        }
        // strength: 1 deep inside a region → 0.5 where two cells weigh equally.
        double strength = w2 <= 0.0 ? 1.0 : Math.max(0.5, w1 / (w1 + w2));
        PlanetBiomeRegion primary = reachable.get(i1);
        PlanetBiomeRegion secondary = w2 <= 0.0 ? primary : reachable.get(i2);
        return new Context(primary, secondary, strength, strengthOf(primary),
                hill / sum, mountain / sum, tBias / sum, hBias / sum, veg / sum);
    }

    /**
     * Per-column biome region context (immutable value). Attributes are ALREADY blended
     * across the transition, so consumers just read them.
     *
     * @param region         dominant biome region (heaviest cell)
     * @param secondary      second-heaviest neighbouring region (may equal region)
     * @param strength       1 deep inside the region → 0.5 where two cells weigh equally
     * @param regionWeight   relative weight of the region in the planet's set (0..1)
     */
    public record Context(PlanetBiomeRegion region, PlanetBiomeRegion secondary,
                          double strength, double regionWeight,
                          double hillMultiplier, double mountainMultiplier,
                          double temperatureBias, double humidityBias,
                          double vegetationBias) {

        /** Single-region context (fast path): attributes taken straight from the region. */
        public static Context single(PlanetBiomeRegion region, double regionWeight) {
            if (region == null) {
                return new Context(null, null, 1.0, regionWeight, 1.0, 1.0, 0.0, 0.0, 0.5);
            }
            return new Context(region, region, 1.0, regionWeight,
                    region.hillMultiplier(), region.mountainMultiplier(),
                    region.temperatureBias(), region.humidityBias(), region.vegetationBias());
        }

        /** Blend factor toward the secondary region in [0,0.5]. */
        public double transition() {
            return 1.0 - strength;
        }

        /** Effective LOCAL mountain coverage for the terrain compositor. */
        public double localMountainCoverage(double planetCoverage) {
            return clamp01(planetCoverage * mountainMultiplier);
        }

        /** True when the column sits inside a genuine transition band. */
        public boolean inTransition() {
            return strength < 0.92 && secondary != null && secondary != region;
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** Value equality: two maps of the same planet (same seed + region set) are equal. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BiomeRegionMap other)) return false;
        return regionSeed == other.regionSeed && reachable.equals(other.reachable);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(regionSeed) * 31 + reachable.hashCode();
    }
}
