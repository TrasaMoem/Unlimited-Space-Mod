package com.modscreating.unlimitedspace.nav;

import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.TestGalaxyScope;
import com.modscreating.unlimitedspace.core.nav.DestinationKind;
import com.modscreating.unlimitedspace.core.nav.DestinationResolver;
import com.modscreating.unlimitedspace.core.nav.DestinationSurfacePlayability;
import com.modscreating.unlimitedspace.core.nav.ResolvedDestination;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidWorldBinding;
import com.modscreating.unlimitedspace.worldgen.dynamic.DynamicPlanetWorldManager;
import com.modscreating.unlimitedspace.worldgen.planet.MoonWorldBinding;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

/**
 * Minecraft/CS adapter that turns a raw {@code (system, object, destination)} triple into a
 * fully classified admin navigation result. It delegates all object/destination semantics to
 * the single pure-domain {@link DestinationResolver} and only adds the resource-location
 * mapping and the playability gates (CS registry, LevelStem).
 */
public final class AdminNav {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    /**
     * R14.3.1: the addon ships DynamicDimensions 0.9.1 as a hard dependency, so the runtime
     * lazy-world seam is always available; a procedural surface with no static CS entry is
     * therefore {@link DestinationSurfacePlayability#DYNAMIC_PROCEDURAL}.
     */
    private static final boolean DYNAMIC_PROCEDURAL_AVAILABLE = true;

    private AdminNav() {
    }

    /**
     * Resolve the triple within the finite {@code scope} and map it to a resource location.
     * This performs domain resolution and the deterministic binding mapping; it does NOT yet
     * check the runtime registries (see {@link #classify(NavResult, DestinationCatalog)}).
     */
    public static NavResult resolveAndMap(Galaxy galaxy, TestGalaxyScope scope,
                                          int system, int object, int destination) {
        Objects.requireNonNull(galaxy, "galaxy");
        Objects.requireNonNull(scope, "scope");
        if (!scope.contains(system)) {
            return NavResult.fail(NavStatus.OUT_OF_SCOPE, NavStatus.OUT_OF_SCOPE.message());
        }
        ResolvedDestination resolved = DestinationResolver.resolve(galaxy, system, object, destination);
        if (resolved.isError()) {
            return NavResult.fail(NavStatus.RESOLVE_ERROR, resolved.errorMessage());
        }
        DestinationKind kind = resolved.destinationKind();
        ResourceLocation rl = null;
        switch (kind) {
            case PLANET_SURFACE:
                rl = PlanetWorldBinding.location(resolved.planet().id(), WorldKind.SURFACE);
                break;
            case PLANET_ORBIT:
                rl = PlanetWorldBinding.location(resolved.planet().id(), WorldKind.ORBIT);
                break;
            case MOON_SURFACE:
                rl = MoonWorldBinding.location(resolved.moon().id(), WorldKind.SURFACE);
                break;
            case MOON_ORBIT:
                rl = MoonWorldBinding.location(resolved.moon().id(), WorldKind.ORBIT);
                break;
            case ASTEROID_FIELD:
                rl = AsteroidWorldBinding.location(resolved.asteroid().id());
                break;
            case STAR_BODY:
            case STAR_ORBIT:
                return NavResult.resolved(NavStatus.STAR_NOT_SUPPORTED,
                        NavStatus.STAR_NOT_SUPPORTED.message(), resolved, null);
            default:
                throw new IllegalStateException("unhandled destination kind " + kind);
        }
        return NavResult.ready(resolved, rl);
    }

