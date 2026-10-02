package com.modscreating.unlimitedspace.core.worldgen.materials;

import com.modscreating.unlimitedspace.core.planets.PlanetSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACT worldgen fix — STAGE 0.1 / STAGE 2: a VOLCANIC world must never be surfaced with sand, and
 * red dust must never become its ordinary dominant material. The volcanic language is dark
 * basalt / cooled rock, ash and cinder, with sulfur only as a sparse chemical accent.
 *
 * <p>These are DIRECT semantic assertions on the single authority
 * {@link MaterialSemantics#mayLead}, so they cannot be satisfied by a producer existing somewhere
 * else - they pin the gate itself.
 */
class VolcanicNoSandTest {

    @Test
    void volcanicForbidsSandAsSurfaceAndAsDeposit() {
        assertFalse(MaterialSemantics.mayLeadSurface(
                        MaterialSemanticFamily.SAND, PlanetSurface.SOLID_VOLCANIC),
                "sand must never lead a volcanic surface");
        assertFalse(MaterialSemantics.mayLead(
                        MaterialSemanticFamily.SAND, MaterialRole.PRIMARY_SURFACE,
                        PlanetSurface.SOLID_VOLCANIC),
                "sand must never lead the PRIMARY surface of a volcanic world");
        // The measured leak: the SEDIMENT role was the elected role on ~41% of a volcanic world,
        // and quartz sand was allowed to fill it.
        assertFalse(MaterialSemantics.mayLead(
                        MaterialSemanticFamily.SAND, MaterialRole.SEDIMENT,
                        PlanetSurface.SOLID_VOLCANIC),
                "sand must never lead the SEDIMENT (deposit) role of a volcanic world");
    }

    @Test
    void volcanicLeadsWithDarkRockAndAsh() {
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.VOLCANIC_DARK, PlanetSurface.SOLID_VOLCANIC));
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.VOLCANIC_ASH, PlanetSurface.SOLID_VOLCANIC));
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.DARK_ROCK, PlanetSurface.SOLID_VOLCANIC));
        // Ash / tuff / cinder is the ONLY family that may lead the deposit role on a volcanic world.
        assertTrue(MaterialSemantics.mayLead(
                MaterialSemanticFamily.VOLCANIC_ASH, MaterialRole.SEDIMENT,
                PlanetSurface.SOLID_VOLCANIC));
    }

    @Test
    void volcanicRedDustIsNotAnOrdinaryDominantMaterial() {
        // Red dust is a rare, context-earned accent, never the blanket surface of a volcanic world.
        assertFalse(MaterialSemantics.mayLeadSurface(
                        MaterialSemanticFamily.RED_DUST, PlanetSurface.SOLID_VOLCANIC),
                "red dust must not dominate a volcanic surface");
        assertFalse(MaterialSemantics.mayLead(
                        MaterialSemanticFamily.RED_DUST, MaterialRole.SEDIMENT,
                        PlanetSurface.SOLID_VOLCANIC),
                "red dust must not lead the deposit role of a volcanic world");
        assertFalse(MaterialSemantics.mayLead(
                        MaterialSemanticFamily.RED_DUST, MaterialRole.PRIMARY_SURFACE,
                        PlanetSurface.SOLID_VOLCANIC),
                "red dust must not lead the primary surface of a volcanic world");
    }

    @Test
    void ordinarySandAndRedDustRemainLegalWhereTheyBelong() {
        // The fix is a SEMANTIC restriction, not a ban: a desert still leads with sand / red dust.
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.SAND, PlanetSurface.SOLID_DESERT));
        assertTrue(MaterialSemantics.mayLeadSurface(
                MaterialSemanticFamily.RED_DUST, PlanetSurface.SOLID_DESERT));
        assertTrue(MaterialSemantics.mayLead(
                MaterialSemanticFamily.SAND, MaterialRole.SEDIMENT, PlanetSurface.SOLID_DESERT));
    }
}