package com.modscreating.unlimitedspace.command;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.config.GalaxyConfig;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.SystemPathIndex;
import com.modscreating.unlimitedspace.core.physics.Gravity;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.cs.ProceduralCsRuntime;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidWorldBinding;
import com.modscreating.unlimitedspace.worldgen.planet.MoonWorldBinding;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import com.modscreating.unlimitedspace.worldgen.star.StarWorldBinding;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import net.minecraft.resources.ResourceLocation;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyCoordinate;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyLayout;
import com.modscreating.unlimitedspace.core.galaxy.layout.PlanetPosition;
import com.modscreating.unlimitedspace.core.galaxy.layout.SpaceConstants;
import com.modscreating.unlimitedspace.core.galaxy.layout.WorldgenVersion;
import com.modscreating.unlimitedspace.core.galaxy.layout.StarSystemPosition;
import com.modscreating.unlimitedspace.core.nav.DestinationResolver;
import com.modscreating.unlimitedspace.nav.AdminNav;
import com.modscreating.unlimitedspace.nav.CsCatalog;
import com.modscreating.unlimitedspace.nav.NavResult;
import com.modscreating.unlimitedspace.nav.NavStatus;
import com.modscreating.unlimitedspace.worldgen.space.SpaceChunkGenerator;
import com.modscreating.unlimitedspace.worldgen.space.SpaceDimensionBinding;
import com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.text.DecimalFormat;

