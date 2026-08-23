package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.MoonId;
import com.modscreating.unlimitedspace.core.planets.MoonProperties;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;
import com.modscreating.unlimitedspace.core.seed.Seeds;
import com.modscreating.unlimitedspace.core.stars.Star;
import com.modscreating.unlimitedspace.core.stars.StarColor;
import com.modscreating.unlimitedspace.core.stars.StarStage;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.worldgen.MoonGenerationProfile;
import com.modscreating.unlimitedspace.core.worldgen.MoonSkyProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetSurfaceColor;
import com.modscreating.unlimitedspace.core.worldgen.PlanetVisualProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side resolver that turns the current {@link ClientLevel} into deterministic
 * {@link ResolvedVisual} data for the R12 celestial visualisations.
 *
 * <p>Data is derived ONLY from authoritative domain objects:
 * {@code WorldSeed -> Galaxy -> StarSystem -> Planet/Moon -> PlanetWorldgenProfile}.
 * The world seed comes from the shared {@link CelestialSeedCache} (populated in
 * singleplayer / integrated server by {@code ServerStartedEvent}), else from the
 * integrated server's overworld, else deterministic {@code 0} (degraded, safe).
 *
 * <p>Results are cached per dimension path and world seed; only bodies actually
 * viewed are kept ({@code CACHE_CAPACITY} entries).
 */
public final class CelestialVisualResolver {

    private static final int CACHE_CAPACITY = 24;

    private static final Map<String, ResolvedVisual> CACHE =
            new LinkedHashMap<>(CACHE_CAPACITY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ResolvedVisual> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            };

    private CelestialVisualResolver() {
    }

    /** Resolve the visual data for the level the player is currently in (cached). */
    public static ResolvedVisual resolve(ClientLevel level) {
        if (level == null) return null;
        String key = level.dimension().location().getPath();
        ResolvedVisual cached = CACHE.get(key);
        return cached != null ? cached : computeAndCache(key, level);
    }

    /** Drop all cached visuals + orbiting sprites (world switch / dimension unloading). */
    public static void clearCache() {
        CACHE.clear();
        com.modscreating.unlimitedspace.client.graphics.CelestialTextureCache.clear();
    }

    private static ResolvedVisual computeAndCache(String path, ClientLevel level) {
        ResolvedVisual vis = compute(path, worldSeedFor(level));
        if (vis != null) {
            CACHE.put(path, vis);
        }
        return vis;
    }

    /** Pure derivation exposed for tests: path + world seed в†’ visual data. */
    static ResolvedVisual compute(String path, long worldSeed) {
        CelestialBodyPath.Result parsed = CelestialBodyPath.parse(path);
        if (parsed == null) return null;
        try {
            return switch (parsed.kind()) {
                case PLANET -> resolvePlanet(worldSeed, parsed);
                case MOON -> resolveMoon(worldSeed, parsed);
                case ASTEROID -> resolveAsteroidOrSpace(worldSeed, parsed);
                case STAR -> resolveStar(worldSeed, parsed);
                case VOID -> resolveAsteroidOrSpace(worldSeed, parsed);
            };
        } catch (RuntimeException e) {
            // Never let a visual resolver crash the client; degrade safely to null.
            return null;
        }
    }

    /**
     * Actual Minecraft world seed on the client: in singleplayer the shared
     * {@link CelestialSeedCache} (set by {@code ServerStartedEvent}) wins; otherwise
     * fall back to the integrated server's overworld seed; last resort is
     * deterministic {@code 0L} (safe, non-crashing, degraded).
     */
    public static long worldSeedFor(ClientLevel level) {
        if (CelestialSeedCache.isSet()) {
            return CelestialSeedCache.get();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            ServerLevel overworld = mc.getSingleplayerServer().overworld();
            if (overworld != null) {
                return overworld.getSeed();
            }
        }
        return 0L;
    }

