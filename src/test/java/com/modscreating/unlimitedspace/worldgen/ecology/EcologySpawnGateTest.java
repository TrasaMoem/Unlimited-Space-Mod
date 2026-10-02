package com.modscreating.unlimitedspace.worldgen.ecology;

import com.modscreating.unlimitedspace.core.destination.ProceduralDimension;
import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.habitability.MobEcologyProfile;
import com.modscreating.unlimitedspace.core.habitability.MoonHabitability;
import com.modscreating.unlimitedspace.core.habitability.SystemHabitability;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.3 — the focused test of the EXISTING skeleton placeholder gate.
 *
 * <p>{@code EcologySpawnGate} is the runtime half of the existing mobile-ecology architecture: the
 * planet/moon biome JSONs carry the placeholder {@code minecraft:skeleton} spawner, and this gate
 * either lets vanilla natural spawning decide (DEFAULT) or FAILs the spawn. The gate must allow
 * exactly {@code actualHabitable ∧ mobsEnabled} — no rule is changed here, only the existing
 * decision chain is pinned:
 *
 * <pre>
 * SystemHabitability → LifeState → MobEcologyProfile → this gate → vanilla natural spawner
 * </pre>
 *
 * <p>The decision is memoized on {@code (worldSeed, dimensionPath)} — the ACT 2.3 runtime fix: the
 * same body path in two different worlds must resolve INDEPENDENTLY (the old path-only cache
 * leaked one world's answer into another world and could permanently deny the placeholder).
 */
@Tag("worldgen")
class EcologySpawnGateTest {

    /** The ACT 2 report world seed (deterministic reference galaxy). */
    private static final long WORLD_SEED = 20260000L;

    @Test
    void skeletonPlaceholderIsAllowedExactlyWhenActuallyHabitableAndMobsEnabled() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        boolean foundAllowed = false;
        boolean foundDeniedByLottery = false;
        boolean foundDeniedByHabitability = false;
        for (int s = 0; s < 900
                && !(foundAllowed && foundDeniedByLottery && foundDeniedByHabitability); s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                boolean actual = result.isActuallyHabitable(o);
                MobEcologyProfile profile = MobEcologyProfile.of(planet.seed().value(), actual);
                String path = new ProceduralDimension(
                        ProceduralDimension.Kind.PLANET_SURFACE, s, o, -1, -1, 0).resourcePath();

                assertEquals(actual && profile.mobsEnabled(),
                        EcologySpawnGate.mobsAllowedForPath(path, WORLD_SEED),
                        "gate must follow actualHabitable AND mobsEnabled for " + path);
                if (actual && profile.mobsEnabled()) {
                    foundAllowed = true;
                    assertEquals(MobEcologyProfile.PLACEHOLDER_SPECIES, profile.placeholderSpecies(),
                            "the existing placeholder species is the vanilla skeleton");
                } else if (actual) {
                    foundDeniedByLottery = true;
                } else if (!actual) {
                    foundDeniedByHabitability = true;
                }

                for (Moon moon : planet.moons()) {
                    MoonHabitability.MoonResult moonResult =
                            MoonHabitability.of(result, planet.id(), moon);
                    String moonPath = new ProceduralDimension(
                            ProceduralDimension.Kind.MOON_SURFACE, s, o, moon.moonIndex(), -1, 0)
                            .resourcePath();
                    assertEquals(moonResult.life().mobsEnabled(),
                            EcologySpawnGate.mobsAllowedForPath(moonPath, WORLD_SEED),
                            "moon gate must follow the canonical moon life state for " + moonPath);
                }
            }
        }
        assertTrue(foundAllowed, "expected an actually habitable world with mobs enabled");
        assertTrue(foundDeniedByLottery,
                "expected an actually habitable world denied by the 20% mob lottery");
        assertTrue(foundDeniedByHabitability,
                "expected worlds denied because they are not actually habitable");
    }

    @Test
    void moonGateFollowsTheCanonicalMoonLifeState() {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        boolean found = false;
        for (int s = 0; s < 8000 && !found; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount() && !found; o++) {
                Planet planet = system.getPlanet(o);
                for (Moon moon : planet.moons()) {
                    MoonHabitability.MoonResult moonResult =
                            MoonHabitability.of(result, planet.id(), moon);
                    if (!moonResult.life().mobsEnabled()) continue;
                    String moonPath = new ProceduralDimension(
                            ProceduralDimension.Kind.MOON_SURFACE, s, o, moon.moonIndex(), -1, 0)
                            .resourcePath();
                    assertTrue(moonResult.actuallyHabitable(),
                            "mobsEnabled implies the moon is actually habitable");
                    assertTrue(EcologySpawnGate.mobsAllowedForPath(moonPath, WORLD_SEED),
                            "an actually habitable moon with mobs enabled must open the gate: "
                                    + moonPath);
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "expected an actually habitable moon with mobs enabled in the sample");
    }

    @Test
    void theDecisionIsKeyedByWorldSeedForTheSameBodyPath() {
        // The mob decision is a function of (worldSeed, path); find two world seeds whose answers
        // for the SAME body path differ, then resolve both through the gate.
        String path = new ProceduralDimension(
                ProceduralDimension.Kind.PLANET_SURFACE, 0, 0, -1, -1, 0).resourcePath();
        long seedA = -1L;
        long seedB = -1L;
        boolean expectedA = false;
        boolean expectedB = false;
        outer:
        for (long a = 1; a <= 40; a++) {
            for (long b = a + 1; b <= 60; b++) {
                boolean ea = expectedForPath(path, a);
                boolean eb = expectedForPath(path, b);
                if (ea != eb) {
                    seedA = a;
                    seedB = b;
                    expectedA = ea;
                    expectedB = eb;
                    break outer;
                }
            }
        }
        assertTrue(seedA > 0,
                "expected two world seeds with different decisions for the same body path");

        assertEquals(expectedA, EcologySpawnGate.mobsAllowedForPath(path, seedA),
                "world A decision");
        // SECOND world resolved for the SAME path — must NOT be shadowed by world A's answer.
        assertEquals(expectedB, EcologySpawnGate.mobsAllowedForPath(path, seedB),
                "world B must resolve independently (seed-keyed memoization)");
        // ... and world A's memoized answer must still be world A's answer.
        assertEquals(expectedA, EcologySpawnGate.mobsAllowedForPath(path, seedA),
                "world A decision must stay stable after world B resolved");
    }

    @Test
    void onlySurfaceBindingsAreGated() {
        // Orbits, space, asteroid and star paths are pass-through (no gate decision here) and
        // malformed planet paths are denied — but never for a surface binding.
        assertFalse(EcologySpawnGate.mobsAllowedForPath(
                new ProceduralDimension(ProceduralDimension.Kind.PLANET_ORBIT, 0, 0, -1, -1, 0)
                        .resourcePath(), WORLD_SEED));
        assertFalse(EcologySpawnGate.mobsAllowedForPath("space/system_0000", WORLD_SEED));
        assertFalse(EcologySpawnGate.mobsAllowedForPath("planet/system_0000_planet_00",
                WORLD_SEED));
    }

    /** Fresh resolution through the canonical chain — deliberately NOT through the gate cache. */
    private static boolean expectedForPath(String path, long worldSeed) {
        ProceduralDimension dimension = ProceduralDimension.parse(path).orElseThrow();
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystem system = galaxy.getStarSystem(galaxy.systemId(dimension.systemIndex()));
        SystemHabitability.Result result = SystemHabitability.of(system);
        Planet planet = system.getPlanet(dimension.planetIndex());
        boolean actual = result.isActuallyHabitable(dimension.planetIndex());
        return MobEcologyProfile.of(planet.seed().value(), actual).mobsEnabled();
    }
}
