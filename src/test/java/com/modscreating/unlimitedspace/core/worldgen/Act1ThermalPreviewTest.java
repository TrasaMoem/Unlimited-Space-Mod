package com.modscreating.unlimitedspace.core.worldgen;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetThermal;
import com.modscreating.unlimitedspace.core.physics.StellarThermalModel;
import com.modscreating.unlimitedspace.core.stars.StarSystem;
import com.modscreating.unlimitedspace.core.stars.StarType;
import com.modscreating.unlimitedspace.tools.MapPreview;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 1 — THERMAL PREVIEWS (headless, item N).
 *
 * <p>Renders REAL planets of the galaxy through {@link MapPreview} and writes the complete stellar
 * thermal chain next to the maps, so the recalibration can be judged visually AND numerically:
 * an inner orbit must read hotter than an outer orbit of the same system, a dim-star system colder
 * than a bright-star system, and every fact must be the canonical value the runtime uses.
 *
 * <p>Artifacts: {@code build/map-previews/ACT1_<STAR>_<INNER|OUTER>/} — PNG maps, {@code facts.txt}
 * (with the ACT 1 chain) and {@code metrics.txt}.
 */
@Tag("worldgen")
@Tag("audit")
class Act1ThermalPreviewTest {

    private static final long WORLD_SEED = 4242L;
    private static final File OUT_DIR = new File("build", "map-previews");

    private record Rendered(File dir, String facts, PlanetThermal thermal, double surfaceK) {}

    /** First system (deterministic order) of the galaxy whose primary is {@code type}. */
    private static StarSystem findSystem(Galaxy galaxy, StarType type) {
        for (int s = 0; s < 4000; s++) {
            StarSystem system = galaxy.getStarSystem(galaxy.systemId(s));
            if (system.star().type() == type && system.planetCount() >= 4) return system;
        }
        fail("no system with a " + type + " primary and >= 4 planets found");
        return null;
    }

    private static Rendered render(String tag, StarType type, boolean inner) throws Exception {
        Galaxy galaxy = Galaxy.from(WORLD_SEED);
        StarSystem system = findSystem(galaxy, type);
        int slot = inner ? 0 : system.planetCount() - 1;
        Planet planet = system.getPlanet(slot);
        File dir = MapPreview.render("ACT1_" + tag, system, planet, 256, 16, OUT_DIR);
        for (String file : new String[]{"temperature.png", "biome.png", "pressure.png",
                "metrics.txt", "facts.txt"}) {
            File f = new File(dir, file);
            assertTrue(f.isFile() && f.length() > 0, "preview missing: " + f.getPath());
        }
        String facts = Files.readString(new File(dir, "facts.txt").toPath(),
                StandardCharsets.UTF_8);
        for (String key : new String[]{"ACT 1 STELLAR THERMAL CHAIN:", "star type", "star luminosity",
                "stars                :", "orbital distance", "received flux", "equilibrium temp",
                "greenhouse multiplier", "internal contribution", "surface temperature",
                "thermal class"}) {
            assertTrue(facts.contains(key), "facts.txt missing '" + key + "':\n" + facts);
        }
        PlanetThermal thermal = planet.properties().thermal();
        double surfaceK = planet.properties().temperature();
        // The sheet must quote the canonical temperature of the canonical chain.
        assertTrue(facts.contains(StellarThermalModel.temperatureText(surfaceK)),
                "facts.txt does not quote the canonical surface temperature:\n" + facts);
        System.out.println("[ACT 1 preview] " + dir.getPath().replace('\\', '/'));
        System.out.println(facts);
        return new Rendered(dir, facts, thermal, surfaceK);
    }

    @Test
    void representativePlanetsShowTheWholeThermalChain() throws Exception {
        StarType[] ladder = {StarType.M, StarType.G, StarType.A, StarType.B};
        for (StarType type : ladder) {
            Rendered inner = render(type + "_INNER", type, true);
            Rendered outer = render(type + "_OUTER", type, false);

            // Same star, same system: the INNER planet must be visibly hotter than the outer one.
            assertTrue(inner.surfaceK() > outer.surfaceK() + 5.0,
                    type + " inner orbit must be hotter than the outer one: "
                            + inner.surfaceK() + " vs " + outer.surfaceK());
            assertTrue(inner.thermal().stellarFlux() > outer.thermal().stellarFlux(),
                    type + " inner flux must exceed the outer flux");
            assertTrue(inner.thermal().orbitAU() < outer.thermal().orbitAU(),
                    type + " inner AU must be smaller than the outer AU");

            // The stored chain explains the printed surface temperature exactly.
            for (Rendered r : new Rendered[]{inner, outer}) {
                assertEquals(StellarThermalModel.clampKelvin(
                                r.thermal().equilibriumK() * r.thermal().greenhouse()
                                        + r.thermal().internalK()),
                        r.surfaceK(), 1e-9, "stored chain does not explain " + r.dir().getName());
            }
        }
    }

    @Test
    void aBrighterStarSystemReadsHotterAtTheSameOrbitSlot() throws Exception {
        // Same slot (innermost), same galaxy: dim vs bright primary.
        Rendered dim = render("M_INNER", StarType.M, true);
        Rendered sun = render("G_INNER", StarType.G, true);
        Rendered bright = render("B_INNER", StarType.B, true);
        assertTrue(dim.surfaceK() < sun.surfaceK(), "M must be colder than G at slot 0");
        assertTrue(sun.surfaceK() < bright.surfaceK(), "G must be colder than B at slot 0");
    }
}
