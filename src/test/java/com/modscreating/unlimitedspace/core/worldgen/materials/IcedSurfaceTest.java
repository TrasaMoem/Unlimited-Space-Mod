package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 0.1 / STAGE 2 (ICE): an ice shell leads with the FROZEN families
 * (snow, ice, frozen ground). A generic sedimentary bed (calcite), a sorted clast deposit
 * (gravel) or plain stone must NOT become the ice-shell substrate merely because they fell into
 * the SEDIMENT family, and ordinary quartz sand must never blanket an ice shell.
 */
class IcedSurfaceTest {

    @Test
    void iceShellLeadsWithFrozenFamilies() {
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.FROZEN_ICE, PlanetSurface.SOLID_ICE));
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.FROZEN_SNOW, PlanetSurface.SOLID_ICE));
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.FROZEN_ROCK, PlanetSurface.SOLID_ICE));
    }

    @Test
    void genericSedimentAndStoneCannotBecomeTheIceSubstrate() {
        // calcite is a COHESIVE/LAMINATED sedimentary BED; gravel is a sorted clast deposit; stone
        // is plain rock. None of them is the dominant language of an ice shell.
        assertFalse(MaterialSemantics.mayLeadSurface(
                        MaterialSemanticFamily.ROCK, PlanetSurface.SOLID_ICE),
                "a generic rock (incl. calcite / cemented beds) must not lead an ice surface");
        assertFalse(MaterialSemantics.mayLeadSurface(
                        MaterialSemanticFamily.SEDIMENT, PlanetSurface.SOLID_ICE),
                "a generic sorted deposit (gravel) must not lead an ice surface");
        assertFalse(MaterialSemantics.mayLeadSurface(
                        MaterialSemanticFamily.SAND, PlanetSurface.SOLID_ICE),
                "ordinary sand must not dominate an ice shell");
    }

    @Test
    void iceDepositRoleIsFrozenSnowNotQuartzSand() {
        assertTrue(MaterialSemantics.mayLead(
                MaterialSemanticFamily.FROZEN_SNOW, MaterialRole.SEDIMENT, PlanetSurface.SOLID_ICE));
        assertFalse(MaterialSemantics.mayLead(
                        MaterialSemanticFamily.SAND, MaterialRole.SEDIMENT, PlanetSurface.SOLID_ICE),
                "quartz sand is a warm-world deposit; it must not fill the ice deposit role");
    }

    @Test
    void frozenRockStaysLegalWhereItsSignalExists() {
        // The fix must keep ice geology - rock exposure on ridges is real on an ice shell.
        assertTrue(MaterialSemantics.mayLead(
                MaterialSemanticFamily.FROZEN_ROCK, MaterialRole.MOUNTAIN, PlanetSurface.SOLID_ICE));
    }
}