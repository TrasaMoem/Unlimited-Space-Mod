package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemanticFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * ACT FINAL - the FINAL WORLDGEN OUTPUT PACKAGE (dev/test only, NOT part of the runtime).
 * Headless driver producing run/final-worldgen/: deterministic PNG previews and audits for
 * REAL planets of one world seed, enumerated exactly as the production chunk generator does.
 * No synthetic profiles anywhere. Usage: FinalWorldgenPackage <worldSeed> <systems>
 */
public final class FinalWorldgenPackage {

    private FinalWorldgenPackage() {
    }

    private static final int RES = 96;
    private static final int STEP = 64;
    private static final int MIN_PER_FAMILY = 5;
    private static final int AUDIT_GRID = 100;
    private static final int AUDIT_STEP = 160;

    /**
     * ACT STAGE 5: the spire-signal gate the audit counts, READ FROM PRODUCTION so the report can
     * never drift from what the material layer actually elects.
     */
    private static final double SPIRE_GATE =
            com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField
                    .SPIRE_STRUCTURE;

    /**
     * ACT STAGE 4: the hydrology gate the audit counts. This is the same continuous channel the
     * block writer reads, sampled at the resolution that actually resolves a corridor or a basin
     * (the audit grid is 160 blocks, so a narrow channel is a point hit).
     */
    private static final double HYDROLOGY_GATE = 0.05;

    private record PlanetEntry(PlanetId pid, PlanetWorldgenProfile profile,
                               PlanetPhysicalProfile phys, V3ColumnSampler sampler,
                               PlanetSurface surface, String dominantBiome) {
    }

    private record AuditResult(List<String> lines, List<String> featureLines,
                               List<String> corrLines) {
    }

    public static void main(String[] args) throws IOException {
        long worldSeed = args.length > 0 ? Long.parseLong(args[0]) : 0L;
        int maxSystems = args.length > 1 ? Integer.parseInt(args[1]) : 24;
        run(worldSeed, maxSystems, new File("run/final-worldgen"));
    }

    public static void run(long worldSeed, int maxSystems, File outRoot) throws IOException {
        Files.createDirectories(outRoot.toPath());
        Galaxy galaxy = Galaxy.from(worldSeed);
        Map<String, List<PlanetEntry>> found = new LinkedHashMap<>();
        for (String f : List.of("DESERT", "VOLCANIC", "ICE", "ROCKY", "EARTHLIKE", "OCEANIC")) {
            found.put(f, new ArrayList<>());
        }
        for (int system = 0; system < maxSystems && !allFilled(found); system++) {
            if (!galaxy.exists(system)) {
                continue;
            }
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (CelestialObject obj : sys.canonicalCelestialObjects()) {
                if (obj.kind() != ObjectKind.PLANET || allFilled(found)) {
                    continue;
                }
                Planet planet = obj.planet();
                PlanetId pid = planet.id();
                PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(pid, worldSeed);
                if (profile.geology() == null || profile.geology().physical() == null) {
                    continue;
                }
                V3ColumnSampler sampler = samplerFor(profile);
                if (sampler == null) {
                    continue;
                }
                Map<String, Integer> counts = new TreeMap<>();
                WorldgenColumnSample col = new WorldgenColumnSample();
                for (int iz = -20; iz < 20; iz++) {
                    for (int ix = -20; ix < 20; ix++) {
                        sampler.sampleColumn(ix * 96, iz * 96, col);
                        if (col.biome != null) {
                            counts.merge(col.biome.id(), 1, Integer::sum);
                        }
                    }
                }
                String family = familyFor(profile, dominantOf(counts));
                List<PlanetEntry> list = found.get(family);
                if (list == null || list.size() >= MIN_PER_FAMILY) {
                    continue;
                }
                list.add(new PlanetEntry(pid, profile, profile.geology().physical(), sampler,
                        profile.properties().surface(), dominantOf(counts)));
            }
        }
        emitReports(found, outRoot);
        System.out.println("FINAL WORLDGEN PACKAGE COMPLETE: " + outRoot.getAbsolutePath());
    }

