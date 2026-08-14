# Creating Space 1.7.18 — Архитектура

Документ для addon «Unlimited Space» (цель — процедурная генерация галактики, star systems и планет поверх существующей системы ракет/взлёта/путешествия Creating Space).

Анализ выполнен по исходникам официального репозитория:
`RealAntEngineer/creating_space`, ветка `1.21.1-release`, коммит `7f7f79fd31b659ab1fed43fd29649cfa9e2c845e` (MC 1.21.1 / NeoForge 21.1.248 / Create 6.0.9-216), тот же билд, что используется в dev-environment как `creatingspace-1.7.18.jar`.

---

## 0. Общая модель (главный вывод)

Creating Space построен на **datapack-реестрах**, а не на жёстко зашитых классах планет:

- «Планета» = **запись в datapack-реестре** `creatingspace:rocket_accessible_dimension`
  (`com.rae.creatingspace.api.planets.RocketAccessibleDimension`). Реестр создаётся официальным NeoForge-API
  `DataPackRegistryEvent.NewRegistry.dataPackRegistry(...)` (см. `CreatingSpace.java:54-61`) с `sync(true)`.
- «Измерение» = **datapack-JSON** (LevelStem / DimensionType / NoiseGeneratorSettings / Biome), генерируемые через
  `RegistrySetBuilder` в `content.datagen.CSWorldGenProvider`.
- Логика реюза (гравитация, кислород, телепорт, выбор destination, стоимость путешествия) **читает эти реестры**,
  а не хардкод-перечисление. Значит addon может **добавлять записи, не трогая код CS**.

---

## 1. Планеты

- `com.rae.creatingspace.api.planets.RocketAccessibleDimension` — **datapack-реестровый объект** «небесное тело / точка путешествия».
  - Поля: `distanceToOrbitingBody`, `orbitedBody` (ResourceLocation другого небесного тела), `arrivalHeight`, `gravity`
    (float), `adjacentDimensions` (Map&lt;ResourceLocation, AccessibilityParameter{deltaV, arrivalHeight}&gt;).
  - Public API: конструктор; getter'ы `gravity()`, `arrivalHeight()`, `orbitedBody()`, `distanceToOrbitedBody()`,
    `adjacentDimensions()`; статические `REGISTRY_KEY`, `CODEC`, `AccessibilityParameter` (record с `CODEC`).
  - **Доступ напрямую: да. Расширяем: да (новые записи/подклассы). Internal: нет** — публичная API-модель.
  - **Mixin: нет. AT: нет.** Безопасный способ: запись в тот же датапак-реестр.
- `com.rae.creatingspace.content.planets.CSDimensionUtil` — статические утилиты чтения реестра.
  - Public API: `getPlanets()` → List&lt;ResourceLocation&gt;; `getTravelMap()`; `updatePlanetsFromRegistry(...)`;
    `cost(from,to)`; `gravity(...)`; `arrivalHeight(...)`; `isOrbit(...)`; `planetUnder(dimension)`;
    `hasO2Atmosphere(biome)`.
  - **Доступ: да (public static).** Internal-нюанс: `getPlanets()`/`getTravelMap()` на клиенте читают реестр через
    `Minecraft.getInstance().getConnection()` (комментарий автора: «will fail on dedicated server»), на сервере
    `registryAccess` заполняется из `DataEventHandler.onServerStarted`. **Не расширяется (static-утилиты).**

## 2. Звёздные системы

- `com.rae.creatingspace.api.planets.Star` — **пустой класс-заглушка**. Javadoc: *«will be used to add other solar
  system. don't use it now»*.
- **Вывод: системы звёзд и «галактика» в 1.7.18 НЕ реализованы.** Есть только граф `adjacentDimensions` внутри
  `RocketAccessibleDimension`. Это целиком зона для вашего addon.

## 3. Dimensions

