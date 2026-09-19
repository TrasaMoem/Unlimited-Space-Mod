package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeRegionMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategorySelector;

import java.util.EnumMap;
import java.util.Map;

/**
 * Headless terrain statistics (R20 diagnostics stage).
 *
 * <p>Samples a logical grid (e.g. 512×512 columns at a given step) through the SAME hierarchical
 * {@link TerrainShaper} the chunk generator uses, and reports the numbers needed to judge
 * terrain QUALITY without launching Minecraft: height range, variance, local slope, relief at
 * different scales, plus province / surface-category distributions.
 *
 * <p>Pure domain, deterministic — usable directly in unit tests as a "terrain preview".
 */
public final class TerrainDiagnostics {

    private TerrainDiagnostics() {}

    /** Aggregated terrain statistics for one planet. */
    public record Stats(
            long planetSeed,
            String archetype,
            int samples,
            double minHeight,
            double maxHeight,
            double avgHeight,
            double heightVariance,
            double avgSlope1,          // mean |Δh| between adjacent columns (local roughness)
            double avgSlope64,         // mean |Δh| over 64-block steps (macro roughness)
            double macroRelief,        // maxHeight - minHeight (geography span)
            double localDetailBound,   // max |contribution| of the tiny-detail layer
            Map<GeologicalProvince, Integer> provinceCounts,
            Map<SurfaceCategory, Integer> surfaceCounts,
            // ---------------- R21 quality metrics ----------------
            String identity,           // climate / relief / mountains / theme
            double mountainCoverage,   // measured share of columns inside mountain systems
            double flatlandCoverage,   // measured share of genuine flat plains
            double hillCoverage,       // measured share of rolling-hill country
            double valleyCoverage,     // measured share of valley / basin floor
            double percentile95Slope,  // 95th percentile |Δh| over 64-block steps
            double biomeMedianDiameter,// median biome-region run length (blocks)
            double biomeChangesPer1000,// biome label switches per 1000 blocks along transects
            double materialMedianPatch,// median material-zone patch diameter (blocks)
            double materialSwitchRate) { // material switches per 1000 blocks

        public double rockySurfaceShare() {
            int total = 0, rocky = 0;
            for (Map.Entry<SurfaceCategory, Integer> e : surfaceCounts.entrySet()) {
                total += e.getValue();
                if (e.getKey() == SurfaceCategory.ROCKY) rocky += e.getValue();
            }
            return total == 0 ? 1.0 : (double) rocky / total;
        }

        public String summary() {
            return String.format(java.util.Locale.ROOT,
                    "archetype=%s samples=%d h=[%.0f..%.0f] avg=%.1f var=%.1f slope1=%.2f slope64=%.2f "
                            + "relief=%.0f localBound=%.2f rockyShare=%.2f provinces=%s surfaces=%s",
                    archetype, samples, minHeight, maxHeight, avgHeight, heightVariance,
                    avgSlope1, avgSlope64, macroRelief, localDetailBound, rockySurfaceShare(),
                    provinceCounts, surfaceCounts);
        }

        /** R21: the headless PLANET SUMMARY (quality gate without launching Minecraft). */
        public String planetSummary() {
            return String.format(java.util.Locale.ROOT,
                    "Planet %d%n  %s%n  Terrain: flat %.0f%%  rolling %.0f%%  hills %.0f%%  "
                            + "mountains %.0f%%  valleys %.0f%%%n  Height: min %.0f  avg %.1f  "
                            + "max %.0f  (relief %.0f, slope95 %.2f)%n  Biome: median diameter "
                            + "= %.0f blocks, %.2f changes / 1000 blocks%n  Material: primary "
                            + "patch median = %.0f blocks, %.2f switches / 1000 blocks",
                    planetSeed, identity,
                    flatlandCoverage * 100.0, hillCoverage * 100.0, hillCoverage * 100.0,
                    mountainCoverage * 100.0, valleyCoverage * 100.0,
                    minHeight, avgHeight, maxHeight, macroRelief, percentile95Slope,
                    biomeMedianDiameter, biomeChangesPer1000,
                    materialMedianPatch, materialSwitchRate);
        }
    }

