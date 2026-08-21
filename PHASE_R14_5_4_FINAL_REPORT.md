# PHASE R14.5.4 — Final Report: Seed-Aware Creating Space Registry Probe & Dynamic Destination Bridge

**STATUS: BLOCKED at the lifecycle level. NO probe entry was created. Per §28 the blocker is reported
exactly and no workaround (no `travelMap.put`, no reflection, no mixin, no bytecode change, no pinned
seed, no custom network protocol) was attempted.**

This phase did NOT implement the probe. Instead, per §3 ("ПЕРЕД КОДОМ — ПРОВЕРИТЬ ACTUAL LIFECYCLE"),
the actual NeoForge 21.1.248 and Creating Space 1.7.18 lifecycle was verified from real sources/bytecode.
The verification proves the proposed pipeline

```text
WorldSeed -> virtual datapack (RepositorySource/PackResources) -> RocketAccessibleDimension registry
           -> CSDimensionUtil.travelMap
```

cannot be driven through the official datapack registry lifecycle, because the dynamic registry that
holds `RocketAccessibleDimension` is loaded and frozen before the world seed is known, and the only
public reload entry point (`MinecraftServer.reloadResources`) does not rebuild that registry layer.

---

## 1. ACTUAL LIFECYCLE VERIFICATION (§3)

Verified against real artifacts (not assumptions):

- NeoForge `21.1.248` sources jar + merged jar in `build/moddev/artifacts/`.
- Creating Space `1.7.18` classes in `_cs_extract/` (javap on `CreatingSpace`, `DataEventHandler`,
  `CSDimensionUtil`, `RocketAccessibleDimension`) and its shipped datapack JSONs.

### A. When is the RepositorySource registered

`AddPackFindersEvent` is an `IModBusEvent` (mod bus). It is fired inside
`ResourcePackLoader.populatePackRepository(PackRepository, PackType, trusted)`:

```java
resourcePacks.addPackFinder(buildPackFinder(modResourcePacks, packType));
ModLoader.postEvent(new AddPackFindersEvent(packType, resourcePacks::addPackFinder, trusted));
```

`populatePackRepository` is invoked from `ServerPacksSource.createPackRepository(...)`, which for a
dedicated server runs in `net.minecraft.server.Main.main` (line 190), **before** `WorldLoader.load`
(the WorldStem). At this moment the world seed is NOT yet available as a `WorldData` object; only the
raw `level.dat` NBT (`dynamic1`) and `server.properties` exist. There is no mod hook with a server
context at this point.

### B. When PackResources methods are called

`Pack.ResourcesSupplier.openPrimary` / `openFull` and `PackResources.getMetadataSection` run during
`PackRepository.reload()` (called from `WorldLoader.PackConfig.createResourceManager()` ->
`MinecraftServer.configurePackRepository`). `getResource`/`listResources` run during
`RegistryDataLoader.loadContentsFromManager` (via `FileToIdConverter.listMatchingResources`) and the
tag/recipe/loot/advancement/function reloaders. All of this happens inside `WorldLoader.load`, i.e.
**before the WorldSeed is decoded**.

### C. When the current WorldSeed becomes available

The authoritative seed lives in `WorldData.worldGenOptions().seed()`. For a dedicated server the
`WorldData` is decoded **inside** `WorldLoader.load` in the `WorldDataSupplier` lambda:

```java
LevelDataAndDimensions ldd = LevelStorageSource.getLevelDataAndDimensions(
    dynamic1, p_307161_.dataConfiguration(), registry, p_307161_.datapackWorldgen());
return new WorldLoader.DataLoadOutput<>(ldd.worldData(), ...);
```

That lambda executes **after** `RegistryDataLoader.load(...)` has already built and frozen the WORLDGEN
and DIMENSIONS registry layers (lines 35-48 of `WorldLoader.load`). The first official event with the
seed is `ServerAboutToStartEvent` (`DedicatedServer.initServer`), then `ServerStartingEvent` — both
after the `WorldStem` already exists.

### D. When `rocket_accessible_dimension` is decoded

`RocketAccessibleDimension` is a NeoForge datapack registry registered by Creating Space:

```java
// CreatingSpace.lambda$new$3 (mod bus, DataPackRegistryEvent.NewRegistry)
newRegistry.dataPackRegistry(RocketAccessibleDimension.REGISTRY_KEY, RAD.CODEC, RAD.CODEC, sync(true));
## 2. THE RELOAD EXPERIMENT (§8) — PROVABLY CANNOT REBUILD THE REGISTRY

`MinecraftServer.reloadResources(Collection<String>)` (verified in the 21.1.248 sources):

```java
CompletableFuture.supplyAsync(() -> packRepository.rebuildSelected(selectedIds).stream().map(Pack::open).toList(), this)
  .thenCompose(packs -> {
      CloseableResourceManager rm = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
      return ReloadableServerResources.loadResources(rm, this.registries, ...);
  })
  .thenAcceptAsync(... swap resources, setSelected, updateRegistryTags, playerList.reloadResources ...);
