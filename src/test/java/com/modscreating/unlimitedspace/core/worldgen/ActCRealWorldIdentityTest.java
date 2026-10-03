package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.presentation.WorldStatusText;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.surface.SurfaceCategory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT-C ITEM 1c/1d — GEOLOGY IS IDENTITY, CLIMATE ONLY MODULATES, asserted on REAL WORLD IDs.
 *
 * <h2>Why a synthetic profile is not enough</h2>
 * The identity contract can be proved on a hand-built profile in one line, and it was already
 * proved there ({@code ColdRockyIdentityTest}). What a fixture cannot prove is that the REAL
 * pipeline keeps it: the mode resolver, the surface-category classifier and the UI authority are
 * three separate reads of a planet, and only a real world carries all three at once with a real
 * temperature, a real weight set and a real surface class. This test therefore walks real planets
 * of two real seeds.
 *
 * <h2>The measured defect this pins (seed 0)</h2>
 * <pre>
 *   system_0000_planet_00  SOLID_ROCKY     K=217.9 t01=0.394 glacW=0.971 crystal=0.622
 *        surface CATEGORY: FROZEN on 86.6% of columns      &lt;- cold rewrote geology
 *   system_0002_planet_00  SOLID_VOLCANIC  K=468.3 t01=0.546 duneW=0.652 hum=0.305
 *        surface CATEGORY: SANDY on up to 66.7% of columns &lt;- dune rewrote geology
 * </pre>
 * while the MODE resolver was already correct on both ({@code CRYSTAL} and {@code VOLCANIC}). The
 * leak was one layer down, in the per-column classifier, which had no planet context at all.
 *
 * <h2>The UI table rows</h2>
 * The navigation panel's {@code Surface} row is fed by the same authority, so this also pins the
 * two rows that had no coverage before: {@code CRYSTAL} (a worldgen mode with no synthetic fixture)
 * and {@code FOREST} (a {@code PlanetType} whose planets are {@code SOLID_ROCKY} on the surface,
 * i.e. the case where the type and the surface disagree by design).
 */
@Tag("worldgen")
class ActCRealWorldIdentityTest {

    /** The seed whose worlds this ACT measured; plus the running single-player world as a second. */
    private static final long SEED = 0L;
    private static final long RUNNING_SEED = 403244903253430305L;

    /** The columns each world is measured on. Enough to see a dominant category, cheap enough to run. */
    private static final int GRID = 40;
    private static final int STEP = 160;

    /** The specific real worlds the ACT measured, pinned by ID so a regression names itself. */
    private static final int[][] ROCKY_WORLDS = {
            {0, 0}, {1, 0}, {2, 2}, {2, 3}, {3, 0}, {3, 1}, {0, 2}};
    private static final int[][] VOLCANIC_WORLDS = {
            {2, 0}, {4, 0}, {6, 1}, {11, 2}, {14, 2}};

