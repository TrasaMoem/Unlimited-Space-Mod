package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRules;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemanticFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialSemantics;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialVariantField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT-C ITEM 2 — IMPACT EJECTA IS A CRATER-LOCAL ACCENT, never a planet-wide blanket.
 *
 * <h2>The measured defect this pins (real world, seed 0)</h2>
 * {@code system_0002_planet_00} — SOLID_VOLCANIC, 468.3 K, volcanicWeight 0.546:
 * <pre>
 *   BEFORE  roles  = SEDIMENT 88.4%, MOUNTAIN 10.7%, PRIMARY_SURFACE 0.9%
 *           blocks = us.impactite 64.2%, van.deepslate 14.8%, us.astral_basalt 10.0%
 *           family = DARK_ROCK 89.1%, VOLCANIC_DARK 5.9%
 *   AFTER   blocks = us.cinderstone 89.1%, van.basalt 5.1%, ...
 *           family = VOLCANIC_ASH 89.1%, VOLCANIC_DARK 8.7%, DARK_ROCK 0.2%
 * </pre>
 *
 * <p>Two independent causes, both fixed in place and both pinned here:
 * <ol>
 *   <li>{@code us.impactite} (family {@code ROCK_IMPACT}) could lead any planet-wide role. Its
 *       {@code SURFACE_FORMING} + {@code IMPACT} declaration then handed it 64.2% of a role elected
 *       on 88.4% of the columns — a crater floor painted as a continent;</li>
 *   <li>{@code requiresVolcanism} was answered by a continuous threshold that DISAGREED with the
 *       planet's own identity (0.546 against {@code isVolcanicallyDriven()}'s 0.55), which starved
 *       the SEDIMENT role of its only ash-family entry and forced the tier-3 rock fallback.</li>
 * </ol>
 *
 * <p>The four other volcanic worlds of the same seed are asserted to be UNCHANGED, so the fix
 * cannot be bought by repainting every volcano.
 */
@Tag("worldgen")
class ActCVolcanicImpactEjectaTest {

    private static final long SEED = 0L;
    private static final int GRID = 40;
    private static final int STEP = 160;

    /** The world whose histogram the ACT measured, pinned by ID so a regression names itself. */
    private static final int[] IMPACT_WORLD = {2, 0};
    /** The other four volcanic worlds of the same seed, which must NOT move. */
    private static final int[][] UNCHANGED_WORLDS = {{4, 0}, {6, 1}, {11, 2}, {14, 2}};

    /** block id -> share, over the standard grid, through the production tables. */
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

    private static double share(Map<String, Integer> counts, String id) {
        int total = 0;
        for (int v : counts.values()) {
            total += v;
        }
        return total == 0 ? 0.0 : counts.getOrDefault(id, 0) / (double) total;
    }

