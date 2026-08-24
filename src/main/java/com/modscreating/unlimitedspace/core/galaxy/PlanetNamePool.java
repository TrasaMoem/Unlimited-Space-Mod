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
 * Pool of 10 000 creative multilingual planet names (two bundled text resources,
 * 5 000 names each). Every procedural planet gets a RANDOM name from this pool.
 *
 * <p>The pick is a hash of {@code (systemIndex, orbitIndex)}, so it is arbitrary yet
 * STABLE: the same planet always resolves to the same name across sessions, UI
 * rebuilds and dimension reloads. Duplicates between different planets are allowed
 * by design - the pool is simply sampled uniformly at random per planet identity.
 */
public final class PlanetNamePool {

    private static final String[] FILES = {
            "/assets/unlimitedspace/names/planet_names_a.txt",
            "/assets/unlimitedspace/names/planet_names_b.txt"};

    /** Resource lines look like {@code 0042. Waldthalion} or {@code 0001. Qasrirkhayris Bloom}. */
    private static final Pattern NAME_LINE = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");

    private static final String[] NAMES = load();

    private PlanetNamePool() {}

    /** Number of loaded names (10 000 when the bundled resources are present). */
    public static int size() { return NAMES.length; }

    /**
     * The stable random display name of the planet at {@code (systemIndex, orbitIndex)}.
     * Never returns null; falls back to {@code "Planet"} if the pool failed to load.
     */
    public static String forPlanet(int systemIndex, int orbitIndex) {
        long h = systemIndex * 0x9E3779B97F4A7C15L + orbitIndex * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return NAMES[(int) Long.remainderUnsigned(h, NAMES.length)];
    }

    private static String[] load() {
        List<String> out = new ArrayList<>(10240);
        for (String file : FILES) {
            try (InputStream in = PlanetNamePool.class.getResourceAsStream(file)) {
                if (in == null) continue;
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        Matcher m = NAME_LINE.matcher(line);
                        if (m.matches()) out.add(m.group(1));
                    }
                }
            } catch (Exception ignored) {
                // a missing/corrupt list must never break worldgen or the UI
            }
        }
        if (out.isEmpty()) return new String[]{"Planet"};
        return out.toArray(new String[0]);
    }
}
