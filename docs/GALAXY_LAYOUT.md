# Galaxy Layout — Architecture (Phase 5)

Deterministic, lazy, scalable spatial layout for the Variant-D `unlimitedspace:space`
dimension. Builds on the existing seed hierarchy and stays inside the pure `core`
package (no Minecraft imports, verified by CoreArchitectureTest).

---

## 1. Coordinate system

Two coordinate layers, kept strictly distinct:

| Type | Unit | Plane | Notes |
|---|---|---|---|
| `GalaxyCoordinate` | GU (galaxy units), `double x/z` | x/z plane | lookup key for the spatial index; signed; **no** BlockPos/ChunkPos. |
| `StarSystemPosition`, `PlanetPosition` | GU, `double x/z` | x/z plane | layout positions of systems / planets. Pure domain. |
| `PlanetInfluenceRegion` | GU radius | x/z plane | circle that a planet surface-generates inside. |

Minecraft-space adapter (Phase 6, not implemented here) converts `ChunkPos`/`BlockPos` to a
`GalaxyCoordinate` by dividing block coordinates by `SpaceConstants.BLOCKS_PER_GALAXY_UNIT` (256).
The galaxy is centred at the origin, so both positive and negative coordinates are valid.

---

## 2. Galaxy shape (uses existing GalaxyConfig)

No new config is introduced. The server config `GalaxyConfig` already yields
`GalaxyParameters(radius, starDensity, type)`, and Phase 5 uses it directly:

- `radius` -> populates the disc of valid cells.
- `starDensity` -> **cell size**: `cellSize = sqrt(pi / starDensity)`. With one system per cell
  this makes the density of systems exactly `starDensity` per GU squared.
- `type` -> reserved for future placement morphologies; V1_GRID uses the spiral disc for
  all types and selects star/property parameters from the seed (consistent with Phase 3).

`GalaxyParameters.DEFAULT` (radius 100, density 0.8, SPIRAL) is the default; tests override
radius/density to synthesise 100..100000-system galaxies.

---

## 3. Deterministic system placement

Placement is **grid-based and invertible**:

1. The galaxy disc lives on an integer cell grid aligned to the origin. A cell `(cx, cz)` is
   populated iff `cx*cx + cz*cz <= radiusCells*radiusCells`.
2. Each cell maps to a stable **non-negative long index** via zigzag + Cantor pairing
   (`SpatialGrid.indexOfCell`). The inverse (`SpatialGrid.cellOfIndex`) is O(1).
3. The star position inside its cell = `cellCenter + seed-dependent jitter`. Jitter is a pure
   function of the system seed (`Seeds.fraction`), so the **same `(galaxySeed, index)` always
   yields the same coordinates**, and a different galaxy seed yields a different galaxy
   (different jitter + star + planet properties).
4. System seed = `Seeds.starSystem(galaxySeed, index)`; planet seeds reuse the existing
   `PlanetSeed.forSlot(systemSeed, orbit)` chain, so terrain/biome/ore seeds stay compatible
   with the rest of the pipeline.

Properties: order-independent, no global `Random`, restart-stable, restart of galaxy seed is
the only thing that changes placement.

---

## 4. Star system distribution

- Exactly **one system per cell**; minimum centre-to-centre distance between adjacent
  systems = `cellSize * (1 - 2 * JITTER_FRACTION)` = `0.5 * cellSize` (with `JITTER_FRACTION=0.25`)
  -> never zero, no catastrophic overlaps.
- Cell size follows density, so denser galaxies simply pack more systems per area.
- A system is materialised only when its cell is queried (lazy); the disc membership test is
  O(1) per cell, so enumerating neighbours is cheap.

---

## 5. Planet orbit placement

`PlanetPlacer` places planets for a system as a pure function of `(systemSeed, orbitIndex)`:

- planet count = `Seeds.rangeLong(systemSeed, 1, 1, MAX+1)` (1..6; `MAX=6` for Phase 5);
- each orbit `k` gets a monotonically growing radius in `[0.12*cellSize, 0.20*cellSize]` drawn
  from a fixed slot, plus a deterministic angle `Seeds.fraction(...) * 2*pi`;
