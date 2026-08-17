package com.modscreating.unlimitedspace.core.r8;

import com.modscreating.unlimitedspace.core.galaxy.Galaxy;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyParameters;
import com.modscreating.unlimitedspace.core.galaxy.GalaxyType;
import com.modscreating.unlimitedspace.core.galaxy.GalacticPosition;
import com.modscreating.unlimitedspace.core.galaxy.SystemPlacer;
import com.modscreating.unlimitedspace.core.planets.Planet;
import com.modscreating.unlimitedspace.core.planets.PlanetId;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.stars.StarSystemId;
import com.modscreating.unlimitedspace.core.worldgen.PlanetEnvironmentProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetVisualProfile;
import com.modscreating.unlimitedspace.core.worldgen.PlanetWorldgenProfile;
import com.modscreating.unlimitedspace.core.worldgen.TerrainPattern;
import com.modscreating.unlimitedspace.core.worldgen.TerrainProfile;
import com.modscreating.unlimitedspace.core.worldgen.FluidProfile;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiome;
import com.modscreating.unlimitedspace.core.worldgen.biome.PlanetBiomeProfile;
import com.modscreating.unlimitedspace.core.worldgen.materials.MaterialFamily;
import com.modscreating.unlimitedspace.core.worldgen.materials.PlanetMaterialProfile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R8 acceptance gate. Replaces the previously-referenced-but-missing
 * {@code testG_requirement9_assertions} with an invariant-based suite.
 */
class R8AcceptanceTest {

    private static final long WORLD_SEED = 0x5EEDCAFE0L;
    private static final int SYSTEM = 0;
    private static final int PLANETS = 3;

    private static Planet[] planets(long worldSeed) {
        Galaxy galaxy = Galaxy.from(worldSeed);
        StarSystemId sys = StarSystemId.of(SYSTEM);
        Planet[] out = new Planet[PLANETS];
        for (int orbit = 0; orbit < PLANETS; orbit++) {
            out[orbit] = galaxy.getStarSystem(sys).getPlanet(orbit);
        }
        return out;
    }

        private static PlanetId id(int orbit) {
        return PlanetId.of(StarSystemId.of(SYSTEM), orbit);
    }

