package com.modscreating.unlimitedspace.core.worldgen.geography;

import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetaryEnvironment;

/**
 * WORLDGEN V2 — the macro-geography ARCHETYPE of one lattice site.
 *
 * <p>An archetype is NOT a biome, NOT a material and NOT a hard gate anywhere in the
 * pipeline. It is the <em>identity</em> half of the macro geography and, at the same time,
 * the carrier of a set of CONTINUOUS attributes that the terrain / geology / material
 * stages blend. Two archetypes are distinguished exactly by their nearest-site Voronoi
 * ownership (see {@link MacroGeography}); everything else they express is a continuous
 * field.
 *
 * <p>Immutable by construction (all fields {@code final}, enum constants).
 * Pure domain: no Minecraft types.
 */
public enum ProvinceArchetype {

    // ------------------------------------------------------------- COMMON tier
    /** Vast open country; deliberately suppresses mountains. */
    OPEN_PLAINS(Tier.COMMON, 0.00, 0.00, 0.30, 1.00, 0.25,
            0.75, 0.00, 0.00, 0.70, 1.00, 0.00, 0.00, 0.55, 0.45, 0.60, 0.35,
            env(0.50, 0.45, 0.55).water(0.35).relief(0.30).organic(0.35)),
    /** Gentle rolling country — where the "beautiful hills" live. */
    ROLLING_COUNTRY(Tier.COMMON, 0.00, 0.00, 0.75, 1.05, 0.45,
            0.90, 0.00, 0.00, 0.80, 1.00, 0.00, 0.00, 0.60, 0.50, 0.55, 0.40,
            env(0.50, 0.45, 0.55).water(0.40).relief(0.40).organic(0.40)),
    /** Highland country: hills rising toward real relief. */
    HIGHLANDS(Tier.COMMON, 0.05, -0.05, 1.10, 1.30, 0.45,
            1.15, 1.00, 0.00, 0.90, 0.95, 0.10, 0.00, 0.55, 0.50, 0.40, 0.35,
            env(0.48, 0.45, 0.50).water(0.30).relief(0.70).erosion(0.45)),
    /** Low wet ground: basins, floodplains, marshes. */
    BASIN_LOWLANDS(Tier.COMMON, 0.00, 0.10, 0.35, 0.95, 0.55,
            0.60, -1.00, 0.00, 0.55, 0.90, 0.20, 0.00, 0.75, 0.65, 0.30, 0.30,
            env(0.50, 0.60, 0.45).water(0.60).relief(0.20).organic(0.50)),
    /** Cold expanse: frozen plains / ice fields. */
    FROZEN_EXPANSE(Tier.COMMON, -0.35, 0.10, 0.70, 1.05, 0.05,
            0.95, 0.20, 0.00, 0.45, 0.75, 0.00, 0.00, 0.30, 0.55, 0.25, 0.30,
            env(0.10, 0.60, 0.50).water(0.35).relief(0.35).radiation(0.80)),
    /** Low sediment country near liquid: shores, muddy flats, deltas. */
    SEDIMENTARY_LOWLANDS(Tier.COMMON, 0.00, 0.10, 0.30, 0.90, 0.55,
            0.65, -0.70, 0.00, 0.50, 1.00, 0.35, 0.00, 0.70, 0.70, 0.25, 0.25,
            env(0.50, 0.62, 0.45).water(0.75).relief(0.18).organic(0.55)),

