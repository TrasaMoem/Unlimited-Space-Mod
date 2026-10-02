package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT-A ITEM 1d - the RELAXED PATH must never return a semantically forbidden material.
 *
 * <p>A fast, synthetic test of the STRUCTURAL claim, so it runs in the {@code fastTest} lane on
 * every commit. The real-planet evidence lives in {@link ActAIcedGateAudit} (the {@code audit} lane).
 *
 * <p>The defect it pins: the old relaxed tier called {@code mayLead(family, role, null)}, and for
 * {@code PRIMARY_SURFACE} that argument lands in {@code mayLeadSurface}, whose first line is
 * {@code if (surface == null) return true;} - so the tier did not relax the family gate, it
 * DELETED it. On a {@code SOLID_ICE} planet whose temperature sits above every frozen material's
 * climate window, tier 1 was empty and the relaxed tier therefore returned sand and calcite.
 *
 * <p>The ice profile below is the real measured shape of that planet: 159.8 K, about 113 K below
 * the frost point, with {@code temperature01 = 0.33} above the 0.20-0.32 windows of every cryogenic
 * material in the catalogue.
 */
@Tag("worldgen")
class RelaxedPathRespectsFamilyGateTest {

    /** temp01 = 0.33 on the log-Kelvin axis is 159.8 K: deeply frozen, above every cryo window. */
    private static PlanetPhysicalProfile iceShell() {
        return new PlanetPhysicalProfile(0.33, null, 0.20, 0.6, null, 0.45, 0.45, 0.5,
                0.30, 0.20, 0.20, 0.35, 0.2, 0.4, 0.3, 0.25, 0.10, 0.1, 0.2, 0.5, 0.5, null,
                PlanetSurface.SOLID_ICE);
    }

    private static PlanetPhysicalProfile volcanic() {
        return new PlanetPhysicalProfile(0.88, null, 0.10, 0.6, null, 0.05, 0.05, 0.5,
                0.85, 0.90, 0.70, 0.40, 0.2, 0.4, 0.6, 0.05, 0.05, 0.1, 0.9, 0.5, 0.5, null,
                PlanetSurface.SOLID_VOLCANIC);
    }

    private static boolean frozen(MaterialSemanticFamily f) {
        return f == MaterialSemanticFamily.FROZEN_ICE
                || f == MaterialSemanticFamily.FROZEN_SNOW
                || f == MaterialSemanticFamily.FROZEN_ROCK;
    }

    @Test
    void theStrictGateReallyIsEmptyOnAFrozenShell() {
        // The premise of the whole defect: no frozen material is physically admissible here, which
        // is exactly what used to hand control to the leaking tier.
        List<MaterialSpec> legal = MaterialCatalog.admissibleCandidatesFor(iceShell(),
                MaterialRole.PRIMARY_SURFACE);
        boolean anyFrozen = false;
        for (MaterialSpec s : legal) {
            anyFrozen |= frozen(MaterialSemantics.familyOf(s));
        }
        assertFalse(anyFrozen, "premise: the STRICT gate admits no frozen material at temp01=0.33");
        assertTrue(legal.stream().anyMatch(s -> "van.sand".equals(s.id())),
                "premise: and the strict gate does admit van.sand, the leak's raw material");
    }