    /** The surface-category histogram of one real world, sampled on the standard grid. */
    private static Map<SurfaceCategory, Integer> categoriesOf(V3ColumnSampler sampler) {
        WorldgenColumnSample col = new WorldgenColumnSample();
        Map<SurfaceCategory, Integer> cats = new TreeMap<>();
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                sampler.sampleColumn((ix - GRID / 2) * STEP, (iz - GRID / 2) * STEP, col);
                cats.merge(col.surfaceCategory, 1, Integer::sum);
            }
        }
        return cats;
    }

    private static double shareOf(Map<SurfaceCategory, Integer> cats, SurfaceCategory wanted) {
        int total = 0;
        for (int v : cats.values()) {
            total += v;
        }
        return total == 0 ? 0.0 : cats.getOrDefault(wanted, 0) / (double) total;
    }

    private static double frozenShare(Map<SurfaceCategory, Integer> cats) {
        int total = 0;
        int frozen = 0;
        for (Map.Entry<SurfaceCategory, Integer> e : cats.entrySet()) {
            total += e.getValue();
            if (e.getKey().isFrozen()) {
                frozen += e.getValue();
            }
        }
        return total == 0 ? 0.0 : frozen / (double) total;
    }
    @Test
    void aColdRockyWorldKeepsRockOnItsRockColumns() {
        int checked = 0;
        for (int[] id : ROCKY_WORLDS) {
            PlanetWorldgenProfile profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_ROCKY) {
                continue;
            }
            V3ColumnSampler sampler = ActCRealWorlds.samplerFor(profile);
            Map<SurfaceCategory, Integer> cats = categoriesOf(sampler);
            double frozen = frozenShare(cats);
            PlanetCharacter ch = new PlanetCharacter(profile.geology().physical());
            System.out.printf(Locale.ROOT,
                    "[ACTC-1c] %s SOLID_ROCKY K=%.1f glacW=%.3f cats=%s frozenShare=%.4f%n",
                    profile.planetId().code(),
                    com.modscreating.unlimitedspace.core.physics.StellarThermalModel
                            .denormalizeKelvin(profile.geology().physical().temperature01()),
                    ch.glacialWeight(), cats, frozen);
            assertTrue(frozen <= 0.60,
                    "a SOLID_ROCKY world must not read as ice on a majority of its columns, got "
                            + frozen + " for " + profile.planetId().code()
                            + " (cold must modulate, not rewrite geology)");
            checked++;
        }
        assertTrue(checked > 0, "the walk must actually reach real SOLID_ROCKY worlds");
    }

    @Test
    void aVolcanicWorldNeverReadsAsSand() {
        int checked = 0;
        for (int[] id : VOLCANIC_WORLDS) {
            PlanetWorldgenProfile profile = ActCRealWorlds.profileOf(id[0], id[1], SEED);
            if (profile.properties().surface() != PlanetSurface.SOLID_VOLCANIC) {
                continue;
            }
            V3ColumnSampler sampler = ActCRealWorlds.samplerFor(profile);
            Map<SurfaceCategory, Integer> cats = categoriesOf(sampler);
            double sandy = shareOf(cats, SurfaceCategory.SANDY);
            PlanetCharacter ch = new PlanetCharacter(profile.geology().physical());
            System.out.printf(Locale.ROOT,
                    "[ACTC-1b] %s SOLID_VOLCANIC duneW=%.3f hum=%.3f cats=%s sandy=%.4f%n",
                    profile.planetId().code(), ch.duneWeight(),
                    profile.geology().physical().humidity(), cats, sandy);
            assertEquals(0.0, sandy,
                    "a SOLID_VOLCANIC world must never classify a column as SANDY, "
                            + profile.planetId().code()
                            + ": loose material on an edifice is ejecta, not an erg");
            checked++;
        }
        assertTrue(checked > 0, "the walk must actually reach real SOLID_VOLCANIC worlds");
    }

    /**
     * ITEM 1d — every real world resolves its mode from its geological identity, and the UI shows
     * exactly that mode. Both seeds, so a fix cannot pass on one galaxy layout only.
     */
    @Test
    void everyRealWorldResolvesItsModeFromItsIdentityAndTheUiAgrees() {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (long seed : new long[]{SEED, RUNNING_SEED}) {
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(seed);
            outer:
            for (int system = 0; system < 40 && checked < 220; system++) {
                if (!galaxy.exists(system)) {
                    continue;
                }
                var sys = galaxy.getStarSystem(galaxy.systemId(system));
                for (var obj : sys.canonicalCelestialObjects()) {
                    if (obj.kind()
                            != com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET) {
                        continue;
                    }
                    var profile = ActCRealWorlds.profileOf(obj.planet().id(), seed);
                    if (profile.geology() == null || profile.geology().physical() == null) {
                        continue;
                    }
                    PlanetSurfaceMode mode = PlanetSurfaceMode.forProfile(profile);
                    PlanetCharacter ch = new PlanetCharacter(profile.geology().physical());
                    PlanetPhysicalProfile phys = profile.geology().physical();
                    String id = obj.planet().id().code();
                    switch (profile.properties().surface()) {
                        case SOLID_ROCKY -> {
                            if (mode != PlanetSurfaceMode.SOLID_ROCKY
                                    && mode != PlanetSurfaceMode.CRYSTAL) {
                                mismatches.add(id + " SOLID_ROCKY -> " + mode
                                        + " (glacW=" + ch.glacialWeight()
                                        + " duneW=" + ch.duneWeight() + ")");
                            }
                        }
                        case SOLID_VOLCANIC -> {
                            if (mode != PlanetSurfaceMode.VOLCANIC) {
                                mismatches.add(id + " SOLID_VOLCANIC -> " + mode
                                        + " (duneW=" + ch.duneWeight()
                                        + " hum=" + phys.humidity() + ")");
                            }
                        }
                        case SOLID_ICE -> {
                            if (mode != PlanetSurfaceMode.GLACIAL) {
                                mismatches.add(id + " SOLID_ICE -> " + mode);
                            }
                        }
                        case SOLID_DESERT -> {
                            if (mode != PlanetSurfaceMode.DUNE_ARID) {
                                mismatches.add(id + " SOLID_DESERT -> " + mode);
                            }
                        }
                        case OCEANIC -> {
                            if (mode != PlanetSurfaceMode.OCEANIC) {
                                mismatches.add(id + " OCEANIC -> " + mode);
                            }
                        }
                        case GASEOUS -> {
                            if (mode != PlanetSurfaceMode.GAS_GIANT) {
                                mismatches.add(id + " GASEOUS -> " + mode);
                            }
                        }
                    }
                    assertEquals(mode.displayLabel(),
                            WorldStatusText.surfaceAuthorityLabel(seed, obj.planet()),
                            "the navigation Surface row must be the worldgen mode on a real world: "
                                    + id);
                    checked++;
                    if (checked >= 220) {
                        break outer;
                    }
                }
            }
        }
        assertTrue(checked > 100,
                "the walk must reach a real, substantial population of worlds, got " + checked);
        assertTrue(mismatches.isEmpty(),
                "climate rewrote geology on real worlds: " + mismatches);
    }

    /**
 * ITEM 1d — the UI table gains its two missing rows.
 *
 * <ul>
 *   <li><b>CRYSTAL</b>: a real worldgen mode whose label must exist and must be distinct from
 *       every other mode, so a crystal world is nameable rather than folded into "Rocky";</li>
 *   <li><b>FOREST</b>: a {@code PlanetType} whose planets carry the SOLID_ROCKY surface, i.e.
 *       exactly the case where the archetype and the geological surface legitimately disagree.
 *       The panel must still render a real, non-blank Surface row for it.</li>
 * </ul>
 */
    @Test
    void theUiTableHasACrystalAndAForestRow() {
        // CRYSTAL: displayable, and distinct from the ordinary rocky label.
        assertEquals("Crystal", PlanetSurfaceMode.CRYSTAL.displayLabel());
        assertFalse(PlanetSurfaceMode.CRYSTAL.displayLabel()
                        .equals(PlanetSurfaceMode.SOLID_ROCKY.displayLabel()),
                "a crystal world must not be labelled like an ordinary rocky one");

        // FOREST: the orbital body-kind table carries the row, and it carries a colour.
        var forest = java.util.Arrays.stream(
                        com.modscreating.unlimitedspace.client.nav.SystemOrbitalRenderer.BodyKind
                                .values())
                .filter(k -> k.name().equals("FOREST"))
                .findFirst();
        assertTrue(forest.isPresent(), "the body-kind table must carry a FOREST row");
        assertNotNull(planetKindOf(
                        com.modscreating.unlimitedspace.core.planets.PlanetType.FOREST),
                "a FOREST-typed planet must resolve to a real body kind, not a blank one");

        // And a real FOREST-typed planet resolves to the rocky surface and a real Surface label.
        var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(SEED);
        int seen = 0;
        for (int system = 0; system < 40 && seen < 3; system++) {
            if (!galaxy.exists(system)) {
                continue;
            }
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (var obj : sys.canonicalCelestialObjects()) {
                if (obj.kind() != com.modscreating.unlimitedspace.core.galaxy.ObjectKind.PLANET
                        || obj.planet().properties().type()
                        != com.modscreating.unlimitedspace.core.planets.PlanetType.FOREST) {
                    continue;
                }
                var profile = ActCRealWorlds.profileOf(obj.planet().id(), SEED);
                PlanetSurfaceMode mode = PlanetSurfaceMode.forProfile(profile);
                assertTrue(mode == PlanetSurfaceMode.SOLID_ROCKY
                                || mode == PlanetSurfaceMode.CRYSTAL,
                        "a FOREST-typed planet is a rocky body on the surface, got " + mode);
                String ui = WorldStatusText.surfaceAuthorityLabel(SEED, obj.planet());
                assertEquals(mode.displayLabel(), ui);
                assertFalse(ui.isBlank(), "the FOREST row must never render blank");
                seen++;
                if (seen >= 3) {
                    break;
                }
            }
        }
        assertTrue(seen > 0, "the walk must reach a real FOREST-typed planet");
    }

    /** The body kind the navigation screen assigns to a planet archetype. */
    private static Object planetKindOf(com.modscreating.unlimitedspace.core.planets.PlanetType t) {
        for (var k : com.modscreating.unlimitedspace.client.nav.SystemOrbitalRenderer.BodyKind
                .values()) {
            String name = k.name();
            if (t.name().equals(name) || (name.equals("OCEAN") && t.name().equals("OCEAN"))
                    || (name.equals("ICE") && t.name().equals("ICE"))) {
                return k;
            }
        }
        return null;
    }
}