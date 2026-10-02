package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.7 STAGE 9 and STAGE 10 - the DISTRIBUTION and the SMOKE diagnostics.
 *
 * <h2>What is asserted, and what is deliberately left open</h2>
 * <p>The ACT explicitly forbids inventing a target ("exactly 5 blocks", "exactly 20%") before the
 * real palette is established. So the only structural claim asserted here is the one the V3.6 audit
 * falsified: <b>no role may collapse to a single block</b>. Everything else - the number of
 * distinct blocks, the family mix, the red-dust share - is MEASURED and printed, so the numbers in
 * the report are measurements of this codebase rather than aspirations.
 *
 * <p>Stage 9 sweeps five ICE planets at 10 000 columns each. Stage 10 is a diagnostic smoke test
 * over three planets each of DESERT, VOLCANIC and ROCKY, reporting whether an inappropriate
 * material (calcite or gravel on a desert, bright material on a volcanic world, red dust taking
 * over a rocky world) becomes dominant.
 */
@Tag("worldgen")
@Tag("audit")
class MaterialDistributionDiagnosticTest {

    private static final int COLUMNS = 10_000;

    private static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                                double volc, double geo, double ero, double crystal,
                                                PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    private record World(String name, PlanetPhysicalProfile profile, ReliefArchetype relief) {}