    /**
     * Apply the playability gates against the runtime catalogs. A {@code OK_READY} result
     * means the destination is registered in both Creating Space and the LevelStem registry.
     */
    public static NavResult classify(NavResult nav, DestinationCatalog catalog) {
        Objects.requireNonNull(nav, "nav");
        Objects.requireNonNull(catalog, "catalog");
        if (nav.status() == NavStatus.STAR_NOT_SUPPORTED) {
            return nav;
        }
        if (nav.isError() || nav.resolved() == null) {
            return nav;
        }
        // R14.4: a body that was already dynamically materialised (or is a static proof world)
        // arrives here as OK_READY from ensureSurface(); its runtime CS travel entry is already
        // registered and the ServerLevel exists, so the static RocketAccessibleDimension/LevelStem
        // datapack gate must NOT reject it. Only unresolved/unsupported or failed bodies proceed.
        if (nav.status() == NavStatus.OK_READY) {
            return nav;
        }
        ResourceLocation rl = nav.resourceLocation();
        if (rl == null) {
            return NavResult.fail(NavStatus.NOT_PLAYABLE, NavStatus.NOT_PLAYABLE.message());
        }
        ResolvedDestination resolved = nav.resolved();
        // PLANET_SURFACE destinations are prepared by ensureSurface() (static proof worlds pass
        // through; procedural worlds are created on demand via DynamicDimensions). classify() is
        // always invoked *after* ensureSurface() in GalaxyCommands.runNav, so a surface that
        // reaches here is already OK_READY (static) or was dynamically created + handed its runtime
        // CS travel entry. The pure playability model governs the gate: STATIC_REGISTERED and
        // DYNAMIC_PROCEDURAL are both playable (the latter must never be rejected by the static
        // csRegistered/hasLevelStem gate BEFORE the dynamic world exists); only DOMAIN_ONLY is an
        // explicit NOT_PLAYABLE failure.
        if (resolved.destinationKind() == DestinationKind.PLANET_SURFACE) {
            DestinationSurfacePlayability play =
                    DestinationSurfacePlayability.classifyPlanetSurface(
                            catalog.csRegistered(rl), catalog.hasLevelStem(rl), DYNAMIC_PROCEDURAL_AVAILABLE);
            if (play == DestinationSurfacePlayability.STATIC_REGISTERED
                    || play == DestinationSurfacePlayability.DYNAMIC_PROCEDURAL) {
                return nav;
            }
            return NavResult.fail(NavStatus.NOT_PLAYABLE,
                    NavStatus.NOT_PLAYABLE.message() + ": " + rl);
        }
        if (!catalog.csRegistered(rl)) {
            return NavResult.fail(NavStatus.NOT_REGISTERED_IN_CS,
                    NavStatus.NOT_REGISTERED_IN_CS.message() + ": " + rl);
        }
        if (!catalog.hasLevelStem(rl)) {
            return NavResult.fail(NavStatus.NO_LEVEL_STEM,
                    NavStatus.NO_LEVEL_STEM.message() + ": " + rl);
        }
        return NavResult.ready(resolved, rl);
    }