- `com.rae.creatingspace.init.worldgen.DimensionInit` — статические `ResourceKey` всех дим CS:
  `EARTH_ORBIT_KEY`, `MOON_ORBIT_KEY`, `MOON_KEY`, `MARS_KEY`, плюс `ResourceKey<LevelStem>` и
  `ResourceKey<DimensionType>` (`EARTH_ORBIT_TYPE`, `MARS_TYPE`, ...).
  - **Доступ: да (public static константы). Расширяем: нет.** Добавочные дим — свои ключи.

## 4. DimensionType

- `com.rae.creatingspace.content.datagen.worldgen.CSDimensionTypeGen` — `bootstrap(BootstrapContext<DimensionType>)`
  регистрирует DimensionType для earth_orbit / mars / mars_orbit / moon_orbit / the_moon / venus
  (minY=-64, высота 384, логическая высота 384, `MonsterSettings`, `BlockTags.INFINIBURN_OVERWORLD`).
  - data-driven; addon делает то же через свой `RegistrySetBuilder`. **Mixin/AT: нет.**

## 5. ChunkGenerator

- `com.rae.creatingspace.content.datagen.worldgen.CSDimensionGen` — `bootstrap` регистрирует LevelStem:
  - Орбиты: `FlatLevelSource` (пустой «space»-биом).
  - Терра-миры (mars, moon): **`NoiseBasedChunkGenerator`** (ванильный) + кастомные `NoiseGeneratorSettings`.
- **Собственного класса ChunkGenerator в CS нет** — используются ванильные `NoiseBasedChunkGenerator`/`FlatLevelSource`.

## 6. BiomeSource

- Только ванильные: **`MultiNoiseBiomeSource`** (`createFromList` из `Climate.ParameterList`, параметры в record
  `BiomeParams`) для mars/moon и **`FixedBiomeSource`** для «space»/орбит. Собственного BiomeSource нет.

## 7. Terrain generation

- `CSDimensionGen` (LevelStem + генераторы) + кастомные `NoiseGeneratorSettings` (mars_noise, moon_noise) + **carver**:
  `init.worldgen.CarverInit` (`DeferredRegister<WorldCarver<?>>` в `Registries.CARVER`, регистрирует `CraterCarver`) и
  `content.planets.worldgen.CraterCarver` / `CraterCarverConfig`.
- Террейн = datapack-worldgen (NoiseGeneratorSettings / Carver / Biome).

## 8. Noise generation

- Ванильная: `NoiseBasedChunkGenerator` + `NoiseGeneratorSettings`. Собственных noise-систем нет. Мир seed'ится
  уровнем автоматически (seed-зависимо), в CS нет явного поля seed.

## 9. Planet registration

- `content.datagen.worldgen.CSRocketAccessibleDimensionGen.bootstrap` — добавляет записи
  `earth_orbit, the_moon, moon_orbit, mars, mars_orbit, venus` в реестр `RocketAccessibleDimension`. Это «набор планет».
  Addon добавляет свои записи в тот же реестр.

## 10. Dimension registration

- `content.datagen.CSWorldGenProvider` → `RegistrySetBuilder`
  `.add(DIMENSION_TYPE) .add(LEVEL_STEM) .add(RocketAccessibleDimension.REGISTRY_KEY)` →
  `DatapackBuiltinEntriesProvider` (NeoForge `GatherDataEvent`). Carvers — отдельный `DeferredRegister` в `CarverInit`.

## 11. Rocket

- `com.rae.creatingspace.content.rocket.RocketContraptionEntity` — сущность ракеты (цепочка Create
  `AbstractContraptionEntity`). Поля: `destination` (ResourceLocation), `originDimension`, `initialPosMap`
  (HashMap&lt;ResourceLocation,BlockPos&gt;), `schedule` (`RocketScheduleRuntime`).
  - Public API: `static create(level, contraption, destination)`, `deltaV()`, `startNavigation(path)`,
    `isInPropulsionPhase()`, `changeDimension(...)`.