    /** Five genuinely different ICE worlds, not five re-seeds of one profile. */
    private static World[] iceWorlds() {
        return new World[]{
                new World("ice-a", profile(0.05, 0.55, 0.55, 0.20, 0.10, 0.15, 0.30, 0.10,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL),
                new World("ice-b", profile(0.12, 0.40, 0.45, 0.35, 0.15, 0.25, 0.35, 0.15,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL),
                new World("ice-c", profile(0.20, 0.60, 0.60, 0.10, 0.05, 0.10, 0.25, 0.05,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.MOUNTAINOUS),
                new World("ice-d", profile(0.08, 0.30, 0.35, 0.55, 0.30, 0.45, 0.50, 0.20,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.MIXED),
                new World("ice-e", profile(0.25, 0.50, 0.50, 0.25, 0.20, 0.30, 0.40, 0.10,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.ROLLING),
        };
    }

    private static World[] smokeWorlds() {
        return new World[]{
                // DESERT: hot, arid, low erosion.
                new World("desert-a", profile(0.86, 0.06, 0.03, 0.55, 0.20, 0.25, 0.70, 0.02,
                        PlanetSurface.SOLID_DESERT), ReliefArchetype.CANYONLAND),
                new World("desert-b", profile(0.78, 0.12, 0.05, 0.35, 0.10, 0.15, 0.60, 0.03,
                        PlanetSurface.SOLID_DESERT), ReliefArchetype.PLATEAU),
                new World("desert-c", profile(0.92, 0.04, 0.02, 0.65, 0.30, 0.35, 0.80, 0.01,
                        PlanetSurface.SOLID_DESERT), ReliefArchetype.FLAT),
                // VOLCANIC: hot, tectonically active, ash and basalt.
                new World("volcanic-a", profile(0.88, 0.10, 0.05, 0.85, 0.90, 0.70, 0.40, 0.05,
                        PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.VOLCANIC),
                new World("volcanic-b", profile(0.80, 0.20, 0.08, 0.75, 0.80, 0.60, 0.45, 0.08,
                        PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.VOLCANIC),
                new World("volcanic-c", profile(0.95, 0.06, 0.03, 0.90, 0.95, 0.80, 0.35, 0.03,
                        PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.MOUNTAINOUS),
                // ROCKY: temperate-to-hot, very rocky, little sediment.
                new World("rocky-a", profile(0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55, 0.10,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.MOUNTAINOUS),
                new World("rocky-b", profile(0.45, 0.30, 0.15, 0.65, 0.20, 0.25, 0.50, 0.15,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.ROLLING),
                new World("rocky-c", profile(0.62, 0.15, 0.08, 0.85, 0.35, 0.40, 0.60, 0.20,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.CANYONLAND),
        };
    }

    /** The full measured distribution of one world: role, block and family shares. */
    private record Distribution(String name, Map<MaterialRole, Integer> roleCount,
                                Map<MaterialRole, Map<String, Integer>> roleBlocks,
                                Map<MaterialRole, Map<String, Integer>> roleFamilies,
                                int columns) {

        /** Every block used by the roles that carry a real share of the surface. */
        Map<String, Integer> allBlocks() {
            Map<String, Integer> out = new TreeMap<>();
            for (Map<String, Integer> m : roleBlocks.values()) {
                for (Map.Entry<String, Integer> e : m.entrySet()) {
                    out.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            return out;
        }

        /** The blocks of ONE role, or an empty map when the role never fires. */
        Map<String, Integer> blocksOf(MaterialRole role) {
            return roleBlocks.getOrDefault(role, Map.of());
        }

        Map<String, Integer> familiesOf(MaterialRole role) {
            return roleFamilies.getOrDefault(role, Map.of());
        }
    }

    private static Distribution measure(World w, long seed) {
        PlanetGeologyPalette palette = PlanetGeologyPalette.create(seed, w.profile(),
                GeologicalProvinceMap.create(seed, w.profile()),
                PlanetColorTheme.select(TemperatureBand.of(w.profile().temperature()),
                        ClimateArchetypeSelector.create(seed, w.profile()), seed));
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed,
                    palette.materialFor(role));
        }
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
        WorldgenColumnSample col = new WorldgenColumnSample();

        Map<MaterialRole, Integer> roleCount = new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, Map<String, Integer>> roleBlocks = new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, Map<String, Integer>> roleFamilies = new EnumMap<>(MaterialRole.class);
        for (int i = 0; i < COLUMNS; i++) {
            int x = (int) ((i * 397L) % 9000L) - 4500;
            int z = (int) ((i * 641L) % 9000L) - 4500;
            sampler.sampleColumn(x, z, col);
            MaterialRole role = col.materialRole;
            MaterialVariantField field = role == null ? null : tables[role.ordinal()];
            PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
            roleCount.merge(role, 1, Integer::sum);
            roleBlocks.computeIfAbsent(role, k -> new TreeMap<>())
                    .merge(mat == null ? "NONE" : mat.id(), 1, Integer::sum);
            roleFamilies.computeIfAbsent(role, k -> new TreeMap<>())
                    .merge(mat == null || mat.family() == null ? "NONE" : mat.family().name(),
                            1, Integer::sum);
        }
        return new Distribution(w.name(), roleCount, roleBlocks, roleFamilies, COLUMNS);
    }

    // ------------------------------------------------------------------ STAGE 9: ICE

    /**
     * STAGE 9 - five ICE planets, 10 000 columns each. The full blockId, material-family and role
     * distributions are printed for every world, and the ONE structural claim the V3.6 audit
     * falsified is asserted: a role that carries a real share of the surface may not collapse to a
     * single block.
     */
    @Test
    void fiveIcePlanetsAreNoLongerOneBlockPerRole() {
        World[] worlds = iceWorlds();
        assertTrue(worlds.length >= 5, "the ACT mandates at least five ICE worlds");
        int planetIndex = 0;
        for (World w : worlds) {
            long seed = 0x1CE000L + planetIndex * 40503L;
            Distribution d = measure(w, seed);
            System.out.println("[V3.7-S9] " + w.name() + " seed=0x" + Long.toHexString(seed)
                    + " columns=" + d.columns());
            System.out.println("[V3.7-S9]   roleShare   = " + pct(d.roleCount(), d.columns()));
            for (Map.Entry<MaterialRole, Integer> e : d.roleCount().entrySet()) {
                System.out.println("[V3.7-S9]   " + e.getKey() + " blocks = "
                        + pct(d.blocksOf(e.getKey()), e.getValue()));
                System.out.println("[V3.7-S9]   " + e.getKey() + " family = "
                        + pct(d.familiesOf(e.getKey()), e.getValue()));
            }
            System.out.println("[V3.7-S9]   allBlocks   = " + d.allBlocks());
            for (Map.Entry<MaterialRole, Integer> e : d.roleCount().entrySet()) {
                // A role needs a real share before "one block" is a meaningful claim about it.
                if (e.getValue() < 500) continue;
                assertTrue(d.blocksOf(e.getKey()).size() > 1,
                        w.name() + ": role " + e.getKey() + " carries " + e.getValue()
                                + " columns but resolves to a single block "
                                + d.blocksOf(e.getKey()));
            }
            planetIndex++;
        }
    }

    // ------------------------------------------------------------------ STAGE 10: smoke tests

    /**
     * STAGE 10 - the DESERT / VOLCANIC / ROCKY smoke diagnostics.
     *
     * <p>These are DIAGNOSTICS, not a final redesign. They measure whether the spatial variation
     * introduced for ICE broke - or fixed - the other visual families, and they print the evidence
     * that the numbers in the report come from. The only hard assertion is the one that is
     * physically indefensible in any case: a material must never become the DOMINANT surface of a
     * world whose own surface class forbids it.
     */
    @Test
    void desertVolcanicAndRockySmokeDiagnostics() {
        for (World w : smokeWorlds()) {
            long seed = 0x2CE000L + (w.name().hashCode() & 0xFFFF);
            Distribution d = measure(w, seed);
            System.out.println("[V3.7-S10] " + w.name() + " surface=" + w.profile().surface()
                    + " columns=" + d.columns());
            System.out.println("[V3.7-S10]   roleShare = " + pct(d.roleCount(), d.columns()));
            System.out.println("[V3.7-S10]   blocks   = " + d.allBlocks());
            for (Map.Entry<MaterialRole, Integer> e : d.roleCount().entrySet()) {
                if (e.getValue() < 200) continue;
                System.out.println("[V3.7-S10]   " + e.getKey() + " family = "
                        + pct(d.familiesOf(e.getKey()), e.getValue()));
            }
            assertNoForbiddenDominant(w, d);
        }
    }

    /**
     * The single indefensible outcome: a material that the planet's own surface class forbids
     * taking over the dominant surface. Everything else is reported, not forbidden.
     */
    private static void assertNoForbiddenDominant(World w, Distribution d) {
        Map<String, Integer> blocks = d.allBlocks();
        if (blocks.isEmpty()) return;
        String top = blocks.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        int topShare = blocks.get(top) * 100 / Math.max(1, d.columns());
        System.out.println("[V3.7-S10]   dominantBlock = " + top + " at " + topShare + "%");
        switch (w.profile().surface()) {
            case SOLID_DESERT -> assertTrue(!isCalcite(top) && !isGravel(top),
                    w.name() + ": calcite / gravel must not dominate a desert, got " + top);
            case SOLID_VOLCANIC -> assertTrue(!isBright(top),
                    w.name() + ": a bright / icy material must not dominate a volcanic world, got "
                            + top);
            case SOLID_ICE -> assertTrue(!isBright(top),
                    w.name() + ": a bright non-frozen material must not dominate an ice shell, got "
                            + top);
            default -> {
                // SOLID_ROCKY: red dust is legal geology but must not BECOME the world. Reported
                // above; the hard claim is only that the world is not monochrome.
                assertTrue(blocks.size() > 1,
                        w.name() + ": a rocky world must not collapse to one block, got " + blocks);
            }
        }
    }

    private static boolean isCalcite(String materialId) {
        return "van.calcite".equals(materialId);
    }

    private static boolean isGravel(String materialId) {
        return "van.gravel".equals(materialId);
    }

    /** A material that is visually bright on a dark world. */
    private static boolean isBright(String materialId) {
        return switch (materialId) {
            case "van.snow", "us.frost_soil", "us.froststone", "van.sand", "van.calcite",
                 "us.salt_crust", "us.luminite_rock" -> true;
            default -> false;
        };
    }

    private static <K> String pct(Map<K, Integer> counts, int n) {
        Map<K, Double> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<K, Integer> e : counts.entrySet()) {
            out.put(e.getKey(), Math.round(1000.0 * e.getValue() / Math.max(1, n)) / 10.0);
        }
        return out.toString();
    }
}
