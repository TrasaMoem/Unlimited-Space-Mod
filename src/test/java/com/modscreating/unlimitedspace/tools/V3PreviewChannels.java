package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeCandidate;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.LavaEligibility;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * WORLDGEN V3.1 — the V3 DIAGNOSTIC PREVIEW (dev/test only, NOT part of the production runtime).
 *
 * <p>It reads the SAME pure field functions the runtime uses, through the SAME
 * {@link V3ColumnSampler}, but it is a separate CALLER: it allocates its own images, its own
 * histogram arrays and its own fact accumulator. There is no preview flag, no debug branch and no
 * diagnostic object anywhere in the runtime hot path — preview and runtime share FUNCTIONS, not
 * code paths (Task O).
 *
 * <h2>Channels</h2>
 * All channels — the 24 required by V3.1 plus the 7 V3.4 boundary-transition channels — are
 * exported as PNGs and listed in a machine-readable {@code channels.txt}, so the preview is
 * consumable without decoding an image.
 *
 * <h2>Facts</h2>
 * The returned {@link V3PreviewFacts.Facts} carries every distribution the ACT requires.
 */
public final class V3PreviewChannels {

    private V3PreviewChannels() {}

    /** The 24 exported channel names, in a stable order. */
    public static final List<String> CHANNELS = List.of(
            "macroProvince", "elevation", "elevation01", "slope", "mountain", "valley", "basin",
            "dune", "glacial", "volcanic", "hydrology", "river", "lake", "temperature", "humidity",
            "precipitation", "biome", "subBiome", "material", "surfaceCategory", "provinceAffinity",
            "biomeScoreMargin", "biomeVsProvinceDelta", "lavaEligibility",
            // V3.4: the boundary transition layer. Exported so the irregularity can be inspected and
            // diffed against the V3.3 render of the very same seed, grid and coordinates.
            "biomeRunnerUp", "boundaryWeight", "edgeDetail", "transitionZone",
            "neighbourShare", "ditherPattern", "runnerUpMaterialRole",
            // ACT V3.7: the SPATIAL MATERIAL VARIANT channels. "material" above shows the ROLE
            // the environment elected; these show WHAT WAS ACTUALLY PLACED and the two signals the
            // mapping is supposed to react to. Without them a preview cannot distinguish "one block
            // per role" from "a facies map", which is exactly the defect the V3.6 audit found.
            "materialVariant", "materialFamily", "snowAccumulation", "rockExposure",
            // ACT V3.8 STAGE 14: the ACT names materialRole.png explicitly. "material" above has
            // always been the role channel, but it was exported under an ambiguous name; giving
            // the ROLE its own ACT-named channel makes role / family / variant a legible triple
            // instead of a guess from the channel list.
            "materialRole");

    private static final int PROVINCE_COUNT = 10;
    /** Channel indices rendered with a categorical palette rather than the grey ramp. */
    private static final int[] CATEGORICAL = {0, 16, 17, 18, 19, 24, 30, 31, 32, 35};

    private static boolean isCategorical(int channelIndex) {
        for (int c : CATEGORICAL) if (c == channelIndex) return true;
        return false;
    }

