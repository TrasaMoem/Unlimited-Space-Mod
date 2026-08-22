package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R14.9 — star stage / life-stage derivation tests. Proves the domain can distinguish the required
 * stages (red dwarf, blue dwarf, normal, giant, supergiant, white dwarf, neutron star, black hole,
 * supernova) from existing {@link Star} data without inventing a second model or impossible
 * combinations.
 */
class StarStageTest {

    private static final long SEED = 424242L;
    private static final StarSystemId SYSTEM = StarSystemId.of(1);

    private static Star star(StarType type, double temp, double size, double lum) {
        long seed = Seeds.derive(SEED, "us.test.star.stage", type.ordinal());
        return Star.of(new StarId(SYSTEM), seed, type, temp, size, lum, type.colorRgb());
    }

    @Test
    void redDwarfIsACoolMClassStar() {
        assertEquals(StarStage.RED_DWARF, StarStage.from(star(StarType.M, 3000.0, 0.3, 0.01)));
    }

    @Test
    void normalStarIsTheMainSequenceBasis() {
        assertEquals(StarStage.MAIN_SEQUENCE, StarStage.from(star(StarType.G, 5600.0, 1.0, 1.0)));
        assertEquals(StarStage.MAIN_SEQUENCE, StarStage.from(star(StarType.F, 7000.0, 1.2, 3.0)));
    }

    @Test
    void hotMainSequenceIsNotMistakenForBlueDwarf() {
        // A large hot O/B main-sequence star is a MAIN_SEQUENCE, never a compact blue dwarf.
        assertEquals(StarStage.MAIN_SEQUENCE, StarStage.from(star(StarType.B, 20000.0, 8.0, 5000.0)));
        assertEquals(StarStage.MAIN_SEQUENCE, StarStage.from(star(StarType.O, 40000.0, 20.0, 50000.0)));
    }

    @Test
    void blueDwarfIsACompactHotStar() {
        assertEquals(StarStage.BLUE_DWARF, StarStage.from(star(StarType.A, 12000.0, 0.5, 2.0)));
    }

    @Test
    void giantAndSupergiantAreDistinct() {
        assertEquals(StarStage.GIANT, StarStage.from(star(StarType.GIANT, 4000.0, 20.0, 200.0)));
        assertEquals(StarStage.SUPERGIANT, StarStage.from(star(StarType.SUPERGIANT, 8000.0, 80.0, 50000.0)));
    }

    @Test
    void whiteDwarfAndNeutronStarAreCompactRemnants() {
        assertEquals(StarStage.WHITE_DWARF, StarStage.from(star(StarType.A, 20000.0, 0.05, 0.01)));
        assertEquals(StarStage.NEUTRON_STAR, StarStage.from(star(StarType.B, 30000.0, 0.01, 0.0002)));
    }

    @Test
    void blackHoleIsNeverAGlowingSun() {
        assertEquals(StarStage.BLACK_HOLE, StarStage.from(star(StarType.BLACK_HOLE, 0.0, 1.0, 0.0)));
    }

    @Test
    void supernovaIsDistinctFromSupergiant() {
        // Transient luminous shell, not a persistent supergiant.
        assertEquals(StarStage.SUPERNOVA, StarStage.from(star(StarType.SUPERGIANT, 10000.0, 300.0, 200000.0)));
        assertEquals(StarStage.SUPERGIANT, StarStage.from(star(StarType.SUPERGIANT, 10000.0, 80.0, 50000.0)));
    }

    @Test
    void compactStagesAreMarkedCompactAndHugeStagesHuge() {
        assertEquals(true, StarStage.RED_DWARF.isCompact());
        assertEquals(true, StarStage.WHITE_DWARF.isCompact());
        assertEquals(false, StarStage.GIANT.isCompact());
        assertEquals(true, StarStage.SUPERGIANT.isHuge());
        assertEquals(true, StarStage.SUPERNOVA.isHuge());
    }
}