    /**
     * Sample a {@code gridSize × gridSize} logical grid with the given column step through a
     * hierarchical shaper (e.g. gridSize=256, step=8 → 2048-block span).
     */
    public static Stats sample(long planetSeed,
                               PlanetPhysicalProfile profile,
                               GeologicalProvinceMap provinces,
                               TerrainSignature signature,
                               double baseHeight,
                               double terrainAmplitude,
                               int gridSize,
                               int step) {
        return sample(planetSeed, profile, provinces, signature, baseHeight, terrainAmplitude,
                gridSize, step, null, null);
    }

    /** R21 overload with explicit planet-level geography (forced relief archetypes in tests). */
    public static Stats sample(long planetSeed,
                               PlanetPhysicalProfile profile,
                               GeologicalProvinceMap provinces,
                               TerrainSignature signature,
                               double baseHeight,
                               double terrainAmplitude,
                               int gridSize,
                               int step,
                               com.modscreating.unlimitedspace.core.worldgen.relief.PlanetReliefProfile relief,
                               BiomeRegionMap regions) {
        TerrainShaper shaper = TerrainShaper.create(null, planetSeed, profile, provinces,
                signature, baseHeight, terrainAmplitude, relief, regions);
        com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetype climateArch =
                com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector
                        .create(planetSeed, profile);
        com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme theme =
                com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme
                        .select(climateArch, planetSeed);
        String identity = String.format(java.util.Locale.ROOT,
                "climate=%s relief=%s mountains=%.2f theme=%s",
                climateArch, shaper.relief().label(), shaper.relief().mountainCoverage(),
                theme.label());
        long materialSeed = planetSeed;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0.0, sumSq = 0.0;
        double slope1 = 0.0, slope64 = 0.0;
        long slope1N = 0, slope64N = 0;
        Map<GeologicalProvince, Integer> provinces1 = new EnumMap<>(GeologicalProvince.class);
        Map<SurfaceCategory, Integer> surfaces = new EnumMap<>(SurfaceCategory.class);

        int n = 0;
        int mountains = 0, flats = 0, hillsN = 0, valleys = 0;
        java.util.List<Integer> slopeSamples = new java.util.ArrayList<>();
        java.util.List<Integer> biomeRuns = new java.util.ArrayList<>();
        java.util.List<Integer> materialRuns = new java.util.ArrayList<>();
        long biomeSwitches = 0, materialSwitches = 0;
        long transectBlocks = 0;
        double spanBlocks = 6.5 * shaper.amplitudeBound();
        for (int i = 0; i < gridSize; i++) {
            int x = i * step;
            TerrainSample prev = shaper.sample(x, 0);
            int prevBiome = biomeLabel(shaper, x, 0);
            int prevMaterial = materialZone(materialSeed, x, 0);
            int biomeRun = 1, materialRun = 1;
            for (int j = 0; j < gridSize; j++) {
                int z = j * step;
                TerrainSample cur = prev;
                if (j > 0) cur = shaper.sample(x, z);
                double h = cur.height();
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                sumSq += h * h;
                n++;
                provinces1.merge(provinces != null
                        ? provinces.provinceAt(x, z, cur.elevation01(shaper.minBound(), shaper.maxBound()))
                        : GeologicalProvince.PLAINS, 1, Integer::sum);
                surfaces.merge(SurfaceCategorySelector.classify(profile,
                        shaper.archetype().primary(),
                        provinces != null
                                ? provinces.provinceAt(x, z, cur.elevation01(shaper.minBound(), shaper.maxBound()))
                                : GeologicalProvince.PLAINS,
                        climateArch, shaper.relief() == null ? null : shaper.relief().archetype(),
                        cur.elevation01(shaper.minBound(), shaper.maxBound())), 1, Integer::sum);
                if (j > 0) {
                    slope1 += Math.abs(cur.height() - prev.height());
                    slope1N++;
                }
                // R21 relief composition: mountains / foothills+hills / valleys / flats
                if (cur.mountainEnvelope() > 0.5) {
                    mountains++;
                } else if (cur.foothillEnvelope() > 0.45) {
                    hillsN++;
                } else if (cur.macroElevation() < -0.25 * spanBlocks) {
                    valleys++;
                } else if (Math.abs(cur.macroElevation()) < 0.08 * spanBlocks) {
                    flats++;
                } else {
                    hillsN++;
                }
                prev = cur;

                // biome / material run-length statistics along this row
                int biome = biomeLabel(shaper, x, z);
                int material = materialZone(materialSeed, x, z);
                if (biome != prevBiome) {
                    biomeRuns.add(biomeRun * step);
                    biomeSwitches++;
                    biomeRun = 1;
                    prevBiome = biome;
                } else {
                    biomeRun++;
                }
                if (material != prevMaterial) {
                    materialRuns.add(materialRun * step);
                    materialSwitches++;
                    materialRun = 1;
                    prevMaterial = material;
                } else {
                    materialRun++;
                }
                transectBlocks += step;
            }
            biomeRuns.add(biomeRun * step);
            materialRuns.add(materialRun * step);
        }
        // macro slope: every 64 blocks along a diagonal transect
        for (int i = step; i < gridSize * step; i += 64) {
            double d = Math.abs(shaper.surfaceHeight(i, i) - shaper.surfaceHeight(i - 64, i - 64));
            slope64 += d;
            slope64N++;
            slopeSamples.add((int) Math.round(d));
        }

        double avg = sum / Math.max(1, n);
        double variance = Math.max(0.0, sumSq / Math.max(1, n) - avg * avg);
        return new Stats(planetSeed,
                shaper.archetype().summary(),
                n,
                min, max, avg, variance,
                slope1 / Math.max(1, slope1N),
                slope64 / Math.max(1, slope64N),
                max - min,
                shaper.sample(0, 0).localDetailAmplitude() * 2.0,
                provinces1, surfaces,
                identity,
                mountains / (double) n,
                flats / (double) n,
                hillsN / (double) n,
                valleys / (double) n,
                percentile(slopeSamples, 0.95),
                median(biomeRuns),
                transectBlocks <= 0 ? 0.0 : biomeSwitches * 1000.0 / transectBlocks,
                median(materialRuns),
                transectBlocks <= 0 ? 0.0 : materialSwitches * 1000.0 / transectBlocks);
    }

