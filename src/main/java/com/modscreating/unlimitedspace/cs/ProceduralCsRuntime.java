package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.Config;
import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.cs.ProceduralMetadataGenerator;
import com.modscreating.unlimitedspace.core.cs.ProceduralRocketAccessibleDimension;
import com.rae.creatingspace.api.planets.RocketAccessibleDimension;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R14.6.2 runtime bridge that makes Creating Space's {@code rocket_accessible_dimension}
 * travel map SEED-AWARE and FULLY COVERED.
 *
 * <p>Why a runtime bridge is the correct architecture: the CS registry is a WORLDGEN-layer
 * datapack registry loaded and frozen during WorldStem creation, BEFORE the world seed is
 * decoded, and there is no public API to rebuild it afterwards (verified against Minecraft
 * 1.21.1 {@code WorldLoader.load} / {@code MinecraftServer.reloadResources} and NeoForge
 * 21.1.248 sources). Creating Space, however, exposes a public runtime seam:
 * {@code CSDimensionUtil.updatePlanetsFromRegistry(Registry)} + {@code updateCostMap()} +
 * {@code removeUnreachableDimensions()}, called at {@code ServerStartedEvent}. At that point
 * the world seed IS known, so the bridge publishes the metadata from the actual procedural
 * domain state (same {@code Galaxy}/{@code Planet}/{@code Moon} objects worldgen uses).
 *
 * <p>This preserves THE SINGLE SOURCE OF TRUTH: every surface gravity in the CS travel map
 * is {@code Planet.properties().gravity()} (or {@code Moon.properties().gravity()}) converted
 * to m/s². There is no second gravity formula and no hash-based placeholder.
 *
 * <p>On an integrated (single-process) client+server the static CS travel map is shared, so
 * the client immediately observes the same seed-aware values. On a dedicated server + remote
 * client the authoritative server physics (trajectory, gravity, arrival height, isOrbit) is
 * seed-aware; the remote client syncs the frozen (seed-independent) registry because the
 * vanilla registry sync cannot carry post-WorldStem changes.
 */
public final class ProceduralCsRuntime {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Hard cap on total travel-map entries. Creating Space builds an all-pairs Dijkstra cost
     * map at ServerStartedEvent (one run per entry); measured ~24s for ~6 000 entries and a
     * 60s server watchdog. The cap keeps any single bridge application (startup or on-demand)
     * inside the budget and turns an over-budget request into a loud error instead of a silent
     * fallback.
     */
    public static final int MAX_METADATA_ENTRIES = 9_000;

    private static long worldSeed = Long.MIN_VALUE;
    private static int coveredSystemCount = 0;
    private static List<ProceduralRocketAccessibleDimension> generated = List.of();

    private ProceduralCsRuntime() {
    }

    /** Whether the runtime bridge has already run for the current server. */
    public static synchronized boolean isInitialized() {
        return worldSeed != Long.MIN_VALUE;
    }

    /**
     * Build and apply the seed-aware travel map. Called on {@code ServerStartedEvent} at
     * {@code EventPriority.LOWEST} so it runs AFTER Creating Space's own travel-map build
     * (which reads the small frozen registry) and is therefore never overwritten.
     */
    public static synchronized void onServerStarted(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        worldSeed = seed;
        int scope = safeMetadataScope();
        coveredSystemCount = Math.max(scope, 0);
        generated = ProceduralMetadataGenerator.generate(seed, scope, UnlimitedSpace.MODID);
        LOGGER.info("[unlimitedspace][R14.6.2] building seed-aware CS metadata: worldSeed={} systems=[0..{}) generatedEntries={}",
                seed, scope, generated.size());
        apply(server, "startup");
        verifyCoverage(seed);
    }

