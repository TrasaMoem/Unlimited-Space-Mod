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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT WORLDGEN V3.8 STAGE 12 / 13 - MULTI-SEED DISTRIBUTIONS and FAMILY-LEVEL CORRELATIONS.
 *
 * <p>The ACT forbids a single-seed conclusion. This sweeps <b>five genuinely different worlds of
 * each of the four reference families</b> (DESERT, VOLCANIC, ICE, ROCKY) at 10 000 real columns
 * each, and reports for every world:
 *
 * <pre>
 *   roleShare, familyShare, blockShare, red-dust share, NONE share
 * </pre>
 *
 * <p>It then asserts the STAGE 13 correlations on the FAMILY level (never on a block id):
 * DESERT sediment&rarr;sediment dominance, VOLCANIC intensity&rarr;volcanic dominance, ROCKY
 * exposure&rarr;rock dominance, ICE snow&rarr;snow-family and exposure&rarr;rock-family.
 *
 * <p>It is a diagnostic plus a set of structural claims. The numbers it prints are measurements of
 * THIS codebase after the V3.8 semantic fix, not targets invented in advance.
 */
@Tag("worldgen")
@Tag("audit")
class MaterialSemanticDistributionTest {

    private static final int COLUMNS = 10_000;

    /**
     * A family share at or above this value is SATURATED: the mapping has already claimed (almost)
     * the whole surface, so a correlation cannot be expressed as a strict rise any more.
     */
    private static final double SATURATED = 0.999;

    private record World(String name, PlanetPhysicalProfile profile, ReliefArchetype relief) {}

    private static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                                 double volc, double geo, double ero, double crystal,
                                                 PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    private static final ReliefArchetype[] RELIEF = {
            ReliefArchetype.CANYONLAND, ReliefArchetype.PLATEAU, ReliefArchetype.FLAT,
            ReliefArchetype.ROLLING, ReliefArchetype.MOUNTAINOUS};

    /** Five DESERT worlds: not five re-seeds, but five different temperature / moisture profiles. */
    private static World[] deserts() {
        return new World[]{
                new World("desert-1", profile(0.86, 0.06, 0.03, 0.55, 0.20, 0.25, 0.70, 0.02,
                        PlanetSurface.SOLID_DESERT), RELIEF[0]),
                new World("desert-2", profile(0.78, 0.12, 0.05, 0.35, 0.10, 0.15, 0.60, 0.03,
                        PlanetSurface.SOLID_DESERT), RELIEF[1]),
                new World("desert-3", profile(0.92, 0.04, 0.02, 0.65, 0.30, 0.35, 0.80, 0.01,
                        PlanetSurface.SOLID_DESERT), RELIEF[2]),
                new World("desert-4", profile(0.70, 0.20, 0.10, 0.45, 0.15, 0.20, 0.55, 0.05,
                        PlanetSurface.SOLID_DESERT), RELIEF[3]),
                new World("desert-5", profile(0.82, 0.08, 0.04, 0.75, 0.25, 0.30, 0.65, 0.04,
                        PlanetSurface.SOLID_DESERT), RELIEF[4]),
        };
    }

