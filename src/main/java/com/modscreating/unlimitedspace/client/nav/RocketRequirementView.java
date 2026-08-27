package com.modscreating.unlimitedspace.client.nav;

/**
 * Pure, Minecraft-free display helpers for the ROCKET tab requirements block.
 * <p>
 * Extracted from {@link RocketControlNavigationScreen} so the two rules that were
 * repeatedly broken can be unit-tested without a game runtime:
 * <ul>
 *   <li>when no destination is selected the whole trip-requirement block must read "-";</li>
 *   <li>DIST FUEL is always rendered as a number (extra kg for the trip length), never as
 *       the bare placeholder "adjacent/free".</li>
 * </ul>
 */
public final class RocketRequirementView {

    private RocketRequirementView() {
    }

    /**
     * Whether the trip-requirement block (FUEL REQ/HAVE, THRUST REQ/HAVE, LIFT-OFF,
     * DIST FUEL, FUEL RATE, TRIP TIME) should be rendered.
     * <p>
     * A trip needs a real, selected destination: with none picked every row must read "-",
     * even if stale requirement numbers still linger in the client overlay from a previous
     * route.
     *
     * @param hasDestination whether the ROCKET tab has a destination selected
     * @param requiredFuelKg server-reported required fuel
     * @param thrustRequired server-reported required thrust
     */
    public static boolean showRequirements(boolean hasDestination,
                                           double requiredFuelKg, double thrustRequired) {
        return hasDestination && (requiredFuelKg > 0 || thrustRequired > 0);
    }

    /** Whether the FUEL RATE / TRIP TIME rows should be rendered. */
    public static boolean showTripTiming(boolean hasDestination, double consumptionKgS) {
        return hasDestination && consumptionKgS > 0;
    }

    /**
     * LIFT-OFF value text: an actual surcharge is "+X dV"; a free (orbit / asteroid / space)
     * start reads "orbit start (free)".
     */
    public static String liftOffText(double deltaV) {
        return deltaV > 0
                ? String.format(java.util.Locale.ROOT, "+%.0f dV", deltaV)
                : "orbit start (free)";
    }

    /**
     * DIST FUEL value text: this is the EXTRA fuel (kg) burned purely because the target
     * system is far away. It is always rendered as a number (analogous to LIFT-OFF), never
     * as a bare "adjacent/free" placeholder. So an adjacent/free hop shows "+0 kg".
     */
    public static String distFuelText(double distFuelKg) {
        return String.format(java.util.Locale.ROOT, "+%.0f kg", distFuelKg);
    }

    /** Whether the DIST FUEL row represents a real (paid) distance surcharge, for colour. */
    public static boolean distFuelPaid(double distFuelKg) {
        return distFuelKg > 0;
    }
}
