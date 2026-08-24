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
 * Pool of 20 000 epic multilingual ASTEROID FIELD names (one bundled text resource).
 * Every asteroid cluster gets a RANDOM name from this pool; duplicates between
 * fields are explicitly allowed - the pool is simply sampled uniformly at random
 * per field identity {@code (systemIndex, clusterIndex)} via a stable hash, so a
 * field keeps the same name across sessions and UI rebuilds.
 */
public final class AsteroidFieldNamePool {

    private static final String RESOURCE =
            "/assets/unlimitedspace/names/asteroid_field_names.txt";

    /** Resource lines look like {@code 00042. Rhenium Basin}. */
    private static final Pattern NAME_LINE = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");

    private static final String[] NAMES = load();

    private AsteroidFieldNamePool() {}

    /** Number of loaded names (20 000 when the bundled resource is present). */
    public static int size() { return NAMES.length; }

    /**
     * The stable random display name of the asteroid cluster {@code clusterIndex}
     * in system {@code systemIndex}. Never null; falls back to {@code "Asteroid Field"}
     * if the pool failed to load.
     */
    public static String forField(int systemIndex, int clusterIndex) {
        long h = systemIndex * 0xA24BAED4963EE407L + clusterIndex * 0x9FB21C651E98DF25L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return NAMES[(int) Long.remainderUnsigned(h, NAMES.length)];
    }

    private static String[] load() {
        List<String> out = new ArrayList<>(20480);
        try (InputStream in = AsteroidFieldNamePool.class.getResourceAsStream(RESOURCE)) {
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
        if (out.isEmpty()) return new String[]{"Asteroid Field"};
        return out.toArray(new String[0]);
    }
}
