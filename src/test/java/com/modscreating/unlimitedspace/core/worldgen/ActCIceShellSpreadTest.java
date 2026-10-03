package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemanticFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT-C ITEM 3 — the ICED MONOCULTURE, on real ice worlds.
 *
 * <h2>The measured defect this pins (seed 0)</h2>
 * The frozen climate windows are calibrated on the log-Kelvin axis for {@code temperature01 <= 0.24},
 * so the STRICT candidate tier decays 4 -&gt; 3 -&gt; 2 -&gt; 1 -&gt; 0 across
 * {@code temperature01 0.24..0.34}. Every {@code SOLID_ICE} planet inside that band collapsed to a
 * <b>single</b> PRIMARY_SURFACE candidate, which is the one thing a variant layer cannot use:
 * <pre>
 *   system_0000_planet_01  K=141.3 t01=0.308 -&gt; pool [us.frost_soil] -&gt; frost_soil 85.1%
 *   system_0007_planet_02  K=140.9 t01=0.307 -&gt; pool [us.frost_soil] -&gt; frost_soil 92.3%
 *   system_0015_planet_02  K=147.5 t01=0.316 -&gt; pool [us.frost_soil] -&gt; frost_soil-dominant
 * </pre>
 * A genuinely colder world (t01 &lt;= 0.28) had 3-4 candidates and a healthy spread, which is what
 * proves the hole is on the temperature AXIS and not in the frozen family itself.
 *
 * <p>The fix consults the temperature-relaxed tier whenever tier 1 cannot offer a choice. It widens
 * exactly what tier 2 already widens — the climate window — and never the family gate, so the
 * {@code sand <= 5%} / {@code frozen >= 40%} contract is asserted here unchanged.
 */
@Tag("worldgen")
class ActCIceShellSpreadTest {

    private static final long SEED = 0L;
    private static final int GRID = 40;
    private static final int STEP = 160;

    /**
     * Five real ice worlds: the three degenerate ones and two healthy controls, so the test cannot
     * pass by loosening the contract everywhere.
     */
    private static final int[][] ICE_WORLDS = {
            {0, 1}, {7, 2}, {15, 2}, {0, 2}, {0, 4}};

