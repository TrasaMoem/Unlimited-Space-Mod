package com.modscreating.unlimitedspace.core.galaxy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pool of 30 000 creative multilingual natural-satellite names (one bundled text
 * resource). Every procedural moon gets a RANDOM name from this pool.
 *
 * <p>The pick is a hash of {@code (systemIndex, orbitIndex, moonIndex)}, so it is
 * arbitrary yet STABLE: the same moon always resolves to the same name across
 * sessions, UI rebuilds and dimension reloads. Duplicates between different moons
 * are allowed BY DESIGN - the pool is simply sampled uniformly at random per moon
 * identity (30 000 names cannot uniquely cover the whole galaxy anyway).
 */
public final class MoonNamePool {

    private static final String RESOURCE =
            "/assets/unlimitedspace/names/moon_names.txt";

    /** Resource lines look like {@code 00042. Ognevsahilalele}. */
    private static final Pattern NAME_LINE = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");

    private static final String[] NAMES = load();

    private MoonNamePool() {}

    /** Number of loaded names (30 000 when the bundled resource is present). */
    public static int size() { return NAMES.length; }

    /**
     * The stable random display name of the moon at
     * {@code (systemIndex, orbitIndex, moonIndex)}. Never returns null; falls back to
     * {@code "Moon"} if the pool failed to load.
     */
    public static String forMoon(int systemIndex, int orbitIndex, int moonIndex) {
        long h = systemIndex * 0x9E3779B97F4A7C15L
                + orbitIndex * 0xC2B2AE3D27D4EB4FL
                + moonIndex * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return NAMES[(int) Long.remainderUnsigned(h, NAMES.length)];
    }

    private static String[] load() {
        List<String> out = new ArrayList<>(32768);
        try (InputStream in = MoonNamePool.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        Matcher m = NAME_LINE.matcher(line);
                        if (m.matches()) out.add(m.group(1));
                    }
                }
            }
        } catch (Exception ignored) {
            // a missing/corrupt list must never break worldgen or the UI
        }
        if (out.isEmpty()) return new String[]{"Moon"};
        return out.toArray(new String[0]);
    }
}