# ACT STATUS — PHASE 1..12 (какие возможности реально подключены)

Дата: 2026-09-21 · Проверка: `gradlew.bat test` → **775 тестов, BUILD SUCCESSFUL**.

## Как читать этот отчёт

В коде живут **две разные нумерации**, их нельзя смешивать:

| Стиль маркера | Что это | Где |
| --- | --- | --- |
| `PHASE n` | **текущий PLAN** (R-серия) | `core/physics`, `core/planets`, `core/worldgen/*`, `worldgen/planet` |
| `Phase n` (заглавная P + пробел) | **старый POC-план** | `worldgen/space/SpaceChunkGenerator.java`, `UnlimitedSpace.java:183`, `docs/GALAXY_LAYOUT.md`, `PlanetStructure`/`VegetationSelector` |
| `Rnn` | исходные research-фазы (R10…R22) | тесты, `SpaceWorldgenRegistries` (R11 asteroid) |

Столбец «игрок видит» = фактическая точка контакта: панель навигации (`RocketControlNavigationScreen`), F3-оверлей (`PlanetChunkGenerator`), headless-превью (`test/tools/MapPreview`), логи мира.

---

## PHASE 1 — звёздная/планетарная тепловая модель

- **Канон:** `core/physics/StellarThermalModel.java` (:10, :38 LOG-нормализация Kelvin, :237, :267, :305 `temperatureText`), `core/physics/OrbitProfile.java:6`, `core/planets/PlanetThermal.java` (:6, :62 `describe`), `core/planets/PlanetProperties.java` (:65, :101 — `thermal()` **никогда не null**, fallback `PlanetThermal.none`), `core/planets/PlanetPropertyGenerator.java` (:39, :54, :79), `core/stars/StarSystem.java:95`.
- **Игрок видит:** панель навигации планеты: `Temperature = 288 K (15 C)`, **`Thermal class`**, и для миров с выведенной от звезды температурой — `Orbit = X AU (e ..)`, `Solar flux = X.XXx Earth (T_eq N K)`.
- **F3:** `PlanetChunkGenerator.java:470-471` — одна каноническая строка `thermal.describe(...)`.
- **Тесты:** `core/planets/ThermalFactsTest.java` (шкала K/C, ярлыки классов, orbit/flux, legacy «no stellar context»).
- **Статус: WIRED** (ядро → UI → F3 → тесты).

## PHASE 2 — луны

- **Канон:** `core/physics/MoonThermalModel.java` (:7, :141, :184 — `MoonThermalClass.displayName()`), `core/planets/MoonProperties.java` (:45, :67, :72, :77), `core/planets/MoonPropertyGenerator.java` (:21, :45, :66, :93).
- **Игрок видит:** панель навигации (SATELLITE): `Temperature = 150 K (-123 C)`, **`Thermal class`**, **`Tidal heating = N%`** (при наличии тепловой модели).
- **Тесты:** `core/physics/MoonThermalModelTest.java` (+ печать распределения температур по лунам).
- **Статус: WIRED.**

## PHASE 3 — фазово-осознанные жидкости и вода

- **Канон:** `core/worldgen/fluids/WaterPhaseModel.java` (:10, :73 `Phase.displayName`, :108 `ofProperties(...)` — вход из сырых свойств, :142, :163 geothermal-карманы), `FluidFamily.java:41`, `worldgen/planet/PlanetFluids.java:24`, `PlanetChunkGenerator.java` (:112, :200, :213, :270), `PlanetFeaturePlacer.java:154`, `PlanetMaterialProfile.java:72`.
- **Игрок видит:** панель навигации, строка **`Water phase` = Ice / Liquid water / Ice & liquid / Water vapour / No water** (цвет по фазе), и для планет, и для лун (у лун water availability = water coverage, humidity у них нет).
- **F3:** `PlanetChunkGenerator.java:472` — `water phase=<label> strata surf=.. sub=..`.
- **Инвариант:** UI и мир считают воду из **одного** бленда `PlanetPhysicalProfileFactory.waterAbundance(coverage, humidity)` (`PlanetPhysicalProfileFactory.java:100`), поэтому фаза в панели совпадает с фазой, которую разместит worldgen.
- **Тесты:** `WaterPhaseModelTest`, `PlanetSeedPipelineTest`, `ThermalFactsTest` (равенство фазы UI↔worldgen по всем планетам системы; запрет «жидкой воды» и «пара» на мёрзлом мире).
- **Статус: WIRED.**

