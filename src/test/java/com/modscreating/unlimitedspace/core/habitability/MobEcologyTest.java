package com.modscreating.unlimitedspace.core.habitability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACT 2 — the mob lottery (F) and the derived life-state rules (LifeState).
 */
@Tag("audit")
class MobEcologyTest {

    @Test
    void nonHabitableWorldsNeverHaveMobs() {
        for (long seed = 0; seed < 2000; seed++) {
            MobEcologyProfile p = MobEcologyProfile.of(seed, false);
            assertFalse(p.mobsEnabled(), "non-habitable world can never have mobs, seed " + seed);
            assertNull(p.placeholderSpecies());
        }
    }

    @Test
    void habitableWorldMobLotteryIsTwentyPercent() {
        int n = 40_000;
        int mobs = 0;
        for (long seed = 1; seed <= n; seed++) {
            if (MobEcologyProfile.of(seed, true).mobsEnabled()) mobs++;
        }
        double share = mobs / (double) n;
        assertTrue(Math.abs(share - 0.20) < 0.012,
                "mob lottery share must be ~20%, got " + share);
    }

    @Test
    void sameSeedSameResultAndDeterministicPlaceholder() {
        assertEquals(MobEcologyProfile.of(12345L, true), MobEcologyProfile.of(12345L, true));
        MobEcologyProfile enabled = MobEcologyProfile.of(1L, true);
        if (enabled.mobsEnabled()) {
            assertEquals(MobEcologyProfile.PLACEHOLDER_SPECIES, enabled.placeholderSpecies(),
                    "ACT 2 uses the vanilla skeleton as the placeholder species");
        }
        // The reserved species-count architecture stays in [1, 20].
        for (long seed = 0; seed < 500; seed++) {
            int count = MobEcologyProfile.speciesCount(seed);
            assertTrue(count >= MobEcologyProfile.SPECIES_COUNT_MIN
                            && count <= MobEcologyProfile.SPECIES_COUNT_MAX,
                    "reserved species count in [1,20], got " + count);
        }
    }

    @Test
    void lifeStateRulesAreExact() {
        LifeState none = LifeState.notHabitable();
        assertFalse(none.actualHabitable());
        assertFalse(none.vegetationPermitted());
        assertFalse(none.mobsEnabled());
        assertFalse(none.structureEligible());

        LifeState habitableNoMobs = LifeState.of(true, false);
        assertTrue(habitableNoMobs.actualHabitable());
        assertTrue(habitableNoMobs.vegetationPermitted(), "habitable → vegetation permitted");
        assertFalse(habitableNoMobs.mobsEnabled(), "habitable ≠ has mobs");
        assertTrue(habitableNoMobs.structureEligible(), "habitable → structurally eligible");

        LifeState habitableWithMobs = LifeState.of(true, true);
        assertTrue(habitableWithMobs.mobsEnabled());
    }
}
