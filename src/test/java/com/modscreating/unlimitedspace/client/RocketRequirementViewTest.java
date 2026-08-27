package com.modscreating.unlimitedspace.client;

import com.modscreating.unlimitedspace.client.nav.RocketRequirementView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification for the ROCKET-tab requirement display rules that were repeatedly broken:
 * <ul>
 *   <li>no destination selected -> requirements read "-" (never stale or fabricated numbers);</li>
 *   <li>DIST FUEL is always rendered as a number (extra kg for the trip length), never as the
 *       bare "adjacent/free" placeholder.</li>
 * </ul>
 * Pure logic; no Minecraft client required.
 */
class RocketRequirementViewTest {

    @Test
    void noDestinationAlwaysHidesRequirements() {
        // Even with realistic stale numbers lingering, no destination means "-".
        assertFalse(RocketRequirementView.showRequirements(false, 14269, 57127));
        assertFalse(RocketRequirementView.showRequirements(false, 0, 0));
    }

    @Test
    void destinationWithRequirementsShowsThem() {
        assertTrue(RocketRequirementView.showRequirements(true, 14269, 57127));
    }

    @Test
    void destinationWithoutAnyDataStillHides() {
        // Destination picked but nothing computed yet -> "-".
        assertFalse(RocketRequirementView.showRequirements(true, 0, 0));
    }

    @Test
    void liftOffPaidSurchargeIsADeltaVNumber() {
        assertEquals("+1500 dV", RocketRequirementView.liftOffText(1500));
        assertEquals("+4000 dV", RocketRequirementView.liftOffText(4000));
    }

    @Test
    void liftOffFreeStartReadsOrbitStartFree() {
        assertEquals("orbit start (free)", RocketRequirementView.liftOffText(0));
    }

    @Test
    void distFuelIsAlwaysANumber() {
        assertEquals("+1542 kg", RocketRequirementView.distFuelText(1542.4));
        // An adjacent/free hop is still a number, never the bare placeholder.
        assertEquals("+0 kg", RocketRequirementView.distFuelText(0));
        assertFalse(RocketRequirementView.distFuelText(0).contains("adjacent"));
        assertFalse(RocketRequirementView.distFuelText(0).contains("free"));
    }

    @Test
    void distFuelPaidFlag() {
        assertTrue(RocketRequirementView.distFuelPaid(1542.4));
        assertFalse(RocketRequirementView.distFuelPaid(0));
    }

    @Test
    void tripTimingRequiresADestinationAndConsumption() {
        assertFalse(RocketRequirementView.showTripTiming(false, 1836.55));
        assertFalse(RocketRequirementView.showTripTiming(true, 0));
        assertTrue(RocketRequirementView.showTripTiming(true, 1836.55));
    }
}
