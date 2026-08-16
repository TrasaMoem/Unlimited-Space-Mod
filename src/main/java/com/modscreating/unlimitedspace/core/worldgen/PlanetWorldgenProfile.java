package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialPalette;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialSelector;

/**
 * Pure-domain worldgen profile derived from {@link PlanetProperties}. It is a
 * mapping object that abstracts the shape/structure of a world WITHOUT any
 * Minecraft types: no {@code BlockState}, no {@code ResourceLocation} of blocks, no
 * {@code Level}, no {@code Biome}. Minecraft-specific material mapping happens in
 * the adapter layer.
 *
 * <p>R8 additions over R7:
 * <ul>
 *   <li>{@link #terrainPattern} — deterministic terrain SHAPE (flat/hills/mountains/cratered/...),
 *       selected from the planet terrain seed ({@link TerrainPattern}).</li>
 *   <li>{@link #materialPalette} — deterministic block family chosen by surface category via
 *       {@link PlanetMaterialSelector}, so each planet gets a distinct block palette.</li>
 *   <li>{@link #seaLevel} is now driven by {@code waterCoverage} (basin flooding) instead of
 *       being pinned to the mean terrain height, so a 28%-coverage world is no longer a 50% ocean.</li>
 * </ul>
 *
 * @param planetId           owning planet (informational; stable identity)
 * @param terrainSeed        deterministic terrain subsystem seed
 * @param baseHeight         nominal ground elevation (world Y)
 * @param amplitude          terrain relief amplitude (blocks)
 * @param frequency          base terrain noise frequency
 * @param seaLevel           world Y considered "sea level" for this planet
 * @param hasWater           whether the surface hosts an open fluid body
 * @param surfaceMaterial    abstract top-layer material
 * @param subsurfaceMaterial abstract material beneath the top layer
 * @param fluid              abstract fluid profile (oceans/lakes)
 * @param terrainPattern     deterministic terrain shape pattern
 * @param materialPalette    deterministic block-family palette
 */
public record PlanetWorldgenProfile(
        PlanetId planetId,
        long terrainSeed,
        double baseHeight,
        double amplitude,
        double frequency,
        double seaLevel,
        boolean hasWater,
        SurfaceMaterial surfaceMaterial,
        SurfaceMaterial subsurfaceMaterial,
        FluidProfile fluid,
        TerrainPattern terrainPattern,
        PlanetMaterialPalette materialPalette) {

    public static PlanetWorldgenProfile from(Planet planet) {
        return from(planet.id(), planet.properties());
    }

    public static PlanetWorldgenProfile from(PlanetId planetId, PlanetProperties p) {
        // base elevation drifts around 64 by the semantic baseHeight parameter
        double baseHeight = 64.0 + p.generationParameters().baseHeight() * 24.0;
        // relief amplitude from ruggedness, softened by erosion
        double amplitude = (4.0 + p.terrainRoughness() * 28.0) * (1.0 - 0.4 * p.erosion());
        // frequency scales with the terrainFrequency parameter
        double frequency = 0.01 + p.generationParameters().terrainFrequency() * 0.02;

        boolean gas = p.surface() == PlanetSurface.GASEOUS;
        boolean hasWater = !gas && p.waterCoverage() > 0.01;

        // Hydrology (R8 fix): sea level is driven by waterCoverage, NOT pinned to the
        // mean terrain height. Terrain roughly spans [baseHeight - amplitude, baseHeight + amplitude];
        // solving for a waterCoverage fraction below the (near-)uniform noise yields:
        //   seaLevel = baseHeight + amplitude * (2 * waterCoverage - 1).
        // For coverage 0.5 the sea sits at the mean (current behaviour); for lower coverage it
        // sinks into the lowlands so only basins flood. Clamped to the terrain span.
        double seaLevel;
        if (gas || !hasWater) {
            seaLevel = baseHeight;
        } else {
            double lo = baseHeight - amplitude;
            double hi = baseHeight + amplitude;
            seaLevel = baseHeight + amplitude * (2.0 * p.waterCoverage() - 1.0);
            if (seaLevel < lo) seaLevel = lo;
            if (seaLevel > hi) seaLevel = hi;
        }

        return new PlanetWorldgenProfile(
                planetId,
                p.terrainSeed(),
                baseHeight,
                amplitude,
                frequency,
                seaLevel,           // waterCoverage-aware (was: baseHeight)
                hasWater,
                surfaceFor(p.surface()),
                subsurfaceFor(p.surface()),
                hasWater ? FluidProfile.WATER : FluidProfile.NONE,
                TerrainPattern.select(p.seed().value(), p.surface()),
                PlanetMaterialSelector.paletteFor(p.surface(), p.materialSeed()));
    }

    private static SurfaceMaterial surfaceFor(PlanetSurface s) {
        return switch (s) {
            case SOLID_ROCKY -> SurfaceMaterial.STONE;
            case SOLID_ICE -> SurfaceMaterial.ICE;
            case SOLID_DESERT -> SurfaceMaterial.SAND;
            case SOLID_VOLCANIC -> SurfaceMaterial.BASALT;
            case OCEANIC -> SurfaceMaterial.SAND;
            case GASEOUS -> SurfaceMaterial.METALLIC;
        };
    }

    private static SurfaceMaterial subsurfaceFor(PlanetSurface s) {
        return switch (s) {
            case SOLID_ICE -> SurfaceMaterial.ICE;
            case SOLID_VOLCANIC -> SurfaceMaterial.BASALT;
            default -> SurfaceMaterial.ROCK;
        };
    }
}