## PHASE 4 — суб-биомы

- **Канон:** `core/worldgen/biome/SubBiome.java:7`, `PlanetBiomeSelector.java:39`, `PlanetGeologyProfile.java:107`, `PlanetMaterialProfile.java:75`, `PlanetMaterialSelector.java:51`, `worldgen/planet/PlanetChunkGenerator.java:523`.
- **Тесты:** `core/worldgen/biome/SubBiomeTest.java:15`.
- **Игрок видит:** пока **ничего** — суб-биом не выведен ни в панель, ни в F3.
- **Статус: генератор WIRED, поверхность для игрока — SCAFFOLDED** (первый кандидат на «доделать»).

## PHASE 5 — масштабируемая раскладка галактики

- **Канон:** `core/galaxy/layout/GalaxyLayout.java` (:141, :155), `SpaceConstants.java` (:4, :19), `PlanetInfluenceRegion.java:6`, `WorldgenVersion.java:9`, `worldgen/space/adapter/BlockPosToGalaxyCoordinate.java:10`, `PlanetGeologyProfile.java:53`, `MaterialZoneMap.java:38`.
- **Док:** `docs/GALAXY_LAYOUT.md` (Phase 5) — там же описан адаптер Phase 6, который теперь существует.
- **Статус: WIRED** (канон + адаптер + тесты `SpaceLookupTest`).

## PHASE 6 — формы рельефа (landforms)

- **Канон:** `core/worldgen/terrain/LandformBudget.java:6`, `LandformField.java` (:8, :80), `TerrainShaper.java` (:54, :141, :147, :306), `worldgen/planet/PlanetChunkGenerator.java:369`, `SpaceWorldgenRegistries.java:11`.
- **Тесты:** `core/worldgen/terrain/LandformFieldTest.java:13` (бюджет детерминирован и выведен из профиля, «нет стен» между колонками, фиссуры/кревассы по тектонике/ледникам).
- **Игрок видит:** визуально — да (сам рельеф), в тексте — **нет**.
- **Статус: worldgen WIRED, вывод для игрока — SCAFFOLDED.**

## PHASE 7 — приоритет ландшафта в порядке рельефа

- **Единственный маркер:** `core/worldgen/terrain/LandformField.java:61` — «landforms sit BETWEEN macro relief and regional hills» (порядок применения + ограничение `0.55 * amplitude`).
- **Статус: правило WIRED** (реально исполняется в `TerrainShaper.surfaceHeight`), но отдельного вывода/теста именно на «порядок» нет. **Нужно подтверждение, что в PHASE 7 закладывалось ещё что-то** — иначе фаза закрыта.

## PHASE 8 — страта поверхности, ресурсы, материалы, диагностика

- **Канон:** `core/worldgen/surface/SurfaceStrata.java:7`, `core/worldgen/resources/PlanetResource.java:6`, `PlanetResourceSelector.java:11`, `core/worldgen/materials/MaterialFamily.java:4`, `PlanetChunkGenerator.java` (:114, :221, :375, :385, :417), `SpaceChunkGenerator.java:245` (одна сводка на чанк для сверки блоков).
- **Игрок видит:** панель — `Minerals`, `Fuel abundance` и т.п.; F3 — страты (`surf=`, `sub=`).
- **Статус: WIRED.**

## PHASE 9 — (внимание: маркер переиспользован)

**(a) Старый POC-смысл:** структуры + растительность — `PlanetStructure.java` (:4, :17), `StructureSelector.java:14`, `PlantDefinition.java:6`, `VegetationSelector.java:10`, `SpaceChunkGenerator.java` (:124, :185, :225).

**(b) Текущий смысл (эта итерация): канонические тепловые/водные факты для игрока и диагностики**

