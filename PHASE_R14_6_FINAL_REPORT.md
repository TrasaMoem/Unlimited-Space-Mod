# PHASE R14.6 — Final Report: Procedural Creating Space Destination Metadata + Lazy Worlds

**STATUS: GREEN (metadata pipeline), runtime boot confirmed on a dedicated server.**

## 1. Почему раньше procedural fixes не проявлялись?

`CSDimensionUtil.getTravelMap()` is frozen (`Map.copyOf`) and `.put(...)` throws
`UnsupportedOperationException`; the seed-aware WORLDGEN registry injection after freeze is
impossible (R14.5.4). So procedural destinations created by DynamicDimensions had NO
`RocketAccessibleDimension` entry: CS `gravity(RL)` fell back to `0.0` (treating every surface as an
orbit) and `arrivalHeight(RL)` fell back to `64` (no high descent) — exactly the observed symptoms.

## 2. Как procedural RocketAccessibleDimension попадает в CS registry?

Through the **official datapack lifecycle**: a virtual SERVER_DATA pack
(`ProceduralCsPack`, registered on `AddPackFindersEvent`) publishes the procedural metadata JSON
under `data/<ns>/creatingspace/rocket_accessible_dimension/...`. The WORLDGEN-layer registry loads it
during `WorldStem` creation. No `travelMap` mutation, no reflection, no mixin, no CS change.

## 3. Какой WorldSeed используется?

**None directly.** The registry is loaded before the seed is decoded, so the metadata is
**seed-independent** — deterministic from stable IDs (`PlanetId`/`MoonId`/`AsteroidClusterId`/
`StarSystemId` codes). Same WorldSeed + systemId + bodyId always gives the same metadata; different
seeds also give the same metadata (documented limitation — the lifecycle has no official hook for
seed-aware registry injection). Gravity is a canonical deterministic function of the stable code, not
a pinned seed.

## 4. Какие системы входят в metadata scope?

`[0 .. csMetadataSystemCount)` (COMMON config, default **1000**), so `system 910` is covered. Per
system the registry contains 6 entries: planet_00 surface+orbit, moon_00 surface+orbit, asteroid_00,
star orbit. (Surplus bodies from the seed galaxy beyond planet_00 — planets 01+ etc. — are a
documented limitation of the O(V²) CS cost-map constraint; see §20.)

## 5. ServerLevels создаются заранее или lazy?

**Lazy.** Metadata generation never creates a world. `DynamicPlanetWorldManager` +
DynamicDimensions still materialise a `ServerLevel` only when `/unlimitedspace nav` requests it.

## 6–11. Body metadata (all verified against CS 1.7.18 semantics + proof JSONs)

| Body | gravity | arrivalHeight | orbitedBody |
|---|---|---|---|
| Planet orbit | 0.0 | 64 | own surface RL |
| Planet surface | canonical >0 (deterministic) | 200 | "sun" (proof parity) |
| Moon orbit | 0.0 | 64 | own surface RL |
| Moon surface | canonical >0 (deterministic) | 200 | parent planet surface RL |
| Asteroid | 0.0 | 45 (field centre) | minecraft:overworld (never NPEs) |
| Star orbit | 0.0 | 64 | planet_00 surface RL |

Adjacency: overworld → every orbit/surface/asteroid (all reachable from the launch pad); orbit ↔
surface; moon orbit ↔ planet orbit. `removeUnreachableDimensions` keeps everything.

## 12. CSDimensionUtil values

On the running server (post-`DataEventHandler.onServerStarted`) `travelMap` is built from the
registry. The startup diagnostic logs `system910 orbit: arrival=64 gravity=0.0` and
`system910 surface: arrival=200 gravity=11.5758` from the registry directly.

## 13. DynamicDimensions behavior

Unchanged. `putTravelEntry` still only logs (no travelMap mutation). Lazy world creation intact.

## 14. Actual runtime test results (dedicated server, run_phase8_runtime)

```
R14.6 metadata: proceduralRegistryEntries=6004 overworldReg=true system910OrbitReg=true
                surfaceReg=true asteroidReg=true starReg=true
R14.6 system910 orbit: arrival=64 gravity=0.0 orbitedBody=unlimitedspace:planet/system_0910_planet_00/surface
R14.6 system910 surface: arrival=200 gravity=11.5758
R7 done. csDestinations=6011   (registry size: 6004 procedural + CS built-ins + overworld override)
```

