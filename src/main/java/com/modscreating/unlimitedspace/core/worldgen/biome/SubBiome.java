package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.admissibility.PlanetAdmissibility;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.geography.MacroSample;
import com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvinceContext;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — the LOCAL ecology inside a macro geography.
 *
 * <pre>
 * MACRO GEOGRAPHY (~2400 blocks, MacroGeography)
 *   -> SUB-BIOME (~600 blocks, this)  the local ecological texture
 * </pre>
 *
 * <p>A sub-biome is NOT a registered Minecraft biome and NOT a colour choice: it is a
 * core-domain label derived from the CONTINUOUS climate channels, the local elevation, the
 * wetness, the geology and a soft macro-province affinity.
 *
 * <p>WORLDGEN V2 removed the old hierarchy rules: there is no climate BUCKETING, no CORE
 * allow-list, and no hash tie-break strong enough to overturn a compatibility score. The macro
 * province is an AFFINITY here, never a hard gate; the only hard gate is the physical water
 * phase.
 *
 * <p>Pure domain, deterministic, allocation-free.
 */
public enum SubBiome {

    /** Lush open grass / meadow country. */
    MEADOW(0.50, 0.65, 0.25, 0.55, 0.0, MaterialRole.SOIL),
    /** Dry grass / steppe. */
    DRY_GRASSLAND(0.52, 0.35, 0.30, 0.25, 0.0, MaterialRole.SEDIMENT),
    /** Bare gravel and rock rubble flats. */
    GRAVEL_FLATS(0.50, 0.40, 0.45, 0.15, 0.0, MaterialRole.MOUNTAIN),
    /** Wind-scoured dust barrens. */
    DUST_BARRENS(0.58, 0.15, 0.20, 0.05, 0.0, MaterialRole.SEDIMENT),
    /** Evaporite salt crust. */
    SALT_CRUST(0.55, 0.25, 0.08, 0.20, 0.0, MaterialRole.SEDIMENT),
    /** Solid ice fields. */
    ICE_FIELDS(0.06, 0.45, 0.25, 0.45, 0.0, null),
    /** Cracked, frost-shattered ground. */
    FROST_CRACKS(0.10, 0.40, 0.40, 0.30, 0.0, null),
    /** Deep snow drifts. */
    SNOW_DRIFTS(0.08, 0.60, 0.15, 0.50, 0.0, null),
    /** Loose scree slopes below high ground. */
    SCREE_SLOPES(0.45, 0.35, 0.80, 0.15, 0.0, MaterialRole.MOUNTAIN),
    /** Ash / cinder flats around thermal country. */
    ASH_FLATS(0.62, 0.25, 0.35, 0.10, 0.45, null),
    /** Fumarole / hot-spring ground. */
    GEOTHERMAL_VENTS(0.58, 0.50, 0.40, 0.25, 0.80, MaterialRole.GEOTHERMAL),
    /** Crystal-bearing ground. */
    CRYSTAL_GARDEN(0.45, 0.40, 0.35, 0.20, 0.30, MaterialRole.CRYSTAL),
    /** Waterlogged mud and tidal flats. */
    MUDFLATS(0.50, 0.75, 0.06, 0.85, 0.0, MaterialRole.SOIL),
    /** Recently flowed volcanic ground. */
    LAVA_FIELDS(0.70, 0.20, 0.50, 0.05, 0.70, null),
    /** Ice-carved valley floor inside a frozen macro province. */
    FROZEN_VALLEY(0.10, 0.55, 0.35, 0.45, 0.0, null),
    /** Wet ground near (brine) water inside a glacial macro province. */
    GLACIAL_WETLAND(0.08, 0.70, 0.10, 0.85, 0.0, MaterialRole.SOIL),
    /** A coherent dune field: large, wave-like granular bodies. */
    DUNE_SEA(0.58, 0.12, 0.30, 0.05, 0.0, MaterialRole.SEDIMENT),
    /** Bare arid rock country (badland shoulders, desert pavement). */
    ROCKY_ARID(0.55, 0.20, 0.60, 0.10, 0.0, MaterialRole.MOUNTAIN),
    /** Organic waterlogged ground on a temperate/wet world. */
    MARSH(0.48, 0.85, 0.05, 0.90, 0.0, MaterialRole.SOIL),
    /** Volcanic cinder / scoria country. */
    SCORIA(0.66, 0.25, 0.55, 0.10, 0.60, null),
    /** Warm altered plains around thermal activity. */
    THERMAL_PLAINS(0.60, 0.45, 0.30, 0.25, 0.70, MaterialRole.GEOTHERMAL);

    public static final SubBiome[] VALUES = values();

