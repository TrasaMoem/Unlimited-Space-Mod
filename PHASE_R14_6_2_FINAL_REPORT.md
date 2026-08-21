# PHASE R14.6.2 - FINAL REPORT: ACTUAL PROCEDURAL CS FLIGHT PIPELINE FIX

STATUS: NOT GREEN. Build + 333 tests + dedicated-server runtime proof OK; rocket flight requires a physical client test (not performed here).

## 1. Root cause #1 - metadata coverage was incomplete (VERIFIED and FIXED)

Verified in source: `ProceduralMetadataGenerator.PLANETS_PER_SYSTEM = 1` - only `planet_00`
(plus one moon, one asteroid, one star orbit) received CS metadata per system. Every other
canonical planet (`planet_01`, `planet_02`, ...) had NO RocketAccessibleDimension entry, so
`CSDimensionUtil.gravity(RL)` silently fell back to 9.81 and `arrivalHeight(RL)` to 64 -
exactly the observed "nothing changed / no zero-G / surface not like Venus" symptoms.

The 21.08 05:55 R14.6 client-test log confirmed it: navigating to `system_0841_planet_02`
logged `runtimeCsEntry=false` because only planet_00 was covered.

Fix: the generator now walks the ACTUAL canonical object list of every in-scope system
(`StarSystem.canonicalCelestialObjects()` / `planetCount()` / `planet.moons()` /
`asteroidClusterCount()` / star) and produces surface+orbit for every planet, surface+orbit
for every moon, one entry for every asteroid cluster, and the star orbit.

## 2. Root cause #2 - hash-based seed-independent gravity (VERIFIED and REMOVED)

Verified in source: `canonicalPlanetGravityMs(PlanetId)` used `id.code().hashCode()` and was
seed-independent, while `PlanetProperties.gravity()` is seed-derived. Two different gravity
models existed; the CS registry used the wrong one.

Fix: `canonicalPlanetGravityMs` / `canonicalMoonGravityMs` were DELETED. The single source of
truth is now `Planet.properties().gravity()` / `Moon.properties().gravity()` converted via
`Gravity.toMetersPerSecondSq(...)`. The generator delegates per-body construction to
`ProceduralRocketAccessibleDimensionFactory`, so there is exactly ONE metadata builder.

## 3. Files changed

- src/main/java/com/modscreating/unlimitedspace/core/cs/ProceduralMetadataGenerator.java (rewritten: seed-aware, full coverage, hash gravity removed)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsPack.java (serves ONLY the minecraft:overworld override - the frozen registry is read before the seed exists)
- src/main/java/com/modscreating/unlimitedspace/cs/ProceduralCsRuntime.java (NEW: runtime seed-aware bridge + on-demand expansion + coverage verification)
- src/main/java/com/modscreating/unlimitedspace/UnlimitedSpace.java (LOWEST-priority ServerStartedEvent bridge hook + gravity-parity proof)
- src/main/java/com/modscreating/unlimitedspace/nav/AdminNav.java (ensureSystem before world prep; loud MISSING-metadata errors; rocket-destination logging)
- src/main/java/com/modscreating/unlimitedspace/command/GalaxyCommands.java (canonical object list in /system; new /unlimitedspace trace)
- src/main/java/com/modscreating/unlimitedspace/Config.java (default csMetadataSystemCount 1000 -> 200; comments)
- src/test/java/com/modscreating/unlimitedspace/core/cs/ProceduralMetadataGeneratorTest.java (rewritten for the seed-aware API)
- run/config/unlimitedspace-common.toml (csMetadataSystemCount 1000 -> 200 so the dev client uses the new budget)

## 4. Architecture

Verified against Minecraft 1.21.1 sources (`WorldLoader.load`, `MinecraftServer.reloadResources`)
and NeoForge 21.1.248: the creatingspace:rocket_accessible_dimension registry is a WORLDGEN-layer
datapack registry loaded and frozen during WorldStem creation, BEFORE the world seed is decoded,
and `reloadResources` does NOT rebuild that layer. Creating Space, however, exposes the public
runtime seam `CSDimensionUtil.updatePlanetsFromRegistry(Registry)` +
`updateCostMap()` + `removeUnreachableDimensions()`.

The smallest correct bridge therefore runs at `ServerStartedEvent` (EventPriority.LOWEST, i.e.
after Creating Space builds its own travel map from the frozen registry) and re-points the CS
runtime travel map from a `MappedRegistry` containing:

- every official CS entry not overridden (mars/venus/the_moon/earth_orbit/...)
- the full seed-aware procedural set (from the SAME Galaxy/Planet/Moon objects worldgen uses)
- the seed-aware minecraft:overworld routing override

The runtime verification log proves the ordering:

