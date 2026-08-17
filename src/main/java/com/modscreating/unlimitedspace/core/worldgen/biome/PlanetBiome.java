package com.modscreating.unlimitedspace.core.worldgen.biome;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;

/**
 * Deterministic, climate-aware biome presetc catalogue (R8).
 *
 * <p>Each preset carries the climate/material constraints it is compatible
 * with, so a planet can deterministically select 1..5 distinct presets whose
 * collective climate matches the planet properties. Selection is driven by
 * {@code PlanetSeed} + {@link PlanetBiomeProfile}, never by display name.
 *
 * <p>No Minecraft types are referenced here; the Minecraft-side adapter maps
 * each {@code PlanetBiome} to a concrete {@code Holder<Biome>}.
 */
public enum PlanetBiome {

    // ---- cold end ----
    FROZEN_DESERT(-30, 10, 0.10, 0.40, true, false, PlanetSurface.SOLID_ICE, "frozen_desert"),
    FROZEN_PLAINS(-40, 20, 0.20, 0.60, true, false, PlanetSurface.SOLID_ICE, "frozen_plains"),
    SNOWY_HIGHLANDS(-45, 15, 0.25, 0.70, true, false, PlanetSurface.SOLID_ICE, "snowy_highlands"),
    COLD_ROCKY_PLAINS(-130, 30, 0.05, 0.65, false, false, PlanetSurface.SOLID_ROCKY, "cold_rocky"),
    FROZEN_CRACKS(-50, 10, 0.05, 0.30, true, false, PlanetSurface.SOLID_ICE, "frozen_cracks"),
    ICE_FIELDS(-40, 10, 0.30, 0.60, true, false, PlanetSurface.SOLID_ICE, "ice_fields"),

    // ---- temperate ----
    ROCKY_PLAINS(20, 40, 0.30, 0.50, false, false, PlanetSurface.SOLID_ROCKY, "rocky_plains"),
    STONE_RAVINES(15, 45, 0.20, 0.40, false, false, PlanetSurface.SOLID_ROCKY, "stone_ravines"),
    COASTAL_PLAINS(10, 50, 0.60, 0.70, false, true, PlanetSurface.OCEANIC, "coastal_plains"),
    MUDGY_BASIN(5, 55, 0.70, 0.80, false, true, PlanetSurface.SOLID_ROCKY, "muddy_basin"),
    DRY_PLATEAU(25, 30, 0.10, 0.30, false, false, PlanetSurface.SOLID_DESERT, "dry_plateau"),
    RED_SAND_VALLEY(25, 30, 0.15, 0.35, false, false, PlanetSurface.SOLID_DESERT, "red_sand"),
    DEAD_FOREST(15, 40, 0.40, 0.55, false, true, PlanetSurface.SOLID_ROCKY, "dead_forest"),
    SALT_FLATS(10, 20, 0.05, 0.20, false, false, PlanetSurface.SOLID_DESERT, "salt_flats"),
    WARM_WET(25, 60, 0.80, 0.90, false, true, PlanetSurface.OCEANIC, "warm_wet"),
    CRYSTAL_FIELDS(20, 50, 0.50, 0.60, false, false, PlanetSurface.SOLID_ROCKY, "crystal"),

    // ---- hot / dry ----
    HOT_DESERT(15, 90, 0.05, 0.25, false, false, PlanetSurface.SOLID_DESERT, "hot_desert"),
    WASTELAND(20, 90, 0.08, 0.30, false, false, PlanetSurface.SOLID_DESERT, "wasteland"),
    RED_HIGHLANDS(25, 50, 0.15, 0.40, false, false, PlanetSurface.SOLID_DESERT, "red_highlands"),
    HOT_ROCKY(70, 90, 0.05, 0.30, false, false, PlanetSurface.SOLID_ROCKY, "hot_rocky"),
    ASH_FIELDS(126, 627, 0.01, 1.00, true, false, PlanetSurface.SOLID_VOLCANIC, "ash_fields"),
    VOLCANIC_FIELDS(126, 627, 0.05, 0.25, false, false, PlanetSurface.SOLID_VOLCANIC, "volcanic"),