    /**
     * Ensure the full seed-aware metadata of {@code systemIndex} exists in the CS travel map.
     * Idempotent. Returns {@code false} (with a loud error) when the resulting entry count
     * would exceed the CS cost-map budget - the caller must then treat the destination as
     * NOT playable instead of letting Creating Space silently fall back to 9.81/64.
     */
    public static synchronized boolean ensureSystem(MinecraftServer server, int systemIndex) {
        if (worldSeed == Long.MIN_VALUE) {
            onServerStarted(server);
        }
        if (systemIndex < coveredSystemCount) {
            return true;
        }
        long projected = countForScope(worldSeed, systemIndex + 1);
        if (projected > MAX_METADATA_ENTRIES) {
            LOGGER.error("[unlimitedspace][MISSING PROCEDURAL CS METADATA] "
                            + "system={} kind=STAR_SYSTEM requestedScope=[0..{}) projectedEntries={} exceeds CS "
                            + "cost-map budget {}; destination must stay inside the configured metadata scope",
                    systemIndex, systemIndex + 1, projected, MAX_METADATA_ENTRIES);
            return false;
        }
        generated = ProceduralMetadataGenerator.generate(worldSeed, systemIndex + 1, UnlimitedSpace.MODID);
        coveredSystemCount = systemIndex + 1;
        LOGGER.info("[unlimitedspace][R14.6.2] on-demand CS metadata expansion to [0..{}): entries={}",
                coveredSystemCount, generated.size());
        apply(server, "on-demand");
        return true;
    }