/**
 * Server-side debug commands for inspecting the procedural galaxy. They only read
 * and display domain data; they never create dimensions, levels, chunks or worldgen.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID)
public final class GalaxyCommands {

        private static final Logger LOGGER = LogManager.getLogger();
    private static final DecimalFormat FMT = new DecimalFormat("0.##");

    private GalaxyCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("unlimitedspace")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("galaxy")
                        .executes(ctx -> runGalaxy(ctx.getSource())))
                .then(Commands.literal("system")
                        .executes(ctx -> runCurrentSystem(ctx.getSource()))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> runSystem(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id")))))
                .then(Commands.literal("planet")
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .then(Commands.argument("orbit", IntegerArgumentType.integer(0))
                                        .executes(ctx -> runPlanet(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "system"),
                                                IntegerArgumentType.getInteger(ctx, "orbit"))))))
                .then(Commands.literal("space")
                        .executes(ctx -> runSpace(ctx.getSource())))
                .then(Commands.literal("spaceinfo")
                        .executes(ctx -> runSpaceInfo(ctx.getSource())))
                .then(Commands.literal("nav")
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .then(Commands.argument("object", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("destination", IntegerArgumentType.integer(0))
                                                .executes(ctx -> runNav(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "system"),
                                                        IntegerArgumentType.getInteger(ctx, "object"),
                                                        IntegerArgumentType.getInteger(ctx, "destination")))))))
                .then(Commands.literal("trace")
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .then(Commands.argument("object", IntegerArgumentType.integer(0))
                                        .executes(ctx -> runTrace(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "system"),
                                                IntegerArgumentType.getInteger(ctx, "object"))))))
                .then(Commands.literal("cscheck")
                        .then(Commands.argument("rl", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> runCsCheck(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rl")))))
                .then(Commands.literal("costroute")
                        .then(Commands.argument("route", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> runCostRoute(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "route"))))));
    }

    private static Galaxy galaxyFor(CommandSourceStack src) {
        long worldSeed = src.getServer().overworld().getSeed();
        return Galaxy.from(worldSeed, GalaxyConfig.parameters());
    }

    private static int runGalaxy(CommandSourceStack src) {
        Galaxy g = galaxyFor(src);
        send(src, "World Seed      : " + g.worldSeed());
        send(src, "Galaxy Seed     : " + g.seed().value());
        send(src, "Galaxy Type     : " + g.type());
        send(src, "System Estimate : " + g.estimatedSystemCount() + " (metadata/debug)");
        return 1;
    }

    /** `/unlimitedspace system` Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р Р†РІР‚С›РЎС›Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р Р†РІР‚С›РЎС›Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В Р Р‹Р Р†РІР‚С›РЎС›Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В РІР‚В Р В Р вЂ Р В РІР‚С™Р РЋРІР‚С”Р В Р Р‹Р РЋРІР‚С”Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂєР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂє resolve and print the player's current system. */
    private static int runCurrentSystem(CommandSourceStack src) {
        String path = src.getLevel() != null
                ? src.getLevel().dimension().location().getPath() : null;
        return runSystem(src, SystemPathIndex.fromDimensionPath(path));
    }

    private static int runSystem(CommandSourceStack src, int id) {
        Galaxy g = galaxyFor(src);
        StarSystemId systemId = g.systemId(id);
        StarSystem system = g.getStarSystem(systemId);
        StarSystem.SystemCounts counts = system.counts();
        send(src, "Unlimited Space System Info");
        send(src, "System: " + system.id().code());
        send(src, "Stars: " + counts.stars());
        send(src, "Planets: " + counts.planets());
        send(src, "Moons: " + counts.moons());
        send(src, "Asteroid Clusters: " + counts.asteroidClusters());
        send(src, "World Seed: " + g.worldSeed());
        send(src, "Star types: " + system.stars().stream()
                .map(s -> String.valueOf(s.type())).distinct().toList());
        // R14.6.1/14.6.2: the COMPLETE canonical object list with kind + stable id. The numeric
        // object index in /nav is resolved through THIS list; never assume "object 2 = Planet 1".
        java.util.List<com.modscreating.unlimitedspace.core.galaxy.CelestialObject> objs =
                system.canonicalCelestialObjects();
        send(src, "--- Canonical objects (" + objs.size() + ") ---");
        for (int i = 0; i < objs.size(); i++) {
            com.modscreating.unlimitedspace.core.galaxy.CelestialObject obj = objs.get(i);
            send(src, "Object " + i + ": " + obj.kind() + " " + obj.code());
        }
        return 1;
    }

    private static int runPlanet(CommandSourceStack src, int system, int orbit) {
        Galaxy g = galaxyFor(src);
        StarSystemId systemId = g.systemId(system);
        StarSystem starSystem = g.getStarSystem(systemId);
        Planet planet = starSystem.getPlanet(orbit);
        var def = planet.definition();
        var props = planet.properties();
        send(src, "Planet ID       : " + def.id().code());
        send(src, "Planet Seed     : " + def.seed().value());
        send(src, "Planet Type     : " + def.type());
        send(src, "Gravity         : " + fmt(props.gravity()) + " g");
        send(src, "Temperature     : " + fmt(props.temperature()) + " K");
        send(src, "Humidity        : " + fmt(props.humidity()));
        send(src, "Water Coverage  : " + fmt(props.waterCoverage()));
        send(src, "Atmosphere      : " + props.atmosphere()
                + " (density=" + fmt(props.atmosphericDensity()) + ")");
        send(src, "Life Level      : " + fmt(props.lifeLevel()));
        send(src, "Surface         : " + props.surface());
        send(src, "Terrain Seed    : " + props.terrainSeed());
        return 1;
    }

    /**
     * {@code /unlimitedspace nav <system> <object> <destination>} Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р Р†РІР‚С›РЎС›Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р Р†РІР‚С›РЎС›Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В Р Р‹Р Р†РІР‚С›РЎС›Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В РІР‚В Р В Р вЂ Р В РІР‚С™Р РЋРІР‚С”Р В Р Р‹Р РЋРІР‚С”Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂєР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћвЂ“Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂє the admin navigation
     * command. It uses the SAME {@link DestinationResolver} as every other navigation path
     * (via {@link AdminNav}), and may initiate real Creating Space travel through the public
     * CS bridge. It NEVER performs a direct teleport.
     */
    private static int runNav(CommandSourceStack src, int system, int object, int destination) {
        long worldSeed = src.getServer().overworld().getSeed();
        Galaxy galaxy = Galaxy.from(worldSeed, GalaxyConfig.parameters());
        long tNav = System.nanoTime();
        // R14.5 BUG 7A/7B: validate navigation via Galaxy.exists Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂєР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В Р Р‹Р Р†РІР‚С›РЎС›Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В Р вЂ№Р В Р Р‹Р Р†РІР‚С›РЎС› NOT the finite statistics scope.
        // Any resolvable system (incl. far-out indices like 5000) is navigable; predecessor systems
        // are never materialised Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р вЂ Р Р†Р вЂљРЎвЂєР РЋРЎвЂєР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В Р Р‹Р Р†РІР‚С›РЎС›Р В Р’В Р вЂ™Р’В Р В Р’В Р В РІР‚в„–Р В Р’В Р В Р вЂ№Р В Р Р‹Р Р†РІР‚С›РЎС› resolve system N directly from WorldSeed + systemId.
        if (!galaxy.exists(system)) {
            send(src, "System does not exist in the procedural galaxy.");
            return 0;
        }
        LOGGER.info("[unlimitedspace][NAV] /unlimitedspace nav {} {} {} (worldSeed={})",
                system, object, destination, worldSeed);
        NavResult nav = AdminNav.resolveAndMap(galaxy, system, object, destination);
        // R14.3.1: lazy-create procedural planet surfaces BEFORE the static CS-registry gate.
        // ensureSurface runs first: it creates the dynamic ServerLevel and registers the runtime
        // CS travel entry, so classify() never rejects a procedural surface as NOT_REGISTERED_IN_CS.
        nav = AdminNav.ensureSurface(src.getServer(), nav);
        nav = AdminNav.classify(nav, CsCatalog.of(src.getServer()));
        if (src.getEntity() instanceof ServerPlayer player) {
            LOGGER.info("[unlimitedspace][NAV] before launch: rl={} status={} ok={}",
                    nav.resourceLocation(), nav.status(), nav.ok());
            nav = AdminNav.attemptTravel(player, nav);
        }
        LOGGER.info("[US] total navigation setup: {} ms (system={} object={} destination={} rl={} status={} ok={})",
                String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - tNav) / 1_000_000.0),
                system, object, destination, nav.resourceLocation(), nav.status(), nav.ok());

        if (nav.ok()) {
            send(src, nav.status() == NavStatus.TRAVEL_STARTED
                    ? "Travel started: system " + system + ", object " + object + ", destination " + destination + "."
                    : "Destination ready: system " + system + ", object " + object + ", destination " + destination
                            + ". Launch your rocket.");
            return 1;
        }
        send(src, "Error: " + nav.message());
        return 0;
    }

    /**
     * {@code /unlimitedspace trace <system> <object>} - the R14.6.1/14.6.2 end-to-end diagnostic
     * for ONE exact procedural celestial object: domain properties, resource locations, CS
     * runtime values, server levels and coverage status.
     */
    private static int runTrace(CommandSourceStack src, int system, int object) {
        Galaxy g = galaxyFor(src);
        if (!g.exists(system)) {
            send(src, "System does not exist in the procedural galaxy.");
            return 0;
        }
        StarSystem sys = g.getStarSystem(g.systemId(system));
        var objs = sys.canonicalCelestialObjects();
        if (object < 0 || object >= objs.size()) {
            send(src, "Object index " + object + " out of range 0.." + (objs.size() - 1)
                    + " for system " + sys.id().code());
            return 0;
        }
        var obj = objs.get(object);
        send(src, "=== PROCEDURAL FLIGHT TRACE ===");
        send(src, "SYSTEM: " + sys.id().code() + " (index " + system + ")");
        send(src, "OBJECT INDEX: " + object);
        send(src, "KIND: " + obj.kind());
        send(src, "STABLE ID: " + obj.code());
        send(src, "COVERAGE: " + (ProceduralCsRuntime.isCovered(system)
                ? "IN SCOPE (covered systems=" + ProceduralCsRuntime.coveredSystemCount()
                        + ", entries=" + ProceduralCsRuntime.generatedEntryCount() + ")"
                : "NOT GENERATED YET (lazy; /nav will generate on demand)"));
        switch (obj.kind()) {
            case PLANET -> tracePlanet(src, obj.planet());
            case ASTEROID_FIELD -> traceAsteroid(src, obj.asteroid());
            case STAR -> traceStar(src, sys);
            default -> send(src, "KIND: unknown - no trace available");
        }
        return 1;
    }

    private static void tracePlanet(CommandSourceStack src, com.modscreating.unlimitedspace.core.planets.Planet planet) {
        send(src, "DOMAIN: gravity=" + fmt(planet.properties().gravity()) + "g ("
                + fmt(Gravity.toMetersPerSecondSq(planet.properties().gravity())) + " m/s^2) type=" + planet.properties().type()
                + " moons=" + planet.moonCount());
        ResourceLocation surf = PlanetWorldBinding.location(planet.id(), WorldKind.SURFACE);
        ResourceLocation orbit = PlanetWorldBinding.location(planet.id(), WorldKind.ORBIT);
        send(src, "RL surface: " + surf);
        send(src, "RL orbit  : " + orbit);
        send(src, "CS RUNTIME surface: gravity=" + csGrav(surf) + " arrival=" + csArr(surf)
                + " isOrbit=" + csOrbit(surf));
        send(src, "CS RUNTIME orbit  : gravity=" + csGrav(orbit) + " arrival=" + csArr(orbit)
                + " isOrbit=" + csOrbit(orbit));
        send(src, "SERVERLEVEL surface: " + levelStatus(src, PlanetWorldBinding.level(planet.id(), WorldKind.SURFACE)));
        send(src, "SERVERLEVEL orbit  : " + levelStatus(src, PlanetWorldBinding.level(planet.id(), WorldKind.ORBIT)));
        for (int m = 0; m < Math.min(planet.moonCount(), 2); m++) {
            var moon = planet.moon(m);
            send(src, "  moon " + moon.id().code() + ": domain gravity="
                    + fmt(moon.properties().gravity()) + "g ("
                    + fmt(Gravity.toMetersPerSecondSq(moon.properties().gravity())) + " m/s^2) surf=" + MoonWorldBinding.location(moon.id(), WorldKind.SURFACE)
                    + " orbit=" + MoonWorldBinding.location(moon.id(), WorldKind.ORBIT));
        }
    }

    private static void traceAsteroid(CommandSourceStack src, com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster asteroid) {
        send(src, "DOMAIN: asteroid field, weightless (0 m/s^2)");
        ResourceLocation rl = AsteroidWorldBinding.location(asteroid.id());
        send(src, "RL: " + rl);
        send(src, "CS RUNTIME: gravity=" + csGrav(rl) + " arrival=" + csArr(rl)
                + " isOrbit=" + csOrbit(rl));
        send(src, "SERVERLEVEL: " + levelStatus(src, AsteroidWorldBinding.level(asteroid.id())));
    }

    private static void traceStar(CommandSourceStack src, StarSystem sys) {
        send(src, "DOMAIN: star orbit, weightless (0 m/s^2)");
        ResourceLocation rl = StarWorldBinding.location(sys.id(), WorldKind.ORBIT);
        send(src, "RL orbit: " + rl);
        send(src, "CS RUNTIME orbit: gravity=" + csGrav(rl) + " arrival=" + csArr(rl)
                + " isOrbit=" + csOrbit(rl));
        send(src, "SERVERLEVEL orbit: " + levelStatus(src, StarWorldBinding.level(sys.id(), WorldKind.ORBIT)));
    }

    private static String csGrav(ResourceLocation rl) {
        try {
            var travelMap = CSDimensionUtil.getTravelMap();
            if (travelMap == null || travelMap.get(rl) == null) {
                return "MISSING (fallback 9.81)";
            }
            return fmt(travelMap.get(rl).gravity());
        } catch (Throwable t) {
            return "MISSING (fallback 9.81)";
        }
    }

    private static String csArr(ResourceLocation rl) {
        try {
            var travelMap = CSDimensionUtil.getTravelMap();
            if (travelMap == null || travelMap.get(rl) == null) {
                return "MISSING (fallback 64)";
            }
            return String.valueOf(travelMap.get(rl).arrivalHeight());
        } catch (Throwable t) {
            return "MISSING (fallback 64)";
        }
    }

    private static String csOrbit(ResourceLocation rl) {
        try {
            return String.valueOf(CSDimensionUtil.isOrbit(rl));
        } catch (Throwable t) {
            return "MISSING";
        }
    }

    private static String levelStatus(CommandSourceStack src, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key) {
        try {
            var level = src.getServer().getLevel(key);
            return level == null ? "not loaded" : level.getChunkSource().getGenerator().getClass().getSimpleName();
        } catch (Throwable t) {
            return "n/a";
        }
    }

    private static void send(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
    }

    /**
     * {@code /unlimitedspace cscheck <rl>} - server-side mirror of the client diagnostic: prints the
     * authoritative SERVER values for one resource location (registry entry, CSDimensionUtil gravity,
     * arrivalHeight, isOrbit, travel-map membership) plus the domain value when the RL is a known
     * procedural body.
     */
    private static int runCsCheck(CommandSourceStack src, String rlString) {
        ResourceLocation rl;
        try {
            rl = ResourceLocation.parse(rlString.trim());
        } catch (Throwable t) {
            send(src, "Invalid resource location: " + rlString);
            return 0;
        }
        send(src, "=== SERVER PROCEDURAL CS TRACE ===");
        send(src, "RL: " + rl);
        try {
            var registry = src.getServer().registryAccess()
                    .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
            var regEntry = registry == null ? null : registry.get(rl);
            send(src, "Server registry entry: " + (regEntry == null ? "NO" : "YES")
                    + (regEntry == null ? "" : " (gravity=" + regEntry.gravity()
                    + " arrival=" + regEntry.arrivalHeight() + ")"));
        } catch (Throwable t) {
            send(src, "Server registry read failed: " + t.getMessage());
        }
        var travelMap = CSDimensionUtil.getTravelMap();
        var entry = travelMap == null ? null : travelMap.get(rl);
        send(src, "Server travelMap membership: " + (entry != null ? "YES" : "NO"));
        send(src, "Server CSDimensionUtil gravity: "
                + (entry == null ? "MISSING (fallback 9.81)" : String.valueOf(entry.gravity())));
        send(src, "Server arrivalHeight: "
                + (entry == null ? "MISSING (fallback 64)" : String.valueOf(entry.arrivalHeight())));
        send(src, "Server isOrbit: " + CSDimensionUtil.isOrbit(rl));
        // Domain value when the RL is a known procedural body.
        try {
            Galaxy g = galaxyFor(src);
            String path = rl.getPath();
            if (path.startsWith("planet/") && path.endsWith("/surface")) {
                int s = Integer.parseInt(path.substring("planet/system_".length(), path.indexOf("_planet_")));
                int o = Integer.parseInt(path.substring(path.indexOf("_planet_") + "_planet_".length(), path.indexOf("/surface")));
                var planet = g.getStarSystem(g.systemId(s)).getPlanet(o);
                send(src, "Domain gravity (m/s^2): " + fmt(Gravity.toMetersPerSecondSq(planet.properties().gravity())));
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    /**
     * {@code /unlimitedspace costroute <origin> <dest>} - R14.6.5 diagnostic: guarantees the
     * (origin -> destination) route exists in the Creating Space cost graph via the cheap route-scoped
     * rebuild (never the O(V^2) full map) and reports the resulting cost. Logs the timing to the
     * server log.
     */
    private static int runCostRoute(CommandSourceStack src, String routeString) {
        try {
            String[] parts = routeString.trim().split("\\s+");
            if (parts.length != 2) {
                send(src, "Usage: /unlimitedspace costroute <origin> <destination>");
                return 0;
            }
            ResourceLocation origin = ResourceLocation.parse(parts[0]);
            ResourceLocation destination = ResourceLocation.parse(parts[1]);
            boolean ready = ProceduralCsRuntime.ensureCostRoute(src.getServer(), origin, destination);
            int cost = CSDimensionUtil.cost(origin, destination);
            send(src, "Cost route: " + (ready ? "READY" : "FAILED") + " cost=" + cost
                    + " (" + origin + " -> " + destination + ")");
        } catch (Throwable t) {
            send(src, "Cost route error: " + t);
        }
        return 1;
    }

    private static GalaxyLayout spaceLayout(CommandSourceStack src) {
        return GalaxyLayout.from(src.getServer().overworld().getSeed(), GalaxyConfig.parameters());
    }

    /** The layout actually used by the running space dimension (closed set to the generator). */
    private static GalaxyLayout spaceLayoutFor(ServerLevel space) {
        if (space.getChunkSource().getGenerator() instanceof SpaceChunkGenerator g) {
            return g.layout();
        }
        return spaceLayoutFrom(space);
    }

    private static GalaxyLayout spaceLayoutFrom(ServerLevel space) {
        return GalaxyLayout.from(space.getSeed(), GalaxyConfig.parameters());
    }

    private static int runSpace(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;
        ServerLevel level = src.getServer().getLevel(SpaceDimensionBinding.level());
        if (level == null) {
            send(src, "Dimension not loaded: " + SpaceDimensionBinding.location());
            return 0;
        }
        GalaxyLayout layout = spaceLayoutFor(level);
        // Teleport onto the first planet of system 0 (the ROCKY POC planet),
        // not into deep space: (0,0) in the galaxy is usually interplanetary void.
        StarSystemPosition sys = layout.systemById(new StarSystemId(0));
        java.util.List<? extends PlanetPosition> planets = layout.planetsFor(sys);
        if (planets.isEmpty()) {
            send(src, "System 0 has no planet to land on.");
            return 0;
        }
        PlanetPosition p = planets.get(0);
        int bx = (int) Math.floor(p.x() * SpaceConstants.BLOCKS_PER_GALAXY_UNIT);
        int bz = (int) Math.floor(p.z() * SpaceConstants.BLOCKS_PER_GALAXY_UNIT);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (y <= level.getMinBuildHeight()) {
            y = 96;
        }
        player.teleportTo(level, bx + 0.5, y + 1, bz + 0.5, player.getYRot(), player.getXRot());
        send(src, "Teleported to " + SpaceDimensionBinding.location()
                + " near " + p.id().code() + " at " + bx + "," + (y + 1) + "," + bz);
        return 1;
    }

    private static int runSpaceInfo(CommandSourceStack src) {
        GalaxyLayout layout = spaceLayout(src);
        var pos = src.getPosition();
        GalaxyCoordinate g = BlockPosToGalaxyCoordinate.fromBlock((long) pos.x, (long) pos.z);
        var res = layout.lookup(g);
        send(src, "MC: " + pos);
        send(src, "GalaxyCoordinate: " + g.x() + "," + g.z());
        send(src, "WorldgenVersion: " + WorldgenVersion.V1_GRID);
        send(src, "Mode: " + (res.planet() != null ? "PLANET" : "DEEP_SPACE"));
        if (res.system() != null) send(src, "System: " + res.system().id().code());
        if (res.planet() != null) send(src, "Planet: " + res.planet().id().code());
        return 1;
    }

    private static String fmt(double v) {
        return FMT.format(v);
    }
}