    private static void emitReports(Map<String, List<PlanetEntry>> found, File outRoot)
            throws IOException {
        List<String> materialAudit = new ArrayList<>();
        List<String> featureAudit = new ArrayList<>();
        List<String> correlations = new ArrayList<>();
        materialAudit.add("# STAGE 12 - FINAL MATERIAL AUDIT (real planets, "
                + AUDIT_GRID * AUDIT_GRID + " columns each)");
        featureAudit.add("# STAGE 13 - FINAL FEATURE AUDIT (real planets)");
        correlations.add("# STAGE 14 - CORRELATIONS (family level, "
                + AUDIT_GRID * AUDIT_GRID + " columns per planet)");
        for (Map.Entry<String, List<PlanetEntry>> e : found.entrySet()) {
            String family = e.getKey();
            File familyDir = new File(outRoot, "previews/" + family.toLowerCase(Locale.ROOT));
            for (PlanetEntry pe : e.getValue()) {
                String name = family + "_" + pe.pid().code();
                File dir = new File(familyDir, pe.pid().code());
                MaterialPreviewChannels.Render render = MaterialPreviewChannels.render(
                        name, pe.phys(), pe.profile().planetSeed(), reliefFor(family),
                        dir, RES, STEP);
                AuditResult audit = auditPlanet(pe);
                materialAudit.add("");
                materialAudit.add("## " + name);
                materialAudit.add("surfaceClass=" + pe.surface()
                        + " dominantBiome=" + pe.dominantBiome());
                materialAudit.addAll(audit.lines());
                materialAudit.add("largestVariantComponentShare="
                        + String.format(Locale.ROOT, "%.3f", render.largestComponentShare())
                        + " distinctVariants=" + render.distinctVariants()
                        + " components=" + render.componentAreas().size());
                featureAudit.add("");
                featureAudit.add("## " + name);
                featureAudit.addAll(audit.featureLines());
                correlations.add("");
                correlations.add("## " + name);
                correlations.addAll(audit.corrLines());
            }
        }
        writeLines(new File(outRoot, "material-audit.txt"), materialAudit);
        writeLines(new File(outRoot, "feature-audit.txt"), featureAudit);
        writeLines(new File(outRoot, "correlations.txt"), correlations);
    }

    private static boolean allFilled(Map<String, List<PlanetEntry>> found) {
        for (List<PlanetEntry> l : found.values()) {
            if (l.size() < MIN_PER_FAMILY) {
                return false;
            }
        }
        return true;
    }

