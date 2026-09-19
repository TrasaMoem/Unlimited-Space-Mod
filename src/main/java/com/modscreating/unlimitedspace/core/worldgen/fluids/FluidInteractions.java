package com.modscreating.unlimitedspace.core.worldgen.fluids;

/**
 * Deterministic block&harr;fluid contact reactions (R19 fluid-ecology stage).
 *
 * <p>NOT a fluid-chemistry engine — a small, closed set of 1:1 deterministic reactions that
 * the worldgen feature stage can apply at fluid/terrain contact columns. Each reaction returns
 * an opaque material block key (resolved by the Minecraft adapter with a safe fallback), never
 * a {@code BlockState}: the core stays registry-free.
 *
 * <p>Architectural hook: more reaction pairs may be added later, but they must stay
 * deterministic and closed (no runtime chemistry simulation).
 */
public final class FluidInteractions {

    /** Deterministic contact outcome: the altered material key (or {@code null} = no reaction). */
    public record ContactOutcome(String materialKey, String reactionId) {
        public boolean occurred() {
            return materialKey != null;
        }
    }

    /** Shared outcome constants — the currently implemented reactions. */
    private static final ContactOutcome COOLED_VOLCANIC =
            new ContactOutcome("unlimitedspace:cinderstone", "molten_cooled");
    private static final ContactOutcome FROST_WEATHERED =
            new ContactOutcome("unlimitedspace:cryoclast", "cryo_frost");
    private static final ContactOutcome SULFUR_ALTERED =
            new ContactOutcome("unlimitedspace:sulfurstone", "sulfur_altered");
    private static final ContactOutcome NO_REACTION = new ContactOutcome(null, "none");

    private FluidInteractions() {}

    /**
     * Deterministic reaction when fluid {@code a} is in contact with fluid {@code b} (order
     * free). Currently implemented: MOLTEN &times; WATER_LIKE &rarr; cooled volcanic crust;
     * MOLTEN &times; CRYOGENIC &rarr; thermal-shock glassy crust; CRYOGENIC &times; any liquid
     * &rarr; frost-weathered surface; SULFURIC &times; WATER_LIKE &rarr; altered sulfurous crust.
     *
     * @return never null; {@link #NO_REACTION} for unimplemented pairs
     */
    public static ContactOutcome contact(FluidFamily a, FluidFamily b) {
        if (a == null || b == null) return NO_REACTION;
        if (a == b) return NO_REACTION;

        if (a == FluidFamily.MOLTEN || b == FluidFamily.MOLTEN) {
            FluidFamily other = a == FluidFamily.MOLTEN ? b : a;
            if (other == FluidFamily.WATER_LIKE) return COOLED_VOLCANIC;
            if (other == FluidFamily.CRYOGENIC) return FROST_WEATHERED;
            return NO_REACTION;
        }
        if (a == FluidFamily.SULFURIC || b == FluidFamily.SULFURIC) {
            FluidFamily other = a == FluidFamily.SULFURIC ? b : a;
            if (other == FluidFamily.WATER_LIKE) return SULFUR_ALTERED;
            return NO_REACTION;
        }
        if (a == FluidFamily.CRYOGENIC || b == FluidFamily.CRYOGENIC) {
            FluidFamily other = a == FluidFamily.CRYOGENIC ? b : a;
            if (other != FluidFamily.NONE) return FROST_WEATHERED;
        }
        return NO_REACTION;
    }
}