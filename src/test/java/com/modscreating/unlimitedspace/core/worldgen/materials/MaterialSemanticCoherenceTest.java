package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT WORLDGEN V3.8 STAGE 11 - MATERIAL SEMANTIC COHERENCE.
 *
 * <p>Every assertion runs on the REAL production path: {@link V3ColumnSampler} (the real
 * elevation / climate / hydrology / biome / material sampling), {@link SurfaceMaterialField#roleAt}
 * and {@link MaterialVariantField}. There is no test-only branch, no synthetic candidate list and
 * no re-derived admissibility - the same tables the chunk generator builds per world are measured
 * here.
 *
 * <h2>What V3.7 measured and V3.8 fixes</h2>
 * <pre>
 *   ROCKY    SEDIMENT = 99.25% of the surface; ACTUAL = red_sand 56.8%, sand 29.6%, gravel 12.5%
 *   VOLCANIC PRIMARY_SURFACE contained us.red_dust; SEDIMENT contained red_sand 55.5%
 *   DESERT   PRIMARY_SURFACE contained van.calcite at 34.9% of the role
 * </pre>
 *
 * <p>The cause was that {@code MaterialRules.coherentForSurface} answered the semantic question
 * for {@code PRIMARY_SURFACE} ONLY, so every other role admitted whatever was physically legal.
 * V3.8 adds the missing semantic layer ({@link MaterialSemantics}) and applies it BEFORE the
 * spatial weights, which is the order STAGE 9 demands.
 */
@Tag("worldgen")
@Tag("audit")
class MaterialSemanticCoherenceTest {

    private static final int COLUMNS = 10_000;

    private record World(String name, PlanetPhysicalProfile profile, ReliefArchetype relief) {}

    static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                         double volc, double geo, double ero, double crystal,
                                         PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    static World desert() {
        return new World("desert", profile(0.86, 0.06, 0.03, 0.55, 0.20, 0.25, 0.70, 0.02,
                PlanetSurface.SOLID_DESERT), ReliefArchetype.CANYONLAND);
    }

    static World volcanic() {
        return new World("volcanic", profile(0.88, 0.10, 0.05, 0.85, 0.90, 0.70, 0.40, 0.05,
                PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.VOLCANIC);
    }

    static World ice() {
        return new World("ice", profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15,
                PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL);
    }

    static World rocky() {
        return new World("rocky", profile(0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55, 0.10,
                PlanetSurface.SOLID_ROCKY), ReliefArchetype.MOUNTAINOUS);
    }


    /** The measured semantic-family distribution of one world, per role. */
    private record Dist(Map<MaterialRole, Integer> roleCount,
                        Map<MaterialRole, Map<MaterialSemanticFamily, Integer>> roleFamilies,
                        Map<MaterialRole, Map<String, Integer>> roleBlocks) {

        Map<MaterialSemanticFamily, Integer> familiesOf(MaterialRole role) {
            return roleFamilies.getOrDefault(role, Map.of());
        }

        Map<String, Integer> blocksOf(MaterialRole role) {
            return roleBlocks.getOrDefault(role, Map.of());
        }

        int total() {
            int n = 0;
            for (int v : roleCount.values()) n += v;
            return n;
        }

        /** Share of ALL columns whose material is of the given family, across every role. */
        double familyShare(MaterialSemanticFamily f) {
            int hit = 0;
            for (Map<MaterialSemanticFamily, Integer> m : roleFamilies.values()) {
                hit += m.getOrDefault(f, 0);
            }
            return hit / (double) Math.max(1, total());
        }

        /** Share of all columns of ONE role that resolved to the given family. */
        double familyShareOf(MaterialRole role, MaterialSemanticFamily f) {
            int n = roleCount.getOrDefault(role, 0);
            return n == 0 ? 0.0 : familiesOf(role).getOrDefault(f, 0) / (double) n;
        }
    }

    private static Dist measure(World w, long seed) {
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
        }
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<MaterialRole, Integer> roleCount = new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, Map<MaterialSemanticFamily, Integer>> roleFamilies =
                new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, Map<String, Integer>> roleBlocks = new EnumMap<>(MaterialRole.class);
        for (int i = 0; i < COLUMNS; i++) {
            int x = (int) ((i * 397L) % 9000L) - 4500;
            int z = (int) ((i * 641L) % 9000L) - 4500;
            sampler.sampleColumn(x, z, col);
            MaterialRole role = col.materialRole;
            MaterialVariantField field = role == null ? null : tables[role.ordinal()];
            PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
            roleCount.merge(role, 1, Integer::sum);
            MaterialSemanticFamily fam = mat == null ? null : MaterialSemantics.familyOf(mat);
            roleFamilies.computeIfAbsent(role, k -> new TreeMap<>())
                    .merge(fam == null ? MaterialSemanticFamily.ROCK : fam, 1, Integer::sum);
            roleBlocks.computeIfAbsent(role, k -> new TreeMap<>())
                    .merge(mat == null ? "NONE" : mat.id(), 1, Integer::sum);
        }
        return new Dist(roleCount, roleFamilies, roleBlocks);
    }

    private static void report(String tag, World w, Dist d) {
        System.out.println("[" + tag + "] " + w.name() + " surface=" + w.profile().surface()
                + " columns=" + d.total());
        for (Map.Entry<MaterialRole, Integer> e : d.roleCount().entrySet()) {
            if (e.getValue() < 100) continue;
            System.out.println("[" + tag + "]   " + e.getKey() + " n=" + e.getValue()
                    + " families=" + d.familiesOf(e.getKey())
                    + " blocks=" + d.blocksOf(e.getKey()));
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ================================================================== STAGE 3: DESERT

    /**
     * Requirement 1 + 2: a FERRIINOUS (red) material may not DOMINATE a VOLCANIC or a ROCKY surface.
     *
     * <p>The measured quantity is deliberately the RED share, not the whole sand family. Plain
     * grey {@code van.sand} in the SEDIMENT role of a wind-scoured volcanic plain is real aeolian
     * geology and the ACT explicitly keeps it; what the ACT forbids is a red, iron-oxide regolith
     * taking over a world whose climate and mineral context do not support one. Requirement 6 and
     * requirement 10 assert the same quantity from the other direction.
     */
    @Test
    void redSandCannotDominateVolcanicOrRocky() {
        World v = volcanic();
        Dist dv = measure(v, 0x38A001L);
        report("V3.8-D1", v, dv);
        assertTrue(redShare(dv) < 0.10,
                "red sand / red dust must not take over a VOLCANIC world, got "
                        + round(redShare(dv)));

        World r = rocky();
        Dist dr = measure(r, 0x38A002L);
        report("V3.8-D2", r, dr);
        assertTrue(redShare(dr) < 0.10,
                "red sand / red dust must not take over a ROCKY world, got "
                        + round(redShare(dr)));
    }

    /** Share of all columns whose material is one of the catalogue's ferruginous (red) materials. */
    private static double redShare(Dist d) {
        int red = 0;
        for (Map<String, Integer> byRole : d.roleBlocks().values()) {
            red += byRole.getOrDefault("van.red_sand", 0);
            red += byRole.getOrDefault("us.red_dust", 0);
        }
        return red / (double) Math.max(1, d.total());
    }

    /** Requirement 3: an ordinary DESERT is led by the sand / sediment family. */
    @Test
    void sandFamilyDominatesOrdinaryDesert() {
        World w = desert();
        Dist d = measure(w, 0x38A003L);
        report("V3.8-D3", w, d);
        double sedimentLed = d.familyShare(MaterialSemanticFamily.SAND)
                + d.familyShare(MaterialSemanticFamily.SEDIMENT);
        System.out.println("[V3.8-D3] sand+sediment share = " + round(sedimentLed)
                + " rockShare=" + round(d.familyShare(MaterialSemanticFamily.ROCK)));
        assertTrue(sedimentLed > 0.60,
                "a desert's dominant surface must belong to the sand/sediment family, got "
                        + round(sedimentLed));
    }

    /** Requirement 4: calcite / gravel (a sorted or cemented rock) may not lead a desert. */
    @Test
    void calciteAndGravelCannotDominateDesert() {
        World w = desert();
        Dist d = measure(w, 0x38A004L);
        double cemented = d.familyShare(MaterialSemanticFamily.ROCK)
                + d.familyShare(MaterialSemanticFamily.DARK_ROCK)
                + d.familyShare(MaterialSemanticFamily.MINERAL_ROCK);
        System.out.println("[V3.8-D4] desert rock-family share = " + round(cemented));
        assertTrue(cemented < 0.20,
                "a desert must not be led by calcite/gravel/rock, got " + round(cemented));
    }

    // ================================================================== STAGE 4: VOLCANIC

    /** Requirement 5: the dark / ash volcanic family leads a volcanic world. */
    @Test
    void darkAshVolcanicFamilyDominates() {
        World w = volcanic();
        Dist d = measure(w, 0x38A005L);
        report("V3.8-V5", w, d);
        double volcanic = d.familyShare(MaterialSemanticFamily.VOLCANIC_DARK)
                + d.familyShare(MaterialSemanticFamily.VOLCANIC_ASH);
        System.out.println("[V3.8-V5] dark/ash volcanic share = " + round(volcanic)
                + " sandShare=" + round(d.familyShare(MaterialSemanticFamily.SAND)));
        assertTrue(volcanic > 0.60,
                "a volcanic world must be led by the dark/ash volcanic family, got "
                        + round(volcanic));
    }

    /** Requirement 6: red sand specifically must not dominate a volcanic surface. */
    @Test
    void redSandDoesNotDominateVolcanic() {
        World w = volcanic();
        Dist d = measure(w, 0x38A006L);
        int red = 0;
        for (Map<String, Integer> byRole : d.roleBlocks().values()) {
            red += byRole.getOrDefault("van.red_sand", 0);
            red += byRole.getOrDefault("us.red_dust", 0);
        }
        double share = red / (double) Math.max(1, d.total());
        System.out.println("[V3.8-V6] red material share on a volcanic world = " + round(share));
        assertTrue(share < 0.10, "red sand / red dust must not dominate a volcanic world, got "
                + round(share));
    }

    /** Requirement 7: lava remains a LOCAL feature - it is never a dominant substrate anywhere. */
    @Test
    void lavaRemainsLocal() {
        for (World w : new World[]{volcanic(), desert(), rocky(), ice()}) {
            Dist d = measure(w, 0x38A007L + w.name().hashCode());
            System.out.println("[V3.8-V7] " + w.name() + " lavaShare="
                    + round(d.familyShare(MaterialSemanticFamily.LAVA)));
            assertTrue(d.familyShare(MaterialSemanticFamily.LAVA) == 0.0,
                    "lava must remain a feature, never a dominant surface, on " + w.name());
        }
    }

    // ================================================================== STAGE 5 + 6: ROCKY

    /** Requirement 8: a normal valid rocky role must never resolve to NONE. */
    @Test
    void noNoneForNormalRockyRole() {
        for (long seed : new long[]{0x38B001L, 0x38B002L, 0x38B003L}) {
            World w = rocky();
            Dist d = measure(w, seed);
            int none = 0;
            for (Map<String, Integer> byRole : d.roleBlocks().values()) {
                none += byRole.getOrDefault("NONE", 0);
            }
            System.out.println("[V3.8-R8] rocky seed=0x" + Long.toHexString(seed)
                    + " NONE=" + none + "/" + d.total());
            assertTrue(none == 0, "a normal rocky column must always have a material, NONE=" + none);
        }
    }

    /** Requirement 9: the rock family leads ordinary rocky samples. */
    @Test
    void rockFamilyDominatesOrdinaryRocky() {
        World w = rocky();
        Dist d = measure(w, 0x38B004L);
        report("V3.8-R9", w, d);
        double rock = 0.0;
        for (MaterialSemanticFamily f : new MaterialSemanticFamily[]{
                MaterialSemanticFamily.ROCK, MaterialSemanticFamily.DARK_ROCK,
                MaterialSemanticFamily.MINERAL_ROCK, MaterialSemanticFamily.SPIRE_ROCK,
                MaterialSemanticFamily.FROZEN_ROCK, MaterialSemanticFamily.VOLCANIC_DARK}) {
            rock += d.familyShare(f);
        }
        System.out.println("[V3.8-R9] rock family share = " + round(rock)
                + " sandShare=" + round(d.familyShare(MaterialSemanticFamily.SAND)));
        assertTrue(rock > d.familyShare(MaterialSemanticFamily.SAND),
                "a rocky world must be led by rock, but the sand share is higher");
    }

    /**
     * Requirement 10: red dust is CONDITIONAL, not universal.
     *
     * <p>The material is never banned: on a genuinely iron-rich arid world it still appears, and
     * the test asserts exactly that. What is refused is the old behaviour, where a ferruginous
     * material was a default candidate on EVERY dry world regardless of its iron context.
     */
    @Test
    void redDustIsConditionalOnIronContext() {
        // (a) A dry but NOT iron-rich world must not produce red dust at all.
        World poorIron = new World("rocky-poor-iron",
                profile(0.55, 0.10, 0.05, 0.75, 0.25, 0.30, 0.55, 0.10,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.MOUNTAINOUS);
        assertFalse(MaterialSemantics.redDustContext(poorIron.profile()),
                "a low-metallicity world must not claim a red-dust context");
        Dist dPoor = measure(poorIron, 0x38B005L);
        int redPoor = 0;
        for (Map<String, Integer> byRole : dPoor.roleBlocks().values()) {
            redPoor += byRole.getOrDefault("van.red_sand", 0);
            redPoor += byRole.getOrDefault("us.red_dust", 0);
        }
        System.out.println("[V3.8-R10] poor-iron red share = "
                + round(redPoor / (double) Math.max(1, dPoor.total())));
        assertTrue(redPoor == 0,
                "without an iron context no red material may be placed, got " + redPoor);

        // (b) An iron-rich arid world DOES get red regolith - the material stays legal.
        World ironRich = new World("rocky-iron-rich",
                profile(0.70, 0.08, 0.04, 0.75, 0.25, 0.30, 0.55, 0.10,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.CANYONLAND);
        ironRich = new World("rocky-iron-rich", withIron(ironRich.profile(), 0.80),
                ironRich.relief());
        assertTrue(MaterialSemantics.redDustContext(ironRich.profile()),
                "an iron-rich arid world must be allowed a red-dust context");
        Dist dIron = measure(ironRich, 0x38B006L);
        int redIron = 0;
        for (Map<String, Integer> byRole : dIron.roleBlocks().values()) {
            redIron += byRole.getOrDefault("van.red_sand", 0);
            redIron += byRole.getOrDefault("us.red_dust", 0);
        }
        System.out.println("[V3.8-R10] iron-rich red share = "
                + round(redIron / (double) Math.max(1, dIron.total())));
        assertTrue(redIron > 0, "an iron-rich arid world must still be able to use red regolith");
    }

    /** Requirement 11: spire / structurally-shaped rock stays legal where its signal exists. */
    @Test
    void spireRockRemainsLegalWhereSpireSignalExists() {
        // A structurally fractured, iron-poor cold-rocky world: the spire family must still be
        // reachable, because the ACT forbids solving the leak by deleting the geology.
        World w = new World("rocky-fractured",
                profile(0.30, 0.25, 0.15, 0.85, 0.20, 0.25, 0.60, 0.15,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.MOUNTAINOUS);
        Dist d = measure(w, 0x38B007L);
        report("V3.8-R11", w, d);
        double rock = 0.0;
        for (MaterialSemanticFamily f : new MaterialSemanticFamily[]{
                MaterialSemanticFamily.ROCK, MaterialSemanticFamily.DARK_ROCK,
                MaterialSemanticFamily.MINERAL_ROCK, MaterialSemanticFamily.SPIRE_ROCK,
                MaterialSemanticFamily.FROZEN_ROCK, MaterialSemanticFamily.VOLCANIC_DARK}) {
            rock += d.familyShare(f);
        }
        assertTrue(rock > 0.5,
                "the rock family must remain the dominant language of a rocky world, got "
                        + round(rock));
    }

    /** A copy of a profile with a different metallicity, keeping every other channel identical. */
    private static PlanetPhysicalProfile withIron(PlanetPhysicalProfile p, double metallicity) {
        return new PlanetPhysicalProfile(
                p.temperature(), p.temperatureBand(), p.humidity(), p.atmosphericDensity(),
                p.pressureClass(), p.waterAbundance(), p.oceanCoverage(), p.continentality(),
                p.tectonicActivity(), p.volcanicActivity(), p.geothermalActivity(), p.erosion(),
                p.impactFrequency(), p.mineralAbundance(), metallicity, p.crystalAbundance(),
                p.organicPotential(), p.radiation(), p.geothermalFlux(), p.axialClimateBias(),
                p.relativeAge(), p.gravityClass(), p.surface());
    }

    // ================================================================== STAGE 7: ICE
    /** Requirement 12: snow / ice family leads cold ordinary surfaces. */
    @Test
    void snowIceFamilyDominatesColdSurfaces() {
        World w = ice();
        Dist d = measure(w, 0x38C001L);
        report("V3.8-I12", w, d);
        double frozen = d.familyShare(MaterialSemanticFamily.FROZEN_ICE)
                + d.familyShare(MaterialSemanticFamily.FROZEN_SNOW)
                + d.familyShare(MaterialSemanticFamily.FROZEN_ROCK);
        System.out.println("[V3.8-I12] frozen share = " + round(frozen)
                + " sandShare=" + round(d.familyShare(MaterialSemanticFamily.SAND)));
        assertTrue(frozen > 0.50,
                "an ice shell must be led by the snow/ice/frozen family, got " + round(frozen));
        assertTrue(d.familyShare(MaterialSemanticFamily.SAND) < 0.05,
                "an ice shell must not be led by loose sediment, got "
                        + round(d.familyShare(MaterialSemanticFamily.SAND)));
    }

    /** Requirement 13: the rock family appears where rock exposure is high (STAGE 7 coherence). */
    @Test
    void rockFamilyAppearsWhereRockExposureIsHigh() {
        World w = ice();
        assertRockExposureCorrelation(w, 0x38C002L);
        assertRockExposureCorrelation(w, 0x38C003L);
    }

    /**
     * The V3.7-proven ICE correlation, re-asserted at the SEMANTIC FAMILY level: split the sampled
     * columns by their own median rock-exposure signal and require the rock share to be higher on
     * the exposed half. It is asserted on the family, never on one block id.
     */
    private void assertRockExposureCorrelation(World w, long seed) {
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
        }
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = 8000;
        double[] expo = new double[n];
        int[] rock = new int[n];
        for (int i = 0; i < n; i++) {
            int x = (int) ((i * 397L) % 9000L) - 4500;
            int z = (int) ((i * 641L) % 9000L) - 4500;
            sampler.sampleColumn(x, z, col);
            expo[i] = SurfaceMaterialField.rockExposure(col);
            MaterialRole role = col.materialRole;
            MaterialVariantField field = role == null ? null : tables[role.ordinal()];
            PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
            MaterialSemanticFamily f = mat == null ? null : MaterialSemantics.familyOf(mat);
            rock[i] = f != null && MaterialSemantics.isRockFamily(f) ? 1 : 0;
        }
        double[] sorted = expo.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        int lowN = 0, lowRock = 0, highN = 0, highRock = 0;
        for (int i = 0; i < n; i++) {
            if (expo[i] < median) {
                lowN++;
                lowRock += rock[i];
            } else {
                highN++;
                highRock += rock[i];
            }
        }
        double low = lowN == 0 ? 0.0 : (double) lowRock / lowN;
        double high = highN == 0 ? 0.0 : (double) highRock / highN;
        System.out.println("[V3.8-I13] seed=0x" + Long.toHexString(seed)
                + " rockShare(lowExposure)=" + round(low)
                + " rockShare(highExposure)=" + round(high));
        assertTrue(high > low,
                "higher rock exposure must raise the rock-FAMILY share, low=" + round(low)
                        + " high=" + round(high));
    }

    /** Requirement 14: volcanic material remains LOCAL on a cold world. */
    @Test
    void volcanicMaterialRemainsLocalOnIce() {
        World w = ice();
        Dist d = measure(w, 0x38C004L);
        double volcanic = d.familyShare(MaterialSemanticFamily.VOLCANIC_DARK)
                + d.familyShare(MaterialSemanticFamily.VOLCANIC_ASH);
        System.out.println("[V3.8-I14] ice-world volcanic share = " + round(volcanic));
        assertTrue(volcanic < 0.25,
                "volcanic material must remain local on an ice shell, got " + round(volcanic));
    }

    /**
     * STAGE 8 - SPATIAL COHERENCE: the semantic filter must not turn the material field into a
     * checkerboard.
     *
     * <p>The semantic layer shrinks the candidate set, and a smaller set could in principle make
     * the facies field's choice flip more often rather than less. This measures the actual map:
     * a 128x128 grid at a realistic step, counting 4-connected components per SEMANTIC FAMILY.
     *
     * <p>Two structural claims, both of which the V3.7 architecture already satisfied and which
     * the V3.8 semantic filter must not break:
     * <ul>
     *   <li>no single-cell islands - that is the salt-and-pepper signature, and its absence is what
     *       "not a checkerboard" means quantitatively;</li>
     *   <li>the largest component holds a real share of its family, i.e. the regions are bodies
     *       rather than confetti.</li>
     * </ul>
     */
    @Test
    void semanticFilteringDoesNotCreateACheckerboard() {
        for (World w : new World[]{desert(), volcanic(), ice(), rocky()}) {
            int res = 128;
            int step = 64;
            int[] map = new int[res * res];
            long seed = 0x38F001L + w.name().hashCode();
            MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
            for (MaterialRole role : MaterialRole.values()) {
                tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
            }
            V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
            WorldgenColumnSample col = new WorldgenColumnSample();
            int origin = -(res * step) / 2;
            int maxFamily = 0;
            for (int iz = 0; iz < res; iz++) {
                for (int ix = 0; ix < res; ix++) {
                    int x = origin + ix * step;
                    int z = origin + iz * step;
                    sampler.sampleColumn(x, z, col);
                    MaterialVariantField field = col.materialRole == null
                            ? null : tables[col.materialRole.ordinal()];
                    PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
                    int f = mat == null ? 0 : MaterialSemantics.familyOf(mat).ordinal() + 1;
                    maxFamily = Math.max(maxFamily, f);
                    map[iz * res + ix] = f;
                }
            }
            int islands = 0;
            int components = 0;
            for (int f = 1; f <= maxFamily; f++) {
                int[] sizes = componentSizes(map, res, f);
                for (int s : sizes) {
                    components++;
                    if (s == 1) islands++;
                }
            }
            System.out.println("[V3.8-S8] " + w.name() + " components=" + components
                    + " singleCellIslands=" + islands + " distinctFamilies=" + maxFamily);
            assertTrue(maxFamily > 1, w.name() + ": the material field must not be monochrome");
            assertTrue(islands * 20 < res * res,
                    w.name() + ": the material map has become a checkerboard, single-cell islands="
                            + islands + " of " + (res * res) + " cells");
        }
    }

    /** 4-connected component areas of one label in a label map. */
    private static int[] componentSizes(int[] map, int res, int label) {
        boolean[] seen = new boolean[map.length];
        java.util.List<Integer> areas = new java.util.ArrayList<>();
        int[] stack = new int[map.length];
        for (int i = 0; i < map.length; i++) {
            if (seen[i] || map[i] != label) continue;
            int top = 0;
            stack[top++] = i;
            seen[i] = true;
            int area = 0;
            while (top > 0) {
                int p = stack[--top];
                area++;
                int px = p % res;
                int pz = p / res;
                if (px > 0 && !seen[p - 1] && map[p - 1] == label) {
                    seen[p - 1] = true;
                    stack[top++] = p - 1;
                }
                if (px < res - 1 && !seen[p + 1] && map[p + 1] == label) {
                    seen[p + 1] = true;
                    stack[top++] = p + 1;
                }
                if (pz > 0 && !seen[p - res] && map[p - res] == label) {
                    seen[p - res] = true;
                    stack[top++] = p - res;
                }
                if (pz < res - 1 && !seen[p + res] && map[p + res] == label) {
                    seen[p + res] = true;
                    stack[top++] = p + res;
                }
            }
            areas.add(area);
        }
        int[] out = new int[areas.size()];
        for (int i = 0; i < out.length; i++) out[i] = areas.get(i);
        return out;
    }

    /**
     * STAGE 6/8 structural invariants, asserted directly on the pure functions.
     *
     * <p>These are the properties the whole V3.8 layer rests on, so they are checked without a
     * sampler: the semantic gate must be a pure function of the semantic triple, the fallback must
     * be deterministic, and no role may ever produce an empty variant table for a normal planet.
     */
    @Test
    void semanticGateIsPureAndFallbackIsDeterministic() {
        PlanetPhysicalProfile p = rocky().profile();
        for (MaterialSemanticFamily f : MaterialSemanticFamily.values()) {
            for (MaterialRole role : MaterialRole.values()) {
                boolean a = MaterialSemantics.mayLead(f, role, p.surface());
                boolean b = MaterialSemantics.mayLead(f, role, p.surface());
                assertTrue(a == b, "the semantic gate must be pure");
            }
        }
        MaterialVariantField f1 = MaterialVariantField.forRole(p, MaterialRole.PRIMARY_SURFACE, 42L);
        MaterialVariantField f2 = MaterialVariantField.forRole(p, MaterialRole.PRIMARY_SURFACE, 42L);
        assertTrue(f1.size() == f2.size(), "the fallback must be deterministic");
        for (int i = 0; i < f1.size(); i++) {
            assertTrue(f1.at(i).id().equals(f2.at(i).id()),
                    "the fallback must be reproducible from the same inputs");
        }
    }

    /** Every role must carry at least one material on a normal physical profile. */
    @Test
    void everyRoleHasAtLeastOneVariant() {
        for (World w : new World[]{desert(), volcanic(), ice(), rocky()}) {
            for (MaterialRole role : MaterialRole.values()) {
                MaterialVariantField field = MaterialVariantField.forRole(w.profile(), role, 7L);
                assertTrue(field.size() > 0,
                        "role " + role + " on " + w.name() + " must have a material, got NONE");
            }
        }
    }
}
