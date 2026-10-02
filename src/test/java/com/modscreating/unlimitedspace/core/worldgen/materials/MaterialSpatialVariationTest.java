package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateArchetypeSelector;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.geology.PlanetGeologyPalette;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.7 STAGE 8 - the SPATIAL MATERIAL VARIATION contract.
 *
 * <p>The V3.6 audit proved the defect by measurement: over 1024 spatially distinct ICE columns the
 * production chain resolved PRIMARY_SURFACE to exactly ONE block and MOUNTAIN to exactly ONE
 * block, while snow accumulation, rock exposure and glacial relief all varied across those very
 * columns. These tests assert the opposite behaviour on the REAL production path -
 * V3ColumnSampler, SurfaceMaterialField.roleAt, MaterialVariantField, and the concrete block id -
 * with no test-only branch anywhere in that chain.
 *
 * <h2>What is asserted, and what is deliberately NOT asserted</h2>
 * <ul>
 *   <li>variety is asserted on the ACTUAL block of a role, and on the FAMILY level correlation
 *       with the signals;</li>
 *   <li>monotonicity of a specific blockId in a signal is NOT asserted and would be meaningless:
 *       a family contains several materials, and which one wins inside a family is a spatial
 *       question, not a scalar one. The contract is correlation at the FAMILY level;</li>
 *   <li>the variant set is bounded, so the assertions are about "more than one" and about measured
 *       distributions, never about an invented exact count or an invented exact percentage.</li>
 * </ul>
 */
@Tag("worldgen")
@Tag("audit")
class MaterialSpatialVariationTest {

    private static final long SEED = 0x1d7e37L;

