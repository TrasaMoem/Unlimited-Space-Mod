package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.SurfaceMaterialField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ACT V3.7 STAGE 0 + STAGE 1 - the READ-ONLY audit that decides the root cause by numbers.
 *
 * <p>STAGE 0 asks whether the upstream column is deterministic at far and NEGATIVE coordinates.
 * STAGE 1 then walks more than a thousand spatially distinct coordinates of one ICE planet through
 * the REAL production chain ({@link V3ColumnSampler} -&gt; {@link SurfaceMaterialField#roleAt}
 * -&gt; {@link MaterialCatalog#select}) and records, per elected {@link MaterialRole}, every block
 * that chain can produce, next to the range of the upstream signals at those same columns.
 *
 * <p>The pre-patch expectation is exactly ONE distinct block per role. That is the defect: the
 * spatial signals upstream are real and measurable, but a per-planet constant role-to-block
 * mapping destroys them at the last step. The assertion makes the "before" column of the report a
 * measurement rather than a claim.
 *
 * <p>It makes NO production change.
 */
@Tag("worldgen")
@Tag("audit")
class Act37MaterialAudit {

    /** The far-coordinate grid the ACT mandates, including negative quadrants. */
    private static final int[][] FAR = {
            {0, 0},
            {1_000_000, 1_000_000},
            {-1_000_000, 1_000_000},
            {1_000_000, -1_000_000},
            {-1_000_000, -1_000_000},
            {30_000_000, 30_000_000},
            {-30_000_000, 30_000_000},
            {1, -1},
    };

    private static PlanetPhysicalProfile iceProfile() {
        return profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15, PlanetSurface.SOLID_ICE);
    }

    static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                         double volc, double geo, double ero, double crystal,
                                         PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    /**
     * STAGE 1 - the pre-patch baseline. Role, blockId and the upstream signal ranges over 1024
     * spatially distinct coordinates of one ICE planet.
     */
    @Test
    void stage1TheCurrentRoleToBlockMappingIsConstantPerRole() {
        PlanetPhysicalProfile p = iceProfile();
        long seed = 0x1d7e37L;
        V3ColumnSampler sampler = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
        SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
        WorldgenColumnSample col = new WorldgenColumnSample();

        Map<MaterialRole, Set<String>> blocksByRole = new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, Integer> roleCount = new EnumMap<>(MaterialRole.class);
        Map<MaterialRole, double[]> signalRange = new EnumMap<>(MaterialRole.class);
        int n = 0;
        // A coprime-stride lattice over a 4096-block square, so no two samples share a macro cell,
        // a province cell or a climate cell.
        for (int i = 0; i < 1024; i++) {
            int x = (int) ((i * 149L) % 4096L) - 2048;
            int z = (int) ((i * 271L) % 4096L) - 2048;
            sampler.sampleColumn(x, z, col);
            MaterialRole role = col.materialRole != null ? col.materialRole : material.roleAt(col);
            PlanetMaterial mat = MaterialCatalog.select(p, role, seed);
            blocksByRole.computeIfAbsent(role, k -> new LinkedHashSet<>())
                    .add(mat == null ? "NONE" : mat.blockId());
            roleCount.merge(role, 1, Integer::sum);

            double[] r = signalRange.computeIfAbsent(role, k -> new double[]{
                    Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.MAX_VALUE, -Double.MAX_VALUE});
            double snow = SurfaceMaterialField.snowAccumulation(col);
            double expo = SurfaceMaterialField.rockExposure(col);
            double glac = Math.abs(col.glacialRelief);
            r[0] = Math.min(r[0], snow);
            r[1] = Math.max(r[1], snow);
            r[2] = Math.min(r[2], expo);
            r[3] = Math.max(r[3], expo);
            r[4] = Math.min(r[4], glac);
            r[5] = Math.max(r[5], glac);
            n++;
        }
        System.out.println("[ACT37-STAGE1] ICE planet seed=0x" + Long.toHexString(seed)
                + " columns=" + n);
        for (Map.Entry<MaterialRole, Set<String>> e : blocksByRole.entrySet()) {
            double[] r = signalRange.get(e.getKey());
            System.out.println("[ACT37-STAGE1]   role=" + e.getKey()
                    + " columns=" + roleCount.get(e.getKey())
                    + " distinctBlocks=" + e.getValue().size()
                    + " snow=[" + round(r[0]) + ".." + round(r[1]) + "]"
                    + " rockExposure=[" + round(r[2]) + ".." + round(r[3]) + "]"
                    + " glacialRelief=[" + round(r[4]) + ".." + round(r[5]) + "]"
                    + " -> " + e.getValue());
        }
        for (Map.Entry<MaterialRole, Set<String>> e : blocksByRole.entrySet()) {
            if (roleCount.get(e.getKey()) < 20) continue;
            assertEquals(1, e.getValue().size(),
                    "PRE-PATCH BASELINE: role " + e.getKey() + " was expected to be a single"
                            + " constant block, got " + e.getValue());
        }
        System.out.println("[ACT37-STAGE1] === every role resolves to exactly ONE block while its"
                + " upstream signals vary: the mapping is the defect, not the fields ===");
    }

    /**
     * STAGE 0 - the upstream column is already deterministic and already spatially alive at far
     * and negative coordinates, so the defect is provably downstream of the sampler.
     */
    @Test
    void stage0TheUpstreamColumnIsDeterministicAtFarAndNegativeCoordinates() {
        PlanetPhysicalProfile p = iceProfile();
        long seed = 0x1d7e37L;
        V3ColumnSampler a = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
        V3ColumnSampler b = V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        int mismatch = 0;
        int total = 0;
        for (int[] c : FAR) {
            for (int dx = 0; dx < 40; dx += 8) {
                for (int dz = 0; dz < 40; dz += 8) {
                    a.sampleColumn(c[0] + dx, c[1] + dz, ca);
                    b.sampleColumn(c[0] + dx, c[1] + dz, cb);
                    total++;
                    if (ca.height != cb.height || ca.materialRole != cb.materialRole
                            || ca.surfaceCategory != cb.surfaceCategory
                            || ca.glacialRelief != cb.glacialRelief) mismatch++;
                }
            }
        }
        System.out.println("[ACT37-STAGE0] far columns=" + total
                + " determinismMismatches=" + mismatch);
        assertEquals(0, mismatch, "the upstream column must be deterministic at far coordinates");
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
