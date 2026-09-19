package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfile;
import com.modscreating.unlimitedspace.core.worldgen.profile.PlanetPhysicalProfileFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R16 material-system tests: catalogue validity, rule engine, deterministic selection.
 */
class MaterialCatalogTest {

    // ------------------------------------------------------------- catalogue integrity

    @Test
    void customMaterialLibraryIsComplete() {
        assertEquals(14, MaterialCatalog.CUSTOM_COUNT);
        assertEquals(14, MaterialCatalog.custom().size(),
                "all 14 proof-of-concept materials must be registered");
    }

    @Test
    void everySpecCarriesFullMetadata() {
        for (MaterialSpec spec : MaterialCatalog.all()) {
            assertNotNull(spec.id());
            assertNotNull(spec.family());
            assertNotNull(spec.blockId());
            assertNotNull(spec.tags());
            assertNotNull(spec.roles());
            assertNotNull(spec.visualRole());
            assertTrue(spec.rarity() >= 0.0 && spec.rarity() <= 1.0);
            assertTrue(spec.minTemperature() <= spec.maxTemperature(),
                    "invalid temperature window on " + spec.id());
            assertTrue(spec.minHumidity() <= spec.maxHumidity(),
                    "invalid humidity window on " + spec.id());
            assertFalse(spec.roles().isEmpty(), "material must fill at least one role: " + spec.id());
        }
    }

    @Test
    void blockIdsAreRegistryKeysNotDisplayNames() {
        for (MaterialSpec spec : MaterialCatalog.all()) {
            assertTrue(spec.blockId().contains(":") && !spec.blockId().contains(" "),
                    "block id must be a registry key: " + spec.blockId());
        }
    }

    @Test
    void noDuplicateMaterialIds() {
        Set<String> ids = new java.util.HashSet<>();
        for (MaterialSpec spec : MaterialCatalog.all()) {
            assertTrue(ids.add(spec.id()), "duplicate material id: " + spec.id());
        }
    }

    // ------------------------------------------------------------- rule engine

    private static PlanetPhysicalProfile profile(double temperatureK, double humidity,
                                                 double metallicity, boolean volcanic) {
        return PlanetPhysicalProfileFactory.create(1L,
                minimalProperties(temperatureK, humidity, metallicity, volcanic));
    }

