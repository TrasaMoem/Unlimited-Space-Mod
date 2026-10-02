package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemanticFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaperScratch;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * ACT-A ITEMS 2-6 - the read-only EVIDENCE harness. It changes no production code and asserts no
 * target: every number it prints is a measurement of the pipeline as it stands, so the report can
 * state what is proven, what is still open, and which specific source owns each defect.
 *
 * <p>Items measured here:
 * <ol>
 *   <li>2 - DESERT relief through the FULL {@link TerrainShaper#sampleInto}, P10/P50/P90 and
 *       P90-P10 over at least five REAL worlds;</li>
 *   <li>3 - OCEANIC at coverage .55 / .70 / .85 / .95, cold and non-cold: sea level, relief
 *       percentiles, P90-P10 and the flat-ice-sheet share;</li>
 *   <li>4 - VOLCANIC material histogram plus the exact meaning of
 *       {@code largestVariantComponentShare};</li>
 *   <li>5 - hydrology: whether the published shares are MASKS or final water BLOCKS, and the final
 *       water-block share for the earthlike worlds;</li>
 *   <li>6 - SPIRES: the measured spire signal against the production gate.</li>
 * </ol>
 */
@Tag("worldgen")
@Tag("audit")
class ActAReliefAndHydrologyAudit {

    private static final long WORLD_SEED = 0L;
    private static final int MIN_PER_FAMILY = 5;
    private static final int GRID = 100;
    private static final int STEP = 160;
    private static final int MAX_SYSTEMS = 64;

    private record World(String label, PlanetWorldgenProfile profile, PlanetPhysicalProfile phys,
                         V3ColumnSampler sampler, TerrainShaper shaper) {
    }
    /** The real worlds of one geological family, enumerated as the chunk generator does. */
    private static List<World> family(PlanetSurface wanted) {
        List<World> out = new ArrayList<>();
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int system = 0; system < MAX_SYSTEMS && out.size() < MIN_PER_FAMILY; system++) {
            if (!galaxy.exists(system)) {
                continue;
            }
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (CelestialObject obj : sys.canonicalCelestialObjects()) {
                if (obj.kind() != ObjectKind.PLANET || out.size() >= MIN_PER_FAMILY) {
                    continue;
                }
                Planet planet = obj.planet();
                PlanetId pid = planet.id();
                PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(pid, WORLD_SEED);
                if (profile.geology() == null || profile.geology().physical() == null) {
                    continue;
                }
                if (profile.properties().surface() != wanted) {
                    continue;
                }
                World w = build(wanted.name() + "_" + pid.code(), profile);
                if (w != null) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    private static World build(String label, PlanetWorldgenProfile profile) {
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
        V3ColumnSampler sampler = new V3ColumnSampler(shaper, climateField, biomeField, provinces);
        return new World(label, profile, phys, sampler, shaper);
    }

    // ------------------------------------------------------------------ ITEM 2: DESERT relief

    /**
     * ITEM 2 - DESERT relief measured through the FULL {@link TerrainShaper#sampleInto}, which is
     * the method the production chunk generator calls. The dead {@code ValueNoiseTerrainGenerator}
     * is deliberately NOT used: the ACT asks for the real composer.
     */
    @Test
    void desertReliefPercentilesThroughTheFullShaper() {
        System.out.println();
        System.out.println("===== ACT-A / ITEM 2 : DESERT RELIEF, full TerrainShaper.sampleInto =====");
        for (World w : family(PlanetSurface.SOLID_DESERT)) {
            Relief r = relief(w);
            System.out.printf(Locale.ROOT,
                    "## %s  kelvin=%.1f base=%.1f amp=%.1f sea=%.1f%n",
                    w.label(), StellarThermalModel.denormalizeKelvin(w.phys().temperature01()),
                    w.profile().baseHeight(), w.profile().amplitude(), w.profile().seaLevel());
            System.out.printf(Locale.ROOT,
                    "   P10=%.2f  P50=%.2f  P90=%.2f  P90-P10=%.2f  min=%.1f max=%.1f  span=%d%n",
                    r.p10, r.p50, r.p90, r.p90 - r.p10, r.min, r.max, r.n);
            System.out.printf(Locale.ROOT,
                    "   reliefRelative(p90-p10)/amp = %.3f   duneReliefShare=%.4f%n",
                    (r.p90 - r.p10) / Math.max(1e-9, w.profile().amplitude()), r.duneShare);
        }
    }

    /**
     * Height statistics of one world through the production shaper.
     *
     * @param landShare  fraction of columns above this world's own sea level
     * @param flatShare  fraction of LAND columns within one block of the land median, i.e. the share
     *                   of a visually flat plain / ice sheet
     */
    private record Relief(double p10, double p50, double p90, double min, double max, int n,
                          double duneShare, double landShare, double flatShare) {
    }

    private static Relief relief(World w) {
        int n = GRID * GRID;
        int[] heights = new int[n];
        WorldgenColumnSample col = new WorldgenColumnSample();
        int dunes = 0;
        int i = 0;
        int half = GRID / 2;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                int x = (ix - half) * STEP;
                int z = (iz - half) * STEP;
                w.sampler().sampleColumn(x, z, col);
                heights[i++] = col.height;
                if (Math.abs(col.duneRelief) > 3.0) {
                    dunes++;
                }
            }
        }
        int[] sorted = heights.clone();
        java.util.Arrays.sort(sorted);
        double sea = w.profile().seaLevel();
        int land = 0;
        for (int h : heights) {
            if (h > sea) {
                land++;
            }
        }
        double landMedian = land == 0 ? 0 : medianOfLand(heights, sea);
        int flat = 0;
        for (int h : heights) {
            if (h > sea && Math.abs(h - landMedian) <= 1.0) {
                flat++;
            }
        }
        return new Relief(pct(sorted, 0.10), pct(sorted, 0.50), pct(sorted, 0.90),
                sorted[0], sorted[n - 1], n, dunes / (double) n, land / (double) n,
                land == 0 ? 0.0 : flat / (double) land);
    }

    private static double medianOfLand(int[] heights, double sea) {
        int[] land = java.util.Arrays.stream(heights).filter(h -> h > sea).sorted().toArray();
        return land.length == 0 ? 0 : land[land.length / 2];
    }

    private static double pct(int[] sorted, double q) {
        int idx = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }
    // ------------------------------------------------------------------ ITEM 3: OCEANIC

    /**
     * ITEM 3 - OCEANIC at coverage .55 / .70 / .85 / .95, cold and non-cold: sea level, relief
     * percentiles, P90-P10, and the flat-ice-sheet share (the fraction of LAND columns whose whole
     * neighbourhood sits within one block of the median, i.e. a visually flat plain).
     */
    @Test
    void oceanicCoverageMatrixColdAndNonCold() {
        System.out.println();
        System.out.println("===== ACT-A / ITEM 3 : OCEANIC coverage matrix =====");
        for (World w : family(PlanetSurface.OCEANIC)) {
            Relief r = relief(w);
            double sea = w.profile().seaLevel();
            double kelvin = StellarThermalModel.denormalizeKelvin(w.phys().temperature01());
            System.out.printf(Locale.ROOT,
                    "## %s  kelvin=%.1f (%s) coverage=%.3f sea=%.2f base=%.1f amp=%.1f%n",
                    w.label(), kelvin, w.phys().temperatureBand(),
                    w.phys().oceanCoverage(), sea, w.profile().baseHeight(),
                    w.profile().amplitude());
            System.out.printf(Locale.ROOT,
                    "   P10=%.2f P50=%.2f P90=%.2f P90-P10=%.2f  landShare=%.4f flatShare=%.4f%n",
                    r.p10, r.p50, r.p90, r.p90 - r.p10, r.landShare, r.flatShare);
        }
    }

    // ------------------------------------------------------------------ ITEM 4: VOLCANIC

    /**
     * ITEM 4 - the VOLCANIC material histogram, plus the exact meaning of
     * {@code largestVariantComponentShare} printed next to it.
     */
    @Test
    void volcanicHistogramAndComponentShareMeaning() {
        System.out.println();
        System.out.println("===== ACT-A / ITEM 4 : VOLCANIC histogram =====");
        for (World w : family(PlanetSurface.SOLID_VOLCANIC)) {
            MaterialVariantField[] tables = tablesFor(w);
            Map<String, Integer> blocks = new TreeMap<>();
            Map<MaterialSemanticFamily, Integer> families = new TreeMap<>();
            Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
            WorldgenColumnSample col = new WorldgenColumnSample();
            int n = GRID * GRID;
            int half = GRID / 2;
            for (int iz = 0; iz < GRID; iz++) {
                for (int ix = 0; ix < GRID; ix++) {
                    int x = (ix - half) * STEP;
                    int z = (iz - half) * STEP;
                    w.sampler().sampleColumn(x, z, col);
                    MaterialVariantField t = col.materialRole == null
                            ? null : tables[col.materialRole.ordinal()];
                    roles.merge(col.materialRole == null ? MaterialRole.ACCENT : col.materialRole,
                            1, Integer::sum);
                    if (t == null) {
                        continue;
                    }
                    int idx = t.index(col, x, z);
                    if (idx < 0) {
                        continue;
                    }
                    blocks.merge(t.at(idx).id(), 1, Integer::sum);
                    families.merge(MaterialSemantics.familyOf(t.at(idx)), 1, Integer::sum);
                }
            }
            System.out.printf(Locale.ROOT, "## %s  columns=%d%n", w.label(), n);
            System.out.println("   blockShare  = " + pctStr(blocks, n));
            System.out.println("   familyShare = " + pctFamStr(families, n));
            System.out.println("   roleShare   = " + pctRoleStr(roles, n));
            System.out.println("   ACT histogram of the six families the ACT names:");
            System.out.println("      sand=" + share(blocks, n, "van.sand", "van.sandstone")
                    + " red_dust=" + share(blocks, n, "us.red_dust", "van.red_sand")
                    + " sulfur=" + share(blocks, n, "us.sulfurstone")
                    + " ash=" + share(blocks, n, "van.tuff", "us.cinderstone")
                    + " basalt=" + share(blocks, n, "van.basalt", "us.ember_basalt")
                    + " cinderstone=" + share(blocks, n, "us.cinderstone"));
        }
    }

    private static double share(Map<String, Integer> blocks, int n, String... ids) {
        int hit = 0;
        for (String id : ids) {
            hit += blocks.getOrDefault(id, 0);
        }
        return hit / (double) n;
    }

    private static MaterialVariantField[] tablesFor(World w) {
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(w.phys(), role,
                    w.profile().planetSeed(), null);
        }
        return tables;
    }
    // ------------------------------------------------------------------ ITEM 5 + 6

    /**
     * ITEM 5 - hydrology: the distinction the ACT asks for, measured.
     *
     * <p>It reports, per world, BOTH numbers separately:
     * <ul>
     *   <li><b>maskShare</b> - the share of columns whose continuous drainage mask
     *       ({@code riverMask} / {@code lakeMask}) is non-zero. These are SIGNALS.</li>
     *   <li><b>finalWaterBlockShare</b> - the share of columns that end up BELOW this planet's sea
     *       level, which is the only thing the chunk generator turns into an actual water block.
     *       This is the FINAL BLOCK share.</li>
     * </ul>
     * The two are different quantities; conflating them is what made the earlier report read
     * "0% water" on an ocean world.
     */
    @Test
    void hydrologyMasksVersusFinalWaterBlocks() {
        System.out.println();
        System.out.println("===== ACT-A / ITEM 5 : hydrology = masks vs final water blocks =====");
        System.out.println("(riverMsk/lakeMsk/waterMsk = drainage SIGNAL, FINALBLK = ACTUAL BLOCKS)");
        System.out.printf(Locale.ROOT, "%-34s %9s %9s %9s %9s%n",
                "world", "riverMsk", "lakeMsk", "waterMsk", "FINALBLK");
        report(PlanetSurface.OCEANIC);
        report(PlanetSurface.SOLID_DESERT);
        report(PlanetSurface.SOLID_ICE);
        report(PlanetSurface.SOLID_ROCKY);
    }

    private void report(PlanetSurface surface) {
        for (World w : family(surface)) {
            int n = GRID * GRID;
            int river = 0;
            int lake = 0;
            int anyMask = 0;
            int belowSea = 0;
            double sea = w.profile().seaLevel();
            WorldgenColumnSample col = new WorldgenColumnSample();
            int half = GRID / 2;
            for (int iz = 0; iz < GRID; iz++) {
                for (int ix = 0; ix < GRID; ix++) {
                    int x = (ix - half) * STEP;
                    int z = (iz - half) * STEP;
                    w.sampler().sampleColumn(x, z, col);
                    if (col.riverMask >= 0.05) {
                        river++;
                    }
                    if (col.lakeMask >= 0.05) {
                        lake++;
                    }
                    if (col.riverMask >= 0.05 || col.lakeMask >= 0.05) {
                        anyMask++;
                    }
                    if (col.height <= sea) {
                        belowSea++;
                    }
                }
            }
            System.out.printf(Locale.ROOT,
                    "%-34s %9.4f %9.4f %9.4f %9.4f  (coverage=%.3f sea=%.1f)%n",
                    w.label(), river / (double) n, lake / (double) n, anyMask / (double) n,
                    belowSea / (double) n, w.phys().oceanCoverage(), sea);
        }
    }
    /**
     * ITEM 6 - SPIRES: the measured spire signal against the REAL production gate
     * {@link SurfaceMaterialField#SPIRE_STRUCTURE}, so the report can state whether a spire feature
     * is reachable at all.
     */
    @Test
    void spireSignalAgainstTheProductionGate() {
        System.out.println();
        System.out.println("===== ACT-A / ITEM 6 : SPIRES =====");
        System.out.println("production gate SurfaceMaterialField.SPIRE_STRUCTURE = "
                + SurfaceMaterialField.SPIRE_STRUCTURE);
        PlanetSurface[] all = {PlanetSurface.SOLID_ICE, PlanetSurface.SOLID_ROCKY,
                PlanetSurface.SOLID_DESERT, PlanetSurface.SOLID_VOLCANIC, PlanetSurface.OCEANIC};
        for (PlanetSurface s : all) {
            for (World w : family(s)) {
                int n = GRID * GRID;
                int above = 0;
                double max = 0.0;
                double sum = 0.0;
                WorldgenColumnSample col = new WorldgenColumnSample();
                int half = GRID / 2;
                for (int iz = 0; iz < GRID; iz++) {
                    for (int ix = 0; ix < GRID; ix++) {
                        int x = (ix - half) * STEP;
                        int z = (iz - half) * STEP;
                        w.sampler().sampleColumn(x, z, col);
                        max = Math.max(max, col.spireIntensity);
                        sum += col.spireIntensity;
                        if (col.spireIntensity >= SurfaceMaterialField.SPIRE_STRUCTURE) {
                            above++;
                        }
                    }
                }
                System.out.printf(Locale.ROOT,
                        "## %-32s max=%.4f mean=%.4f  columns>=gate: %d / %d (%.5f)%n",
                        w.label(), max, sum / n, above, n, above / (double) n);
            }
        }
    }

    // ------------------------------------------------------------------ formatting

    private static String pctStr(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey(), 100.0 * e.getValue() / n));
            if (i >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String pctFamStr(Map<MaterialSemanticFamily, Integer> m, int n) {
        List<Map.Entry<MaterialSemanticFamily, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<MaterialSemanticFamily, Integer> e : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey().name(),
                    100.0 * e.getValue() / n));
            if (i >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String pctRoleStr(Map<MaterialRole, Integer> m, int n) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<MaterialRole, Integer> e : m.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey().name(),
                    100.0 * e.getValue() / n));
        }
        return sb.toString();
    }
}