- `content.rocket.contraption.RocketContraption` — компактция/массы/топливо (`getThrust()`, `getStorage().getFluids()`).
- `content.rocket.CustomTeleporter` — создаёт `DimensionTransition` на `arrivalHeight` (для ракеты — по координатам
  `initialPosMap` у цели).
- `content.rocket.squedule.*` — `RocketSchedule`, `RocketScheduleRuntime`, `RocketPath(from,to,cost)`, `ScheduleEntry`,
  `ScheduleInstruction`, условия/инструкции.
- `content.rocket.rocket_control.RocketControlsBlockEntity` — управление/запуск; `network.*`, `flight_recorder.*`,
  `contraption.behaviour.*` (взаимодействия/движение).

## 12. Rocket launch

- Сборка как Create-контрапшн → `RocketContraptionEntity.create(...)` → расписание `RocketScheduleRuntime.tick` → при
  `DestinationInstruction`: `startCurrentInstruction()` (стоимость `CSDimensionUtil.cost(from,to)`, создаёт
  `RocketPath`) → `rocket.startNavigation(path)`.
- В полёте: `tickConsumptionAndSpeed()` считает тягу/расход топлива (`deltaV`), гравитацию через
  `CSDimensionUtil.gravity(dim)`.

## 13. Space travel

- `RocketScheduleRuntime` (State PRE/IN/POST_TRANSIT) + `RocketPath` + карта затрат `CSDimensionUtil` (Dijkstra по
  `adjacentDimensions` из реестра). Стоимость перелёта = сумма `deltaV`.

## 14. Destination selection

- `content.rocket.squedule.instruction.DestinationInstruction` — инструкция расписания; хранит `ResourceLocation` в
  строке «Text»; на клиенте список опций берётся из `CSDimensionUtil.getPlanets()` (реестр планет) через
  selection-scroll (клиентский GUI в `ScheduleMakingScreen` / `api.gui.elements.*`).
- `RocketContraptionEntity.destination` → ResourceLocation; телепорт по `this.destination`.

## 15. Teleportation

- `CustomTeleporter.getTransition(entity, destServerLevel)` → `DimensionTransition` на высоте `arrivalHeight`, в позиции
  `initialPosMap` (для ракеты).
- `RocketContraptionEntity.changeDimension(transition)` — переопределение: `CommonHooks.onTravelToDimension`,
  `unRide()`, телепорт пассажиров и сталкивающихся сущностей, создание нового экземпляра ракеты, `restoreFrom`.
- Вызов: `tickDimensionChangeLogic()` — если `y > 300` и не reentry → `changeDimension(CustomTeleporter.getTransition(...))`,
  где `destServerLevel = server.getLevel(ResourceKey.create(DIMENSION, this.destination))`.
- Падение из орбиты: `CSEventHandler.entityLivingEvent` (`EntityTickEvent.Pre`) → `CSDimensionUtil.planetUnder(dim)` →
  `changeDimension(CustomTeleporter.getTransition(...))`.

## 16. World generation

- `CSWorldGenProvider` (datagen RegistrySetBuilder) → datapack-JSON (DIMENSION_TYPE, LEVEL_STEM,
  rocket_accessible_dimension) + `CarverInit` (carvers). Биомы задаются по ключу в `CSDimensionGen` через
  `biomeGetter.getOrThrow(resource("mars_plains"))` и определяются datapack-worldgen'ом. Пайплайн — стандартный
  ванильный datapack (перекрытие via `data/<ns>/dimension` и т.д.).

## 17. Seeds

- CS не хранит/не использует seed напрямую. `NoiseBasedChunkGenerator` + `NoiseGeneratorSettings` seed'ятся миром
  автоматически → вариативность рельефа от сида есть, но набор планет и их параметры от сида **не зависят**
  (реестры — datapack, сид-независимы). Процедурности от сида нет.


## 18. Registries

- **NeoForge DataPackRegistries** (`DataPackRegistryEvent.NewRegistry`, mod-bus): `creatingspace:rocket_accessible_dimension`
  (RocketAccessibleDimension), `propellant_type` (PropellantTypeInit), `power_pack_type` / `exhaust_pack_type`
  (MiscInit.Keys). Все с `sync(true)` (кроме pack-type).