- planet position = star centre + orbital offset;
- planet seed = `PlanetSeed.forSlot(systemSeed, orbit)`, reused verbatim by terrain/biomes/etc.

Because orbit radii are bounded and jitter is < half a cell, **planets stay inside their
star cell**, which makes cell-ownership lookup correct at a planet centre.

---

## 6. PlanetInfluenceRegion

`PlanetInfluenceRegion(planet, radiusGu, atmosphereRadiusGu, gravityRadiusGu, landingRadiusGu)`.
For Phase 5 only `radiusGu` (the surface disc) is meaningful; the concentric radii are derived
and reserved for future phases (atmosphere shell, gravity well, landing/transition).
`contains(GalaxyCoordinate)` is a pure 2D circle test in GU.

---

## 7. Spatial lookup algorithm (chosen: lazy deterministic spatial grid)

Options evaluated: uniform grid, spatial hash, quadtree, k-d tree. Chosen: **fixed cell grid
with a bounded LRU cache**.

Why this one:
- placement is *defined* on grid cells, so the cell coordinate is already the system
  identity -> no separate structure to build or keep in sync;
- mapping cell <-/> index is O(1) and invertible (zigzag-Cantor);
- the grid is fully reproducible from the seed -> the index needs **no persisted map**;
- per-cell work is constant, so lookup is O(rings) not O(N).

quadtree/k-d tree were rejected because they require materialising or pre-scanning systems
to answer a range query, which conflicts with the laziness requirement.

---

## 8. Spatial index API

`GalaxySpatialIndex` (constructed from `GalaxyLayout`, stateless w.r.t. galaxy size):

- `findSystemAt(GalaxyCoordinate)` -> O(1) cell ownership; the chunk-pipeline lookup.
- `findNearestSystem(GalaxyCoordinate)` -> Euclidean nearest via ring walk; O(rings).
- `findCandidatePlanets(GalaxyCoordinate)` -> planets whose region contains the coordinate
  (ownership based).
- `findNearestPlanet(GalaxyCoordinate)` -> Euclidean nearest planet of the nearest system.
- `systemAtCell(cx, cz)` + `cacheSize()` -> lazy materialisation + introspection.

Reproducible from the seed: the grid, the cell->index map and the jitter are all pure
functions of `(galaxySeed, parameters)`. Memory is bounded by an 8192-entry LRU; only the
explored region is ever held.

---

## 9. Chunk lookup model (pipeline)

```
Minecraft ChunkPos (Phase 6 adapter, not in core)
   |  divide by SpaceConstants.BLOCKS_PER_GALAXY_UNIT
   v
GalaxyCoordinate                       <- core.galaxy.layout
   |  GalaxySpatialIndex.findSystemAt   (O(1), cell ownership)
   v
StarSystemPosition
   |  GalaxyLayout.planetsFor            (lazy planet fan)
   v
candidate PlanetPosition (+ PlanetInfluenceRegion)
   |  PlanetInfluenceRegion.contains
   v
PlanetDefinition                       <- core.planets  (id, seed, type)
   |  PlanetPropertyGenerator.generate
   v
Planet                                 <- core.planets  (definition + properties)
   |  PlanetWorldgenProfile.from
   v
PlanetWorldgenProfile                  <- core.worldgen (terrain/biome/ore seeds)
   |  -> TerrainGenerator / future SpaceChunkGenerator (Phase 6)
   v
Minecraft chunk
```

Stage owners:
- `GalaxyLayout` - spatial model + pipeline entry (`lookup(coord)`).
- `GalaxySpatialIndex` - coordinate resolution.
- `PlanetPlacer` / `SpatialGrid` - placement math.
- `core.planets.*` + `core.worldgen.*` - the existing, unchanged seed/properties/profile pipeline.

---

## 10. Performance