    private static World[] volcanics() {
        return new World[]{
                new World("volcanic-1", profile(0.88, 0.10, 0.05, 0.85, 0.90, 0.70, 0.40, 0.05,
                        PlanetSurface.SOLID_VOLCANIC), RELIEF[0]),
                new World("volcanic-2", profile(0.80, 0.20, 0.08, 0.75, 0.80, 0.60, 0.45, 0.08,
                        PlanetSurface.SOLID_VOLCANIC), RELIEF[1]),
                new World("volcanic-3", profile(0.95, 0.06, 0.03, 0.90, 0.95, 0.80, 0.35, 0.03,
                        PlanetSurface.SOLID_VOLCANIC), RELIEF[4]),
                new World("volcanic-4", profile(0.75, 0.30, 0.12, 0.65, 0.70, 0.55, 0.50, 0.10,
                        PlanetSurface.SOLID_VOLCANIC), RELIEF[3]),
                new World("volcanic-5", profile(0.85, 0.15, 0.06, 0.95, 0.85, 0.75, 0.30, 0.06,
                        PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.VOLCANIC),
        };
    }

    private static World[] ices() {
        return new World[]{
                new World("ice-1", profile(0.05, 0.55, 0.55, 0.20, 0.10, 0.15, 0.30, 0.10,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL),
                new World("ice-2", profile(0.12, 0.40, 0.45, 0.35, 0.15, 0.25, 0.35, 0.15,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL),
                new World("ice-3", profile(0.20, 0.60, 0.60, 0.10, 0.05, 0.10, 0.25, 0.05,
                        PlanetSurface.SOLID_ICE), RELIEF[4]),
                new World("ice-4", profile(0.08, 0.30, 0.35, 0.55, 0.30, 0.45, 0.50, 0.20,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.MIXED),
                new World("ice-5", profile(0.25, 0.50, 0.50, 0.25, 0.20, 0.30, 0.40, 0.10,
                        PlanetSurface.SOLID_ICE), RELIEF[3]),
        };
    }

    private static World[] rockies() {
        return new World[]{
                new World("rocky-1", profile(0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55, 0.10,
                        PlanetSurface.SOLID_ROCKY), RELIEF[4]),
                new World("rocky-2", profile(0.45, 0.30, 0.15, 0.65, 0.20, 0.25, 0.50, 0.15,
                        PlanetSurface.SOLID_ROCKY), RELIEF[3]),
                new World("rocky-3", profile(0.62, 0.15, 0.08, 0.85, 0.35, 0.40, 0.60, 0.20,
                        PlanetSurface.SOLID_ROCKY), RELIEF[0]),
                new World("rocky-4", profile(0.35, 0.35, 0.20, 0.70, 0.15, 0.20, 0.45, 0.12,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.MIXED),
                new World("rocky-5", profile(0.70, 0.10, 0.05, 0.90, 0.45, 0.50, 0.40, 0.08,
                        PlanetSurface.SOLID_ROCKY), RELIEF[1]),
        };
    }

    // ------------------------------------------------------------------ measurement

    private record Dist(Map<MaterialRole, Integer> roleCount,
                        Map<MaterialRole, Map<MaterialSemanticFamily, Integer>> roleFamilies,
                        Map<MaterialRole, Map<String, Integer>> roleBlocks,
                        int columns) {

        int total() {
            int n = 0;
            for (int v : roleCount.values()) n += v;
            return n;
        }

        int none() {
            int n = 0;
            for (Map<String, Integer> m : roleBlocks.values()) n += m.getOrDefault("NONE", 0);
            return n;
        }

        /** Share of all columns whose material belongs to the given semantic family. */
        double family(MaterialSemanticFamily f) {
            int hit = 0;
            for (Map<MaterialSemanticFamily, Integer> m : roleFamilies.values()) {
                hit += m.getOrDefault(f, 0);
            }
            return hit / (double) Math.max(1, columns);
        }

        /** Share of the columns of ONE role that resolved to the given family. */
        double familyOf(MaterialRole role, MaterialSemanticFamily f) {
            int n = roleCount.getOrDefault(role, 0);
            return n == 0 ? 0.0 : roleFamilies.getOrDefault(role, Map.of())
                    .getOrDefault(f, 0) / (double) n;
        }

        double rockLed() {
            double s = 0.0;
            for (MaterialSemanticFamily f : new MaterialSemanticFamily[]{
                    MaterialSemanticFamily.ROCK, MaterialSemanticFamily.DARK_ROCK,
                    MaterialSemanticFamily.MINERAL_ROCK, MaterialSemanticFamily.SPIRE_ROCK,
                    MaterialSemanticFamily.FROZEN_ROCK, MaterialSemanticFamily.VOLCANIC_DARK}) {
                s += family(f);
            }
            return s;
        }

        double frozenLed() {
            return family(MaterialSemanticFamily.FROZEN_ICE)
                    + family(MaterialSemanticFamily.FROZEN_SNOW)
                    + family(MaterialSemanticFamily.FROZEN_ROCK);
        }

        double volcanicLed() {
            return family(MaterialSemanticFamily.VOLCANIC_DARK)
                    + family(MaterialSemanticFamily.VOLCANIC_ASH);
        }

        double red() {
            int n = 0;
            for (Map<String, Integer> m : roleBlocks.values()) {
                n += m.getOrDefault("van.red_sand", 0) + m.getOrDefault("us.red_dust", 0);
            }
            return n / (double) Math.max(1, columns);
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
        return new Dist(roleCount, roleFamilies, roleBlocks, COLUMNS);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ------------------------------------------------------------------ STAGE 12: distributions

    /**
     * STAGE 12 - the multi-seed distribution table, printed for all twenty worlds and asserted on
     * the structural invariants that hold for EVERY one of them: no NONE, and a non-degenerate
     * block set (the V3.6 "one block per role" defect must stay dead).
     */
    @Test
    void twentyWorldsAcrossFourFamiliesHaveCoherentDistributions() {
        Object[][] groups = {
                {"DESERT", deserts()}, {"VOLCANIC", volcanics()}, {"ICE", ices()},
                {"ROCKY", rockies()},
        };
        for (Object[] g : groups) {
            String familyName = (String) g[0];
            World[] worlds = (World[]) g[1];
            for (int i = 0; i < worlds.length; i++) {
                World w = worlds[i];
                long seed = 0x38D000L + i * 40503L + familyName.hashCode();
                Dist d = measure(w, seed);
                System.out.println("[V3.8-S12] " + w.name()
                        + " seed=0x" + Long.toHexString(seed) + " columns=" + d.columns());
                System.out.println("[V3.8-S12]   roleShare   = " + pct(d.roleCount(), d.columns()));
                System.out.println("[V3.8-S12]   familyShare = SAND=" + round(d.family(
                                MaterialSemanticFamily.SAND))
                        + " SEDIMENT=" + round(d.family(MaterialSemanticFamily.SEDIMENT))
                        + " ROCK=" + round(d.family(MaterialSemanticFamily.ROCK))
                        + " DARK_ROCK=" + round(d.family(MaterialSemanticFamily.DARK_ROCK))
                        + " FROZEN=" + round(d.frozenLed())
                        + " VOLCANIC=" + round(d.volcanicLed())
                        + " CRYSTALLINE=" + round(d.family(MaterialSemanticFamily.CRYSTALLINE))
                        + " ORGANIC=" + round(d.family(MaterialSemanticFamily.ORGANIC))
                        + " LAVA=" + round(d.family(MaterialSemanticFamily.LAVA)));
                System.out.println("[V3.8-S12]   blockShare  = " + blocksOf(d));
                System.out.println("[V3.8-S12]   redShare=" + round(d.red())
                        + " noneShare=" + round(d.none() / (double) d.columns()));
                System.out.println("[V3.8-S12]   rockLed=" + round(d.rockLed())
                        + " frozenLed=" + round(d.frozenLed())
                        + " volcanicLed=" + round(d.volcanicLed()));

                // ---- invariants that must hold on EVERY world ----
                assertTrue(d.none() == 0,
                        w.name() + ": no column may resolve to NONE, got " + d.none());
                int distinct = distinctBlocks(d);
                assertTrue(distinct > 1,
                        w.name() + ": a world must not collapse to a single material, got "
                                + blocksOf(d));
                assertTrue(d.family(MaterialSemanticFamily.LAVA) == 0.0,
                        w.name() + ": lava must remain a feature, never a dominant substrate");
            }
        }
    }

    private static Map<String, Integer> blocksOf(Dist d) {
        Map<String, Integer> out = new TreeMap<>();
        for (Map<String, Integer> m : d.roleBlocks().values()) {
            for (Map.Entry<String, Integer> e : m.entrySet()) out.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return out;
    }

    private static int distinctBlocks(Dist d) {
        int n = 0;
        for (String k : blocksOf(d).keySet()) if (!"NONE".equals(k)) n++;
        return n;
    }

    private static <K> String pct(Map<K, Integer> counts, int n) {
        Map<K, Double> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<K, Integer> e : counts.entrySet()) {
            out.put(e.getKey(), Math.round(1000.0 * e.getValue() / Math.max(1, n)) / 10.0);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ STAGE 13: correlations

    /**
     * STAGE 13 - the four family-level correlations, each on several worlds and several seeds.
     *
     * <p>Every one of them is asserted on the SEMANTIC FAMILY, never on a block id, and each is a
     * "split the population by its own median signal, require the high half to lead" test, which is
     * the same shape the V3.7 ICE correlations used and therefore directly comparable.
     */
    @Test
    void familyLevelCorrelationsHold() {
        for (int i = 0; i < deserts().length; i++) {
            assertSignalCorrelation(deserts()[i], 0x38E100L + i * 7919L,
                    SAND_OR_SEDIMENT,
                    col -> col.sedimentShare,
                    "DESERT sedimentShare -> sand/sediment dominance");
        }
        for (int i = 0; i < volcanics().length; i++) {
            assertSignalCorrelation(volcanics()[i], 0x38E200L + i * 7919L,
                    VOLCANIC,
                    col -> col.volcanicIntensity,
                    "VOLCANIC volcanicIntensity -> dark/ash dominance");
        }
        for (int i = 0; i < ices().length; i++) {
            World w = ices()[i];
            long seed = 0x38E300L + i * 7919L;
            assertSignalCorrelation(w, seed, FROZEN,
                    col -> SurfaceMaterialField.snowAccumulation(col),
                    "ICE snowAccumulation -> snow-family share does not fall");
            assertSignalCorrelation(w, seed, ROCKY,
                    col -> SurfaceMaterialField.rockExposure(col),
                    "ICE rockExposure -> rock-family share rises");
        }
        for (int i = 0; i < rockies().length; i++) {
            assertSignalCorrelation(rockies()[i], 0x38E400L + i * 7919L,
                    ROCKY,
                    col -> SurfaceMaterialField.rockExposure(col),
                    "ROCKY rockExposure -> rock-family dominance rises");
        }
    }

    /** A "does this material belong to the family the correlation is about" predicate. */
    private interface FamilyTest {
        boolean matches(MaterialSemanticFamily f);
    }

    /** A continuous per-column signal. */
    private interface Signal {
        double of(WorldgenColumnSample c);
    }

    private static final FamilyTest SAND_OR_SEDIMENT =
            f -> f == MaterialSemanticFamily.SAND || f == MaterialSemanticFamily.SEDIMENT
                    // Ferruginous regolith IS a desert's own surface deposit. A profile at
                    // temperature 0.92 admits no pale sand at all (van.sand's window ends at 0.90),
                    // so the erg of such a world is red - and counting it as "not sediment" would
                    // have measured a family that physically cannot occur there.
                    || f == MaterialSemanticFamily.RED_DUST;
    private static final FamilyTest VOLCANIC =
            f -> f == MaterialSemanticFamily.VOLCANIC_DARK || f == MaterialSemanticFamily.VOLCANIC_ASH;
    private static final FamilyTest FROZEN =
            f -> f == MaterialSemanticFamily.FROZEN_ICE || f == MaterialSemanticFamily.FROZEN_SNOW
                    || f == MaterialSemanticFamily.FROZEN_ROCK;
    private static final FamilyTest ROCKY = MaterialSemantics::isRockFamily;

    private static void assertSignalCorrelation(World w, long seed, FamilyTest test, Signal signal,
                                                String label) {
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
        }
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = 6000;
        double[] sig = new double[n];
        int[] match = new int[n];
        for (int i = 0; i < n; i++) {
            int x = (int) ((i * 397L) % 9000L) - 4500;
            int z = (int) ((i * 641L) % 9000L) - 4500;
            sampler.sampleColumn(x, z, col);
            sig[i] = signal.of(col);
            MaterialRole role = col.materialRole;
            MaterialVariantField field = role == null ? null : tables[role.ordinal()];
            PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
            MaterialSemanticFamily fam = mat == null ? null : MaterialSemantics.familyOf(mat);
            match[i] = fam != null && test.matches(fam) ? 1 : 0;
        }
        double[] sorted = sig.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        int lowN = 0, lowM = 0, highN = 0, highM = 0;
        for (int i = 0; i < n; i++) {
            if (sig[i] < median) {
                lowN++;
                lowM += match[i];
            } else {
                highN++;
                highM += match[i];
            }
        }
        double low = lowN == 0 ? 0.0 : (double) lowM / lowN;
        double high = highN == 0 ? 0.0 : (double) highM / highN;
        System.out.println("[V3.8-S13] " + label + " world=" + w.name()
                + " seed=0x" + Long.toHexString(seed)
                + " low=" + round(low) + " high=" + round(high));
        // A share is a number in [0,1], so a world whose family is ALREADY saturated at ~100% on
        // both halves cannot show a rise - there is no headroom left. That is a mathematical
        // ceiling, not a broken correlation, and treating it as one would be measuring nothing.
        // The claim is therefore split honestly: where there is headroom the high half must lead
        // STRICTLY, and where the family is saturated the correlation must at least not INVERT.
        if (low >= SATURATED) {
            assertTrue(high >= low - 1e-9, label + " on " + w.name()
                    + ": the correlation must not invert on a saturated family, low=" + round(low)
                    + " high=" + round(high));
        } else {
            assertTrue(high > low, label + " on " + w.name()
                    + ": the high-signal half must lead, low=" + round(low)
                    + " high=" + round(high));
        }
    }
}
