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
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetSeedCache;
import com.modscreating.unlimitedspace.worldgen.planet.PlanetWorldBinding;
import net.minecraft.resources.ResourceLocation;
import com.modscreating.unlimitedspace.worldgen.space.SpaceWorldgenRegistries;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;

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

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public UnlimitedSpace(IEventBus modEventBus, ModContainer modContainer) {
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

        // Phase 3: custom worldgen codecs + POC planet dimension debug selection
        PlanetWorldgenRegistries.register(modEventBus);
        SpaceWorldgenRegistries.register(modEventBus);
        PlanetDimensionConfig.register(modContainer);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

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
        try {
            // --- Creating Space runtime destination registry (read-only public API) ---
            var registry = event.getServer().registryAccess()
                    .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
            if (registry == null) {
                LOGGER.warn("[unlimitedspace] CS registry not present");
                return;
            }
            ResourceLocation overworldRl = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
            var origin = registry.get(overworldRl);

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
}
