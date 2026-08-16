package com.modscreating.unlimitedspace.core.worldgen.structures;

import com.modscreating.unlimitedspace.core.planets.AtmosphereType;
import com.modscreating.unlimitedspace.core.planets.PlanetProperties;
import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import com.modscreating.unlimitedspace.core.planets.PlanetType;
import com.modscreating.unlimitedspace.core.seed.PlanetSeed;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StructureSelectorTest {

    private static final long STRUCT = 777222L;

    private static PlanetProperties props(PlanetSurface surface) {
        PlanetType type = surface == PlanetSurface.GASEOUS ? PlanetType.GAS_GIANT : PlanetType.ROCKY;
        return new PlanetProperties(
                new PlanetSeed(1234L), type, surface,
                1.0, 1.0, 285.0, 0.5,
                AtmosphereType.TRACE, 0.5,
                0.3, 0.3, 0.3, 0.5, 0.5, 0.2,
                PlanetProperties.ResourceProfile.of(0.5, false, 0.5),
                new PlanetProperties.BiomeParameters(1.0, 1.0),
                new PlanetProperties.GenerationParameters(0.0, 0.0, 1.0),
                1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void sameSeedSameChunkSamePlacement() {
        PlanetProperties p = props(PlanetSurface.SOLID_ROCKY);
        assertEquals(StructureSelector.decide(STRUCT, p, 10, 20, 160, 320),
                StructureSelector.decide(STRUCT, p, 10, 20, 160, 320));
    }

    @Test
    void differentChunksCanChangePlacement() {
        PlanetProperties p = props(PlanetSurface.SOLID_ROCKY);
        boolean present = false, empty = false;
        for (int cx = 0; cx < 4000; cx++) {
            Optional<StructureSelector.Outcome> o = StructureSelector.decide(STRUCT, p, cx, 0, cx * 16, 0);
            present |= o.isPresent();
            empty |= o.isEmpty();
            if (present && empty) break;
        }
        assertTrue(present, "structures should appear somewhere on a land planet");
        assertTrue(empty, "structures should be absent in most chunks (sparse)");
    }

    @Test
    void differentPlanetSeedsDifferSomewhere() {
        PlanetProperties p = props(PlanetSurface.SOLID_ROCKY);
        boolean different = false;
        outer:
        for (int cx = 0; cx < 12000; cx++) {
            boolean a = StructureSelector.decide(STRUCT, p, cx, 0, cx * 16, 0).isPresent();
            boolean c = StructureSelector.decide(STRUCT + 1, p, cx, 0, cx * 16, 0).isPresent();
            if (a != c) {
                different = true;
                break outer;
            }
        }
        assertTrue(different, "different structure seeds should diverge somewhere");
    }

    @Test
    void generationOrderDoesNotMatter() {
        PlanetProperties p = props(PlanetSurface.SOLID_ROCKY);
        int[][] chunks = {{3, 7}, {-5, 2}, {12, -9}, {0, 0}, {8, 8}};
        Map<String, Optional<StructureSelector.Outcome>> base = new HashMap<>();
        for (int[] c : chunks) {
            base.put(c[0] + "," + c[1], StructureSelector.decide(STRUCT, p, c[0], c[1], c[0] * 16, c[1] * 16));
        }
        Map<String, Optional<StructureSelector.Outcome>> reverse = new HashMap<>();
        for (int i = chunks.length - 1; i >= 0; i--) {
            int[] c = chunks[i];
            reverse.put(c[0] + "," + c[1], StructureSelector.decide(STRUCT, p, c[0], c[1], c[0] * 16, c[1] * 16));
        }
        assertEquals(base, reverse, "placement must not depend on the order chunks are visited");
    }

    @Test
    void deepSpaceLikeSurfacesAreStructureFree() {
        for (int i = 0; i < 300; i++) {
            assertTrue(StructureSelector.decide(STRUCT, props(PlanetSurface.OCEANIC), i, 0, i * 16, 0).isEmpty());
            assertTrue(StructureSelector.decide(STRUCT, props(PlanetSurface.GASEOUS), i, 0, i * 16, 0).isEmpty());
        }
    }
}