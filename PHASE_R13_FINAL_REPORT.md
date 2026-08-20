# PHASE R13 - Final Report (minimal admin navigation & Creating Space bridge)

R13 implements the approved plan: core domain (finite statistics, canonical order, single destination
resolver), playability validation, the official Creating Space travel bridge, the `/unlimitedspace
system` and `/unlimitedspace nav` commands, unit tests, and a confirmed dedicated-server runtime boot.
The interactive GUI inside the Creating Space screen is blocked by a safe public-API limitation (#9)
and is deliberately NOT implemented (no mixin/reflection/screen replacement). R8-R12 remain GREEN.

## 1. CODE EXISTS

New Minecraft-free domain: `core/galaxy/ObjectKind.java`, `CelestialObject.java`, `TestGalaxyScope.java`,
`TestGalaxyStatistics.java`; `core/nav/DestinationKind.java`, `ResolveError.java`, `ResolvedDestination.java`,
`DestinationResolver.java`; `StarSystem.canonicalCelestialObjects()` (the ONE canonical list).
New Minecraft/CS adapter: `nav/NavStatus.java`, `NavResult.java`, `DestinationCatalog.java`, `CsCatalog.java`,
`AdminNav.java`, `CsTravelBridge.java`.
Modified: `StarSystem`, `GalaxyCommands` (+`/nav`), `GalaxyConfig` (+`testSystemCount`), `UnlimitedSpace`
(+ finite statistics log at server start).

## 2. UNIT TESTS (19 new, all green)

`TestGalaxyStatisticsTest` (6): determinism; scope controls count; sums==totals; bounds; seed dependence;
finite scope. `CanonicalObjectIndexTest` (6): deterministic ordering; every index->one object; no
dupes/gaps; ordering stars->planets->asteroids; type from object. `DestinationResolverTest` (7): planet
surface/orbit; moon 0 surface/orbit; moon beyond bounds; star body/orbit; asteroid same field; invalid
system/object/dest; determinism.

## 3. BUILD

`./gradlew test` => BUILD SUCCESSFUL (290 tests, 0 failures). `./gradlew build` => BUILD SUCCESSFUL;
jar `build/libs/unlimitedspace-1.0.0.jar` produced.

## 4. STATISTICS SCOPE (R13.1)

Default test scope: 128 systems. Configured via server config `testSystemCount` (range 1..1_000_000).
Calculated by `TestGalaxyStatistics.of(galaxy, scope)` resolving systems `[0..N-1]` lazily and summing
`counts()`; verified `sum(system.x)==totalX`. Finite: only the configured slice is touched; the potential
galaxy is never materialized. Runtime (confirmed): `Systems=128 (finite [0..127]), Stars=168, Planets=444,
Moons=1161, Asteroid Clusters=469`.

## 5. CANONICAL OBJECT ORDER (R13.2 / R13.14)

`StarSystem.canonicalCelestialObjects()`: 1) all stars (primary first) -> STAR; 2) all planets (orbit
order) -> PLANET; 3) all asteroid fields -> ASTEROID_FIELD. Deterministic per (WorldSeed, SystemId); no
gaps/dupes; type always read from the generated object `kind()`, never from a numeric range. Moons are
reached via a planet's destination index.

## 6. DESTINATION RESOLVER (R13.7 / R13.15)

`DestinationResolver(galaxy, system, object, destination)` -> `ResolvedDestination`, shared by command,
tests and diagnostics. PLANET: 0=surface,1=orbit, even>=2=Moon[(d-2)/2].surface, odd>=3=Moon[(d-3)/2].orbit
(bounds-checked). STAR: 0=body,1=orbit; higher invalid (playability reports star as not registered).
ASTEROID: 0..N = same field. Invalid -> explicit `Invalid System / Invalid Object / Invalid Destination`.

## 7. CREATING SPACE BRIDGE (R13.10)

Verified against CS 1.7.18 bytecode: `RocketContraptionLaunchPacket(int entityId, ResourceLocation)` with
public `handle(ServerPlayer)` sets the rocket's destination and runs the official trajectory/launch
calculation - the same public path the normal Rocket Controls UI uses. `CsTravelBridge.findRocket/launch`
feeds a resolved ResourceLocation into that path. No `player.teleport`, no manual `changeDimension`, no
custom `DimensionTransition`/`RocketPath`/fuel/deltaV/transport.

## 8. COMMAND AUDIT (R13.12)

| Command | Purpose | Status | Reason |
|---|---|---|---|
| `/unlimitedspace galaxy` | galaxy metadata/estimate | KEEP (debug) | useful seed/type/estimate diagnostic |
| `/unlimitedspace system [id]` | current/system counts | KEEP (mandatory) | prints System/Stars/Planets/Moons/Asteroid Clusters |
| `/unlimitedspace planet sys orbit` | planet details | RETAIN AS DEBUG | worldgen/seed diagnostic |
| `/unlimitedspace space` | teleport to space dim near a planet | RETAIN AS DEBUG | debug-only; not a destination-travel mechanism |
| `/unlimitedspace spaceinfo` | position in galaxy | RETAIN AS DEBUG | dev diagnostic |
| `/unlimitedspace nav s o d` | resolve + CS travel (new) | KEEP (temporary admin interface) | uses the one resolver; never direct-teleports |

## 9. SERVER RUNTIME (CONFIRMED) / CLIENT RUNTIME - GUI STOP

