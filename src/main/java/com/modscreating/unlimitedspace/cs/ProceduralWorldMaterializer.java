package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.destination.ProceduralDimension;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.worldgen.dynamic.DynamicPlanetWorldManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * R14.8 single materialisation seam for ANY procedural dimension. Takes a recognised
 * {@code unlimitedspace:} body {@link ResourceLocation}, hands it back to the authoritative domain
 * model ({@link ProceduralDimension}) and then creates the matching live world through the SAME
 * {@link DynamicPlanetWorldManager} seam already proven by {@code AdminNav.ensureSurface},
 * {@code RocketDestinationWorldWatchdog} and {@code ProceduralCsRuntime.ensureSystem}.
 *
 * <p>It deliberately does NOT create a parallel world generator or a second dimension system. It also
 * guarantees (idempotently) that the body's system has seed-aware CS travel metadata before the world
 * is materialised, so Creating Space gravity / arrivalHeight / isOrbit / orbitedBody stay correct at
 * the moment the world appears.
 */
public final class ProceduralWorldMaterializer {

    private static final Logger LOGGER = LogManager.getLogger();

    private ProceduralWorldMaterializer() {
    }

    /**
     * Recognise and materialise the live world for {@code rl}. Returns empty if {@code rl} is not a
     * recognised procedural body binding, or if the DynamicDimensions loader failed to create the world.
     */
    public static Optional<ServerLevel> materialize(MinecraftServer server, ResourceLocation rl) {
        if (server == null || rl == null) {
            return Optional.empty();
        }
        Optional<ProceduralDimension> parsed = ProceduralDimension.parse(rl.getPath());
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        ProceduralDimension dim = parsed.get();

        // Already materialised? Cheap path — no CS-metadata regeneration, no world (re)creation.
        // This is what lets the R14.8 orbit-fall guard poll every tick without doing heavy work.
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        ServerLevel already = server.getLevel(key);
        if (already != null) {
            return Optional.of(already);
        }

        // Guarantee the seed-aware CS travel metadata for the owning system (idempotent, lazy,
        // no hard system cap) so the freshly created world is a valid, reachable destination.
        if (!ProceduralCsRuntime.ensureSystem(server, dim.systemIndex())) {
            LOGGER.error("[unlimitedspace][R14.8] ProceduralWorldMaterializer: could not ensure CS " +
                    "metadata for system={}", dim.systemIndex());
        }

        Optional<ServerLevel> level = ensureLevel(server, dim);
        if (level.isPresent()) {
            LOGGER.info("[unlimitedspace][R14.8] MATERIALIZED: rl={} procedural={} serverLevel={}" +
                            " generator={}",
                    rl, dim, level.get().dimension(),
                    level.get().getChunkSource().getGenerator().getClass().getSimpleName());
        } else {
            LOGGER.error("[unlimitedspace][R14.8] MATERIALIZE_FAILED: rl={} procedural={}", rl, dim);
        }
        return level;
    }

    private static Optional<ServerLevel> ensureLevel(MinecraftServer server, ProceduralDimension dim) {
        StarSystemId system = StarSystemId.of(dim.systemIndex());
        return switch (dim.kind()) {
            case PLANET_SURFACE -> DynamicPlanetWorldManager.ensurePlanetSurface(
                    server, PlanetId.of(system, dim.planetIndex()));
            case PLANET_ORBIT -> DynamicPlanetWorldManager.ensurePlanetOrbit(
                    server, PlanetId.of(system, dim.planetIndex()));
            case MOON_SURFACE -> DynamicPlanetWorldManager.ensureMoonSurface(
                    server, MoonId.of(PlanetId.of(system, dim.planetIndex()), dim.moonIndex()));
            case MOON_ORBIT -> DynamicPlanetWorldManager.ensureMoonOrbit(
                    server, MoonId.of(PlanetId.of(system, dim.planetIndex()), dim.moonIndex()));
            case ASTEROID -> DynamicPlanetWorldManager.ensureAsteroidCluster(
                    server, AsteroidClusterId.of(system, dim.asteroidIndex()));
            case STAR_BODY -> DynamicPlanetWorldManager.ensureStarSurface(
                    server, new StarId(system, dim.starIndex()));
            case STAR_ORBIT -> DynamicPlanetWorldManager.ensureStarOrbit(
                    server, new StarId(system, dim.starIndex()));
        };
    }

    /** Convenience for the orbit-descent guard: materialise the surface a body's orbit falls to. */
    public static Optional<ServerLevel> materializeOrbitFallTarget(MinecraftServer server,
                                                                   ProceduralDimension orbit) {
        if (orbit == null) {
            return Optional.empty();
        }
        Optional<ProceduralDimension> fall = orbit.fallTarget();
        if (fall.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation surfaceRl = ResourceLocation.fromNamespaceAndPath(
                UnlimitedSpace.MODID, fall.get().resourcePath());
        return materialize(server, surfaceRl);
    }
}
