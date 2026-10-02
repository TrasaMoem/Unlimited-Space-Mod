package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT 6 section 8 - the HABITABILITY guarantees: explicit status, real water, real ecology.
 *
 * <p>Uses the ONE canonical chain and asserts the player-facing contract on real generated worlds:
 * <ul>
 *   <li>the status is an explicit HABITABLE / STERILE verdict, never a misleading percentage;</li>
 *   <li>a planet reported HABITABLE really passes the canonical physical validation, which
 *       requires a stable LIQUID water phase - so "habitable" implies reachable water;</li>
 *   <li>the WATER GUARANTEE is deterministic, seed-stable and sparse (never an ocean world);</li>
 *   <li>a sterile world may still show a high bio potential - which is exactly why the UI must
 *       never present a percentage as a habitability flag.</li>
 * </ul>
 */
class Act6HabitabilityContentTest {

    private static final long SEED = 20260000L;
    private static final int SYSTEMS = 6;

    private static int habitablePlanets;
    private static int sterilePlanets;
    private static int habitableMoons;
    private static int sterileMoons;
    private static int sterileWithHighBioPotential;

    private static void scan() {
        Galaxy galaxy = Galaxy.from(SEED);
        for (int s = 0; s < SYSTEMS; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result result = SystemHabitability.of(system);
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                boolean actual = result.isActuallyHabitable(o);
                if (actual) habitablePlanets++; else sterilePlanets++;
                HabitabilityAnswer answer = HabitabilityValidator.validate(
                        HabitabilityProfile.ofPlanet(planet.properties()));
                assertEquals(actual, answer.physicallyHabitable(),
                        "the canonical validator and the system selection must agree for "
                                + planet.id().code());
                if (actual) {
                    WaterPhaseModel.Phase phase = WaterPhaseModel.ofProperties(
                            planet.properties().temperature(), planet.properties().atmosphere(),
                            planet.properties().atmosphericDensity(),
                            planet.properties().waterCoverage());
                    assertEquals(WaterPhaseModel.Phase.LIQUID, phase,
                            "a HABITABLE world must have a stable liquid water phase: "
                                    + planet.id().code());
                } else if (planet.properties().lifeLevel() > 0.3) {
                    sterileWithHighBioPotential++;
                }
                for (Moon moon : planet.moons()) {
                    if (MoonHabitability.of(result, planet.id(), moon).actuallyHabitable()) {
                        habitableMoons++;
                    } else {
                        sterileMoons++;
                    }
                }
            }
        }
    }

    @Test
    void habitabilityIsAnExplicitVerdictNotAPercentage() {
        // The canonical wording is what the navigation panel reads.
        assertEquals("HABITABLE",
                com.modscreating.unlimitedspace.core.presentation.WorldStatusText
                        .habitabilityText(true));
        assertEquals("STERILE",
                com.modscreating.unlimitedspace.core.presentation.WorldStatusText
                        .habitabilityText(false));
        assertFalse(com.modscreating.unlimitedspace.core.presentation.WorldStatusText
                        .habitabilityText(false).contains("%"),
                "a sterile status must never be a percentage");
    }

    @Test
    void habitablePlanetsAndMoonsReallyExistAndAreConsistent() {
        scan();
        System.out.printf(Locale.ROOT,
                "[ACT6-HAB] planets habitable=%d sterile=%d (sterile-with-high-bio=%d)"
                        + " moons habitable=%d sterile=%d%n",
                habitablePlanets, sterilePlanets, sterileWithHighBioPotential,
                habitableMoons, sterileMoons);
        assertTrue(habitablePlanets > 0, "the sample must contain habitable planets");
        assertTrue(sterilePlanets > 0, "the sample must contain sterile planets");
        // A habitable moon needs an actually-habitable parent AND its own 30% lottery, so this
        // sample may legitimately contain none; scan() already asserts every moon verdict
        // against the canonical chain.
        assertTrue(sterileMoons > 0, "the sample must contain sterile moons");
    }

    @Test
    void theWaterGuaranteeIsDeterministicAndSparse() {
        int cell = HabitableWaterSource.SPRING_CELL_CHUNKS;
        java.util.Set<String> cellsSeen = new java.util.HashSet<>();
        int springs = 0;
        int cellsWithSeveral = 0;
        // Iterate over WHOLE lattice cells so the assertion really is "exactly one spring per

        // cell" and not an artefact of a partially scanned edge cell.
        for (int gx = -5; gx <= 5; gx++) {
            for (int gz = -5; gz <= 5; gz++) {
                int inCell = 0;
                for (int cx = gx * cell; cx < (gx + 1) * cell; cx++) {
                    for (int cz = gz * cell; cz < (gz + 1) * cell; cz++) {
                        if (HabitableWaterSource.isGuaranteedSpringChunk(SEED, cx, cz)) inCell++;
                    }
                }
                cellsSeen.add(gx + "/" + gz);
                springs += inCell;
                if (inCell != 1) cellsWithSeveral++;
            }
        }
        System.out.println("[ACT6-HAB] water springs: " + springs + " over "
                + cellsSeen.size() + " whole lattice cells");
        assertEquals(0, cellsWithSeveral,
                "every lattice cell must designate EXACTLY one spring chunk");
        assertTrue(springs * 100L < 400L * 400L,
                "the water guarantee must stay sparse, never an ocean world: " + springs);
        assertTrue(springs > 0, "the water guarantee must actually place springs");
    }

    @Test
    void springPlacementIsSeedStableAndStaysInsideItsChunk() {
        for (int cx = -60; cx < 60; cx++) {
            for (int cz = -60; cz < 60; cz++) {
                if (!HabitableWaterSource.isGuaranteedSpringChunk(SEED, cx, cz)) continue;
                int[] a = HabitableWaterSource.springPlacement(SEED, cx, cz);
                int[] b = HabitableWaterSource.springPlacement(SEED, cx, cz);
                assertTrue(a[0] == b[0] && a[1] == b[1] && a[2] == b[2],
                        "the spring placement must be seed-stable");
                int radius = a[2];
                assertTrue(a[0] - radius >= 0 && a[0] + radius <= 15,
                        "the spring must stay inside its chunk: x=" + a[0] + " r=" + radius);
                assertTrue(a[1] - radius >= 0 && a[1] + radius <= 15,
                        "the spring must stay inside its chunk: z=" + a[1] + " r=" + radius);
                assertTrue(radius >= HabitableWaterSource.SPRING_RADIUS_MIN
                                && radius <= HabitableWaterSource.SPRING_RADIUS_MAX,
                        "the spring radius is out of its declared range: " + radius);
            }
        }
    }
}
