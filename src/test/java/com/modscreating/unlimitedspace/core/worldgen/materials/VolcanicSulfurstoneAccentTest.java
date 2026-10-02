package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainSignatureSelector;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT STAGE 2.1 — sulfurstone is a SPARSE CHEMICAL INCLUSION on a volcanic world, never a substrate.
 *
 * <p>The defect this pins was measured, not hypothesised: {@code us.sulfurstone} held 20.8% of the
 * surface of one volcanic planet and 16.0% of another. Two causes, both fixed in place:
 * <ul>
 *   <li>the GEOTHERMAL candidate pool on a hot world was only FOUR materials wide, and sulfurstone
 *       was the ONLY ash-family entry in it, so the role's whole vote had nowhere else to go;</li>
 *   <li>{@code thermalAffinity} gave {@code MaterialVisualRole.CHEMICAL} a reading of {@code 0.85}
 *       — HIGHER than the basalt / cinder / ash ({@code 0.30}) that actually mantle a volcanic
 *       edifice — so on every genuinely hot column the axis promoted the accent over the substrate.</li>
 * </ul>
 *
 * <p>The assertions below run on REAL sampled columns, because that is where the thermal axis is
 * actually driven: a default-constructed sample has {@code temperature01 = 0.5} and no volcanic
 * intensity at all, so it would exercise the base weights and never the axis under test.
 */
@Tag("worldgen")
class VolcanicSulfurstoneAccentTest {

    /** A hot, strongly volcanic surface profile. */
    private static PlanetPhysicalProfile volcanic() {
        return new PlanetPhysicalProfile(
                0.80, null, 0.10, 0.6, null, 0.05, 0.03, 0.5, 0.55, 0.75, 0.3,
                0.25, 0.2, 0.4, 0.3, 0.2, 0.20, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_VOLCANIC);
    }

    private static V3ColumnSampler sampler(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        TerrainShaper sh = TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p), TerrainSignatureSelector.create(seed, p),
                80.0, 24.0, null, null, climate);
        return new V3ColumnSampler(sh,
                new ClimateField(climate, sh.character(), new WindDirectionField(seed)), null, null);
    }

    private static MaterialSpec sulfurstone() {
        for (MaterialSpec s : MaterialCatalog.all()) {
            if (s.id().equals("us.sulfurstone")) return s;
        }
        return null;
    }

    @Test
    void sulfurstoneRemainsPossibleOnAVolcanicWorld() {
        // The fix must NOT delete the material: fumarole crusts are real geology.
        boolean reachable = false;
        for (MaterialRole role : new MaterialRole[]{
                MaterialRole.GEOTHERMAL, MaterialRole.SECONDARY_SURFACE, MaterialRole.ACCENT}) {
            MaterialVariantField field = MaterialVariantField.forRole(volcanic(), role, 0x5F1L);
            for (int i = 0; i < field.size(); i++) {
                if (field.at(i).id().equals("us.sulfurstone")) reachable = true;
            }
        }
        assertTrue(reachable, "sulfurstone must remain a reachable volcanic candidate");
    }

    @Test
    void theGeothermalPoolIsWideEnoughToCarryItsOwnAsh() {
        // The starvation half of the defect: with four candidates and one ash-family entry, the
        // role's vote had nowhere else to go. The pool must now carry SEVERAL ash-family materials.
        MaterialVariantField field = MaterialVariantField.forRole(
                volcanic(), MaterialRole.GEOTHERMAL, 0x5F11L);
        int ash = 0;
        for (int i = 0; i < field.size(); i++) {
            if (MaterialSemantics.familyOf(field.at(i))
                    == MaterialSemanticFamily.VOLCANIC_ASH) {
                ash++;
            }
        }
        assertTrue(ash >= 3,
                "the geothermal pool must carry several ash-family materials, found " + ash);
    }

    @Test
    void sulfurstoneCannotDominateTheGeothermalSubstrate() {
        for (long seed : new long[]{0x5F21L, 0x5F22L, 0x5F23L}) {
            MaterialVariantField field = MaterialVariantField.forRole(
                    volcanic(), MaterialRole.GEOTHERMAL, seed);
            // REAL sampled columns: this is where the thermal axis is actually driven.
            V3ColumnSampler sampler = sampler(seed, volcanic());
            WorldgenColumnSample col = new WorldgenColumnSample();
            int total = 0, sulfur = 0, dark = 0, ash = 0;
            for (int x = -4800; x <= 4800; x += 24) {
                for (int z = -4800; z <= 4800; z += 24) {
                    sampler.sampleColumn(x, z, col);
                    if (col.materialRole != MaterialRole.GEOTHERMAL) continue;
                    total++;
                    int idx = field.index(col, x, z);
                    if (idx < 0) continue;
                    MaterialSemanticFamily fam = MaterialSemantics.familyOf(field.at(idx));
                    if (fam == MaterialSemanticFamily.VOLCANIC_ASH) {
                        // sulfurstone is filed VOLCANIC_ASH, so it is identified by its own id.
                        if (field.at(idx).id().equals("us.sulfurstone")) sulfur++;
                        else ash++;
                    } else if (fam == MaterialSemanticFamily.VOLCANIC_DARK) {
                        dark++;
                    }
                }
            }
            assertTrue(total > 0, "the geothermal role must actually be elected somewhere");
            double sulfurShare = sulfur / (double) total;
            assertTrue(sulfurShare < 0.10,
                    "sulfurstone must be a sparse accent, got " + String.format("%.4f", sulfurShare)
                            + " of " + total + " geothermal columns, dark=" + dark
                            + " ash=" + ash + " (seed=" + Long.toHexString(seed) + ")");
            // The ACT's other half: volcanic dark rock / basalt / ash must remain DOMINANT.
            double substrateShare = (dark + ash) / (double) total;
            assertTrue(substrateShare > 0.55,
                    "volcanic dark rock and ash must stay dominant, got "
                            + String.format("%.4f", substrateShare) + " of " + total
                            + " geothermal columns, sulfur=" + sulfur
                            + " (seed=" + Long.toHexString(seed) + ")");
        }
    }

    @Test
    void aChemicalCrustStaysDeclaredAsAnAccent() {
        // The invariant behind the fix, asserted on the catalogue's own declaration.
        MaterialSpec sulfur = sulfurstone();
        assertTrue(sulfur != null, "sulfurstone must stay in the catalogue");
        assertTrue(sulfur.hasTag(MaterialTag.ACCENT)
                        || sulfur.roles().contains(MaterialRole.ACCENT),
                "sulfurstone must remain declared as an ACCENT in the catalogue");
        // It may lead GEOTHERMAL because it is a volcanic ash-family material, never because it is
        // a substrate-class one.
        assertTrue(MaterialSemantics.mayLead(MaterialSemanticFamily.VOLCANIC_ASH,
                MaterialRole.GEOTHERMAL, PlanetSurface.SOLID_VOLCANIC),
                "the volcanic ash family may still lead the geothermal role");
        assertFalse(MaterialSemantics.mayLeadSurface(MaterialSemanticFamily.SAND,
                        PlanetSurface.SOLID_VOLCANIC),
                "and sand stays forbidden on a volcanic surface");
    }
}