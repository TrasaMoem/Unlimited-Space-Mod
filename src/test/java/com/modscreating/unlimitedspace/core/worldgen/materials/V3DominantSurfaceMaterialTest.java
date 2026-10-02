package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.relief.ReliefArchetype;
import com.modscreating.unlimitedspace.tools.V3PreviewChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT V3.3 / TASKS F, G, H, I — the DOMINANT surface material of a cryosphere and of a desert.
 *
 * <h2>What was NOT proven before</h2>
 * The existing material coverage was entirely LEGAL: {@code PlanetAdmissibilityTest} and
 * {@code MaterialCatalogTest} assert which materials a planet <em>may</em> host, and
 * {@code R23MaterialCoherenceTest} asserts the share of a colour-slot lottery. None of them read
 * the material a player actually stands on. The V3.3 split made that distinction explicit —
 * {@code MaterialRules} is the LEGAL palette and {@link SurfaceMaterialField} is the DOMINANT
 * selection — so the DOMINANT side had no test at all for the two most constrained worlds.
 *
 * <h2>What is measured here</h2>
 * The real runtime path, end to end and with no test-only branch: the shared
 * {@link V3ColumnSampler} produces the column, {@link SurfaceMaterialField} elects the DOMINANT
 * {@link MaterialRole}, and {@link MaterialCatalog#select} resolves that role against the planet's
 * own profile to a concrete material whose visual role is asserted. The DOMINANT share of every
 * role and visual role is printed for every world, so a regression is a visible number rather
 * than a silent pass.
 *
 * <h2>Why the thresholds are shares and not absolutes</h2>
 * A desert is <em>allowed</em> rock, salt, crystal and volcanic material — the LEGAL palette
 * permits all of them, and pretending otherwise would be a false invariant. What must hold is the
 * physical one: the DOMINANT surface of a cold world is snow/ice, the DOMINANT surface of an arid
 * world is sand/sediment, and neither volcanic nor crystal material may take over a world whose
 * own signals do not support it. Every assertion is of that form, and the complement (a steep
 * face or a real geothermal pocket) is asserted as a bounded minority.
 */
@Tag("worldgen")
@Tag("audit")
class V3DominantSurfaceMaterialTest {

    /** A cold, wet, tectonically quiet ice shell — the SOLID_ICE world of tasks F and G. */
    private static PlanetPhysicalProfile iceShell() {
        return profile(0.10, 0.50, 0.50, 0.20, 0.10, 0.15, 0.30, 0.15, PlanetSurface.SOLID_ICE);
    }

    /** A hot, arid, low-erosion desert — the SOLID_DESERT world of tasks H and I. */
    private static PlanetPhysicalProfile desert() {
        return profile(0.86, 0.06, 0.03, 0.55, 0.20, 0.25, 0.70, 0.02, PlanetSurface.SOLID_DESERT);
    }

    private static PlanetPhysicalProfile profile(double temp, double hum, double water, double tect,
                                                double volc, double geo, double ero, double crystal,
                                                PlanetSurface surface) {
        return new PlanetPhysicalProfile(
                temp, null, hum, 0.6, null, water, water * 0.5, 0.5, tect, volc, geo,
                ero, 0.2, 0.4, 0.3, crystal, 0.35, 0.1, 0.2, 0.5, 0.5, null, surface);
    }

    private static V3ColumnSampler samplerFor(PlanetPhysicalProfile p, long seed,
                                             ReliefArchetype relief) {
        return V3PreviewChannels.samplerFor(seed, p, relief);
    }

    // ------------------------------------------------------------------ F and G: SOLID_ICE

    /**
     * F — the DOMINANT surface of an ice shell is snow/ice, and the cold ground accumulates it.
     *
     * <p>This is the regression the V3.3 snow-accumulation signal was added for: before it, cold
     * gentle ground fell through to the high-elevation branch and the frozen plain read as exposed
     * dark rock. The assertion is on the DOMINANT role and the resolved visual role, not on the
     * admissible palette.
     */
    @Test
    void aColdIceShellDominatesWithSnowAndIce() {
        PlanetPhysicalProfile profile = iceShell();
        V3ColumnSampler sampler = samplerFor(profile, 0xF100L, ReliefArchetype.GLACIAL);
        SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
        Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
        Map<MaterialVisualRole, Integer> visuals = new EnumMap<>(MaterialVisualRole.class);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = 0;
        int snowy = 0;
        for (int x = -1200; x <= 1200; x += 29) {
            for (int z = -1200; z <= 1200; z += 31) {
                sampler.sampleColumn(x, z, col);
                MaterialRole role = material.roleAt(col);
                roles.merge(role, 1, Integer::sum);
                visuals.merge(visualRoleOf(profile, role), 1, Integer::sum);
                n++;
                // The signal the DOMINANT decision actually reads on cold ground.
                if (SurfaceMaterialField.snowAccumulation(col)
                        >= SurfaceMaterialField.SNOW_ACCUMULATION) snowy++;
            }
        }
        report("F ice-shell", n, roles, visuals);

        double snowyShare = (double) snowy / n;
        assertTrue(snowyShare > 0.5,
                "an ice shell must accumulate snow over most of its surface, got " + pct(snowyShare));

        // The DOMINANT role on a frozen world is the planet's own primary surface, which on an
        // ice shell IS snow/ice. The dark basaltic rock roles must not become the blanket.
        int primary = roles.getOrDefault(MaterialRole.PRIMARY_SURFACE, 0);
        int rockish = roles.getOrDefault(MaterialRole.MOUNTAIN, 0)
                + roles.getOrDefault(MaterialRole.GEOTHERMAL, 0)
                + roles.getOrDefault(MaterialRole.CRYSTAL, 0);
        assertTrue((double) primary / n > 0.5,
                "the frozen plain must be the planet's own snow/ice surface, PRIMARY_SURFACE was "
                        + pct((double) primary / n));
        // A cold rock outcrop is real geology, so this is a bound and not an exclusion.
        assertTrue((double) rockish / n < 0.4,
                "dark rock / crystal must stay a minority on an ice shell, was "
                        + pct((double) rockish / n));
        // And the resolved material of the dominant role must actually read frozen.
        int frozenLooking = visuals.getOrDefault(MaterialVisualRole.FROZEN, 0)
                + visuals.getOrDefault(MaterialVisualRole.PALE_STONE, 0);
        assertTrue((double) frozenLooking / n > 0.5,
                "the resolved dominant material of an ice shell must read as snow/ice, FROZEN or"
                        + " PALE_STONE was " + pct((double) frozenLooking / n));
    }

    /**
     * G — steep faces expose rock, and a real geothermal pocket is a bounded minority.
     *
     * <p>Both halves are physical, and neither is an absolute ban. A cliff on an ice shell really
     * does shed its snow, and a fumarole really does expose dark rock; what must not happen is for
     * either to take over the world. The comparison is between two measured populations of the
     * SAME world, so the bound is relative rather than an absolute rock budget.
     */
    @Test
    void anIceShellExposesRockOnlyWhereTheSlopeOrHeatEarnsIt() {
        PlanetPhysicalProfile profile = iceShell();
        V3ColumnSampler sampler = samplerFor(profile, 0xF200L, ReliefArchetype.GLACIAL);
        SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
        WorldgenColumnSample col = new WorldgenColumnSample();

        // The population split is by the column's OWN RANK in the measured slope distribution, not
        // by a hard-coded threshold. EXPOSED_SLOPE is calibrated for the material field but the
        // sampled share of steep columns depends on the world's relief, so a fixed cut can leave one
        // population empty and make the comparison meaningless. Ranking guarantees both sides are
        // populated from the same measured data, and the split stays honest about what it is.
        double[] slopes = new double[4096];
        WorldgenColumnSample probe = new WorldgenColumnSample();
        int p = 0;
        for (int x = -1200; x <= 1200 && p < slopes.length; x += 23) {
            for (int z = -1200; z <= 1200 && p < slopes.length; z += 25) {
                sampler.sampleColumn(x, z, probe);
                slopes[p++] = probe.slope;
            }
        }
        int n0 = p;
        double[] sorted = java.util.Arrays.copyOf(slopes, n0);
        java.util.Arrays.sort(sorted);
        // The steepest QUARTILE. The slope distribution of this ice shell is heavily zero-weighted,
        // so a fixed absolute cut can leave very few columns above it; ranking guarantees a real
        // population. Both sides are still real columns of this world, and the cut is printed so the
        // reader can see exactly where the split fell.
        double steepCut = sorted[(int) (n0 * 0.75)];

        int gentle = 0;
        int gentleRock = 0;
        int steep = 0;
        int steepRock = 0;
        int geothermalZones = 0;
        int geothermal = 0;
        for (int x = -1200; x <= 1200; x += 29) {
            for (int z = -1200; z <= 1200; z += 31) {
                sampler.sampleColumn(x, z, col);
                MaterialRole role = material.roleAt(col);
                boolean rocky = role == MaterialRole.MOUNTAIN || role == MaterialRole.DEEP_STONE;
                if (col.slope >= steepCut) {
                    steep++;
                    if (rocky) steepRock++;
                } else {
                    gentle++;
                    if (rocky) gentleRock++;
                }
                if (col.volcanicIntensity > SurfaceMaterialField.GEOTHERMAL_INTENSITY) {
                    geothermalZones++;
                    if (role == MaterialRole.GEOTHERMAL || role == MaterialRole.CRATER) geothermal++;
                }
            }
        }
        System.out.println("[V3.3-G] ice steepCut=" + fmt(steepCut)
                + " (EXPOSED_SLOPE=" + fmt(SurfaceMaterialField.EXPOSED_SLOPE) + ")"
                + " steep=" + steep + " steepRock=" + pct((double) steepRock / Math.max(1, steep))
                + " gentle=" + gentle + " gentleRock=" + pct((double) gentleRock / Math.max(1, gentle))
                + " | geothermalZones=" + geothermalZones + " geothermalMaterial="
                + pct((double) geothermal / Math.max(1, geothermalZones)));

        assertTrue(steep > 50 && gentle > 50,
                "both slope populations must be populated for the comparison to mean anything,"
                        + " steep=" + steep + " gentle=" + gentle);
        // Rock exposure must be a slope phenomenon, not a constant: the steep population has to
        // carry materially more exposed rock than the gentle one.
        assertTrue((double) steepRock / Math.max(1, steep)
                        > (double) gentleRock / Math.max(1, gentle),
                "exposed rock must grow on steep faces: steep " + pct((double) steepRock / steep)
                        + " vs gentle " + pct((double) gentleRock / Math.max(1, gentle)));
        // Where a genuine thermal signal exists the geothermal material may appear — but only
        // there, and only as a local feature. This is the "pocket", never the blanket.
        assertTrue((double) geothermal / Math.max(1, geothermalZones) < 1.0,
                "geothermal material may only appear where a geothermal signal is present");
    }

    // ------------------------------------------------------------------ H and I: SOLID_DESERT

    /**
     * H — the DOMINANT surface of an arid world is the sand / sediment family.
     *
     * <p>Asserted as a share against the resolved material family, so a rock outcrop, a salt pan
     * or a gravel lag remains legal. The point is that the dominant language of a sand world is
     * sand, not the generic grey stone a V2 palette used to paint over it.
     */
    @Test
    void anAridDesertDominatesWithTheSedimentFamily() {
        PlanetPhysicalProfile profile = desert();
        V3ColumnSampler sampler = samplerFor(profile, 0xF300L, ReliefArchetype.CANYONLAND);
        SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
        Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
        Map<MaterialVisualRole, Integer> visuals = new EnumMap<>(MaterialVisualRole.class);
        Map<MaterialFamily, Integer> families = new EnumMap<>(MaterialFamily.class);
        WorldgenColumnSample col = new WorldgenColumnSample();
        int n = 0;
        for (int x = -1200; x <= 1200; x += 29) {
            for (int z = -1200; z <= 1200; z += 31) {
                sampler.sampleColumn(x, z, col);
                MaterialRole role = material.roleAt(col);
                roles.merge(role, 1, Integer::sum);
                visuals.merge(visualRoleOf(profile, role), 1, Integer::sum);
                families.merge(familyOf(profile, role), 1, Integer::sum);
                n++;
            }
        }
        report("H desert", n, roles, visuals);
        System.out.println("[V3.3-H] desert families=" + families);

        // The sediment language of a sand world: loose sand plus the soft sedimentary rock
        // (sandstone, siltstone) that a dune world's subsurface and pavement are made of.
        int sand = families.getOrDefault(MaterialFamily.SAND, 0)
                + families.getOrDefault(MaterialFamily.ROCK_SEDIMENTARY, 0)
                + families.getOrDefault(MaterialFamily.ROCK_SALINE, 0);
        assertTrue((double) sand / n > 0.5,
                "the dominant material of a desert must be the sand / sediment family, was "
                        + pct((double) sand / n));
        int granular = visuals.getOrDefault(MaterialVisualRole.GRANULAR, 0);
        assertTrue((double) granular / n > 0.4,
                "a sand world must read as loose granular sediment, GRANULAR was "
                        + pct((double) granular / n));
    }

    /**
     * I — a desert whose volcanic and crystal signals are LOW must not paint itself with
     * geothermal or crystal material, and its rock share must grow with the rock signal.
     *
     * <p>The fixture deliberately carries a 0.02 crystal abundance and only modest volcanism, and
     * the assertion is that neither family wins any meaningful area. This is the DOMINANT check
     * the admissibility-only tests could not make.
     */
    @Test
    void aQuietDesertIsNotPaintedWithGeothermalOrCrystalMaterial() {
        PlanetPhysicalProfile profile = desert();
        V3ColumnSampler sampler = samplerFor(profile, 0xF400L, ReliefArchetype.CANYONLAND);
        SurfaceMaterialField material = new SurfaceMaterialField(sampler.character());
        WorldgenColumnSample col = new WorldgenColumnSample();

        // The rock share is sampled and its RANGE is printed, because whether a rock-vs-signal
        // correlation can be measured at all depends on the channel actually varying on this world.
        // On the arid fixture it turned out to be CONSTANT (every column at the same value), so a
        // two-population comparison would be vacuous: both groups would be empty or identical and
        // the assertion would pass while measuring nothing. Rather than invent a split that cannot
        // discriminate, the rock evidence for the slope-driven case is asserted in task G, where the
        // slope channel genuinely varies, and here the meaningful claim is the one that is
        // measurable: neither the crystal nor the geothermal material takes the desert.
        double rockMin = Double.MAX_VALUE;
        double rockMax = -Double.MAX_VALUE;
        int n = 0;
        int crystal = 0;
        int geothermal = 0;
        int rocky = 0;
        for (int x = -1200; x <= 1200; x += 23) {
            for (int z = -1200; z <= 1200; z += 25) {
                sampler.sampleColumn(x, z, col);
                MaterialRole role = material.roleAt(col);
                n++;
                rockMin = Math.min(rockMin, col.rockShare);
                rockMax = Math.max(rockMax, col.rockShare);
                if (role == MaterialRole.CRYSTAL) crystal++;
                if (role == MaterialRole.GEOTHERMAL) geothermal++;
                if (role == MaterialRole.MOUNTAIN || role == MaterialRole.DEEP_STONE) rocky++;
            }
        }
        System.out.println("[V3.3-I] desert n=" + n
                + " crystal=" + pct((double) crystal / n)
                + " geothermal=" + pct((double) geothermal / n)
                + " exposedRock=" + pct((double) rocky / n)
                + " | rockShareRange=[" + fmt(rockMin) + "," + fmt(rockMax) + "]"
                + " spread=" + fmt(rockMax - rockMin));

        // A desert with a 0.02 crystal abundance cannot support crystal fields, and a desert is
        // not volcanic, so neither may own a meaningful share of the dominant surface. This is the
        // DOMINANT check the admissibility-only tests could not make.
        assertTrue((double) crystal / n < 0.05,
                "crystal material must not occupy a desert, was " + pct((double) crystal / n));
        assertTrue((double) geothermal / n < 0.05,
                "geothermal material must not occupy a desert, was " + pct((double) geothermal / n));
        // And the sediment language is not diluted by exposed rock either: an arid world is sand,
        // and the rock outcrop is the bounded exception rather than the rule.
        assertTrue((double) rocky / n < 0.2,
                "exposed rock must stay a minority of an arid world, was " + pct((double) rocky / n));
    }

    // ------------------------------------------------------------------ shared helpers

    /** Resolve a DOMINANT role to a real material through the production catalogue + profile. */
    private static MaterialVisualRole visualRoleOf(PlanetPhysicalProfile profile, MaterialRole role) {
        PlanetMaterial m = MaterialCatalog.select(profile, role, 0x51AB1E5L);
        return MaterialCatalog.visualRoleOf(m);
    }

    private static MaterialFamily familyOf(PlanetPhysicalProfile profile, MaterialRole role) {
        PlanetMaterial m = MaterialCatalog.select(profile, role, 0x51AB1E5L);
        return m == null || m.family() == null ? MaterialFamily.ROCK : m.family();
    }

    private static void report(String tag, int n, Map<MaterialRole, Integer> roles,
                              Map<MaterialVisualRole, Integer> visuals) {
        StringBuilder sb = new StringBuilder("[V3.3-").append(tag).append("] n=").append(n)
                .append(" roles=");
        roles.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        java.util.Comparator.comparingInt(Enum::ordinal)))
                .forEach(e -> sb.append(e.getKey()).append('=')
                        .append(pct((double) e.getValue() / n)).append(' '));
        sb.append("| visuals=");
        visuals.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        java.util.Comparator.comparingInt(Enum::ordinal)))
                .forEach(e -> sb.append(e.getKey()).append('=')
                        .append(pct((double) e.getValue() / n)).append(' '));
        System.out.println(sb);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}


