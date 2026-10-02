package com.modscreating.unlimitedspace.core.worldgen.terrain;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemanticFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT STAGE 5 — SPIRES: the structural signal must be REAL and REACHABLE, not merely declared.
 *
 * <p>The audit reported {@code spireSignalShare = 0.0000} on every audited planet. Two measurement
 * faults produced the reported zero: the counter used a threshold of 0.5 while the published signal
 * is the deformation NORMALISED by the amplitude budget (measured maximum 0.234), and the audit
 * sampled a 160-block grid while a spire core has a radius of 10-32 blocks. The counter now reads
 * the PRODUCTION gate ({@code SurfaceMaterialField.SPIRE_STRUCTURE}).
 *
 * <p>Electing the CRYSTAL role ahead of the cold-shell tests was ALSO tried: it made the signal
 * reach blocks, but it regressed the snow-accumulation correlation in
 * {@code MaterialSemanticDistributionTest}, an architectural guard this ACT forbids weakening. It is
 * therefore reverted and recorded in {@code SurfaceMaterialField.roleAt}; these tests pin the parts
 * that are true and safe.
 */
@Tag("worldgen")
class SpireReachesMaterialRoleTest {

    private static PlanetPhysicalProfile crystalWorld(double crystal) {
        return new PlanetPhysicalProfile(
                0.45, null, 0.35, 0.6, null, 0.25, 0.15, 0.5, 0.55, 0.12, 0.3,
                0.35, 0.2, 0.4, 0.3, crystal, 0.30, 0.1, 0.2, 0.5, 0.5, null, PlanetSurface.SOLID_ROCKY);
    }

    private static V3ColumnSampler sampler(long seed, PlanetPhysicalProfile p) {
        PlanetClimateProfile climate = PlanetClimateProfile.create(seed, p);
        TerrainShaper sh = TerrainShaper.create(null, seed, p,
                GeologicalProvinceMap.create(seed, p), TerrainSignatureSelector.create(seed, p),
                80.0, 24.0, null, null, climate);
        return new V3ColumnSampler(sh,
                new ClimateField(climate, sh.character(), new WindDirectionField(seed)), null, null);
    }

    @Test
    void theSpireSignalIsPublishedAndReachableOnACrystalWorld() {
        V3ColumnSampler s = sampler(0x5B82L, crystalWorld(0.75));
        WorldgenColumnSample col = new WorldgenColumnSample();
        double maxSignal = 0.0;
        for (int x = -4800; x <= 4800; x += 24) {
            for (int z = -4800; z <= 4800; z += 24) {
                s.sampleColumn(x, z, col);
                maxSignal = Math.max(maxSignal, col.spireIntensity);
            }
        }
        assertTrue(maxSignal > 0.0,
                "a crystal-rich world must produce a real structural spire signal, got " + maxSignal);
        assertTrue(maxSignal >= SurfaceMaterialField.SPIRE_STRUCTURE,
                "the spire structure gate " + SurfaceMaterialField.SPIRE_STRUCTURE
                        + " must be reachable by the real signal, whose max is " + maxSignal);
        assertTrue(MaterialSemantics.mayLead(MaterialSemanticFamily.CRYSTALLINE,
                        MaterialRole.CRYSTAL, PlanetSurface.SOLID_ROCKY),
                "a spire's body must resolve to a legal crystalline / spire-rock material");
    }

    @Test
    void aCrystalPoorWorldGetsNoSpires() {
        // Spires must NOT turn every rocky world into a crystal world.
        V3ColumnSampler s = sampler(0x5B83L, crystalWorld(0.05));
        WorldgenColumnSample col = new WorldgenColumnSample();
        for (int x = -4800; x <= 4800; x += 48) {
            for (int z = -4800; z <= 4800; z += 48) {
                s.sampleColumn(x, z, col);
                assertTrue(col.spireIntensity < SurfaceMaterialField.SPIRE_STRUCTURE,
                        "a crystal-POOR world must have no structural spires");
            }
        }
    }
}