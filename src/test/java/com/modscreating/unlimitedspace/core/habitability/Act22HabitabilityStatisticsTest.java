package com.modscreating.unlimitedspace.core.habitability;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Moon;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.core.worldgen.fluids.WaterPhaseModel;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import com.modscreating.unlimitedspace.core.worldgen.profile.PressureClass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2.2 §15–§17 — the LARGE DETERMINISTIC statistical report over 40,000 systems:
 * pattern counts, actual / physical / capture metrics, orbit displacement, accepted-world
 * quality (temperature / water / pressure / gravity / radiation / star / AU / phase) and
 * the moon pipeline (parent → lottery → physical → actual).
 *
 * <p>Written to {@code build/act22-report/statistics.txt}. The PRIMARY acceptance objective
 * is verified on the FULL 40,000-system sample (central tendency), never from a single small
 * 4,000-system window: actual habitable planets ≥ 400 per 4,000 systems (≥ 4,000 total).
 */
@Tag("audit")
class Act22HabitabilityStatisticsTest {

    private static final long WORLD_SEED = 20260923L;
    private static final int SYSTEMS = 40_000;
    private static final int REFERENCE_WINDOW = 4_000;

    @Test
    void act22LargeSampleStatisticsReport() throws Exception {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);

        int[] drawn = new int[4];
        int[] effective = new int[4];
        int[] actualByPattern = new int[4];
        long actual = 0;
        long physicalValid = 0;
        long validNotSelected = 0;
        long orbitSum = 0;
        long selectedCount = 0;
        long shiftSum = 0;
        long maxShift = 0;
        int twoWorldSystems = 0;
        int first4000Actual = 0;
        int nonP4Systems = 0;
        int nonP4WithActual = 0;

        // Accepted-world quality accumulators (§16).
        long accepted = 0;
        double tempMin = Double.MAX_VALUE;
        double tempMax = -Double.MAX_VALUE;
        double tempSum = 0.0;
        double waterSum = 0.0;
        double gravityMin = Double.MAX_VALUE;
        double gravityMax = -Double.MAX_VALUE;
        double radiationMax = 0.0;
        double auMin = Double.MAX_VALUE;
        double auMax = -Double.MAX_VALUE;
        int[] pressureCounts = new int[PressureClass.VALUES.length];
        int[] starCounts = new int[StarType.values().length];
        long liquidPhases = 0;
        long gasGiants = 0;
        long moltenSurfaces = 0;
        long outOfWindowTemp = 0;
        long outOfWindowGravity = 0;
        long overRadiation = 0;
        long underWaterFloor = 0;

        // Moon pipeline (§17).
        int habitableParents = 0;
        int moonPopulation = 0;
        int moonLotterySelected = 0;
        int moonPhysicallyValid = 0;
        int moonActual = 0;
        int moonLotteryAndValid = 0;

