package com.modscreating.unlimitedspace.worldgen.ecology;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.MobEcologyProfile;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.seed.CelestialSeedCache;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ACT 2 — the natural-mob-spawn gate for procedural planet/moon surfaces.
 *
 * <pre>
 * Habitability ──► MobEcologyProfile ──► THIS (runtime spawn gate)
 * mobSpawnAllowed = actualHabitable ∧ mobsEnabled(20% world lottery) ∧ vanilla local rules
 * </pre>
 *
 * <p>Why an event gate instead of static biome JSON: the registered planet biomes are a STATIC
 * shared pool (one biome identity per planet theme), so a per-planet 20% decision can never live
 * in the datapack. The JSONs carry the placeholder skeleton spawner so vanilla natural spawning
 * attempts it; THIS gate decides per WORLD — deterministic, computed once per level (cached).
 *
 * <p>Vanilla compatibility: the gate only ever DENIES; it never forces spawns. Darkness, time,
 * collision, placement rules, difficulty and all vanilla conditions remain untouched. Levels that
 * are not unlimitedspace planet/moon surfaces (vanilla, space, asteroid, star, orbit) are passed
 * through completely (returned {@code null} → no decision), so unrelated dimensions keep their
 * existing behaviour.
 *
 * <p>The decision is fixed per planet/moon — never per chunk, per biome or per spawn attempt.
 */
public final class EcologySpawnGate {

    /** Matches the deterministic surface bindings: planet/&lt;code&gt;/surface, moon/&lt;code&gt;/surface. */
    private static final Pattern BODY_PATTERN =
            Pattern.compile("^(planet|moon)/(system_(\\d+)_planet_(\\d+)(?:_moon_(\\d+))?)/surface$");

    /**
     * Memoized per-world decisions. The key is {@code worldSeed + "|" + dimensionPath} — the mob
     * decision is a deterministic function of (world seed, body slot). A PATH-ONLY key leaked one
     * world's answer into any other world loaded in the same JVM session (integrated server world
     * switches) and permanently cached a degraded "no mobs" answer computed before the world seed
     * was known — both directions of wrong (ACT 2.3 runtime fix: the placeholder skeleton must be
     * spawnable exactly when {@code actualHabitable ∧ mobsEnabled} holds).
     */
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private EcologySpawnGate() {}

    /**
     * The natural-spawn decision points. Vanilla fires BOTH events in the natural spawn cycle;
     * gating both fully blocks natural spawning on non-enabled worlds. The gate only ever
     * FAILs a check — it never forces a spawn.
     */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        Boolean allowed = mobsAllowedFor(event.getLevel().getLevel());
        if (allowed != null && !allowed) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL) return;
        Boolean allowed = mobsAllowedFor(event.getLevel().getLevel());
        if (allowed != null && !allowed) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * {@code null} = this level is not a gated unlimitedspace planet/moon surface (pass-through);
     * {@code true}/{@code false} = the deterministic per-world mob decision.
     */
    static Boolean mobsAllowedFor(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        if (!UnlimitedSpace.MODID.equals(rl.getNamespace())) return null;
        if (!BODY_PATTERN.matcher(rl.getPath()).matches()) return null;
        if (!CelestialSeedCache.isSet()) {
            // Degraded context (no world seed yet): no mobs — but NEVER cached, so the real
            // decision is still produced once the seed is known.
            return false;
        }
        return mobsAllowedForPath(rl.getPath(), CelestialSeedCache.get());
    }

    /**
     * ACT 2.3: the canonical per-world decision for one gated dimension path — the exact call the
     * spawn events make (memoized on {@code (worldSeed, path)}). Package-visible so the focused
     * placeholder-gate test can assert the decision without starting a Minecraft server.
     */
    static boolean mobsAllowedForPath(String dimensionPath, long worldSeed) {
        Matcher m = BODY_PATTERN.matcher(dimensionPath);
        if (!m.matches()) return false;
        return CACHE.computeIfAbsent(worldSeed + "|" + dimensionPath,
                key -> computeAllowed(m, worldSeed));
    }

    /** Deterministic resolution from the canonical domain chain (never from level state). */
    private static boolean computeAllowed(Matcher m, long worldSeed) {
        try {
            int systemIndex = Integer.parseInt(m.group(3));
            int orbit = Integer.parseInt(m.group(4));
            boolean isMoon = "moon".equals(m.group(1));
            Galaxy galaxy = Galaxy.from(worldSeed);
            StarSystem system = galaxy.getStarSystem(StarSystemId.of(systemIndex));
            Planet planet = system.getPlanet(orbit);
            SystemHabitability.Result systemResult = SystemHabitability.of(system);
            if (!isMoon) {
                boolean actual = systemResult.isActuallyHabitable(orbit);
                return MobEcologyProfile.of(planet.seed().value(), actual).mobsEnabled();
            }
            if (m.group(5) == null) return false;
            Moon moon = planet.moon(Integer.parseInt(m.group(5)));
            MoonHabitability.MoonResult moonResult =
                    MoonHabitability.of(systemResult, planet.id(), moon);
            return moonResult.life().mobsEnabled();
        } catch (Throwable t) {
            // Degraded context (malformed id): no mobs, never a crash.
            return false;
        }
    }
}
