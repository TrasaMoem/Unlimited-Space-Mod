package com.modscreating.unlimitedspace;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.level.ServerLevel;
import com.modscreating.unlimitedspace.core.destination.ProofPlanet;
import com.modscreating.unlimitedspace.core.destination.WorldDestination;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.modscreating.unlimitedspace.config.GalaxyConfig;
import com.modscreating.unlimitedspace.config.PlanetDimensionConfig;
import com.modscreating.unlimitedspace.worldgen.PlanetWorldgenRegistries;
import com.modscreating.unlimitedspace.core.destination.WorldKind;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.cs.ProceduralCsPack;
import com.modscreating.unlimitedspace.cs.ProceduralCsRuntime;
import com.modscreating.unlimitedspace.cs.ProceduralCsNetworking;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetSeedCache;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import net.minecraft.resources.ResourceLocation;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidCluster;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidFieldGeometry;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidGenerationProfile;
import com.modscreating.unlimitedspace.core.galaxy.TestGalaxyScope;
import com.modscreating.unlimitedspace.core.galaxy.TestGalaxyStatistics;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidWorldBinding;
import com.modscreating.unlimitedspace.worldgen.asteroid.AsteroidWorldgenRegistries;
import com.modscreating.unlimitedspace.worldgen.space.SpaceWorldgenRegistries;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import com.rae.creatingspace.content.planets.CSDimensionUtil;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(UnlimitedSpace.MODID)
public class UnlimitedSpace {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "unlimitedspace";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "unlimitedspace" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "unlimitedspace" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "unlimitedspace" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "unlimitedspace:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "unlimitedspace:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // R15: Rocket Control Block — the physical gateway into the Unlimited Space navigation UI.
    public static final DeferredBlock<com.modscreating.unlimitedspace.block.RocketControlTerminalBlock>
            ROCKET_CONTROL_TERMINAL = BLOCKS.registerBlock("rocket_control_terminal",
            com.modscreating.unlimitedspace.block.RocketControlTerminalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<BlockItem> ROCKET_CONTROL_TERMINAL_ITEM =
            ITEMS.registerSimpleBlockItem("rocket_control_terminal", ROCKET_CONTROL_TERMINAL);

    // Creates a new food item with the id "unlimitedspace:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "unlimitedspace:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.unlimitedspace")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // R14.9.3-C: dedicated "Unlimited Space" creative tab holding the 8 shimmering star-surface plasma blocks.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> UNLIMITED_SPACE_TAB =
            CREATIVE_MODE_TABS.register("unlimited_space", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.unlimitedspace.unlimited_space"))
                    .withTabsBefore(CreativeModeTabs.BUILDING_BLOCKS)
                    .icon(() -> com.modscreating.unlimitedspace.worldgen.star.StarPlasmaBlocks.item("red_plasma").get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (net.neoforged.neoforge.registries.DeferredItem<BlockItem> it
                                : com.modscreating.unlimitedspace.worldgen.star.StarPlasmaBlocks.items()) {
                            output.accept(it.get());
                        }
                        // R15: the Rocket Control Block lives in the Unlimited Space tab too.
                        output.accept(ROCKET_CONTROL_TERMINAL_ITEM.get());
                    }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public UnlimitedSpace(IEventBus modEventBus, ModContainer modContainer) {
        // R14.9.3-C: register the 8 custom star-surface plasma blocks BEFORE the block registry fires, so
        // the worldgen chunk generator can reference them and every star surface world is built from real
        // Unlimited Space blocks (not vanilla glowstone/sea-lantern/magma).
        com.modscreating.unlimitedspace.worldgen.star.StarPlasmaBlocks.init();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (UnlimitedSpace) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Galaxy generation config (Phase 2)
        GalaxyConfig.register(modContainer);

        // R14.6: register the virtual datapack that publishes procedural Creating Space metadata.
        modEventBus.addListener(ProceduralCsPack::register);

        // R14.6.3: register the server-to-client seed-aware metadata synchronization payload.
        modEventBus.addListener(ProceduralCsNetworking::register);

        // R15: register the Rocket Control navigation payloads (open screen / travel / status).
        modEventBus.addListener(com.modscreating.unlimitedspace.nav.R15Packets::register);

        // R15.1: register the Rocket Control BlockEntity (CS-compatible assembly brain).
        com.modscreating.unlimitedspace.block.USBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // Phase 3: custom worldgen codecs + POC planet dimension debug selection
        PlanetWorldgenRegistries.register(modEventBus);
        SpaceWorldgenRegistries.register(modEventBus);
        // R11: dedicated asteroid worldgen codecs (chunk generator + biome source).
        AsteroidWorldgenRegistries.register(modEventBus);
        // R14.9: star-surface worldgen codecs (chunk generator + biome source).
        com.modscreating.unlimitedspace.worldgen.star.StarWorldgenRegistries.register(modEventBus);
        PlanetDimensionConfig.register(modContainer);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        // R15.1: right-clicking our control block ON the assembled rocket opens the
        // R15 control UI bound to that real RocketContraptionEntity (CS-equivalent of
        // RocketControlInteraction, but with full Unlimited Space controls).
        // Registered directly in common setup (before any contraption can assemble).
        com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY
                .register(ROCKET_CONTROL_TERMINAL.get(),
                        new com.modscreating.unlimitedspace.block.USRocketControlInteraction());
        LOGGER.info("[unlimitedspace][R15.1] registered USRocketControlInteraction for rocket_control_terminal");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    /** R14.6.3: send the seed-aware procedural metadata to each joining player. */
    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ProceduralCsNetworking.sendSyncToPlayer(player);
        }
    }

    /**
     * Read-only Phase R6 diagnostics: prove in a live server that
     * (a) Creating Space's {@code rocket_accessible_dimension} registry contains the
     * procedural planet destinations, (b) the overworld routing edge exists,
     * (c) the procedural dimension ServerLevels resolve to the intended ChunkGenerator
     * classes, and (d) the domain identity (PlanetId/PlanetSeed/WorldDestination) matches
     * the running world seed. No travel/fuel/position systems are created or modified.
     */
    @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
        long worldSeed = event.getServer().overworld().getSeed();
        PlanetSeedCache.set(worldSeed);
        CelestialSeedCache.set(worldSeed);
        try {
            // --- R13.1 finite test-galaxy statistics (explicit FINITE scope only) ---
            // The potential galaxy is lazy and unbounded; these statistics resolve ONLY the
            // configured finite slice [0 .. testScope.systemCount()-1] and never materialize
            // the whole galaxy ("generate everything then count everything" is forbidden).
            Galaxy statsGalaxy = Galaxy.from(worldSeed, GalaxyConfig.parameters());
            TestGalaxyScope scope = GalaxyConfig.testScope();
            TestGalaxyStatistics stats = TestGalaxyStatistics.of(statsGalaxy, scope);
            LOGGER.info("[Unlimited Space] Test Galaxy Statistics");
            LOGGER.info("World Seed: {}", worldSeed);
            LOGGER.info("Systems in test scope: {} (finite scope [0..{}], configurable via "
                    + "\"testSystemCount\"; does not materialize the potential galaxy)",
                    stats.systems(), scope.systemCount() - 1);
            LOGGER.info("Stars: {}", stats.stars());
            LOGGER.info("Planets: {}", stats.planets());
            LOGGER.info("Moons: {}", stats.moons());
            LOGGER.info("Asteroid Clusters: {}", stats.asteroidClusters());
            // --- Creating Space runtime destination registry (read-only public API) ---
            var registry = event.getServer().registryAccess()
                    .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
            if (registry == null) {
                LOGGER.warn("[unlimitedspace] CS registry not present");
                return;
            }
            ResourceLocation overworldRl = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
            var origin = registry.get(overworldRl);

            // --- R11 asteroid cluster diagnostics (read-only; no travel/world mutation) ---
            try {
                AsteroidCluster cluster = Galaxy.from(worldSeed)
                        .getStarSystem(StarSystemId.of(0)).asteroid(0);
                AsteroidGenerationProfile prof = cluster.profile();
                AsteroidFieldGeometry geom = new AsteroidFieldGeometry(cluster.seed().value(), prof);
                ResourceLocation astRl = AsteroidWorldBinding.location(cluster.id());
                boolean dimRegistered = event.getServer().registryAccess()
                        .registryOrThrow(Registries.LEVEL_STEM).containsKey(astRl);
                boolean csReg = registry.containsKey(astRl);
                ServerLevel astLevel = event.getServer().getLevel(AsteroidWorldBinding.level(cluster.id()));
                String astGen = astLevel == null ? "NULL"
                        : astLevel.getChunkSource().getGenerator().getClass().getSimpleName();
                int[] spawn = geom.spawnAt();
                RocketAccessibleDimension astEntry = csReg ? registry.get(astRl) : null;
                float astGravity = astEntry == null ? Float.NaN : astEntry.gravity();
                boolean astIsOrbitCS = astEntry != null && astGravity == 0.0f;
                ResourceLocation astOrbitedBody = astEntry == null ? null : astEntry.orbitedBody();
                LOGGER.info("[unlimitedspace] Asteroid R11: id={} seed={} shape={} density={} "
                                + "asteroidCount={} sizeRange={}-{} dominantOre={} primaryMaterial={}",
                        cluster.id().code(), cluster.seed().value(), prof.shapePattern(),
                        String.format("%.2f", prof.density()), prof.asteroidCount(),
                        String.format("%.1f", prof.sizeRangeMin()), String.format("%.1f", prof.sizeRangeMax()),
                        prof.dominantOre(), prof.material().primary().blockId());
                LOGGER.info("[unlimitedspace] Asteroid R11: dimensionRegistered={} serverLevelResolved={} "
                                + "generator={} csDestinationRegistered={} destRl={} arrival(geom)=({},{},{}) "
                                + "csGravity={} csIsOrbit={} csOrbitedBody={}",
                        dimRegistered, astLevel != null, astGen, csReg, astRl, spawn[0], spawn[1], spawn[2],
                        astGravity, astIsOrbitCS, astOrbitedBody);
            } catch (Throwable t) {
                LOGGER.warn("[unlimitedspace] Asteroid R11 diagnostic failed", t);
            }

            // --- R14.6.2 procedural metadata architecture diagnostic (read-only) ---
            // The frozen WORLDGEN datapack registry is read BEFORE the seed is known, so it can
            // only contain the minecraft:overworld override (ProceduralCsPack). All procedural
            // bodies are published SEED-AWARE by ProceduralCsRuntime at ServerStartedEvent
            // (LOWEST priority, after Creating Space builds its own travel map). This block
            // proves the frozen-registry state; the seed-aware proof is logged by the LOWEST
            // handler after the bridge runs.
            try {
                ResourceLocation probeOrbitRl = ResourceLocation.fromNamespaceAndPath(MODID,
                        "planet/system_0000_planet_00/orbit");
                RocketAccessibleDimension probeOrbit = registry.get(probeOrbitRl);
                long proceduralCount = registry.keySet().stream()
                        .filter(rl -> rl.getNamespace().equals(MODID)).count();
                LOGGER.info("[unlimitedspace] R14.6.2 frozen registry: overworldReg={} proceduralRegistryEntries={} "
                                + "(expected 0: procedural metadata is seed-aware and published at runtime by "
                                + "ProceduralCsRuntime, not by the pre-seed datapack) system0OrbitFrozen={}",
                        origin != null, proceduralCount, probeOrbit != null);
            } catch (Throwable t) {
                LOGGER.warn("[unlimitedspace] R14.6.2 frozen-registry diagnostic failed", t);
            }

            int systemIndex = 0;
            int planetCount = 3;
            Galaxy galaxy = Galaxy.from(worldSeed);
            StarSystemId sysId = StarSystemId.of(systemIndex);

            LOGGER.info("[unlimitedspace] R7 server worldSeed={} system={} planets={}",
                    worldSeed, systemIndex, planetCount);

            for (int orbit = 0; orbit < planetCount; orbit++) {
                PlanetId planetId = PlanetId.of(sysId, orbit);
                Planet planet = galaxy.getStarSystem(sysId).getPlanet(orbit);
                PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet);
                WorldDestination surfDest = WorldDestination.planetSurface(planetId, planet.seed());
                WorldDestination orbDest = WorldDestination.planetOrbit(planetId, planet.seed());
                ResourceLocation surfRl = PlanetWorldBinding.location(planetId, WorldKind.SURFACE);
                ResourceLocation orbitRl = PlanetWorldBinding.location(planetId, WorldKind.ORBIT);

                boolean surfaceReg = registry.containsKey(surfRl);
                boolean orbitReg = registry.containsKey(orbitRl);
                boolean overworldLinksOrbit = origin != null
                        && origin.adjacentDimensions().containsKey(orbitRl);
                ServerLevel surfaceLevel = event.getServer().getLevel(PlanetWorldBinding.level(planetId, WorldKind.SURFACE));
                ServerLevel orbitLevel = event.getServer().getLevel(PlanetWorldBinding.level(planetId, WorldKind.ORBIT));
                String surfaceGen = surfaceLevel == null ? "NULL"
                        : surfaceLevel.getChunkSource().getGenerator().getClass().getSimpleName();
                String orbitGen = orbitLevel == null ? "NULL"
                        : orbitLevel.getChunkSource().getGenerator().getClass().getSimpleName();

                RocketAccessibleDimension orbitEntry = registry.get(orbitRl);
                int orbitArr = orbitEntry == null ? -1 : orbitEntry.arrivalHeight();
                float orbitGrav = orbitEntry == null ? Float.NaN : orbitEntry.gravity();

                LOGGER.info("[unlimitedspace] R7 planet #{} id={} seed={} type={} gravity={}g "
                                + "surfRl={} orbitRl={} destSurf={} destOrbit={} | reg[surface={},orbit={}] "
                                + "linksOrbit={} levels[surface={},orbit={}] gens[surface={},orbit={}] "
                                + "orbitMeta[arr={},grav={}]",
                        orbit, planetId.code(), planet.seed().value(),
                        planet.properties().type(), planet.properties().gravity(),
                        surfRl, orbitRl, surfDest.code(), orbDest.code(),
                        surfaceReg, orbitReg, overworldLinksOrbit,
                                                surfaceLevel != null, orbitLevel != null, surfaceGen, orbitGen,
                        orbitArr, orbitGrav);
                // R8 read-only generation-profile diagnostics: proves the procedural
                // pipeline resolves per-slot (seed+planetId) to distinct patterns,
                // material palettes, and waterCoverage-aware sea levels at runtime.
                LOGGER.info("[unlimitedspace] R8 profile #{} pattern={} family={} surfBlock={} "
                                + "seaLevel={}/{}(amp={},freq={}) hasWater={}",
                        orbit, profile.terrainPattern(), profile.materialPalette().family(),
                        profile.materialPalette().surface().blockId(),
                        (int) Math.round(profile.seaLevel()), profile.baseHeight(),
                        profile.amplitude(), profile.frequency(), profile.hasWater());
            }
            LOGGER.info("[unlimitedspace] R7 done. csDestinations={} overworldLinksOrbitA={}",
                    registry.keySet().size(), origin != null
                            && origin.adjacentDimensions().containsKey(
                                    ResourceLocation.fromNamespaceAndPath(MODID,
                                            "planet/system_0000_planet_00/orbit")));
        } catch (Throwable t) {
            LOGGER.warn("[unlimitedspace] R7 CS/runtime diagnostic failed", t);
        }
    }

    /**
     * R14.6.2: runs AFTER Creating Space's own travel-map build (EventPriority.LOWEST) and applies
     * the seed-aware, fully covered procedural metadata to the CS runtime travel map. Also logs the
     * domain-vs-CS gravity parity proof for a set of in-scope procedural bodies.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStartedSeedAwareBridge(ServerStartedEvent event) {
        try {
            ProceduralCsRuntime.onServerStarted(event.getServer());
            logSeedAwareGravityParity(event);
            // R14.8.1 Objective A: materialise any saved procedural dimension BEFORE the first
            // client's config-phase registry sync, so its DimensionType is present in the client's
            // synced registry and placeNewPlayer finds the level non-null (no respawn decode crash).
            com.modscreating.unlimitedspace.cs.ProceduralReconnectLoader.preload(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[unlimitedspace][R14.6.2] ProceduralCsRuntime bridge failed", t);
        }
    }

    /** Print the four-layer comparison for representative in-scope procedural bodies. */
    private void logSeedAwareGravityParity(ServerStartedEvent event) {
        long worldSeed = event.getServer().overworld().getSeed();
        Galaxy galaxy = Galaxy.from(worldSeed);
        int probeSystems = Math.min(ProceduralCsRuntime.coveredSystemCount(), 4);
        for (int s = 0; s < probeSystems; s++) {
            var system = galaxy.getStarSystem(StarSystemId.of(s));
            int planets = Math.min(system.planetCount(), 3);
            for (int p = 0; p < planets; p++) {
                var planet = system.getPlanet(p);
                ResourceLocation surfRl = PlanetWorldBinding.location(planet.id(), WorldKind.SURFACE);
                ResourceLocation orbitRl = PlanetWorldBinding.location(planet.id(), WorldKind.ORBIT);
                double domainMs = com.modscreating.unlimitedspace.core.physics.Gravity
                        .toMetersPerSecondSq(planet.properties().gravity());
                Float csSurface = csGravity(surfRl);
                Float csOrbit = csGravity(orbitRl);
                Integer csSurfaceArr = csArrival(surfRl);
                Integer csOrbitArr = csArrival(orbitRl);
                LOGGER.info("[unlimitedspace][R14.6.2] gravity parity: system={} body={} "
                                + "domainGravityMs={} csSurfaceGravity={} csOrbitGravity={} "
                                + "csSurfaceArrival={} csOrbitArrival={}",
                        s, planet.id().code(), String.format(java.util.Locale.ROOT, "%.4f", domainMs),
                        csSurface == null ? "MISSING" : String.format(java.util.Locale.ROOT, "%.4f", csSurface),
                        csOrbit == null ? "MISSING" : String.format(java.util.Locale.ROOT, "%.4f", csOrbit),
                        csSurfaceArr == null ? "MISSING" : csSurfaceArr,
                        csOrbitArr == null ? "MISSING" : csOrbitArr);
            }
        }
    }

    private static Float csGravity(ResourceLocation rl) {
        try {
            var e = CSDimensionUtil.getTravelMap().get(rl);
            return e == null ? null : e.gravity();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Integer csArrival(ResourceLocation rl) {
        try {
            var e = CSDimensionUtil.getTravelMap().get(rl);
            return e == null ? null : e.arrivalHeight();
        } catch (Throwable t) {
            return null;
        }
    }
}


