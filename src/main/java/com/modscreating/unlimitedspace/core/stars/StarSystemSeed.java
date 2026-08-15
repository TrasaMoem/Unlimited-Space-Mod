package com.modscreating.unlimitedspace.core.stars;

import com.modscreating.unlimitedspace.core.seed.Seeds;

/**
 * Marker value object for a star system seed, mirroring {@link StarSystem#seed()}.
 *
 * @param value the 64-bit seed
 */
public record StarSystemSeed(long value) {

    public static StarSystemSeed derive(long galaxySeed, int index) {
        return new StarSystemSeed(Seeds.starSystem(galaxySeed, index));
    }
}
