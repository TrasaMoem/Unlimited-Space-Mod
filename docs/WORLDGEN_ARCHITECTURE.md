# Worldgen Architecture — Unlimited Space (Phase 3 POC)

Документ фиксирует решение по архитектуре измерений/миров и по слою
worldgen для **Phase 3 — первый процедурный тестовый планета (ROCKY)**.

> **Статус: Phase 3 — Proof-of-Concept.** Одно pre-registered измерение для
> одного тестового планета. Это **не** окончательная архитектура для всей
> галактики.

---

## 1. Ключевая развилка: как устроены «измерения»

### Установленный факт (NeoForge 1.21.1 + MC 1.21.1)

- `LevelStem`/`DimensionType` регистрируются **только на bootstrap/загрузке
  датапака** (`RegistrySetBuilder` / datapack-JSON).
- **Публичного API для динамического создания измерения после запуска сервера
  нет.** `DimensionManager`, существовавший в старых версиях Forge, в
  NeoForge 1.21.1 отсутствует.
- Единственные способы «добавить измерение в рантайме» — **Mixin/reflection**
  на внутренности сервера. Это хрупко, конфликтует с датапаками и с Creating
  Space, поэтому **запрещено** на этом этапе (см. STOP condition).

### Варианты архитектуры

| Вариант | Описание | Совместимость | Производительность | Персистентность | Сложность |
|---|---|---|---|---|---|
| **A** | Отдельное измерение на планету, **статически pre-registered** (датапак). | Да (как у Creating Space) | Обычная (все планеты «включены» постоянно) | Да (vanilla save) | Низкая |
| **B** | Динамические измерения через **Mixin/reflection** в рантайме. | Опасно (стабильность, датапаки, CS) | Зависит от реализации | Требует ручного сохранения | **Высокая — запрещено** |
| **C** | **Ограниченный пул** pre-registered измерений + переиспользование через кастомный `ChunkGenerator` (мир читает seed планеты из контекста). | Да | Хорошая (фиксированное число миров) | Да (vanilla save) | Средняя |
| **D** | Будущая архитектура, если появится более подходящий API. | — | — | — | — |

**Решение для Phase 3 — Вариант A (только POC):**
одно pre-registered измерение `unlimitedspace:test_planet`. Это baseline,
позволяющий доказать весь конвейер. **Не** является финалом.

**Направление после POC — Вариант C** (кандидат для масштабирования):
фиксированный пул миров + кастомный генератор, читающий детерминированный
`terrainSeed`. Вариант B не используется никогда без отдельного пересмотра.

---

## 2. Разделение «домен ↔ Minecraft»

`core/*` — чистый домен **без** каких-либо `net.minecraft.*`/`net.neoforged.*`
(проверяется architecture-тестом). Всё, что знает о Minecraft, лежит в слое
`worldgen/planet` (Minecraft adapter).

```
core/planets.PlanetProperties ──┐
core/planets.PlanetDefinition ──┤ (без Minecraft)
                                ▼
              core/worldgen.PlanetWorldgenProfile   (чистый маппинг свойств → профиль)
                                ▼
              core/worldgen.terrain.TerrainGenerator (чистая функция seed+x+z → высота)
                                │
        ┌───────────────────────┴──────────────────────────┐
        ▼                                                  ▼
worldgen/planet.PlanetBiomeSource             worldgen/planet.PlanetChunkGenerator  (Minecraft)
        └──────────────────────┬───────────────────────────┘
                               ▼
        worldgen/planet.PlanetDimensions  (LevelStem/DimensionType/датапак-JSON)
```

Правило зависимости (проверяется автоматически):
```
core/*            → НЕ зависит от Minecraft
worldgen/planet/* → МОЖЕТ зависеть от core
```

---

## 3. PlanetId ≠ Dimension-ResourceLocation (adapter / binding layer)

`PlanetId` (`system_0000_planet_00`) и Minecraft `ResourceLocation`
(`unlimitedspace:test_planet`) — **разные понятия**. Домен не знает про
Minecraft-измерения. Связь задаётся отдельным **binding-слоем**:

```
PlanetDefinition
      │
      ▼
PlanetDimensionBinding   (worldgen/planet — Minecraft-слой)
      │
      ▼
ResourceKey<LevelStem> / ResourceLocation   (напр. unlimitedspace:test_planet)
```

Благодаря этому смена архитектуры измерений (A → C → D) не требует правок в
`Galaxy`/`Planet`/`PlanetProperties`. В Phase 3 binding жёстко отображает
выбранный тест-планету на единственное измерение.

---

## 4. PlanetWorldgenProfile — абстрактные материалы (без BlockState)

`PlanetWorldgenProfile` (чистый домен) **не** хранит `BlockState`/`ResourceKey`
блоков/`Biome`. Вместо этого — абстрактные значения:

- `SurfaceMaterial` (STONE, ROCK, SAND, ICE, BASALT, METALLIC, …)
- `FluidProfile` (NONE, WATER, …)

Minecraft-маппинг «материал → BlockState» выполняется в Minecraft-слое
(`worldgen/planet`), не в домене.

---

## 5. TerrainGenerator — детерминированная функция с абстракцией под рост

POC реализует простую форму:

```
TerrainSeed + x + z → height
```

Но интерфейс `TerrainGenerator` спроектирован так, чтобы позже добавить без
переписывания консьюмера:
- несколько слоёв шума (octaves — уже поддержано в `ValueNoiseTerrainGenerator`);
- continentalness / erosion как отдельные факторы;
- peaks/valleys;
- caves / 3D density (расширение интерфейса отдельными методами).

---

## 6. Объём POC (что делаем / что не делаем)

**Делаем:** конвейер
`PlanetProperties → PlanetWorldgenProfile → TerrainGenerator → ChunkGenerator → Minecraft terrain`
для одного ROCKY-планета.

**Не делаем сейчас:** caves, ores, vegetation, structures, сложные surface
rules, продвинутый 3D-шум, полноценную климатическую biome-систему
(`PlanetBiomeSource` минимален — один базовый биом), любые изменения Creating
Space, rocket/travel интеграцию.

---

## 7. Границы / анти-паттерны

- `core/*` никогда не импортирует Minecraft (см. `CoreArchitectureTest`).
- В `Galaxy`/`SystemPlacer`/`Planet` нет жёстких «test-констант» вида
  `if (system==0 && orbit==0)`. Тест-планета выбирается **адаптером/конфигом**,
  а не логикой генерации.
- Никаких Mixin/reflection/workaround. При конфликте с CS или NeoForge —
  **остановиться и сообщить** (STOP condition).
