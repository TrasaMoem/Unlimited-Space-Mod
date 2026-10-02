package com.modscreating.unlimitedspace.tools;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.biome.BiomeMaskField;
import com.modscreating.unlimitedspace.core.worldgen.biome.SubBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.V3ColumnSampler;
import com.modscreating.unlimitedspace.core.worldgen.biome.WorldgenColumnSample;
import com.modscreating.unlimitedspace.core.worldgen.character.PlanetCharacter;
import com.modscreating.unlimitedspace.core.worldgen.climate.ClimateField;
import com.modscreating.unlimitedspace.core.worldgen.climate.PlanetClimateProfile;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceMap;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetColorTheme;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialRoleSelector;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.TemperatureBand;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainShaper;
import com.modscreating.unlimitedspace.core.worldgen.terrain.WindDirectionField;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * WORLDGEN V3.2 - the headless DISTRIBUTION AUDIT over many real planets.
 *
 * <p>The SAME engine produces the Phase 0 baseline and the Phase 10 post-fix run, so the two
 * numbers are directly comparable. It is a pure-domain tool that drives the real production
 * objects and never touches Minecraft types.
 *
 * <h2>Why the violation predicates live HERE and not in production code</h2>
 * A baseline must be measured against an INDEPENDENT statement of physics; otherwise the audit
 * would be scored with the same rule the fix implements and would always report "0 violations".
 * {@link Facts} derives every predicate directly from the raw physical profile and stays valid
 * after the production filters exist.
 */
public final class PlanetAdmissibilityAudit {

    private PlanetAdmissibilityAudit() {}

    /** One named, counted planet-level incoherence. */
    public enum Violation {
        CRYSTAL_PRIMARY_ON_ARID,
        VOLCANIC_PRIMARY_ON_DESERT,
        FROZEN_PRIMARY_ON_HOT,
        ALIEN_CRYSTAL_THEME_ON_BAD_BAND,
        DESERT_THEME_ON_INFERNO,
        FORBIDDEN_PROVINCE,
        FORBIDDEN_SUBBIOME,
        FORBIDDEN_BIOME,
        LIQUID_WATER_IMPOSSIBLE,
        GLACIAL_IMPOSSIBLE;

        public String label() {
            return name();
        }
    }

    /**
     * The raw, production-independent physics of one planet. Every audit predicate is derived
     * from these facts alone, so it is a fair referee both before and after the V3.2 filters.
     */
    public record Facts(
            PlanetType type,
            PlanetSurface surface,
            TemperatureBand band,
            double humidity,
            double waterAbundance,
            WaterPhaseModel.Phase phase,
            boolean volcanicallyDriven,
            double crystalAbundance,
            double kelvin
    ) {
        public static Facts of(PlanetProperties p, PlanetPhysicalProfile phys) {
            return new Facts(p.type(), p.surface(), phys.temperatureBand(), phys.humidity(),
                    phys.waterAbundance(), WaterPhaseModel.ofProfile(phys),
                    phys.isVolcanicallyDriven(), phys.crystalAbundance(),
                    StellarThermalModel.denormalizeKelvin(phys.temperature01()));
        }

        /** Arid atmosphere: the surface language may not be crystalline. */
        public boolean arid() {
            return humidity < 0.30;
        }

        /** Hot half of the spectrum: no frozen surface language. */
        public boolean hot() {
            return band.isHot();
        }

        public boolean crystalTerrainPossible() {
            return crystalAbundance >= 0.30 && !hot();
        }

        public boolean volcanicTerrainPossible() {
            return volcanicallyDriven;
        }

        public boolean glacialTerrainPossible() {
            return kelvin < 273.15;
        }

        public boolean liquidWaterPossible() {
            return phase.allowsLiquid();
        }

        public boolean organicPossible() {
            return liquidWaterPossible() && kelvin >= 265.0 && kelvin <= 335.0;
        }

        public boolean saltPossible() {
            return waterAbundance >= 0.20;
        }
    }