    /**
     * R14.3.1 lazy world creation for admin {@code /nav}.
     *
     * <p>This runs <em>before</em> {@link #classify}'s static Creating Space gate (see
     * {@code GalaxyCommands.runNav}), so a procedural {@link DestinationKind#PLANET_SURFACE} is
     * never rejected by the static {@code RocketAccessibleDimension} registry before the world is
     * created. Two cases:
     * <ul>
     *   <li><b>STATIC_REGISTERED</b> proof worlds ({@code planet_00}..{@code planet_02}) &mdash;
     *       already backed by a datapack dimension + LevelStem &mdash; are passed through untouched;</li>
     *   <li><b>DYNAMIC_PROCEDURAL</b> planet surfaces with no datapack entry are created on first
     *       contact via {@link DynamicPlanetWorldManager#ensurePlanetSurface}, which loads (or
     *       creates) the {@code ServerLevel} and registers the runtime CS travel entry
     *       ({@code registerCsTravelEntry}); the result is then {@code OK_READY}.</li>
     * </ul>
     * Star/moon/asteroid bodies that are not statically registered are left in their classified
     * failure state (no dynamic creation yet).
     */
    public static NavResult ensureSurface(MinecraftServer server, NavResult nav) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(nav, "nav");
        if (nav.isError() || nav.resolved() == null) {
            return nav;
        }
        ResolvedDestination resolved = nav.resolved();
        DestinationKind kind = resolved.destinationKind();
        // Star surfaces/orbits are not playable Minecraft worlds (reported by resolveAndMap).
        if (kind == DestinationKind.STAR_BODY || kind == DestinationKind.STAR_ORBIT) {
            return nav;
        }
        ResourceLocation rl = nav.resourceLocation();
        if (rl == null) {
            return nav;
        }
        // Static proof destinations (already backed by a datapack LevelStem + CS registry) pass
        // through untouched; procedural destinations are lazily materialised below.
        DestinationCatalog catalog = CsCatalog.of(server);
        boolean staticRegistered = catalog.csRegistered(rl) && catalog.hasLevelStem(rl);
        if (staticRegistered) {
            LOGGER.info("[unlimitedspace][NAV] static proof destination; no dynamic creation needed: rl={} kind={}", rl, kind);
            return NavResult.ready(resolved, rl);
        }
        LOGGER.info("[unlimitedspace][NAV] dynamic world preparation: kind={} rl={}", kind, rl);
        Optional<ServerLevel> level = prepareBody(server, kind, resolved);
        if (level.isPresent()) {
            ServerLevel lvl = level.get();
            boolean csRuntime = (CSDimensionUtil.getTravelMap() != null)
                    && CSDimensionUtil.getTravelMap().containsKey(rl);
            String generatorName = lvl.getChunkSource().getGenerator().getClass().getSimpleName();
            LOGGER.info("[unlimitedspace][NAV] after ensureWorld: dynamicWorldReady=true serverLevel=true generator={} runtimeCsEntry={}",
                    generatorName, csRuntime);
            return NavResult.ready(resolved, rl);
        }
        LOGGER.warn("[unlimitedspace][NAV] dynamic world could not be loaded: rl={} kind={}", rl, kind);
        return NavResult.fail(NavStatus.NOT_PLAYABLE,
                NavStatus.NOT_PLAYABLE.message() + " (dynamic world could not be loaded): " + rl);
    }

    private static Optional<ServerLevel> prepareBody(MinecraftServer server, DestinationKind kind,
                                                     ResolvedDestination resolved) {
        return switch (kind) {
            case PLANET_SURFACE -> DynamicPlanetWorldManager.ensurePlanetSurface(server, resolved.planet().id());
            case PLANET_ORBIT -> DynamicPlanetWorldManager.ensurePlanetOrbit(server, resolved.planet().id());
            case MOON_SURFACE -> DynamicPlanetWorldManager.ensureMoonSurface(server, resolved.moon().id());
            case MOON_ORBIT -> DynamicPlanetWorldManager.ensureMoonOrbit(server, resolved.moon().id());
            case ASTEROID_FIELD -> DynamicPlanetWorldManager.ensureAsteroidCluster(server, resolved.asteroid().id());
            default -> Optional.empty();
        };
    }


    /**
     * Hand a fully {@code OK_READY} nav result to the official Creating Space travel bridge for
     * the given player's rocket. Returns a {@code TRAVEL_STARTED} result on success, or an
     * explicit failure ({@code NO_ROCKET} / {@code TRAVEL_BLOCKED}) вЂ” never silent.
     */
    public static NavResult attemptTravel(ServerPlayer player, NavResult nav) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nav, "nav");
        if (nav.status() != NavStatus.OK_READY) {
            return nav;
        }
        RocketContraptionEntity rocket = CsTravelBridge.findRocket(player);
        if (rocket == null) {
            return NavResult.fail(NavStatus.NO_ROCKET, NavStatus.NO_ROCKET.message());
        }
        boolean launched = CsTravelBridge.launch(player, rocket, nav.resourceLocation());
        if (!launched) {
            return NavResult.fail(NavStatus.TRAVEL_BLOCKED, NavStatus.TRAVEL_BLOCKED.message());
        }
        return NavResult.resolved(NavStatus.TRAVEL_STARTED, null, nav.resolved(),
                nav.resourceLocation());
    }
}
