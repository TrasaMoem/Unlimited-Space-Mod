# Phase R12 — Research Archive

Research / verification notes captured while building the **client-only visualization layer** for R12.
Reused solely for the seed→visual bridge; Creating Space (CS) remains the authoritative travel/orbit
mechanics provider. Findings below confirm the exact NeoForge 1.21.1 API surface that the new
`DimensionSpecialEffects` overrides and fog color hook must target.

Sources:
- CFR-decompiled CS internals: `cs_customdim_effects.txt`, `cs_generic_orbit.txt`, `cs_earth_orbit.txt`.
- Neo 1.21.1 source jar: `net/neoforged/neoforge/client/extensions/IDimensionSpecialEffectsExtension.java`,
  plus `RegisterDimensionSpecialEffectsEvent`, `ViewportEvent`, `DimensionSpecialEffects`.

---

## 1. `DimensionSpecialEffects` base constructor (Neo 1.21.1)

Confirmed via the `invokespecial` target in `CustomDimensionEffects.<init>` (CFR, `cs_customdim_effects.txt` line 25):

```
DimensionSpecialEffects(float, boolean, SkyType, boolean, boolean)
  =>  (float, boolean generateStarfield, SkyType skyType, boolean hasGround, boolean cloudless)
```

i.e. bytecode descriptor `(FZLnet/minecraft/client/renderer/DimensionSpecialEffects$SkyType;ZZ)V`.

> This is the **5-argument** form in Neo 1.21.1. Earlier truncated source-jar lookups reported a
> 4-arg `(float, SkyType, boolean, boolean)` signature, which caused the "signature drift"
> failures during the last build attempt. The 5-arg form above is canonical for 1.21.1 and is
> what the new US effects compile against.

### Mapping of US effects into the constructor

| US effect                        | args fed to `super(...)`                            | Notes |
|----------------------------------|------------------------------------------------------|-------|
| `UnlimitedSpaceOrbitEffects`     | `(fadeTime, true, SkyType.NONE, false, false)`       | orbit/asteroid/space dims (no sky dome; starfield from `SystemStarRenderer`) |
| `UnlimitedSpaceSurfaceEffects`   | `(fadeTime, false, SkyType.NORMAL, true, true)`     | planet/moon surface: ground present; vanilla sky gradient overridden via `SurfaceSkyRenderer` + fog hook |

Both compile GREEN — confirming ctor arity/types.

---

## 2. Overrideable rendering methods (`IDimensionSpecialEffectsExtension` / `DimensionSpecialEffects`)

Confirmed signatures (Neo source jar + CS bytecode). CS overrides all three and returns `true`
to cancel vanilla, then renders its own starfield/body:

```java
boolean renderClouds(ClientLevel level, int ticks, float partialTick,
                     PoseStack poseStack, double camX, double camY, double camZ,
                     Matrix4f modelViewMatrix, Matrix4f projectionMatrix);

boolean renderSky(ClientLevel level, int ticks, float partialTick,
                  Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix,
                  boolean isFoggy, Runnable setupFog);

boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                          LightTexture lightTexture, double camX, double camY, double camZ);
```

Our `UnlimitedSpaceOrbitEffects` / `UnlimitedSpaceSurfaceEffects` override the same trio with
matching signatures (compile passes).

`getBrightnessDependentFogColor(Vec3, float)` and `isFoggyAt(int,int)` are also overridable;
CS makes them no-ops (return input / `false`). Our surface effects override neither — procedural
fog is injected via the `ComputeFogColor` viewport hook.

---

## 3. `ViewportEvent.ComputeFogColor` — setter API (confirmed for 1.21.1)

Neo's `ViewportEvent.ComputeFogColor` exposes mutable color channels via **setters**
`setRed(float)`, `setGreen(float)`, `setBlue(float)` — **not** `setFogColor`. Our
`UnlimitedSpaceClientEvents` uses exactly:

```java
event.setRed(r); event.setGreen(g); event.setBlue(b);
```

`compileJava` + `compileTestJava` GREEN confirms this triplet is valid in the resolved 1.21.1
Neo jar — the earlier "setFogColor vs setRed/Green/Blue" concern is resolved: it is the
`setRed/Green/Blue` form.

---

## 4. CS `CustomDimensionEffects` internals (decompiled)

Key facts used to design the US bridge:

- Base `CustomDimensionEffects` extends `DimensionSpecialEffects`.
- Static texture fields: `SPACE_SKY_LOCATION`, `EARTH_LOCATION`, `MOON_LOCATION`,
  `MARS_LOCATION`, `SATURN_LOCATION`, `SUN_LOCATION`, `MOON_PHASES_LOCATION` — all
  `creatingspace:textures/environment/*` (namespace `creatingspace`; vanilla `minecraft` for
  sun/moon_phases).