    /** The accumulated audit numbers, printed verbatim into the V3.2 report. */
    public static final class Report {
        public int planets;
        public int columns;
        public int gaseousSkipped;

        public final Map<String, Integer> planetType = new TreeMap<>();
        public final Map<String, Integer> surface = new TreeMap<>();
        public final Map<String, Integer> band = new TreeMap<>();
        public final Map<String, Integer> theme = new TreeMap<>();
        public final Map<String, Integer> electedBiome = new TreeMap<>();
        public final Map<String, Integer> province = new TreeMap<>();
        public final Map<String, Integer> subBiome = new TreeMap<>();
        public final Map<String, Integer> materialRole = new TreeMap<>();
        public final Map<String, Integer> surfaceCategory = new TreeMap<>();
        public final Map<String, Integer> blockId = new TreeMap<>();
        public final Map<Violation, Integer> violations = new EnumMap<>(Violation.class);

        public int planetsWithForbiddenBiome;
        public int planetsWithForbiddenSubBiome;
        public int planetsWithForbiddenProvince;
        public int planetsWithForbiddenPrimarySurface;
        /** V3.2 diagnostic: which surface / material family pair each primary violation came from. */
        public final Map<String, Integer> primaryCauses = new TreeMap<>();

        public int violationCount() {
            int t = 0;
            for (int v : violations.values()) t += v;
            return t;
        }

        public int count(Violation v) {
            Integer n = violations.get(v);
            return n == null ? 0 : n;
        }

        public double share(Violation v) {
            return columns <= 0 ? 0.0 : (double) count(v) / columns;
        }

        public String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== V3.2 PLANET ADMISSIBILITY DISTRIBUTION AUDIT ===\n");
            sb.append("planets=").append(planets)
                    .append("  columns=").append(columns)
                    .append("  gasGiantsSkipped=").append(gaseousSkipped).append('\n');
            section(sb, "PlanetType", planetType, planets);
            section(sb, "PlanetSurface", surface, planets);
            section(sb, "TemperatureBand", band, planets);
            section(sb, "PlanetColorTheme", theme, planets);
            section(sb, "Province (dominant, per column)", province, columns);
            section(sb, "Elected BiomeCandidate (per column)", electedBiome, columns);
            section(sb, "SubBiome (per column)", subBiome, columns);
            section(sb, "MaterialRole (per column)", materialRole, columns);
            section(sb, "SurfaceCategory (per column)", surfaceCategory, columns);
            section(sb, "Block id (per column)", blockId, columns);
            sb.append("--- FORBIDDEN COMBINATIONS ---\n");
            for (Violation v : Violation.values()) {
                sb.append(String.format("%-34s %8d  %7.3f%%%n", v.label(), count(v),
                        share(v) * 100.0));
            }
            sb.append("--- VIOLATING PRIMARY MATERIALS ---\n");
            for (Map.Entry<String, Integer> e : primaryCauses.entrySet()) {
                sb.append(String.format("  %-44s %8d%n", e.getKey(), e.getValue()));
            }
            sb.append("--- PLANETS AFFECTED ---\n");
            sb.append("planetsWithForbiddenPrimarySurface=")
                    .append(planetsWithForbiddenPrimarySurface).append('\n');
            sb.append("planetsWithForbiddenProvince=").append(planetsWithForbiddenProvince).append('\n');
            sb.append("planetsWithForbiddenBiome=").append(planetsWithForbiddenBiome).append('\n');
            sb.append("planetsWithForbiddenSubBiome=").append(planetsWithForbiddenSubBiome).append('\n');
            return sb.toString();
        }