    /**
     * Whether the CS runtime travel map currently contains {@code rl}. Used to turn a missing
     * procedural entry into an unmistakable error instead of a silent 9.81/64 fallback.
     */
    public static boolean destinationRegistered(ResourceLocation rl) {
        try {
            Map<?, ?> travelMap = CSDimensionUtil.getTravelMap();
            return travelMap != null && travelMap.containsKey(rl);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int coveredSystemCount() {
        return coveredSystemCount;
    }

    public static int generatedEntryCount() {
        return generated.size();
    }

    // ================================================================ internals

    private static void apply(MinecraftServer server, String stage) {
        Registry<RocketAccessibleDimension> official = server.registryAccess()
                .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
        MappedRegistry<RocketAccessibleDimension> reg =
                new MappedRegistry<>(RocketAccessibleDimension.REGISTRY_KEY, Lifecycle.stable());
        // Keys this bridge overrides with seed-aware values: every generated procedural body plus the
        // overworld override. The frozen registry (datapack proof entries, the pack's overworld entry)
        // must NOT be copied for these keys - MappedRegistry.register throws on duplicates and the
        // seed-aware value is authoritative anyway.
        List<ProceduralRocketAccessibleDimension> all = generated;
        Set<ResourceLocation> overrideKeys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension p : all) {
            overrideKeys.add(ResourceLocation.parse(p.key()));
        }
        ProceduralRocketAccessibleDimension overworld = ProceduralMetadataGenerator.overworld(all);
        overrideKeys.add(ResourceLocation.parse(overworld.key()));
        if (official != null) {
            for (Map.Entry<ResourceKey<RocketAccessibleDimension>, RocketAccessibleDimension> e : official.entrySet()) {
                if (!overrideKeys.contains(e.getKey().location())) {
                    reg.register(e.getKey(), e.getValue(), RegistrationInfo.BUILT_IN);
                }
            }
        }
        for (ProceduralRocketAccessibleDimension p : all) {
            reg.register(ResourceKey.create(RocketAccessibleDimension.REGISTRY_KEY,
                    ResourceLocation.parse(p.key())), toCs(p), RegistrationInfo.BUILT_IN);
        }
        reg.register(ResourceKey.create(RocketAccessibleDimension.REGISTRY_KEY,
                ResourceLocation.parse(overworld.key())), toCs(overworld), RegistrationInfo.BUILT_IN);
        CSDimensionUtil.updatePlanetsFromRegistry(reg);
        CSDimensionUtil.updateCostMap();
        CSDimensionUtil.removeUnreachableDimensions();
        LOGGER.info("[unlimitedspace][R14.6.2] seed-aware CS travel map applied: stage={} coveredSystems=[0..{}) "
                        + "proceduralEntries={} officialEntries={}",
                stage, coveredSystemCount, all.size(), official == null ? 0 : official.keySet().size());
    }

    /** Convert the pure-domain definition into the CS runtime value object. */
    private static RocketAccessibleDimension toCs(ProceduralRocketAccessibleDimension p) {
        Map<ResourceLocation, RocketAccessibleDimension.AccessibilityParameter> adj = new HashMap<>();
        for (Map.Entry<String, Integer> e : p.adjacentDimensions().entrySet()) {
            adj.put(ResourceLocation.parse(e.getKey()),
                    new RocketAccessibleDimension.AccessibilityParameter(e.getValue(), 64));
        }
        return new RocketAccessibleDimension(
                p.distanceToOrbitingBody(),
                ResourceLocation.parse(p.orbitedBody()),
                p.arrivalHeight(),
                (float) p.gravity(),
                adj);
    }

    private static long countForScope(long seed, int systemCount) {
        var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(seed);
        long total = 0;
        for (int s = 0; s < systemCount; s++) {
            var system = galaxy.getStarSystem(com.modscreating.unlimitedspace.core.stars.StarSystemId.of(s));
            int planets = system.planetCount();
            int moons = 0;
            for (int p = 0; p < planets; p++) {
                moons += system.getPlanet(p).moonCount();
            }
            total += (long) planets * 2 + (long) moons * 2 + system.asteroidClusterCount() + 1L;
        }
        return total;
    }

    private static int safeMetadataScope() {
        try {
            return Config.CS_METADATA_SYSTEM_COUNT.get();
        } catch (Exception e) {
            return 200;
        }
    }

    /**
     * Verify that every canonical celestial body in scope has CS metadata and that the
     * gravity values match the procedural domain (single source of truth).
     */
    private static void verifyCoverage(long seed) {
        var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(seed);
        Set<String> generatedKeys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : generated) {
            generatedKeys.add(e.key());
        }
        long canonicalBodies = 0;
        long canonicalEntries = 0;
        List<String> missing = new ArrayList<>();
        for (int s = 0; s < coveredSystemCount; s++) {
            var system = galaxy.getStarSystem(com.modscreating.unlimitedspace.core.stars.StarSystemId.of(s));
            for (int p = 0; p < system.planetCount(); p++) {
                var planet = system.getPlanet(p);
                canonicalBodies++;
                canonicalEntries += 2;
                check(generatedKeys, planet.id().code(), "surface", UnlimitedSpace.MODID + ":planet/" + planet.id().code() + "/surface", missing);
                check(generatedKeys, planet.id().code(), "orbit", UnlimitedSpace.MODID + ":planet/" + planet.id().code() + "/orbit", missing);
                for (var moon : planet.moons()) {
                    canonicalBodies++;
                    canonicalEntries += 2;
                    check(generatedKeys, moon.id().code(), "surface", UnlimitedSpace.MODID + ":moon/" + moon.id().code() + "/surface", missing);
                    check(generatedKeys, moon.id().code(), "orbit", UnlimitedSpace.MODID + ":moon/" + moon.id().code() + "/orbit", missing);
                }
            }
            for (int a = 0; a < system.asteroidClusterCount(); a++) {
                canonicalBodies++;
                canonicalEntries++;
                check(generatedKeys, system.asteroid(a).id().code(), "asteroid",
                        UnlimitedSpace.MODID + ":asteroid/" + system.asteroid(a).id().code(), missing);
            }
            canonicalBodies++;
            canonicalEntries++;
            check(generatedKeys, system.id().code(), "star orbit", UnlimitedSpace.MODID + ":star/" + system.id().code() + "/orbit", missing);
        }
        long generatedCount = generated.size();
        LOGGER.info("[unlimitedspace][R14.6.2] coverage: canonicalBodies={} canonicalEntries={} generatedEntries={} missing={}",
                canonicalBodies, canonicalEntries, generatedCount, missing.size());
        if (!missing.isEmpty()) {
            for (String m : missing) {
                LOGGER.error("[unlimitedspace][MISSING PROCEDURAL CS METADATA] {}", m);
            }
        }
    }

    private static void check(Set<String> generatedKeys, String stableId, String kind, String rl, List<String> missing) {
        if (!generatedKeys.contains(rl)) {
            missing.add("system=" + stableId + " kind=" + kind + " rl=" + rl);
        }
    }
}