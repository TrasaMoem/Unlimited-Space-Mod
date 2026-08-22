package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.Config;
import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.cs.ProceduralMetadataGenerator;
import com.modscreating.unlimitedspace.core.cs.ProceduralRocketAccessibleDimension;
import com.modscreating.unlimitedspace.cs.network.ProceduralCsSyncPacket;
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
import java.util.regex.Pattern;

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

    private static long worldSeed = Long.MIN_VALUE;
    private static int baseScope = 0;
    private static final java.util.TreeSet<Integer> coveredSystems = new java.util.TreeSet<>();
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
     *
     * <p>R14.6.4: the base scope is only a PREBUILT cache. Systems beyond it are generated LAZILY
     * on first navigation ({@link #ensureSystem}) - the procedural universe has NO hard system cap.
     */
    public static synchronized void onServerStarted(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        worldSeed = seed;
        baseScope = safeMetadataScope();
        coveredSystems.clear();
        for (int s = 0; s < baseScope; s++) {
            coveredSystems.add(s);
        }
        long t0 = System.nanoTime();
        generated = ProceduralMetadataGenerator.generate(seed, baseScope, UnlimitedSpace.MODID);
        LOGGER.info("[unlimitedspace][R14.6.2] building seed-aware CS metadata: worldSeed={} baseSystems=[0..{}) "
                        + "generatedEntries={} [US] metadata={} ms",
                seed, baseScope, generated.size(), ms(System.nanoTime() - t0));
        publishTravelMap(server, "startup");
        // R14.6.5: build the all-pairs cost graph ONCE at STARTUP over the prebuilt base scope so legacy
        // CS-UI launches to base-scope systems keep working. It is NEVER rebuilt synchronously during /nav:
        // on-demand systems are served by the tiny route-scoped cost graph (ensureCostRoute).
        long tCost = System.nanoTime();
        CSDimensionUtil.updateCostMap();
        CSDimensionUtil.removeUnreachableDimensions();
        LOGGER.info("[US] CS runtime update (startup cost map over base scope): {} ms (coveredSystems={} entries={})",
                ms(System.nanoTime() - tCost), coveredSystems.size(), generated.size());
        verifyCoverage(seed);
    }

    /**
     * R14.6.4: ensure the full seed-aware metadata of {@code systemIndex} exists in the CS travel
     * map, generating ONLY that system lazily when it is outside the prebuilt base scope.
     * Idempotent and always succeeds for any valid non-negative system (no hard system cap).
     * The Creating Space cost map is re-run once per newly generated system (bounded by the
     * O(V^2) all-pairs Dijkstra; this is the accepted cost of lazy generation).
     */
    public static synchronized boolean ensureSystem(MinecraftServer server, int systemIndex) {
        if (systemIndex < 0) {
            return false;
        }
        if (worldSeed == Long.MIN_VALUE) {
            onServerStarted(server);
        }
        if (coveredSystems.contains(systemIndex)) {
            return true;
        }
        long seed = server.overworld().getSeed();
        long t0 = System.nanoTime();
        List<ProceduralRocketAccessibleDimension> sysEntries =
                ProceduralMetadataGenerator.generateForSystem(seed, systemIndex, UnlimitedSpace.MODID);
        long tMeta = System.nanoTime() - t0;
        java.util.Set<String> newKeys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : sysEntries) {
            newKeys.add(e.key());
        }
        List<ProceduralRocketAccessibleDimension> merged = new ArrayList<>(generated);
        merged.removeIf(e -> newKeys.contains(e.key()));
        merged.addAll(sysEntries);
        generated = List.copyOf(merged);
        coveredSystems.add(systemIndex);
        LOGGER.info("[unlimitedspace][R14.6.4] lazy CS metadata generation for system {}: +{} entries (total {}) "
                        + "[US] metadata={} ms",
                systemIndex, sysEntries.size(), generated.size(), ms(tMeta));
        // R14.6.5: publish the travel map WITHOUT rebuilding the all-pairs cost graph on the server thread.
        // The cost graph is populated lazily per flight route by ensureCostRoute().
        long tApply = System.nanoTime();
        publishTravelMap(server, "on-demand[" + systemIndex + "]");
        LOGGER.info("[US] ensureSystem: {} ms (metadata={} ms CS runtime update={} ms)",
                ms(System.nanoTime() - t0), ms(tMeta), ms(System.nanoTime() - tApply));
        ProceduralCsNetworking.broadcastSyncToPlayers(server);
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
        return baseScope;
    }

    /** R14.6.4: whether the given system index has been generated (base scope or on-demand). */
    public static boolean isCovered(int systemIndex) {
        return coveredSystems.contains(systemIndex);
    }

    /**
     * R14.6.5: guarantee {@code CSDimensionUtil.cost(origin, destination) > 0} WITHOUT ever running
     * the O(V^2) all-pairs rebuild on the server thread. The procedural graph is a hub-and-spoke
     * topology (every body reaches the overworld through its planet/star orbit edge, and the
     * overworld override reaches every body), so the route cost only needs the ORIGIN system + the
     * DESTINATION system + the overworld hub + the official dimensions: the rebuild is O(route^2)
     * (tens of nodes = a few ms) instead of O(allCovered^2) (~19 s).
     *
     * <p>Runs on the server thread (same thread as the subsequent rocket launch) and restores the
     * FULL travel map afterwards, so gravity/arrival lookups keep working for every body while the
     * cost graph carries exactly the route rows flight trajectory calculation needs.
     */
    public static synchronized boolean ensureCostRoute(MinecraftServer server,
                                                       ResourceLocation origin, ResourceLocation destination) {
        if (worldSeed == Long.MIN_VALUE) {
            onServerStarted(server);
        }
        if (origin == null || destination == null) {
            return false;
        }
        // R14.6.6: a Creating Space UI/schedule flight can target a body in a system that has NEVER
        // been generated (out-of-scope). Such a system is absent from `generated`, so mergeRoute()
        // would add nothing for it, buildRegistry() would omit it, and after updateCostMap() the
        // destination would NOT be a key => cost == -1. RocketScheduleRuntime.startCurrentInstruction()
        // then returns null (it refuses to start a navigation when cost <= 0), so the schedule stalls
        // and the rocket never launches. Fix: generate the destination (and origin, if procedural)
        // system's seed-aware metadata lazily BEFORE building the route so the route-scoped cost graph
        // actually contains the destination. This is the R14.6.5 route-scoped rebuild (a few ms) and
        // NEVER the O(V^2) all-pairs rebuild; ensureSystem is idempotent and caps at the covered set.
        int destSystem = systemIndexOf(destination);
        if (destSystem >= 0 && !coveredSystems.contains(destSystem)) {
            ensureSystem(server, destSystem);
        }
        int originSystem = systemIndexOf(origin);
        if (originSystem >= 0 && !coveredSystems.contains(originSystem)) {
            ensureSystem(server, originSystem);
        }
        int existing = safeCost(origin, destination);
        if (existing > 0) {
            return true;
        }
        long t0 = System.nanoTime();
        List<ProceduralRocketAccessibleDimension> route = new ArrayList<>();
        mergeRoute(route, origin);
        mergeRoute(route, destination);
        CSDimensionUtil.updatePlanetsFromRegistry(buildRegistry(server, route));
        long t1 = System.nanoTime();
        CSDimensionUtil.updateCostMap();
        long t2 = System.nanoTime();
        CSDimensionUtil.removeUnreachableDimensions();
        CSDimensionUtil.updatePlanetsFromRegistry(buildRegistry(server, generated)); // restore full travelMap
        int cost = safeCost(origin, destination);
        LOGGER.info("[US] ensureCostRoute: {} ms (registry={} ms costMap={} ms) origin={} destination={} cost={} routeEntries={}",
                ms(System.nanoTime() - t0), ms(t1 - t0), ms(t2 - t1), origin, destination, cost, route.size());
        return cost > 0;
    }

    public static int generatedEntryCount() {
        return generated.size();
    }

    /**
     * R14.6.3: compact client-sync projection of the seed-aware metadata. Contains ONLY the fields
     * the remote client actually consumes (gravity for the player-physics mixin, plus arrivalHeight
     * and orbitedBody for a coherent client travel map). Adjacency is intentionally omitted - the
     * authoritative trajectory/cost is computed on the server.
     */
    public static java.util.List<ProceduralCsSyncPacket.Entry> syncEntries() {
        java.util.List<ProceduralCsSyncPacket.Entry> out = new ArrayList<>(generated.size());
        for (ProceduralRocketAccessibleDimension p : generated) {
            out.add(new ProceduralCsSyncPacket.Entry(
                    ResourceLocation.parse(p.key()),
                    (float) p.gravity(),
                    p.arrivalHeight(),
                    ResourceLocation.parse(p.orbitedBody())));
        }
        return out;
    }

    // ================================================================ internals

    /**
     * R14.6.5: publish the full seed-aware travel map (gravity/arrival/isOrbit lookups) WITHOUT the
     * O(V^2) all-pairs cost-graph rebuild. Cheap: one registry copy. {@code removeUnreachableDimensions}
     * is deliberately NOT called here — it reads {@code costAdjacentMap} (NPE when the cost graph was
     * never built) and every procedural body is reachable through the overworld hub, so the planets
     * filter is a no-op for our entries; it only runs at startup after the base-scope cost map build.
     */
    private static void publishTravelMap(MinecraftServer server, String stage) {
        long t0 = System.nanoTime();
        CSDimensionUtil.updatePlanetsFromRegistry(buildRegistry(server, generated));
        long tReg = System.nanoTime() - t0;
        LOGGER.info("[unlimitedspace][R14.6.2] seed-aware CS travel map applied: stage={} coveredSystems={} "
                        + "proceduralEntries={} officialEntries={} [US] updatePlanetsFromRegistry={} ms",
                stage, coveredSystems.size(), generated.size(), officialCount(server), ms(tReg));
    }

    /** Fresh MappedRegistry: non-overridden official entries + the given procedural entries + the
     *  overworld override (whose adjacency covers exactly the given entries). */
    private static MappedRegistry<RocketAccessibleDimension> buildRegistry(MinecraftServer server,
                                                                           List<ProceduralRocketAccessibleDimension> entries) {
        Registry<RocketAccessibleDimension> official = server.registryAccess()
                .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
        MappedRegistry<RocketAccessibleDimension> reg =
                new MappedRegistry<>(RocketAccessibleDimension.REGISTRY_KEY, Lifecycle.stable());
        // Keys this bridge overrides with seed-aware values: the given procedural bodies plus the
        // overworld override. The frozen registry (datapack proof entries, the pack's overworld entry)
        // must NOT be copied for these keys - MappedRegistry.register throws on duplicates and the
        // seed-aware value is authoritative anyway.
        Set<ResourceLocation> overrideKeys = new HashSet<>();
        for (ProceduralRocketAccessibleDimension p : entries) {
            overrideKeys.add(ResourceLocation.parse(p.key()));
        }
        ProceduralRocketAccessibleDimension overworld = ProceduralMetadataGenerator.overworld(entries);
        overrideKeys.add(ResourceLocation.parse(overworld.key()));
        if (official != null) {
            for (Map.Entry<ResourceKey<RocketAccessibleDimension>, RocketAccessibleDimension> e : official.entrySet()) {
                if (!overrideKeys.contains(e.getKey().location())) {
                    reg.register(e.getKey(), e.getValue(), RegistrationInfo.BUILT_IN);
                }
            }
        }
        for (ProceduralRocketAccessibleDimension p : entries) {
            reg.register(ResourceKey.create(RocketAccessibleDimension.REGISTRY_KEY,
                    ResourceLocation.parse(p.key())), toCs(p), RegistrationInfo.BUILT_IN);
        }
        reg.register(ResourceKey.create(RocketAccessibleDimension.REGISTRY_KEY,
                ResourceLocation.parse(overworld.key())), toCs(overworld), RegistrationInfo.BUILT_IN);
        return reg;
    }

    /** The system index owning {@code rl}, or -1 for non-procedural resource locations. */
    private static final Pattern SYSTEM_IN_RL = Pattern.compile("system_(\\d+)");

    private static int systemIndexOf(ResourceLocation rl) {
        if (rl == null || !UnlimitedSpace.MODID.equals(rl.getNamespace())) {
            return -1;
        }
        var m = SYSTEM_IN_RL.matcher(rl.getPath());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** Append every generated entry of the system owning {@code rl} to {@code route} (deduplicated). */
    private static void mergeRoute(List<ProceduralRocketAccessibleDimension> route, ResourceLocation rl) {
        int system = systemIndexOf(rl);
        if (system < 0) {
            return;
        }
        Set<String> have = new HashSet<>();
        for (ProceduralRocketAccessibleDimension e : route) {
            have.add(e.key());
        }
        for (ProceduralRocketAccessibleDimension e : generated) {
            if (systemIndexOf(ResourceLocation.parse(e.key())) == system && have.add(e.key())) {
                route.add(e);
            }
        }
    }

    private static int safeCost(ResourceLocation from, ResourceLocation to) {
        try {
            return CSDimensionUtil.cost(from, to);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int officialCount(MinecraftServer server) {
        try {
            Registry<RocketAccessibleDimension> official = server.registryAccess()
                    .registry(RocketAccessibleDimension.REGISTRY_KEY).orElse(null);
            return official == null ? 0 : official.keySet().size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1_000_000.0);
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

    private static int safeMetadataScope() {
        try {
            return Config.CS_METADATA_SYSTEM_COUNT.get();
        } catch (Exception e) {
            return 200;
        }
    }

    /**
     * Verify that every canonical celestial body of every COVERED system (base scope + on-demand)
     * has CS metadata and that the gravity values match the procedural domain (single source of truth).
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
        for (int s : coveredSystems) {
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
            check(generatedKeys, system.id().code(), "star surface", UnlimitedSpace.MODID + ":star/" + system.id().code() + "/surface", missing);
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