    // ------------------------------------------------------------- UNCOMMON tier
    /** Dry dune / badland country. */
    DRY_BADLANDS(Tier.UNCOMMON, 0.10, -0.20, 0.90, 1.10, 0.02,
            0.85, 0.10, 0.00, 1.25, 0.95, 0.60, 0.00, 0.20, 0.25, 0.60, 0.60,
            env(0.58, 0.32, 0.40).water(0.10).relief(0.55).erosion(0.60).organic(0.05)),
    /** Endless low-frequency dust ocean on arid worlds. */
    DUST_SEA(Tier.UNCOMMON, 0.10, -0.10, 0.25, 0.90, 0.02,
            0.80, -0.40, 0.00, 0.55, 1.00, 0.85, 0.00, 0.10, 0.15, 0.85, 0.55,
            env(0.58, 0.20, 0.35).water(0.05).relief(0.15).erosion(0.55).organic(0.0)),
    /** Salt pans and evaporite basins. */
    SALT_FLATS(Tier.UNCOMMON, 0.05, -0.10, 0.15, 1.00, 0.00,
            0.55, -0.30, 0.00, 0.50, 1.00, 0.45, 0.00, 0.25, 0.30, 0.70, 0.45,
            env(0.55, 0.40, 0.40).water(0.45).relief(0.08).organic(0.0)),
    /** Crystal-bearing fields. */
    CRYSTAL_FIELDS(Tier.UNCOMMON, 0.00, 0.00, 1.20, 1.15, 0.10,
            1.05, 0.40, 0.90, 0.85, 0.95, 0.00, 0.00, 0.40, 0.45, 0.40, 0.45,
            env(0.50, 0.50, 0.50).water(0.20).relief(0.45).crystal(0.75).geothermal(0.35)),
    /** Hot springs / fumarole country. */
    GEOTHERMAL_FIELDS(Tier.UNCOMMON, 0.25, 0.05, 1.30, 1.20, 0.05,
            1.20, 0.50, 0.25, 1.10, 0.95, 0.00, 0.00, 0.55, 0.60, 0.35, 0.30,
            env(0.58, 0.50, 0.50).water(0.30).relief(0.50).geothermal(0.75).volcanic(0.45)),
    /** Young volcanic terrain: cones, ash, flows. */
    VOLCANIC_FIELDS(Tier.UNCOMMON, 0.30, -0.15, 1.60, 1.25, 0.00,
            1.35, 0.70, 0.00, 1.35, 0.90, 0.00, 0.00, 0.30, 0.30, 0.50, 0.55,
            env(0.70, 0.35, 0.55).water(0.10).relief(0.65).volcanic(0.80).organic(0.0)),
    /** Ice-covered highlands: frozen ranges and glacial ridges. */
    GLACIAL_HIGHLANDS(Tier.UNCOMMON, -0.40, 0.05, 1.25, 1.35, 0.05,
            1.20, 0.30, 0.00, 0.55, 0.80, 0.05, 0.00, 0.35, 0.55, 0.30, 0.35,
            env(0.12, 0.60, 0.50).water(0.40).relief(0.75).erosion(0.35).organic(0.0)),
    /** Iron-rich oxidized wasteland on metallic worlds. */
    METALLIC_WASTES(Tier.UNCOMMON, 0.05, -0.15, 0.80, 1.05, 0.02,
            0.90, -0.10, 0.00, 0.95, 1.00, 0.20, 0.00, 0.25, 0.35, 0.60, 0.40,
            env(0.52, 0.42, 0.45).water(0.10).relief(0.45).metallic(0.80).organic(0.0)),

    // ------------------------------------------------------------- RARE tier
    /** A gigantic ancient impact basin: an event, not a biome. */
    GIANT_IMPACT_REGION(Tier.RARE, -0.05, -0.10, 0.50, 0.95, 0.05,
            0.70, -0.20, 0.00, 0.80, 1.00, 0.30, 0.00, 0.45, 0.45, 0.35, 0.20,
            env(0.50, 0.55, 0.45).water(0.25).relief(0.30).impact(0.80)),
    /** Luminous alien terrain. */
    LUMINOUS_TERRAIN(Tier.RARE, 0.05, 0.05, 0.80, 1.05, 0.10,
            0.95, 0.20, 0.70, 0.70, 0.95, 0.00, 0.00, 0.50, 0.55, 0.35, 0.40,
            env(0.50, 0.55, 0.50).water(0.20).relief(0.40).crystal(0.85).geothermal(0.45)),
    /** Bizarre exotic geography (strange macro forms). */
    EXOTIC_ANOMALY(Tier.RARE, 0.00, 0.00, 1.00, 1.25, 0.20,
            1.00, 0.00, 0.30, 0.90, 1.00, 0.10, 0.00, 0.55, 0.60, 0.45, 0.50,
            env(0.50, 0.60, 0.50).water(0.30).relief(0.55).crystal(0.55).radiation(0.85));

    /** How common an archetype is on a planet that can host it. */
    public enum Tier { COMMON, UNCOMMON, RARE }

    /**
     * The ecological envelope: an ideal + tolerance per climate driver, plus a preferred
     * intensity per continuous environment driver. See {@link #compatibility}.
     */
    public static final class Env {
        final double tempIdeal, tempSpan, humIdeal, humSpan;
        double water = 0.4, relief = 0.4, volcanic = 0.15, geothermal = 0.10,
                crystal = 0.10, metallic = 0.10, erosion = 0.30, impact = 0.08,
                organic = 0.25, radiation = 1.0;

        private Env(double tempIdeal, double humIdeal, double span) {
            this.tempIdeal = tempIdeal;
            this.humIdeal = humIdeal;
            this.tempSpan = span;
            this.humSpan = span + 0.05;
        }
        Env water(double v)     { this.water = v; return this; }
        Env relief(double v)    { this.relief = v; return this; }
        Env volcanic(double v)  { this.volcanic = v; return this; }
        Env geothermal(double v){ this.geothermal = v; return this; }
        Env crystal(double v)   { this.crystal = v; return this; }
        Env metallic(double v)  { this.metallic = v; return this; }
        Env erosion(double v)   { this.erosion = v; return this; }
        Env impact(double v)    { this.impact = v; return this; }
        Env organic(double v)   { this.organic = v; return this; }
        Env radiation(double v) { this.radiation = v; return this; }
    }

    /** Envelope builder: (temperature ideal, humidity ideal, climate tolerance span). */
    public static Env env(double tempIdeal, double humIdeal, double span) {
        return new Env(tempIdeal, humIdeal, span);
    }

    public static final ProvinceArchetype[] VALUES = values();

