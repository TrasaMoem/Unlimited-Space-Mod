package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroGeography;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geography.ProvinceArchetype;
import com.modscreating.unlimitedspace.core.worldgen.geography.GeographyMetrics;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialRoleSelector;
import com.modscreating.unlimitedspace.core.worldgen.profile.GravityClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategorySelector;

import java.util.EnumMap;
import java.util.Locale;
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

    /** PHASE 10: the canonical physical facts of the sampled planet (real Kelvin, not normalized). */
    public record Physics(double surfaceKelvin, TemperatureBand band, PressureClass pressureClass,
                          GravityClass gravityClass, double waterAvailability, double humidity) {

        /** One-line physics row for the planet summary. */
        public String line() {
            return String.format(Locale.ROOT,
                    "T = %s  | pressure %s | gravity %s | water availability %.2f",
                    StellarThermalModel.temperatureText(surfaceKelvin), pressureClass, gravityClass,
                    waterAvailability);
        }
    }

    /**
     * PHASE 10: the planet's landform composition and the MEASURED cut coverage of the grid.
     *
     * @param cutCoverage     share of columns with a landform cut (exposure &gt; 0.15)
     * @param deepCutCoverage share of columns with a deep cut (exposure &gt; 0.50)
     */
    public record LandformStats(LandformBudget budget, double cutCoverage, double deepCutCoverage) {

        public LandformStats {
            if (budget == null) budget = LandformBudget.NONE;
        }

        public String summary() {
            return String.format(Locale.ROOT, "%s | cuts on %.1f%% of columns (deep %.1f%%)",
                    budget.summary(), cutCoverage * 100.0, deepCutCoverage * 100.0);
        }
    }

    /** PHASE 10: the measured material-role distribution (the contextual decision chain). */
    public record MaterialShares(double dominant, double secondary, double geologic, double accent) {}

    /**
     * PHASE 10: the measured water-phase distribution over the sampled grid.
     *
     * <p>{@code MIXED} (near-freezing ice with possible local liquid) counts as SOLID — it is never
     * standing liquid water. The hard invariant (PHASE 3.1 / 10.7): a world whose thermal baseline
     * is below the freeze point reports {@code liquid == 0}.
     */
    public record WaterPhases(double solid, double liquid, double vapor, double none,
                              WaterPhaseModel.Phase globalPhase) {

        /** Shares relative to the counted columns (all-zero when nothing was sampled). */
        public static WaterPhases of(Map<WaterPhaseModel.Phase, Integer> counts,
                                     WaterPhaseModel.Phase globalPhase) {
            int total = 0;
            for (int v : counts.values()) total += v;
            if (total == 0) return new WaterPhases(0.0, 0.0, 0.0, 0.0, globalPhase);
            double solid = counts.getOrDefault(WaterPhaseModel.Phase.SOLID, 0)
                    + counts.getOrDefault(WaterPhaseModel.Phase.MIXED, 0);
            return new WaterPhases(
                    solid / total,
                    counts.getOrDefault(WaterPhaseModel.Phase.LIQUID, 0) / (double) total,
                    counts.getOrDefault(WaterPhaseModel.Phase.VAPOR, 0) / (double) total,
                    counts.getOrDefault(WaterPhaseModel.Phase.NONE, 0) / (double) total,
                    globalPhase);
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "global %s  | measured on grid: solid %.0f%%  liquid %.0f%%  vapour %.0f%%  none %.0f%%",
                    globalPhase == null ? "?" : globalPhase.displayName(),
                    solid * 100.0, liquid * 100.0, vapor * 100.0, none * 100.0);
        }
    }

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
            double materialSwitchRate, // material switches per 1000 blocks
            // ---------------- R22 coherence metrics ----------------
            Map<ProvinceArchetype, Integer>
                    regionCounts,   // area share of each macro biome family (diagnostic)
            double dominantRegionShare, // share of the largest macro biome family
            double dominantZoneShare,  // share of the dominant material zone (zone 0)
            // ---------------- PHASE 10 diagnostics (physics / landforms / water / material) ----------------
            Physics physics,               // canonical physical facts (real Kelvin from the profile)
            LandformStats landforms,       // landform budget + measured cut coverage
            MaterialShares materialShares, // measured contextual role distribution
            WaterPhases waterPhases) {     // measured local water-phase distribution

        public Stats {
            if (landforms == null) landforms = new LandformStats(LandformBudget.NONE, 0.0, 0.0);
            if (materialShares == null) materialShares = new MaterialShares(1.0, 0.0, 0.0, 0.0);
            if (waterPhases == null) waterPhases = WaterPhases.of(Map.of(), null);
        }

        /** Area share of one macro biome family in [0,1]. */
        public double regionShare(ProvinceArchetype r) {
            int total = 0;
            for (int v : regionCounts.values()) total += v;
            return total == 0 ? 1.0
                    : regionCounts.getOrDefault(r, 0) / (double) total;
        }

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

        /** R21 + PHASE 10: the headless PLANET SUMMARY (quality gate without launching Minecraft). */
        public String planetSummary() {
            StringBuilder biomes = new StringBuilder();
            regionCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> biomes.append(String.format(Locale.ROOT, "%s %.0f%%  ",
                            e.getKey(), 100.0 * e.getValue() / Math.max(1, samples))));
            return String.format(Locale.ROOT,
                    "Planet %d  [%s]%n"
                            + "  PHYSICS:   %s%n"
                            + "  CLIMATE:   band %s  humidity %.2f  aridity %.2f%n"
                            + "  RELIEF:    flat %.0f%%  hills %.0f%%  mountains %.0f%%  valleys %.0f%%%n"
                            + "  HEIGHT:    min %.0f  avg %.1f  max %.0f  relief %.0f  "
                            + "slope64 %.2f  slope95 %.2f%n"
                            + "  BIOMES:    %d families  dominant %.0f%%  median diameter %.0f blocks  "
                            + "%.2f changes / 1000 blocks%n"
                            + "  BIOME MAP: %s%n"
                            + "  MATERIAL:  dominant %.0f%%  secondary %.0f%%  geologic %.0f%%  "
                            + "accent %.0f%%  patch median %.0f blocks  %.2f switches / 1000 blocks%n"
                            + "  LANDFORMS: %s%n"
                            + "  WATER:     %s",
                    planetSeed, identity,
                    physics == null ? "n/a (no physical profile)"
                            : physics.line(),
                    physics == null ? "n/a" : physics.band(),
                    physics == null ? 0.0 : physics.humidity(),
                    physics == null ? 0.0 : 1.0 - physics.humidity(),
                    flatlandCoverage * 100.0, hillCoverage * 100.0,
                    mountainCoverage * 100.0, valleyCoverage * 100.0,
                    minHeight, avgHeight, maxHeight, macroRelief, avgSlope64, percentile95Slope,
                    regionCounts.size(), dominantRegionShare * 100.0, biomeMedianDiameter,
                    biomeChangesPer1000, biomes.toString().trim(),
                    materialShares.dominant() * 100.0, materialShares.secondary() * 100.0,
                    materialShares.geologic() * 100.0, materialShares.accent() * 100.0,
                    materialMedianPatch, materialSwitchRate,
                    landforms.summary(), waterPhases.summary());
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
                               MacroGeography regions) {
        // ACT 4: diagnostics carry the climate subsystem so the shaper's region context is the
        // SAME climate-aware authority the runtime chunk generator executes — the terrain heights
        // reported here are exactly the runtime ones.
        com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile climate =
                profile == null ? null
                        : com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile
                                .create(planetSeed, profile);
        TerrainShaper shaper = TerrainShaper.create(null, planetSeed, profile, provinces,
                signature, baseHeight, terrainAmplitude, relief, regions, climate);
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
        // The runtime material subsystem seed (PlanetPropertyGenerator: Seeds.subsystem(p,"materials")) —
        // the diagnostics must measure the SAME decision chain the chunk generator executes.
        long materialSeed = com.modscreating.unlimitedspace.core.seed.Seeds.subsystem(planetSeed, "materials");
        // PHASE 10: the canonical physical facts + the models the summary needs (one per planet —
        // never per column).
        double surfaceK = profile == null ? 0.0
                : StellarThermalModel.denormalizeKelvin(profile.temperature01());
        Map<WaterPhaseModel.Phase, Integer> waterCounts = new EnumMap<>(WaterPhaseModel.Phase.class);
        int[] zoneCounts = new int[4];
        int cutColumns = 0, deepCutColumns = 0;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0.0, sumSq = 0.0;
        double slope1 = 0.0, slope64 = 0.0;
        long slope1N = 0, slope64N = 0;
        Map<GeologicalProvince, Integer> provinces1 = new EnumMap<>(GeologicalProvince.class);
        Map<SurfaceCategory, Integer> surfaces = new EnumMap<>(SurfaceCategory.class);
        Map<ProvinceArchetype, Integer>
                regionCounts = new EnumMap<>(ProvinceArchetype.class);
        int dominantZone = 0;

        MacroSample macroOut = new MacroSample();


        double[] provinceWeightScratch = provinces == null


                ? new double[1] : provinces.newScratch();

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
            int prevBiome = biomeLabel(shaper, macroOut, x, 0);
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
                double elev01 = cur.elevation01(shaper.minBound(), shaper.maxBound());
                // ACT 3 (P1/P4): diagnostics measure the SAME authority chain as the runtime:
                // macro region (climate-aware) -> medium (900) region-aware province -> surface
                // (phase-gated) -> material role with the macro boundary context.
                if (regions != null) {
                    regions.sample(x, z, macroOut);
                } else {
                    shaper.geography().sample(x, z, macroOut);
                }
                GeologicalProvince micro = provinces != null
                        ? provinces.provinceAt(x, z)
                        : GeologicalProvince.PLAINS;

                // The CONTINUOUS province share drives the material role, so a role can only fade.
                double columnConfidence = provinces == null ? 1.0

                        : provinces.shareAt(x, z, provinceWeightScratch, micro);
                provinces1.merge(micro, 1, Integer::sum);
                SurfaceCategory surfCat = SurfaceCategorySelector.classify(profile,
                        shaper.archetype().primary(), micro,
                        climateArch, shaper.relief() == null ? null : shaper.relief().archetype(),
                        elev01,
                        // ACT 3 (P3.2): the canonical phase gates liquid-dependent categories.
                        WaterPhaseModel.geothermalPocketPhase(
                                WaterPhaseModel.ofProfile(profile), micro,
                                profile == null ? 0.0 : profile.geothermalFlux()));
                surfaces.merge(surfCat, 1, Integer::sum);
                // PHASE 10: landform exposure, the contextual material slot and the LOCAL water
                // phase — the same decision chain the runtime worldgen executes.
                double exposure = shaper.landformExposure(x, z);
                if (exposure > 0.15) cutColumns++;
                if (exposure > 0.50) deepCutColumns++;
                zoneCounts[PlanetMaterialRoleSelector.zoneAt(theme,
                        columnConfidence, surfCat, materialSeed, x, z,
                        1.0 - macroOut.transitionWeight)]++;
                if (climate != null && profile != null) {
                    double localK = WaterPhaseModel.localSurfaceKelvin(surfaceK,
                            climate.temperatureAt(x, z, elev01), elev01);
                    waterCounts.merge(WaterPhaseModel.surfacePhase(localK,
                            profile.pressureClass(), profile.waterAbundance()), 1, Integer::sum);
                }
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
                int biome = biomeLabel(shaper, macroOut, x, z);
                int material = materialZone(materialSeed, x, z);
                if (material == 0) dominantZone++;
                regionCounts.merge(biome > 0
                        ? ProvinceArchetype.VALUES[biome]
                        : ProvinceArchetype.OPEN_PLAINS,
                        1, Integer::sum);
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
                transectBlocks <= 0 ? 0.0 : materialSwitches * 1000.0 / transectBlocks,
                regionCounts,
                regionCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1)
                        / (double) Math.max(1, n),
                dominantZone / (double) Math.max(1, n),
                profile == null ? null : new Physics(surfaceK, profile.temperatureBand(),
                        profile.pressureClass(), profile.gravityClass(),
                        profile.waterAbundance(), profile.humidity()),
                new LandformStats(LandformBudget.of(profile),
                        cutColumns / (double) Math.max(1, n),
                        deepCutColumns / (double) Math.max(1, n)),
                new MaterialShares(zoneCounts[0] / (double) Math.max(1, n),
                        zoneCounts[1] / (double) Math.max(1, n),
                        zoneCounts[2] / (double) Math.max(1, n),
                        zoneCounts[3] / (double) Math.max(1, n)),
                WaterPhases.of(waterCounts, WaterPhaseModel.ofProfile(profile)));
    }

    /** Stable macro label for run-length statistics (the primary archetype identity). */
    private static int biomeLabel(TerrainShaper shaper, MacroSample out, int x, int z) {
        if (shaper.geography() == null) return 0;
        shaper.geography().sample(x, z, out);
        ProvinceArchetype p = out.province;
        return p == null ? 0 : p.ordinal();
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

    // ============================================================================
    // ACT 4 EXTENDED DIAGNOSTICS — a separate report so the existing Stats record and its
    // consumers (R21/R22/R23 tests) are completely untouched. All numbers are measured through
    // the SAME authoritative shaper the runtime uses (climate-aware region context).
    // ============================================================================

    /**
     * ACT 4 terrain report. Adds the metrics the ACT 4 acceptance criteria need: height
     * percentiles, local/macro slope with worst-case deltas, 48-block coherence, 64-block
     * macro difference, curvature/frequency proxies, basin depth, ridge prominence, landform
     * coverage by identity and landform strength.
     */
    public record Act4Report(
            int samples,
            double p50Height, double p90Height, double p95Height, double p99Height,
            double minHeight, double maxHeight, double macroRelief,
            double avgSlope1, double worstAdjacentDelta, double p95Slope64,
            double avg48BlockCoherence, double macroDiff64,
            double max64OverRelief, double curvatureProxy,
            double basinDepth, double ridgeProminence, double frequencyProxy,
            double landformCoverage, double meanLandformStrength,
            Map<LandformIdentity, Integer> landformCoverageByType) {

        public String summary() {
            StringBuilder lf = new StringBuilder();
            Map<LandformIdentity, Integer> by = landformCoverageByType;
            for (LandformIdentity id : LandformIdentity.VALUES) {
                int v = by.getOrDefault(id, 0);
                if (v > 0) lf.append(String.format(Locale.ROOT, "%s %.0f%%  ", id,
                        100.0 * v / Math.max(1, samples)));
            }
            return String.format(Locale.ROOT,
                    "ACT4 samples=%d h p50=%.0f p90=%.0f p95=%.0f p99=%.0f [%.0f..%.0f] rel=%.0f "
                            + "slope1=%.2f worst=%.0f slope95=%.2f corr48=%.2f macro64=%.2f "
                            + "max64/rel=%.2f curv=%.2f basin=%.2f ridge=%.2f freq=%.2f "
                            + "lfCover=%.2f lfStrength=%.2f%n  LANDFORMS: %s",
                    samples, p50Height, p90Height, p95Height, p99Height, minHeight, maxHeight,
                    macroRelief, avgSlope1, worstAdjacentDelta, p95Slope64, avg48BlockCoherence,
                    macroDiff64, max64OverRelief, curvatureProxy, basinDepth, ridgeProminence,
                    frequencyProxy, landformCoverage, meanLandformStrength, lf.toString().trim());
        }
    }

    /**
     * ACT 4 report: samples a {@code gridSize x gridSize} grid through the climate-aware shaper
     * and returns the full ACT 4 metric set. Pure, deterministic, headless.
     */
    public static Act4Report act4(long planetSeed,
                                  PlanetPhysicalProfile profile,
                                  GeologicalProvinceMap provinces,
                                  TerrainSignature signature,
                                  double baseHeight, double terrainAmplitude,
                                  int gridSize, int step) {
        com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile climate =
                profile == null ? null
                        : com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile
                                .create(planetSeed, profile);
        TerrainShaper shaper = TerrainShaper.create(null, planetSeed, profile, provinces,
                signature, baseHeight, terrainAmplitude, null, null, climate);

        java.util.List<Integer> heights = new java.util.ArrayList<>();
        java.util.List<Integer> slope1 = new java.util.ArrayList<>();
        java.util.List<Integer> slope64 = new java.util.ArrayList<>();
        java.util.List<Integer> slope48 = new java.util.ArrayList<>();
        java.util.List<Integer> slope8 = new java.util.ArrayList<>();
        java.util.List<Integer> curvature = new java.util.ArrayList<>();
        Map<LandformIdentity, Integer> lfByType = new EnumMap<>(LandformIdentity.class);
        int worst = 0;
        int n = 0;
        double basinSum = 0.0, basinN = 0, ridgeSum = 0.0, ridgeN = 0;
        double lfStrengthSum = 0.0, lfCover = 0;

        for (int i = 0; i < gridSize; i++) {
            int x = i * step;
            int prevH = Integer.MIN_VALUE;
            int prevPrevH = Integer.MIN_VALUE;
            for (int j = 0; j < gridSize; j++) {
                int z = j * step;
                int h = shaper.surfaceHeight(x, z);
                heights.add(h);
                if (prevH != Integer.MIN_VALUE) {
                    int d = Math.abs(h - prevH);
                    slope1.add(d);
                    worst = Math.max(worst, d);
                    // 48-block coherence along the row.
                    if (z + 48 < gridSize * step) {
                        slope48.add(Math.abs(h - shaper.surfaceHeight(x, z + 48)));
                    }
                    // 64-block macro difference along the diagonal.
                    if (x >= 64 && z >= 64) {
                        slope64.add(Math.abs(h - shaper.surfaceHeight(x - 64, z - 64)));
                    }
                    // 8-block micro slope (terrain frequency / scale proxy).
                    if (z + 8 < gridSize * step) {
                        slope8.add(Math.abs(h - shaper.surfaceHeight(x, z + 8)));
                    }
                    // curvature proxy: |2h - h_prev - h_prevprev| over the micro step.
                    if (prevPrevH != Integer.MIN_VALUE) {
                        curvature.add(Math.abs(2 * h - prevH - prevPrevH));
                    }
                }
                prevPrevH = prevH;
                prevH = h;

                LandformIdentity id = shaper.landformIdentity(x, z);
                lfByType.merge(id, 1, Integer::sum);
                double lfs = shaper.landformStrength(x, z);
                lfStrengthSum += lfs;
                if (id != LandformIdentity.NONE) lfCover++;
                // basin / ridge proxies from the fields the shaper itself uses.
                double basin = GlobalTerrainFields.basinField(
                        com.modscreating.unlimitedspace.core.seed.Seeds
                                .derive(planetSeed, "us.terrain.fields") + 0xDL, x, z);
                if (basin > 0.58) {
                    basinSum += basin - 0.58;
                    basinN++;
                }
                TerrainSample s = shaper.sample(x, z);
                if (s.ridge() > 0.6) {
                    ridgeSum += s.ridge();
                    ridgeN++;
                }
                n++;
            }
        }
        int hMax = java.util.Collections.max(heights);
        int hMin = java.util.Collections.min(heights);
        double relief = hMax - hMin;
        return new Act4Report(n,
                percentile(heights, 0.50), percentile(heights, 0.90),
                percentile(heights, 0.95), percentile(heights, 0.99),
                hMin, hMax, relief,
                mean(slope1), worst, percentile(slope64, 0.95),
                mean(slope48), mean(slope64),
                relief <= 0.0 ? 0.0 : mean(slope64) / relief,
                mean(curvature),
                basinN <= 0.0 ? 0.0 : basinSum / basinN,
                ridgeN <= 0.0 ? 0.0 : ridgeSum / ridgeN,
                mean(slope8),
                lfCover / (double) Math.max(1, n),
                lfStrengthSum / (double) Math.max(1, n),
                lfByType);
    }

    /** Mean of a list of ints (0 when empty). */
    private static double mean(java.util.List<Integer> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double s = 0.0;
        for (int v : values) s += v;
        return s / values.size();
    }
}