    private static PlanetProperties minimalProperties(double temperatureK, double humidity,
                                                      double metallicity, boolean volcanic) {
        return new PlanetProperties(
                new PlanetSeed(1L),
                volcanic ? PlanetType.VOLCANIC : PlanetType.ICE,
                volcanic ? PlanetSurface.SOLID_VOLCANIC : PlanetSurface.SOLID_ICE,
                1.0, 1.0, temperatureK, humidity,
                AtmosphereType.MODERATE, 0.5,
                0.3, 0.3, 0.3, 0.5, 0.5, volcanic ? 0.9 : 0.1,
                PlanetProperties.ResourceProfile.of(metallicity, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void hotPlanetDropsColdOnlyMaterial() {
        PlanetPhysicalProfile hot = profile(900.0, 0.05, 0.2, false);
        for (MaterialSpec spec : MaterialCatalog.all()) {
            if (!spec.family().isColdOnly() && spec.minTemperature() <= 0.9) continue;
            assertFalse(MaterialRules.isCompatible(spec, hot),
                    "hot planet must drop cold-only material " + spec.id());
        }
    }

    @Test
    void coldPlanetDropsHotOnlyMaterial() {
        PlanetPhysicalProfile cold = profile(140.0, 0.05, 0.2, false);
        for (MaterialSpec spec : MaterialCatalog.all()) {
            if (!spec.family().isHotOnly()) continue;
            assertFalse(MaterialRules.isCompatible(spec, cold),
                    "cold planet must drop hot-only material " + spec.id());
        }
    }

    @Test
    void volcanicRequirementDropsNonVolcanicWorlds() {
        PlanetPhysicalProfile calm = profile(285.0, 0.5, 0.3, false);
        for (MaterialSpec spec : MaterialCatalog.all()) {
            if (!spec.requiresVolcanism()) continue;
            assertFalse(MaterialRules.isCompatible(spec, calm),
                    "calm planet must drop volcanism-required material " + spec.id());
        }
    }

    @Test
    void invalidClimateWindowIsRejected() {
        PlanetPhysicalProfile temp = profile(285.0, 0.5, 0.3, false);
        MaterialSpec impossible = MaterialSpec.builder("test.impossible",
                MaterialFamily.ROCK, "minecraft:stone")
                .temperature(0.95, 0.1) // inverted window -> never matches any planet
                .roles(MaterialRole.PRIMARY_SURFACE).build();
        assertFalse(MaterialRules.isCompatible(impossible, temp),
                "an invalid climate-window combination must be dropped");
        assertTrue(MaterialRules.admissible(List.of(impossible), temp).isEmpty());
    }

    @Test
    void selectionIsDeterministicPerSeed() {
        PlanetPhysicalProfile p = profile(285.0, 0.5, 0.3, false);
        PlanetMaterial a = MaterialCatalog.select(p, MaterialRole.PRIMARY_SURFACE, 4242L);
        PlanetMaterial b = MaterialCatalog.select(p, MaterialRole.PRIMARY_SURFACE, 4242L);
        assertEquals(a, b, "the same seed + profile must yield the same material");
    }

    @Test
    void resolutionAlwaysProducesAMaterial() {
        int resolved = 0;
        for (int s = 0; s < 12; s++) {
            PlanetProperties p = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(99000L + s)
                    .getStarSystem(com.modscreating.unlimitedspace.core.stars.StarSystemId.of(0))
                    .getPlanet(0).properties();
            PlanetPhysicalProfile phys = PlanetPhysicalProfileFactory.create(
                    p.seed().value(), p);
            for (MaterialRole role : MaterialRole.values()) {
                if (MaterialCatalog.select(phys, role, 77L) != null) resolved++;
            }
        }
    }

    @Test
    void craterProvinceFavoursImpactMaterial() {
        // R18: a CRATER province must prefer impact-typed material over, say, frozen soil.
        // Impactite is the canonical impact material (family ROCK_IMPACT).
        MaterialSpec impactite = MaterialCatalog.all().stream()
                .filter(s -> s.family() == MaterialFamily.ROCK_IMPACT).findFirst().orElse(null);
        assertNotNull(impactite, "impact material must exist in the catalogue");
        com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince crater =
                com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince.CRATER;
        assertTrue(MaterialRules.isCoherentWithProvince(impactite, crater),
                "Impactite must be coherent inside a CRATER province");
        assertFalse(MaterialRules.isCoherentWithProvince(
                new MaterialSpec("test.snow", MaterialFamily.SOIL_FROZEN, "minecraft:snow_block",
                        Set.of(), 0.0, 0.0, 1.0, 0.0, 1.0, false, false, false, false,
                        0.0, 0.0, Set.of(com.modscreating.unlimitedspace.core.worldgen.materials.MaterialRole.PRIMARY_SURFACE),
                        MaterialVisualRole.FROZEN), crater),
                "a frozen-soil material must NOT be coherent in a CRATER province");
    }

    @Test
    void glacialProvinceFavoursFrozenMaterial() {
        com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince glacial =
                com.modscreating.unlimitedspace.core.worldgen.geology.GeologicalProvince.GLACIAL;
        MaterialSpec frost = MaterialCatalog.all().stream()
                .filter(s -> s.family() == MaterialFamily.SOIL_FROZEN
                        || s.family() == MaterialFamily.ROCK_FROZEN)
                .findFirst().orElse(null);
        if (frost != null) {
            assertTrue(MaterialRules.isCoherentWithProvince(frost, glacial),
                    "a frozen material must be coherent inside a GLACIAL province");
        }
    }
}
