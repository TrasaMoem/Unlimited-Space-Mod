package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 — the headless DIAGNOSTIC REPORT of the system-habitability universe: representative
 * planets and moons (pattern / candidate / physical / actual / life / mobs + physics inputs),
 * written to {@code build/act2-report/habitability.txt}. Deterministic, no Minecraft runtime.
 */
@Tag("audit")
class Act2HabitabilityReportTest {

    private static final long WORLD_SEED = 20260000L;
    private static final int SYSTEMS_TO_SCAN = 4000;

    @Test
    void representativeHabitabilityReport() throws Exception {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        int[] patterns = new int[4];
        int actualPlanets = 0;
        int mobEnabledPlanets = 0;
        int moonsChecked = 0;
        int actualMoons = 0;
        int mobEnabledMoons = 0;
        int candidateFailed = 0;
        int physicallySuitableNotSelected = 0;
        int habitableParentMoons = 0;
        int moonLotterySelected = 0;
        int moonPhysicalPass = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("# ACT 2 HABITABILITY & LIFE — representative results (worldSeed=")
                .append(WORLD_SEED).append(")\n");
        sb.append("# Every line is READ from the canonical chain: SystemHabitability -> "
                + "HabitabilityValidator -> LifeState -> MobEcologyProfile.\n\n");

        for (int s = 0; s < SYSTEMS_TO_SCAN; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            patterns[r.pattern().ordinal()]++;
            for (int o = 0; o < system.planetCount(); o++) {
                Planet planet = system.getPlanet(o);
                PlanetProperties props = planet.properties();
                HabitabilityAnswer a = HabitabilityValidator.validate(
                        HabitabilityProfile.ofPlanet(props));
                boolean actual = r.isActuallyHabitable(o);
                MobEcologyProfile mob = MobEcologyProfile.of(props.seed().value(), actual);
                if (actual) actualPlanets++;
                if (actual && mob.mobsEnabled()) mobEnabledPlanets++;
                if (r.isCandidate(o) && !a.physicallyHabitable()) candidateFailed++;
                if (!r.isCandidate(o) && a.physicallyHabitable()) physicallySuitableNotSelected++;

                boolean interesting = actual
                        || (r.isCandidate(o) && !a.physicallyHabitable() && candidateFailed <= 5)
                        || (!r.isCandidate(o) && a.physicallyHabitable()
                                && physicallySuitableNotSelected <= 3);
                if (interesting) {
                    appendPlanet(sb, planet, r, a, actual, mob);
                }
                for (Moon moon : planet.moons()) {
                    moonsChecked++;
                    MoonHabitability.MoonResult mr = MoonHabitability.of(r, planet.id(), moon);
                    if (mr.actuallyHabitable()) {
                        actualMoons++;
                        if (mr.life().mobsEnabled()) mobEnabledMoons++;
                        appendMoon(sb, moon, mr);
                    } else if (mr.parentHabitable()) {
                        habitableParentMoons++;
                        if (mr.lotterySelected()) moonLotterySelected++;
                        if (mr.physical().physicallyHabitable()) moonPhysicalPass++;
                        if (habitableParentMoons <= 12) {
                            appendHabitableParentMoon(sb, moon, mr);
                        }
                    }
                }
            }
        }
        writeSummary(sb, patterns, actualPlanets, mobEnabledPlanets, moonsChecked,
                actualMoons, mobEnabledMoons, candidateFailed, physicallySuitableNotSelected,
                habitableParentMoons, moonLotterySelected, moonPhysicalPass);

        assertTrue(actualPlanets > 0,
                "expected at least one ACTUALLY habitable planet over " + SYSTEMS_TO_SCAN
                        + " systems (pattern ∧ physics)");
        assertTrue(physicallySuitableNotSelected > 0,
                "expected physically suitable but NOT selected worlds (the ACT 2 rule proof)");
    }