        for (int s = 0; s < SYSTEMS; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            int n = system.planetCount();
            SystemHabitability.Result r = SystemHabitability.of(system);
            int pOrdinal = r.pattern().ordinal();
            drawn[SystemHabitability.drawPattern(system.seed()).ordinal()]++;
            effective[pOrdinal]++;
            if (r.pattern() != SystemHabitability.Pattern.FOUR) nonP4Systems++;

            // Original (pre-resolution) anchors of the effective pattern — for shift metrics.
            List<Integer> positions = switch (r.pattern()) {
                case ONE -> SystemHabitability.positionsOneBased(n, 2.0);
                case TWO -> SystemHabitability.positionsOneBased(n, 4.0);
                case THREE -> List.of(1);
                case FOUR -> List.of();
            };

            for (int o = 0; o < n; o++) {
                Planet planet = system.getPlanet(o);
                PlanetProperties props = planet.properties();
                boolean phys = HabitabilityValidator.isPhysicallyHabitable(
                        HabitabilityProfile.ofPlanet(props));
                if (phys) physicalValid++;
                if (r.isActuallyHabitable(o)) {
                    actual++;
                    actualByPattern[pOrdinal]++;
                    orbitSum += o;
                    selectedCount++;

                    // Orbit displacement of this selection from the nearest original anchor.
                    int shift = Integer.MAX_VALUE;
                    for (int pos : positions) {
                        shift = Math.min(shift, Math.abs(o - (pos - 1)));
                    }
                    if (shift != Integer.MAX_VALUE) {
                        shiftSum += shift;
                        maxShift = Math.max(maxShift, shift);
                    }

                    // Accepted-world quality profile (§16).
                    accepted++;
                    double t = props.temperature();
                    tempMin = Math.min(tempMin, t);
                    tempMax = Math.max(tempMax, t);
                    tempSum += t;
                    if (t < HabitabilityValidator.TEMP_MIN_K || t > HabitabilityValidator.TEMP_MAX_K) {
                        outOfWindowTemp++;
                    }
                    double water = PlanetPhysicalProfileFactory
                            .waterAbundance(props.waterCoverage(), props.humidity());
                    waterSum += water;
                    if (water < HabitabilityValidator.MIN_WATER_AVAILABILITY) underWaterFloor++;
                    double g = props.gravity();
                    gravityMin = Math.min(gravityMin, g);
                    gravityMax = Math.max(gravityMax, g);
                    if (g < HabitabilityValidator.GRAVITY_MIN_EARTH_G
                            || g > HabitabilityValidator.GRAVITY_MAX_EARTH_G) outOfWindowGravity++;
                    PlanetPhysicalProfile profile =
                            PlanetPhysicalProfileFactory.create(props.seed().value(), props);
                    radiationMax = Math.max(radiationMax, profile.radiation());
                    if (profile.radiation() > HabitabilityValidator.MAX_RADIATION) overRadiation++;
                    pressureCounts[profile.pressureClass().ordinal()]++;
                    starCounts[system.star().type().ordinal()]++;
                    double au = props.thermal().orbitAU();
                    auMin = Math.min(auMin, au);
                    auMax = Math.max(auMax, au);
                    WaterPhaseModel.Phase phase = WaterPhaseModel.ofProperties(
                            t, props.atmosphere(), props.atmosphericDensity(), water);
                    if (phase == WaterPhaseModel.Phase.LIQUID) liquidPhases++;
                    if (props.type() == PlanetType.GAS_GIANT
                            || props.surface() == PlanetSurface.GASEOUS) gasGiants++;
                    if (props.surface() == PlanetSurface.SOLID_VOLCANIC) moltenSurfaces++;

                    // Moon pipeline (§17): only ACTUAL parents even roll the moon lottery.
                    habitableParents++;
                    for (Moon moon : planet.moons()) {
                        moonPopulation++;
                        MoonHabitability.MoonResult mr = MoonHabitability.of(r, planet.id(), moon);
                        if (mr.lotterySelected()) moonLotterySelected++;
                        if (mr.physical().physicallyHabitable()) moonPhysicallyValid++;
                        if (mr.lotterySelected() && mr.physical().physicallyHabitable()) {
                            moonLotteryAndValid++;
                        }
                        if (mr.actuallyHabitable()) moonActual++;
                    }
                } else if (phys) {
                    validNotSelected++;
                }
            }

            int actualHere = r.actuallyHabitableOrbitIndexes().size();
            if (actualHere == 2) twoWorldSystems++;
            if (actualHere > 0 && r.pattern() != SystemHabitability.Pattern.FOUR) {
                nonP4WithActual++;
            }
            if (s < REFERENCE_WINDOW) first4000Actual += actualHere;
        }
        // ---------------------------------------------------------------- assertions (§15/§16)
        long scaledPer4000 = actual * REFERENCE_WINDOW / SYSTEMS;
        // PRIMARY acceptance: ≥ 400 actual habitable planets per 4,000 systems, verified on
        // the FULL 40,000-system central tendency (never from a single small window).
        assertTrue(scaledPer4000 >= 400,
                "ACT 2.2 PRIMARY: actual habitable must reach ≥400/4000 on the 40k sample, got "
                        + scaledPer4000 + "/4000 (raw " + actual + "/" + SYSTEMS + ")");
        // Preferred stable range 400–500/4000 — reported, asserted with generous headroom.
        assertTrue(scaledPer4000 <= 700,
                "actual habitable should stay in the preferred band, got "
                        + scaledPer4000 + "/4000 — investigate P4/effective shares above");
        // Effective P4 == explicit P4 draw: the fallback NEVER demotes into P4.
        assertEquals(drawn[3], effective[3],
                "effective P4 must equal the explicit 10% P4 draw (no artificial demotion)");
        assertEquals(accepted, liquidPhases,
                "100% of accepted worlds must hold LIQUID water");
        assertEquals(0, outOfWindowTemp, "accepted worlds stay inside the REC temperature window");
        assertEquals(0, outOfWindowGravity, "accepted worlds stay inside the REC gravity window");
        assertEquals(0, overRadiation, "accepted worlds stay under the REC radiation ceiling");
        assertEquals(0, underWaterFloor, "accepted worlds stay above the REC water floor");
        assertEquals(0, gasGiants, "gas giants are never accepted");
        assertEquals(0, moltenSurfaces, "molten/volcanic surfaces are never accepted");
        assertTrue(accepted > 0, "the sample must contain accepted worlds");
        // "Broadly Earth-like": mean temperature must stay centered in the temperate band.
        double tempMean = tempSum / accepted;
        assertTrue(tempMean >= 275.0 && tempMean <= 335.0,
                "accepted-world mean temperature inside the REC window, got " + tempMean);
        assertTrue(Math.abs(tempMean - 295.0) <= 30.0,
                "accepted-world mean temperature roughly Earth-like (~295 K ±30), got " + tempMean);
        // Moon lottery stays ~30% of moons of habitable parents (unchanged ACT 2.1 rule).
        if (moonPopulation >= 200) {
            double lotteryShare = moonLotterySelected / (double) moonPopulation;
            assertTrue(Math.abs(lotteryShare - 0.30) < 0.10,
                    "moon lottery must stay ~30%, got " + lotteryShare);
            assertEquals(moonLotteryAndValid, moonActual,
                    "actual moon = lottery ∧ physical (30/70 never forces a world)");
        }
        // Displacement is bounded by the resolver's ±2 window by construction.
        assertTrue(maxShift <= 2, "anchor shift can never exceed the ±2 window, got " + maxShift);
        assertTrue(twoWorldSystems > 0, "expected some two-world systems in 40k");
        assertTrue(first4000Actual > 0, "the reference 4k window must contain actual worlds");
        // ---------------------------------------------------------------- report (§15)
        StringBuilder sb = new StringBuilder();
        sb.append("# ACT 2.2 HABITABILITY STATISTICS (worldSeed=").append(WORLD_SEED)
                .append(", systems=").append(SYSTEMS).append(")\n");
        sb.append("# deterministic report — regenerated identically on every run\n\n");
        sb.append("## 1. PATTERN COUNTS\n");
        sb.append("drawn     P1/P2/P3/P4 : ").append(drawn[0]).append(" / ").append(drawn[1])
                .append(" / ").append(drawn[2]).append(" / ").append(drawn[3])
                .append(String.format(Locale.ROOT, "  (%.2f%% / %.2f%% / %.2f%% / %.2f%%)%n",
                        100.0 * drawn[0] / SYSTEMS, 100.0 * drawn[1] / SYSTEMS,
                        100.0 * drawn[2] / SYSTEMS, 100.0 * drawn[3] / SYSTEMS));
        sb.append("effective P1/P2/P3/P4 : ").append(effective[0]).append(" / ").append(effective[1])
                .append(" / ").append(effective[2]).append(" / ").append(effective[3])
                .append(String.format(Locale.ROOT, "  (%.2f%% / %.2f%% / %.2f%% / %.2f%%)%n",
                        100.0 * effective[0] / SYSTEMS, 100.0 * effective[1] / SYSTEMS,
                        100.0 * effective[2] / SYSTEMS, 100.0 * effective[3] / SYSTEMS));
        sb.append("effective P4 (= explicit draw, no demotion) : ")
                .append(String.format(Locale.ROOT, "%.2f%%%n", 100.0 * effective[3] / SYSTEMS));