    private final double tempIdeal;
    private final double humIdeal;
    private final double elevationIdeal;
    private final double wetnessIdeal;
    private final double thermalIdeal;
    /** V3.1: the ideal LOCAL SLOPE for this ecology (steep rock vs flat ground). */
    private final double slopeIdeal;
    /** V3.1: the ideal RIVER PROXIMITY (a riparian ecology wants to be near a channel). */
    private final double riverIdeal;
    /** V3.1: the ideal GLACIAL intensity of the column. */
    private final double glacialIdeal;
    /** V3.1: the ideal HIGH-RELIEF intensity of the column. */
    private final double mountainIdeal;
    /** The palette role this ecology prefers (null = the theme decides). */
    private final MaterialRole preferredRole;

    /**
     * The V2 six-parameter form. The four V3.1 channels are DERIVED from the existing envelope, so
     * every legacy constant keeps a coherent meaning: a high, cold, dry ecology is also expected
     * to sit on steeper, higher, glacier-carved ground, while a wet lowland ecology is flat,
     * level and unglaciated.
     */
    SubBiome(double tempIdeal, double humIdeal, double elevationIdeal,
             double wetnessIdeal, double thermalIdeal, MaterialRole preferredRole) {
        this(tempIdeal, humIdeal, elevationIdeal, wetnessIdeal, thermalIdeal,
                slopeFrom(elevationIdeal), riverFrom(wetnessIdeal),
                glacialFrom(tempIdeal), mountainFrom(elevationIdeal),
                preferredRole);
    }

    /** The V3.1 ten-parameter form: the full local envelope, stated explicitly. */
    SubBiome(double tempIdeal, double humIdeal, double elevationIdeal,
             double wetnessIdeal, double thermalIdeal, double slopeIdeal, double riverIdeal,
             double glacialIdeal, double mountainIdeal, MaterialRole preferredRole) {
        this.tempIdeal = tempIdeal;
        this.humIdeal = humIdeal;
        this.elevationIdeal = elevationIdeal;
        this.wetnessIdeal = wetnessIdeal;
        this.thermalIdeal = thermalIdeal;
        this.slopeIdeal = slopeIdeal;
        this.riverIdeal = riverIdeal;
        this.glacialIdeal = glacialIdeal;
        this.mountainIdeal = mountainIdeal;
        this.preferredRole = preferredRole;
    }

    private static double slopeFrom(double elevationIdeal) {
        return clamp01(0.15 + 0.65 * elevationIdeal);
    }

    private static double riverFrom(double wetnessIdeal) {
        return clamp01(0.25 + 0.70 * wetnessIdeal);
    }

    private static double glacialFrom(double tempIdeal) {
        return clamp01(0.75 - 1.0 * tempIdeal);
    }

    private static double mountainFrom(double elevationIdeal) {
        return clamp01(elevationIdeal);
    }

    /** The palette role this ecology prefers (null = the theme decides). */
    public MaterialRole preferredRole() {
        return preferredRole;
    }

    /** Sub-biome cell scale in blocks: the local ecological patch layout. */
    public static final int CELL_SIZE = 600;
    private static final String NS = "us.biome.sub";

    /**
     * V3.2: the PLANET-level admissibility of an ecology, in addition to the LOCAL physical gate.
     *
     * <p>This is a TYPE filter, not a spatial one: when it says no, the ecology is not a candidate
     * at all, so the continuous scorer below never scores it and no border can be produced. When
     * it says yes, the local continuous fields still decide whether it actually happens HERE, and
     * {@link #selectV3} / {@link #select} keep applying their own physical conditions.
     *
     * @param adm the planet's admissibility (null = {@link PlanetAdmissibility#PERMISSIVE})
     */
    public static boolean admissible(SubBiome s, PlanetAdmissibility adm) {
        if (s == null) return false;
        PlanetAdmissibility a = adm == null ? PlanetAdmissibility.PERMISSIVE : adm;
        return switch (s) {
            // Crystal-bearing ground needs crystalline geology on this planet.
            case CRYSTAL_GARDEN -> a.crystalFieldsPossible();
            // Volcanic ground needs a volcanic / geothermal drive. Note this is TERRAIN, not lava:
            // the exposed-lava decision stays with LavaEligibility.
            case LAVA_FIELDS, SCORIA, THERMAL_PLAINS, GEOTHERMAL_VENTS, ASH_FLATS ->
                    a.volcanicTerrainPossible();
            // Frozen ground needs a genuinely frozen surface.
            case ICE_FIELDS, FROST_CRACKS, SNOW_DRIFTS, FROZEN_VALLEY -> a.glacialTerrainPossible();
            case GLACIAL_WETLAND -> a.glacialTerrainPossible() && a.liquidWaterPossible();
            // Organic ground needs the life-support window AND real liquid water.
            case MEADOW, MARSH, MUDFLATS -> a.organicPossible() && a.liquidWaterPossible();
            // Evaporite crust needs a real water history.
            case SALT_CRUST -> a.saltPossible();
            // Aeolian ground needs a non-frozen, non-vacuum surface (dunes are a DRY landform).
            case DUNE_SEA, DUST_BARRENS -> a.dunesPossible();
            default -> true;
        };
    }

