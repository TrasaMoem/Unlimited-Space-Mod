package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;

/**
 * Deterministic material selection: pure function of
 * (materialSeed, biome, x, z) -> {@link PlanetMaterialPalette}.
 *
 * <p>No display name, no {@code new Random()}, no mutable state: the whole result
 * derives from the planet's {@code materialSeed} and the co-ordinates via
 * {@link Seeds}, so it is stable across restarts and generation order.
 */
public final class PlanetMaterialSelector {

    private static final String NS = "us.materials";
    private static final long BIOME_SLOT = 41001L;

    private PlanetMaterialSelector() {}

    /**
     * Seed-driven palette by surface category. The planet's {@code materialSeed} selects
     * the FAMILY; future phases can branch inside a family for finer variation. Right now
     * this gives each planet type a consistent, distinct block family.
     */
    public static PlanetMaterialPalette paletteFor(PlanetSurface surface, long materialSeed) {
        return switch (surface) {
            case SOLID_ROCKY -> PlanetMaterialPalette.rocky();
            case SOLID_ICE -> PlanetMaterialPalette.icy();
            case SOLID_DESERT -> PlanetMaterialPalette.desert();
            case SOLID_VOLCANIC -> PlanetMaterialPalette.volcanic();
            case OCEANIC -> PlanetMaterialPalette.oceanic();
            case GASEOUS -> PlanetMaterialPalette.metallic();
        };
    }

    /** Choose between the reusable palettes deterministically from the seed. */
    public static PlanetMaterialPalette palette(long materialSeed) {
        double f = Seeds.fraction(materialSeed, BIOME_SLOT);
        return f < 0.5 ? PlanetMaterialPalette.rocky() : PlanetMaterialPalette.icy();
    }

    /** Optional biome influence: keep selection consistent per palette. */
    public static PlanetMaterialPalette palette(long materialSeed, PlanetBiome biome) {
        // For the POC the biome only slightly offsets the seed mix, keeping the
        // material selection deterministic and not keyed by a display name.
        return palette(Seeds.derive(materialSeed, "biome." + biome.name(), biome.ordinal()));
    }

    /** Full deterministic pipeline for a column. */
    public static PlanetMaterialPalette select(long materialSeed, long biomeSeed, int x, int z) {
        PlanetBiome biome = com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeSelector.select(biomeSeed, x, z);
        return palette(materialSeed, biome);
    }

    /** Deterministic per-cell aux (future ores/structures can reuse it). */
    public static long cellSeed(long materialSeed, int cx, int cz) {
        return Seeds.derive(materialSeed, NS, cx, cz);
    }
}