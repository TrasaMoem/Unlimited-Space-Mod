package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRules;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSpec;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ACT WORLDGEN V3.8 STAGE 0 - the READ-ONLY candidate-matrix audit.
 *
 * <p>The ACT forbids any production change before this table exists:
 *
 * <pre>
 *   ROLE | SURFACE FAMILY | CANDIDATE MATERIALS | ACTUAL BLOCKS | DEFAULT / FALLBACK
 * </pre>
 *
 * <p>Every number is produced by walking the REAL production chain
 * ({@code V3ColumnSampler -> SurfaceMaterialField.roleAt -> MaterialVariantField.variantFor}),
 * never by re-deriving admissibility in the test. Nothing here is a test-only branch.
 */
@Tag("worldgen")
@Tag("audit")
class Act38SemanticAudit {

    private static final int COLUMNS = 10_000;

    private record World(String name, PlanetPhysicalProfile profile, ReliefArchetype relief) {}

    static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                         double volc, double geo, double ero, double crystal,
                                         PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    private static World[] worlds() {
        return new World[]{
                new World("DESERT", profile(0.86, 0.06, 0.03, 0.55, 0.20, 0.25, 0.70, 0.02,
                        PlanetSurface.SOLID_DESERT), ReliefArchetype.CANYONLAND),
                new World("VOLCANIC", profile(0.88, 0.10, 0.05, 0.85, 0.90, 0.70, 0.40, 0.05,
                        PlanetSurface.SOLID_VOLCANIC), ReliefArchetype.VOLCANIC),
                new World("ICE", profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15,
                        PlanetSurface.SOLID_ICE), ReliefArchetype.GLACIAL),
                new World("ROCKY", profile(0.55, 0.20, 0.10, 0.75, 0.25, 0.30, 0.55, 0.10,
                        PlanetSurface.SOLID_ROCKY), ReliefArchetype.MOUNTAINOUS),
        };
    }


    /**
     * STAGE 0 - the mandatory table, for every reference world, over 10 000 real columns.
     */
    @Test
    void candidateMatrix() {
        for (World w : worlds()) {
            long seed = 0x38CE000L + w.name().hashCode();
            System.out.println();
            System.out.println("############ ROLE x SURFACE-FAMILY x CANDIDATE MATRIX ############");
            System.out.println("## world=" + w.name() + " surface=" + w.profile().surface()
                    + " seed=0x" + Long.toHexString(seed) + " columns=" + COLUMNS);

            System.out.println("## ---- LEGAL candidate sets per role (MaterialCatalog) ----");
            for (MaterialRole role : MaterialRole.values()) {
                List<MaterialSpec> legal = MaterialCatalog.admissibleCandidatesFor(w.profile(), role);
                System.out.println("##   " + role + " (" + legal.size() + "): " + ids(legal));
            }

            MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
            for (MaterialRole role : MaterialRole.values()) {
                tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
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

            System.out.println("## ---- MEASURED (role -> blocks / families over " + COLUMNS + " columns) ----");
            System.out.println("##   roleShare = " + pct(roleCount, COLUMNS));
            for (Map.Entry<MaterialRole, Integer> e : roleCount.entrySet()) {
                MaterialRole role = e.getKey();
                int n = e.getValue();
                MaterialVariantField field = tables[role.ordinal()];
                System.out.println("##   ROLE " + role + "  columns=" + n
                        + "  variantTableSize=" + field.size());
                System.out.println("##     ACTUAL BLOCKS   = " + roleBlocks.get(role));
                System.out.println("##     ACTUAL FAMILIES = " + roleFamilies.get(role));
                int none = roleBlocks.get(role).getOrDefault("NONE", 0);
                if (none > 0) {
                    System.out.println("##     *** NONE = " + none + " ("
                            + pct(Map.of("NONE", none), n) + " of this role) ***");
                }
            }
        }
    }


    /**
     * STAGE 0 (questions 5 / 6 / 7 / 8 / 9) - WHY red sand is admitted, and WHAT NONE means.
     *
     * <p>For every surface-capable catalogue entry this prints the exact admission reason, so
     * "legal == suitable" is PROVEN to be the defect rather than asserted. {@code NONE} is resolved
     * to its real cause: an empty variant table is {@code size() == 0}, i.e. a MISSING CANDIDATE
     * SET, not a missing fallback.
     */
    @Test
    void whyAreTheseAdmittedAndWhatDoesNoneMean() {
        System.out.println();
        System.out.println("############ ADMISSION REASONS PER SURFACE-CAPABLE MATERIAL ############");
        for (World w : worlds()) {
            System.out.println("## world=" + w.name() + " surface=" + w.profile().surface());
            for (MaterialSpec s : MaterialCatalog.all()) {
                boolean anySurfaceRole = s.canFill(MaterialRole.PRIMARY_SURFACE)
                        || s.canFill(MaterialRole.SECONDARY_SURFACE)
                        || s.canFill(MaterialRole.MOUNTAIN)
                        || s.canFill(MaterialRole.SEDIMENT)
                        || s.canFill(MaterialRole.GEOTHERMAL)
                        || s.canFill(MaterialRole.SOIL)
                        || s.canFill(MaterialRole.CRATER);
                if (!anySurfaceRole) continue;
                boolean compat = s.compatibleWith(w.profile());
                boolean primaryOk = MaterialRules.coherentForSurface(
                        s, MaterialRole.PRIMARY_SURFACE, w.profile().surface());
                List<MaterialRole> admitted = new ArrayList<>();
                for (MaterialRole role : MaterialRole.values()) {
                    if (MaterialCatalog.admissibleCandidatesFor(w.profile(), role).contains(s)) {
                        admitted.add(role);
                    }
                }
                System.out.println("##   " + pad(s.id(), 22)
                        + " block=" + pad(s.blockId(), 28)
                        + " family=" + pad(String.valueOf(s.family()), 18)
                        + " super=" + pad(String.valueOf(s.family().superFamily()), 12)
                        + " compat=" + pad(String.valueOf(compat), 5)
                        + " primaryCoherent=" + pad(String.valueOf(primaryOk), 5)
                        + " admittedRoles=" + admitted);
            }
        }

        System.out.println();
        System.out.println("############ NONE ROOT CAUSE PER ROLE ############");
        for (World w : worlds()) {
            for (MaterialRole role : MaterialRole.values()) {
                List<MaterialSpec> legal =
                        MaterialCatalog.admissibleCandidatesFor(w.profile(), role);
                MaterialVariantField f = MaterialVariantField.forRole(w.profile(), role, 1L, null);
                if (f.size() == 0) {
                    System.out.println("##   " + w.name() + " " + role
                            + " -> NONE cause=" + (legal.isEmpty()
                            ? "C: EMPTY CANDIDATE LIST (no legal candidate at all)"
                            : "?") + " legalSize=" + legal.size());
                }
            }
        }
    }

    /**
     * STAGE 7 - the exact semantic family, role eligibility and expected spatial condition of every
     * material the ACT names explicitly.
     */
    @Test
    void iceAndRockySemanticClassification() {
        System.out.println();
        System.out.println("############ ICE / ROCKY MATERIAL CLASSIFICATION ############");
        String[] ids = {"us.froststone", "van.packed_ice", "van.snow", "us.cryoclast",
                "us.astral_basalt", "van.stone", "van.blue_ice", "us.frost_soil",
                "van.red_sand", "us.red_dust", "van.gravel", "van.calcite", "van.sand"};
        for (String id : ids) {
            for (MaterialSpec s : MaterialCatalog.all()) {
                if (!s.id().equals(id)) continue;
                List<String> roles = new ArrayList<>();
                for (MaterialRole r : s.roles()) roles.add(r.name());
                roles.sort(String::compareTo);
                System.out.println("##   " + pad(s.id(), 20)
                        + " block=" + pad(s.blockId(), 28)
                        + " family=" + pad(String.valueOf(s.family()), 18)
                        + " super=" + pad(String.valueOf(s.family().superFamily()), 12)
                        + " visual=" + pad(String.valueOf(s.visualRole()), 12)
                        + " coldOnly=" + pad(String.valueOf(s.family().isColdOnly()), 5)
                        + " hotOnly=" + pad(String.valueOf(s.family().isHotOnly()), 5)
                        + " roles=" + roles);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String ids(List<MaterialSpec> specs) {
        if (specs.isEmpty()) return "<EMPTY>";
        List<String> out = new ArrayList<>();
        for (MaterialSpec s : specs) {
            out.add(s.id() + "[" + s.family().superFamily() + "/" + s.blockId() + "]");
        }
        return String.join(", ", out);
    }

    private static String pad(String s, int n) {
        if (s == null) s = "null";
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    private static <K> String pct(Map<K, Integer> counts, int n) {
        Map<K, Double> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<K, Integer> e : counts.entrySet()) {
            out.put(e.getKey(), Math.round(1000.0 * e.getValue() / Math.max(1, n)) / 10.0);
        }
        return out.toString();
    }


    /**
     * STAGE 6 - WHERE does NONE come from?
     *
     * <p>The V3.7 report claims up to 69% NONE on a ROCKY smoke sample. This probe sweeps a grid of
     * physical profiles x every role and reports, for every combination, whether the variant table
     * is EMPTY and how much of the sampled surface that role carries. It answers the ACT's
     * A/B/C/D/E/F/G question by measurement instead of by assumption.
     *
     * <p>MEASURED ANSWER: option <b>C + F</b> - an empty candidate set, compounded by the absence of
     * a fallback. {@link SurfaceMaterialField#roleAt} elects SOIL from a column's real wetness and
     * organic potential, but every soil material in the catalogue is gated behind
     * {@code requiresWater()} or a humidity window, so on an arid planet the environment elects a
     * role the catalogue cannot fill.
     */
    @Test
    void noneRootCauseSweep() {
        System.out.println();
        System.out.println("############ NONE SWEEP: profile grid x role ############");
        double[] temps = {0.05, 0.25, 0.45, 0.65, 0.85, 0.97};
        double[] hums = {0.02, 0.15, 0.35, 0.60, 0.90};
        PlanetSurface[] surfaces = {PlanetSurface.SOLID_ROCKY, PlanetSurface.SOLID_DESERT,
                PlanetSurface.SOLID_VOLCANIC, PlanetSurface.SOLID_ICE};
        for (PlanetSurface s : surfaces) {
            for (double t : temps) {
                for (double h : hums) {
                    PlanetPhysicalProfile p = profile(t, h, Math.max(0.02, h * 0.5), 0.6, 0.4, 0.3,
                            0.5, 0.2, s);
                    List<String> empty = new ArrayList<>();
                    for (MaterialRole role : MaterialRole.values()) {
                        if (MaterialVariantField.forRole(p, role, 1L, null).size() == 0) {
                            empty.add(role.name());
                        }
                    }
                    if (!empty.isEmpty()) {
                        System.out.println("##   surface=" + pad(s.name(), 14)
                                + " temp=" + pad(String.valueOf(t), 5)
                                + " hum=" + pad(String.valueOf(h), 5)
                                + " EMPTY variant tables: " + empty);
                    }
                }
            }
        }
        System.out.println("## ---- NONE MEASURED ON THE REAL CHAIN, per surface x temperature ----");
        for (PlanetSurface s : surfaces) {
            for (double t : temps) {
                World w = new World(s.name() + "@" + t, profile(t, 0.20, 0.10, 0.7, 0.25, 0.30,
                        0.55, 0.10, s), ReliefArchetype.MOUNTAINOUS);
                long seed = 0x38CE000L + s.ordinal() * 7919L + (long) (t * 1000);
                MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
                for (MaterialRole role : MaterialRole.values()) {
                    tables[role.ordinal()] = MaterialVariantField.forRole(w.profile(), role, seed, null);
                }
                V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, w.profile(), w.relief());
                WorldgenColumnSample col = new WorldgenColumnSample();
                Map<MaterialRole, Integer> roleCount = new EnumMap<>(MaterialRole.class);
                Map<MaterialRole, Integer> noneCount = new EnumMap<>(MaterialRole.class);
                for (int i = 0; i < 4000; i++) {
                    int x = (int) ((i * 397L) % 4000L) - 2000;
                    int z = (int) ((i * 641L) % 4000L) - 2000;
                    sampler.sampleColumn(x, z, col);
                    MaterialRole role = col.materialRole;
                    MaterialVariantField field = role == null ? null : tables[role.ordinal()];
                    PlanetMaterial mat = field == null ? null : field.variant(col, x, z);
                    roleCount.merge(role, 1, Integer::sum);
                    if (mat == null) noneCount.merge(role, 1, Integer::sum);
                }
                int totalNone = 0;
                for (Integer v : noneCount.values()) totalNone += v;
                if (totalNone > 0) {
                    System.out.println("##   " + w.name() + " NONE=" + totalNone + "/4000 = "
                            + pct(Map.of("NONE", totalNone), 4000) + " byRole=" + noneCount
                            + " roleShare=" + pct(roleCount, 4000));
                }
            }
        }
    }
}
