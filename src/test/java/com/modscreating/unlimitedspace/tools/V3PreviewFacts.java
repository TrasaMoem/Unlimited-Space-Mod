package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WORLDGEN V3.1 — the machine-readable distributions of one V3 preview pass (Task H).
 *
 * <p>Every number the ACT requires is computed from the SAME {@link
 * com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample} stream the runtime
 * classifies, so the facts and the preview images can never disagree.
 *
 * <p>All accumulators are primitive arrays held by the preview CALLER. Nothing here lives on a
 * runtime object, so adding a metric costs the preview memory and the production hot path nothing.
 */
public final class V3PreviewFacts {

    private V3PreviewFacts() {}

    /** Quantile helper over an unsorted sample: linear interpolation, no caller-visible mutation. */
    public static double quantile(double[] sample, double q) {
        if (sample.length == 0) return 0.0;
        double[] s = sample.clone();
        Arrays.sort(s);
        double pos = q * (s.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(s.length - 1, lo + 1);
        double f = pos - lo;
        return s[lo] + (s[hi] - s[lo]) * f;
    }

    /** The mutable primitive accumulator of one preview pass. */
    public static final class Accumulator {
        public final double[] heights = new double[8192];
        public final double[] elevations01 = new double[8192];
        public final double[] slopes = new double[8192];
        public final double[] margins = new double[8192];
        public double riverShare, lakeShare, mountainShare, valleyShare, basinShare;
        public double positiveRelief, negativeRelief;
        public double maxFlowAccumulation, flowAccumulationSum;
        public double rockShare, organicShare, sedimentShare, wetShare;
        public double glacialShare, volcanicShare, duneShare, alpineShare;
        public double lavaEligibleShare, waterProximityShare;
        public int count;
        public int hydrologyContinuityFailures;

        // ---- ACT STAGE 8.1: the ONE-authority consistency block.
        //
        // These are DIAGNOSTIC OUTPUT, not a second planet model. Each is decided by the SAME
        // authority the runtime and the UI already consult, so the block reports agreement instead
        // of re-deriving a value that could disagree:
        //   * `dominantCategory` is the column's own elected SurfaceCategory (already sampled),
        //   * `temperatureAgrees` / `gravityAgrees` / `waterPhaseAgrees` / `atmosphereAgrees` are
        //     resolved from the character the preview already holds, using the canonical
        //     TemperatureBand / WaterPhaseModel / gravity / atmosphere authorities.
        private final Map<SurfaceCategory, Integer> categoryCounts = new java.util.EnumMap<>(
                SurfaceCategory.class);
        private int temperatureMismatch;
        private int gravityMismatch;
        private int waterPhaseMismatch;
        private int atmosphereMismatch;
        private int temperatureSamples;
        private int gravitySamples;
        private int waterPhaseSamples;
        private int atmosphereSamples;

        /** The surface category a plurality of columns elected (a label, never a selector). */
        public String dominantCategory() {
            String best = "?";
            int bestCount = -1;
            for (Map.Entry<SurfaceCategory, Integer> e : categoryCounts.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    best = e.getKey().name();
                }
            }
            return best;
        }

        /** True when no sampled column disagreed with the canonical temperature band. */
        public boolean temperatureAgrees() {
            return temperatureSamples == 0 || temperatureMismatch == 0;
        }

        /** True when no sampled column disagreed with the canonical gravity scale. */
        public boolean gravityAgrees() {
            return gravitySamples == 0 || gravityMismatch == 0;
        }

        /** True when no sampled column disagreed with the canonical water phase. */
        public boolean waterPhaseAgrees() {
            return waterPhaseSamples == 0 || waterPhaseMismatch == 0;
        }

        /** True when no sampled column disagreed with the canonical atmosphere band. */
        public boolean atmosphereAgrees() {
            return atmosphereSamples == 0 || atmosphereMismatch == 0;
        }

        /** Record one column's elected surface category for the plurality readout. */
        public void recordCategory(WorldgenColumnSample c) {
            if (c.surfaceCategory != null) {
                categoryCounts.merge(c.surfaceCategory, 1, Integer::sum);
            }
        }

        /**
         * Record one UI-vs-worldgen disagreement verdict. Each flag compares the value the UI would
         * display against the value worldgen would use, both derived from the SAME planet profile
         * by the canonical authority - so a {@code false} here is a real divergence, not a
         * recomputation that could drift.
         */
        public void recordAgreement(boolean temperature, boolean gravity, boolean waterPhase,
                                    boolean atmosphere) {
            temperatureSamples++;
            if (!temperature) temperatureMismatch++;
            gravitySamples++;
            if (!gravity) gravityMismatch++;
            waterPhaseSamples++;
            if (!waterPhase) waterPhaseMismatch++;
            atmosphereSamples++;
            if (!atmosphere) atmosphereMismatch++;
        }
    }

