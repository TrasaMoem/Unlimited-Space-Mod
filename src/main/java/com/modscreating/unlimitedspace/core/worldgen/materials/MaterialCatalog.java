package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The candidate material catalogue (R16 planet-diversity foundation).
 *
 * <p>Single, pure-domain registry of every material the generator may place, mixing:
 *
 * <ul>
 *   <li><b>Unlimited Space custom blocks</b> ({@code unlimitedspace:...}) — the 14-material
 *       proof-of-concept library (Astral/Ember Basalt, Froststone, Rustrock, Cinderstone,
 *       Prismstone, Sulfurstone, Impactite, Cryoclast, Luminite Rock, Red Dust, Frost Soil,
 *       Salt Crust, Crystalstone);</li>
 *   <li><b>vanilla blocks</b> ({@code minecraft:...}) reused as geological materials;</li>
 *   <li><b>Creating Space blocks</b> resolved ONLY through the registry-guarded adapter
 *       ({@code PlanetBlocks}), never assumed present by the core.</li>
 * </ul>
 *
 * <p>The catalogue is a stable immutable list; its order only affects deterministic
 * tie-breaking, never correctness. Admissibility is always decided by {@link MaterialRules}.
 */
public final class MaterialCatalog {

    /** Proof-of-concept custom material count (diagnostics/tests). */
    public static final int CUSTOM_COUNT = 14;

    private static final List<MaterialSpec> ALL = buildAll();
    private static final List<MaterialSpec> ALL_VIEW = Collections.unmodifiableList(ALL);

    private MaterialCatalog() {}

    /** Every catalogue entry in stable order. */
    public static List<MaterialSpec> all() {
        return ALL_VIEW;
    }

    /** Only the custom Unlimited Space entries. */
    public static List<MaterialSpec> custom() {
        List<MaterialSpec> out = new ArrayList<>(CUSTOM_COUNT);
        for (MaterialSpec s : ALL) {
            if (s.blockId().startsWith("unlimitedspace:")) out.add(s);
        }
        return List.copyOf(out);
    }

    /** Stable candidate list for a single palette role. */
    public static List<MaterialSpec> candidatesFor(MaterialRole role) {
        List<MaterialSpec> out = new ArrayList<>();
        for (MaterialSpec s : ALL) {
            if (s.canFill(role)) out.add(s);
        }
        return List.copyOf(out);
    }

    /**
     * V3.2 PHASE 6: the candidates that are BOTH physically admissible
     * ({@link MaterialSpec#compatibleWith}) AND coherent for this planet's surface class in this
     * role ({@link MaterialRules#coherentForSurface}).
     *
     * <p>Every selection path goes through here, so the primary surface of a planet can never be
     * an off-language material, while accents, rare blocks and underground fill stay free to be the
     * genuine geological exceptions the architecture wants.
     */
    private static List<MaterialSpec> admissibleCandidates(PlanetPhysicalProfile profile,
                                                            MaterialRole role) {
        List<MaterialSpec> out = new ArrayList<>();
        for (MaterialSpec s : candidatesFor(role)) {
            if (!s.compatibleWith(profile)) continue;
            if (!MaterialRules.coherentForSurface(s, role, profile.surface())) continue;
            out.add(s);
        }
        return out;
    }

    /**
     * ACT V3.7: the admissible candidate list of a single role, exposed.
     *
     * <p>It is the SAME list {@link #select} / {@link #selectThemed} draw from - physical
     * admissibility ({@link MaterialSpec#compatibleWith}) AND surface-class coherence
     * ({@link MaterialRules#coherentForSurface}) - so there is still exactly ONE definition of
     * "may this material appear here". The spatial variant authority
     * ({@code MaterialVariantField}) builds its bounded per-role variant table from this list
     * instead of re-deriving admissibility, which is what keeps the variant layer from ever
     * inventing a material the catalogue would refuse.
     */
    public static List<MaterialSpec> admissibleCandidatesFor(PlanetPhysicalProfile profile,
                                                             MaterialRole role) {
        return admissibleCandidates(profile, role);
    }

    /**
     * ACT-A ITEM 1c - the SAME list on the TEMPERATURE-RELAXED physical gate
     * ({@link MaterialRules#isCompatibleRelaxingTemperature}), with the surface-class coherence
     * veto still applied.
     *
     * <p>This is the candidate pool the relaxed semantic tier draws from. It exists because the
     * cryogenic climate windows are calibrated on the log-Kelvin axis, where the 273.15 K frost
     * point sits at {@code temperature01 = 0.439} while every frozen material tops out at
     * 0.20..0.32 - so a genuinely frozen world (measured: 159.8 K, {@code temperature01 = 0.33})
     * had <b>zero</b> physically admissible frozen materials, and the old fallback answered that
     * with sand and calcite. The family-level gate is <b>not</b> relaxed here: every consumer of
     * this list still has to pass {@link MaterialSemantics#mayLead} with the real surface class.
     */
    public static List<MaterialSpec> temperatureRelaxedCandidatesFor(PlanetPhysicalProfile profile,
                                                                     MaterialRole role) {
        if (profile == null || role == null) {
            return List.of();
        }
        List<MaterialSpec> out = new ArrayList<>();
        for (MaterialSpec s : candidatesFor(role)) {
            if (!MaterialRules.isCompatibleRelaxingTemperature(s, profile)) continue;
            if (!MaterialRules.coherentForSurface(s, role, profile.surface())) continue;
            out.add(s);
        }
        return List.copyOf(out);
    }

