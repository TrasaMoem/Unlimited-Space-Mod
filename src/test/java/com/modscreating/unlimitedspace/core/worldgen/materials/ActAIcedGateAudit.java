package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.galaxy.CelestialObject;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.ObjectKind;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetSurfaceMode;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT-A / ITEM 1 - the ICED GATE LEAK audit, on REAL planets.
 *
 * <p>Everything here is read through the production chain
 * ({@code Galaxy -> PlanetWorldgenProfile -> TerrainShaper -> V3ColumnSampler ->
 * SurfaceMaterialField.roleAt -> MaterialVariantField}), and every admission decision is read from
 * the production authorities ({@link MaterialRules}, {@link MaterialSemantics},
 * {@link MaterialCatalog}). Nothing re-derives admissibility, so a leak can never be explained away
 * by the same code that produced it.
 *
 * <p>What it prints per ICE world: the real Kelvin, the {@link PlanetSurface} identity, the resolved
 * {@link PlanetSurfaceMode}, the whole frozen-family candidate list with the reason each one is
 * inadmissible, and the three semantic tiers of the PRIMARY_SURFACE role.
 */
@Tag("worldgen")
@Tag("audit")
class ActAIcedGateAudit {

    private static final long WORLD_SEED = 0L;
    private static final int MIN_PER_FAMILY = 5;
    private static final int GRID = 100;
    private static final int STEP = 160;
    private static final int MAX_SYSTEMS = 64;

    private record World(String label, PlanetWorldgenProfile profile, PlanetPhysicalProfile phys,
                         V3ColumnSampler sampler) {
    }

    /** The real worlds of one geological family, enumerated exactly as the chunk generator does. */
    private static List<World> family(PlanetSurface wanted) {
        List<World> out = new ArrayList<>();
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        for (int system = 0; system < MAX_SYSTEMS && out.size() < MIN_PER_FAMILY; system++) {
            if (!galaxy.exists(system)) {
                continue;
            }
            var sys = galaxy.getStarSystem(galaxy.systemId(system));
            for (CelestialObject obj : sys.canonicalCelestialObjects()) {
                if (obj.kind() != ObjectKind.PLANET || out.size() >= MIN_PER_FAMILY) {
                    continue;
                }
                Planet planet = obj.planet();
                PlanetId pid = planet.id();
                PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(pid, WORLD_SEED);
                if (profile.geology() == null || profile.geology().physical() == null) {
                    continue;
                }
                if (profile.properties().surface() != wanted) {
                    continue;
                }
                V3ColumnSampler sampler = samplerFor(profile);
                if (sampler == null) {
                    continue;
                }
                out.add(new World(wanted.name() + "_" + pid.code(), profile,
                        profile.geology().physical(), sampler));
            }
        }
        return out;
    }