    private static ResolvedVisual resolvePlanet(long worldSeed, CelestialBodyPath.Result parsed) {
        PlanetId planetId = parsed.planetId();
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystem system = galaxy.getStarSystem(planetId.system());
        Planet planet = system.getPlanet(planetId.orbitIndex());
        PlanetProperties props = planet.properties();
        PlanetWorldgenProfile profile = PlanetWorldgenProfile.from(planet);
        PlanetVisualProfile visual = profile.visual();

        int surfaceColor = PlanetSurfaceColor.surfaceColorArgb(props);
        float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.3);
        float iceBlend = props.temperature() < 260.0
                ? (float) Math.min(1.0, (260.0 - props.temperature()) / 110.0)
                : 0.0f;

        return new ResolvedVisual(worldSeed, CelestialBodyPath.Kind.PLANET,
                planetId, null, parsed.surface(),
                visual.skyColor(), visual.waterColor(), visual.fogColor(),
                visual.sunTint(), visual.cloudColor(),
                surfaceColor, waterBlend, iceBlend,
                (float) props.radiusProfile(),
                toStarVisuals(system.seed(), system.stars()), -1, 0,
                siblingBodies(system, planetId, null, null));
    }

    private static ResolvedVisual resolveMoon(long worldSeed, CelestialBodyPath.Result result) {
        MoonId moonId = result.moonId();
        if (moonId == null) return null;
        PlanetId parentId = moonId.parentPlanetId();
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystem system = galaxy.getStarSystem(parentId.system());
        Planet parent = system.getPlanet(parentId.orbitIndex());
        Moon moon = parent.moon(moonId.moonIndex());
        MoonProperties props = moon.properties();
        MoonSkyProfile visual = MoonSkyProfile.create(props.seed().value(), props);

        int surfaceColor = MoonGenerationProfile.surfaceColorArgb(props.type());
        float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.6);
        float iceBlend = props.temperature() < 250.0
                ? (float) Math.min(1.0, (250.0 - props.temperature()) / 110.0)
                : 0.0f;

        int parentDiscArgb = 0;
        if (!result.surface()) {
            PlanetProperties parentProps = parent.properties();
            parentDiscArgb = PlanetSurfaceColor.surfaceColorArgb(parentProps);
        }
        return new ResolvedVisual(worldSeed, CelestialBodyPath.Kind.MOON,
                parentId, moonId, result.surface(),
                visual.skyColor(), visual.waterColor(), visual.fogColor(),
                visual.sunTint(), visual.cloudColor(),
                surfaceColor, waterBlend, iceBlend,
                (float) props.radiusProfile(),
                toStarVisuals(system.seed(), system.stars()), -1, parentDiscArgb,
                siblingBodies(system, null, moonId, parentId));
    }

    /**
     * R14.9.2: a star SURFACE (whole-dome luminous plasma — no blue space, no background stars/bodies)
     * or a star ORBIT (reuse the same black-space orbital sky as planet/moon orbit, with the local star
     * as the dominant body and every other system body as a distant sibling), derived from the SPECIFIC
     * star of the system at {@code result.starIndex()} (a companion is its own star, never the primary).
     * A black hole surface is intentionally a dark void stand-in, not a bright photosphere, so a black
     * hole never renders a normal sun.
     */
    private static ResolvedVisual resolveStar(long worldSeed, CelestialBodyPath.Result result) {
        StarSystemId systemId = result.systemId() != null
                ? result.systemId() : StarSystemId.of(0);
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystem system = galaxy.getStarSystem(systemId);
        Star star = system.star(result.starIndex());
        boolean surface = result.surface();

        StarStage stage = StarStage.from(star);
        boolean blackHole = stage == StarStage.BLACK_HOLE || star.type() == StarType.BLACK_HOLE;

        int plasma = StarColor.temperatureRgb(star.temperature());
        int surfaceColor = blackHole ? 0xFF05050A : (0xFF000000 | plasma);

        // On a star surface the whole sky is the star's own opaque luminous plasma. A black hole is
        // deliberately dark (void) — never a bright photosphere. The fog matches so the atmosphere
        // reads as plasma all the way out.
        int sky = blackHole ? 0xFF030307 : (0xFF000000 | plasma);
        // R14.9.3-A: the fog must be the SATURATED plasma colour, not the outer halo (plasma × 0.62). The
        // halved halo reads as flat gray-tan for a G/white/blue star and produced a gray horizon / washed-out
        // lower sky. Using the star's own temperature colour keeps the atmosphere plasma-coloured (never gray)
        // and makes the fog match the sky dome so there is no hard horizon band.
        int fog = blackHole ? 0xFF04040A : (0xFF000000 | plasma);

        // Surface hides background planets (the plasma field fills the entire view); orbit shows them.
        List<SiblingBody> bodies = surface
                ? List.of()
                : siblingBodies(system, null, null, null);

        return new ResolvedVisual(worldSeed, CelestialBodyPath.Kind.STAR, null, null, surface,
                sky, 0x00000000, fog, 0x00000000, 0x00000000,
                surfaceColor, 0.0f, 0.0f, (float) star.size(),
                toStarVisuals(system.seed(), system.stars()), result.starIndex(), 0, bodies);
    }

    /** Asteroid fields and the legacy space dimension: show the host system's star(s). */
    private static ResolvedVisual resolveAsteroidOrSpace(long worldSeed, CelestialBodyPath.Result result) {
        StarSystemId systemId = result.systemId() != null
                ? result.systemId() : StarSystemId.of(0);
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystem system = galaxy.getStarSystem(systemId);
        return new ResolvedVisual(worldSeed, result.kind(), result.planetId(),
                result.moonId(), false,
                0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                0x00000000, 0.0f, 0.0f, 0.0f,
                toStarVisuals(system.seed(), system.stars()), -1, 0,
                siblingBodies(system, null, null, null));
    }

    private static List<StarVisual> toStarVisuals(long systemSeed, List<Star> stars) {
        List<StarVisual> out = new ArrayList<>(stars.size());
        for (int i = 0; i < stars.size(); i++) {
            out.add(StarVisual.create(systemSeed, stars.get(i), i));
        }
        return out;
    }

    // ------------------------------------------------------------ sibling bodies (R12.3)

    /**
     * Every other celestial body of the system visible in the sky from the current orbit.
     * The body currently being orbited is excluded (it is drawn large below the camera), but
     * its own moons are still shown as distant bodies. Moon orbits additionally show the parent
     * planet (so its disc appears without a dedicated pass). Apparent size shrinks with the
     * body's orbit distance and, for moons, the parent's scale.
     */
    private static List<SiblingBody> siblingBodies(StarSystem system, PlanetId excludePlanet,
                                                   MoonId excludeMoon, PlanetId featuredParent) {
        List<SiblingBody> out = new ArrayList<>();
        int[] idx = new int[]{0};   // running body index -> deterministic spread across the whole dome
        for (int oi = 0; oi < system.planetCount(); oi++) {
            Planet p = system.getPlanet(oi);
            if (excludePlanet != null && excludePlanet.equals(p.id())) {
                // This is the planet currently being orbited: keep its moons as distant bodies.
                addMoons(out, p, excludeMoon, p.seed().value(), idx);
                continue;
            }
            if (featuredParent != null && featuredParent.equals(p.id())) {
                // Moon-orbit parent planet: clearly visible at ~1/3 of the current body's scale,
                // positioned on the same whole-dome layout (distinct salt so it stays apart).
                addFeaturedParent(out, system.seed(), p, idx);
            } else {
                addPlanetSibling(out, system.seed(), p, oi, idx);
            }
            addMoons(out, p, excludeMoon, p.seed().value(), idx);
        }
        return out;
    }

    /** The parent planet of a moon orbit — ~1/3 of the CS current-body size, distinct placement. */
    private static void addFeaturedParent(List<SiblingBody> out, long systemSeed, Planet p, int[] idx) {
        PlanetProperties props = p.properties();
        int surfaceColor = PlanetSurfaceColor.surfaceColorArgb(props);
        float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.3);
        float iceBlend = props.temperature() < 260.0
                ? (float) Math.min(1.0, (260.0 - props.temperature()) / 110.0)
                : 0.0f;
        float apparent = CelestialVisualScale.parentBodyHalf();
        int k = idx[0]++;
        out.add(new SiblingBody(p.id().code(), surfaceColor,
                props.type() == com.modscreating.unlimitedspace.core.planets.PlanetType.GAS_GIANT
                        ? surfaceColor : 0,
                props.type() == com.modscreating.unlimitedspace.core.planets.PlanetType.GAS_GIANT ? 0.0f : waterBlend,
                iceBlend, (float) props.radiusProfile(),
                skyAzimuth(systemSeed, p.id().code(), 2, k), skyElevation(systemSeed, p.id().code(), 2, k),
                apparent));
    }

    private static void addPlanetSibling(List<SiblingBody> out, long systemSeed, Planet p, int orbitIndex, int[] idx) {
        PlanetProperties props = p.properties();
        int surfaceColor = PlanetSurfaceColor.surfaceColorArgb(props);
        float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.3);
        float iceBlend = props.temperature() < 260.0
                ? (float) Math.min(1.0, (260.0 - props.temperature()) / 110.0)
                : 0.0f;
        // Clearly visible pixel body: apparent half-size shrinks with orbit distance from the player,
        // but never exceeds the featured parent (so it stays below the current body in dominance).
        float apparent = CelestialVisualScale.siblingPlanetHalf((float) props.radiusProfile(), orbitIndex);
        int k = idx[0]++;
        out.add(new SiblingBody(p.id().code(), surfaceColor,
                props.type() == com.modscreating.unlimitedspace.core.planets.PlanetType.GAS_GIANT
                        ? surfaceColor : 0,
                props.type() == com.modscreating.unlimitedspace.core.planets.PlanetType.GAS_GIANT ? 0.0f : waterBlend,
                iceBlend, (float) props.radiusProfile(),
                skyAzimuth(systemSeed, p.id().code(), 0, k), skyElevation(systemSeed, p.id().code(), 0, k),
                apparent));
    }

    private static void addMoons(List<SiblingBody> out, Planet parent, MoonId excludeMoon, long parentSeed, int[] idx) {
        for (Moon moon : parent.moons()) {
            if (excludeMoon != null && excludeMoon.equals(moon.id())) continue;
            MoonProperties props = moon.properties();
            int surfaceColor = MoonGenerationProfile.surfaceColorArgb(props.type());
            float waterBlend = (float) Math.min(1.0, props.waterCoverage() * 1.6);
            float iceBlend = props.temperature() < 250.0
                    ? (float) Math.min(1.0, (250.0 - props.temperature()) / 110.0)
                    : 0.0f;
            // Moons are the smallest body layer (R12.6 role scale).
            float apparent = CelestialVisualScale.siblingMoonHalf((float) props.radiusProfile());
            int k = idx[0]++;
            out.add(new SiblingBody(moon.id().code(), surfaceColor, 0, waterBlend, iceBlend,
                    (float) props.radiusProfile(),
                    skyAzimuth(parentSeed, moon.id().code(), 3, k),
                    skyElevation(parentSeed, moon.id().code(), 3, k),
                    apparent));
        }
    }

    /**
     * Deterministic sky azimuth for a sibling body, spread evenly across the whole dome (golden-angle
     * separation by body index + per-body seed jitter) so bodies never pile into one narrow sky band.
     */
    private static float skyAzimuth(long systemSeed, String code, int salt, int index) {
        long s = Seeds.derive(systemSeed, "us.client.sibling." + salt, code.hashCode());
        float jitter = (float) (Seeds.fraction(s, 1L) - 0.5) * 18.0f;
        float az = index * 137.507764f + jitter;   // golden angle keeps consecutive bodies far apart
        az = az % 360.0f;
        return az < 0.0f ? az + 360.0f : az;
    }

    /** Deterministic sky elevation for a sibling body — spread over the upper hemisphere, off-poles. */
    private static float skyElevation(long systemSeed, String code, int salt, int index) {
        long s = Seeds.derive(systemSeed, "us.client.sibling." + salt, code.hashCode());
        float f = (float) Seeds.fraction(s, 2L);
        // keep clear of the pole (no stacked ring) and of the horizon band below the camera
        float mid = 0.5f + 0.5f * (float) Math.sin(index * 2.399963f);   // ~phi, decorrelates rows
        float el = 6.0f + 58.0f * clamp01((f * 0.7f + mid * 0.3f));
        return el;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