    private final Tier tier;
    private final Env envelope;
    private final double temperatureBias;
    private final double humidityBias;
    private final double hillMultiplier;
    private final double mountainMultiplier;
    private final double vegetationBias;
    private final double uplift;
    private final double bias;
    private final double roughness;
    private final double carve;
    private final double dune;
    private final double volcanic;
    private final double crystal;
    private final double precipitationBias;
    private final double waterAffinity;
    private final double continentalnessBias;
    private final double reliefAffinity;

    ProvinceArchetype(Tier tier, double temperatureBias, double humidityBias,
                      double hillMultiplier, double mountainMultiplier, double vegetationBias,
                      double uplift, double bias, double roughness, double carve, double dune,
                      double volcanic, double crystal, double precipitationBias,
                      double waterAffinity, double continentalnessBias, double reliefAffinity,
                      Env envelope) {
        this.tier = tier;
        this.temperatureBias = temperatureBias;
        this.humidityBias = humidityBias;
        this.hillMultiplier = hillMultiplier;
        this.mountainMultiplier = mountainMultiplier;
        this.vegetationBias = vegetationBias;
        this.uplift = uplift;
        this.bias = bias;
        this.roughness = roughness;
        this.carve = carve;
        this.dune = dune;
        this.volcanic = volcanic;
        this.crystal = crystal;
        this.precipitationBias = precipitationBias;
        this.waterAffinity = waterAffinity;
        this.continentalnessBias = continentalnessBias;
        this.reliefAffinity = reliefAffinity;
        this.envelope = envelope;
    }

    public Tier tier()                   { return tier; }
    public Env envelope()                { return envelope; }
    public double temperatureBias()      { return temperatureBias; }
    public double humidityBias()         { return humidityBias; }
    public double hillMultiplier()       { return hillMultiplier; }
    public double mountainMultiplier()   { return mountainMultiplier; }
    public double vegetationBias()       { return vegetationBias; }
    public double uplift()               { return uplift; }
    public double bias()                 { return bias; }
    public double roughness()            { return roughness; }
    public double carve()                { return carve; }
    public double dune()                 { return dune; }
    public double volcanic()             { return volcanic; }
    public double crystal()              { return crystal; }
    public double precipitationBias()    { return precipitationBias; }
    public double waterAffinity()        { return waterAffinity; }
    public double continentalnessBias()  { return continentalnessBias; }
    public double reliefAffinity()       { return reliefAffinity; }

    /** Prior weight of this archetype on a planet (before availability gating). */
    public double priorWeight() {
        return switch (tier) {
            case COMMON -> 1.0;
            case UNCOMMON -> 0.34;
            case RARE -> 0.07;
        };
    }

    /**
     * WEIGHTED COMPATIBILITY of this archetype against a planet's derived
     * {@link PlanetaryEnvironment}, in [0,1].
     *
     * <p>The score is a weighted PRODUCT of fitness terms — an archetype incompatible along
     * ONE dominant driver (a frozen expanse on an inferno world) is killed completely, while a
     * merely off-ideal archetype stays reachable. The planet therefore restricts the
     * macro-archetype SPACE before any spatial placement happens.
     */
    public double compatibility(PlanetaryEnvironment env) {
        if (env == null) return 1.0;
        Env e = envelope;
        double f = 1.0;
        f *= pow(bump(env.temperatureTendency(), e.tempIdeal, e.tempSpan), 2.4);
        f *= pow(bump(env.humidityTendency(), e.humIdeal, e.humSpan), 1.4);
        f *= pow(similar(env.surfaceWetness(), e.water), 1.1);
        f *= pow(similar(env.reliefRegime(), e.relief), 1.0);
        f *= pow(similar(env.volcanicIntensity(), e.volcanic), 0.9);
        f *= pow(similar(env.geothermalIntensity(), e.geothermal), 0.5);
        f *= pow(similar(env.crystalIntensity(), e.crystal), 0.8);
        f *= pow(similar(env.metallicIntensity(), e.metallic), 0.5);
        f *= pow(similar(env.erosionIntensity(), e.erosion), 0.7);
        f *= pow(similar(env.impactIntensity(), e.impact), 0.4);
        f *= pow(similar(env.organicIntensity(), e.organic), 0.5);
        f *= pow(clamp01(1.0 - Math.min(1.0,
                Math.max(0.0, env.radiationIntensity() - e.radiation) / 0.5)), 0.8);
        if (e.water > 0.45 && env.pressure01() < 0.12) f *= 0.15;
        f *= pow(bump(env.age01(), 0.5, 0.75), 0.25);
        return clamp01(f);
    }

    /** Triangular fitness: 1 at the ideal, 0 beyond the tolerance span. */
    private static double bump(double v, double ideal, double span) {
        if (span <= 0.0) return v == ideal ? 1.0 : 0.0;
        return clamp01(1.0 - Math.abs(v - ideal) / span);
    }

    /** Affinity similarity: 1 when the environment matches the archetype's preference. */
    private static double similar(double envValue, double preferred) {
        return clamp01(1.0 - Math.abs(envValue - preferred) * 1.6);
    }

    private static double pow(double v, double w) {
        return v <= 0.0 ? 0.0 : Math.pow(v, w);
    }

    static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
}

