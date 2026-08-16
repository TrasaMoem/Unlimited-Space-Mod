package com.modscreating.unlimitedspace.core.r8;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8 diagnostic: materialises the real PlanetProperties + PlanetWorldgenProfile for the
 * three R7 surface slots (s0/o0, s0/o1, s0/o2) under the canonical seed 0x5EEDCAFE and
 * writes them to build/r8diag.txt so the water-world complaint can be pinned to actual
 * numbers (or cleared as a legitimate ocean roll). Pure domain: no Minecraft types.
 */
class PlanetR8DiagnosticTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;
    private static final int SYSTEM = 0;

    @Test
    void dumpThreePlanets() throws IOException {
        Galaxy g = Galaxy.from(WORLD_SEED);
        StarSystemId sys = StarSystemId.of(SYSTEM);
        Path out = Path.of("build/r8diag.txt");
        Files.createDirectories(Path.of("build"));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("R8 diagnostic  worldSeed=" + WORLD_SEED + " system=" + SYSTEM);
            long prevSeed = Long.MAX_VALUE;
            String prevProfile = null;
            for (int orbit = 0; orbit < 3; orbit++) {
                PlanetId pid = PlanetId.of(sys, orbit);
                Planet p = g.getStarSystem(sys).getPlanet(orbit);
                PlanetProperties props = p.properties();
                PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
                boolean distinctSeed = p.seed().value() != prevSeed;
                prevSeed = p.seed().value();

                w.println("---- planet #" + orbit + " id=" + pid.code() + " ----");
                w.println("  planetSeed=" + p.seed().value() + " type=" + props.type()
                        + " surface=" + props.surface() + " isGasGiant=" + props.isGasGiant());
                w.println("  temperature=" + fmt(props.temperature()) + "K humidity=" + fmt(props.humidity())
                        + " waterCoverage=" + fmt(props.waterCoverage()) + " gravity=" + fmt(props.gravity()) + "g");
                w.println("  atmosphere=" + props.atmosphere() + " atmosphericDensity=" + fmt(props.atmosphericDensity())
                        + " terrainRoughness=" + fmt(props.terrainRoughness()) + " erosion=" + fmt(props.erosion()));
                w.println("  vegetationDensity=" + fmt(props.vegetationDensity()) + " lifeLevel=" + fmt(props.lifeLevel())
                        + " geologicalActivity=" + fmt(props.geologicalActivity()));
                var gp = props.generationParameters();
                w.println("  genParams: seaLevelOffset=" + fmt(gp.seaLevelOffset())
                        + " baseHeight=" + fmt(gp.baseHeight()) + " terrainFrequency=" + fmt(gp.terrainFrequency()));
                var r = props.resources();
                w.println("  resources: mineral=" + fmt(r.mineralRichness())
                        + " rare=" + r.rareMaterials() + " fuel=" + fmt(r.fuelAbundance()));
                w.println("  seeds: terrain=" + props.terrainSeed() + " biome=" + props.biomeSeed()
                        + " material=" + props.materialSeed() + " ore=" + props.oreSeed()
                        + " vegetation=" + props.vegetationSeed() + " structure=" + props.structureSeed());
                w.println("  profile: terrainSeed=" + prof.terrainSeed()
                        + " baseHeight=" + fmt(prof.baseHeight()) + " amplitude=" + fmt(prof.amplitude())
                        + " frequency=" + fmt(prof.frequency()));
                w.println("  profile: seaLevel=" + fmt(prof.seaLevel()) + " hasWater=" + prof.hasWater()
                        + " surfaceMaterial=" + prof.surfaceMaterial()
                        + " subsurfaceMaterial=" + prof.subsurfaceMaterial() + " fluid=" + prof.fluid());
                boolean distinctProfile = !prof.toString().equals(prevProfile);
                prevProfile = prof.toString();
                w.println("  >> distinctSeedFromPrev=" + distinctSeed + " distinctProfileFromPrev=" + distinctProfile);
            }
        }

        // Regression invariants (Test F): slots must not collapse into frozen/identical worlds.
        Planet[] planets = new Planet[3];
        PlanetWorldgenProfile[] profs = new PlanetWorldgenProfile[3];
        for (int i = 0; i < 3; i++) {
            planets[i] = g.getStarSystem(sys).getPlanet(i);
            profs[i] = PlanetWorldgenProfile.from(planets[i]);
        }
        assertNotEquals(planets[0].seed(), planets[1].seed(), "planet 0 seed must differ from planet 1");
        assertNotEquals(planets[1].seed(), planets[2].seed(), "planet 1 seed must differ from planet 2");
        assertNotEquals(profs[0], profs[1], "profiles must differ between planet 0 and 1");
        assertNotEquals(profs[1], profs[2], "profiles must differ between planet 1 and 2");
        assertTrue(profs[0].amplitude() > 0.0, "planet 0 must have non-flat terrain: " + profs[0].amplitude());
    }

    private static String fmt(double d) {
        return String.format("%.4f", d);
    }
}