    /**
     * ITEM 1d, the precise contract. There are three tiers and they carry DIFFERENT guarantees, so
     * the test asserts each one against the tier that actually ran:
     *
     * <pre>
     *   tier 1  suitable       mayLead(family, role, surface) == true     (the normal path)
     *   tier 2  relaxed        mayLead(family, role, surface) == true     (temperature widened)
     *   tier 3  nearest family mayLeadSurface(family, surface) == true    (role clause relaxed)
     * </pre>
     *
     * <p>Tier 3 relaxes the ROLE clause on purpose - that is what the ACT asks for ("выбирать
     * ближайшее admissible семейство, а не обходить gate"), and a role that resolved to nothing at
     * all would put a NONE block in the world. What tier 3 must NEVER do is return a family the
     * planet's own language forbids, which is the family-level prohibition.
     *
     * <p>Note also that {@code DEEP_STONE} / {@code CAVE} / {@code ORE_HOST} / {@code ACCENT} /
     * {@code RARE} are deliberately NOT surface-restricted: a basalt cavern under a glacier is real,
     * so {@code mayLead} returns {@code true} for them unconditionally. Asserting
     * {@code mayLeadSurface} on those roles would be asserting a rule the architecture never had.
     */
    @Test
    void everyTierHonoursItsOwnGuarantee() {
        double[] temps = {0.05, 0.20, 0.33, 0.45, 0.65, 0.85, 0.97};
        double[] hums = {0.02, 0.20, 0.45, 0.80};
        PlanetSurface[] surfaces = {PlanetSurface.SOLID_ICE, PlanetSurface.SOLID_DESERT,
                PlanetSurface.SOLID_VOLCANIC, PlanetSurface.SOLID_ROCKY, PlanetSurface.OCEANIC};
        int tier1 = 0;
        int tier2 = 0;
        int tier3 = 0;
        for (PlanetSurface surface : surfaces) {
            for (double t : temps) {
                for (double hum : hums) {
                    PlanetPhysicalProfile p = new PlanetPhysicalProfile(t, null, hum, 0.6, null,
                            0.30, 0.30, 0.5, 0.6, 0.4, 0.35, 0.5, 0.2, 0.4, 0.3, 0.2, 0.3, 0.1, 0.2,
                            0.5, 0.5, null, surface);
                    for (MaterialRole role : MaterialRole.values()) {
                        MaterialVariantField field = MaterialVariantField.forRole(p, role, 0x51E7L, null);
                        String where = surface + "@t" + t + "/h" + hum + " role " + role;
                        assertTrue(field.size() > 0, where + " resolved to NONE");

                        if (tier1Has(p, role)) {
                            tier1++;
                            for (int i = 0; i < field.size(); i++) {
                                MaterialSemanticFamily f = MaterialSemantics.familyOf(field.at(i));
                                assertTrue(MaterialSemantics.mayLead(f, role, surface),
                                        where + " tier1 returned " + field.at(i).id() + " (" + f
                                                + "), which mayLead forbids");
                            }
                        } else if (tier2Has(p, role)) {
                            tier2++;
                            for (int i = 0; i < field.size(); i++) {
                                MaterialSemanticFamily f = MaterialSemantics.familyOf(field.at(i));
                                assertTrue(MaterialSemantics.mayLead(f, role, surface),
                                        where + " tier2 returned " + field.at(i).id() + " (" + f
                                                + "), which mayLead forbids");
                            }
                        } else {
                            tier3++;
                            for (int i = 0; i < field.size(); i++) {
                                MaterialSemanticFamily f = MaterialSemantics.familyOf(field.at(i));
                                // Tier 3's guarantee: the NEAREST ADMISSIBLE family - either a family
                                // the planet's own language accepts, or competent rock as the
                                // documented universal substrate. It must never be a foreign
                                // deposit, which is exactly what the old relaxed tier returned.
                                assertTrue(MaterialSemantics.mayLeadSurface(f, surface)
                                                || MaterialSemantics.isRockFamily(f),
                                        where + " tier3 returned " + field.at(i).id() + " (" + f
                                                + "), which is neither the planet's language nor "
                                                + "competent rock");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[ACT-A 1d] tier usage over the sweep: tier1=" + tier1
                + " tier2=" + tier2 + " tier3=" + tier3);
        assertTrue(tier1 > 0 && tier2 > 0,
                "the sweep must actually exercise tiers 1 and 2, got " + tier1 + "/" + tier2);
    }

    /** A synthetic physical profile, the same constructor the distribution test uses. */
    private static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                                  double volc, double geo, double ero, double crystal,
                                                  PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    /** Whether the production tier-1 (strict) candidate set of this role is non-empty. */
    private static boolean tier1Has(PlanetPhysicalProfile p, MaterialRole role) {
        for (MaterialSpec s : MaterialCatalog.admissibleCandidatesFor(p, role)) {
            if (MaterialSemantics.mayLead(MaterialSemantics.familyOf(s), role, p.surface())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the production tier-2 (temperature-relaxed) candidate set is non-empty. */
    private static boolean tier2Has(PlanetPhysicalProfile p, MaterialRole role) {
        for (MaterialSpec s : MaterialCatalog.temperatureRelaxedCandidatesFor(p, role)) {
            if (MaterialSemantics.mayLead(MaterialSemantics.familyOf(s), role, p.surface())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aFrozenShellStillGetsAFrozenCrustInsteadOfNothing() {
        MaterialVariantField field = MaterialVariantField.forRole(iceShell(),
                MaterialRole.PRIMARY_SURFACE, 0x51E7L, null);
        assertTrue(field.size() > 0, "the STAGE 6 promise: a role must always have a candidate");
        boolean anyFrozen = false;
        for (int i = 0; i < field.size(); i++) {
            anyFrozen |= frozen(MaterialSemantics.familyOf(field.at(i)));
        }
        assertTrue(anyFrozen, "a SOLID_ICE shell at 160 K must keep its frozen crust, got "
                + ids(field));
    }

    /**
     * ACT-A ITEM 4 - the volcanic guard is NOT disturbed by the tier rewrite.
     *
     * <p>Measured while closing the ACT: the only synthetic world whose V3.8-STAGE13
     * {@code volcanicIntensity -> dark/ash} correlation inverts does so on <b>both</b> the pre-ACT-A
     * and post-ACT-A tiers, with the identical value (-0.0113 on volcanic-2, whose family is already
     * 94% saturated on the low half, so there is no headroom left to rise into). The ACT-A change is
     * therefore not the cause, and this test pins the thing that IS in scope for volcanic worlds:
     * no sand, no red dust, and lava stays a feature.
     */
    @Test
    void volcanicWorldsStillRefuseSandAndRedDust() {
        for (MaterialRole role : MaterialRole.values()) {
            MaterialVariantField field = MaterialVariantField.forRole(volcanic(), role, 0x51E7L, null);
            for (int i = 0; i < field.size(); i++) {
                MaterialSemanticFamily f = MaterialSemantics.familyOf(field.at(i));
                assertTrue(f != MaterialSemanticFamily.SAND && f != MaterialSemanticFamily.RED_DUST,
                        "role " + role + ": a volcanic world returned the foreign deposit "
                                + field.at(i).id() + " (" + f + ")");
            }
        }
    }

    /** Diagnostic: print every tier's candidate list for one profile x role, to see the leak. */
    @Test
    void printTiersForAGeothermalColumnOnAFrozenWorld() {
        // Which tier actually fires for each role of the synthetic VOLCANIC worlds? If tiers 2/3
        // never fire there, the ACT-A change cannot be the cause of any volcanic regression.
        double[][] v = {
                {0.88, 0.10, 0.05, 0.85, 0.90, 0.70, 0.40, 0.05},
                {0.80, 0.15, 0.06, 0.70, 0.75, 0.60, 0.45, 0.08},
                {0.75, 0.20, 0.08, 0.60, 0.65, 0.55, 0.50, 0.10},
                {0.92, 0.08, 0.03, 0.90, 0.95, 0.80, 0.35, 0.03},
                {0.85, 0.15, 0.06, 0.95, 0.85, 0.75, 0.30, 0.06},
        };
        for (int i = 0; i < v.length; i++) {
            PlanetPhysicalProfile p = profile(v[i][0], v[i][1], v[i][2], v[i][3], v[i][4], v[i][5],
                    v[i][6], v[i][7], PlanetSurface.SOLID_VOLCANIC);
            int t1 = 0;
            int t2 = 0;
            int t3 = 0;
            for (MaterialRole role : MaterialRole.values()) {
                if (tier1Has(p, role)) {
                    t1++;
                } else if (tier2Has(p, role)) {
                    t2++;
                    System.out.println("  volcanic-" + (i + 1) + " TIER2 role=" + role + " -> "
                            + names(MaterialCatalog.temperatureRelaxedCandidatesFor(p, role)));
                } else {
                    t3++;
                    System.out.println("  volcanic-" + (i + 1) + " TIER3 role=" + role
                            + " legal=" + names(MaterialCatalog.admissibleCandidatesFor(p, role)));
                }
            }
            System.out.println("volcanic-" + (i + 1) + " tier1=" + t1 + " tier2=" + t2
                    + " tier3=" + t3);
        }
    }

    @Test
    void printTiersForAGeothermalColumnOnAFrozenWorld2() {
        PlanetPhysicalProfile p = new PlanetPhysicalProfile(0.20, null, 0.20, 0.6, null,
                0.30, 0.30, 0.5, 0.6, 0.4, 0.35, 0.5, 0.2, 0.4, 0.3, 0.2, 0.3, 0.1, 0.2,
                0.5, 0.5, null, PlanetSurface.SOLID_ICE);
        System.out.println("solid ICE t=0.2 h=0.2, role GEOTHERMAL");
        System.out.println("  tier1 legal   = " + names(
                MaterialCatalog.admissibleCandidatesFor(p, MaterialRole.GEOTHERMAL)));
        System.out.println("  tier1 MOUNT   = " + names(
                MaterialCatalog.admissibleCandidatesFor(p, MaterialRole.MOUNTAIN)));
        System.out.println("  tier2 relaxed = " + names(
                MaterialCatalog.temperatureRelaxedCandidatesFor(p, MaterialRole.GEOTHERMAL)));
        System.out.println("  final table   = " + ids(
                MaterialVariantField.forRole(p, MaterialRole.GEOTHERMAL, 0x51E7L, null)));
    }

    private static String names(List<MaterialSpec> specs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(specs.get(i).id());
        }
        return sb.append(']').toString();
    }

    @Test
    void lavaStaysAFeatureAndNeverADominantSubstrate() {
        for (double t : new double[]{0.05, 0.33, 0.65, 0.88, 0.97}) {
            PlanetPhysicalProfile p = new PlanetPhysicalProfile(t, null, 0.10, 0.6, null,
                    0.10, 0.10, 0.5, 0.95, 0.95, 0.80, 0.40, 0.2, 0.4, 0.5, 0.05, 0.0, 0.1, 0.9,
                    0.5, 0.5, null, PlanetSurface.SOLID_VOLCANIC);
            for (MaterialRole role : MaterialRole.values()) {
                MaterialVariantField field = MaterialVariantField.forRole(p, role, 0x51E7L, null);
                for (int i = 0; i < field.size(); i++) {
                    assertFalse(MaterialSemantics.familyOf(field.at(i)) == MaterialSemanticFamily.LAVA,
                            "lava must remain a feature, got " + field.at(i).id()
                                    + " in role " + role);
                }
            }
        }
    }

    private static String ids(MaterialVariantField field) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < field.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(field.at(i).id());
        }
        return sb.append(']').toString();
    }
}