- **DeferredRegister (CreateRegistrate + NeoForge)**: BlockInit, ItemInit, BlockEntityInit, EntityInit, FluidInit,
  SoundInit, ParticleTypeInit, MenuTypesInit, DataComponentsInit, CreativeModeTabsInit, CarverInit (WorldCarver).
- **Worldgen через RegistrySetBuilder/datagen**: DIMENSION_TYPE, LEVEL_STEM, rocket_accessible_dimension
  (+ NoiseGeneratorSettings/Biome-параметры в ресурсах worldgen).

## 19. NeoForge events (используемые CS)

- `DataPackRegistryEvent.NewRegistry` (datapack-реестры), `RegisterCommandsEvent`, `RegisterCapabilitiesEvent`
  (mod-bus), `AddReloadListenerEvent`, `ServerStartedEvent`, `PlayerEvent.PlayerLoggedInEvent`, `LevelEvent.Load`,
  `EntityTickEvent.Pre` (гравитация/падение/кислород), `SleepFinishedTimeEvent`, `BlockEvent.NeighborNotifyEvent`
  (sealer-комнаты); клиент: `RegisterDimensionSpecialEffectsEvent`, `RegisterGuiLayersEvent`, `ClientTickEvent.Post`,
  `EntityRenderersEvent.AddLayers`, `ItemTooltipEvent`, `EntityMountEvent`, `GatherDataEvent` (datagen).

## 20. Config / data files

- `configs.*`: `CSCfgClient`, `CSCfgCommon`, `CSCfgServer`, `CSConfigs`, `CSKinetics`, `CSOxygenBacktank`,
  `CSRocketEngine`, `CSStress`, `CSConfigBase` (NeoForge `ModConfigSpec`; регистрация `CSConfigs.registerConfigs`).
- Data файлы: `src/generated/resources` (datapack worldgen из `CSWorldGenProvider`), реестр `MassOfBlockReader`
  (`AddReloadListenerEvent`), биомовый тег `TagsInit.CustomBiomeTags.NO_OXYGEN`.

---

## Ключевые классы — сводка доступа

| Класс | Package | Доступ | Расшир. | Internal | Mixin | AT | Альтернатива (безопасный API) |
|---|---|---|---|---|---|---|---|
| RocketAccessibleDimension | api.planets | public | да (записи) | нет | нет | нет | datapack-записи в реестре |
| Star (stub) | api.planets | public (пустой) | нет (системы нет) | заглушка | — | — | реализовать свою систему |
| CSDimensionUtil | content.planets | public static | нет | частично | нет | нет | читать реестр напрямую |
| DimensionInit | init.worldgen | public const | нет | нет | нет | нет | свои ResourceKey |
| CSDimensionTypeGen | content.datagen.worldgen | bootstrap | нет | нет (datagen) | нет | нет | свой RegistrySetBuilder |
| CSDimensionGen | content.datagen.worldgen | bootstrap | нет | нет (datagen) | нет | нет | свой RegistrySetBuilder |
| CSRocketAccessibleDimensionGen | content.datagen.worldgen | bootstrap | нет | нет | нет | нет | свой bootstrap/реестр |
| CSWorldGenProvider | content.datagen | class | да (подкласс) | нет | нет | нет | свой DatapackBuiltinEntriesProvider |
| RocketContraptionEntity | content.rocket | public | частично | нет | нет (можно без) | возможно | реюз как есть |
| CustomTeleporter | content.rocket | public | да | нет | нет | нет | реюз / свой DimensionTransition |
| DestinationInstruction | content.rocket.squedule.instruction | public | — | нет | нет | нет | реюз |
| RocketScheduleRuntime | content.rocket.squedule | public | нет | нет | нет | нет | реюз |
| CSEventHandler | content.event | public static | — | нет | нет | нет | реюз событий |
| DataEventHandler | content.event | public static | — | нет | нет | нет | реюз |
| CarverInit | init.worldgen | public | — | нет | нет | нет | свой DeferredRegister |
| CustomDimensionEffects | content.planets | public абстрактн. | да | сейчас нет | нет | нет | RegisterDimensionSpecialEffectsEvent |
| mixin/entity/gravity/..., mixin/recipe/... | mixin.* | internal | — | да (mixin-классы) | internal | — | addon'у не нужны |
---