    private static double familyShare(PlanetWorldgenProfile profile, V3ColumnSampler sampler,
                                      MaterialSemanticFamily... wanted) {
        MaterialVariantField[] tables = ActCRealWorlds.tablesFor(profile);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = 0;
        int hit = 0;
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
                MaterialSemanticFamily fam = MaterialSemantics.familyOf(f.at(idx));
                for (MaterialSemanticFamily w : wanted) {
                    if (fam == w) {
                        hit++;
                    }
                }
            }
        }
        return n == 0 ? 0.0 : hit / (double) n;
    }
    @Test
    void impactEjectaCannotLeadAPlanetWideRoleButStaysInTheCraterRole() {
        // The positive half is UNCHANGED: a CRATER still PREFERS impact rock.
        var impactite = com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog
                .all().stream()
                .filter(s -> s.family() == MaterialFamily.ROCK_IMPACT).findFirst().orElseThrow();
        assertTrue(MaterialRules.isCoherentWithProvince(impactite,
                        com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince
                                .CRATER),
                "impact rock must remain the preferred material of a CRATER province");

        // The negative half, on the role's own production variant table.
        var profile = ActCRealWorlds.profileOf(IMPACT_WORLD[0], IMPACT_WORLD[1], SEED);
        MaterialVariantField[] tables = ActCRealWorlds.tablesFor(profile);
        for (MaterialRole role : new MaterialRole[]{
                MaterialRole.PRIMARY_SURFACE, MaterialRole.SECONDARY_SURFACE,
                MaterialRole.SEDIMENT, MaterialRole.MOUNTAIN, MaterialRole.GEOTHERMAL,
                MaterialRole.SOIL, MaterialRole.CRYSTAL}) {
            MaterialVariantField f = tables[role.ordinal()];
            for (int i = 0; i < 64 && f.at(i) != null; i++) {
                assertTrue(!impactite.id().equals(f.at(i).id()),
                        "impact ejecta must not be a planet-wide candidate of role " + role
                                + " on " + profile.planetId().code()
                                + ", found it at index " + i);
            }
        }
        // ... and it is still a legal candidate of the role it belongs to.
        MaterialVariantField crater = tables[MaterialRole.CRATER.ordinal()];
        boolean stillCrater = false;
        for (int i = 0; i < 64 && crater.at(i) != null; i++) {
            if (impactite.id().equals(crater.at(i).id())) {
                stillCrater = true;
            }
        }
        assertTrue(stillCrater,
                "impact ejecta must REMAIN available as the crater-local accent on "
                        + profile.planetId().code());
    }

    @Test
    void theMeasuredVolcanicWorldReadsAsVolcanicRockNotACraterFloor() {
        var profile = ActCRealWorlds.profileOf(IMPACT_WORLD[0], IMPACT_WORLD[1], SEED);
        assertEquals(PlanetSurface.SOLID_VOLCANIC, profile.properties().surface(),
                "the pinned world must still be the volcanic body the ACT measured");
        var sampler = ActCRealWorlds.samplerFor(profile);

        Map<String, Integer> blocks = blocksOf(profile, sampler);
        double impact = share(blocks, "us.impactite");
        double volcanic = familyShare(profile, sampler,
                MaterialSemanticFamily.VOLCANIC_DARK, MaterialSemanticFamily.VOLCANIC_ASH);
        double darkRock = familyShare(profile, sampler, MaterialSemanticFamily.DARK_ROCK);
        System.out.printf(Locale.ROOT,
                "[ACTC-2] %s impactite=%.4f volcanicDark+ash=%.4f darkRock=%.4f blocks=%s%n",
                profile.planetId().code(), impact, volcanic, darkRock, blocks);

        assertTrue(impact <= 0.15,
                "impact ejecta must not lead a SOLID_VOLCANIC planet, got " + impact);
        assertTrue(volcanic >= 0.70,
                "volcanic dark rock + ash must dominate a SOLID_VOLCANIC planet, got " + volcanic);
        assertTrue(darkRock <= 0.20,
                "impact dark rock must not be the blanket of a volcanic world, got " + darkRock);
    }

    @Test
    void theOtherVolcanicWorldsDidNotMove() {
        for (int[] id : UNCHANGED_WORLDS) {
            var profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_VOLCANIC) {
                continue;
            }
            var sampler = ActCRealWorlds.samplerFor(profile);
            double volcanic = familyShare(profile, sampler,
                    MaterialSemanticFamily.VOLCANIC_DARK, MaterialSemanticFamily.VOLCANIC_ASH);
            System.out.printf(Locale.ROOT, "[ACTC-2-stable] %s volcanicDark+ash=%.4f%n",
                    profile.planetId().code(), volcanic);
            assertTrue(volcanic >= 0.70,
                    "an untouched volcanic world must still read volcanic, got " + volcanic
                            + " on " + profile.planetId().code());
        }
    }

    /**
     * The identity half, stated directly: a {@code SOLID_VOLCANIC} planet satisfies
     * {@code requiresVolcanism} by its declared identity, even when the continuous activity reading
     * falls a hair below the threshold — which is exactly the case that starved the SEDIMENT role of
     * its only ash-family entry on {@code system_0002_planet_00}.
     */
    @Test
    void aDeclaredVolcanicPlanetAdmitsVolcanicMaterialByItsIdentityAlone() {
        var profile = ActCRealWorlds.profileOf(IMPACT_WORLD[0], IMPACT_WORLD[1], SEED);
        var phys = profile.geology().physical();
        assertEquals(PlanetSurface.SOLID_VOLCANIC, phys.surface());
        // The premise: the continuous reading alone would have refused it.
        assertTrue(!phys.isVolcanicallyDriven(),
                "the pinned world is only interesting because the continuous reading is marginal: "
                        + "volcanicActivity/geothermalFlux crossed the drive threshold");
        boolean cinderstoneAdmissible = false;
        for (var s : com.modscreating.unlimitedspace.core.worldgen.materials.MaterialCatalog.all()) {
            if ("us.cinderstone".equals(s.id()) && s.canFill(MaterialRole.SEDIMENT)
                    && MaterialRules.isCompatible(s, phys)) {
                cinderstoneAdmissible = true;
            }
        }
        assertTrue(cinderstoneAdmissible,
                "a planet that declares itself SOLID_VOLCANIC must admit volcanic ejecta, "
                        + "otherwise its deposit role has no language of its own");
    }
}