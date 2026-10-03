# ACT FINAL REPORT — PLAN PHASE 1..12

Дата: 22.09.2026 · Build: `gradlew cleanTest test --no-build-cache` → **791 тест, 0 failed, BUILD SUCCESSFUL** ·
Runtime: dedicated server (`gradlew runServer`, RCON) — **server-side worldgen VERIFIED**, визуальная F3-проверка — **NOT VERIFIED** (headless-окружение).

## 1. ROOT CAUSES FIXED

| ID | Было | Стало |
| --- | --- | --- |
| C1 | температура = uniform random в таблице PlanetType (~100–900 K), orbit/star не участвовали | температура выводится из star system + orbit + albedo + greenhouse + internal heat; PlanetType — структурный архетип (`reconcileType`) |
| C2 | холодные миры получали liquid water (CRYOGENIC → WATER) | `WaterPhaseModel`: T < 273.15 K → liquid запрещён (hard invariant, тест `coldWorldsReportNoStandingLiquidWater`) |
| C3 | linear normalization 70–1000 K | monotonic log: `T01 = ln(T/30)/ln(4600/30)`, clamp [0,1]; TemperatureBand по реальным Kelvin |
| H1 | PlanetPosition.radius = физический orbit | layout-радиус отделён от `OrbitProfile.orbitAU` (0.05–60 AU, log-space) |
| H2 | луны не зависели от parent/star | `MoonThermalModel`: stellar inherit + planetshine + tidal heating |
| H3 | Biome/Geology/Material карты частично независимы | ONE environment → ONE ecological context → ONE biome decision → ONE geology → ONE material decision (`PlanetaryEnvironment`, `PlanetMaterialRoleSelector`) |
| H4 | нет fissures/ravines/sinkholes/gullies/crevasses | `LandformBudget` + `LandformField` (budget-driven, между macro relief и hills) |
| R21/R22 | terrain слишком гладкий, биомы-«сектора» | hills как mid-frequency field (10–40 blocks), warped region sites, sub-biome layer |

## 2–4. THERMAL / ORBIT / MULTI-STAR MODEL
- `core/physics/StellarThermalModel` — канон **30–4600 K** (DESIGN-диапазон; prior = DESIGN POPULATION PRIOR, не научное утверждение). F_total = Σ L_i/d_i² (все звёзды, не nearest-star-only), orbit-averaged flux (ecc), T_eq = 278.5·F^0.25·(1−A)^0.25, bounded greenhouse по pressure01, internal heating (tectonic/geothermal/age), albedo derived от PlanetType. Log-normalization.
- `core/physics/OrbitProfile.forSlot(planetSeed, orbitIndex, totalLuminosity)`: log-space slots, r ∝ L^0.35, eccentricity bimodal 0…0.25, fluxAveragingFactor = 1/√(1−e²) < 2. Galaxy layout не тронут.
- Планетарных thermal classes — **8**: CRYOGENIC, FROZEN, COLD, TEMPERATE, WARM, HOT, VERY_HOT, MOLTEN.

## 5. PLANET TEMPERATURE DISTRIBUTION (n = 1112, 3 world seeds)
min **91.9 K** · p50 **359.2** · p75 447.8 · p90 542.7 · **p95 649.6 K** · p99 1137.3 · max **1987.5 K**.
Гистограмма: <200 K: 48 (4.3%) · 200–400: 644 · 400–600: 352 · 600–800: 34 · 800–1000: 20 · >1000: 14 (1.3%) — non-uniform, экстремумы редки.

## 6–7. MOON THERMAL / TIDAL (n = 1731)
T: min 123.5 / p50 340.2 / p90 513.3 / max 1943.4 K; mean |T_moon − T_parent| = 43 K; «родитель >420 K → луна <200 K» = 0/1731; planetshine ≤ 0.169; tidal >0.40 — 55 лун, <0.05 — 1564, max 1.000. Классы лун (**6**): COLD_FROZEN 293 / COLD_GEOLOGICALLY_ACTIVE 10 / TEMPERATE 442 / WARM 545 / HOT 369 / VOLCANIC 72. Tidal → geology вероятностно (`strongTidesDoNotAlwaysMeanVolcanoes`).

## 8–9. WATER PHASE / HYDROLOGY
SOLID/LIQUID/VAPOR/NONE (+MIXED 273.15–277 K), pressure-boiling (VACUUM 250 K … CRUSHING 450 K), local T = baseline × climate ±37% × elevation lapse, geothermal pockets (только GEOTHERMAL/VOLCANIC, flux > 0.55). Замеры: 41 K → solid 100%; 175 K → solid 100%; 289 K wet → liquid 95%/solid 3%; 336 K → liquid 62%/solid 27%/vapour 9%; 2781 K → none 100%. UI и worldgen считают из одного бленда; фазы UI↔F3 идентичны (`ThermalFactsTest`).