```

`ReloadableServerResources.loadResources` -> `ReloadableServerRegistries.reload(registries, rm, ex)`
replaces **only** `RegistryLayer.RELOADABLE` (loot tables) plus runs the reload listeners (tags,
recipes, functions, advancements, NeoForge `AddReloadListenerEvent`). The **WORLDGEN** layer (where
`RocketAccessibleDimension` lives) and the **DIMENSIONS** layer are never rebuilt.

Therefore §8's required checks fail at step 3 ("RocketAccessibleDimension registry действительно
перестраивается"): the registry is provably not rebuilt by `reloadResources`, so the seed-aware entry
is never decoded, so `updatePlanetsFromRegistry` cannot see it.

---

## 3. BLOCKER REPORT (§28)

```text
WHERE:
  net.minecraft.server.WorldLoader.load(...) — WORLDGEN layer load.
  RocketAccessibleDimension is a NeoForge datapack registry
  (DataPackRegistryEvent.NewRegistry, sync(true), CS CreatingSpace.lambda$new$3).
  It is loaded into RegistryLayer.WORLDGEN by
  RegistryDataLoader.load(rm, staticAccess, DataPackRegistriesHooks.getDataPackRegistries())
  and frozen immediately (RegistryAccess.ImmutableRegistryAccess.freeze()).

WHY:
  The WORLDGEN layer is loaded and frozen BEFORE the world seed is decoded.
  The seed (WorldData.worldGenOptions().seed()) is first decoded inside WorldLoader.load's
  WorldDataSupplier lambda — AFTER the WORLDGEN+DIMENSIONS registry layers are already frozen.
  The only public runtime reload entry point, MinecraftServer.reloadResources(Collection),
  rebuilds only RegistryLayer.RELOADABLE (loot tables) + tags/recipes/advancements/functions.
  It does NOT re-run RegistryDataLoader.load for the WORLDGEN or DIMENSIONS layers.
  Hence no seed-aware JSON can ever reach the RocketAccessibleDimension registry after the seed
  is known, and CSDimensionUtil.travelMap (built by DataEventHandler.onServerStarted from the
  frozen registry) can never see it.

PUBLIC API LIMIT:
  - No public API to reload the WORLDGEN (datapack-registry) layer on a running server.
  - No public API to replace the server's LayeredRegistryAccess
    (MinecraftServer.registries is a private final field; no setter).
  - reloadResources does not reload dynamic datapack registries.
  - The seed has no public mod-accessible hook before WorldStem load.

EARLIEST POSSIBLE HOOK:
  None inside the official lifecycle for a seed-aware entry:
  - to provide seed-aware metadata you need the seed BEFORE RegistryDataLoader.load, but the
    seed is only decoded AFTER that load inside WorldLoader.load (no event between them);
  - to change metadata after the seed is known you need to rebuild the WORLDGEN layer, but no
    public API exists (reflection on MinecraftServer.registries would be required — forbidden).
```

---

## 4. PROBE SPEC (§4/§6/§7/§9) — CONFIRMED FORMAT, IMPOSSIBLE TIMING

The probe JSON format was verified against the real CS 1.7.18 codec and shipped files
(`earth_orbit.json`, `the_moon.json`, `mars.json`, `moon_orbit.json`):

```json
{
  "adjacentDimensions": { "<dest>": { "deltaV": <int> } },
  "arrivalHeight": 64,
  "gravity": <float>,
  "orbitedBody": "minecraft:overworld",
  "distanceToOrbitingBody": <int>
}
```

File path for `unlimitedspace:probe/test_planet`:
`data/unlimitedspace/creatingspace/rocket_accessible_dimension/probe/test_planet.json`
(the registry key is `creatingspace:rocket_accessible_dimension`, so `elementsDirPath` is
`creatingspace/rocket_accessible_dimension`). There is NO `"id"` field — the id comes from the file path.
## 5. FINAL REPORT (§29)

```text
WORLD SEED:
  Available only at ServerAboutToStartEvent / ServerStartingEvent (WorldData.worldGenOptions().seed()).
  NOT available when the RocketAccessibleDimension registry is loaded (WorldLoader.load).
  Dedicated server: raw seed NBT exists in level.dat (dynamic1) before WorldLoader.load, but no public
  mod hook with server/level context exists there; server.properties seed is NOT authoritative for an
  existing world.

INITIAL REGISTRY:
  RocketAccessibleDimension decoded into RegistryLayer.WORLDGEN during WorldLoader.load, before the
  seed is decoded. Frozen immediately. Contains the CS built-ins (earth_orbit, the_moon, moon_orbit,
  mars, mars_orbit, venus) plus any static addon JSON.