- **Канон:** `StellarThermalModel` (:44 `celsius`, :53 `temperatureText`, :305 `ThermalClass.displayName`), `MoonThermalModel:184`, `PlanetThermal:62` (`describe`), `WaterPhaseModel` (:73, :108), `PlanetPhysicalProfileFactory:100` (`waterAbundance`).
- **Игрок видит:** панель навигации — планета (:956-966) и луна (:1267-1275); F3 — `PlanetChunkGenerator:470-472`; headless — `MapPreview` (:30, :45 перегрузка с `PlanetThermal`, :102-111 `facts.txt`) + проверки в `R22MapPreviewTest:43-50`.
- **Артефакт прогона:** `build/map-previews/<PLANET>/facts.txt`, например
  `surface temperature : 336 K (63 C)` / `thermal : 336 K (63 C) | warm | no stellar context` / `water phase : Liquid water`.
- **Тесты:** `ThermalFactsTest` (8 кейсов), `R22MapPreviewTest` (факт-лист рядом с PNG).
- **Статус: WIRED** (ядро → UI → F3 → превью → тесты).

## PHASE 10–12 — SCAFFOLDED (не реализованы)

- В `src` **нет ни одного маркера `PHASE 10`, `PHASE 11`, `PHASE 12`** (поиск по всем `.java`, включая тесты): ни классов, ни тестов, ни комментариев под этими номерами.
- Косвенное свидетельство прошлых ручных прогонов: имена сохранений в `run/crash-reports/*` — `Phase 11.1 test 2`, `Phase 12.3 cat` (измерение `unlimitedspace:asteroid/...`), плюс комментарий `UnlimitedSpace.java:185` «R11: dedicated asteroid worldgen codecs». То есть астероидное измерение в коде помечено **R11**, а не «PHASE 11».
- **Вывод:** следующая невыполненная фаза — PHASE 10, но её содержание в репозитории не зафиксировано: нужен текст плана (или выбор из вариантов ниже).

---

## Сводка (одним экраном)

| PHASE | Возможность | Ядро | UI | F3 | Превью | Тесты | Итог |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | тепло планеты от звезды | да | да | да | да | да | **WIRED** |
| 2 | тепло луны (прилив/планетосвет) | да | да | — | — | да | **WIRED** |
| 3 | фазо-осознанные жидкости | да | да | да | да | да | **WIRED** |
| 4 | суб-биомы | да | нет | нет | нет | да | частично |
| 5 | раскладка галактики | да | — | — | — | да | **WIRED** |
| 6 | ландформы | да | нет | нет | нет | да | частично |
| 7 | порядок ландшафта | да | нет | нет | нет | да | правило WIRED |
| 8 | страты/ресурсы/материалы | да | да | да | да | да | **WIRED** |
| 9b | канонические факты | да | да | да | да | да | **WIRED** (эта итерация) |
| 10–12 | — | нет | нет | нет | нет | нет | **не начаты** |

## Ближайшие логичные шаги (по возрастанию объёма)

1. **Закрыть «дырки» PHASE 4/6 в UI** (низкий риск, чистый выигрыш): строки `Sub-biome` и `Landform` (бюджет/категория колонки) в панель навигации + `subbiome.png`/`landform.png` в `MapPreview`, используя уже существующие `PlanetBiomeSelector`/`LandformField`.
2. **PHASE 10**: сохранить текст плана в репозиторий (например `docs/PLAN.md`) и реализовать по той же схеме: ядро → UI/F3 → headless → тесты.
3. **Диагностический отчёт по вселенной** (`build/reports/`): прогон по системам сида и печать `thermal.describe` + `Water phase` для всех планет/лун — быстрый способ поймать «невозможные» миры (лёд при 400 K и т.п.).

## Команды проверки

```
gradlew.bat test                                  # 775 тестов, BUILD SUCCESSFUL
gradlew.bat test --tests "*ThermalFactsTest*"
build/map-previews/<PLANET>/facts.txt             # канонический факт-лист рядом с PNG
```

> Примечание: Kelvin — источник истины для физики, Celsius и тексты — только отображение. Любая новая строка UI обязана брать значения из `StellarThermalModel` / `MoonThermalModel` / `WaterPhaseModel`, а не считать заново.