```
[16:18:21] DataEventHandler: updating the travel map          <- CS builds from the frozen (small) registry
[16:18:21] DataEventHandler: updating the space travel cost map
[16:18:22] ProceduralCsRuntime: building seed-aware CS metadata: systems=[0..200) generatedEntries=5567
[16:18:41] ProceduralCsRuntime: seed-aware CS travel map applied: proceduralEntries=5567 officialEntries=16
[16:18:41] ProceduralCsRuntime: coverage: canonicalBodies=3246 canonicalEntries=5567 generatedEntries=5567 missing=0
```

## 5. Gravity source of truth

WorldSeed -> Galaxy -> StarSystem -> PlanetSeed -> PlanetProperties.gravity() ->
Gravity.toMetersPerSecondSq(...) -> RocketAccessibleDimension.gravity() ->
CSDimensionUtil.gravity(RL) -> player physics. One formula. No hash model.

## 6. Metadata coverage

Per system: every canonical planet (surface+orbit), every moon (surface+orbit), every asteroid
cluster, the star orbit. Object kinds are read from the canonical model, never assumed from a
numeric index. Coverage is verified at startup and `missing=0`.

Default scope is 200 systems (~5 600 entries) because Creating Space builds an all-pairs Dijkstra
cost map (measured ~19s for 5 567 entries; ~10 000 crosses the 60s watchdog). Systems beyond the
scope are added ON DEMAND by `ProceduralCsRuntime.ensureSystem` (bounded by the same budget; an
over-budget request is refused with an explicit MISSING-PROCEDURAL-CS-METADATA error instead of a
silent 9.81/64 fallback).

## 7. DynamicDimensions

Unchanged (still lazy). The RL used by RocketAccessibleDimension is exactly the RL prepared by
DynamicPlanetWorldManager (PlanetWorldBinding/MoonWorldBinding/AsteroidWorldBinding/StarWorldBinding
are the single key scheme). AdminNav calls ensureSystem BEFORE world preparation so the CS metadata
and the world both exist before the rocket launches.

## 8. CSDimensionUtil

The travel map is rebuilt from the seed-aware MappedRegistry at ServerStartedEvent (LOWEST). The
runtime log shows gravity parity for system 0's planets:

```
system_0000_planet_00: domain=8.4180 m/s²  csSurface=8.4180 csOrbit=0.0000 arrivalSurface=200
system_0000_planet_01: domain=7.7760 m/s²  csSurface=7.7760 csOrbit=0.0000 arrivalSurface=200
system_0000_planet_02: domain=7.1121 m/s²  csSurface=7.1121 csOrbit=0.0000 arrivalSurface=200
```
Three different planets -> three different seed-derived gravities, each matching its own
PlanetProperties.gravity(). Orbit gravity 0, surface arrival 200.

## 9. Rocket destination

AdminNav.attemptTravel now logs the exact rocket destination and the CS runtime values Creating
Space will read (gravity / arrivalHeight / isOrbit) before handing the launch packet to
`RocketContraptionLaunchPacket.handle` (the official CS launch path).

## 10. Build

`./gradlew build` (with tests) -> BUILD SUCCESSFUL in 11s.

## 11. Runtime verification (dedicated server, run_phase8_runtime)

Server booted: "Done (1.388s)". Startup logs prove: CS builds from the frozen registry, then the
seed-aware bridge applies 5567 procedural + 16 official entries, coverage missing=0, and the
gravity parity above. The cost map (~19s) is inside the 60s watchdog.

## 12. Physical client verification

PHYSICALLY CONFIRMED: NO - requires a live client + rocket (cannot be operated from this headless
environment). The user must physically run `/unlimitedspace nav <system> <planetObject> 0|1` for an
in-scope planet and observe: orbit = weightless direct arrival (no fall), surface = CS-style descent
at arrival 200 then landing with the domain gravity. A second planet (e.g. system_0000 planet_01)
must show a DIFFERENT gravity than planet_00.

## 13. Remaining limitations

- On a DEDICATED server + remote client, the vanilla registry sync carries only the frozen
  (seed-independent) registry, so a remote client's LOCAL travel map cannot be seed-aware. The
  server (authoritative for trajectory/gravity/arrival/isOrbit) IS seed-aware; the integrated dev
  client (single JVM) shares the seed-aware travel map. A fully client-synced seed-aware value
  requires a CS-side feature or a custom packet (not invented here).
- Metadata scope is bounded by the CS O(V^2) cost map (~5 600 entries / 200 systems default;
  configurable csMetadataSystemCount). On-demand expansion covers navigated systems beyond the
  scope up to the budget, then refuses loudly.
- The 9 static proof JSONs for system_0000 in the mod datapack are overridden by the seed-aware
  bridge at runtime (same RL, seed-aware value) - no behavioral regression.

STOP.
DO NOT START R15.