    private static String dominantOf(Map<String, Integer> counts) {
        String best = null;
        int bestN = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * STAGE 2/12 fix - the audit family must follow the SAME authority that decides which
     * materials are legal.
     *
     * <p>The previous labelling used {@code V34FamilyScan.familyOf(dominant biome id)}, i.e. the
     * single most common LOCAL biome over a 40x40 transect. That is a different authority from
     * the one production materials obey: {@code MaterialSemantics.mayLeadSurface} and
     * {@code BiomeMaskField.candidatesFor} are both keyed on the planet's own
     * {@link PlanetSurface}. A planet can easily carry a large {@code dune_sea} patch and still
     * be classified {@code SOLID_VOLCANIC}, in which case production correctly refuses to emit
     * sand and a "DESERT"-labelled audit entry correctly reports basalt. Labelling by the local
     * biome therefore produced a false failure signal: it asserted a material contract against a
     * planet the contract does not govern.
     *
     * <p>{@link PlanetSurface} is the single authority: it is what gates the biome candidates and
     * what gates the leading semantic family. The local biome is still reported, because a
     * genuine desert whose own surface class disagrees with its dominant biome is exactly the kind
     * of incoherence this ACT is meant to surface - it is recorded, not silently relabelled away.
     *
     * @return the audit family name, or null when the planet is outside the five audited families
     *         (gas giant / ocean world), which are simply not sampled.
     */
    private static String familyFor(PlanetWorldgenProfile profile, String dominantBiome) {
        PlanetSurface surface = profile.properties() == null ? null
                : profile.properties().surface();
        if (surface == null) {
            return null;
        }
        return switch (surface) {
            case SOLID_DESERT -> "DESERT";
            case SOLID_VOLCANIC -> "VOLCANIC";
            case SOLID_ICE -> "ICE";
            case SOLID_ROCKY -> earthlikeAdmission(profile, dominantBiome)
                    ? "EARTHLIKE" : "ROCKY";
            // A gas giant has no surface and is therefore never sampled. An OCEANIC world IS a
            // real, auditable terrain archetype (STAGE 9: the final matrix must carry it), so it
            // becomes its own family instead of being skipped - its column pipeline is identical,
            // only its water coverage differs.
            case OCEANIC -> "OCEANIC";
            case GASEOUS -> null;
        };
    }

    /**
     * ACT STAGE 9: which SOLID_ROCKY planet belongs in the EARTHLIKE audit family.
     *
     * <p>The measured reason this rule exists: the previous admission keyed on the DOMINANT BIOME
     * NAME being one of {@code forest / wetland / temperate_lowlands}, and on real planets it
     * admitted ZERO worlds — the audit produced 0 EARTHLIKE planets out of 48 systems, because the
     * genuinely life-bearing rocky worlds elect {@code steppe} as their dominant biome (measured:
     * organicShare 0.61, wetShare 0.66 on system_0002_planet_00). The family was therefore empty
     * not because no earthlike world exists, but because the label it matched on was the wrong one.
     *
     * <p>The admission is now a PHYSICAL test, read from the same {@link PlanetAdmissibility} the
     * generation itself uses: a world that can genuinely host organic soil and liquid water IS an
     * earthlike world, whatever its dominant biome happens to be called. The dominant biome is
     * still reported so a genuine incoherence stays visible rather than being relabelled away.
     */
    private static boolean earthlikeAdmission(PlanetWorldgenProfile profile, String dominantBiome) {
        var phys = profile.geology() == null ? null : profile.geology().physical();
        if (phys == null) return false;
        PlanetAdmissibility adm = PlanetAdmissibility.of(phys, profile.properties().surface());
        // The same canonical ecology gate the biome / sub-biome / vegetation path already uses.
        return adm.organicPossible() && adm.liquidWaterPossible();
    }

    private static ReliefArchetype reliefFor(String family) {
        return switch (family) {
            case "DESERT" -> ReliefArchetype.CANYONLAND;
            case "VOLCANIC" -> ReliefArchetype.VOLCANIC;
            case "ICE" -> ReliefArchetype.GLACIAL;
            case "OCEANIC" -> ReliefArchetype.MOUNTAINOUS;
            default -> ReliefArchetype.HILLY;
        };
    }

    private static V3ColumnSampler samplerFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        var provinces = profile.geology().provinces();
        var climate = profile.geology().climate();
        TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(), phys, provinces,
                profile.geology().terrainSignature(), profile.baseHeight(), profile.amplitude(),
                null, profile.geology().geography(), climate);
        if (shaper == null) {
            return null;
        }
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(profile.planetSeed()));
        BiomeMaskField biomeField = new BiomeMaskField(character, climateField,
                BiomeMaskField.candidatesFor(
                        PlanetAdmissibility.of(phys, profile.properties().surface()),
                        profile.properties().surface()));
        return new V3ColumnSampler(shaper, climateField, biomeField, provinces);
    }

    private static AuditResult auditPlanet(PlanetEntry pe) {
        MaterialVariantField[] tables = MaterialPreviewChannels.tablesFor(pe.phys(),
                pe.profile().planetSeed());
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = AUDIT_GRID * AUDIT_GRID;
        Map<String, Integer> blocks = new TreeMap<>();
        Map<String, Integer> families = new TreeMap<>();
        Map<String, Integer> roles = new TreeMap<>();
        // ACT worldgen fix: REAL Pearson correlation between each continuous column channel and the
        // indicator that the column's material belongs to the corresponding semantic family. The
        // previous "corr" was a mean-vs-share RATIO (it could exceed 1 in magnitude and even go
        // negative), which is not a correlation at all. A zero-variance sample now returns the
        // conventional neutral 0.0 instead of NaN, and the sample count is unchanged.
        Corr snowCorr = new Corr();
        Corr rockCorr = new Corr();
        Corr sedCorr = new Corr();
        Corr duneCorr = new Corr();
        Corr volcCorr = new Corr();
        Corr glacCorr = new Corr();
        Corr spireCorr = new Corr();
        int duneCols = 0, glacialCols = 0, volcanicCols = 0;
        int spireCols = 0, lavaCols = 0, riverCols = 0, lakeCols = 0;
        int half = AUDIT_GRID / 2;
        for (int iz = 0; iz < AUDIT_GRID; iz++) {
            for (int ix = 0; ix < AUDIT_GRID; ix++) {
                int x = (ix - half) * AUDIT_STEP;
                int z = (iz - half) * AUDIT_STEP;
                pe.sampler().sampleColumn(x, z, col);
                MaterialVariantField field = col.materialRole == null
                        ? null : tables[col.materialRole.ordinal()];
                boolean frozenCol = false, rockyCol = false, sandyCol = false;
                boolean darkCol = false, spireFamCol = false, sandFamCol = false;
                if (field != null) {
                    int idx = field.index(col, x, z);
                    if (idx >= 0) {
                        PlanetMaterial m = field.at(idx);
                        blocks.merge(m.id(), 1, Integer::sum);
                        MaterialSemanticFamily fam = MaterialSemantics.familyOf(m);
                        families.merge(fam.name(), 1, Integer::sum);
                        frozenCol = fam == MaterialSemanticFamily.FROZEN_ICE
                                || fam == MaterialSemanticFamily.FROZEN_SNOW
                                || fam == MaterialSemanticFamily.FROZEN_ROCK;
                        rockyCol = fam == MaterialSemanticFamily.ROCK
                                || fam == MaterialSemanticFamily.SPIRE_ROCK;
                        sandyCol = fam == MaterialSemanticFamily.SAND
                                || fam == MaterialSemanticFamily.SEDIMENT;
                        darkCol = fam == MaterialSemanticFamily.VOLCANIC_DARK
                                || fam == MaterialSemanticFamily.VOLCANIC_ASH
                                || fam == MaterialSemanticFamily.DARK_ROCK;
                        spireFamCol = fam == MaterialSemanticFamily.SPIRE_ROCK;
                        sandFamCol = fam == MaterialSemanticFamily.SAND;
                    }
                }
                roles.merge(col.materialRole == null ? "(none)"
                        : col.materialRole.name(), 1, Integer::sum);
                // REAL Pearson: channel vs. the 0/1 family indicator of THIS column's material.
                snowCorr.add(SurfaceMaterialField.snowAccumulation(col), frozenCol ? 1.0 : 0.0);
                rockCorr.add(SurfaceMaterialField.rockExposure(col), rockyCol ? 1.0 : 0.0);
                sedCorr.add(col.sedimentShare, sandyCol ? 1.0 : 0.0);
                duneCorr.add(col.duneRelief, sandFamCol ? 1.0 : 0.0);
                volcCorr.add(col.volcanicIntensity, darkCol ? 1.0 : 0.0);
                glacCorr.add(col.glacialRelief, frozenCol ? 1.0 : 0.0);
                spireCorr.add(col.spireIntensity, spireFamCol ? 1.0 : 0.0);
                if (col.duneRelief > 0.35) duneCols++;
                if (col.glacialRelief > 0.35) glacialCols++;
                if (col.volcanicRelief > 0.35) volcanicCols++;
                // ACT STAGE 5 / 4 measurement correction. These three counters previously used
                // thresholds ABOVE the real signal range, so they read 0.0000 while the producers
                // were alive and correct:
                //   * spireIntensity is the deformation NORMALISED by the amplitude budget, whose
                //     measured maximum is 0.234 - a 0.5 gate can never fire. The gate is now the
                //     ACTUAL production gate, SurfaceMaterialField.SPIRE_STRUCTURE.
                //   * riverMask / lakeMask are the real drainage masks; measured on a warm wet world
                //     they exceed 0.02 on ~4.8% / ~6.5% of columns with trunk courses at 1.0, so a 0.5
                //     gate only caught the very widest trunk. 0.05 is the same channel read at a
                //     resolution that actually resolves a river corridor.
                // Every threshold below is the PRODUCTION gate, read from the same constant the
                // block layer uses - never a number chosen to make the report look better.
                if (col.spireIntensity >= SPIRE_GATE) spireCols++;
                if (col.lavaMask > 0.5) lavaCols++;
                if (col.riverMask >= HYDROLOGY_GATE) riverCols++;
                if (col.lakeMask >= HYDROLOGY_GATE) lakeCols++;
            }
        }
        return new AuditResult(
                buildLines(n, blocks, families, roles),
                buildFeatureLines(duneCols, glacialCols, volcanicCols, spireCols,
                        lavaCols, riverCols, lakeCols, n),
                buildCorrLines(snowCorr, rockCorr, sedCorr, duneCorr, volcCorr, glacCorr,
                        spireCorr));
    }

    private static List<String> buildLines(int n, Map<String, Integer> blocks,
                                           Map<String, Integer> families,
                                           Map<String, Integer> roles) {
        List<String> lines = new ArrayList<>();
        lines.add("columns=" + n);
        lines.add("blockShare=" + pct(blocks, n));
        lines.add("familyShare=" + pct(families, n));
        lines.add("roleShare=" + pct(roles, n));
        return lines;
    }

    private static List<String> buildFeatureLines(int dune, int glacial, int volcanic,
                                                  int spire, int lava, int river, int lake,
                                                  int n) {
        List<String> out = new ArrayList<>();
        out.add("duneReliefShare=" + f(dune / (double) n)
                + " glacialReliefShare=" + f(glacial / (double) n)
                + " volcanicReliefShare=" + f(volcanic / (double) n)
                + " spireSignalShare=" + f(spire / (double) n));
        out.add("lavaMaskShare=" + f(lava / (double) n)
                + " riverMaskShare=" + f(river / (double) n)
                + " lakeMaskShare=" + f(lake / (double) n));
        return out;
    }

    private static List<String> buildCorrLines(Corr snow, Corr rock, Corr sed, Corr dune,
                                               Corr volc, Corr glac, Corr spire) {
        List<String> out = new ArrayList<>();
        out.add("corr(snowAccumulation -> frozen family)=" + f(snow.pearson()));
        out.add("corr(rockExposure -> rock family)=" + f(rock.pearson()));
        out.add("corr(sedimentShare -> sand/sediment family)=" + f(sed.pearson()));
        out.add("corr(duneRelief -> sand family)=" + f(dune.pearson()));
        out.add("corr(volcanicIntensity -> volcanic dark/ash family)=" + f(volc.pearson()));
        out.add("corr(glacialRelief -> frozen family)=" + f(glac.pearson()));
        out.add("corr(spireIntensity -> spire family)=" + f(spire.pearson()));
        return out;
    }

    /**
     * ACT worldgen fix: a REAL Pearson correlation accumulator.
     *
     * <p>The previous channel was {@code mean(channel) vs share(family)} - a ratio, not a
     * correlation, and one that could exceed 1 in magnitude (measured up to -19.6). This computes
     * the genuine Pearson coefficient {@code r} between the continuous channel and the 0/1 family
     * indicator of each column.
     *
     * <p>When either sample has zero variance (a constant channel, or a family present on zero or
     * every column) the coefficient is mathematically undefined; per the diagnostic convention it
     * returns the deterministic neutral {@code 0.0}, never {@code NaN}.
     */
    static final class Corr {
        private long n;
        private double sx, sy, sxx, syy, sxy;

        void add(double x, double y) {
            n++;
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }

        double pearson() {
            if (n <= 1L) return 0.0;
            double nn = n;
            double cov = sxy - sx * sy / nn;
            double varX = sxx - sx * sx / nn;
            double varY = syy - sy * sy / nn;
            if (varX <= 1e-12 || varY <= 1e-12) return 0.0;
            return cov / Math.sqrt(varX * varY);
        }
    }

    private static String pct(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey(),
                    100.0 * e.getValue() / n));
            if (i >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static void writeLines(File file, List<String> lines) throws IOException {
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }
}