| Systems (est.) | radiusGu | radiusCells | per-lookup | lookup ops/chunk | lazy? |
|---|---|---|---|---|---|---|
| 100 | ~7 | 4 | O(1) cell test | O(MAX_PLANETS) | yes |
| 1,000 | ~20 | 10 | O(1) | O(MAX_PLANETS) | yes |
| 10,000 | ~63 | 32 | O(rings) ~1-3 | O(MAX_PLANETS) | yes |
| 100,000 | ~199 | 101 | O(rings) ~1-3 | O(MAX_PLANETS) | yes |
| 1,000,000 | ~630 | 318 | O(rings), cap 318 | O(MAX_PLANETS) | yes (LRU bounded) |

- Lookup is **never O(N) per chunk**; the worst case is a void/edge query walking up to
  `radiusCells` rings (O(sqrt(N))), and in-galaxy points almost always resolve in 1-3 rings.
- Planet fan is bounded by `MAX_PLANETS_PER_SYSTEM` (constant, independent of N).
- Memory: bounded LRU (<=8192 systems) over the explored region.
- Verified by `GalaxyLayoutPerformanceTest` (100..100000 systems, 2000 lookups each).

---

## 11. Persistence

- Source of truth remains the seed chain `WorldSeed -> GalaxySeed -> SystemSeed ->
  PlanetSeed -> subsystem seeds` (all pure, order-independent).
- `(galaxySeed, StarSystemId)` and `(galaxySeed, systemCell, orbit)` reproduce positions/
  planets exactly after restart -> no persisted map is required or written.
- Generation order does not affect results: each cell/planet is computed from its stable
  identity only.
- No migration system yet (Phase 5 limitation); `WorldgenVersion` tags the layout so Phase 5
  can detect an outdated layout and decide regen vs. preserve later.

---

## 12. Worldgen version

`WorldgenVersion.V1_GRID` is the current layout algorithm. `GalaxyLayout.version()` reports it.
It lives outside `GalaxyParameters` (which stays a pure size/density/type config) so a future
`V2` can swap placement without touching config. Migration is a Phase-5+ task.

---

## 13. Phase 3 compatibility

Untouched and still working:
- `Galaxy`, `SystemPlacer` (golden-angle), `GalaxyParameters`, `GalaxyConfig`,
  `PlanetDimensionBinding`, `PlanetDimensionConfig`, `PlanetChunkGenerator`, `test_planet`.
- All 46 existing tests.
- `GalaxyLayout` is a NEW, parallel, scalable model; it does not modify `Galaxy`/`SystemPlacer`.
  The legacy `test_planet` POC continues to use `Galaxy.getStarSystem` (golden-angle), while
  the scalable `space` dimension (Phase 6) will use `GalaxyLayout`.

---

## 14. Limitations (Phase 5 out of scope)

- No `SpaceDimension`/`ChunkGenerator` (Phase 6).
- No Creating Space travel bridge (separate phase).
- No climate/biomes/ores/vegetation generation in the space dimension.
- No migration system.
- Identity is `int`-based (via `StarSystemId`); valid up to roughly 1e6 systems for the default
  cell size; extremely large galaxies need a `long` identity (planned for `WorldgenVersion.V2`).

---

## 15. New files (Phase 5)

`core/galaxy/layout/`
- `SpaceConstants.java`
- `WorldgenVersion.java`
- `GalaxyCoordinate.java` - lookup key (GU).
- `StarSystemPosition.java` - 2D system position.
- `PlanetPosition.java` - 2D planet position.
- `PlanetInfluenceRegion.java` - mathematical region (extensible radii).
- `SpatialGrid.java` - cell math + zigzag-Cantor index (package-private).
- `PlanetPlacer.java` - deterministic orbit placement (package-private).
- `GalaxyLayout.java` - facade + chunk-lookup pipeline entry.
- `GalaxySpatialIndex.java` - lazy ring-walk lookup + bounded LRU cache.

`core/galaxy/layout/` (tests)
- `GalaxyLayoutTest.java`
- `GalaxyLayoutPerformanceTest.java`

docs:
- `GALAXY_LAYOUT.md` (this file)

No existing source files were modified.