    /** The block histogram of one real world, through the production tables. */
    private static Map<String, Integer> blocksOf(PlanetWorldgenProfile profile,
                                                 V3ColumnSampler sampler) {
        MaterialVariantField[] tables = ActCRealWorlds.tablesFor(profile);
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<String, Integer> blocks = new TreeMap<>();
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                int x = (ix - GRID / 2) * STEP;
                int z = (iz - GRID / 2) * STEP;
                sampler.sampleColumn(x, z, col);
                MaterialVariantField f = tables[col.materialRole.ordinal()];
                int idx = f.index(col, x, z);
                if (idx >= 0) {
                    blocks.merge(f.at(idx).id(), 1, Integer::sum);
                }
            }
        }
        return blocks;
    }

    private static double leadingShare(Map<String, Integer> blocks) {
        int total = 0;
        int top = 0;
        for (int v : blocks.values()) {
            total += v;
            top = Math.max(top, v);
        }
        return total == 0 ? 0.0 : top / (double) total;
    }
    @Test
    void everyIceShellOffersMoreThanOnePrimarySurfaceCandidate() {
        int checked = 0;
        for (int[] id : ICE_WORLDS) {
            var profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_ICE) {
                continue;
            }
            MaterialVariantField[] tables = ActCRealWorlds.tablesFor(profile);
            MaterialVariantField primary = tables[MaterialRole.PRIMARY_SURFACE.ordinal()];
            int pool = 0;
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < 64 && primary.at(i) != null; i++) {
                pool++;
                ids.append(primary.at(i).id()).append(' ');
            }
            System.out.printf(Locale.ROOT, "[ACTC-3] %s PRIMARY_SURFACE pool=%d [%s]%n",
                    profile.planetId().code(), pool, ids.toString().trim());
            assertTrue(pool >= 2,
                    "an ice shell whose PRIMARY_SURFACE has a single candidate cannot produce any "
                            + "spatial variation by construction: " + profile.planetId().code());
            checked++;
        }
        assertTrue(checked >= 5, "the test must reach five real ice worlds, reached " + checked);
    }

    @Test
    void noSingleBlockOwnsAnIceShellAndRockStaysVisible() {
        int checked = 0;
        for (int[] id : ICE_WORLDS) {
            var profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_ICE) {
                continue;
            }
            V3ColumnSampler sampler = ActCRealWorlds.samplerFor(profile);
            MaterialVariantField[] tables = ActCRealWorlds.tablesFor(profile);
            WorldgenColumnSample col = new WorldgenColumnSample();
            Map<String, Integer> blocks = new TreeMap<>();
            int n = 0;
            int frozen = 0;
            int sand = 0;
            int darkRock = 0;
            for (int iz = 0; iz < GRID; iz++) {
                for (int ix = 0; ix < GRID; ix++) {
                    int x = (ix - GRID / 2) * STEP;
                    int z = (iz - GRID / 2) * STEP;
                    sampler.sampleColumn(x, z, col);
                    n++;
                    MaterialVariantField f = tables[col.materialRole.ordinal()];
                    int idx = f.index(col, x, z);
                    if (idx < 0) {
                        continue;
                    }
                    blocks.merge(f.at(idx).id(), 1, Integer::sum);
                    MaterialSemanticFamily fam = MaterialSemantics.familyOf(f.at(idx));
                    if (fam == MaterialSemanticFamily.FROZEN_ICE
                            || fam == MaterialSemanticFamily.FROZEN_SNOW
                            || fam == MaterialSemanticFamily.FROZEN_ROCK) {
                        frozen++;
                    }
                    // ACT-C: the "sand leak" metric is exactly the one the existing family guard
                    // (ActAIcedGateAudit) uses - the AEOLIAN and FERRUGINOUS families. Sorted
                    // clastic debris (van.gravel / us.salt_crust, the SEDIMENT family) is legitimate
                    // ice-shell geology: a moraine and a salt pan are what a glacier leaves. Counting
                    // those as "sand" would be a stricter contract than the architecture states, and
                    // it would measure a moraine as a desert.
                    if (fam == MaterialSemanticFamily.SAND
                            || fam == MaterialSemanticFamily.RED_DUST) {
                        sand++;
                    }
                    if (fam == MaterialSemanticFamily.DARK_ROCK
                            || fam == MaterialSemanticFamily.ROCK
                            || fam == MaterialSemanticFamily.VOLCANIC_DARK) {
                        darkRock++;
                    }
                }
            }
            double top = leadingShare(blocks);
            System.out.printf(Locale.ROOT,
                    "[ACTC-3] %s top=%.4f frozen=%.4f sand=%.4f darkRock=%.4f blocks=%s%n",
                    profile.planetId().code(), top, frozen / (double) n, sand / (double) n,
                    darkRock / (double) n, blocks);
            assertTrue(sand / (double) n <= 0.05,
                    "sand / regolith must stay <= 5% of an ice shell, got "
                            + (sand / (double) n) + " on " + profile.planetId().code());
            assertTrue(frozen / (double) n >= 0.40,
                    "the frozen family must hold >= 40% of an ice shell, got "
                            + (frozen / (double) n) + " on " + profile.planetId().code());
            checked++;
        }
        assertTrue(checked >= 5, "the test must reach five real ice worlds, reached " + checked);
    }

    /**
     * The degenerate worlds specifically: their leading block must have come DOWN off the
     * monoculture, which is the defect the ACT measured.
     */
    @Test
    void theDegenerateIceWorldsNoLongerCollapseOntoOneBlock() {
        for (int[] id : new int[][]{{0, 1}, {7, 2}, {15, 2}}) {
            var profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_ICE) {
                continue;
            }
            V3ColumnSampler sampler = ActCRealWorlds.samplerFor(profile);
            Map<String, Integer> blocks = blocksOf(profile, sampler);
            double top = leadingShare(blocks);
            System.out.printf(Locale.ROOT, "[ACTC-3-degenerate] %s topShare=%.4f blocks=%s%n",
                    profile.planetId().code(), top, blocks);
            // Measured after the fix (grid 40x40, step 160):
            //   system_0000_planet_01  0.4831  (was 0.851 on us.frost_soil alone)
            //   system_0007_planet_02  0.6181  (was 0.923 on us.frost_soil alone)
            //   system_0015_planet_02  0.2744
            // The structural defect - a one-candidate table, which is variation-free BY CONSTRUCTION -
            // is gone on all three. The residual concentration on system_0007_planet_02 is the WEIGHT
            // SHARE of packed ice versus froststone inside a now-healthy 4-candidate pool, and
            // flattening that further would mean retuning GAIN_SNOW / baseWeight from these numbers,
            // i.e. tuning weights blind. The guard therefore pins the measured bound honestly.
            assertTrue(top <= 0.65,
                    "an ice shell must not be a single block; " + profile.planetId().code()
                            + " leading share is " + top);
        }
    }
}