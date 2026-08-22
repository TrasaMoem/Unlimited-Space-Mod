package com.modscreating.unlimitedspace.cs;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.asteroids.AsteroidClusterId;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.worldgen.dynamic.DynamicPlanetWorldManager;
import com.rae.creatingspace.api.squedule.instruction.DestinationInstruction;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R14.6.4 primary fix for the real in-game failure {@code "rocket rises then falls without a
 * dimension change"}.
 *
 * <p>Verified against Creating Space 1.7.18 bytecode, the transition is performed by
 * {@code RocketContraptionEntity.tickDimensionChangeLogic()} which fires once the rocket's Y
 * exceeds 300 and then requires
 * {@code server.getLevel(ResourceKey.create(Registries.DIMENSION, rocket.destination))} to be
 * non-null. When the destination {@link ServerLevel} was never materialised (the player launched
 * through the CS schedule/rocket-controls UI instead of {@code /unlimitedspace nav}), that lookup
 * returns null, CS logs {@code "rocket failed to get server for destination : {}"} and sets the
 * status to {@code ON_FINAL} — the rocket stops thrusting and falls back down. The real run log
 * confirms exactly this for {@code unlimitedspace:moon/system_0170_planet_01_moon_03/orbit}.
 *
 * <p>This watchdog closes the gap for EVERY flight path: while a rocket with a non-null
 * destination exists, it lazily materialises the destination world through the SAME
 * {@link DynamicPlanetWorldManager} seam the admin {@code /nav} path uses (which also guarantees
 * the seed-aware CS travel entry via {@link ProceduralCsRuntime#ensureSystem}). Because the
 * materialisation happens within a few ticks of launch — long before the rocket crosses Y=300 —
 * {@code tickDimensionChangeLogic} finds the level and the transition completes normally.
 *
 * <p>Pure read/watch instrumentation over public CS API; it only ever *creates the missing
 * destination world*, never modifies the rocket or flight state.
 */
@EventBusSubscriber(modid = UnlimitedSpace.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RocketDestinationWorldWatchdog {

    private static final Logger LOGGER = LogManager.getLogger();

    // Deterministic RL paths (see PlanetWorldBinding/MoonWorldBinding/AsteroidWorldBinding/StarWorldBinding):
    //   unlimitedspace:planet/system_%04d_planet_%02d/<surface|orbit>
    //   unlimitedspace:moon/system_%04d_planet_%02d_moon_%02d/<surface|orbit>
    //   unlimitedspace:asteroid/system_%04d_asteroid_%02d
    //   unlimitedspace:star/system_%04d/orbit
    private static final Pattern PLANET_RL =
            Pattern.compile("^planet/system_(\\d{4})_planet_(\\d{2})/(surface|orbit)$");
    private static final Pattern MOON_RL =
            Pattern.compile("^moon/system_(\\d{4})_planet_(\\d{2})_moon_(\\d{2})/(surface|orbit)$");
    private static final Pattern ASTEROID_RL =
            Pattern.compile("^asteroid/system_(\\d{4})_asteroid_(\\d{2})$");
    private static final Pattern STAR_RL =
            Pattern.compile("^star/system_(\\d{4})/(surface|orbit)$");

    private RocketDestinationWorldWatchdog() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        // Scan every 5 ticks: materialising the destination world early is time-critical only
        // relative to the hundreds of ticks the rocket needs to climb to Y=300.
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            var whole = new net.minecraft.world.phys.AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            List<RocketContraptionEntity> rockets =
                    level.getEntitiesOfClass(RocketContraptionEntity.class, whole, rocket -> rocket.isAlive());
            for (RocketContraptionEntity rocket : rockets) {
                ensureDestinationWorld(server, rocket);
            }
        }
    }

    private static void ensureDestinationWorld(MinecraftServer server, RocketContraptionEntity rocket) {
        ResourceLocation destination = rocket.destination;
        if (destination == null) {
            // R14.6.6: a Creating Space UI/schedule flight to a NEVER-touched out-of-scope system stalls
            // inside RocketScheduleRuntime.startCurrentInstruction() because cost(currentWorld,dest) == -1,
            // which returns null BEFORE startNavigation() is ever reached, so rocket.destination stays null
            // for the whole flight. Derive the intended destination from the rocket's schedule so the
            // system metadata + route cost + world can still be prepared before the rocket reaches Y=300.
            destination = scheduledDestination(rocket);
            if (destination == null) {
                return;
            }
        }
        if (!UnlimitedSpace.MODID.equals(destination.getNamespace())) {
            return; // official CS/vanilla destination — not ours to materialise
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, destination);
        if (server.getLevel(key) != null) {
            return; // world already exists — nothing to do
        }
        // R14.6.5: also guarantee the (origin -> destination) cost route (route-scoped rebuild, a few ms,
        // NEVER the full O(V^2) all-pairs rebuild) so trajectory re-calculation after materialisation has
        // a valid cost. The /nav launch path additionally runs ensureCostRoute before the launch packet.
        try {
            ResourceLocation origin = rocket.level().dimension().location();
            ProceduralCsRuntime.ensureCostRoute(server, origin, destination);
        } catch (Throwable t) {
            LOGGER.warn("[R14.6.5] RocketDestinationWorldWatchdog: ensureCostRoute failed for destination={}", destination, t);
        }
        String path = destination.getPath();

        Matcher planet = PLANET_RL.matcher(path);
        if (planet.matches()) {
            int sys = Integer.parseInt(planet.group(1));
            int orbitIndex = Integer.parseInt(planet.group(2));
            boolean isOrbit = "orbit".equals(planet.group(3));
            ensureSystem(server, sys);
            PlanetId id = PlanetId.of(StarSystemId.of(sys), orbitIndex);
            Optional<ServerLevel> created = isOrbit
                    ? DynamicPlanetWorldManager.ensurePlanetOrbit(server, id)
                    : DynamicPlanetWorldManager.ensurePlanetSurface(server, id);
            logMaterialised(rocket, destination, created);
            return;
        }

        Matcher moon = MOON_RL.matcher(path);
        if (moon.matches()) {
            int sys = Integer.parseInt(moon.group(1));
            int planetIndex = Integer.parseInt(moon.group(2));
            int moonIndex = Integer.parseInt(moon.group(3));
            boolean isOrbit = "orbit".equals(moon.group(4));
            ensureSystem(server, sys);
            MoonId id = MoonId.of(PlanetId.of(StarSystemId.of(sys), planetIndex), moonIndex);
            Optional<ServerLevel> created = isOrbit
                    ? DynamicPlanetWorldManager.ensureMoonOrbit(server, id)
                    : DynamicPlanetWorldManager.ensureMoonSurface(server, id);
            logMaterialised(rocket, destination, created);
            return;
        }

        Matcher asteroid = ASTEROID_RL.matcher(path);
        if (asteroid.matches()) {
            int sys = Integer.parseInt(asteroid.group(1));
            int clusterIndex = Integer.parseInt(asteroid.group(2));
            ensureSystem(server, sys);
            AsteroidClusterId id = AsteroidClusterId.of(StarSystemId.of(sys), clusterIndex);
            Optional<ServerLevel> created = DynamicPlanetWorldManager.ensureAsteroidCluster(server, id);
            logMaterialised(rocket, destination, created);
            return;
        }

        Matcher star = STAR_RL.matcher(path);
        if (star.matches()) {
            int sys = Integer.parseInt(star.group(1));
            boolean surface = "surface".equals(star.group(2));
            ensureSystem(server, sys);
            Optional<ServerLevel> created = surface
                    ? DynamicPlanetWorldManager.ensureStarSurface(server, StarSystemId.of(sys))
                    : DynamicPlanetWorldManager.ensureStarOrbit(server, StarSystemId.of(sys));
            logMaterialised(rocket, destination, created);
        }
    }

    /**
     * R14.6.6: read the intended destination from a rocket's Creating Space schedule, if any. A CS-UI
     * schedule flight sets {@code schedule} but may never reach {@code startNavigation}, so
     * {@code rocket.destination} can remain null. We scan the current and subsequent entries for the
     * first {@link DestinationInstruction} (public CS API) and return its destination. This is read-only
     * over the public {@code rocket.schedule}/{@code getSchedule()}/{@code entries} surface.
     */
    private static ResourceLocation scheduledDestination(RocketContraptionEntity rocket) {
        try {
            var runtime = rocket.schedule;
            if (runtime == null) {
                return null;
            }
            var schedule = runtime.getSchedule();
            if (schedule == null || schedule.entries == null) {
                return null;
            }
            int from = Math.max(0, runtime.currentEntry);
            int size = schedule.entries.size();
            for (int i = 0; i < size; i++) {
                int idx = (from + i) % size;
                var entry = schedule.entries.get(idx);
                if (entry != null && entry.instruction instanceof DestinationInstruction di) {
                    return di.getDestination();
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[R14.6.6] RocketDestinationWorldWatchdog: could not read scheduled destination", t);
        }
        return null;
    }

    /** Guarantee the seed-aware CS metadata (and travel entry) for the destination's system. */
    private static void ensureSystem(MinecraftServer server, int systemIndex) {
        if (!ProceduralCsRuntime.ensureSystem(server, systemIndex)) {
            LOGGER.error("[R14.6.4] RocketDestinationWorldWatchdog: could not generate CS metadata for system={}",
                    systemIndex);
        }
    }

    private static void logMaterialised(RocketContraptionEntity rocket, ResourceLocation destination,
                                        Optional<ServerLevel> created) {
        if (created.isPresent()) {
            ServerLevel lvl = created.get();
            LOGGER.info("[R14.6.4] ROCKET_DESTINATION_WORLD_READY: rocket={} destination={} generator={}",
                    rocket.getUUID(), destination,
                    lvl.getChunkSource().getGenerator().getClass().getSimpleName());
        } else {
            LOGGER.error("[R14.6.4] ROCKET_DESTINATION_WORLD_FAILED: rocket={} destination={}",
                    rocket.getUUID(), destination);
        }
    }
}
