package com.modscreating.unlimitedspace.core.stars;

/**
 * Stable identity of the star of a given star system.
 *
 * @param system the owning star system
 */
public record StarId(StarSystemId system) {

    public static StarId of(StarSystem system) {
        return new StarId(system.id());
    }

    public String code() {
        return system.code() + "_star";
    }
}
