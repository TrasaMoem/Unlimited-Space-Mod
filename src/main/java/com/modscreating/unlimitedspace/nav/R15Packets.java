package com.modscreating.unlimitedspace.nav;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * R15 networking for the Rocket Control navigation UI. The server is authoritative: every
 * travel request re-runs the EXACT {@code /unlimitedspace nav} pipeline
 * ({@code AdminNav.resolveAndMap -> ensureSurface -> classify -> attemptTravel}); no client
 * ResourceLocation is trusted and no second navigation implementation exists.
 */
public final class R15Packets {

    private static final Logger LOGGER = LogManager.getLogger();

    /** S->C: open the R15 Navigation Screen. Binds either a control block (blockPos) or an
     *  assembled rocket entity (rocketId >= 0, blockPos = Long.MIN_VALUE). */
    public record OpenScreenPacket(long worldSeed, int currentSystem, long blockPos, int rocketId)
            implements CustomPacketPayload {
        public static final Type<OpenScreenPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_open_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, OpenScreenPacket::worldSeed,
                        ByteBufCodecs.VAR_INT, OpenScreenPacket::currentSystem,
                        ByteBufCodecs.VAR_LONG, OpenScreenPacket::blockPos,
                        ByteBufCodecs.VAR_INT, OpenScreenPacket::rocketId,
                        OpenScreenPacket::new);
        @Override public Type<OpenScreenPacket> type() { return TYPE; }
    }

    /**
     * C->S: rocket-control action (R15.1). Target = control block (blockPos valid) OR
     * assembled rocket entity (rocketId >= 0). action: 0 = STATUS, 1 = ASSEMBLE (block only),
     * 2 = DISASSEMBLE, 3 = SCHEDULE, 4 = SET DESTINATION (block only).
     */
    public record ControlActionPacket(long blockPos, int rocketId, int action, String destination)
            implements CustomPacketPayload {
        public static final Type<ControlActionPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_control_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlActionPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, ControlActionPacket::blockPos,
                        ByteBufCodecs.VAR_INT, ControlActionPacket::rocketId,
                        ByteBufCodecs.VAR_INT, ControlActionPacket::action,
                        ByteBufCodecs.STRING_UTF8, ControlActionPacket::destination,
                        ControlActionPacket::new);
        @Override public Type<ControlActionPacket> type() { return TYPE; }
    }

    /**
     * S->C: authoritative assembly/rocket status snapshot for the ROCKET tab.
     * Fields are packed as one '\u0001'-separated string (StreamCodec.composite supports
     * max 6 entries): assembled, status, thrust, dryMass, deltaV, destination, exception,
     * rocketId, hasSchedule, scheduleState.
     */
    public record ControlSnapshotPacket(String data)
            implements CustomPacketPayload {
        public static final char SEP = '\u0001';
        public static final Type<ControlSnapshotPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_control_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlSnapshotPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ControlSnapshotPacket::data,
                        ControlSnapshotPacket::new);
        @Override public Type<ControlSnapshotPacket> type() { return TYPE; }

        public static String pack(boolean assembled, String status, String thrust, String dryMass,
                                  String deltaV, String destination, String exception,
                                  int rocketId, boolean hasSchedule, String scheduleState) {
            String[] f = {String.valueOf(assembled), nz(status), nz(thrust), nz(dryMass), nz(deltaV),
                    nz(destination), nz(exception).replace(SEP, ' '), String.valueOf(rocketId),
                    String.valueOf(hasSchedule), nz(scheduleState)};
            return String.join(String.valueOf(SEP), f);
        }

        public static String[] unpack(String data) {
            return data.split("\u0001", -1);
        }

        /** Append flight-requirement fields (indexes 10..) to an existing packed snapshot. */
        public static String appendRequirements(String data,
                com.modscreating.unlimitedspace.nav.RocketFlightPlanner.Requirements r) {
            if (r == null) return data;
            String[] extra = {
                    String.format(java.util.Locale.ROOT, "%.1f", r.requiredFuelKg()),
                    String.format(java.util.Locale.ROOT, "%.1f", r.availableFuelKg()),
                    String.format(java.util.Locale.ROOT, "%.1f", r.fuelShortageKg()),
                    String.format(java.util.Locale.ROOT, "%.1f", r.thrustRequired()),
                    String.format(java.util.Locale.ROOT, "%.1f", r.thrustAvailable()),
                    String.valueOf(r.fuelOk()),
                    String.valueOf(r.thrustOk()),
                    String.format(java.util.Locale.ROOT, "%.2f", r.consumptionKgS()),
                    String.format(java.util.Locale.ROOT, "%.0f", r.travelSeconds()),
                    r.perPropellant() == null ? "" : r.perPropellant().replace(SEP, ' ')
            };
            return data + SEP + String.join(String.valueOf(SEP), extra);
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }

    /** C->S: launch/travel request for the selected (system, object, destination) triple. */
    public record TravelRequestPacket(int system, int object, int destination)
            implements CustomPacketPayload {
        public static final Type<TravelRequestPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_travel_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TravelRequestPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, TravelRequestPacket::system,
                        ByteBufCodecs.VAR_INT, TravelRequestPacket::object,
                        ByteBufCodecs.VAR_INT, TravelRequestPacket::destination,
                        TravelRequestPacket::new);
        @Override public Type<TravelRequestPacket> type() { return TYPE; }
    }

    /** C->S: ask for the authoritative rocket/route/cost state of a destination. */
    public record StatusRequestPacket(int system, int object, int destination, int rocketId)
            implements CustomPacketPayload {
        public static final Type<StatusRequestPacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_status_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusRequestPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, StatusRequestPacket::system,
                        ByteBufCodecs.VAR_INT, StatusRequestPacket::object,
                        ByteBufCodecs.VAR_INT, StatusRequestPacket::destination,
                        ByteBufCodecs.VAR_INT, StatusRequestPacket::rocketId,
                        StatusRequestPacket::new);
        @Override public Type<StatusRequestPacket> type() { return TYPE; }
    }

    /** S->C authoritative result. kind: 0 = travel result, 1 = status report. */
    public record ResponsePacket(int kind, String status, String message,
                                 String resourceLocation, int cost)
            implements CustomPacketPayload {
        public static final Type<ResponsePacket> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UnlimitedSpace.MODID, "r15_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, ResponsePacket::kind,
                        ByteBufCodecs.STRING_UTF8, ResponsePacket::status,
                        ByteBufCodecs.STRING_UTF8, ResponsePacket::message,
                        ByteBufCodecs.STRING_UTF8, ResponsePacket::resourceLocation,
                        ByteBufCodecs.VAR_INT, ResponsePacket::cost,
                        ResponsePacket::new);
        @Override public Type<ResponsePacket> type() { return TYPE; }
    }

    /** Register on the MOD bus next to {@link com.modscreating.unlimitedspace.cs.ProceduralCsNetworking}. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UnlimitedSpace.MODID).versioned("1");
        registrar.playToClient(OpenScreenPacket.TYPE, OpenScreenPacket.STREAM_CODEC,
                (payload, context) -> com.modscreating.unlimitedspace.client.nav.R15NavClient
                        .openNavigationScreen(payload.worldSeed(), payload.currentSystem(), payload.blockPos(), payload.rocketId()));
        registrar.playToClient(ResponsePacket.TYPE, ResponsePacket.STREAM_CODEC,
                (payload, context) -> com.modscreating.unlimitedspace.client.nav.R15NavClient
                        .onResponse(payload.kind(), payload.status(), payload.message(),
                                payload.resourceLocation(), payload.cost()));
        registrar.playToClient(ControlSnapshotPacket.TYPE, ControlSnapshotPacket.STREAM_CODEC,
                (payload, context) -> {
                    String[] f = com.modscreating.unlimitedspace.nav.R15Packets
                            .ControlSnapshotPacket.unpack(payload.data());
                    com.modscreating.unlimitedspace.client.nav.R15NavClient.onSnapshot(
                            Boolean.parseBoolean(f[0]), f[1], f[2], f[3], f[4], f[5], f[6],
                            Integer.parseInt(f[7]), Boolean.parseBoolean(f[8]), f.length > 9 ? f[9] : "");
                    // R15.2: optional flight requirements (indexes 10..16)
                    // requiredFuel(10), availableFuel(11), shortage(12), thrustReq(13),
                    // thrustAvail(14), fuelOk(15), thrustOk(16), consumptionKgS(17),
                    // travelSeconds(18), perPropellant(19)
                    if (f.length >= 20) {
                        com.modscreating.unlimitedspace.client.nav.R15NavClient.onRequirements(
                                Boolean.parseBoolean(f[15]), Boolean.parseBoolean(f[16]),
                                parseDouble(f[10]), parseDouble(f[11]), parseDouble(f[12]),
                                parseDouble(f[13]), parseDouble(f[14]));
                        com.modscreating.unlimitedspace.client.nav.R15NavClient.onConsumption(
                                parseDouble(f[17]), parseDouble(f[18]), f[19]);
                    }
                });
        registrar.playToServer(ControlActionPacket.TYPE, ControlActionPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) handleControlAction(sp, payload);
                }));
        registrar.playToServer(TravelRequestPacket.TYPE, TravelRequestPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) handleTravel(sp, payload);
                }));
        registrar.playToServer(StatusRequestPacket.TYPE, StatusRequestPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) handleStatus(sp, payload);
                }));
        LOGGER.info("[unlimitedspace][R15] registered Rocket Control navigation payloads");
    }

    /** Open the UI bound to a control block (pre-assembly). */
    public static void openScreen(ServerPlayer player, long seed, net.minecraft.core.BlockPos pos) {
        PacketDistributor.sendToPlayer(player,
                new OpenScreenPacket(seed, currentSystemOf(player), pos.asLong(), -1));
    }

    /** Open the UI bound to an ALREADY ASSEMBLED rocket entity (post-assembly). */
    public static void openScreen(ServerPlayer player, long seed, long blockPosSentinel, int rocketId) {
        PacketDistributor.sendToPlayer(player,
                new OpenScreenPacket(seed, currentSystemOf(player), blockPosSentinel, rocketId));
    }

    /**
     * R15.1 server-authoritative rocket control actions. Everything operates on the REAL
     * Creating Space infrastructure (our BE's CS-faithful assembly / CS packets).
     */
    private static void handleControlAction(ServerPlayer player, ControlActionPacket packet) {
        var level = player.level();
        com.modscreating.unlimitedspace.block.USRocketControlBlockEntity be = null;
        if (packet.blockPos() != Long.MIN_VALUE) {
            if (!(level.getBlockEntity(net.minecraft.core.BlockPos.of(packet.blockPos()))
                    instanceof com.modscreating.unlimitedspace.block.USRocketControlBlockEntity found)) {
                UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15.1] control action ignored: no Rocket Control BlockEntity at {}", net.minecraft.core.BlockPos.of(packet.blockPos()));
                return;
            }
            be = found;
        }
        // Resolve the REAL rocket: either bound by entity id (post-assembly UI)
        // or via the control block (pre-assembly / after re-assembly).
        RocketContraptionEntity rocket;
        if (packet.rocketId() >= 0) {
            rocket = level.getEntity(packet.rocketId())
                    instanceof RocketContraptionEntity r ? r : null;
        } else {
            rocket = be != null ? be.getRocket() : null;
        }
        UnlimitedSpace.LOGGER.info("[unlimitedspace][R15.1] control action {} received (block={}, rocketId={}, resolvedRocket={})",
                packet.action(), packet.blockPos(), packet.rocketId(),
                rocket == null ? "none" : rocket.getId());
        switch (packet.action()) {
            case 1 -> { // ASSEMBLE - block mode only; run the real CS assembly SYNCHRONOUSLY
                if (be == null) break;
                ResourceLocation dest = packet.destination() == null || packet.destination().isBlank()
                        ? null : ResourceLocation.tryParse(packet.destination());
                be.queueAssembly(dest);
                be.assembleNow();
            }
            case 2 -> { // DISASSEMBLE - reuse the official CS disassemble packet
                if (rocket != null) {
                    new com.rae.creatingspace.content.rocket.network.RocketContraptionDisassemblePacket(
                            rocket.getId()).handle(player);
                }
            }
            case 3 -> { // SCHEDULE - opens the REAL CS schedule menu of the rocket entity
                if (rocket != null && rocket.isAlive()) {
                    // ScheduleMakingMenu expects the rocket entity id as extra screen data
                    // (same as CS RocketControlInteraction); without it createOnClient NPEs.
                    player.openMenu(rocket, buf -> buf.writeVarInt(rocket.getId()));
                }
            }
            case 4 -> { // SET DESTINATION on the block (used at next assembly) - block mode only
                if (be == null) break;
                ResourceLocation dest = packet.destination() == null || packet.destination().isBlank()
                        ? null : ResourceLocation.tryParse(packet.destination());
                be.setDestination(dest);
            }
            default -> { } // 0 = STATUS
        }
        // Authoritative reply: entity snapshot when bound to a rocket, else the BE snapshot.
        com.modscreating.unlimitedspace.block.USRocketControlBlockEntity.Snapshot snap =
                packet.rocketId() >= 0
                        ? com.modscreating.unlimitedspace.block.USRocketControlBlockEntity.of(rocket)
                        : be != null ? be.snapshot()
                          : com.modscreating.unlimitedspace.block.USRocketControlBlockEntity.none();
        String data = ControlSnapshotPacket.pack(
                snap.assembled(), snap.status(), snap.thrust(), snap.dryMass(), snap.deltaV(),
                snap.destination(), snap.exception(), snap.rocketId(),
                snap.hasSchedule(), snap.scheduleState());
        // R15.2.1: ALWAYS attach flight requirements (fuel/thrust/rate/time) so the panel
        // shows them immediately on open — not only after CONNECT/STATUS.
        RocketContraptionEntity reqRocket = rocket != null
                ? rocket : (be != null ? be.getRocket() : null);
        if (reqRocket != null) {
            ResourceLocation destRL = reqRocket.destination != null
                    ? reqRocket.destination : reqRocket.level().dimension().location();
            try {
                var req = RocketFlightPlanner.compute(reqRocket, destRL);
                data = ControlSnapshotPacket.appendRequirements(data, req);
            } catch (Throwable t) {
                UnlimitedSpace.LOGGER.warn("[US][R15.2] requirement computation failed", t);
            }
        }
        PacketDistributor.sendToPlayer(player, new ControlSnapshotPacket(data));
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private static ControlSnapshotPacket toSnapshot(
            com.modscreating.unlimitedspace.block.USRocketControlBlockEntity.Snapshot s) {
        return new ControlSnapshotPacket(ControlSnapshotPacket.pack(
                s.assembled(), s.status(), s.thrust(), s.dryMass(), s.deltaV(),
                s.destination(), s.exception(), s.rocketId(), s.hasSchedule(), s.scheduleState()));
    }

    /**
     * Best-effort authoritative "which system is the player in": exact when inside the space
     * dimension via the canonical layout lookup; inside DYNAMIC procedural dimensions
     * (planet/moon/asteroid/star surfaces and orbits) resolved from the level key
     * ("system_<index>"); -1 (= unknown) otherwise.
     */
    private static int currentSystemOf(ServerPlayer player) {
        try {
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level
                    && level.dimension().location().equals(
                    com.modscreating.unlimitedspace.worldgen.space.SpaceDimensionBinding.location())
                    && level.getChunkSource().getGenerator()
                        instanceof com.modscreating.unlimitedspace.worldgen.space.SpaceChunkGenerator gen) {
                var c = com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate
                        .fromBlock((long) player.getBlockX(), (long) player.getBlockZ());
                var res = gen.layout().lookup(c);
                if (res.system() != null) {
                    return res.system().id().index();
                }
            }
            // R16 FIX: dynamic procedural dimensions carry "system_<index>" in their key.
            // Without this fallback the UI always fell back to the Sol anchor, so
            // "Dist. surcharge" was measured from the Solar system even when the player
            // stood on a planet of a distant system.
            int parsed = GalaxyMapModel.systemIndexFromKey(
                    player.level().dimension().location().toString());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * The ONE server-authoritative validation + launch path for the GUI  - identical to
     * {@code GalaxyCommands.runNav} (the {@code /unlimitedspace nav} command).
     */
    private static void handleTravel(ServerPlayer player, TravelRequestPacket packet) {
        MinecraftServer server = player.server;
        NavResult nav;
        if (packet.system() == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            // R16: Sol = the REAL Creating Space home system. Map object/destination onto the
            // actual CS dimensions (Overworld, earth/moon/mars orbits, the_moon, mars, venus);
            // unreachable bodies fall back to the Earth surface. Skips procedural
            // resolve/ensure/classify and goes through the SAME validated launch path below.
            String rl = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                    .destinationRl(packet.object(), packet.destination());
            nav = NavResult.resolved(NavStatus.OK_READY,
                    "Sol - " + com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                            .destinationLabel(packet.object(), packet.destination()),
                    null, ResourceLocation.parse(rl));
        } else {
            Galaxy galaxy = Galaxy.from(server.overworld().getSeed());
            nav = AdminNav.resolveAndMap(galaxy, packet.system(), packet.object(), packet.destination());
            nav = AdminNav.ensureSurface(server, nav);
            nav = AdminNav.classify(nav, CsCatalog.of(server));
        }
        // R15.1: launch requires a REAL, assembled CS rocket that is not already traveling.
        if (nav.ok()) {
            RocketContraptionEntity rocket = CsTravelBridge.findRocket(player);
            if (rocket == null) {
                nav = NavResult.fail(NavStatus.NO_ROCKET,
                        "No assembled Creating Space rocket near the player. Assemble one first.");
            } else {
                ResourceLocation destRL = nav.resourceLocation();
                if (destRL != null) {
                    var req = RocketFlightPlanner.compute(rocket, destRL);
                    if (!req.thrustOk()) {
                        nav = NavResult.fail(NavStatus.TRAVEL_BLOCKED,
                                String.format(java.util.Locale.ROOT,
                                        "Insufficient thrust: need %.0f N, have %.0f N. Add engines or reduce mass.",
                                        req.thrustRequired(), req.thrustAvailable()));
                    } else if (!req.fuelOk()) {
                        nav = NavResult.fail(NavStatus.TRAVEL_BLOCKED,
                                String.format(java.util.Locale.ROOT,
                                        "Not enough fuel: need %.1f kg, have %.1f kg (short %.1f kg).",
                                        req.requiredFuelKg(), req.availableFuelKg(), req.fuelShortageKg()));
                    }
                }
                try {
                    var status = rocket.getEntityData()
                            .get(RocketContraptionEntity.STATUS_DATA_ACCESSOR);
                    if (status == RocketContraptionEntity.RocketStatus.TRAVELING) {
                        nav = NavResult.fail(NavStatus.TRAVEL_BLOCKED,
                                "Rocket is already traveling.");
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (nav.ok()) {
            LOGGER.info("[unlimitedspace][R15] before launch: rl={} status={}",
                    nav.resourceLocation(), nav.status());
            nav = AdminNav.attemptTravel(player, nav);
        }
        PacketDistributor.sendToPlayer(player, new ResponsePacket(0,
                nav.status().name(),
                nav.message() == null ? "" : nav.message(),
                nav.resourceLocation() == null ? "" : nav.resourceLocation().toString(),
                0));
    }

    /** Authoritative route/cost/rocket report for the ROCKET tab (values from CS itself). */
    private static void handleStatus(ServerPlayer player, StatusRequestPacket packet) {
        MinecraftServer server = player.server;
        NavResult nav;
        if (packet.system() == GalaxyMapModel.SOL_SYSTEM_INDEX) {
            String rl = com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                    .destinationRl(packet.object(), packet.destination());
            nav = NavResult.resolved(NavStatus.OK_READY,
                    "Sol - " + com.modscreating.unlimitedspace.core.galaxy.SolSystemCatalog
                            .destinationLabel(packet.object(), packet.destination()),
                    null, ResourceLocation.parse(rl));
        } else {
            Galaxy galaxy = Galaxy.from(server.overworld().getSeed());
            nav = AdminNav.resolveAndMap(galaxy, packet.system(), packet.object(), packet.destination());
        }
        String status;
        String message;
        int cost = -1;
        // R15.2: prefer the entity-bound rocket (post-assembly UI) so requirements work
        // even when the player is not standing next to it.
        RocketContraptionEntity rocket = null;
        if (packet.rocketId() >= 0 && player.level().getEntity(packet.rocketId())
                instanceof RocketContraptionEntity bound) {
            rocket = bound;
        }
        if (rocket == null) {
            rocket = CsTravelBridge.findRocket(player);
        }
        if (!nav.isError()) {
            boolean hasRocket = rocket != null;
            status = hasRocket ? "CONNECTED" : "NO_ROCKET";
            ResourceLocation origin = player.level().dimension().location();
            ResourceLocation dest = nav.resourceLocation();
            if (dest != null) {
                try {
                    boolean ready = com.modscreating.unlimitedspace.cs.ProceduralCsRuntime
                            .ensureCostRoute(server, origin, dest);
                    cost = com.rae.creatingspace.content.planets.CSDimensionUtil.cost(origin, dest);
                    message = ready ? "ROUTE READY" : "ROUTE UNAVAILABLE";
                } catch (Throwable t) {
                    // route build failed - still report cost/requirements below, never stall the UI
                    try {
                        cost = com.rae.creatingspace.content.planets.CSDimensionUtil.cost(origin, dest);
                    } catch (Throwable ignored) { }
                    message = "ROUTE ERROR: " + t.getMessage();
                }
                // R15.3 fix: requirements are sent on EVERY status request, in their OWN
                // try-block. Previously they lived inside the ensureCostRoute try, so any
                // route-build exception silently skipped them and the panel kept showing
                // the PREVIOUS moon's numbers (or nothing at all).
                if (hasRocket) {
                    sendRequirements(player, rocket, dest);
                }
            } else {
                message = "";
            }
        } else {
            status = "INVALID";
            message = nav.message();
        }
        final String fStatus = status;
        final String fMessage = message;
        final int fCost = cost;
        PacketDistributor.sendToPlayer(player, new ResponsePacket(1,
                fStatus,
                fMessage,
                nav.resourceLocation() == null ? "" : nav.resourceLocation().toString(),
                fCost));
    }

    /** Compute flight requirements for a destination and push them via the snapshot packet. */
    private static void sendRequirements(ServerPlayer player, RocketContraptionEntity rocket,
                                         ResourceLocation destRL) {
        try {
            var req = RocketFlightPlanner.compute(rocket, destRL);
            var snap = com.modscreating.unlimitedspace.block.USRocketControlBlockEntity.of(rocket);
            String data = ControlSnapshotPacket.pack(
                    snap.assembled(), snap.status(), snap.thrust(), snap.dryMass(), snap.deltaV(),
                    snap.destination(), snap.exception(), snap.rocketId(),
                    snap.hasSchedule(), snap.scheduleState());
            data = ControlSnapshotPacket.appendRequirements(data, req);
            PacketDistributor.sendToPlayer(player, new ControlSnapshotPacket(data));
        } catch (Throwable t) {
            UnlimitedSpace.LOGGER.warn("[US][R15.2] sendRequirements failed", t);
        }
    }
}

