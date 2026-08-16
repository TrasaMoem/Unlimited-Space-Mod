package com.modscreating.unlimitedspace.worldgen.planet;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.destination.ProofPlanet;
import com.modscreating.unlimitedspace.core.destination.WorldDestination;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.SurfaceMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Minecraft-side adapter for the Phase-1 proof planet. It is the single source of truth
 * that binds the pure-domain {@link PlanetId} (via {@link ProofPlanet}) to the concrete
 * Minecraft integration identifiers and to the deterministic values materialised into the
 * datapack {@code LevelStem} / {@code dimension} files.
 *
 * <p>This is the "Destination Binding" seam of the corrected architecture: the domain
 * ({@code ProofPlanet}/{@code WorldDestination}) knows nothing about
 * {@code ResourceLocation}/{@code ResourceKey}; this class is where the Java ↔ dimension
 * mapping lives. It deliberately does <strong>not</strong> import any Creating Space
 * class — CS integration is purely data (see {@code data/.../rocket_accessible_dimension/}),
 * so this file can compile and be tested without locking onto CS internals.
 */
public final class ProofPlanetWorlds {

    public static final String SURFACE_PATH = "procedural_planet_surface";
    public static final String ORBIT_PATH = "procedural_planet_orbit";

    private ProofPlanetWorlds() {}

    /* ---------------- Minecraft identifiers (destination key == dimension key) ---------------- */

    public static ResourceLocation surfaceLocation() {
        return ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, SURFACE_PATH);
    }

    public static ResourceLocation orbitLocation() {
        return ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, ORBIT_PATH);
    }

    public static ResourceKey<Level> surfaceLevel() {
        return ResourceKey.create(Registries.DIMENSION, surfaceLocation());
    }

    public static ResourceKey<Level> orbitLevel() {
        return ResourceKey.create(Registries.DIMENSION, orbitLocation());
    }

    public static ResourceKey<LevelStem> surfaceLevelStem() {
        return ResourceKey.create(Registries.LEVEL_STEM, surfaceLocation());
    }

    public static ResourceKey<DimensionType> surfaceDimensionType() {
        return ResourceKey.create(Registries.DIMENSION_TYPE, surfaceLocation());
    }

    /* ---------------- deterministic datapack values (mirror of the LevelStem JSON) ---------------- */

    public static WorldDestination surfaceDestination() {
        return ProofPlanet.surfaceDestination();
    }

    public static WorldDestination orbitDestination() {
        return ProofPlanet.orbitDestination();
    }

    /**
     * Concrete values decoded into {@code dimension/procedural_planet_surface.json}.
     * Kept in ONE place so it is auditable against the domain pipeline.
     */
    public record SurfaceWorldConfig(
            long terrainSeed, long biomeSeed,
            double baseHeight, double amplitude, double frequency,
            int seaLevel, int minY, int height, boolean hasWater,
            String surfaceBlock, String subsurfaceBlock, String fluidBlock) {

        public static SurfaceWorldConfig fromProfile() {
            PlanetWorldgenProfile w = ProofPlanet.worldProfile();
            PlanetProperties p = ProofPlanet.properties();
            return new SurfaceWorldConfig(
                    w.terrainSeed(),
                    p.biomeSeed(),
                    w.baseHeight(), w.amplitude(), w.frequency(),
                    (int) Math.round(w.seaLevel()), -64, 384, w.hasWater(),
                    block(w.surfaceMaterial()),
                    block(w.subsurfaceMaterial()),
                    w.hasWater() && w.fluid() == FluidProfile.WATER ? "minecraft:water" : "minecraft:air");
        }
    }

    /** Replicate {@link PlanetBlocks} mapping so the adapter exposes the same block ids as JSON. */
    private static String block(SurfaceMaterial m) {
        return switch (m) {
            case STONE -> "minecraft:stone";
            case ROCK -> "minecraft:diorite";
            case SAND -> "minecraft:sand";
            case ICE -> "minecraft:ice";
            case BASALT -> "minecraft:basalt";
            case GRASSY -> "minecraft:grass_block";
            case METALLIC -> "minecraft:smooth_basalt";
        };
    }
}