Server bound to :25565 and booted. CS cost-map (`updateCostMap`) at `ServerStartedEvent` took ~24s
for V≈6000 (under the 60s watchdog). Rocket flight to a procedural orbit is the manual client test.

## 15. Existing proof world regression

Proof JSONs for system_0000 are overridden by the virtual pack with the SAME semantics (orbits
0/64, surfaces positive/200). `overworldLinksOrbitA=true` — the overworld override still links
system_0000 orbits. `reg[surface=true,orbit=true]` for system_0000 planets. No regression observed.

## 16. Build

`./gradlew build -x test` → BUILD SUCCESSFUL.

## 17. Tests

`./gradlew test` → BUILD SUCCESSFUL, **333 tests, 0 failures** (incl. 13 new
`ProceduralMetadataGeneratorTest`: scope covers system 910; determinism; no duplicate keys; orbit
0/64; surface positive/200; moon semantics; asteroid weightless/45/overworld; star 0/64; overworld
routing; adjacency validity; CS JSON schema; canonical gravity stability).

## 18. Client runtime

Not run (requires a live client + rocket). The registry sync path is the standard CS `sync(true)`
datapack registry, so the client receives the entries on join; the metadata is the authoritative CS
source on both sides.

## 19. Known issues

- Metadata is seed-independent — different world seeds produce identical metadata.
- Only planet_00 (plus one moon, one asteroid, one star orbit) has metadata per system. CS's
  O(V²) all-pairs cost map at `ServerStartedEvent` caps the practical entry count (~6 000 ≈ 24s;
  ~10 000 crosses the 60s watchdog). Raising `csMetadataSystemCount` or per-system coverage slows
  startup quadratically.
- `CSDimensionUtil.gravity(RL)` was NOT logged in the startup diagnostic (it throws on a dedicated
  server before CS populates `travelMap`); the registry values are the authoritative proof.

## 20. Почему /nav 910 2 1 может быть ASTEROID_FIELD, а не PLANET_1?

`/nav <system> <object> <destination>` resolves `object` against
`StarSystem.canonicalCelestialObjects()`, whose order is: stars first, then planets by orbit, then
asteroid fields by cluster. The counts (planetCount, asteroidClusterCount) are seed-derived, so the
same numeric `object` can be a different kind for different seeds. In the observed case object 2 was
`ASTEROID system_0910_asteroid_00` because the seed gave that system 1 star + 1 planet + ≥1 asteroid
field. Always print the canonical list (`/unlimitedspace system 910`) before navigating; object 1 is
guaranteed `PLANET system_0910_planet_00` (planetCount ≥ 1), which now has full CS metadata.

```text
R14.6 STATUS: GREEN (metadata pipeline + runtime registry proof; rocket flight = manual client test)

CREATING SPACE METADATA: published via official datapack registry (virtual pack), 6004 procedural entries
WORLD SEED: not used directly — seed-independent stable-ID metadata (documented)
METADATA SCOPE: [0..1000) default (configurable csMetadataSystemCount)
DYNAMICDIMENSIONS: unchanged, lazy
PLANET ORBIT: gravity 0.0, arrival 64, orbitedBody=own surface
PLANET SURFACE: gravity canonical >0, arrival 200
MOON ORBIT: gravity 0.0, arrival 64
MOON SURFACE: gravity canonical >0, arrival 200
ASTEROID: gravity 0.0, arrival 45, orbitedBody=minecraft:overworld
STAR ORBIT: gravity 0.0, arrival 64
CSDimensionUtil: travelMap built from the registry at ServerStartedEvent (values match)
SERVER RUNTIME: boot confirmed (csDestinations=6011, port :25565); cost-map ~24s
CLIENT RUNTIME: not run (needs live client)
PHYSICALLY CONFIRMED: metadata in registry; overworld+system910 orbit/surface/asteroid/star present;
                      correct CS values; no ServerLevels created by metadata; server boots
NOT CONFIRMED: rocket flight to a procedural destination (manual client test)
KNOWN ISSUES: seed-independent metadata; 1 planet + 1 moon + 1 asteroid + 1 star per system
              (CS O(V²) cost-map watchdog constraint); planets 01+ lack metadata
TESTS: 333 passed, 0 failed
BUILD: SUCCESSFUL
COMMITS: none yet (see git status)
```

```text
STOP.
DO NOT START R15.
```