    /** The reference ICE shell: cold, wet, tectonically quiet, glacial relief. */
    private static PlanetPhysicalProfile iceShell() {
        return profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15, PlanetSurface.SOLID_ICE);
    }

    private static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                                double volc, double geo, double ero, double crystal,
                                                PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    private static V3ColumnSampler samplerFor(PlanetPhysicalProfile p, long seed) {
        return V3PreviewChannels.samplerFor(seed, p, ReliefArchetype.GLACIAL);
    }

    /**
     * The planet's OWN palette, built through the same production factory the runtime uses, so the
     * variant tables are constructed from the same colour identity the chunk generator sees.
     */
    private static PlanetGeologyPalette paletteFor(PlanetPhysicalProfile p, long seed) {
        return PlanetGeologyPalette.create(seed, p,
                GeologicalProvinceMap.create(seed, p),
                PlanetColorTheme.select(TemperatureBand.of(p.temperature()),
                        ClimateArchetypeSelector.create(seed, p), seed));
    }

    private static MaterialVariantField[] tablesFor(PlanetPhysicalProfile p, long seed) {
        PlanetGeologyPalette palette = paletteFor(p, seed);
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(p, role, seed,
                    palette.materialFor(role));
        }
        return tables;
    }

    /**
     * The production resolution of one column: the role the environment elects, and the concrete
     * block that role's spatial variant table yields at this coordinate.
     */
    private static PlanetMaterial resolve(V3ColumnSampler sampler, MaterialVariantField[] tables,
                                         WorldgenColumnSample col, int x, int z) {
        MaterialRole role = col.materialRole != null
                ? col.materialRole : new SurfaceMaterialField(sampler.character()).roleAt(col);
        MaterialVariantField field = tables[role.ordinal()];
        return field == null ? null : field.variant(col, x, z);

    }

    // ------------------------------------------- 1. PRIMARY_SURFACE is no longer one block

    /**
     * Requirement 1: inside ONE ice planet the PRIMARY_SURFACE role must resolve to more than one
     * distinct ACTUAL block. This is the direct inverse of the V3.6 baseline (distinctBlocks = 1).
     */
    @Test
    void icePrimarySurfaceResolvesToMoreThanOneActualBlock() {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, SEED);
        MaterialVariantField[] tables = tablesFor(p, SEED);
        WorldgenColumnSample col = new WorldgenColumnSample();

        Set<String> blocks = new LinkedHashSet<>();
        int primary = 0;
        for (int i = 0; i < 4096; i++) {
            int x = (int) ((i * 149L) % 6000L) - 3000;
            int z = (int) ((i * 271L) % 6000L) - 3000;
            sampler.sampleColumn(x, z, col);
            if (col.materialRole != MaterialRole.PRIMARY_SURFACE) continue;
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            assertNotNull(m, "PRIMARY_SURFACE must resolve to a legal material at " + x + "," + z);
            blocks.add(m.blockId());
            primary++;
        }
        System.out.println("[V3.7-1] ice PRIMARY_SURFACE columns=" + primary
                + " distinctBlocks=" + blocks.size() + " -> " + blocks);
        assertTrue(primary > 500, "the ICE primary share must be a real population, got " + primary);
        assertTrue(blocks.size() > 1,
                "PRIMARY_SURFACE must resolve to >1 distinct actual block on one ice planet, got "
                        + blocks);
    }

    // ------------------------------------------- 2. MOUNTAIN is no longer one block

    /**
     * Requirement 2: the MOUNTAIN role must resolve to more than one distinct block whenever the
     * palette actually provides more than one legal mountain material. The condition is read from
     * the real candidate table, so the test can never demand variety the palette does not legally
     * have - and when the palette has only one legal mountain rock, that is reported rather than
     * hidden.
     */
    @Test
    void iceMountainResolvesToMoreThanOneActualBlockWhenThePaletteAllowsIt() {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, SEED);
        MaterialVariantField mountain = MaterialVariantField.forRole(p, MaterialRole.MOUNTAIN, SEED,
                paletteFor(p, SEED).materialFor(MaterialRole.MOUNTAIN));
        MaterialVariantField[] tables = tablesFor(p, SEED);
        WorldgenColumnSample col = new WorldgenColumnSample();

        Set<String> blocks = new LinkedHashSet<>();
        int mountainColumns = 0;
        for (int i = 0; i < 20000; i++) {
            int x = (int) ((i * 149L) % 9000L) - 4500;
            int z = (int) ((i * 271L) % 9000L) - 4500;
            sampler.sampleColumn(x, z, col);
            if (col.materialRole != MaterialRole.MOUNTAIN) continue;
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            if (m == null) continue;
            blocks.add(m.blockId());
            mountainColumns++;
        }
        System.out.println("[V3.7-2] ice MOUNTAIN columns=" + mountainColumns
                + " legalCandidates=" + mountain.size()
                + " distinctBlocks=" + blocks.size() + " -> " + blocks);
        if (mountain.size() > 1) {
            assertTrue(blocks.size() > 1,
                    "MOUNTAIN must resolve to >1 distinct block when the palette offers "
                            + mountain.size() + " legal materials, got " + blocks);
        } else {
            System.out.println("[V3.7-2] NOTE: this planet's MOUNTAIN role has only "
                    + mountain.size() + " legal material, so no variation is possible or claimed");
        }
    }

    // ------------------------------------------- 3. the distribution varies with coordinates

    /**
     * Requirement 3: the material distribution must be a function of position, not a per-planet
     * constant. Two far-apart regions of the SAME planet must not produce the same histogram, and a
     * single region must already contain more than one material.
     */
    @Test
    void theDistributionVariesWithCoordinates() {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, SEED);
        MaterialVariantField[] tables = tablesFor(p, SEED);
        WorldgenColumnSample col = new WorldgenColumnSample();

        Map<String, Integer> regionA = new TreeMap<>();
        Map<String, Integer> regionB = new TreeMap<>();
        for (int i = 0; i < 3000; i++) {
            int x = (int) ((i * 37L) % 900L) - 450;
            int z = (int) ((i * 91L) % 900L) - 450;
            sampler.sampleColumn(x, z, col);
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            if (m != null) regionA.merge(m.blockId(), 1, Integer::sum);
        }
        for (int i = 0; i < 3000; i++) {
            int x = 20000 + (int) ((i * 37L) % 900L);
            int z = -20000 + (int) ((i * 91L) % 900L);
            sampler.sampleColumn(x, z, col);
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            if (m != null) regionB.merge(m.blockId(), 1, Integer::sum);
        }
        System.out.println("[V3.7-3] regionA=" + regionA);
        System.out.println("[V3.7-3] regionB=" + regionB);
        assertTrue(!regionA.isEmpty() && !regionB.isEmpty(), "both regions must be populated");
        assertTrue(regionA.size() > 1,
                "a single region must already contain more than one material, got " + regionA);
        assertTrue(!regionA.equals(regionB),
                "two distant regions of one planet must not share an identical material histogram");
    }

    // ------------------------------------------- 4. determinism (bit-identical)

    /**
     * Requirement 4: recomputing the same coordinate must be bit-identical. Two INDEPENDENT
     * pipelines (independent sampler, independent column object, independent variant tables) are
     * compared, so a hidden per-column state or a hidden accumulator would show up here.
     */
    @Test
    void recomputingTheSameCoordinateIsBitIdentical() {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler a = samplerFor(p, SEED);
        V3ColumnSampler b = samplerFor(p, SEED);
        MaterialVariantField[] ta = tablesFor(p, SEED);
        MaterialVariantField[] tb = tablesFor(p, SEED);
        WorldgenColumnSample ca = new WorldgenColumnSample();
        WorldgenColumnSample cb = new WorldgenColumnSample();
        for (int i = 0; i < 6000; i++) {
            int x = (int) ((i * 149L) % 12000L) - 6000;
            int z = (int) ((i * 271L) % 12000L) - 6000;
            a.sampleColumn(x, z, ca);
            b.sampleColumn(x, z, cb);
            PlanetMaterial ma = resolve(a, ta, ca, x, z);
            PlanetMaterial mb = resolve(b, tb, cb, x, z);
            assertEquals(ma == null ? null : ma.blockId(), mb == null ? null : mb.blockId(),
                    "the variant must be bit-identical at " + x + "," + z);
            assertEquals(ca.materialRole, cb.materialRole, "the role must match at " + x + "," + z);
        }
    }

    // ------------------------------------------- 5. no checkerboard (spatial coherence)

    /**
     * Requirement 5: neighbouring columns must NOT alternate. A per-column random roll produces
     * roughly 50% changes along a transect; coherent facies regions produce a small fraction. The
     * bound is deliberately loose and the MEASURED value is printed, because the structural claim
     * is "large coherent regions", not a tuned number.
     */
    @Test
    void nearbyCoordinatesDoNotFormACheckerboard() {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, SEED);
        MaterialVariantField[] tables = tablesFor(p, SEED);
        WorldgenColumnSample col = new WorldgenColumnSample();

        int changes = 0;
        int steps = 0;
        // Several independent transects, so one unlucky line cannot decide the result.
        for (int line = 0; line < 12; line++) {
            int z0 = -6000 + line * 977;
            String previous = null;
            for (int x = -4000; x <= 4000; x += 8) {
                sampler.sampleColumn(x, z0, col);
                PlanetMaterial m = resolve(sampler, tables, col, x, z0);
                String id = m == null ? "NONE" : m.blockId();
                if (previous != null) {
                    steps++;
                    if (!previous.equals(id)) changes++;
                }
                previous = id;
            }
        }
        double rate = steps == 0 ? 1.0 : (double) changes / steps;
        System.out.println("[V3.7-5] adjacent-column material change rate = " + round(rate)
                + " (" + changes + "/" + steps + ")");
        // 50% is the signature of a per-column roll. Coherent regions stay far below it.
        assertTrue(rate < 0.30,
                "neighbouring columns must mostly agree (coherent regions), change rate=" + rate);
    }

    // ------------------------------------------- 6. snow signal -> snow-family dominance

    /**
     * Requirement 6: a higher snow-accumulation signal must STATISTICALLY raise the snow-family
     * share. The comparison is between the two halves of one measured population, split by that
     * population's own median, so the cut is honest about what it is and no absolute threshold is
     * invented. The claim is on the FAMILY, never on one block id.
     */
    @Test
    void higherSnowAccumulationRaisesTheSnowFamilyShare() {
        assertSnowCorrelation(0x1d7e37L);
        assertSnowCorrelation(0x51AB1EL);
    }

    private void assertSnowCorrelation(long seed) {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, seed);
        MaterialVariantField[] tables = tablesFor(p, seed);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int samples = 12000;

        double[] snow = new double[samples];
        boolean[] snowFamily = new boolean[samples];
        for (int i = 0; i < samples; i++) {
            int x = (int) ((i * 149L) % 12000L) - 6000;
            int z = (int) ((i * 271L) % 12000L) - 6000;
            sampler.sampleColumn(x, z, col);
            snow[i] = SurfaceMaterialField.snowAccumulation(col);
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            snowFamily[i] = m != null && isSnowFamily(m.family());
        }
        double[] sorted = snow.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        int lowN = 0;
        int lowSnow = 0;
        int highN = 0;
        int highSnow = 0;
        for (int i = 0; i < samples; i++) {
            if (snow[i] < median) {
                lowN++;
                if (snowFamily[i]) lowSnow++;
            } else {
                highN++;
                if (snowFamily[i]) highSnow++;
            }
        }
        double lowShare = lowN == 0 ? 0.0 : (double) lowSnow / lowN;
        double highShare = highN == 0 ? 0.0 : (double) highSnow / highN;
        System.out.println("[V3.7-6] seed=0x" + Long.toHexString(seed)
                + " snowMedian=" + round(median)
                + " snowFamilyShare(lowSnow)=" + round(lowShare)
                + " snowFamilyShare(highSnow)=" + round(highShare));
        assertTrue(highShare > lowShare,
                "higher snow accumulation must raise the snow-FAMILY share, low=" + lowShare
                        + " high=" + highShare);
    }

    /** The snow family of the catalogue: real ice, packed ice, snow and frozen ground. */
    private static boolean isSnowFamily(MaterialFamily f) {
        if (f == null) return false;
        return f.superFamily() == MaterialFamily.MaterialSuperFamily.ICE
                || f == MaterialFamily.SOIL_FROZEN
                || f == MaterialFamily.ROCK_FROZEN;
    }

    // ------------------------------------------- 7. rock exposure -> rock-family dominance

    /**
     * Requirement 7: a higher rock-exposure signal must raise the rock-family share. Split by the
     * population's own median exposure, and asserted on the FAMILY (a STONE-superfamily rock, i.e.
     * the actual substrate of an exposed face) rather than on one block id.
     *
     * <p>This is the test the V3.6 mapping could not pass: there, exposure reached its gate and the
     * role became MOUNTAIN, but the block stayed one frozen material, so the rock-family share
     * could not rise with the signal however much rock was exposed.
     */
    @Test
    void higherRockExposureRaisesTheRockFamilyShare() {
        assertRockCorrelation(0x1d7e37L);
        assertRockCorrelation(0x51AB1EL);
    }

    private void assertRockCorrelation(long seed) {
        PlanetPhysicalProfile p = iceShell();
        V3ColumnSampler sampler = samplerFor(p, seed);
        MaterialVariantField[] tables = tablesFor(p, seed);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int samples = 12000;

        double[] expo = new double[samples];
        boolean[] rockFamily = new boolean[samples];
        for (int i = 0; i < samples; i++) {
            int x = (int) ((i * 149L) % 12000L) - 6000;
            int z = (int) ((i * 271L) % 12000L) - 6000;
            sampler.sampleColumn(x, z, col);
            expo[i] = SurfaceMaterialField.rockExposure(col);
            PlanetMaterial m = resolve(sampler, tables, col, x, z);
            rockFamily[i] = m != null && isRockFamily(m.family());
        }
        double[] sorted = expo.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        int lowN = 0;
        int lowRock = 0;
        int highN = 0;
        int highRock = 0;
        for (int i = 0; i < samples; i++) {
            if (expo[i] < median) {
                lowN++;
                if (rockFamily[i]) lowRock++;
            } else {
                highN++;
                if (rockFamily[i]) highRock++;
            }
        }
        double lowShare = lowN == 0 ? 0.0 : (double) lowRock / lowN;
        double highShare = highN == 0 ? 0.0 : (double) highRock / highN;
        System.out.println("[V3.7-7] seed=0x" + Long.toHexString(seed)
                + " exposureMedian=" + round(median)
                + " rockFamilyShare(lowExposure)=" + round(lowShare)
                + " rockFamilyShare(highExposure)=" + round(highShare));
        assertTrue(highShare > lowShare,
                "higher rock exposure must raise the rock-FAMILY share, low=" + lowShare
                        + " high=" + highShare);
    }

    /**
     * The rock family of the catalogue: the STONE super-family, i.e. what an exposed face actually
     * shows. Frozen rock is deliberately EXCLUDED, because counting it as "rock" would let an
     * ice-coloured block satisfy a rock-exposure claim - exactly the mislabelling the V3.6 report
     * found (froststone rendering an ice-coloured mountain).
     */
    private static boolean isRockFamily(MaterialFamily f) {
        return f != null && f.superFamily() == MaterialFamily.MaterialSuperFamily.STONE;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