    /**
     * WORLDGEN V3.1 — the sub-biome of a REAL, fully sampled V3 column.
     *
     * <p>This is the ONLY path the runtime uses. It reads the same {@link WorldgenColumnSample}
     * the biome classifier just wrote, so the ecology can never disagree with the biome it sits
     * inside. The removed hidden dependencies are all gone by construction:
     *
     * <ul>
     *   <li>no province ID — the geology enters only as CONTINUOUS intensities;</li>
     *   <li>no old region classification — the macro geography is not consulted at all;</li>
     *   <li>no random {@code hasRivers} — the REAL river and lake masks are read;</li>
     *   <li>no climate bucket switch and no one-time planetary selection — the score is a
     *       continuous product over the sample's channels.</li>
     * </ul>
     *
     * <p>Deterministic: a pure function of the sample. Allocation-free.
     */
    public static SubBiome selectV3(WorldgenColumnSample c) {
        return selectV3(c, null);
    }

    /**
     * V3.2: {@link #selectV3(WorldgenColumnSample)} restricted by the PLANET's admissibility.
     *
     * <p>The admissibility check is a pre-filter on the candidate set, so the local physical gates
     * and the continuous score below are untouched: a planet-admissible ecology is still decided
     * purely by this column's real fields, and an inadmissible one is simply not scored.
     */
    public static SubBiome selectV3(WorldgenColumnSample c, PlanetAdmissibility adm) {
        if (c == null) return MEADOW;
        double t = clamp01(c.temperature01);
        double h = clamp01(c.humidity01);
        double e = clamp01(c.elevation01);
        double w = clamp01(c.wetness01);
        double slope = clamp01(c.slope);
        // The REAL hydrology of this column, not a lottery.
        double river = clamp01(c.riverProximity);
        double lake = clamp01(c.lakeMask);
        double thermal = clamp01(c.volcanicIntensity + c.lavaEligibility * 0.5);
        double glacial = clamp01(c.glacialIntensity);
        double mountain = clamp01(c.mountainIntensity + c.mountainEnvelope * 0.5);
        double crystal = clamp01(c.crystalIntensity);
        boolean liquid = c.waterProximity > 0.02;

        SubBiome best = MEADOW;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SubBiome s : VALUES) {
            // V3.2: the PLANET-level type filter runs first, then the LOCAL physical gates.
            if (adm != null && !admissible(s, adm)) continue;
            if (liquidRequired(s) && !liquid) continue;
            if (s == GEOTHERMAL_VENTS && thermal < 0.55) continue;
            if (s == LAVA_FIELDS && thermal < 0.65) continue;
            if (s == CRYSTAL_GARDEN && crystal < 0.35) continue;
            if (s == SCREE_SLOPES && mountain < 0.35) continue;
            if (s == FROZEN_VALLEY && glacial < 0.30) continue;

            double score = bump(t, s.tempIdeal, 0.55)
                    * bump(h, s.humIdeal, 0.60)
                    * bump(e, s.elevationIdeal, 0.75)
                    * bump(w, s.wetnessIdeal, 0.65)
                    * bump(slope, s.slopeIdeal, 0.70)
                    * bump(river, s.riverIdeal, 0.60)
                    * bump(thermal, s.thermalIdeal, 0.45)
                    * bump(glacial, s.glacialIdeal, 0.50)
                    * bump(mountain, s.mountainIdeal, 0.55);
            if (s.thermalIdeal > 0.0 && thermal < 0.30) score *= 0.10;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** True for the ecologies whose semantics genuinely require standing liquid water. */
    private static boolean liquidRequired(SubBiome s) {
        return s == MARSH || s == MUDFLATS || s == GLACIAL_WETLAND;
    }

    /**
     * WORLDGEN V2 CANONICAL SELECTION — a RULE TABLE over CONTINUOUS channels.
     *
     * <p>Kept for the V2 preview and the legacy call sites. The runtime uses
     * {@link #selectV3(WorldgenColumnSample)} instead, which reads real V3 fields.
     *
     * <p>What this replaced, and why it was wrong: hard climate BUCKETS (quantisation made the
     * elected label flip inside one 600-block cell), a CORE allow-LIST (that turned the macro
     * LABEL into a hard gate on the ecology — the "province identity as a hard gate" failure
     * mode), and a hash tie-break strong enough to overturn an incompatible candidate.
     *
     * <p>What replaces them: every candidate is scored against the SAME continuous climate and
     * landform channels, and the macro province contributes only a soft AFFINITY multiplier.
     *
     * <p>Physical gates are allowed and are the only gates allowed: {@code MARSH} /
     * {@code MUDFLATS} genuinely require a liquid water phase. That is physics, not a region ID.
     */
    public static SubBiome select(long seed, int x, int z, MacroSample macro,
                                  double climateT01, double humidity01,
                                  double elevation01, double wetness01,
                                  PlanetaryEnvironment env,
                                  GeologicalProvinceContext geology,
                                  WaterPhaseModel.Phase phase) {
        // ---- 1. CONTINUOUS channels. Nothing is quantised.
        double t = clamp01(climateT01);
        double h = clamp01(humidity01);
        double w = clamp01(wetness01);
        double e = clamp01(elevation01);
        double thermal = env == null ? 0.0 : env.geothermalIntensity();
        // ---- 2. The macro province contributes a soft AFFINITY, never a gate.
        double provinceAffinity = 1.0;
        if (macro != null) {
            t = clamp01(t + macro.temperatureBias * 0.30);
            h = clamp01(h + macro.humidityBias * 0.25);
            provinceAffinity = 1.0 + 0.20 * (macro.coreShare - 0.5);
        }
        // ---- 3. The deterministic spatial patch layout (~600 blocks).
        long cellKey = Seeds.derive(seed, NS + ".cell",
                Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE));
        // ---- 4. Physical gate: liquid-dependent ecologies need a liquid-capable phase.
        boolean liquidDenied = w <= 0.02 || phaseDisallowsLiquid(phase);

        SubBiome best = VALUES[0];
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SubBiome s : VALUES) {
            if (liquidDenied && isLiquidDependent(s)) continue;
            if (frozenPhaseDenied(s, phase)) continue;
            double score = bump(t, s.tempIdeal, 0.55)
                    * bump(h, s.humIdeal, 0.60)
                    * bump(e, s.elevationIdeal, 0.75)
                    * bump(w, s.wetnessIdeal, 0.65);
            if (s.thermalIdeal > 0.0) {
                score *= bump(thermal, s.thermalIdeal, 0.45);
                if (thermal < 0.30) score *= 0.10;
            }
            // Soft affinities: the macro and geological provinces NUDGE the score.
            score *= provinceAffinity;
            if (geology != null && geology.province() != null) {
                score *= 0.7 + 0.6 * geology.share(geology.province());
            }
            // A weak spatial discriminator applied AFTER compatibility, so it can only separate
            // candidates that are already compatible. Bounded to +/-6% and a pure function of
            // the patch cell, so it can never invent a border.
            score *= 0.94 + 0.12 * Seeds.fraction(
                    Seeds.derive(cellKey, NS + ".tie", s.ordinal()), 3L);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Convenience overload for callers without a geological context. */
    public static SubBiome select(long seed, int x, int z, MacroSample macro,
                                  double climateT01, double humidity01,
                                  double elevation01, double wetness01,
                                  PlanetaryEnvironment env,
                                  WaterPhaseModel.Phase phase) {
        return select(seed, x, z, macro, climateT01, humidity01, elevation01, wetness01,
                env, null, phase);
    }

    /** Ecologies whose semantics require standing liquid water. */
    private static boolean isLiquidDependent(SubBiome s) {
        return s == MARSH || s == MUDFLATS;
    }

    /** True when the water phase forbids ordinary liquid surface water. */
    private static boolean phaseDisallowsLiquid(WaterPhaseModel.Phase phase) {
        if (phase == null) return false;
        return switch (phase) {
            case LIQUID, MIXED -> false;
            case SOLID, VAPOR, NONE -> true;
        };
    }

    /**
     * The ONE explicitly defined frozen ecology. On a VAPOR / NONE world there is no brine at
     * all, so the glacial wetland cannot exist. This is a statement about the FLUID, not about a
     * geometric region ID.
     */
    private static boolean frozenPhaseDenied(SubBiome s, WaterPhaseModel.Phase phase) {
        if (s != GLACIAL_WETLAND || phase == null) return false;
        return phase == WaterPhaseModel.Phase.VAPOR || phase == WaterPhaseModel.Phase.NONE;
    }

    /** Triangular fitness: 1 at the ideal, 0 beyond the tolerance span. */
    private static double bump(double v, double ideal, double span) {
        double d = Math.abs(v - ideal);
        return d >= span ? 0.0 : 1.0 - d / span;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}