    private static void writeSummary(StringBuilder sb, int[] patterns, int actualPlanets,
                                     int mobEnabledPlanets, int moonsChecked, int actualMoons,
                                     int mobEnabledMoons, int candidateFailed,
                                     int physicallySuitableNotSelected,
                                     int habitableParentMoons, int moonLotterySelected,
                                     int moonPhysicalPass) throws Exception {
        sb.append("\n# SUMMARY\n");
        sb.append("systems scanned      : ").append(SYSTEMS_TO_SCAN).append('\n');
        sb.append("patterns P1/P2/P3/P4 : ").append(patterns[0]).append(" / ")
                .append(patterns[1]).append(" / ").append(patterns[2]).append(" / ")
                .append(patterns[3]).append('\n');
        sb.append("actual habitable planets : ").append(actualPlanets).append('\n');
        sb.append("mob-enabled planets      : ").append(mobEnabledPlanets).append('\n');
        sb.append("candidates failing physics: ").append(candidateFailed).append('\n');
        sb.append("physically suitable but NOT selected: ")
                .append(physicallySuitableNotSelected).append('\n');
        sb.append("moons checked / actual / mob-enabled: ").append(moonsChecked).append(" / ")
                .append(actualMoons).append(" / ").append(mobEnabledMoons).append('\n');
        sb.append("moons of ACTUAL parents  : ").append(habitableParentMoons)
                .append(" (lottery-selected ").append(moonLotterySelected)
                .append(", physically valid ").append(moonPhysicalPass).append(")\n");

        Path dir = Path.of("build", "act2-report");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("habitability.txt"), sb.toString(),
                StandardCharsets.UTF_8);
    }

    private static void appendPlanet(StringBuilder sb, Planet planet,
                                     SystemHabitability.Result r, HabitabilityAnswer a,
                                     boolean actual, MobEcologyProfile mob) {
        PlanetProperties p = planet.properties();
        PlanetPhysicalProfile physical =
                PlanetPhysicalProfileFactory.create(p.seed().value(), p);
        WaterPhaseModel.Phase phase = WaterPhaseModel.ofProperties(p.temperature(), p.atmosphere(),
                p.atmosphericDensity(),
                PlanetPhysicalProfileFactory.waterAbundance(p.waterCoverage(), p.humidity()));
        sb.append("PLANET ").append(planet.id().code())
                .append(" | pattern=").append(r.pattern())
                .append(" candidate=").append(r.isCandidate(planet.id().orbitIndex()))
                .append(" physical=").append(a.physicallyHabitable()).append(" [").append(a.describe())
                .append("] actual=").append(actual)
                .append(" life=veg:").append(actual).append(",mobs:").append(mob.mobsEnabled())
                .append(" structures:").append(actual)
                .append("\n    T=").append(StellarThermalModel.temperatureText(p.temperature()))
                .append(" waterPhase=").append(phase.displayName())
                .append(" pressure=").append(physical.pressureClass())
                .append(String.format(java.util.Locale.ROOT, " gravity=%.2fg", p.gravity()))
                .append(String.format(java.util.Locale.ROOT, " radiation=%.2f", physical.radiation()))
                .append(" atmosphere=").append(p.atmosphere())
                .append(" water=").append(String.format(java.util.Locale.ROOT, "%.2f", p.waterCoverage()))
                .append(" surface=").append(p.surface())
                .append(" type=").append(p.type()).append('\n');
    }

    private static void appendMoon(StringBuilder sb, Moon moon,
                                   MoonHabitability.MoonResult mr) {
        sb.append("MOON ").append(moon.id().code())
                .append(" | parentActual=").append(mr.parentHabitable())
                .append(" lottery=").append(mr.lotterySelected())
                .append(" physical=").append(mr.physical().physicallyHabitable())
                .append(" [").append(mr.physical().describe()).append("]")
                .append(" actual=").append(mr.actuallyHabitable())
                .append(" mobs=").append(mr.life().mobsEnabled())
                .append("\n    T=").append(StellarThermalModel.temperatureText(
                        moon.properties().temperature()))
                .append(" waterPhase=").append(WaterPhaseModel.ofProperties(
                        moon.properties().temperature(), moon.properties().atmosphere(),
                        moon.properties().atmosphericDensity(), moon.properties().waterCoverage())
                        .displayName())
                .append(" gravity=").append(String.format(java.util.Locale.ROOT, "%.2fg",
                        moon.properties().gravity()))
                .append(" surface=").append(moon.properties().surface()).append('\n');
    }

    /** A moon of an ACTUAL parent that did not become habitable — with the exact reason mix. */
    private static void appendHabitableParentMoon(StringBuilder sb, Moon moon,
                                                  MoonHabitability.MoonResult mr) {
        sb.append("MOON-OF-HABITABLE-PARENT ").append(moon.id().code())
                .append(" | lottery=").append(mr.lotterySelected())
                .append(" physical=").append(mr.physical().physicallyHabitable())
                .append(" [").append(mr.physical().describe()).append("]")
                .append(" actual=").append(mr.actuallyHabitable())
                .append("\n    T=").append(StellarThermalModel.temperatureText(
                        moon.properties().temperature()))
                .append(" gravity=").append(String.format(java.util.Locale.ROOT, "%.2fg",
                        moon.properties().gravity()))
                .append(" atmosphere=").append(moon.properties().atmosphere())
                .append(" water=").append(String.format(java.util.Locale.ROOT, "%.2f",
                        moon.properties().waterCoverage()))
                .append(" type=").append(moon.properties().type()).append('\n');
    }

    @Test
    void reportFormatIsStableAcrossRuns() {
        List<String> first = scanSummary(Galaxy.from(WORLD_SEED));
        List<String> second = scanSummary(Galaxy.from(WORLD_SEED));
        assertEquals(first, second, "the habitability report must be deterministic");
    }

    private static List<String> scanSummary(Galaxy galaxy) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int s = 0; s < 200; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            SystemHabitability.Result r = SystemHabitability.of(system);
            out.add(s + ":" + r.pattern() + ":" + r.candidateOrbitIndexes() + ":"
                    + r.actuallyHabitableOrbitIndexes());
        }
        return out;
    }
}