        private static void section(StringBuilder sb, String title, Map<String, Integer> m, int total) {
            sb.append("--- ").append(title).append(" (").append(total).append(") ---\n");
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                double pct = total <= 0 ? 0.0 : 100.0 * e.getValue() / total;
                sb.append(String.format("  %-34s %8d  %7.3f%%%n", e.getKey(), e.getValue(), pct));
            }
        }
    }

    /** Column budget per planet (keeps the 2000-planet run interactive). */
    private static final int COLUMNS_PER_PLANET = 12;
    private static final int COLUMN_STEP = 317;

    /**
     * Run the audit.
     *
     * @param worldSeeds how many distinct world seeds to draw systems/orbits from
     * @param maxPlanets hard cap on the number of planets actually profiled
     */
    public static Report run(int worldSeeds, int maxPlanets) {
        Report r = new Report();
        for (Violation v : Violation.values()) r.violations.put(v, 0);
        int audited = 0;
        outer:
        for (int w = 0; w < worldSeeds && audited < maxPlanets; w++) {
            long worldSeed = 0x5EED0000L + w * 0x9E3779B1L;
            Galaxy galaxy = Galaxy.from(worldSeed);
            for (int sys = 0; sys < 8; sys++) {
                for (int orbit = 0; orbit < 8; orbit++) {
                    if (audited >= maxPlanets) break outer;
                    try {
                        auditPlanet(galaxy, worldSeed, sys, orbit, r);
                    } catch (RuntimeException ignored) {
                        // A planet that cannot be profiled is not a distribution finding.
                    }
                    audited++;
                }
            }
        }
        return r;
    }

    /** Audit one planet: identity, theme, province table, palette, then the column pipeline. */
    private static void auditPlanet(Galaxy galaxy, long worldSeed, int sys, int orbit, Report r) {
        PlanetId pid = PlanetId.of(StarSystemId.of(sys), orbit);
        Planet planet = galaxy.getStarSystem(StarSystemId.of(sys)).getPlanet(orbit);
        if (planet == null) return;
        PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(pid, worldSeed);
        PlanetPhysicalProfile phys = profile.geology().physical();
        Facts facts = Facts.of(planet.properties(), phys);

        r.planets++;
        bump(r.planetType, facts.type().name());
        bump(r.surface, facts.surface().name());
        bump(r.band, facts.band().name());

        // ---- planet-level: the color theme (a HARD identity, no columns involved) ----
        PlanetColorTheme theme = profile.geology().colorTheme();
        bump(r.theme, theme == null ? "?" : theme.name());
        if (theme == PlanetColorTheme.ALIEN_CRYSTAL_THEME
                && (facts.hot() || facts.band() == TemperatureBand.FROZEN)) {
            r.violations.merge(Violation.ALIEN_CRYSTAL_THEME_ON_BAD_BAND, 1, Integer::sum);
        }
        if (theme == PlanetColorTheme.DESERT_THEME && facts.band() == TemperatureBand.INFERNO) {
            r.violations.merge(Violation.DESERT_THEME_ON_INFERNO, 1, Integer::sum);
        }

        // ---- planet-level: the province reachability table ----
        GeologicalProvinceMap provinces = profile.geology().provinces();
        for (GeologicalProvince pr : provinces.provinces()) {
            if (forbiddenProvince(facts, pr)) {
                r.violations.merge(Violation.FORBIDDEN_PROVINCE, 1, Integer::sum);
                r.planetsWithForbiddenProvince++;
            }
        }

        // ---- planet-level: the palette PRIMARY_SURFACE ----
        PlanetMaterial primary = profile.geology().palette().primarySurface();
        bump(r.blockId, primary == null ? "?" : primary.blockId());
        judgePrimary(r, facts, primary == null ? null : primary.family());

        // ---- a gas giant has no surface columns at all ----
        if (facts.surface() == PlanetSurface.GASEOUS) {
            r.gaseousSkipped++;
            return;
        }
        auditColumns(r, profile, facts, provinces, theme);
    }

    /** The three PRIMARY_SURFACE coherence rules, counted identically at planet and column level. */
    private static void judgePrimary(Report r, Facts facts, MaterialFamily family) {
        if (isCrystalline(family) && facts.arid()) {
            r.violations.merge(Violation.CRYSTAL_PRIMARY_ON_ARID, 1, Integer::sum);
            r.planetsWithForbiddenPrimarySurface++;
        }
        if (isVolcanic(family) && facts.surface() == PlanetSurface.SOLID_DESERT) {
            r.violations.merge(Violation.VOLCANIC_PRIMARY_ON_DESERT, 1, Integer::sum);
            bump(r.primaryCauses, facts.surface() + "/" + family);
            r.planetsWithForbiddenPrimarySurface++;
        }
        if (isFrozen(family) && facts.hot()) {
            r.violations.merge(Violation.FROZEN_PRIMARY_ON_HOT, 1, Integer::sum);
            r.planetsWithForbiddenPrimarySurface++;
        }
    }

    /** Audit the real V3 column pipeline of one planet. */
    private static void auditColumns(Report r, PlanetWorldgenProfile profile, Facts facts,
                                     GeologicalProvinceMap provinces, PlanetColorTheme theme) {
        V3ColumnSampler sampler = samplerFor(profile);
        if (sampler == null) return;
        WorldgenColumnSample col = new WorldgenColumnSample();
        long materialSeed = profile.materialSeed();
        boolean badBiome = false;
        boolean badSub = false;

        for (int k = 0; k < COLUMNS_PER_PLANET; k++) {
            int x = (k % 4) * COLUMN_STEP * 3 - 500;
            int z = (k / 4) * COLUMN_STEP * 3 - 700;
            sampler.sampleColumn(x, z, col);
            r.columns++;

            // province
            GeologicalProvince pr = provinces.provinceAt(x, z);
            bump(r.province, pr.name());

            // elected biome
            String biomeId = col.biome == null ? "?" : col.biome.id();
            bump(r.electedBiome, biomeId);
            if (forbiddenBiome(facts, biomeId)) {
                r.violations.merge(Violation.FORBIDDEN_BIOME, 1, Integer::sum);
                badBiome = true;
            }
            if ("wetland".equals(biomeId) && !facts.liquidWaterPossible()) {
                r.violations.merge(Violation.LIQUID_WATER_IMPOSSIBLE, 1, Integer::sum);
            }
            if (glacialBiome(biomeId) && !facts.glacialTerrainPossible()) {
                r.violations.merge(Violation.GLACIAL_IMPOSSIBLE, 1, Integer::sum);
            }

            // sub-biome
            SubBiome sb = col.subBiome;
            bump(r.subBiome, sb == null ? "?" : sb.name());
            if (forbiddenSubBiome(facts, sb)) {
                r.violations.merge(Violation.FORBIDDEN_SUBBIOME, 1, Integer::sum);
                badSub = true;
            }

            // material role / surface category
            MaterialRole role = col.materialRole;
            bump(r.materialRole, role == null ? "?" : role.name());
            bump(r.surfaceCategory, col.surfaceCategory == null ? "?" : col.surfaceCategory.name());

            // the effective surface material of the column, mirroring the chunk generator
            int zone = PlanetMaterialRoleSelector.zoneAt(theme, col.provinceConfidence,
                    col.surfaceCategory, materialSeed, x, z, 0.0);
            MaterialRole effective = switch (zone) {
                case PlanetMaterialRoleSelector.SECONDARY -> MaterialRole.SECONDARY_SURFACE;
                case PlanetMaterialRoleSelector.GEOLOGIC -> MaterialRole.MOUNTAIN;
                case PlanetMaterialRoleSelector.ACCENT -> MaterialRole.ACCENT;
                default -> MaterialRole.PRIMARY_SURFACE;
            };
            PlanetMaterial m = profile.geology().palette().materialFor(effective);
            if (m != null) {
                bump(r.blockId, m.blockId());
                if (effective == MaterialRole.PRIMARY_SURFACE) {
                    judgePrimary(r, facts, m.family());
                }
            }
        }
        if (badBiome) r.planetsWithForbiddenBiome++;
        if (badSub) r.planetsWithForbiddenSubBiome++;
    }

    // =====================================================================================
    // MATERIAL FAMILY CLASSIFICATION (raw, catalogue-independent)
    // =====================================================================================

    public static boolean isCrystalline(MaterialFamily f) {
        return f != null && f.superFamily() == MaterialFamily.MaterialSuperFamily.CRYSTALLINE;
    }

    public static boolean isVolcanic(MaterialFamily f) {
        return f != null && (f == MaterialFamily.ROCK_VOLCANIC
                || f == MaterialFamily.ROCK_SULFURIC
                || f == MaterialFamily.SOIL_ASH
                || f == MaterialFamily.ROCK_BASALTIC);
    }

    public static boolean isFrozen(MaterialFamily f) {
        return f != null && f.isColdOnly();
    }

    // =====================================================================================
    // PROVINCE / BIOME / SUBBIOME FORBIDDEN SETS
    // =====================================================================================

    public static boolean forbiddenProvince(Facts f, GeologicalProvince p) {
        if (f == null || p == null) return false;
        return switch (p) {
            case VOLCANIC, GEOTHERMAL -> !f.volcanicTerrainPossible();
            case GLACIAL -> !f.glacialTerrainPossible();
            case SALT -> !f.saltPossible();
            default -> false;
        };
    }

    public static boolean forbiddenBiome(Facts f, String biomeId) {
        if (f == null || biomeId == null) return false;
        return switch (biomeId) {
            case "crystal_field" -> !f.crystalTerrainPossible();
            case "volcanic_plateau" -> !f.volcanicTerrainPossible();
            case "ice_sheet", "alpine_snow", "tundra" -> !f.glacialTerrainPossible();
            case "dune_sea", "arid_rock" -> f.band() == TemperatureBand.INFERNO;
            case "wetland", "forest" -> !f.organicPossible();
            default -> false;
        };
    }

    public static boolean glacialBiome(String biomeId) {
        return "ice_sheet".equals(biomeId) || "alpine_snow".equals(biomeId)
                || "tundra".equals(biomeId);
    }

    public static boolean forbiddenSubBiome(Facts f, SubBiome s) {
        if (f == null || s == null) return false;
        return switch (s) {
            case CRYSTAL_GARDEN -> !f.crystalTerrainPossible();
            case LAVA_FIELDS, SCORIA, THERMAL_PLAINS, GEOTHERMAL_VENTS, ASH_FLATS ->
                    !f.volcanicTerrainPossible();
            case ICE_FIELDS, FROST_CRACKS, SNOW_DRIFTS, FROZEN_VALLEY -> !f.glacialTerrainPossible();
            case GLACIAL_WETLAND -> !f.glacialTerrainPossible() || !f.liquidWaterPossible();
            case MARSH, MUDFLATS, MEADOW -> !f.organicPossible();
            case SALT_CRUST -> !f.saltPossible();
            default -> false;
        };
    }

    // =====================================================================================
    // SAMPLER CONSTRUCTION (the real production pipeline)
    // =====================================================================================

    private static V3ColumnSampler samplerFor(PlanetWorldgenProfile profile) {
        PlanetPhysicalProfile phys = profile.geology().physical();
        GeologicalProvinceMap provinces = profile.geology().provinces();
        PlanetClimateProfile climate = profile.geology().climate();
        TerrainShaper shaper = TerrainShaper.create(null, profile.planetSeed(), phys, provinces,
                profile.geology().terrainSignature(), profile.baseHeight(), profile.amplitude(),
                null, profile.geology().geography(), climate);
        if (shaper == null) return null;
        PlanetCharacter character = shaper.character();
        ClimateField climateField = new ClimateField(climate, character,
                new WindDirectionField(profile.planetSeed()));
        BiomeMaskField biomeField = new BiomeMaskField(character, climateField,
                BiomeMaskField.candidatesFor(
                        PlanetAdmissibility.of(phys, profile.properties().surface()),
                        profile.properties().surface())); 
        return new V3ColumnSampler(shaper, climateField, biomeField, provinces);
    }

    private static void bump(Map<String, Integer> m, String key) {
        m.merge(key, 1, Integer::sum);
    }
}