    // ---- water ----
    SHALLOW_OCEAN(15, 80, 0.90, 0.95, true, false, PlanetSurface.OCEANIC, "shallow_ocean"),
    DEEP_OCEAN(10, 90, 0.95, 1.00, true, false, PlanetSurface.OCEANIC, "deep_ocean"),
    TOXIC_WASTELAND(40, 70, 0.80, 0.90, true, true, PlanetSurface.SOLID_ROCKY, "toxic"),
    BASALT_VALLEY(126, 627, 0.01, 1.00, false, false, PlanetSurface.SOLID_VOLCANIC, "basalt_valley"),
    PLATEAU(25, 30, 0.30, 0.45, false, false, PlanetSurface.SOLID_ROCKY, "plateau"),

    // ---- legacy coarse archetypes (kept as aliases for R7-era call-sites) ----
    // NOTE: WARM_WET already declared above in the climate-aware catalogue; it is
    // reused here as the legacy "warm wet" archetype (minecraft:forest alias).
    OCEAN(10, 80, 0.60, 0.95, true, false, PlanetSurface.OCEANIC, "minecraft:deep_ocean"),
    HOT_DRY(25, 55, 0.05, 0.30, false, false, PlanetSurface.SOLID_DESERT, "minecraft:desert"),
    COLD_DRY(-30, 30, 0.05, 0.40, false, false, PlanetSurface.SOLID_ROCKY, "minecraft:snowy_tundra"),

    // ---- fail-safe universal (matches any climate, last resort only) ----
    SURFACE_GENERIC(-1000, 1000, 0.0, 1.0, false, false, PlanetSurface.SOLID_ROCKY, "generic_surface"),

    // ---- gas giants are not solid-surface biomes ----
    GAS_GIANT(-1000, 1000, 0.0, 1.0, false, false, PlanetSurface.GASEOUS, "gas_giant");

    private static final PlanetBiome[] VALUES = values();

    private final int minTemp;
    private final int maxTemp;
    private final double minHumidity;
    private final double maxHumidity;
    private final boolean prefersWater;
    private final boolean allowsWater;
    private final PlanetSurface requiredSurface;
    private final String minecraftAlias;

    PlanetBiome(int minTemp, int maxTemp, double minHumidity, double maxHumidity,
                boolean prefersWater, boolean allowsWater,
                PlanetSurface requiredSurface, String minecraftAlias) {
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.prefersWater = prefersWater;
        this.allowsWater = allowsWater;
        this.requiredSurface = requiredSurface;
        this.minecraftAlias = minecraftAlias;
    }

    public int minTemperature() { return minTemp; }

    public int maxTemperature() { return maxTemp; }

    public double minHumidity() { return minHumidity; }

    public double maxHumidity() { return maxHumidity; }

    public boolean prefersWater() { return prefersWater; }

    public boolean allowsWater() { return allowsWater; }

    public PlanetSurface requiredSurface() { return requiredSurface; }

    public String minecraftAlias() { return minecraftAlias; }

    /** True if this preset's climate window overlaps the planet's actual climate. */
    public boolean climateMatches(double planetTemp, double planetHumidity, boolean hasWater) {
        if (planetTemp < minTemp || planetTemp > maxTemp) return false;
        if (planetHumidity < minHumidity || planetHumidity > maxHumidity) return false;
        if (hasWater && requiredSurface == PlanetSurface.SOLID_DESERT) return false;
        return true;
    }

    /** Restrict presets to those valid for a given planet surface category. */
    public static PlanetBiome[] forSurface(PlanetSurface surface) {
        return java.util.Arrays.stream(VALUES)
                .filter(b -> b.requiredSurface == surface || b.requiredSurface == PlanetSurface.GASEOUS)
                .toArray(PlanetBiome[]::new);
    }

    public static PlanetBiome[] allSolid() {
        return java.util.Arrays.stream(VALUES)
                .filter(b -> b.requiredSurface != PlanetSurface.GASEOUS)
                .toArray(PlanetBiome[]::new);
    }

    public static PlanetBiome[] valuesOf() { return VALUES; }
}


