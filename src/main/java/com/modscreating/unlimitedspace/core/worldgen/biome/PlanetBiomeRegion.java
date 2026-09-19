package com.modscreating.unlimitedspace.core.worldgen.biome;

/**
 * LARGE biome region type (R21 planetary-geography stage).
 *
 * <pre>
 * PLANET -&gt; CLIMATE -&gt; RELIEF -&gt; BIOME REGION (this) -&gt; TRANSITION -&gt; LOCAL LANDFORMS -&gt; MATERIALS
 * </pre>
 *
 * <p>A region is a MACRO-SCALE ecological/geographical identity spanning 500–3000+ blocks.
 * It does NOT hardcode terrain: it carries MODIFIERS (climate bias, mountain-coverage
 * multiplier, vegetation and material preferences) on top of the planet's relief and geology.
 * Provinces and regions CROSS-COMBINE (a FROZEN_EXPANSE region on a VOLCANIC province reads
 * as frozen volcanic terrain).
 *
 * <p>Commonness tier keeps biomes sparse: COMMON regions dominate, UNCOMMON are regional
 * specialities, RARE regions must genuinely feel like events.
 *
 * <p>Pure domain: no Minecraft types.
 */
public enum PlanetBiomeRegion {

    // ------------------------------------------------------------- COMMON
    /** Vast open country; deliberately suppresses mountains. */
    OPEN_PLAINS(Tier.COMMON, 0.00, 0.00, 0.30, 1.00, 0.25),
    /** Gentle rolling country — where the "beautiful hills" live. */
    ROLLING_COUNTRY(Tier.COMMON, 0.00, 0.00, 0.75, 1.05, 0.45),
    /** Highland country: hills rising toward real relief. */
    HIGHLANDS(Tier.COMMON, 0.05, -0.05, 1.10, 1.30, 0.45),
    /** Low wet ground: basins, floodplains, marshes. */
    BASIN_LOWLANDS(Tier.COMMON, 0.00, 0.10, 0.35, 0.95, 0.55),
    /** Cold expance: frozen plains / ice fields. */
    FROZEN_EXPANSE(Tier.COMMON, -0.35, 0.10, 0.70, 1.05, 0.05),

    // ------------------------------------------------------------- UNCOMMON
    /** Dry dune / badland country. */
    DRY_MARSHLESS_BADLANDS(Tier.UNCOMMON, 0.10, -0.20, 0.90, 1.10, 0.02),
    /** Salt pans and evaporite basins. */
    SALT_FLATS(Tier.UNCOMMON, 0.05, -0.10, 0.15, 1.00, 0.00),
    /** Crystal-bearing fields. */
    CRYSTAL_FIELDS(Tier.UNCOMMON, 0.00, 0.00, 1.20, 1.15, 0.10),
    /** Hot springs / fumarole country. */
    GEOTHERMAL_FIELDS(Tier.UNCOMMON, 0.25, 0.05, 1.30, 1.20, 0.05),
    /** Young volcanic terrain: cones, ash, flows. */
    VOLCANIC_FIELDS(Tier.UNCOMMON, 0.30, -0.15, 1.60, 1.25, 0.00),

    // ------------------------------------------------------------- RARE
    /** A gigantic ancient impact basin: an event, not a biome. */
    GIANT_IMPACT_REGION(Tier.RARE, -0.05, -0.10, 0.50, 0.95, 0.05),
    /** Luminous alien terrain. */
    LUMINOUS_TERRAIN(Tier.RARE, 0.05, 0.05, 0.80, 1.05, 0.10),
    /** Bizarre exotic geography (strange macro forms). */
    EXOTIC_ANOMALY(Tier.RARE, 0.00, 0.00, 1.00, 1.25, 0.20);

    public static final PlanetBiomeRegion[] VALUES = values();

    /** How common a region type is on a planet that can host it. */
    public enum Tier { COMMON, UNCOMMON, RARE }

    private final Tier tier;
    private final double temperatureBias;   // additive shift of the local climate, in [−1,1]
    private final double humidityBias;
    private final double hillMultiplier;    // local hills/relief multiplier
    private final double mountainMultiplier;// local mountain-coverage multiplier
    private final double vegetationBias;    // additive vegetation preference in [0,1]

    PlanetBiomeRegion(Tier tier, double temperatureBias, double humidityBias,
                      double hillMultiplier, double mountainMultiplier, double vegetationBias) {
        this.tier = tier;
        this.temperatureBias = temperatureBias;
        this.humidityBias = humidityBias;
        this.hillMultiplier = hillMultiplier;
        this.mountainMultiplier = mountainMultiplier;
        this.vegetationBias = vegetationBias;
    }

    public Tier tier()                 { return tier; }
    public double temperatureBias()    { return temperatureBias; }
    public double humidityBias()       { return humidityBias; }
    public double hillMultiplier()     { return hillMultiplier; }
    public double mountainMultiplier() { return mountainMultiplier; }
    public double vegetationBias()     { return vegetationBias; }

    /** Prior weight of this region type on a planet (before availability gating). */
    public double priorWeight() {
        return switch (tier) {
            case COMMON -> 1.0;
            case UNCOMMON -> 0.34;
            case RARE -> 0.07;
        };
    }
}
