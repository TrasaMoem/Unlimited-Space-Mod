# PHASE R14.6.3 - FINAL REPORT: REMOTE CLIENT END-TO-END CREATING SPACE FLIGHT AUDIT

STATUS: NOT GREEN. Source-level ownership audit + minimal client synchronization implemented + build +
333 tests + dedicated-server boot verified. PHYSICALLY CONFIRMED = NO (no interactive remote client
available in this headless environment).

## RESULT: CATEGORY B - CLIENT SYNCHRONIZATION BUG

The remote client requires seed-aware procedural CS values (gravity) for its local player physics and
only ever sees the frozen (pre-seed) registry via the vanilla sync. This DOES affect actual gameplay.
The minimal synchronization fix was implemented (server-to-client packet).

## CLIENT/SERVER OWNERSHIP (traced from Creating Space 1.7.18 bytecode)

Operation | Side
---|---
Rocket launch (RocketContraptionLaunchPacket.handle) | SERVER (sets rocket.destination, calls handelTrajectoryCalculation; gated `!level.isClientSide` in tick)
Trajectory calculation (handelTrajectoryCalculation) | SERVER (reads cost(origin,dest), mass/fuel/deltaV)
Destination resolution | SERVER (rocket.destination set by the launch packet)
Cost calculation | SERVER (handelTrajectoryCalculation) + CLIENT (RocketScheduleRuntime.startCurrentInstruction -> cost, cosmetic schedule path)
Gravity(destination) for rocket acceleration | SERVER (tickConsumptionAndSpeed returns early on client)
Gravity for PLAYER physics | BOTH (LivingEntityMixin.getDefaultGravity) - SERVER authoritative, CLIENT local prediction
ArrivalHeight(destination) | SERVER ONLY (CustomTeleporter.getTransition; no client call site exists)
isOrbit(destination) | SERVER (changeDimension) + CLIENT (CSEventHandler branch is server-gated; Orbit GUI icon)
DynamicDimensions world creation | SERVER
Dimension change | SERVER (tickDimensionChangeLogic is inside the !isClientSide branch)
Player teleport | SERVER
Client visual flight | CLIENT - driven ONLY by RocketContraptionUpdatePacket(entityID, coord, speed); the client never recomputes gravity/cost/arrival for the flight

## CSDimensionUtil CLIENT USAGE (every call site)

Call | Client call site | Gameplay impact
---|---|---
gravity(RL) | LivingEntityMixin.getDefaultGravity | **YES - local player movement in procedural dimensions**
gravity(RL) | (nothing else client-side for flight) | - (server tickConsumptionAndSpeed is client-gated off)
cost(RL,RL) | RocketScheduleRuntime.startCurrentInstruction | Cosmetic (client schedule path; null path just returns, no crash)
getPlanets() | ScheduleMakingScreen / DestinationInstruction | Cosmetic (UI destination list)
isOrbit(RL) | Orbit GUI element, CSEventHandler (server-gated teleport) | Cosmetic
hasO2Atmosphere | EngineMovementBehaviour.renderInContraption | Cosmetic (thruster plume colour)
arrivalHeight(RL) | NONE on the client (only server CustomTeleporter) | None
accessibleFrom(RL) | NONE outside CSDimensionUtil | None

## CSDimensionUtil SERVER USAGE

cost (trajectory), gravity (rocket acceleration + player physics), arrivalHeight (CustomTeleporter),
isOrbit (changeDimension), planetUnder (fall-through teleport), updatePlanetsFromRegistry + updateCostMap
(DataEventHandler at ServerStartedEvent), getTravelMap.

## REMOTE CLIENT VALUE

