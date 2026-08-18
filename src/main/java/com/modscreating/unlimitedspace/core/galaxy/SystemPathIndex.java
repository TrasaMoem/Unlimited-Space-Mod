package com.modscreating.unlimitedspace.core.galaxy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, Minecraft-free resolver of the owning star-system index from a dimension path,
 * e.g. {@code "planet/system_0000_planet_01/orbit"} -> 0. Used by the
 * {@code /unlimitedspace system} command. Returns 0 (system_0000) when the path maps to
 * no generated system yet (overworld / space), as a documented deterministic fallback.
 */
public final class SystemPathIndex {

    private static final Pattern SYSTEM_INDEX = Pattern.compile("system_(\\d+)");

    private SystemPathIndex() {
    }

    public static int fromDimensionPath(String dimensionPath) {
        if (dimensionPath != null) {
            Matcher m = SYSTEM_INDEX.matcher(dimensionPath);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 0;
    }
}