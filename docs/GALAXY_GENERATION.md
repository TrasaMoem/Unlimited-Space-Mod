# Galaxy Generation

Pure-domain, deterministic procedural generation for **Unlimited Space**.

## Design goals

- **Stable identity.** A planet/star/system is identified by its fixed id
  (galaxy/world seed + fixed indices). Renaming or re-ordering generation never
  changes identities or seeds.
- **No Minecraft coupling at the data layer.** All generation below the
  `core` package returns plain Java records/objects (`PlanetProperties`,
  `StarSystem`, `Galaxy`). No `Level`, `ServerLevel`, `ChunkGenerator`,
  `DimensionType` or `ResourceKey` appears here.
- **Deterministic.** Every value is a pure function of its seed and a fixed
  slot (`Seeds.fraction / rangeDouble / subsystem`), so results are stable across
  restarts, JVM builds and generation order.
- **Lazy.** Only the requested system/planet is ever materialised; the whole
  galaxy is never built.

## Seed chain

```
WorldSeed -> Seeds.galaxy         -> GalaxySeed
GalaxySeed -> Seeds.starSystem(i) -> StarSystemSeed
SysSeed    -> Seeds.planet(o)     -> PlanetSeed
PlanetSeed -> Seeds.subsystem(n)  -> SubsystemSeed (terrain, biome, ore,
                                                       structures, vegetation, materials)
```

`Seeds` uses a SplitMix64-style avalanche (`mix`) plus an FNV-1a string fold
(`hashString`) — **not** `String.hashCode()`, which is platform-dependent in
older specs. The chain is the single source of determinism.

## Galaxy (`core.galaxy.Galaxy`)

`Galaxy.from(worldSeed[, params])`:
- derives `galaxySeed = Seeds.galaxy(worldSeed)`;
- `systemId(index)` is a stable `StarSystemId` (never materialises siblings);
- `starSystemSeed(index) = Seeds.starSystem(galaxySeed, index)`;
- `planetSeed(index, orbit) = Seeds.planet(starSystemSeed(index), orbit)`;
- `getStarSystem(id)` lazily builds one system (position + star);
- `estimatedSystemCount()` is debug metadata only — it does **not** define ids/seeds.

`GalaxyParameters(radius, starDensity, type)` is **configuration only**: it shapes
placement and the debug estimate, but is deliberately excluded from any identity
or seed. Renaming the radius in the config file therefore never moves an existing
system or changes its planets.

## Star systems (`core.stars`)

`StarSystem` holds a stable id, seed, deterministic `GalacticPosition` (from
`SystemPlacer`) and its `Star`. `StarGenerator.fromSeed` picks a spectral type
(`StarType`, weighted realistic-ish) and derives `temperature`, `size`,
`luminosity` (all within type ranges) from fixed slots.

## Planets (`core.planets`)

`PlanetPropertyGenerator.define(seed, systemId, orbit)` builds a `PlanetDefinition`
(id + seed + `PlanetType`). `generate(def)` produces `PlanetProperties`:

- **Type** is chosen by slot 0 weighted by `PlanetType.occurrenceWeight`.
- All scalar properties are sampled within the *selected type's* allowed ranges, so
  a `GAS_GIANT` can never receive a rocky property profile.
- `PlanetProperties` is a record containing nested records
  (`ResourceProfile`, `BiomeParameters`, `GenerationParameters`) and the six
  independent subsystem seeds.

### Cross-constraints honoured by the generator
- Gas giants → `surface = GASEOUS`, `waterCoverage = 0`, `lifeLevel = 0`,
  `terrainRoughness = 0`, `atmosphere = GASEOUS`.
- Temperature is clamped to `[type.temperatureMinK..type.temperatureMaxK]`.
- Humidity is damped on cold worlds (`coldFactor`).
- `isHabitable()` requires `230K <= T <= 350K`, `water > 0.1`, `humidity > 0.05`,
  non-gas/non-volcanic — and implies `lifeLevel > 0` and `vegetationDensity > 0`.

## Config & commands

- `config.GalaxyConfig` is a NeoForge `ModConfig` (`COMMON`) exposing radius,
  density and type; it feeds `GalaxyParameters` and is registered in
  `UnlimitedSpace` constructor.
- `command.GalaxyCommands` (`/unlimitedspace galaxy|system|planet ...`) is a
  permission-2 debug command that only *reads* and prints domain data — it never
  creates dimensions.

## Testing

Pure-domain JUnit 5 tests live in `src/test/java/com/modscreating/unlimitedspace`:
`SeedsTest`, `GalaxyTest`, `StarSystemTest`, `PlanetPropertyGeneratorTest`.
They assert determinism, range constraints and the cross-constraints above, with no
Minecraft runtime (see `build.gradle` test block).
