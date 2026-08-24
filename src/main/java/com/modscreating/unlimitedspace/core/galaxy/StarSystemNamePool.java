package com.modscreating.unlimitedspace.core.galaxy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pool of 10 000 epic multilingual STAR SYSTEM names (one bundled text resource)
 * with a GUARANTEED-UNIQUE assignment: no two star systems of one galaxy share a name.
 *
 * <p>How uniqueness is achieved: the canonical system identity is its grid cell
 * (see {@code SpatialGrid}: cellSize = sqrt(pi / starDensity), disc test
 * {@code cx^2 + cz^2 <= radiusCells^2}, Cantor-paired cell index). All populated
 * cells are enumerated in ascending Cantor-index order and numbered
 * {@code rank = 0, 1, 2, ...}. The default galaxy has ~8 000 populated cells while
 * the pool holds 10 000 names, so {@code rank} maps injectively into the pool.
 * A seeded AFFINE PERMUTATION modulo the pool size then scrambles ranks into
 * arbitrary-looking slots - every distinct rank yields a DISTINCT name.
 *
 * <p>The result is deterministic per (worldSeed, systemIndex): reopening the map or
 * reloading dimensions never renames a system. If the galaxy is configured larger
 * than the pool (more populated cells than names), the permutation wraps and the
 * strict-uniqueness guarantee degrades gracefully instead of failing.
 */
public final class StarSystemNamePool {

    private static final String RESOURCE =
            "/assets/unlimitedspace/names/system_names.txt";

    /** Resource lines look like {@code 00042. Titan Lumirayx}. */
    private static final Pattern NAME_LINE = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");

    private static final String[] NAMES = load();

    /** Cached rank tables, keyed by radiusCells (the only geometry that matters). */
    private static final Map<Integer, RankTable> RANKS = new ConcurrentHashMap<>();

    private StarSystemNamePool() {}

    /** Number of loaded names (10 000 when the bundled resource is present). */
    public static int size() { return NAMES.length; }

    /** Whether the canonical index corresponds to a populated grid cell of the galaxy. */
    public static boolean isPopulated(double galaxyRadius, double starDensity, int systemIndex) {
        return rankOf(galaxyRadius, starDensity, systemIndex) >= 0;
    }

    /**
     * The stable UNIQUE display name of the star system with the given canonical index.
     *
     * @param galaxyRadius galaxy radius in GU ({@link GalaxyParameters#radius()})
     * @param starDensity  stars per square GU ({@link GalaxyParameters#starDensity()})
     * @param worldSeed    authoritative world seed (scrambles WHICH name a rank gets)
     * @param systemIndex  canonical system index ({@code StarSystemId.index()})
     */
    public static String forSystem(double galaxyRadius, double starDensity,
                                   long worldSeed, int systemIndex) {
        int rank = rankOf(galaxyRadius, starDensity, systemIndex);
        if (rank < 0) {
            // unknown / out-of-disc index: deterministic arbitrary fallback
            return NAMES[(int) Long.remainderUnsigned(mix(worldSeed ^ systemIndex), NAMES.length)];
        }
        return NAMES[permute(rank, worldSeed)];
    }

    /**
     * Seeded affine permutation modulo the pool size: {@code slot = (A*rank + B) mod N}
     * with gcd(A, N) == 1 - a bijection on [0, N), so distinct ranks never collide
     * while ranks stay below N.
     */
    private static int permute(int rank, long seed) {
        int n = NAMES.length;
        long h = mix(seed);
        long a = 1L + 2L * (h & 0x7FFFFFFFL);            // odd
        while (a % 5L == 0L) a += 2L;                     // now coprime with 2^4*5^4
        long b = h >>> 32;
        return (int) (((a * (rank & 0xFFFFFFFFL)) + b) % n);
    }

    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return x;
    }

    /**
     * Rank of the system's grid cell among ALL populated cells in ascending
     * Cantor-index order (-1 when the index is not a populated cell). Mirrors the
     * geometry of the package-private {@code SpatialGrid} exactly.
     */
    private static int rankOf(double galaxyRadius, double starDensity, int systemIndex) {
        double cellSize = Math.sqrt(Math.PI / starDensity);
        int radiusCells = Math.max(1, (int) Math.ceil(galaxyRadius / cellSize));
        RankTable t = RANKS.computeIfAbsent(radiusCells, r -> new RankTable(r));
        return t.rank(systemIndex);
    }

    /** One-time ordered enumeration of all populated cells for one galaxy size. */
    private static final class RankTable {
        private final int[] sortedCantor;
        private final int maxCantorExclusive;

        RankTable(int radiusCells) {
            int zMax = 2 * radiusCells + 1;               // zigzag range for |cell| <= radiusCells
            List<Integer> idx = new ArrayList<>();
            int top = 0;
            for (int a = 0; a <= zMax; a++) {
                for (int b = 0; b <= zMax; b++) {
                    long s = a + b;
                    long cantor = (s * (s + 1)) / 2 + b;
                    if (inDisc(unzag(a), unzag(b), radiusCells)) {
                        idx.add((int) cantor);
                    }
                    top = Math.max(top, (int) cantor);
                }
            }
            int[] arr = new int[idx.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = idx.get(i);
            Arrays.sort(arr);
            this.sortedCantor = arr;
            this.maxCantorExclusive = top + 1;
        }

        int rank(int systemIndex) {
            if (systemIndex < 0 || systemIndex >= maxCantorExclusive) return -1;
            int pos = Arrays.binarySearch(sortedCantor, systemIndex);
            return pos >= 0 ? pos : -1;
        }

        private static boolean inDisc(int cx, int cz, int radiusCells) {
            long r = radiusCells;
            return (long) cx * cx + (long) cz * cz <= r * r;
        }

        private static int unzag(long z) {
            return z % 2 == 0 ? (int) (z / 2) : (int) (-(z + 1) / 2);
        }
    }

    private static String[] load() {
        List<String> out = new ArrayList<>(10240);
        try (InputStream in = StarSystemNamePool.class.getResourceAsStream(RESOURCE)) {
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
        if (out.isEmpty()) return new String[]{"System"};
        return out.toArray(new String[0]);
    }
}