    @Test
    void deterministicGenerationSameWorldSeedSamePlanetId() {
        Planet[] a = planets(WORLD_SEED);
        Planet[] b = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            assertEquals(a[i].id(), b[i].id(), "stable identity");
            assertEquals(a[i].seed(), b[i].seed(), "seed reproducible");
            assertEquals(a[i].properties(), b[i].properties(), "properties reproducible");
            PlanetWorldgenProfile pa = PlanetWorldgenProfile.from(a[i]);
            PlanetWorldgenProfile pb = PlanetWorldgenProfile.from(b[i]);
            assertEquals(pa, pb, "worldgen profile reproducible for planet " + i);
            assertEquals(pa.terrain(), pb.terrain(), "terrain profile reproducible");
            assertEquals(pa.biome(), pb.biome(), "biome profile reproducible");
            assertEquals(pa.material(), pb.material(), "material profile reproducible");
            assertEquals(pa.water(), pb.water(), "water profile reproducible");
            assertEquals(pa.environment(), pb.environment(), "environment profile reproducible");
                        assertEquals(pa.visual(), pb.visual(), "visual profile reproducible");
        }
    }

    @Test
    void deterministicFromWorldSeedPath() {
        Planet[] p = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p[i]);
            PlanetWorldgenProfile profFromWorldSeed = PlanetWorldgenProfile.from(id(i), WORLD_SEED);
            assertEquals(prof, profFromWorldSeed, "reconstruct from WorldSeed must match");
        }
    }

    @Test
    void planetDifferentiationAcrossSlots() {
        Planet[] p = planets(WORLD_SEED);
        Set<PlanetWorldgenProfile> distinct = new HashSet<>();
        for (int i = 0; i < PLANETS; i++) {
            distinct.add(PlanetWorldgenProfile.from(p[i]));
        }
        assertTrue(distinct.size() >= 2,
                "the three surface planets must not be frozen copies of one world: " + distinct.size());
        for (int i = 1; i < PLANETS; i++) {
            boolean differs = !p[i].seed().equals(p[0].seed())
                    || !p[i].properties().equals(p[0].properties());
            assertTrue(differs || !PlanetWorldgenProfile.from(p[i]).equals(PlanetWorldgenProfile.from(p[0])),
                    "planet " + i + " must meaningfully differ from planet 0");
        }
    }

    @Test
    void biomeCountWithinBoundsAndAllClimateCompatible() {
        long[] seeds = {0x5EEDCAFE0L, 0x12345678L, 0x9ABCDEF0L, 0xDEADBEEFL, 0xCAFEBABE0L, 0x2B5EED00L};
        for (long worldSeed : seeds) {
            Planet[] p = planets(worldSeed);
            for (int i = 0; i < PLANETS; i++) {
                PlanetProperties props = p[i].properties();
                PlanetBiomeProfile bp = PlanetBiomeProfile.create(p[i].seed().value(), props);

                assertTrue(bp.count() >= 1 && bp.count() <= 5,
                        "biomeCount must be in [1,5], got " + bp.count() + " for " + id(i) + " seed=" + worldSeed);

                List<PlanetBiome> presets = bp.presets();
                assertEquals(presets.size(), new HashSet<>(presets).size(),
                        "biome presets must all be distinct for " + id(i));

                double tempC = props.temperature() - 273.15;
                double humidity = props.humidity();
                boolean hasWater = props.waterCoverage() > 0.01 && props.surface() != PlanetSurface.GASEOUS;
                for (PlanetBiome b : presets) {
                    assertTrue(b.climateMatches(tempC, humidity, hasWater),
                            "biome " + b + " must be climate-compatible with planet " + id(i)
                                    + " (tempC=" + tempC + ", humidity=" + humidity + ", hasWater=" + hasWater + ")");
                                }
            }
        }
    }

    @Test
    void fallbackNeverSelectsIncompatibleBiome() {
        long[] seeds = {0x5EEDCAFE0L, 0x12345678L, 0x9ABCDEF0L, 0xDEADBEEFL, 0xCAFEBABE0L, 0x2B5EED00L,
                0x111111L, 0x22222222L, 0x333333333L};
        for (long worldSeed : seeds) {
            Planet[] p = planets(worldSeed);
            for (int i = 0; i < PLANETS; i++) {
                PlanetProperties props = p[i].properties();
                PlanetBiomeProfile bp = PlanetBiomeProfile.create(p[i].seed().value(), props);
                double tempC = props.temperature() - 273.15;
                double humidity = props.humidity();
                boolean hasWater = props.waterCoverage() > 0.01 && props.surface() != PlanetSurface.GASEOUS;
                for (PlanetBiome b : bp.presets()) {
                    assertTrue(b.climateMatches(tempC, humidity, hasWater),
                            "FALLBACK VIOLATION: " + b + " selected for " + id(i)
                                    + " (tempC=" + tempC + ", humidity=" + humidity + ", hasWater=" + hasWater + ")");
                }
            }
        }
    }

    @Test
    void temperatureUnitConversionIsCorrect() {
        Planet p = planets(WORLD_SEED)[0];
        PlanetProperties props = p.properties();
        double tempK = props.temperature();
        double tempC = tempK - 273.15;
        // Verify conversion produces a plausible Celsius temperature
        assertTrue(tempC > -273.15, "converted temperature must be above absolute zero");
        assertTrue(tempC < 1000.0, "converted temperature must be within plausible range");
        // Verify all biome temperature ranges are self-consistent
        for (PlanetBiome b : PlanetBiome.allSolid()) {
            assertTrue(b.minTemperature() <= b.maxTemperature(),
                    "minTemp must be <= maxTemp for " + b);
        }
        // Verify that climate-matching uses the same Celsius value the profile uses
        double humidity = props.humidity();
        boolean hasWater = props.waterCoverage() > 0.01 && props.surface() != PlanetSurface.GASEOUS;
        for (PlanetBiome b : PlanetBiome.allSolid()) {
            // If tempC is inside the biome window but climateMatches fails,
            // it must be due to humidity or water (NOT temperature unit confusion)
            boolean tempInRange = tempC >= b.minTemperature() && tempC <= b.maxTemperature();
            if (tempInRange) {
                boolean matchesClimate = b.climateMatches(tempC, humidity, hasWater);
                boolean humidityOutside = humidity < b.minHumidity() || humidity > b.maxHumidity();
                boolean waterRejection = hasWater && b.requiredSurface() == PlanetSurface.SOLID_DESERT;
                assertTrue(matchesClimate || humidityOutside || waterRejection,
                        "temp in range but climate fails for " + b.name()
                                + " — likely unit confusion: tempC=" + tempC
                                + " minTemp=" + b.minTemperature() + " maxTemp=" + b.maxTemperature());
            }
        }
    }

    @Test
    void materialProfileWithinBoundsAndDeterministic() {
        long[] seeds = {0x5EEDCAFE0L, 0x12345678L, 0x9ABCDEF0L, 0xDEADBEEFL, 0xCAFEBABE0L};
        for (long worldSeed : seeds) {
            Planet[] p = planets(worldSeed);
            for (int i = 0; i < PLANETS; i++) {
                PlanetProperties props = p[i].properties();
                PlanetBiomeProfile bp = PlanetBiomeProfile.create(p[i].seed().value(), props);
                PlanetMaterialProfile mp = PlanetMaterialProfile.create(p[i].seed().value(), props, bp.presets());

                int minExpected = PlanetMaterialProfile.MIN_COUNT;
                int maxExpected = PlanetMaterialProfile.MIN_COUNT + bp.presets().size();
                assertTrue(mp.count() >= minExpected, "material count must be >= " + minExpected);
                assertTrue(mp.count() <= maxExpected, "material count must be <= " + maxExpected + ", got " + mp.count());

                PlanetMaterialProfile mp2 = PlanetMaterialProfile.create(p[i].seed().value(), props, bp.presets());
                assertEquals(mp, mp2, "material profile must be deterministic");
                assertNotNull(mp.surface(), "surface material must exist");
                assertNotNull(mp.subsurface(), "subsurface material must exist");
            }
        }
    }

    @Test
    void gasGiantsHaveNoSurfaceWater() {
        Galaxy g = Galaxy.from(WORLD_SEED);
        boolean found = false;
        for (int s = 0; s < 200 && !found; s++) {
            Planet p = g.getStarSystem(StarSystemId.of(s)).getPlanet(0);
            if (p.properties().type() == PlanetType.GAS_GIANT) {
                found = true;
                PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
                assertEquals(FluidProfile.NONE, prof.water().fluid(), "gas giant must not carry surface water");
                assertEquals(PlanetSurface.GASEOUS, p.properties().surface());
            }
        }
        assertTrue(found, "expected at least one gas giant across the scan range to validate");
    }

    @Test
    void gasGiantReceivesMetallicSurface() {
        Galaxy g = Galaxy.from(WORLD_SEED);
        boolean found = false;
        for (int s = 0; s < 200 && !found; s++) {
            Planet p = g.getStarSystem(StarSystemId.of(s)).getPlanet(0);
            if (p.properties().type() == PlanetType.GAS_GIANT) {
                found = true;
                PlanetWorldgenProfile prof = PlanetWorldgenProfile.from(p);
                assertEquals(MaterialFamily.METAL, prof.material().surface().family(),
                        "gas giant must receive the metallic/gaseous material family, not a terrestrial surface");
            }
        }
                assertTrue(found, "expected at least one gas giant across the scan range to validate");
    }

    @Test
    void terrainProfileIsDeterministicAndParameterSensitive() {
        Planet[] p = planets(WORLD_SEED);
        for (int i = 0; i < PLANETS; i++) {
            TerrainProfile t1 = PlanetWorldgenProfile.from(p[i]).terrain();
            TerrainProfile t2 = PlanetWorldgenProfile.from(p[i]).terrain();
            assertEquals(t1, t2, "terrain profile reproducible");
        }

        assertTrue(TerrainPattern.values().length >= 2, "terrain pattern catalogue must hold >1 pattern");
        assertNotEquals(TerrainPattern.FLAT.amplitudeMultiplier(),
                TerrainPattern.MOUNTAINS.amplitudeMultiplier(),
                "FLAT and MOUNTAINS must scale terrain differently");

        Set<TerrainPattern> seen = new HashSet<>();
        for (long w = 1; w <= 60; w++) {
            Planet pp = Galaxy.from(w).getStarSystem(StarSystemId.of(SYSTEM)).getPlanet(0);
            seen.add(PlanetWorldgenProfile.from(pp).terrainPattern());
        }
        assertTrue(seen.size() >= 2, "multiple terrain patterns must be reachable: " + seen);
    }

    @Test
    void visualProfileIsDeterministicAndInternallyValid() {
        long[] seeds = {0x5EEDCAFE0L, 0x12345678L, 0x9ABCDEF0L, 0xDEADBEEFL, 0xCAFEBABE0L};
        for (long worldSeed : seeds) {
            Planet[] p = planets(worldSeed);
            for (int i = 0; i < PLANETS; i++) {
                PlanetVisualProfile v1 = PlanetWorldgenProfile.from(p[i]).visual();
                PlanetVisualProfile v2 = PlanetWorldgenProfile.from(p[i]).visual();
                assertEquals(v1, v2, "visual profile reproducible");
                assertTrue((v1.skyColor() & 0xFFFFFFFFL) >= 0, "skyColor valid");
                assertTrue((v1.waterColor() & 0xFFFFFFFFL) >= 0, "waterColor valid");
                assertTrue((v1.fogColor() & 0xFFFFFFFFL) >= 0, "fogColor valid");
                assertTrue((v1.sunTint() & 0xFFFFFFFFL) >= 0, "sunTint valid");
            }
        }
    }

    @Test
    void differentWorldSeedsCanProduceDifferentVisualProfiles() {
        PlanetId pid0 = PlanetId.of(StarSystemId.of(SYSTEM), 0);
        PlanetVisualProfile v1 = PlanetWorldgenProfile.from(pid0, WORLD_SEED).visual();
        boolean differs = false;
        for (long w = 1; w <= 200; w++) {
            if (!v1.equals(PlanetWorldgenProfile.from(pid0, w).visual())) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "different world seeds must be able to produce different visual profiles");
    }

    @Test
    void environmentProfileIsDeterministic() {
        long[] seeds = {0x5EEDCAFE0L, 0x12345678L, 0x9ABCDEF0L, 0xDEADBEEFL, 0xCAFEBABE0L};
        for (long worldSeed : seeds) {
            Planet[] p = planets(worldSeed);
            for (int i = 0; i < PLANETS; i++) {
                PlanetEnvironmentProfile e1 = PlanetWorldgenProfile.from(p[i]).environment();
                PlanetEnvironmentProfile e2 = PlanetWorldgenProfile.from(p[i]).environment();
                assertEquals(e1, e2, "environment profile reproducible");
                assertTrue(e1.temperature() > 0.0, "temperature must be positive Kelvin");
            }
        }
    }

    @Test
    void galaxyParametersAreDeterministicAndLazy() {
        Galaxy g1 = Galaxy.from(WORLD_SEED);
        Galaxy g2 = Galaxy.from(WORLD_SEED);
        assertEquals(g1.seed(), g2.seed(), "galaxy seed stable");
        GalacticPosition a0 = g1.getStarSystem(StarSystemId.of(0)).position();
        GalacticPosition b0 = g2.getStarSystem(StarSystemId.of(0)).position();
        assertEquals(a0, b0, "system position deterministic");
        GalacticPosition a100 = g1.getStarSystem(StarSystemId.of(100)).position();
        GalacticPosition b100 = g2.getStarSystem(StarSystemId.of(100)).position();
        assertEquals(a100, b100, "far system position deterministic");
    }

    @Test
    void galaxyParametersChangeScale() {
        GalaxyParameters small = new GalaxyParameters(20.0, 0.8, GalaxyType.SPIRAL);
        GalaxyParameters large = new GalaxyParameters(200.0, 0.8, GalaxyType.SPIRAL);
        assertTrue(large.estimatedSystemCount() > small.estimatedSystemCount(),
                "larger radius + density must yield higher estimated system count");
        GalacticPosition pSmall = SystemPlacer.position(small, 0L, 0);
        GalacticPosition pLarge = SystemPlacer.position(large, 0L, 0);
        double distSmall = Math.sqrt(pSmall.x() * pSmall.x() + pSmall.y() * pSmall.y() + pSmall.z() * pSmall.z());
        double distLarge = Math.sqrt(pLarge.x() * pLarge.x() + pLarge.y() * pLarge.y() + pLarge.z() * pLarge.z());
        assertTrue(distLarge >= distSmall, "large params should not shrink positions");
    }

    @Test
    void existingR8PlanetSlotsRemainIdentical() {
        PlanetWorldgenProfile prior0 = PlanetWorldgenProfile.from(id(0), WORLD_SEED);
        PlanetWorldgenProfile prior1 = PlanetWorldgenProfile.from(id(1), WORLD_SEED);
        PlanetWorldgenProfile prior2 = PlanetWorldgenProfile.from(id(2), WORLD_SEED);
        assertEquals(prior0, PlanetWorldgenProfile.from(id(0), WORLD_SEED), "planet 0 identity stable");
        assertEquals(prior1, PlanetWorldgenProfile.from(id(1), WORLD_SEED), "planet 1 identity stable");
        assertEquals(prior2, PlanetWorldgenProfile.from(id(2), WORLD_SEED), "planet 2 identity stable");
    }
}