## 10–12. BIOME ECOLOGY / SUB-BIOME / TRANSITIONS
Macro diameter median **2056 blocks** (888–2056 по эталонам), transitions **0.03–0.48 changes/1000** (R22 transect: 2176 blocks, 0.35/1000). Macro families: 1–7 (41 K — одна ICE-семья, 5 sub-biome'ов). Sub-biome 200–800 blocks, 5–7 distinct на планету, зависит от macro+climate+elevation+wetness+geology. Compatibility: T<200 K → нет HOT; T>500 K → нет FROZEN.

## 13–14. COLOR THEME / MATERIAL COHERENCE
`PlanetColorTheme` (10 темм, thermal-фильтр) → `PlanetMaterialRoleSelector` (THEME × PROVINCE × SURFACE). Замер (7 превью): dominant **0.70–0.78**, secondary **0.14–0.25**, geologic 0.04–0.11 (host-rock провинции), accent 0.004–0.011; theme compatibility 0.20–0.94 (низкие значения — легитимные полярные шапки). Secondary recalibrated: `variation01` wavelength 1100→480, threshold 0.62→0.66 → **planet-wide ≈0.19, стабильно по сидам (0.186–0.194)**. Material switching 0.92–2.74/1000, patch median 208–528 blocks.

## 15–16. LANDFORMS + TERRAIN
`LandformBudget` (gully/ravine/fissure/sinkhole/crevasse/depression) + `LandformField` (anisotropic drainage, warped line cracks, cellular bowls), bounded 0.55·amplitude, gravity 0.75–1.25. Measured cut coverage: DRY+ERODED **61.4%** (deep 18.7%) · HOT_ARID 40.1% · FLAT_TEMPERATE 2.9% · MOUNTAINOUS 2.4% · GLACIAL 1.9% · DEAD FLAT **0.0%**. Порядок: GLOBAL→CONTINENT→MACRO→RANGE→VALLEYS→**LANDFORMS**→HILLS→DETAIL. Mountain coverage: FLAT 0.02 / ROLLING 0.05 / BASIN_RICH 0.08 / GLACIAL 0.18 / MIXED 0.26 / CANYONLAND 0.28 / MOUNTAINOUS 0.42.

## 17. SURFACE STRATA
`SurfaceStrata` + landform exposure (6.9): surface 1–4 / subsurface 4–12 / deep 12+; F3: `water phase=… strata surf=… sub=…`.

## 18–20. UI / DIAGNOSTICS / MAP PREVIEWS
- Панель навигации: `Temperature = X K (Y C)` + `Thermal class` + `Orbit/Solar flux` + `Water phase` + **`Pressure`** (планета и луна, PHASE 9.3); луны: `Tidal heating`.
- F3: `thermal.describe` + water phase + strata (`PlanetChunkGenerator`).
- Headless: `TerrainDiagnostics.planetSummary()` — секции PHYSICS/CLIMATE/RELIEF/HEIGHT/BIOMES/BIOME MAP/MATERIAL/LANDFORMS/WATER (records `Physics`, `LandformStats`, `MaterialShares`, `WaterPhases`); `tools/MapPreview` — **12 карт** (biome, height, temperature, humidity, material, province, subbiome, surface, waterphase, registeredbiome, **landform**, **pressure**) + facts.txt + metrics.txt (секционный summary, theme compatibility, macro regions, cut coverage).

## 21. FILES ADDED
`core/physics/`: StellarThermalModel, MoonThermalModel, OrbitProfile · `core/planets/`: PlanetThermal · `core/worldgen/`: biome/SubBiome, fluids/WaterPhaseModel, geology/ProvinceField, materials/PlanetBiomeIdentity, materials/PlanetMaterialRoleSelector, profile/PlanetaryEnvironment, surface/SurfaceStrata, terrain/LandformBudget, terrain/LandformField · ресурсы: 16 registered biome JSON (`worldgen/biome/planet_*_{a,b}.json`) · тесты: StellarThermalModelTest, OrbitProfileTest, MoonThermalModelTest, ThermalFactsTest, WaterPhaseModelTest, SubBiomeTest, LandformFieldTest, R22BiomeEcologyTest, R22MapPreviewTest, R23ClimateCoherenceTest, R23MaterialCoherenceTest, R23ProvinceNestingTest, tools/MapPreview.

## 22. FILES CHANGED (осн.)
RocketControlNavigationScreen, GalaxyLayout, MoonProperties/MoonPropertyGenerator/Planet/PlanetProperties/PlanetPropertyGenerator/StarSystem, BiomeRegionMap/PlanetBiome*/PlanetBiomeSelector, ClimateArchetype/PlanetClimateField/PlanetClimateProfile, FluidFamily, PlanetGeologyPalette/PlanetGeologyProfile, MaterialCatalog/MaterialZoneMap/PlanetColorTheme/PlanetMaterialSelector, PlanetPhysicalProfileFactory/TemperatureBand, TerrainDiagnostics/TerrainShaper, VegetationSelector/PlantDefinition, DynamicPlanetWorldManager, PlanetBiomeSource/PlanetChunkGenerator/PlanetFeaturePlacer/PlanetFluids, SpaceBiomeSource/SpaceChunkGenerator, surface.json ×4; удалён MaterialZoneMapTest (устаревший lottery-контракт). В этой итерации: MapPreview (фикс compile-ошибки, +landform/pressure карты, секционный metrics, theme compatibility), TerrainDiagnostics (+PHASE 10 секции, runtime material seed), TerrainDiagnosticsTest (+4 теста), R22MapPreviewTest (12 карт + metrics), RocketControlNavigationScreen (+Pressure ×2), MaterialZoneMap (480), PlanetMaterialRoleSelector (0.66).

## 23–24. TEST COUNT / BUILD
**791 тест (114 классов), 0 failed** — `gradlew cleanTest test --no-build-cache` → BUILD SUCCESSFUL in 1m 26s. Новые в этой итерации: 4 PHASE 10/11 теста в `TerrainDiagnosticsTest` (summary sections, cold-world liquid=0, warm-wet liquid>0, landform budget) + расширенный `R22MapPreviewTest` (12 карт + секции metrics.txt).

## 25. RUNTIME (dedicated server, RCON)
- Сервер: `gradlew runServer` → **Done (1.488s)**, worldSeed −7206522302591668930.
- Канонические температуры с РЕАЛЬНОГО сервера (`/unlimitedspace planet`): system_0000_planet_00 (OCEAN) **1743.89 K**, planet_01 (BARREN) **1291.56 K**, system_0001_planet_00 **417.89 K**, system_0002_planet_00 (VOLCANIC) **441 K**, system_0003_planet_00 **951.23 K**, system_0010_planet_00 **469.29 K**, **system_0010_planet_02 (ICE) 263.4 K** (water 73%, MODERATE), system_0042_planet_01 **335.97 K**.
- `/unlimitedspace nav 10 3 0`, `nav 10 4 0` → `Destination ready`; dynamic worlds materialized (`kind=PLANET_SURFACE rl=…/system_0010_planet_0{2,3}/surface generator=PlanetChunkGenerator`), setup 30.3 ms.
- Ошибок worldgen в `latest.log` НЕТ (только штатные mixin/tag warnings + vanilla MobSpawnSettings warning).
- **VISUAL F3 CHECKS (A–H): NOT VERIFIED** — требуют живого клиента; headless-превью их не заменяет.

## 26. PERFORMANCE
StellarThermalModel / OrbitProfile / PlanetaryEnvironment — once per planet; biome macro fields, theme, landform budget — cached; O(1) sampling колонок, zero allocation в hot path (детерминизм- и bounds-тесты).

## 27. MIGRATION
Worldgen outcomes изменились (температура, материалы, secondary-поле 1100→480). `WorldgenVersion` = **V1_GRID** без изменений — тег отслеживает galaxy layout, который не менялся; планетарные изменения зафиксированы этим отчётом. Planet ID / destinations / CS metadata / DynamicDimensions / navigation не тронуты; существующие чанки сохраняют старые блоки, новые генерируются по новой модели.

## 28. KNOWN LIMITATIONS
1. `pressure.png` — planet-level swatch (локального поля давления в модели нет).
2. У границ фаз локальная доля дрейфует (мир 371 K/MODERATE: глобально «Liquid water», локально ~99% vapour — окно ±37% накрывает точку кипения).
3. Theme compatibility считается по всей площади, включая полярные шапки (C_MOUNTAINOUS 0.20, F_COLD_GLACIAL 0.33 — легитимный лёд понижает метрику).
4. Secondary-доля в малых окнах (≤4 км) дрейфует (52/39 на det-мире); planet-wide стабильно ≈81/19.
5. ESCARPMENT как отдельный тип не реализован (плато-террасы дают эквивалент); MESA — plateau terracing, DUNE — duneField.

## 29. REMAINING RISKS
- Визуальная приёмка (19 состояний + критические проверки A–H) не выполнена живым клиентом — риск косметических артефактов, невидимых в headless.
- Earth-like миры (288–300 K) в выборке редки (p50 359 K) — при желании доля temperate поднимается через orbit prior (DESIGN POPULATION PRIOR).
- Локальный дрейф secondary в малых окнах — мониторить по metrics.txt при будущих правках палитр.


