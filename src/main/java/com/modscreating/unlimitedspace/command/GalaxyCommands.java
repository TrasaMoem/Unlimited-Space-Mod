package com.modscreating.unlimitedspace.command;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.config.GalaxyConfig;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.SystemPathIndex;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
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
                                                        IntegerArgumentType.getInteger(ctx, "destination"))))))));
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

    /** `/unlimitedspace system` вЂ” resolve and print the player's current system. */
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
        send(src, "Unlimited Space вЂ” Current System");
        send(src, "System: " + system.id().code());
        send(src, "Stars: " + counts.stars());
        send(src, "Planets: " + counts.planets());
        send(src, "Moons: " + counts.moons());
        send(src, "Asteroid Clusters: " + counts.asteroidClusters());
        send(src, "World Seed: " + g.worldSeed());
        send(src, "Star types: " + system.stars().stream()
                .map(s -> String.valueOf(s.type())).distinct().toList());
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
     * {@code /unlimitedspace nav <system> <object> <destination>} вЂ” the admin navigation
     * command. It uses the SAME {@link DestinationResolver} as every other navigation path
     * (via {@link AdminNav}), and may initiate real Creating Space travel through the public
     * CS bridge. It NEVER performs a direct teleport.
     */
    private static int runNav(CommandSourceStack src, int system, int object, int destination) {
        long worldSeed = src.getServer().overworld().getSeed();
        Galaxy galaxy = Galaxy.from(worldSeed, GalaxyConfig.parameters());
        LOGGER.info("[unlimitedspace][NAV] /unlimitedspace nav {} {} {} (worldSeed={})",
                system, object, destination, worldSeed);
        NavResult nav = AdminNav.resolveAndMap(galaxy, GalaxyConfig.testScope(), system, object, destination);
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

        if (nav.ok()) {
            send(src, "Unlimited Space вЂ” Admin Nav");
            send(src, "System: " + system + "  Object: " + object + "  Destination: " + destination);
            send(src, "Resolved: " + (nav.resolved().object() != null ? nav.resolved().object().toString() : "?")
                    + " -> " + nav.status());
            if (nav.resourceLocation() != null) {
                send(src, "ResourceLocation: " + nav.resourceLocation());
            }
            send(src, "Status: " + (nav.status() == NavStatus.TRAVEL_STARTED
                    ? "Creating Space travel started."
                    : "Destination ready; launch your rocket."));
            return 1;
        }
        send(src, "Unlimited Space вЂ” Admin Nav");
        send(src, "Error: " + nav.message());
        return 0;
    }

    private static void send(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
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