Before fix: the client`s travel map is built from the synced frozen registry (16 entries: CS 6 +
overworld override + 9 static proof JSONs). For system_0000_planet_00 surface the frozen value is
`gravity=9.71, arrivalHeight=128`; for every procedural body NOT in the frozen set the client falls
back to `gravity=9.81, arrivalHeight=64`.

After fix: the client receives the seed-aware values via ProceduralCsSyncPacket and its travel map is
re-pointed through the public CSDimensionUtil.updatePlanetsFromRegistry. Expected client values for
system_0000_planet_01: surface gravity 7.7760 m/s2, arrival 200; orbit gravity 0, arrival 64.

## SERVER VALUE (authoritative, verified at boot)

system_0000_planet_01 surface: domain 7.7760 m/s2 = CS registry 7.7760 = CSDimensionUtil 7.7760.
Orbit: 0.0. All three planets differ (8.4180 / 7.7760 / 7.1121). coverage missing=0.

## CLIENT VALUE USED FOR FLIGHT: YES

The client uses CSDimensionUtil.gravity(RL) in the CS gravity mixin for the LOCAL player. A stale value
means the client predicts normal Earth gravity in procedural bodies: on a 7.776 m/s2 surface it
over-predicts movement (rubber-band/slide); in a zero-g orbit it predicts a downward fall after
leaving the rocket (server then corrects to weightless). The FLIGHT MOTION itself (trajectory,
arrival height, placement) is 100% server-authoritative and correct - the defect is the client-side
local physics prediction and UI/state visibility.

## ACTUAL ROOT CAUSE

Client travel map is populated ONLY from the vanilla datapack-registry sync, which carries the frozen
pre-seed registry. The seed-aware values exist only in the server`s runtime travel map (R14.6.2
bridge). The vanilla sync cannot carry them (no official post-WorldStem registry rebuild; verified
against Minecraft 1.21.1 WorldLoader / reloadResources). The client requires the seed-aware gravity
for its local player physics, so a custom synchronization path is required.

## FIX IMPLEMENTED (minimal)

New server-to-client custom payload ProceduralCsSyncPacket (RL, gravity, arrivalHeight, orbitedBody)
carrying ONLY the fields the client consumes. Sent once per player join (PlayerLoggedInEvent). On the
client it is applied via the SAME public API the server uses (CSDimensionUtil.updatePlanetsFromRegistry)
using a MappedRegistry built from the client`s synced official entries + the packet entries.

- The client travel map is deliberately NOT cost-map rebuilt (an all-pairs Dijkstra over ~5600 entries
  costs ~19s; the authoritative trajectory/cost is server-side and the client UI cost display is
  informational). The client travel map is pre-warmed from the frozen registry first so cost() can
  never trigger a large lazy rebuild.
- Official CS entries (mars/venus/earth_orbit/...) are preserved (only the seed-aware procedural keys
  are overridden).
- The server remains authoritative for rocket destination, trajectory, dimension change, player
  placement and physics. The client copy is for client-side calculations only.

## FILES CHANGED

- src/main/java/com/modscreating/unlimitedspace/cs/network/ProceduralCsSyncPacket.java (NEW)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsNetworking.java (NEW: payload registration + sendSyncToPlayer)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsClientSync.java (NEW: client apply)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsClientCommand.java (NEW: /usclientcs client diagnostic)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsRuntime.java (syncEntries projection)
- src/main/java/com/modscreating/unlimitedspace/UnlimitedSpace.java (payload registration + player-login send)
- src/main/java/com/modscreating/unlimitedspace/command/GalaxyCommands.java (/unlimitedspace cscheck <rl> server diagnostic)

## BUILD

./gradlew build (with tests) -> BUILD SUCCESSFUL.

## AUTOMATED TESTS

333 tests, 0 failures, 0 skipped (unchanged R14.6.2 suite; no generator/gravity/worldgen changes).

## DEDICATED SERVER TEST

run_phase8_runtime booted: Done (1.286s); payload registration logged
(`registered ProceduralCsSyncPacket payload`); R14.6.2 bridge still applies 5567 seed-aware entries,
coverage missing=0, gravity parity confirmed. No dist-loading regression (the earlier LocalPlayer
crash was fixed by removing client-only types from the common handler class).

## REMOTE CLIENT TEST

Not performed - no interactive remote client in this environment. When run:
- server: /unlimitedspace cscheck unlimitedspace:planet/system_0000_planet_01/surface
- client: /usclientcs unlimitedspace:planet/system_0000_planet_01/surface
must both show gravity 7.7760, arrival 200; the orbit RL must show 0 / 64 / isOrbit=true.

## PHYSICALLY CONFIRMED

NO - requires an interactive remote client (not available headless).

## REMAINING LIMITATION

- Client UI cost display is informational and stays on the frozen graph (server-trajectory authority
  is unaffected).
- The client sync carries the full covered metadata (~5600 entries, ~600 KB, one packet per join) - it
  is bounded by the same CS cost-map scope as the server (csMetadataSystemCount).

## FINAL STATUS

CATEGORY B addressed: client now receives and uses the same seed-aware values as the server for the
only gameplay-critical client consumer (player gravity). Server authority untouched. Physical
confirmation still required.

STOP.
DO NOT START R15.