    /** Stable biome label for run-length statistics (primary region identity). */
    private static int biomeLabel(TerrainShaper shaper, int x, int z) {
        BiomeRegionMap.Context ctx = shaper.regions() == null
                ? null : shaper.regions().contextAt(x, z);
        if (ctx == null || ctx.region() == null) return 0;
        // The metric measures how LARGE a biome region is, so the identity is the dominant
        // region only — including the transition flag would chop runs into short pieces.
        return ctx.region().ordinal();
    }

    /** Stable material-zone label for run-length statistics. */
    private static int materialZone(long materialSeed, int x, int z) {
        return com.modscreating.unlimitedspace.core.worldgen.materials.MaterialZoneMap
                .zoneAt(materialSeed, x, z);
    }

    /** Median of a list of ints (pure). */
    private static double median(java.util.List<Integer> values) {
        if (values == null || values.isEmpty()) return 0.0;
        java.util.List<Integer> copy = new java.util.ArrayList<>(values);
        java.util.Collections.sort(copy);
        int mid = copy.size() / 2;
        return copy.size() % 2 == 1 ? copy.get(mid)
                : 0.5 * (copy.get(mid - 1) + copy.get(mid));
    }

    /** Nearest-rank percentile of a list of ints (pure). */
    private static double percentile(java.util.List<Integer> values, double p) {
        if (values == null || values.isEmpty()) return 0.0;
        java.util.List<Integer> copy = new java.util.ArrayList<>(values);
        java.util.Collections.sort(copy);
        int idx = (int) Math.min(copy.size() - 1,
                Math.max(0, Math.round(p * (copy.size() - 1))));
        return copy.get(idx);
    }
}