## Ответы A–L

**A. Может ли addon создавать новые планеты?** Да (чистый способ). Записи в datapack-реестр
`creatingspace:rocket_accessible_dimension` (JSON датапака или свой `RegistrySetBuilder`). Без Mixin. Для реального
захода нужен парный LevelStem + DimensionType.

**B. Может ли addon создавать новые dimensions?** Да. NeoForge-пайплайн (`DatapackBuiltinEntriesProvider` +
`RegistrySetBuilder`: DIMENSION_TYPE, LEVEL_STEM). Это же делает сам CS.

**C. Можно ли создавать планеты процедурно на основе seed?** Частично. Рельеф (NoiseBasedChunkGenerator +
NoiseGeneratorSettings) seed'ится сидом → сид-процедурный рельеф возможен. Но набор/параметры планет от сида не
зависят (реестры датапак-независимы). По-настоящему сид-генерируемый набор планет требует генерации датапака под сид
на лету (публичного API в 1.21.1 нет) — см. E.

**D. Можно ли создавать star systems процедурно?** Да (для вас). Система звёзд в CS отсутствует (`Star` —
заглушка). Процедурные star systems — полностью ваша новая фича. Имеющийся `adjacentDimensions`/`orbitedBody` можно
использовать как «телескопический» каркас графа.

**E. Можно ли создавать planet dimensions динамически, не регистрируя тысячи заранее?** Нет с публичным API 1.21.1/NEO:
реестры LevelStem/Dimension заполняются из датапаков при загрузке мира/сейва, рантайм-регистрации новой дим нет.
Варианты: (1) прегенерация LevelStem датагеном (статично), (2) рантайм-запись через Mixin в
`MinecraftServer`/`ServerLevel`/`DimensionDataStorage` — тяжело и хрупко, (3) генерация датапака под сид при создании
мира и перезагрузка реестров — нештатно.

**F. Можно ли менять terrain generator существующих планет?** Да, на уровне данных. LevelStem/NoiseGeneratorSettings/
Carver/Biome — датапак-реестровые; датапак addon может переопределить `creatingspace:mars_noise`/LevelStem (приоритет
датапаков), добавить carvers/features. Менять *класс* генератора существующего LevelStem — только переопределением
его JSON; код-хук — Mixin.

**G. Можно ли использовать собственный ChunkGenerator?** Да (новый класс с Codec, чтобы LevelStem-JSON мог его
закодировать). CS их не использует (ваниль). Для большинства случаев проще `NoiseBasedChunkGenerator` + свои
NoiseGeneratorSettings. Полностью кастом/процедурный — свой ChunkGenerator (+ при необходимости регистрируемый кодек).

**L. Где потребуются Mixins, если потребуются.**
- Не нужны для: добавления планет/дим/destinations, чтения реестров, своих worldgen (всё через реестры/события).
- Понадобятся только для: (1) рантайм-динамических (не пре-регистрированных) измерений под сид — Mixin в серверное
  управление дим/`ServerLevel`/`DimensionDataStorage`; (2) замены поведения существующего генератора/
  `CustomTeleporter`/`RocketContraptionEntity` на уровне кода (иначе — datapack/реюз); (3) полностью кастомного
  `BiomeSource`/`ChunkGenerator`, если нельзя оформить кодеком. В mixin-классах CS (`mixin/entity/gravity/*`,
  `mixin/recipe/*`) addon не нужен.

---

## Таблица «Требование → API»