    /**
     * The finished, immutable fact set of one preview pass.
     *
     * <p>Field names map 1:1 onto the required report lines, so {@link #render()} is a
     * machine-readable {@code key : value} document.
     */
    public record Facts(
            String name,
            PlanetSurfaceMode mode,
            int columns,
            double heightMin, double heightP10, double heightMedian, double heightP90, double heightMax,
            double elevation01Min, double elevation01Median, double elevation01Max,
            double slopeMedian, double slopeP90,
            double positiveReliefShare, double negativeReliefShare,
            double mountainShare, double valleyShare, double basinShare,
            double riverShare, double lakeShare,
            double maxFlowAccumulation, double meanFlowAccumulation,
            int hydrologyContinuityFailures,
            double marginP10, double marginMedian, double marginP90,
            Map<Integer, Double> biomeShares,
            Map<Integer, Double> subBiomeShares,
            Map<Integer, Double> materialShares,
            Map<Integer, Double> surfaceCategoryShares,
            Map<Integer, Double> provinceShares,
            double provinceAgreement,
            double meanBiomeVsProvinceDelta,
            double lavaEligibleShare, double actualLavaShare, double maxLavaBodySize,
            int geothermalExceptions,
            double duneWeight, double glacialWeight, double volcanicWeight, double alpineWeight,
            double rockWeight, double organicWeight, double sedimentWeight, double wetWeight,
            boolean hasSolidSurface,
            int terrainQueryCount, int hydrologyQueryCount, int lavaQueryCount,
            int biomeTransitionsInsideProvince, int biomeTransitionsAcrossProvince,
            int biomeTransitionsNearMacroBoundary,
            // ---- ACT STAGE 8.1: ONE surface authority, proven in the diagnostic output ----
            String uiSurface, String worldgenSurfaceMode, String worldgenSurfaceLabel,
            String worldgenDominantCategory,
            boolean temperatureAgrees, boolean gravityAgrees, boolean waterPhaseAgrees,
            boolean atmosphereAgrees) {

        /** The documented fact document. */
        public List<String> render() {
            List<String> out = new ArrayList<>();
            out.add("# WORLDGEN V3.1 preview facts");
            out.add(String.format(Locale.ROOT, "planet                    : %s", name));
            out.add(String.format(Locale.ROOT, "surfaceMode               : %s", mode));
            out.add(String.format(Locale.ROOT, "hasSolidSurface           : %s", hasSolidSurface));
            out.add(String.format(Locale.ROOT, "columns                   : %d", columns));
            // ---- ACT STAGE 8.1: the ONE-authority consistency block. These are DIAGNOSTIC OUTPUT:
            // every value is read from the existing authorities (PlanetSurfaceMode.forProfile /
            // displayLabel, WorldStatusText, WaterPhaseModel, StellarThermalModel), so nothing here
            // recomputes a planet and no planet calculation is duplicated.
            out.add("UI / WORLDGEN CONSISTENCY");
            out.add(String.format(Locale.ROOT, "uiSurface                 : %s", uiSurface));
            out.add(String.format(Locale.ROOT, "worldgenSurfaceMode       : %s", worldgenSurfaceMode));
            out.add(String.format(Locale.ROOT, "worldgenDominantCategory  : %s", worldgenDominantCategory));
            // ACT STAGE 8.1: the agreement is between the two READINGS of the SAME authority — the
            // human label the UI shows and the canonical label the worldgen authority publishes.
            // Comparing a label against an enum NAME would always read false and would say nothing
            // about divergence, so both sides are canonical labels and the enum identity is
            // reported separately above as `worldgenSurfaceMode`.
            out.add(String.format(Locale.ROOT, "worldgenSurfaceLabel     : %s", worldgenSurfaceLabel));
            out.add(String.format(Locale.ROOT, "surfaceAgrees             : %s",
                    uiSurface.equals(worldgenSurfaceLabel)));
            out.add(String.format(Locale.ROOT, "temperatureAgrees         : %s", temperatureAgrees));
            out.add(String.format(Locale.ROOT, "gravityAgrees             : %s", gravityAgrees));
            out.add(String.format(Locale.ROOT, "waterPhaseAgrees          : %s", waterPhaseAgrees));
            out.add(String.format(Locale.ROOT, "atmosphereAgrees          : %s", atmosphereAgrees));
            out.add("PLANET CHARACTER");
            out.add(f("duneShare", duneWeight));
            out.add(f("glacialShare", glacialWeight));
            out.add(f("volcanicShare", volcanicWeight));
            out.add(f("alpineShare", alpineWeight));
            out.add(f("rockShare", rockWeight));
            out.add(f("organicShare", organicWeight));
            out.add(f("sedimentShare", sedimentWeight));
            out.add(f("wetShare", wetWeight));
            out.add(f("lavaEligibilityShare", lavaEligibleShare));
            out.add("TERRAIN");
            out.add(f("heightMin", heightMin));
            out.add(f("heightP10", heightP10));
            out.add(f("heightMedian", heightMedian));
            out.add(f("heightP90", heightP90));
            out.add(f("heightMax", heightMax));
            out.add(f("elevation01Min", elevation01Min));
            out.add(f("elevation01Median", elevation01Median));
            out.add(f("elevation01Max", elevation01Max));
            out.add(f("slopeMedian", slopeMedian));
            out.add(f("slopeP90", slopeP90));
            out.add(f("positiveReliefShare", positiveReliefShare));
            out.add(f("negativeReliefShare", negativeReliefShare));
            out.add(f("mountainShare", mountainShare));
            out.add(f("valleyShare", valleyShare));
            out.add(f("basinShare", basinShare));
            out.add("HYDROLOGY");
            out.add(f("riverShare", riverShare));
            out.add(f("lakeShare", lakeShare));
            out.add(f("maxFlowAccumulation", maxFlowAccumulation));
            out.add(f("meanFlowAccumulation", meanFlowAccumulation));
            out.add(String.format(Locale.ROOT,
                    "hydrologyContinuityFailures : %d", hydrologyContinuityFailures));
            out.add("BIOMES");
            out.add(f("scoreMarginP10", marginP10));
            out.add(f("scoreMarginMedian", marginMedian));
            out.add(f("scoreMarginP90", marginP90));
            for (Map.Entry<Integer, Double> e : biomeShares.entrySet()) {
                out.add(String.format(Locale.ROOT, "biomeShare[%s] = %.4f",
                        biomeName(e.getKey()), e.getValue()));
            }
            for (Map.Entry<Integer, Double> e : subBiomeShares.entrySet()) {
                out.add(String.format(Locale.ROOT, "subBiomeShare[%s] = %.4f",
                        subBiomeName(e.getKey()), e.getValue()));
            }
            out.add("MATERIAL");
            for (Map.Entry<Integer, Double> e : materialShares.entrySet()) {
                out.add(String.format(Locale.ROOT, "materialShare[%s] = %.4f",
                        roleName(e.getKey()), e.getValue()));
            }
            for (Map.Entry<Integer, Double> e : surfaceCategoryShares.entrySet()) {
                out.add(String.format(Locale.ROOT, "surfaceCategoryShare[%s] = %.4f",
                        categoryName(e.getKey()), e.getValue()));
            }
            out.add("MACRO");
            for (Map.Entry<Integer, Double> e : provinceShares.entrySet()) {
                out.add(String.format(Locale.ROOT, "provinceShare[%s] = %.4f",
                        provinceName(e.getKey()), e.getValue()));
            }
            out.add(f("provinceAgreement", provinceAgreement));
            out.add(String.format(Locale.ROOT,
                    "biomeTransitionsInsideProvince : %d", biomeTransitionsInsideProvince));
            out.add(String.format(Locale.ROOT,
                    "biomeTransitionsAcrossProvince  : %d", biomeTransitionsAcrossProvince));
            out.add(String.format(Locale.ROOT,
                    "biomeTransitionsNearMacroBd     : %d", biomeTransitionsNearMacroBoundary));
            out.add(f("biomeVsProvinceDelta", meanBiomeVsProvinceDelta));
            out.add("LAVA");
            out.add(f("lavaEligibleShare", lavaEligibleShare));
            out.add(f("actualLavaShare", actualLavaShare));
            out.add(f("maxLavaBodySize", maxLavaBodySize));
            out.add(String.format(Locale.ROOT,
                    "geothermalExceptions     : %d", geothermalExceptions));
            out.add("GAS GIANT");
            out.add(String.format(Locale.ROOT, "terrainQueryCount        : %d", terrainQueryCount));
            out.add(String.format(Locale.ROOT, "hydrologyQueryCount      : %d", hydrologyQueryCount));
            out.add(String.format(Locale.ROOT, "lavaQueryCount           : %d", lavaQueryCount));
            return out;
        }

        private static String f(String key, double v) {
            return String.format(Locale.ROOT, "%-26s : %.4f", key, v);
        }
    }

    static String biomeName(int ordinal) {
        List<BiomeCandidate> c = BiomeMaskField.defaultCandidates();
        return ordinal >= 0 && ordinal < c.size() ? c.get(ordinal).id() : ("candidate_" + ordinal);
    }

    static String subBiomeName(int ordinal) {
        SubBiome[] v = SubBiome.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal].name() : ("sub_" + ordinal);
    }

    static String roleName(int ordinal) {
        MaterialRole[] v = MaterialRole.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal].name() : ("role_" + ordinal);
    }

    static String categoryName(int ordinal) {
        SurfaceCategory[] v = SurfaceCategory.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal].name() : ("cat_" + ordinal);
    }

    static String provinceName(int ordinal) {
        GeologicalProvince[] v = GeologicalProvince.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal].name() : ("prov_" + ordinal);
    }
}