    /**
     * Deterministically pick one candidate for a role, respecting planet compatibility.
     */
    public static PlanetMaterial select(PlanetPhysicalProfile profile, MaterialRole role,
                                        long roleSeed) {
        if (profile == null || role == null) return null;
        List<MaterialSpec> compatible = admissibleCandidates(profile, role);
        if (compatible.isEmpty()) return null;
        return weightedPick(compatible, role, roleSeed);
    }

    /**
     * R18: province-aware selection. When {@code province} is non-null, candidates are first
     * filtered by {@link MaterialRules#isCoherentWithProvince} so a CRATER province prefers
     * impact materials, a VOLCANIC province volcanic/basaltic ones, a GLACIAL province frozen
     * ones, etc. The ordinary per-profile rules are still applied on top, so a material that is
     * physically impossible on this planet is still dropped. Weighted rarity-pick is unchanged.
     */
    public static PlanetMaterial selectFor(PlanetPhysicalProfile profile,
                                           com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince province,
                                           MaterialRole role, long roleSeed) {
        if (profile == null || role == null) return null;
        List<MaterialSpec> compatible = new ArrayList<>();
        for (MaterialSpec s : admissibleCandidates(profile, role)) {
            if (province != null && !MaterialRules.isCoherentWithProvince(s, province)) continue;
            compatible.add(s);
        }
        if (compatible.isEmpty()) {
            // A too-strict province filter shouldn't empty a province: fall back to the
            // ordinary (profile-only) selection so the province still has a surface.
            return select(profile, role, roleSeed);
        }
        return weightedPick(compatible, role, roleSeed);
    }

    /**
     * R21: THEME-WEIGHTED selection. Same pipeline as {@link #select}, but every candidate's
     * weight is multiplied by the planet's {@link PlanetColorTheme} weight for its visual
     * role. This makes the planetary color palette dominant (70–85% dominant material) while
     * still allowing controlled off-theme exceptions (rare accents) instead of random colors.
     */
    public static PlanetMaterial selectThemed(PlanetPhysicalProfile profile, MaterialRole role,
                                              long roleSeed, PlanetColorTheme theme) {
        if (profile == null || role == null) return null;
        List<MaterialSpec> compatible = admissibleCandidates(profile, role);
        if (compatible.isEmpty()) return null;
        if (theme == null) return weightedPick(compatible, role, roleSeed);
        double total = 0.0;
        double[] weights = new double[compatible.size()];
        for (int i = 0; i < compatible.size(); i++) {
            MaterialSpec s = compatible.get(i);
            double rarityW = 1.0 - 0.7 * s.rarity();
            // Theme dominance: on-theme visuals are strongly boosted, off-theme ones survive
            // only as rare accents (a hard ban would kill geological honesty).
            weights[i] = rarityW * (0.15 + theme.weightFor(s.visualRole()));
            total += weights[i];
        }
        double pick = Seeds.fraction(Seeds.derive(roleSeed, "us.material.role", role.ordinal()),
                92001L) * total;
        double acc = 0.0;
        for (int i = 0; i < compatible.size(); i++) {
            acc += weights[i];
            if (pick < acc) {
                MaterialSpec chosen = compatible.get(i);
                return PlanetMaterial.of(chosen.id(), chosen.family(), chosen.blockId());
            }
        }
        MaterialSpec fallback = compatible.get(compatible.size() - 1);
        return PlanetMaterial.of(fallback.id(), fallback.family(), fallback.blockId());
    }

    /** Deterministic rarity-weighted pick (shared by select / selectFor). */
    private static PlanetMaterial weightedPick(List<MaterialSpec> compatible, MaterialRole role,
                                               long roleSeed) {
        double total = 0.0;
        double[] weights = new double[compatible.size()];
        for (int i = 0; i < compatible.size(); i++) {
            weights[i] = 1.0 - 0.7 * compatible.get(i).rarity();
            total += weights[i];
        }
        double pick = Seeds.fraction(Seeds.derive(roleSeed, "us.material.role", role.ordinal()),
                92001L) * total;
        double acc = 0.0;
        for (int i = 0; i < compatible.size(); i++) {
            acc += weights[i];
            if (pick < acc) {
                MaterialSpec chosen = compatible.get(i);
                return PlanetMaterial.of(chosen.id(), chosen.family(), chosen.blockId());
            }
        }
        MaterialSpec fallback = compatible.get(compatible.size() - 1);
        return PlanetMaterial.of(fallback.id(), fallback.family(), fallback.blockId());
    }

    /** R23 (E-1): visual role of a resolved material (STONE when unknown). */
    public static MaterialVisualRole visualRoleOf(PlanetMaterial m) {
        if (m == null) return MaterialVisualRole.STONE;
        for (MaterialSpec s : ALL) {
            if (s.id().equals(m.id())) return s.visualRole();
        }
        return MaterialVisualRole.STONE;
    }

    private static List<MaterialSpec> buildAll() {
        List<MaterialSpec> out = new ArrayList<>();
        out.addAll(CustomMaterials.ROCK_SET);
        out.addAll(CustomMaterialsExtra.SPECIAL_SET);
        out.addAll(VanillaMaterials.SET);
        return List.copyOf(out);
    }

    /** Catalogue-internal builder entry point. */
    static MaterialSpecBuilder spec(String id, MaterialFamily family, String blockId) {
        return MaterialSpec.builder(id, family, blockId);
    }
}