| Требование | API Creating Space | NeoForge API | Mixin | Сложность |
|---|---|---|---|---|
| Новая планета (destination) | datapack реестр `rocket_accessible_dimension` | DataPackRegistryEvent / RegistrySetBuilder | нет | низкая |
| Новое измерение | LevelStem/DimensionType как у CS | DatapackBuiltinEntriesProvider | нет | низкая |
| Планеты от seed | (нет; рельеф по сиду авто) | NoiseBasedChunkGenerator+NoiseSettings | нет | средняя |
| процедур. star systems | `Star` — заглушка (нет) | — | нет | средняя (новая фича) |
| Динамические дим (не пре-регистр.) | (нет) | (нет публичного API) | **да** (сервер/димы) | высокая |
| Менять terrain существующих планет | датапак-оверрайд LevelStem/NoiseSettings | RegistrySetBuilder/датапаки | нет (оверрайд) / да (код) | средняя |
| Собственный ChunkGenerator | (нет у CS) | свой класс+Codec в LevelStem | нет | средняя |
| Собственный BiomeSource | (нет у CS; MultiNoise/Fixed) | свой BiomeSource в ChunkGenerator; или MultiNoise Params | опц. | средняя |
| Ракета передаёт addon-destination | `RocketContraptionEntity.destination` + реестр | `ServerLevel.getLevel(key)` | нет | низкая |
| Реюз ракеты/взлёта/телепорта | реюз как есть | `DimensionTransition`, `CommonHooks` | нет | низкая |
**H. Можно ли использовать собственный BiomeSource?** Да, как часть вашего ChunkGenerator (кодек BiomeSource
встраивается в кодек генератора; LevelStem сериализует генератор). CS использует только `MultiNoiseBiomeSource`/
`FixedBiomeSource`. Безопасный путь: генерить `MultiNoiseBiomeSource` из собственного `ParameterList` (реестр
`MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST`). Полный кастом без собственного ChunkGenerator — Mixin.

**I. Как ракета получает destination?** Поле `RocketContraptionEntity.destination` (ResourceLocation). Путь: сборка →
`RocketScheduleRuntime.tick` → `startCurrentInstruction()` видит `DestinationInstruction.getDestination()` (ресурслокация
из реестра планет, `CSDimensionUtil.getPlanets()`) → `CSDimensionUtil.cost(from,to)` → `RocketPath` →
`rocket.startNavigation(path)`. В полёте при `y>300`: `tickDimensionChangeLogic()` берёт
`server.getLevel(key(DIMENSION, destination))` и телепортит `changeDimension(CustomTeleporter.getTransition(...))`.

**J. Можно ли передать ракете destination, созданный addon?** Да, при двух условиях: (1) ваш ResourceLocation есть в
реестре `rocket_accessible_dimension`; (2) существует реальный LevelStem/диmension с этим id (иначе `destServerLevel ==
null` → ERROR). Тогда запуск, `CustomTeleporter` (arrivalHeight/координаты `initialPosMap`), стоимость, выбор в GUI
работают без переделки. `DestinationInstruction`/GUI сам подхватит ваши планеты из `getPlanets()`.

**K. Что заменить / что оставить.**
- **Оставить без изменений:** ракету, взлёт, орбитальную/межпланетную логику (`RocketContraptionEntity`,
  `CustomTeleporter`, `RocketScheduleRuntime`/`RocketPath`, deltaV-граф), кислород/гравитацию (data-driven: gravity из
  RocketAccessibleDimension, кислород из биом-тега `NO_OXYGEN`), клиентские dimension-effects, двигатели/топливо
  (pack/propellant реестры).
- **Заменить/построить:** «тело» галактики — набор планет (entries `rocket_accessible_dimension`), их worldgen
  (LevelStem/DimensionType/NoiseGeneratorSettings/Biome/Carver), отсутствующую систему звёзд/орбит (`Star`). Поскольку
  всё это датапак/реестр, addon поставляет свои записи (добавочно) или переопределяет записи CS датапаком, не трогая
  код CS. Полностью свой ChunkGenerator/BiomeSource — для процедурного рельефа.