    private static V3ColumnSampler samplerFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        var provinces = profile.geology().provinces();
        var climate = profile.geology().climate();
        TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(), phys, provinces,
                profile.geology().terrainSignature(), profile.baseHeight(), profile.amplitude(),
                null, profile.geology().geography(), climate);
        if (shaper == null) {
            return null;
        }
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(profile.planetSeed()));
        BiomeMaskField biomeField = new BiomeMaskField(character, climateField,
                BiomeMaskField.candidatesFor(
                        PlanetAdmissibility.of(phys, profile.properties().surface()),
                        profile.properties().surface()));
        return new V3ColumnSampler(shaper, climateField, biomeField, provinces);
    }
    // ------------------------------------------------------------------ 1a / 1b: the evidence

    @Test
    void iceShellIdentityAndFrozenAdmissibilityArePrinted() {
        List<World> ices = family(PlanetSurface.SOLID_ICE);
        System.out.println();
        System.out.println("===== ACT-A / ITEM 1a-1b : ICED GATE LEAK, REAL WORLDS =====");
        System.out.println("worlds=" + ices.size());
        for (World w : ices) {
            PlanetPhysicalProfile p = w.phys();
            double kelvin = StellarThermalModel.denormalizeKelvin(p.temperature01());
            PlanetCharacter ch = w.sampler().character();
            PlanetSurfaceMode mode = PlanetSurfaceMode.of(p.surface(), false, p.temperature01(),
                    p.humidity(), ch.duneWeight(), ch.glacialWeight(), ch.volcanicWeight(),
                    p.crystalAbundance(), p.waterAbundance());
            System.out.printf(Locale.ROOT,
                    "## %s  kelvin=%.1f  temp01=%.4f  band=%s  PlanetSurface=%s  surfaceMode=%s%n",
                    w.label(), kelvin, p.temperature01(), p.temperatureBand(), p.surface(), mode);
            System.out.printf(Locale.ROOT,
                    "      humidity=%.3f water=%.3f crystal=%.3f isColdWorld=%s isHotWorld=%s%n",
                    p.humidity(), p.waterAbundance(), p.crystalAbundance(), p.isColdWorld(),
                    p.isHotWorld());

            System.out.println("   FROZEN CANDIDATES (role PRIMARY_SURFACE):");
            boolean any = false;
            for (MaterialSpec s : MaterialCatalog.candidatesFor(MaterialRole.PRIMARY_SURFACE)) {
                MaterialSemanticFamily f = MaterialSemantics.familyOf(s);
                if (f != MaterialSemanticFamily.FROZEN_ICE
                        && f != MaterialSemanticFamily.FROZEN_SNOW
                        && f != MaterialSemanticFamily.FROZEN_ROCK) {
                    continue;
                }
                any = true;
                System.out.printf(Locale.ROOT,
                        "      %-22s family=%-11s tempWindow=[%.2f..%.2f] -> %s%n",
                        s.id(), f, s.minTemperature(), s.maxTemperature(), reason(s, p));
            }
            if (!any) {
                System.out.println("      (none declares PRIMARY_SURFACE)");
            }

            System.out.println("   PRIMARY_SURFACE TIERS:");
            System.out.println("      tier1 suitable = " + tier(p, MaterialRole.PRIMARY_SURFACE, false));
            System.out.println("      tier2 relaxed  = " + tier(p, MaterialRole.PRIMARY_SURFACE, true));
            System.out.println("      final table   = " + finalTable(p, MaterialRole.PRIMARY_SURFACE));
        }
    }

    /** The production candidate list of a role, on the strict or the temperature-relaxed gate. */
    private static List<String> tier(PlanetPhysicalProfile p, MaterialRole role, boolean relaxed) {
        List<String> out = new ArrayList<>();
        for (MaterialSpec s : relaxed
                ? MaterialCatalog.temperatureRelaxedCandidatesFor(p, role)
                : MaterialCatalog.admissibleCandidatesFor(p, role)) {
            MaterialSemanticFamily f = MaterialSemantics.familyOf(s);
            boolean mayLead = MaterialSemantics.mayLead(f, role, p.surface());
            if (relaxed && !mayLead) {
                continue;
            }
            out.add(s.id() + "[" + f + (mayLead ? "" : "!GATED") + "]");
        }
        return out;
    }

    /** What the production variant table actually resolved to, after every tier. */
    private static List<String> finalTable(PlanetPhysicalProfile p, MaterialRole role) {
        List<String> out = new ArrayList<>();
        MaterialVariantField f = MaterialVariantField.forRole(p, role, 0xA1C7L, null);
        for (int i = 0; i < f.size(); i++) {
            out.add(f.at(i).id() + "[" + MaterialSemantics.familyOf(f.at(i)) + "]");
        }
        return out;
    }

    /** The exact production rule that refuses this material here, in words. */
    private static String reason(MaterialSpec s, PlanetPhysicalProfile p) {
        if (p.temperature01() < s.minTemperature() - 1e-9) {
            return "INADMISSIBLE temp01 " + f(p.temperature01()) + " < min " + f(s.minTemperature());
        }
        if (p.temperature01() > s.maxTemperature() + 1e-9) {
            return "INADMISSIBLE temp01 " + f(p.temperature01()) + " > max " + f(s.maxTemperature())
                    + "  (world is " + String.format(Locale.ROOT, "%.1f",
                    StellarThermalModel.denormalizeKelvin(p.temperature01()))
                    + " K; the frost point is 273.15 K)";
        }
        if (p.humidity() < s.minHumidity() - 1e-9 || p.humidity() > s.maxHumidity() + 1e-9) {
            return "INADMISSIBLE humidity " + f(p.humidity()) + " outside ["
                    + f(s.minHumidity()) + ".." + f(s.maxHumidity()) + "]";
        }
        if (s.family().isColdOnly() && p.isHotWorld()) {
            return "INADMISSIBLE cold-only family on a HOT world";
        }
        if (s.requiresWater() && !p.canHoldSurfaceLiquid() && p.waterAbundance() < 0.25) {
            return "INADMISSIBLE requiresWater";
        }
        if (s.hasTag(MaterialTag.CRYOGENIC) && p.temperature01() > 0.35 + 1e-9) {
            return "INADMISSIBLE CRYOGENIC tag above temp01 0.35";
        }
        if (!MaterialRules.coherentForSurface(s, MaterialRole.PRIMARY_SURFACE, p.surface())) {
            return "INADMISSIBLE coherentForSurface veto (visual " + s.visualRole() + ")";
        }
        MaterialSemanticFamily f = MaterialSemantics.familyOf(s);
        return "ADMISSIBLE, but mayLeadSurface(" + f + "," + p.surface() + ")="
                + MaterialSemantics.mayLeadSurface(f, p.surface());
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
    // ------------------------------------------------------------------ 1d / 1e: the contract

    /**
     * ITEM 1d + 1e - the relaxed path must NEVER return a semantically prohibited material, and
     * an ice shell must read as ice: SAND at most 5%, frozen family at least 40%.
     */
    @Test
    void relaxedPathNeverReturnsASemanticallyForbiddenMaterialAndIceReadsAsIce() {
        List<World> ices = family(PlanetSurface.SOLID_ICE);
        assertTrue(ices.size() >= MIN_PER_FAMILY, "need >= " + MIN_PER_FAMILY
                + " real ice worlds, got " + ices.size());

        System.out.println();
        System.out.println("===== ACT-A / ITEM 1d-1e : RELAXED GATE + ICE IDENTITY =====");
        for (World w : ices) {
            PlanetPhysicalProfile p = w.phys();
            MaterialVariantField[] tables = tablesFor(p, w.profile().planetSeed());
            Map<String, Integer> blocks = new TreeMap<>();
            Map<MaterialSemanticFamily, Integer> families = new TreeMap<>();
            Map<MaterialRole, Integer> roles = new EnumMap<>(MaterialRole.class);
            int sand = 0;
            int frozen = 0;
            int n = GRID * GRID;
            WorldgenColumnSample col = new WorldgenColumnSample();
            int half = GRID / 2;
            for (int iz = 0; iz < GRID; iz++) {
                for (int ix = 0; ix < GRID; ix++) {
                    int x = (ix - half) * STEP;
                    int z = (iz - half) * STEP;
                    w.sampler().sampleColumn(x, z, col);
                    MaterialVariantField table = col.materialRole == null
                            ? null : tables[col.materialRole.ordinal()];
                    roles.merge(col.materialRole == null ? MaterialRole.ACCENT : col.materialRole,
                            1, Integer::sum);
                    if (table == null) {
                        continue;
                    }
                    int idx = table.index(col, x, z);
                    if (idx < 0) {
                        continue;
                    }
                    PlanetMaterial m = table.at(idx);
                    blocks.merge(m.id(), 1, Integer::sum);
                    MaterialSemanticFamily fam = MaterialSemantics.familyOf(m);
                    families.merge(fam, 1, Integer::sum);
                    if (fam == MaterialSemanticFamily.SAND || fam == MaterialSemanticFamily.RED_DUST) {
                        sand++;
                    }
                    if (fam == MaterialSemanticFamily.FROZEN_ICE
                            || fam == MaterialSemanticFamily.FROZEN_SNOW
                            || fam == MaterialSemanticFamily.FROZEN_ROCK) {
                        frozen++;
                    }
                    // ITEM 1d: the structural claim, checked on EVERY column of EVERY role.
                    // ITEM 1d, the invariant that actually holds for every column of every role.
                    // A returned material must be EITHER semantically admissible for its own role on
                    // this planet (mayLead), OR the planet's own surface language (mayLeadSurface),
                    // OR competent rock (the documented tier-3 substrate). What it may NEVER be is a
                    // foreign deposit: SAND / SEDIMENT / RED_DUST on an ice shell, SAND on a
                    // volcanic one - which is exactly what the old relaxed tier produced.
                    // CRYSTAL roles legitimately yield CRYSTALLINE (mayLead allows it by design), and
                    // MOUNTAIN roles legitimately yield a cemented bed where rock exposure earned it.
                    assertTrue(MaterialSemantics.mayLead(fam, col.materialRole, p.surface())
                                    || MaterialSemantics.mayLeadSurface(fam, p.surface())
                                    || MaterialSemantics.isRockFamily(fam),
                            w.label() + ": a candidate table returned " + m.id() + " (" + fam
                                    + ") in role " + col.materialRole
                                    + ", which is a foreign deposit on a " + p.surface() + " world");
                }
            }
            double sandShare = sand / (double) n;
            double frozenShare = frozen / (double) n;
            System.out.printf(Locale.ROOT, "## %s  columns=%d%n", w.label(), n);
            System.out.println("   blockShare  = " + pct(blocks, n));
            System.out.println("   familyShare = " + pctFam(families, n));
            System.out.println("   roleShare   = " + pctRole(roles, n));
            System.out.printf(Locale.ROOT, "   SAND+RED_DUST=%.4f   FROZEN=%.4f%n",
                    sandShare, frozenShare);
            assertTrue(sandShare <= 0.05, w.label()
                    + ": sand/regolith must stay <= 5% of an ice shell, got " + f(sandShare)
                    + " (" + pct(blocks, n) + ")");
            assertTrue(frozenShare >= 0.40, w.label()
                    + ": the frozen family must hold >= 40% of an ice shell, got " + f(frozenShare)
                    + " (" + pctFam(families, n) + ")");
        }
    }
    private static MaterialVariantField[] tablesFor(PlanetPhysicalProfile p, long seed) {
        MaterialVariantField[] tables = new MaterialVariantField[MaterialRole.values().length];
        for (MaterialRole role : MaterialRole.values()) {
            tables[role.ordinal()] = MaterialVariantField.forRole(p, role, seed, null);
        }
        return tables;
    }

    private static String pct(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey(), 100.0 * e.getValue() / n));
            if (i >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String pctFam(Map<MaterialSemanticFamily, Integer> m, int n) {
        List<Map.Entry<MaterialSemanticFamily, Integer>> sorted = new ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<MaterialSemanticFamily, Integer> e : sorted) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey().name(),
                    100.0 * e.getValue() / n));
            if (i >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private static String pctRole(Map<MaterialRole, Integer> m, int n) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<MaterialRole, Integer> e : m.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%s %.1f%%", e.getKey().name(),
                    100.0 * e.getValue() / n));
        }
        return sb.toString();
    }
}
