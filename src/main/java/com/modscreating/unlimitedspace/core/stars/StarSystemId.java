package com.modscreating.unlimitedspace.core.stars;

/**
 * Stable identity of a star system inside a galaxy. A value object based on the
 * system's fixed index; it is independent of any display name and of how many
 * other systems have been generated.
 *
 * @param index stable, non-negative system index
 */
public record StarSystemId(int index) {

    public StarSystemId {
        if (index < 0) throw new IllegalArgumentException("system index must be >= 0");
    }

    public static StarSystemId of(int index) {
        return new StarSystemId(index);
    }

    public String code() {
        return "system_" + String.format("%04d", index);
    }

    @Override
    public String toString() {
        return code();
    }
}
