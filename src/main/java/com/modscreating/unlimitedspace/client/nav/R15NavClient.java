package com.modscreating.unlimitedspace.client.nav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.nav.BookmarkStore;
import com.modscreating.unlimitedspace.core.nav.PlayerStats;
import com.modscreating.unlimitedspace.core.nav.SystemVisibility;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * R15 client state for the Rocket Control navigation UI: the canonical galaxy map model
 * (built once from the authoritative world seed), the current selection/destination,
 * recents and bookmarks (persisted to {@code config/unlimitedspace_nav.json}).
 * No giant renderer state is persisted.
 */
@OnlyIn(Dist.CLIENT)
public final class R15NavClient {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile GalaxyMapModel mapModel;
    private static long worldSeed;
    private static int currentSystemIndex = -1;

    /** Current selection: system index (-1 none), canonical object index, destination index. */
    private static int selectedSystem = -1;
    private static int selectedObject = -1;
    private static int selectedDestination = -1;

    /** Destination actually set on the rocket panel (triple; -1 when unset). */
    private static int destSystem = -1;
    private static int destObject = -1;
    private static int destDestination = -1;

    /** Last authoritative server response (travel result / status report). */
    public static String lastStatus = "";
    public static String lastMessage = "";
    /**
     * R23 FIX: message carried by the LAST kind==0 LAUNCH response. Previously the launch
     * response message was dropped entirely (only kind==1 status polls updated
     * {@link #lastMessage}), so a failed launch showed the STALE route text of the last
     * status poll instead - the confusing "The rocket failed to launch: ROUTE READY".
     */
    public static String lastLaunchMessage = "";
    public static String lastDestinationRl = "";
    public static int lastCost = -1;
    /** Kind of the LAST server response: 0 = launch/travel, 1 = status poll. */
    public static int lastKind;

    private static BookmarkStore store = new BookmarkStore();

    private static long storeSeed = Long.MIN_VALUE;

    private R15NavClient() {}

    // ---- lifecycle ----

    /** Called from the S->C open-screen packet handler (server-authoritative entry point). */
    public static void openNavigationScreen(long seed, int currentSystem, long blockPos, int rocketId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ensureModel(seed);
            // R16 FIX: the nav history is PER-WORLD. When the UI opens for a different
            // world (different seed), wipe the in-memory chain and load that world's
            // own file instead of showing another world's travel history.
            if (storeSeed != seed) {
                storeSeed = seed;
                store = new BookmarkStore();
                currentSystemIndex = -1;
                selectedSystem = -1;
                selectedObject = -1;
                selectedDestination = -1;
                destSystem = -1;
                destObject = -1;
                destDestination = -1;
                lastTab = 0; // R16: a fresh world starts on GALAXY
                lastStatus = "";
                lastMessage = "";
                lastLaunchMessage = "";
                lastDestinationRl = "";
                lastCost = -1;
            }
            currentSystemIndex = currentSystem;
            thisBlockPos = blockPos;
            hasBoundBlock = blockPos != Long.MIN_VALUE;
            boundRocketId = rocketId;
            load();
            // R23.4: a persisted destination triple can reference something that no longer
            // resolves (e.g. a moon index from an older session). The server would reject it
            // with the opaque "Invalid Destination" AFTER the countdown - correct it up-front:
            // fall back to the body surface, or clear the selection entirely.
            if (destSystem >= 0 && destSystem != GalaxyMapModel.SOL_SYSTEM_INDEX
                    && !destinationTripleValid(destSystem, destObject, destDestination)) {
                if (destObject >= 0 && destinationTripleValid(destSystem, destObject, 0)) {
                    destDestination = 0; // fall back to the body surface
                } else {
                    destSystem = -1;
                    destObject = -1;
                    destDestination = -1;
                }
                save();
            }
            if (selectedSystem < 0 && currentSystem >= 0) {
                select(currentSystem, 0, 0);
            }
            // R23.3 FIX ("the ROCKET panel sometimes opens without the OXYGEN/METHANE
            // have/req rows; REFRESH fixes it"): the plain snapshot request and the
            // STATUS request both produce ControlSnapshotPackets, and the server applies
            // them IN ORDER. After ARRIVAL Creating Space clears rocket.destination, so
            // the plain snapshot arrives WITHOUT the flight-requirement extras; with the
            // previous ordering (status first, snapshot last) it OVERWROTE the extras
            // snapshot. Request the snapshot FIRST and the status LAST - exactly the
            // order the working REFRESH button uses - so the extras snapshot always wins.
            requestSnapshot();
            // R15.3: a persisted selection immediately becomes the rocket target, so the
            // ROCKET panel is fully calculated (route/cost/fuel) on first open.
            if (selectedSystem >= 0 && selectedObject >= 0 && selectedDestination >= 0) {
                setDestination(selectedSystem, selectedObject, selectedDestination);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.modscreating.unlimitedspace.nav.R15Packets.StatusRequestPacket(
                                selectedSystem, selectedObject, selectedDestination, boundRocketId));
            }
            mc.setScreen(new RocketControlNavigationScreen());
        });
    }