RELOAD:
  MinecraftServer.reloadResources(...) re-opens packs and rebuilds only RegistryLayer.RELOADABLE
  (loot tables) + tags/recipes/advancements/functions. WORLDGEN/DIMENSIONS layers are NOT rebuilt.
  Verified in the 21.1.248 sources (ReloadableServerResources.loadResources ->
  ReloadableServerRegistries.reload -> createUpdatedRegistries -> replaceFrom(RELOADABLE, ...)).

PROBE:
  NOT CREATED. A seed-aware probe cannot be produced through the official registry lifecycle.

ROCKET_ACCESSIBLE_DIMENSION:
  Worldgen-layer datapack registry; loaded once at WorldStem load; frozen; never reloaded publicly.

TRAVEL MAP:
  Built once at ServerStartedEvent by CS DataEventHandler.onServerStarted via
  CSDimensionUtil.updatePlanetsFromRegistry(server.registryAccess().registryOrThrow(REGISTRY_KEY)).
  It reads the frozen, seed-agnostic registry. A seed-aware entry cannot reach it.

CLIENT SYNC:
  Would work ONLY for entries that exist in the server's frozen datapack registry (sync(true)
  re-syncs on join). A seed-aware entry never exists there, so client sync is unreachable.

SERVERLEVEL BEFORE:
  n/a — no probe entry was created; existing R13/R14 DynamicDimensions lazy world creation untouched.

SERVERLEVEL AFTER:
  n/a — nothing was added.

DYNAMICDIMENSIONS:
  Untouched. DynamicPlanetWorldManager -> DynamicDimensions -> ServerLevel remains lazy and works.

TESTS:
  `./gradlew test` -> BUILD SUCCESSFUL, 320 tests, 0 failures (all R8-R14.5.x suites).

BUILD:
  `./gradlew compileJava` and `./gradlew build` -> BUILD SUCCESSFUL.
  Working tree restored to the committed green state (see COMMITS below).

SERVER RUNTIME:
  Not started for this probe — the static lifecycle proof (sources + bytecode) is decisive and §28
```text
KNOWN ISSUES:
  - The WIP files left over from the earlier failed attempt (modified
    ProceduralRocketAccessibleDimension + untracked CSDimensionUtil /
    ProceduralRocketAccessibleDimensionDatapackProvider / SeedAwareDatapackTest) did not compile
    (24 errors) and were removed; the committed pure-domain ProceduralRocketAccessibleDimension
    record (String keys, Map adjacency) was restored and the build is green again.

COMMITS:
  None created in this phase (working tree clean, all restored to the last committed state).
  Working-tree changes made during verification: reverted the broken WIP modification of
  src/main/java/com/modscreating/unlimitedspace/core/cs/ProceduralRocketAccessibleDimension.java,
  deleted the three non-compiling untracked WIP files, added this report file
  (PHASE_R14_5_4_FINAL_REPORT.md).

STATUS:
  BLOCKED. The core question of R14.5.4 —
  "can the current WorldSeed automatically provide Creating Space procedural
  RocketAccessibleDimension metadata through the normal datapack registry lifecycle,
  without mutating travelMap, without pre-creating worlds, without reflection/mixin" —
  is answered NO for NeoForge 1.21.1 / Creating Space 1.7.18, with the exact lifecycle blocker
  in §3. No code was written around it, and no follow-up scope (procedural metadata factory,
  csMetadataSystemScope, 912 systems) is started.
```

---

## 6. WHY NO WORKAROUND WAS ATTEMPTED (restating §0 / §28)

Every listed escape hatch was deliberately not used:

- `CSDimensionUtil.getTravelMap().put(...)` — frozen `Map.copyOf`, `UnsupportedOperationException`,
  and explicitly forbidden.
- Reflection on `MinecraftServer.registries` to swap a hand-rebuilt `RegistryAccess` — forbidden.
- Mixin/bytecode change to CS or MC — forbidden.
- Pinned/fixed WorldSeed — forbidden (defeats §9).
- A custom registry object handed to `CSDimensionUtil.updatePlanetsFromRegistry` — this WOULD make
  the server travelMap see a seed-aware entry, but the entry would not exist in the official
  `server.registryAccess()` datapack registry, so §12 client sync (normal registry synchronization)
  would fail; it is therefore not the official datapack registry lifecycle and was not used.
- Custom network protocol / own teleport / fake CS destination — forbidden.

If a real seed-aware CS destination is required, the next viable (non-probe) option is to scope it to
what the public lifecycle can actually support: seed-agnostic registry entries for real bodies, with
seed-derived worldgen applied lazily by DynamicDimensions at travel time (the existing R14 design). That
is R15 territory and is not started here.

```text
STOP.
DO NOT START R15.
```
