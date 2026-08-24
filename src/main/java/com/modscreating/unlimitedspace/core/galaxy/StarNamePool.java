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
 * Pool of 10 000 epic multilingual PRIMARY STAR names (one bundled text resource).
 * Every star gets a RANDOM name from this pool; duplicates between stars are
 * explicitly allowed - the pool is simply sampled uniformly at random per star
 * identity {@code (systemIndex, starIndex)} via a stable hash, so a star keeps
 * the same name across sessions and UI rebuilds.
 */
public final class StarNamePool {

    private static final String RESOURCE =
            "/assets/unlimitedspace/names/star_names.txt";

    /** Resource lines look like {@code 00042. Titan Lumirayx}. */
    private static final Pattern NAME_LINE = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");

    private static final String[] NAMES = load();

    private StarNamePool() {}

    /** Number of loaded names (10 000 when the bundled resource is present). */
    public static int size() { return NAMES.length; }

    /**
     * The stable random display name of the star {@code starIndex} in system
     * {@code systemIndex}. Never null; falls back to {@code "Star"} if the pool
     * failed to load.
     */
    public static String forStar(int systemIndex, int starIndex) {
        long h = systemIndex * 0xD6E8FEB86659FD93L + starIndex * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return NAMES[(int) Long.remainderUnsigned(h, NAMES.length)];
    }

    private static String[] load() {
        List<String> out = new ArrayList<>(10240);
        try (InputStream in = StarNamePool.class.getResourceAsStream(RESOURCE)) {
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
        if (out.isEmpty()) return new String[]{"Star"};
        return out.toArray(new String[0]);
    }
}