        sb.append("\n## 2. HABITABLE POPULATION\n");
        sb.append("actual habitable planets (40k)        : ").append(actual).append('\n');
        sb.append("actual habitable / 4000 (scaled)      : ").append(scaledPer4000).append('\n');
        sb.append("actual habitable / 4000 (first 4000)  : ").append(first4000Actual).append('\n');
        sb.append("actual by pattern P1/P2/P3/P4         : ").append(actualByPattern[0]).append(" / ")
                .append(actualByPattern[1]).append(" / ").append(actualByPattern[2]).append(" / ")
                .append(actualByPattern[3]).append('\n');
        sb.append("actual / 4000 by pattern (scaled)     : ")
                .append(actualByPattern[0] * REFERENCE_WINDOW / SYSTEMS).append(" / ")
                .append(actualByPattern[1] * REFERENCE_WINDOW / SYSTEMS).append(" / ")
                .append(actualByPattern[2] * REFERENCE_WINDOW / SYSTEMS).append(" / ")
                .append(actualByPattern[3] * REFERENCE_WINDOW / SYSTEMS).append('\n');
        sb.append("physical-valid planets                : ").append(physicalValid).append('\n');
        sb.append("physically valid but NOT selected     : ").append(validNotSelected).append('\n');
        sb.append("candidate capture % (actual/physical) : ")
                .append(String.format(Locale.ROOT, "%.2f%%%n",
                        physicalValid == 0 ? 0.0 : 100.0 * actual / physicalValid));
        sb.append("non-P4 systems                        : ").append(nonP4Systems)
                .append(" (with ≥1 actual: ").append(nonP4WithActual).append(")\n");
        sb.append("two-world frequency                   : ").append(twoWorldSystems)
                .append(String.format(Locale.ROOT, " systems (%.2f%%)%n",
                        100.0 * twoWorldSystems / SYSTEMS));