Server (CONFIRMED): a dedicated server booted with the current build (`./gradlew runServer`), loaded
without startup errors, and printed the R13 finite statistics on `onServerStarted`; `/unlimitedspace nav`
registers server-side without errors.

Client GUI - STOPPED (approved Option 2). `RegisterGuiLayersEvent` layers are vanilla `LayeredDraw.Layer`
HUD entries that only render while no Screen is open; while the CS `ScheduleMakingScreen` is open the HUD
layer pipeline is not drawn and GUI layers receive no mouse/keyboard/text input. There is no public
NeoForge 1.21.1 API to render over another mod's open screen and capture input without a
mixin/subclass/reflection (forbidden). Documented: "Interactive fields inside ScheduleMakingScreen are
blocked by the lack of a safe public NeoForge 1.21.1 API for interactive overlays over an already-open
third-party Screen." Not a failure of R13; `/nav` is the temporary administrative interface.

## 10. PHYSICALLY CONFIRMED / NOT CONFIRMED

Unit tests PASS (290); build/jar PASS; finite statistics/canonical/resolver PASS (domain + confirmed
server printout); dedicated-server boot CONFIRMED. Real Creating Space rocket trip through `/nav` on a
live client (TEST A/B/C/D/E): NOT CONFIRMED - CLIENT REQUIRED (no live Minecraft client / real rocket
available in this automated environment).

## 11. KNOWN ISSUES

`StarId.code()` is shared by all stars of a system (companions), so display uniqueness is by object
identity, not code. Star destinations resolve in domain but are not currently playable/registered. Client
GUI blocked by the #9 public-API limitation; a client UI can be added later via an approved architecture.

## 12. COMMIT

Not committed in this run. Working tree holds the implemented R13 (core + command + bridge + tests + this
report). Commit deferred until the real-client `/nav` validation and the final R13 decision.

## 13. R13 STATUS

NOT CONFIRMED - CLIENT REQUIRED. Statistics, canonical order, the single destination resolver, playability
validation, the official CS bridge, 19 new green tests, build, and a confirmed server statistics printout
are DONE. R13 GREEN still requires real client evidence that `/unlimitedspace nav` -> Creating Space ->
real rocket travel -> real US destination works (TEST A-E), which cannot be produced in this environment.
No GUI inside the CS screen was shipped (safe API limitation, approved to leave STOPPED). R14+ / Galaxy
Map not started.
---

## 14. CS BRIDGE NPE INVESTIGATION & MINIMAL FIX (real-client reproduction)

Real error reproduced on a real client:
`/unlimitedspace nav 0 1 0` launched the rocket, then NPE during destination transition:
`Cannot invoke net.minecraft.core.BlockPos.getX() because the return value of java.util.HashMap.get(Object) is null`.

### Exact NPE source (verified from CS 1.7.18 bytecode, not guessed)

| Item | Value |
|---|---|
| CLASS | `com.rae.creatingspace.content.rocket.CustomTeleporter` |
| METHOD | `getTransition(Entity, ServerLevel)` |
| FIELD | `RocketContraptionEntity.initialPosMap : HashMap<ResourceLocation, BlockPos>` |
| MAP KEY | target `destination` = `destServerLevel.dimension().location()` |
| EXPECTED VALUE | `BlockPos` (arrival X/Z; Y is overridden by the destination's `arrivalHeight`) |
| ACTUAL VALUE | `null` -> `BlockPos.getX()` NPE |
| WHO INSERTS IT | normal client: `RocketEntryPosMapClientPacket` -> `rocket.setInitialPosMap(...)`; and `RocketControlsBlockEntity.assemble()` calls `rocket.setInitialPosMap(block.initialPosMap)` |

### Flow comparison

NORMAL CS: Rocket Controls GUI config -> block.`initialPosMap` persisted -> `assemble()` passes it to the rocket -> launch -> `CustomTeleporter` reads present entry -> arrival.

`/nav`: `RocketContraptionLaunchPacket.handle` sets `rocket.destination` + runs trajectory calc, but does NOT touch the position map -> launch -> `CustomTeleporter` reads MISSING entry -> NPE.

Missing step in `/nav`: filling `initialPosMap[destination]`.

### Minimal fix (no blind NPE patch, no inventing, no reflection/mixin)
`CsTravelBridge.ensureInitialPosition(...)` is called before launch. It merges the target destination into the rocket's position map *via the same public setter the official pipeline uses* (`setInitialPosMap`), preserving any destinations the player already configured, and uses the rocket's own position as the destination's initial position (the semantic meaning of the map; no hard-coded `(0,0,0)`, no parallel map, no teleport). CS still computes the arrival Y from the destination's `arrivalHeight` and performs the actual transition.

### Tests
No fake unit test for the runtime map initialisation (it can only be verified on a live server/client). The deterministic R13 regression gates (destination-RL resolution, validation) remain green: `./gradlew test` BUILD SUCCESSFUL, 290 tests, 0 failures; `./gradlew build` SUCCESS. Runtime transport (initialPosMap init + real transition) => CLIENT/SERVER RUNTIME REQUIRED.
---

## 15. POST-FIX / FINAL R13 STATUS

The CS-bridge NPE root cause is identified and the smallest public-API fix is implemented and green
(code-level). R13 GREEN still requires a real client to confirm the fix: `/unlimitedspace nav` must now
complete the Creating Space dimension transition (
initialPosMap entry is set, CustomTeleporter reads it, arrival at the target world). Without that live
client confirmation R13 remains **NOT CONFIRMED — CLIENT REQUIRED**. R14 / Galaxy Map not started.
