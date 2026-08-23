package com.modscreating.unlimitedspace.client.nav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.modscreating.unlimitedspace.core.nav.BookmarkStore;
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
    public static String lastDestinationRl = "";
    public static int lastCost = -1;

    private static BookmarkStore store = new BookmarkStore();

    private R15NavClient() {}

    // ---- lifecycle ----

    /** Called from the S->C open-screen packet handler (server-authoritative entry point). */
    public static void openNavigationScreen(long seed, int currentSystem, long blockPos, int rocketId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ensureModel(seed);
            currentSystemIndex = currentSystem;
            thisBlockPos = blockPos;
            hasBoundBlock = blockPos != Long.MIN_VALUE;
            boundRocketId = rocketId;
            load();
            if (selectedSystem < 0 && currentSystem >= 0) {
                select(currentSystem, 0, 0);
            }
            requestSnapshot();
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

    public static synchronized void ensureModel(long seed) {
        if (mapModel == null || worldSeed != seed) {
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
    }

    public static int selectedSystem() { return selectedSystem; }
    public static int selectedObject() { return selectedObject; }
    public static int selectedDestination() { return selectedDestination; }

    /** Map -> Rocket integration: SET DESTINATION populates the ROCKET tab target. */
    public static void setDestination(int system, int object, int destination) {
        destSystem = system;
        destObject = object;
        destDestination = destination;
        lastStatus = "";
        lastMessage = "";
    }

    public static boolean hasDestination() { return destSystem >= 0; }
    public static int destSystem() { return destSystem; }
    public static int destObject() { return destObject; }
    public static int destDestination() { return destDestination; }

    public static BookmarkStore store() { return store; }

    // ---- server responses ----

    public static void onResponse(int kind, String status, String message, String rl, int cost) {
        lastStatus = status;
        lastMessage = message;
        lastDestinationRl = rl;
        lastCost = cost;
        if (kind == 0 && ("TRAVEL_STARTED".equals(status) || "OK_READY".equals(status))) {
            store.addRecent(destSystem);
            save();
        }
    }

    // ---- persistence (small identity data only) ----

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("unlimitedspace_nav.json");
    }

    public static synchronized void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Persist p = new Persist();
            p.data = store.serialize();
            p.lastSystem = selectedSystem;
            p.lastObject = selectedObject;
            p.lastDestination = selectedDestination;
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
                selectedSystem = p.lastSystem;
                selectedObject = p.lastObject;
                selectedDestination = p.lastDestination;
            }
        } catch (Exception e) {
            UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15] failed to load nav state", e);
        }
    }

    /** JSON shape on disk — deliberately tiny. */
    private static class Persist {
        String data = "";
        int lastSystem = -1;
        int lastObject = -1;
        int lastDestination = -1;
    }
}