        sb.append("\n## 3. ORBIT DISPLACEMENT\n");
        sb.append("average selected orbitIndex           : ")
                .append(String.format(Locale.ROOT, "%.3f%n",
                        selectedCount == 0 ? 0.0 : orbitSum / (double) selectedCount));
        sb.append("average anchor shift                  : ")
                .append(String.format(Locale.ROOT, "%.3f%n",
                        selectedCount == 0 ? 0.0 : shiftSum / (double) selectedCount));
        sb.append("max anchor shift                      : ").append(maxShift).append('\n');
        sb.append("\n## 4. ACCEPTED-WORLD QUALITY (§16)\n");
        sb.append("temperature K min/mean/max            : ")
                .append(String.format(Locale.ROOT, "%.2f / %.2f / %.2f%n", tempMin, tempMean, tempMax));
        sb.append("water availability mean               : ")
                .append(String.format(Locale.ROOT, "%.3f (floor %.2f)%n",
                        waterSum / accepted, HabitabilityValidator.MIN_WATER_AVAILABILITY));
        sb.append("pressure classes V/T/TH/M/D/C         : ");
        for (int i = 0; i < pressureCounts.length; i++) {
            sb.append(pressureCounts[i]);
            sb.append(i + 1 < pressureCounts.length ? " / " : "\n");
        }
        sb.append("gravity g min/max                     : ")
                .append(String.format(Locale.ROOT, "%.3f / %.3f%n", gravityMin, gravityMax));
        sb.append("radiation max                         : ")
                .append(String.format(Locale.ROOT, "%.3f (ceiling %.2f)%n",
                        radiationMax, HabitabilityValidator.MAX_RADIATION));
        sb.append("AU min/max                            : ")
                .append(String.format(Locale.ROOT, "%.3f / %.3f%n", auMin, auMax));
        sb.append("star types (accepted)                 : ");
        for (int i = 0; i < starCounts.length; i++) {
            if (starCounts[i] > 0) {
                sb.append(StarType.values()[i]).append('=').append(starCounts[i]).append(' ');
            }
        }
        sb.append('\n');
        sb.append("water phase LIQUID share              : ")
                .append(liquidPhases).append('/').append(accepted)
                .append(String.format(Locale.ROOT, " (%.2f%%)%n",
                        100.0 * liquidPhases / accepted));
        sb.append("gas giants / molten accepted          : ")
                .append(gasGiants).append(" / ").append(moltenSurfaces).append('\n');

        sb.append("\n## 5. MOON PIPELINE (§17)\n");
        sb.append("actual habitable parent planets       : ").append(habitableParents).append('\n');
        sb.append("moon population (of those parents)     : ").append(moonPopulation).append('\n');
        sb.append("moon lottery success (~30%)            : ").append(moonLotterySelected).append('\n');
        sb.append("physically valid moon candidates       : ").append(moonPhysicallyValid).append('\n');
        sb.append("actual habitable moons                 : ").append(moonActual).append('\n');

        Path dir = Path.of("build", "act22-report");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("statistics.txt"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println(sb);
    }
}