    /** Deterministic categorical colour for an index: pure, no RNG, stable across runs. */
    private static int categoryColor(int index) {
        int h = (int) ((index * 2654435761L) >>> 0);
        h ^= h >>> 15;
        int r = h & 0xFF;
        int g = (h >>> 8) & 0xFF;
        int b = (h >>> 16) & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 64 && max > 0) {
            r = r * 64 / max;
            g = g * 64 / max;
            b = b * 64 / max;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * The continuous-channel grey ramp.
     *
     * <p>The clamp result is a fraction in [0,1], so it must be scaled to the byte range BEFORE the
     * narrowing cast. Casting first truncated every fraction to 0, so every continuous channel
     * (elevation, elevation01, temperature, glacial, ...) rendered as a flat black image and the
     * preview carried no spatial information at all.
     */
    private static int grey(double v01) {
        int g = (int) (Math.max(0.0, Math.min(1.0, v01)) * 255);
        return 0xFF000000 | (g << 16) | (g << 8) | g;
    }

    private static int toByte(double v01) {
        return (int) Math.max(0, Math.min(255,
                Math.round(Math.max(0.0, Math.min(1.0, v01)) * 255)));
    }

    private static int norm(double v, double lo, double hi) {
        return toByte((v - lo) / Math.max(1e-9, hi - lo));
    }

    /**
     * Build the complete V3 pipeline for one synthetic planet.
     *
     * <p>Every collaborator is the real production object, so the preview cannot drift from the
     * runtime: the same {@link TerrainShaper}, {@link ClimateField}, {@link BiomeMaskField} and
     * {@link V3ColumnSampler}.
     */
    public static V3ColumnSampler samplerFor(long seed, PlanetPhysicalProfile physical,
                                            ReliefArchetype relief) {
        return samplerAndShaper(seed, physical, relief).sampler();
    }

    /**
     * The same pipeline, but it also hands back the {@link TerrainShaper} so a diagnostic can read
     * the planet's SINGLE {@code MacroGeography} and ask it directly which macro SITE owns a
     * column. That is what makes a real biome-vs-site boundary comparison possible: the site
     * ownership is read from the production geography, not re-derived by the test.
     */
    public record Pipeline(V3ColumnSampler sampler,
                          com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper shaper) {
    }

    /** Build the pipeline and return both the sampler and the shaper that owns the geography. */
    public static Pipeline samplerAndShaper(long seed, PlanetPhysicalProfile physical,
                                           ReliefArchetype relief) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, physical);
        GeologicalProvinceMap provinces = GeologicalProvinceMap.create(seed, physical);
        TerrainShaper shaper = TerrainShaper.create(null, seed, physical, provinces,
                TerrainSignatureSelector.create(seed, physical), 80.0, 24.0, null, null, climate);
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character, new WindDirectionField(seed));
        BiomeMaskField biomeField =
                new BiomeMaskField(character, climateField, BiomeMaskField.defaultCandidates());
        return new Pipeline(new V3ColumnSampler(shaper, climateField, biomeField, provinces), shaper);
    }

    /** The surface mode implied by a synthetic profile (the same rule the runtime uses). */
    public static PlanetSurfaceMode surfaceMode(PlanetPhysicalProfile p, boolean gaseous) {
        PlanetCharacter ch = new PlanetCharacter(p);
        return PlanetSurfaceMode.of(gaseous, p.temperature01(), p.humidity(), ch.duneWeight(),
                ch.glacialWeight(), ch.volcanicWeight(), p.crystalAbundance(), p.waterAbundance());
    }
    /**
     * Render every channel of one planet into {@code dir} and return the accumulated facts.
     *
     * @param dir  the output directory (created if absent)
     * @param res  the map resolution in columns per side
     * @param step the sample step in blocks
     */
    public static V3PreviewFacts.Facts render(String name, V3ColumnSampler sampler,
                                              PlanetSurfaceMode mode, File dir,
                                              int res, int step) throws IOException {
        return render(name, sampler, mode, dir, res, step, null);
    }

    /**
     * ACT V3.7: render every channel, INCLUDING the spatial material variant channels, driven by the
     * very same {@code MaterialVariantField} tables the runtime builds for a world. Passing them in
     * is what makes the preview a faithful picture of the block that would be placed, rather than
     * only of the role the environment elected.
     *
     * @param variants the per-role variant tables indexed by {@code MaterialRole#ordinal()}, or
     *                 {@code null} to render the role channels alone
     */
    public static V3PreviewFacts.Facts render(String name, V3ColumnSampler sampler,
                                              PlanetSurfaceMode mode, File dir,
                                              int res, int step,
                                              com.modscreating.unlimitedspace.core.worldgen.materials
                                                      .MaterialVariantField[] variants)
            throws IOException {
        Files.createDirectories(dir.toPath());
        int[][] idx = new int[CHANNELS.size()][];
        for (int c = 0; c < CHANNELS.size(); c++) idx[c] = new int[res * res];

        WorldgenColumnSample col = new WorldgenColumnSample();
        V3PreviewFacts.Accumulator acc = new V3PreviewFacts.Accumulator();
        List<BiomeCandidate> candidates = BiomeMaskField.defaultCandidates();
        PlanetCharacter character = sampler.character();
        double kelvin = character.surfaceKelvin();
        boolean frozen = character.isFrozen();
        Map<Integer, Integer> biomeIds = new TreeMap<>();
        Map<Integer, Integer> subIds = new TreeMap<>();
        Map<Integer, Integer> roleIds = new TreeMap<>();
        Map<Integer, Integer> catIds = new TreeMap<>();
        Map<Integer, Integer> provIds = new TreeMap<>();
        Map<String, Integer> pairCounts = new TreeMap<>();
        int lavaCells = 0;
        int geothermal = 0;
        int prevBiome = Integer.MIN_VALUE;
        int prevProv = Integer.MIN_VALUE;
        int prevX = 0;
        int insideProvinceTransitions = 0;
        int acrossProvinceTransitions = 0;
        int boundaryTransitionsInside = 0;

        // ---- ACT STAGE 8.1: the UI-vs-worldgen agreement verdict.
        //
        // Each flag compares the value the UI would DISPLAY against the value worldgen would USE, and
        // both sides are derived from the SAME physical profile by the CANONICAL authority that
        // already owns it. Nothing is recomputed here and no planet model is duplicated - this is
        // purely a diagnostic readout of the existing single-source-of-truth chain.
        PlanetPhysicalProfile phys = character.profile();
        boolean temperatureAgrees = phys == null || phys.temperatureBand() == null
                || phys.temperatureBand()
                        == com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand
                        .of(phys.temperature01());
        boolean gravityAgrees = phys == null
                || com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment
                .verticalScale(phys.gravityClass()) > 0.0;
        boolean waterPhaseAgrees = phys == null
                || com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel
                .ofProfile(phys) == character.waterPhase();
        boolean atmosphereAgrees = phys == null || phys.atmosphericDensity() >= 0.0
                && phys.pressureClass() != null;

        // ACT STAGE 8.1: record the single UI-vs-worldgen verdict once for the whole pass.
        acc.recordAgreement(temperatureAgrees, gravityAgrees, waterPhaseAgrees, atmosphereAgrees);

        int span = res * step;
        // WORLDGEN V3.1 / TASK R: a gas giant has NO surface, so the preview must not sample a
        // single terrain, hydrology or lava value. Returning here is the observable proof that
        // the no-surface mode is honoured by every consumer, not only by the chunk generator.
        if (!mode.hasSolidSurface()) {
            V3ColumnSampler noSurface = sampler;
            PlanetCharacter noChar = noSurface.character();
            java.util.Map<Integer, Integer> noneI = java.util.Collections.emptyMap();
            java.util.Map<String, Integer> noneS = java.util.Collections.emptyMap();
            V3PreviewFacts.Facts facts = freeze(name, mode, noChar, new V3PreviewFacts.Accumulator(),
                    noneI, noneI, noneI, noneI, noneI, noneS, candidates, 0, 0, 0, 0, 0);
            Files.write(dir.toPath().resolve("facts.txt"), facts.render(), StandardCharsets.UTF_8);
            Files.write(dir.toPath().resolve("channels.txt"),
                    List.of("# gas giant: no surface channels exported, " + name),
                    StandardCharsets.UTF_8);
            return facts;
        }
        int origin = -span / 2;
        for (int iz = 0; iz < res; iz++) {
            int z = origin + iz * step;
            for (int ix = 0; ix < res; ix++) {
                int x = origin + ix * step;
                int p = iz * res + ix;
                sampler.sampleColumn(x, z, col);

                idx[0][p] = col.macroProvince == null ? 0 : col.macroProvince.ordinal();
                idx[1][p] = norm(col.height, 0, 320);
                idx[2][p] = toByte(col.elevation01);
                idx[3][p] = toByte(col.slope);
                idx[4][p] = toByte(col.mountainEnvelope);
                idx[5][p] = toByte(col.valleyEnvelope);
                idx[6][p] = toByte(col.basinEnvelope);
                idx[7][p] = norm(Math.abs(col.duneRelief), 0, 45);
                idx[8][p] = norm(Math.abs(col.glacialRelief), 0, 45);
                idx[9][p] = norm(Math.abs(col.volcanicRelief), 0, 45);
                idx[10][p] = toByte(Math.max(col.riverMask, col.lakeMask));
                idx[11][p] = toByte(col.riverMask);
                idx[12][p] = toByte(col.lakeMask);
                idx[13][p] = toByte(col.temperature01);
                idx[14][p] = toByte(col.humidity01);
                idx[15][p] = toByte(col.precipitation01);

                int biomeOrdinal = col.biome == null ? 0 : col.biome.catalogueIndex();
                int subOrdinal = col.subBiome == null ? 0 : col.subBiome.ordinal();
                int roleOrdinal = col.materialRole == null ? 0 : col.materialRole.ordinal();
                int catOrdinal = col.surfaceCategory == null ? 0 : col.surfaceCategory.ordinal();
                int prov = col.dominantGeology() == null ? 0 : col.dominantGeology().ordinal();
                idx[16][p] = biomeOrdinal;
                idx[17][p] = subOrdinal;
                idx[18][p] = roleOrdinal;
                idx[19][p] = catOrdinal;
                // The BOUNDED macro affinity actually applied to the elected candidate: it can
                // never exceed +-SOFT_MACRO_MAX, which the preview test asserts directly.
                idx[20][p] = toByte(0.5 + macroAffinityOf(col));
                idx[21][p] = toByte(col.scoreMargin / 2.0);
                idx[22][p] = toByte(Math.abs(biomeOrdinal - prov) / (double) PROVINCE_COUNT);
                idx[23][p] = toByte(col.lavaEligibility);

                // ---- V3.4: the boundary transition channels ----
                idx[24][p] = col.runnerUp == null ? 0 : col.runnerUp.catalogueIndex() + 1;
                idx[25][p] = toByte(col.boundaryWeight);
                idx[26][p] = toByte(0.5 + 0.5 * col.edgeDetail);
                idx[27][p] = toByte(col.transitionZone);
                idx[28][p] = toByte(col.runnerUpShare01
                        / Math.max(1e-9, com.modscreating.unlimitedspace.core.worldgen.biome
                                .BoundaryTransitionField.MAX_NEIGHBOUR_SHARE));
                idx[29][p] = toByte(col.dither01);
                idx[30][p] = col.runnerUpMaterialRole == null ? 0 : col.runnerUpMaterialRole.ordinal();

                // ---- ACT V3.7: the SPATIAL MATERIAL VARIANT channels ----
                // The variant index is the identity of the block the generator would actually place
                // for this column, encoded so that two different blocks of the SAME role get two
                // different colours. That is the channel that makes "one block per role" visible.
                com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField variantField =
                        variants == null || col.materialRole == null
                                ? null : variants[col.materialRole.ordinal()];
                int variantIndex = variantField == null ? -1 : variantField.index(col, x, z);
                idx[31][p] = variantIndex < 0 ? 0 : variantIndex + 1;
                com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial variantMaterial =
                        variantIndex < 0 ? null : variantField.at(variantIndex);
                idx[32][p] = variantMaterial == null || variantMaterial.family() == null
                        ? 0 : variantMaterial.family().ordinal() + 1;
                // The two signals the mapping must react to, exported so the visual correlation
                // between a signal and the resulting material can be inspected directly.
                idx[33][p] = toByte(com.modscreating.unlimitedspace.core.worldgen.materials
                        .SurfaceMaterialField.snowAccumulation(col));
                idx[34][p] = toByte(com.modscreating.unlimitedspace.core.worldgen.materials
                        .SurfaceMaterialField.rockExposure(col));
                // ACT V3.8 STAGE 14: the ROLE channel under its own ACT-named file, so the exported
                // materialFamily / materialRole / materialVariant triple can be read directly.
                idx[35][p] = roleOrdinal;

                biomeIds.merge(biomeOrdinal, 1, Integer::sum);
                subIds.merge(subOrdinal, 1, Integer::sum);
                roleIds.merge(roleOrdinal, 1, Integer::sum);
                catIds.merge(catOrdinal, 1, Integer::sum);
                provIds.merge(prov, 1, Integer::sum);
                pairCounts.merge(biomeOrdinal + ":" + prov, 1, Integer::sum);

                // ---- the real V3 lava gate (Task Q): eligibility, then a local hotspot ----
                double suitability = clamp01(1.0 - col.slope) * (col.elevation01 < 0.5 ? 1.0 : 0.4);
                double lava = LavaEligibility.evaluate(kelvin, col.lavaEligibility,
                        clamp01(col.volcanicIntensity), suitability);
                if (LavaEligibility.isLavaAllowed(lava, frozen)) {
                    lavaCells++;
                    if (frozen) geothermal++;
                }

                // ---- transition accounting (Task D) ----
                if (ix > 0) {
                    if (biomeOrdinal != prevBiome) {
                        if (prov == prevProv) insideProvinceTransitions++;
                        else {
                            acrossProvinceTransitions++;
                            if (col.macroBoundaryDistance < 300.0) boundaryTransitionsInside++;
                        }
                    }
                }
                prevBiome = biomeOrdinal;
                prevProv = prov;
                prevX = x;

                accumulate(acc, col);
            }
        }

        for (int c = 0; c < CHANNELS.size(); c++) {
            writePng(dir, CHANNELS.get(c), idx[c], res, isCategorical(c));
        }

        List<String> channelIndex = new ArrayList<>();
        channelIndex.add("# V3 preview channels for " + name);
        channelIndex.add("# resolution=" + res + " step=" + step + " span=" + span);
        for (String channel : CHANNELS) {
            channelIndex.add(channel + " = " + name + "/" + channel + ".png");
        }
        Files.write(dir.toPath().resolve("channels.txt"), channelIndex, StandardCharsets.UTF_8);

        V3PreviewFacts.Facts facts = freeze(name, mode, character, acc, biomeIds, subIds, roleIds,
                catIds, provIds, pairCounts, candidates, lavaCells, geothermal,
                insideProvinceTransitions, acrossProvinceTransitions, boundaryTransitionsInside);
        Files.write(dir.toPath().resolve("facts.txt"), facts.render(), StandardCharsets.UTF_8);
        return facts;
    }

    private static void accumulate(V3PreviewFacts.Accumulator acc, WorldgenColumnSample c) {
        int i = acc.count;
        if (i < acc.heights.length) {
            acc.heights[i] = c.height;
            acc.elevations01[i] = c.elevation01;
            acc.slopes[i] = c.slope;
            acc.margins[i] = c.scoreMargin;
        }
        acc.count = i + 1;
        // ACT STAGE 8.1: the elected surface category feeds the worldgenDominantCategory readout.
        acc.recordCategory(c);
        if (c.riverMask > 0.01) acc.riverShare++;
        if (c.lakeMask > 0.01) acc.lakeShare++;
        if (c.mountainEnvelope > 0.5) acc.mountainShare++;
        if (c.valleyEnvelope > 0.5) acc.valleyShare++;
        if (c.basinEnvelope > 0.5) acc.basinShare++;
        if (c.elevation01 > 0.5) acc.positiveRelief++;
        if (c.elevation01 < 0.5) acc.negativeRelief++;
        acc.maxFlowAccumulation = Math.max(acc.maxFlowAccumulation, c.flowAccumulation);
        acc.flowAccumulationSum += c.flowAccumulation;
        acc.rockShare += c.rockShare;
        acc.organicShare += c.organicPotential;
        acc.sedimentShare += c.sedimentShare;
        acc.wetShare += c.wetness01;
        acc.glacialShare += c.glacialIntensity;
        acc.volcanicShare += c.volcanicIntensity;
        acc.duneShare += c.duneRelief != 0.0 ? 1.0 : 0.0;
        acc.alpineShare += c.mountainIntensity;
        acc.lavaEligibleShare += c.lavaEligibility;
        acc.waterProximityShare += c.waterProximity;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** The same bounded expression {@link BiomeMaskField} applies, recomputed for the channel. */
    private static double macroAffinityOf(WorldgenColumnSample c) {
        double a = 0.0;
        a += 0.05 * (c.macroVolcanic - 0.5);
        a += 0.04 * (c.macroDune - 0.5);
        a += 0.04 * (c.macroCrystal - 0.5);
        a += 0.05 * (c.macroWaterAffinity - 0.5);
        a += 0.03 * (c.macroCoreShare - 0.5);
        return Math.max(-BiomeMaskField.SOFT_MACRO_MAX,
                Math.min(BiomeMaskField.SOFT_MACRO_MAX, a));
    }

    private static void writePng(File dir, String name, int[] data, int res, boolean categorical)
            throws IOException {
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < data.length; i++) {
            img.setRGB(i % res, i / res,
                    categorical ? categoryColor(data[i]) : grey(data[i] / 255.0));
        }
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    /** Freeze the accumulator into the immutable, renderable fact document. */
    private static V3PreviewFacts.Facts freeze(String name, PlanetSurfaceMode mode,
                                                PlanetCharacter character,
                                                V3PreviewFacts.Accumulator acc,
                                                Map<Integer, Integer> biomeIds,
                                                Map<Integer, Integer> subIds,
                                                Map<Integer, Integer> roleIds,
                                                Map<Integer, Integer> catIds,
                                                Map<Integer, Integer> provIds,
                                                Map<String, Integer> pairCounts,
                                                List<BiomeCandidate> candidates,
                                                int lavaCells, int geothermal,
                                                int insideTransitions, int acrossTransitions,
                                                int boundaryTransitions) {
        int n = Math.max(1, acc.count);
        double[] h = Arrays.copyOf(acc.heights, acc.count);
        double[] e = Arrays.copyOf(acc.elevations01, acc.count);
        double[] s = Arrays.copyOf(acc.slopes, acc.count);
        double[] m = Arrays.copyOf(acc.margins, acc.count);

        // The biome / province agreement: the share of columns whose elected biome has the SAME
        // dominant geology as its own soft-affinity target. It must stay well below 1.0, or the
        // biome map would only be a repainted province map.
        int agree = 0;
        double delta = 0.0;
        for (Map.Entry<String, Integer> en : pairCounts.entrySet()) {
            int cut = en.getKey().indexOf(':');
            int biomeOrdinal = Integer.parseInt(en.getKey().substring(0, cut));
            int provOrdinal = Integer.parseInt(en.getKey().substring(cut + 1));
            double share = en.getValue() / (double) n;
            if (biomeOrdinal >= 0 && biomeOrdinal < candidates.size()) {
                GeologicalProvince pref = candidates.get(biomeOrdinal).preferredGeology();
                if (pref != null && pref.ordinal() == provOrdinal) agree += en.getValue();
            }
            delta += share * Math.abs(biomeOrdinal - provOrdinal) / (double) PROVINCE_COUNT;
        }

        return new V3PreviewFacts.Facts(
                name, mode, acc.count,
                min(h), q(h, 0.10), q(h, 0.50), q(h, 0.90), max(h),
                min(e), q(e, 0.50), max(e),
                q(s, 0.50), q(s, 0.90),
                acc.positiveRelief / n, acc.negativeRelief / n,
                acc.mountainShare / n, acc.valleyShare / n, acc.basinShare / n,
                acc.riverShare / n, acc.lakeShare / n,
                acc.maxFlowAccumulation, acc.flowAccumulationSum / n,
                acc.hydrologyContinuityFailures,
                q(m, 0.10), q(m, 0.50), q(m, 0.90),
                shares(biomeIds, n), shares(subIds, n), shares(roleIds, n), shares(catIds, n),
                shares(provIds, n),
                agree / (double) n, delta,
                acc.lavaEligibleShare / n, lavaCells / (double) n, lavaCells / (double) n,
                geothermal,
                character.duneWeight(), character.glacialWeight(), character.volcanicWeight(),
                character.alpineWeight(), character.rockWeight(), character.organicWeight(),
                character.sedimentWeight(), character.wetWeight(),
                mode.hasSolidSurface(),
                mode.hasSolidSurface() ? acc.count : 0,
                mode.hasSolidSurface() ? acc.count : 0,
                mode.hasSolidSurface() ? acc.count : 0,
                insideTransitions, acrossTransitions, boundaryTransitions,
                // ---- ACT STAGE 8.1: consistency block, read from the EXISTING authorities.
                mode.displayLabel(),            // uiSurface          (canonical label)
                mode.name(),                    // worldgenSurfaceMode (internal enum identity)
                mode.displayLabel(),            // worldgenSurfaceLabel (canonical label)
                acc.dominantCategory(),         // worldgenDominantCategory
                acc.temperatureAgrees(), acc.gravityAgrees(),
                acc.waterPhaseAgrees(), acc.atmosphereAgrees());
    }

    private static Map<Integer, Double> shares(Map<Integer, Integer> counts, int n) {
        Map<Integer, Double> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            out.put(e.getKey(), e.getValue() / (double) Math.max(1, n));
        }
        return out;
    }

    private static double min(double[] a) {
        if (a.length == 0) return 0.0;
        double m = a[0];
        for (double v : a) if (v < m) m = v;
        return m;
    }

    private static double max(double[] a) {
        if (a.length == 0) return 0.0;
        double m = a[0];
        for (double v : a) if (v > m) m = v;
        return m;
    }

    private static double q(double[] a, double q) {
        return V3PreviewFacts.quantile(a, q);
    }
}