/** The control block this screen belongs to (pos.asLong()). BlockPos.asLong() is NEGATIVE
  *  for negative X/Z - never test validity by sign; use hasBoundBlock. */
    public static long thisBlockPos = Long.MIN_VALUE;
    public static boolean hasBoundBlock = false;
    /** Assembled-rocket entity binding (post-assembly UI), -1 when block-bound. */
    public static int boundRocketId = -1;

    // ---- R15.1 authoritative rocket-control snapshot ----
    public static boolean rocketAssembled = false;
    public static String rocketStatus = "";
    public static String rocketThrust = "";
    public static String rocketDryMass = "";
    public static String rocketDeltaV = "";

    // R22: fog-of-war visibility state - the radius is mutable so future
    // visibility boosters / server config can raise it at runtime.
    private static final SystemVisibility VISIBILITY = new SystemVisibility();

    /** R22: shared visibility model (radius defaults to 1600 ly). */
    public static SystemVisibility visibility() {
        return VISIBILITY;
    }
    public static String rocketDestination = "";
    public static String assemblyException = "";
    public static int rocketId = -1;
    public static boolean hasSchedule = false;
    public static String scheduleState = "";

    /** Ask the server for the authoritative assembly/rocket snapshot of the bound block. */
    public static void requestSnapshot() {
        sendControlAction(0, "");
    }

    /** Send a server-authoritative control action to the bound block.
     * action: 1 = assemble, 2 = disassemble, 3 = schedule menu, 4 = set destination. */
    public static void sendControlAction(int action, String destination) {
        if (!hasBoundBlock && boundRocketId < 0) {
            UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15.1] control action {} skipped: no bound block pos", action);
            return;
        }
        UnlimitedSpace.LOGGER.info("[unlimitedspace][R15.1] sending control action {} for block {}", action, thisBlockPos);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.modscreating.unlimitedspace.nav.R15Packets.ControlActionPacket(
                        thisBlockPos, boundRocketId, action, destination));
    }

    public static void onSnapshot(boolean assembled, String status, String thrust, String dryMass,
                                  String deltaV, String destination, String exception,
                                  int rId, boolean sched, String schedState) {
        rocketAssembled = assembled;
        rocketStatus = status;
        rocketThrust = thrust;
        rocketDryMass = dryMass;
        rocketDeltaV = deltaV;
        rocketDestination = destination;
        assemblyException = exception;
        rocketId = rId;
        hasSchedule = sched;
        scheduleState = schedState;
    }

    // ---- R15.2 flight-requirement overlay (parsed from extras in the snapshot packet) ----
    public static double reqRequiredFuelKg = 0;
    public static double reqAvailableFuelKg = 0;
    public static double reqFuelShortageKg = 0;
    public static double reqThrustRequired = 0;
    public static double reqThrustAvailable = 0;

    public static void onRequirements(boolean fuelOk, boolean thrustOk, double requiredFuelKg,
                                      double availableFuelKg, double shortageKg,
                                      double thrustRequired, double thrustAvailable) {
        reqRequiredFuelKg = requiredFuelKg;
        reqAvailableFuelKg = availableFuelKg;
        reqFuelShortageKg = shortageKg;
        reqThrustRequired = thrustRequired;
        reqThrustAvailable = thrustAvailable;
    }

    // R15.2.1: consumption rate / trip time / per-propellant breakdown
    public static double reqConsumptionKgS = 0;
    public static double reqTravelSeconds = 0;
    public static String reqPerPropellant = "";

    /** R16: lift-off surcharge (deltaV) - surface/star starts cost extra fuel. */
    public static double reqLaunchSurcharge;
    /** R16: distance surcharge (deltaV) - current system -> destination system. */
    public static double reqDistSurcharge;
    /** R20: distance-only fuel (kg) - how much of the burned fuel comes from the trip length. */
    public static double reqDistFuelKg;

    /** R17: per-fluid fuel balance - "tag=req,have;..." (or "~est" marker). */
    public static String reqFluidBalance = "";

    public static void onLiftOffSurcharge(double deltaV) {
        reqLaunchSurcharge = deltaV;
    }

    public static void onDistanceSurcharge(double deltaV) {
        reqDistSurcharge = deltaV;
    }

    public static void onDistanceFuel(double fuelKg) {
        reqDistFuelKg = fuelKg;
    }

    public static void onFluidBalance(String balance) {
        reqFluidBalance = balance == null ? "" : balance;
    }

    public static void onConsumption(double consumptionKgS, double travelSeconds,
                                     String perPropellant) {
        reqConsumptionKgS = consumptionKgS;
        reqTravelSeconds = travelSeconds;
        reqPerPropellant = perPropellant == null ? "" : perPropellant;
    }

    public static synchronized void ensureModel(long seed) {
        // R16 FIX: build the map ONCE and NEVER rebuild it. The server now always
        // sends the Overworld seed; even if a stray different seed ever arrived,
        // rebuilding here would visually regenerate the whole galaxy mid-session -
        // so the FIRST authoritative seed wins for the lifetime of the client.
        if (mapModel == null) {
            worldSeed = seed;
            mapModel = GalaxyMapModel.from(seed);
        }
    }

    public static GalaxyMapModel model() { return mapModel; }
    public static long worldSeed() { return worldSeed; }
    public static int currentSystemIndex() { return currentSystemIndex; }

    // ---- selection ----

    public static void select(int system, int object, int destination) {
        selectedSystem = system;
        selectedObject = object;
        selectedDestination = destination;
        save(); // R15.2: persist selection immediately so a reopen never loses it
    }

    public static int selectedSystem() { return selectedSystem; }

    /** R16: the tab the UI was on when it was closed (persisted per world). */
    public static int lastTab;
    public static int selectedObject() { return selectedObject; }
    public static int selectedDestination() { return selectedDestination; }

    /** Map -> Rocket integration: SET DESTINATION populates the ROCKET tab target. */
    public static void setDestination(int system, int object, int destination) {
        destSystem = system;
        destObject = object;
        destDestination = destination;
        lastStatus = "";
        lastMessage = "";
        save(); // R15.2: persist the destination triple (drives LAUNCH after reopen)
    }

    public static boolean hasDestination() {
        return destSystem >= 0 || destSystem == GalaxyMapModel.SOL_SYSTEM_INDEX;
    }
    public static int destSystem() { return destSystem; }
    public static int destObject() { return destObject; }
    public static int destDestination() { return destDestination; }

    /**
     * R23.4: client mirror of the server-side {@code DestinationResolver} bounds. The UI can
     * render a plausible target for a triple that the server REJECTS (moon names are a total
     * procedural function, and a persisted/re-focused selection may reference an object or a
     * moon index that does not exist) - which surfaced as the confusing red
     * "The rocket failed to launch: Invalid Destination". Use it to catch a broken triple
     * BEFORE the launch countdown instead of after it.
     */
    public static boolean destinationTripleValid(int system, int object, int destination) {
        if (system < 0 || object < 0 || destination < 0) return false;
        try {
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(worldSeed());
            var systemId = galaxy.systemId(system);
            var starSystem = galaxy.getStarSystem(systemId);
            var objects = starSystem.canonicalCelestialObjects();
            if (object >= objects.size()) return false;
            var obj = objects.get(object);
            switch (obj.kind()) {
                case STAR -> {
                    if (destination > 1) return false; // star: 0 = body, 1 = orbit
                }
                case PLANET -> {
                    if (destination >= 2) {
                        int moonCount = obj.planet().moonCount();
                        if (moonCount <= 0) return false;
                        int moonIndex = (destination % 2 == 0)
                                ? (destination - 2) / 2   // even -> moon surface
                                : (destination - 3) / 2;  // odd  -> moon orbit
                        if (moonIndex < 0 || moonIndex >= moonCount) return false;
                    }
                }
                default -> { } // asteroid field: any destination >= 0 resolves server-side
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }


    public static BookmarkStore store() { return store; }

    // ---- server responses ----

    public static void onResponse(int kind, String status, String message, String rl, int cost) {
        lastKind = kind; // R16: lets the UI distinguish LAUNCH responses from status polls
        lastStatus = status;
        // R23 FIX: remember the REAL launch-response message for the failure toast. It used
        // to be dropped, so updateLaunchToast showed whatever the last STATUS poll had left
        // in lastMessage ("ROUTE READY") as the launch error reason.
        lastLaunchMessage = kind == 0 ? (message == null ? "" : message) : lastLaunchMessage;
        // R16 FIX: after a launch the old requirement numbers belonged to the PRE-launch
        // state; keeping them around mixed with the new "traveling" snapshot distorted
        // every field. Clear the overlay so the panel resets until fresh data arrives.
        if (kind == 0 && ("TRAVEL_STARTED".equals(status) || "OK_READY".equals(status))) {
            reqRequiredFuelKg = 0;
            reqAvailableFuelKg = 0;
            reqFuelShortageKg = 0;
            reqThrustRequired = 0;
            reqThrustAvailable = 0;
            reqConsumptionKgS = 0;
            reqTravelSeconds = 0;
            reqPerPropellant = "";
            reqLaunchSurcharge = 0;
            reqDistSurcharge = 0;
            reqDistFuelKg = 0;
            reqFluidBalance = "";
        }
        // R16 FIX: a LAUNCH response carries no real route info (cost=0, status text).
        // Overwriting COST/ROUTE with that would blank/garbge the ROCKET panel on a
        // failed launch, so keep the previous route values for kind==0 and only let
        // the fresh STATUS poll (kind==1) refresh them.
        if (kind == 1) {
            lastMessage = message;
            lastDestinationRl = rl;
            lastCost = cost;
        }
        if (kind == 0 && ("TRAVEL_STARTED".equals(status) || "OK_READY".equals(status))) {
            store.addRecent(destSystem);
            save();
        }
    }

    /** R22g: lifetime exploration statistics (persisted per world). */
    private static PlayerStats stats = new PlayerStats();

    public static PlayerStats stats() {
        return stats;
    }

    // ---- persistence (small identity data only) ----

    private static Path file() {
        // R16 FIX: PER-WORLD storage. The old single file was shared by every world
        // in the save folder, so each world showed another world's travel history.
        if (storeSeed != Long.MIN_VALUE) {
            return FMLPaths.GAMEDIR.get().resolve("config").resolve(
                    "unlimitedspace_nav_" + Long.toUnsignedString(storeSeed) + ".json");
        }
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("unlimitedspace_nav.json");
    }

    public static synchronized void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Persist p = new Persist();
            p.data = store.serialize();
            p.statsData = stats.serialize();
            p.lastSystem = selectedSystem;
            p.lastObject = selectedObject;
            p.lastDestination = selectedDestination;
        p.lastTab = lastTab; // R16: reopen on the same tab, per world
            p.destSystem = destSystem;
            p.destObject = destObject;
            p.destDestination = destDestination;
            Files.writeString(f, GSON.toJson(p), StandardCharsets.UTF_8);
        } catch (Exception e) {
            UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15] failed to save nav state", e);
        }
    }

    public static synchronized void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Persist p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Persist.class);
            if (p != null) {
                store = BookmarkStore.deserialize(p.data);
                stats = PlayerStats.deserialize(p.statsData);
                selectedSystem = p.lastSystem;
                selectedObject = p.lastObject;
                selectedDestination = p.lastDestination;
                lastTab = p.lastTab; // R16: restore the last active tab
                destSystem = p.destSystem;
                destObject = p.destObject;
                destDestination = p.destDestination;
            }
        } catch (Exception e) {
            UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15] failed to load nav state", e);
        }
    }

    /** JSON shape on disk — deliberately tiny. */
    private static class Persist {
        String data = "";
        String statsData = ""; // R22g: player exploration statistics
        int lastSystem = -1;
        int lastObject = -1;
        int lastDestination = -1;
        int lastTab; // R16: persisted active UI tab
        int destSystem = -1;
        int destObject = -1;
        int destDestination = -1;
    }
}