- `renderSpaceSky`: 6-iteration starfield quad loop using `SPACE_SKY_LOCATION` +
  `Tesselator` + `RenderSystem` blend/depthMask → CS draws its own starfield texture sheet.
- `renderAdditionalBody(...)` is the **hook point** subclasses override to draw their body
  (Earth/Moon/Mars/etc.) onto the starfield backdrop.
- `GenericCelestialOrbitEffect.<init>` calls `super(Float.NaNf, false, SkyType.NONE, false, false)`
  and sets `renderSun = true`. → **orbit dims use `SkyType.NONE`** (matches US orbit effects):
  the sun is rendered manually, not by the vanilla sky.

This validates the US design: orbit dims get `SkyType.NONE` + real star renderer
(`SystemStarRenderer` + `StarfieldRenderer`); surface dims get `SkyType.NORMAL` with sky color from
`SurfaceSkyRenderer`/`MoonSkyProfile` and fog from the viewport hook.

### EarthOrbit specifics (texture-based rendering to be replaced procedurally)
- Moon phase computed from `Level.getTimeOfDay` (0.0–1.0) → 4 phase UVs + 2-row phase index.
- Earth disc rendered at fixed azimuth/elevation via `renderAstralBody`.
- US replaces both: `SystemStarRenderer` (seeded primary + companions) and
  `PlanetSphereRenderer` (vertex-colored sphere + atmosphere halo), so no texture sheet needed.

---

## 5. Dimension type JSON wiring (final)

| dimension_type json            | SpecialEffect class            | SkyType / hasGround               |
|--------------------------------|--------------------------------|-----------------------------------|
| `space.json`                   | `UnlimitedSpaceOrbitEffects`   | NONE, no ground                   |
| `asteroid_field.json`          | `UnlimitedSpaceOrbitEffects`   | NONE, no ground                   |
| `procedural_planet_orbit.json` | `UnlimitedSpaceOrbitEffects`   | NONE, no ground                   |
| `procedural_planet_surface.json` | `UnlimitedSpaceSurfaceEffects` | NORMAL, hasGround=true            |

(The older `planet_orbit` / `planet_surface` / `space` shorthand JSONs were renamed to the
`procedural_planet_*` convention; surface effects apply on the surface json only.)

---

## 6. Risk items resolved during R12

| Concern                                            | Resolution |
|----------------------------------------------------|------------|
| `ViewportEvent.ComputeFogColor` setter name        | `setRed/Green/Blue` confirmed valid (compile GREEN) |
| `renderClouds` / `renderSnowAndRain` override arity | Matches `IDimensionSpecialEffectsExtension` defaults exactly |
| `DimensionSpecialEffects` ctor arity (4 vs 5 args) | 5-arg `(F,Z,SkyType,Z,Z)` is the correct 1.21.1 form; code compiles |
| Truncated source-jar lookups → spurious failures   | Re-confirmed against CFR bytecode + Neo source jar; overrides reconcile |

---

## 7. Open questions / carry-over for R13

1. **Star size/luminosity units** — US `StarVisual.apparentSize`/`luminosity` feeds
   `PlanetSphereRenderer` atmosphere halo + `SystemStarRenderer` disc radius; R13 (lighting)
   should reuse `StarVisual` directly rather than re-deriving from `Star.temperature`.
2. **Moon phase rendering** — CS used texture `moon_phases.png`; US could procedurally tint the
   moon's `MoonSkyProfile` terminator by phase if moon lighting is added in R13.
3. **Fog density** — `ComputeFogColor` overrides RGB only; R13 may want `SetupFogEvent` to set
   view-distance fog so space doesn't render a vanilla-distance haze.
4. **Black-hole accretion ring color** — `StarVisual` already distinguishes black holes
   (`isBlackHole`, `accretionArgb`); R13 lighting should skip emissive solar irradiance and use
   accretion-ring emission instead.

---

## 8. R12 status (final)

- `compileJava` — GREEN (1 deprecation note in `GalaxyLayout`, pre-existing, not R12).
- `compileTestJava` — GREEN.
- `./gradlew test` — **BUILD SUCCESSFUL**, 0 failures (220 tests total).
- R12 suite (31 new assertions) fully green:
  `CelestialBodyPathTest` (7), `CelestialVisualResolverTest` (8), `StarVisualTest` (6),
  `StarGeneratorMultiplicityTest` (5), `PlanetSurfaceColorTest` (5), `MoonSkyProfileTest` (5).
- Multi-star + black-hole rendering wired end-to-end generator → visual → renderer.
- Seed plumbing: world seed → `CelestialSeedCache` → `CelestialVisualResolver` (cached, exception-safe).
