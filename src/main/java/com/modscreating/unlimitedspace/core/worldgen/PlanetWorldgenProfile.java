package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialProfile;
import com.modscreating.unlimitedspace.core.worldgen.terrain.TerrainGenerator;

/**
 * Canonical root of procedural planet world generation (R8).
 *
 * <p>Single, Minecraft-free profile composing all sub-profiles. Evolved (not
 * replaced) from the R7 record: the flat terrain fields became a
 * {@link TerrainProfile}; the fixed {@code materialPalette} became a variable
 * {@link PlanetMaterialProfile}; new sub-profiles (biome, water, environment,
 * visual) were added as new fields on this same canonical type.
 *
 * <pre>
 * PlanetSeed ── PlanetProperties ── PlanetWorldgenProfile (this)
 *     ├── TerrainProfile        (TerrainPattern + shaping)
 *     ├── PlanetBiomeProfile     (climate-aware 1..5 presets + spatial distribution)
 *     ├── PlanetMaterialProfile  (roles + biome compat)
 *     ├── PlanetWaterProfile     (coverage-aware sea level + hydrology)
 *     ├── PlanetEnvironmentProfile (climate/atmosphere/gravity)
 *     └── PlanetVisualProfile    (deterministic sky/water/fog/sun colors)
 * </pre>
 *
 * <p>Deterministic: {@code (PlanetId, worldSeed)} → identical profile.
 * Pure domain: no Minecraft/NeoForge types. Lightweight placeholder
 * sub-profiles (vegetation/resources/structures) are reserved for future phases
 * and intentionally not materialized here.
 */
public record PlanetWorldgenProfile(
        PlanetId planetId,
        long planetSeed,
        PlanetProperties properties,
        TerrainProfile terrain,
        PlanetBiomeProfile biome,
        PlanetMaterialProfile material,
        PlanetWaterProfile water,
        PlanetEnvironmentProfile environment,
        PlanetVisualProfile visual
) {

        /** Back-compat accessor: primary terrain pattern. */
    public TerrainPattern terrainPattern() {
        return terrain.primaryPattern();
    }

    /** Back-compat accessor: material palette family. */
    public com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily materialFamily() {
        return material.surface().family();
    }

    /** Back-compat accessor: material palette surface. */
    public com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterial surfaceMaterialRaw() {
        return material.surface();
    }

    /** Back-compat accessor: reconstructed legacy palette view for old call-sites. */
    public com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialPalette materialPalette() {
        return com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialSelector
                .paletteFor(properties.surface(), materialSeed());
    }

    /** Back-compat: surface material role (Minecraft adapter). */
    public SurfaceMaterial surfaceMaterial() {
        return surfaceMaterialRaw().family() == com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily.ICE
                ? SurfaceMaterial.ICE : surfaceMaterialRaw().family() == com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily.SAND
                ? SurfaceMaterial.SAND : surfaceMaterialRaw().family() == com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily.METAL
                ? SurfaceMaterial.METALLIC : SurfaceMaterial.STONE;
    }

    /** Back-compat: subsurface material role. */
    public SurfaceMaterial subsurfaceMaterial() {
        return SurfaceMaterial.ROCK;
    }

    /** Back-compat: fluid profile. */
    public FluidProfile fluid() {
        return water.fluid();
    }

    // --- subsystem seed accessors (consumed by PlanetChunkGenerator) ---
    public long terrainSeed()    { return properties.terrainSeed(); }
    public long biomeSeed()      { return properties.biomeSeed(); }
    public long materialSeed()   { return properties.materialSeed(); }
    public long structureSeed()  { return properties.structureSeed(); }
    public long vegetationSeed() { return properties.vegetationSeed(); }

    /** Back-compat accessor: base terrain height. */
    public double baseHeight() { return terrain.baseHeight(); }
    /** Back-compat accessor: terrain amplitude. */
    public double amplitude() { return terrain.amplitude(); }
    /** Back-compat accessor: terrain frequency. */
    public double frequency() { return terrain.frequency(); }
    /** Back-compat accessor: sea level (waterCoverage-aware). */
    public double seaLevel() { return water.seaLevel(); }
    /** Back-compat accessor: has water. */
    public boolean hasWater() { return water.fluid() != FluidProfile.NONE || water.hasRivers(); }
    /** Back-compat accessor: erosion. */
    public double erosion() { return properties.erosion(); }

    /**
     * Canonical factory: builds the complete profile from a Planet + planetId.
     *
     * @deprecated use {@link #from(PlanetId, long)} with the real world seed.
     */
    public static PlanetWorldgenProfile from(Planet planet) {
        return from(planet.id(), planet.properties());
    }

    /**
     * Canonical factory: builds the complete profile from a PlanetId + PlanetProperties.
     */
    public static PlanetWorldgenProfile from(PlanetId planetId, PlanetProperties p) {
        TerrainProfile terrain = TerrainProfile.from(p.seed().value(), p);
        PlanetBiomeProfile biome = PlanetBiomeProfile.create(p.seed().value(), p);
        PlanetMaterialProfile material = PlanetMaterialProfile.create(p.seed().value(), p, biome.presets());
        PlanetWaterProfile water = PlanetWaterProfile.create(p.seed().value(), p,
                terrain.baseHeight(), terrain.amplitude());
        PlanetEnvironmentProfile env = PlanetEnvironmentProfile.from(p);
        PlanetVisualProfile visual = PlanetVisualProfile.create(p.seed().value(), p);

        return new PlanetWorldgenProfile(planetId, p.seed().value(), p,
                terrain, biome, material, water, env, visual);
    }

        /**
     * Canonical entry point from the real WorldSeed:
     * WorldSeed → Galaxy → StarSystem → PlanetSeed → PlanetProperties → profile.
     * The real Minecraft world seed flows through the deterministic Galaxy/Planet
     * pipeline, so the same (worldSeed, planetId) pair always yields the same profile.
     */
    public static PlanetWorldgenProfile from(PlanetId planetId, long worldSeed) {
        Planet planet = Galaxy.from(worldSeed)
                .getStarSystem(planetId.system())
                .getPlanet(planetId.orbitIndex());
        return from(planetId, planet.properties());
    }

        /** Convenience: resolve the TerrainGenerator for this planet's terrain seed. */
    public TerrainGenerator terrainGenerator() {
        return TerrainGenerators.from(this);
    }
}


