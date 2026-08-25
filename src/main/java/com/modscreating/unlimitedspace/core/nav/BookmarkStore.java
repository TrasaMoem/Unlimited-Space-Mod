package com.modscreating.unlimitedspace.core.nav;

import java.util.ArrayList;
import java.util.List;

/**
 * R15: pure player navigation memory — bookmarks and recent destinations.
 * Deliberately Minecraft-free so it can be unit-tested and serialized by the
 * client persistence layer. Only small identity data is kept here (never
 * renderer state).
 */
public final class BookmarkStore {

    /**
     * One saved bookmark / recent entry: name, system index, visit timestamp and,
     * for bookmarks, WHAT exactly is saved:
     * kind "S" = whole system, "O" = one object (star/planet/asteroid field),
     * "L" = an exact location (surface / orbit / satellite surface-orbit).
     */
    public record Entry(String name, int systemIndex, long visitedAtMs,
                        String kind, int objectId, int destId) {
        public boolean isLocation() { return "L".equals(kind); }
    }

    /** Kind code of an entry ("S" system / "O" object / "L" location). */
    public static String kindOf(Entry e) {
        return e.kind() == null || e.kind().isBlank() ? "S" : e.kind();
    }

    public static final int MAX_RECENT = 16;

    private final List<Entry> bookmarks = new ArrayList<>();
    private final List<Entry> recent = new ArrayList<>();

    public List<Entry> bookmarks() {
        return List.copyOf(bookmarks);
    }

    public List<Entry> recent() {
        return List.copyOf(recent);
    }

    public boolean addBookmark(String name, int systemIndex) {
        return addEntry("S", name, systemIndex, -1, -1);
    }

    /** Bookmark a single object (star / planet / asteroid field) of a system. */
    public boolean addObjectBookmark(String name, int systemIndex, int objectIndex) {
        return addEntry("O", name, systemIndex, objectIndex, -1);
    }

    /** Bookmark an exact location: body surface / body orbit / satellite surface-orbit. */
    public boolean addLocationBookmark(String name, int systemIndex,
                                       int objectIndex, int destinationIndex) {
        return addEntry("L", name, systemIndex, objectIndex, destinationIndex);
    }

    /** Shared insert with dedupe per kind ("S" whole system / "O" object / "L" location). */
    public boolean addEntry(String kind, String name, int systemIndex,
                            int objectIndex, int destinationIndex) {
        if (systemIndex < -2 || systemIndex == -1) return false; // -2 = Sol anchor, -1 = none
        long now = System.currentTimeMillis();
        String n = (name == null || name.isBlank())
                ? defaultName(systemIndex) : name.trim();
        for (Entry e : bookmarks) {
            if (matches(e, kind, systemIndex, objectIndex, destinationIndex)) {
                return false; // already bookmarked
            }
        }
        bookmarks.add(new Entry(n, systemIndex, now, kind, objectIndex, destinationIndex));
        return true;
    }

    private static boolean matches(Entry e, String kind, int sys, int obj, int dst) {
        if (!kindOf(e).equals(kind) || e.systemIndex() != sys) return false;
        return switch (kind) {
            case "O" -> e.objectId() == obj;
            case "L" -> e.objectId() == obj && e.destId() == dst;
            default -> true;
        };
    }

    /** Removes the entry matching exactly this bookmark key (any kind). */
    public boolean removeBookmarkExact(String kind, int systemIndex,
                                       int objectIndex, int destinationIndex) {
        return bookmarks.removeIf(e ->
                matches(e, kind, systemIndex, objectIndex, destinationIndex));
    }

    public boolean removeBookmark(int systemIndex) {
        return bookmarks.removeIf(e -> e.systemIndex() == systemIndex);
    }

    public boolean isBookmarked(int systemIndex) {
        return bookmarks.stream().anyMatch(e -> e.systemIndex() == systemIndex);
    }

    /** Record a visited/launched-to system at the front of the recents (deduplicated). */
    public void addRecent(int systemIndex) {
        if (systemIndex < -2) return; // -2 = the Sol anchor is a legal recent too
        recent.removeIf(e -> e.systemIndex() == systemIndex);
        recent.add(0, new Entry(defaultName(systemIndex), systemIndex,
                System.currentTimeMillis(), "R", -1, -1));
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
    }

    public void clearRecent() {
        recent.clear();
    }

    // ---- line serialization: "b|name|sys|timeMs|kind|obj|dest" / "r|name|sys|timeMs" ----

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : bookmarks) {
            sb.append('b').append('|').append(escape(e.name())).append('|')
                    .append(e.systemIndex()).append('|').append(e.visitedAtMs()).append('|')
                    .append(kindOf(e)).append('|')
                    .append(e.objectId()).append('|').append(e.destId()).append('\n');
        }
        for (Entry e : recent) {
            sb.append('r').append('|').append(escape(e.name())).append('|')
                    .append(e.systemIndex()).append('|').append(e.visitedAtMs()).append('\n');
        }
        return sb.toString();
    }

    public static BookmarkStore deserialize(String data) {
        BookmarkStore store = new BookmarkStore();
        if (data == null || data.isBlank()) return store;
        for (String line : data.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) continue;
            try {
                int idx = Integer.parseInt(parts[2].trim());
                // R16: optional visit timestamp; old saves without it -> "now"
                long ts = parts.length >= 4 ? Long.parseLong(parts[3].trim())
                        : System.currentTimeMillis();
                // R16: bookmark entries carry kind + object + destination
                String kind = parts.length >= 7 ? parts[4].trim() : "S";
                int obj = parts.length >= 7 ? Integer.parseInt(parts[5].trim()) : -1;
                int dst = parts.length >= 7 ? Integer.parseInt(parts[6].trim()) : -1;
                if ("b".equals(parts[0])) {
                    store.bookmarks.add(new Entry(unescape(parts[1]), idx, ts, kind, obj, dst));
                } else if ("r".equals(parts[0])) {
                    store.recent.add(new Entry(unescape(parts[1]), idx, ts, "R", -1, -1));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed line
            }
        }
        return store;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ");
    }

    private static String unescape(String s) {
        return s.replace("\\p", "|").replace("\\\\", "\\");
    }

    public static String defaultName(int systemIndex) {
        return "System " + systemIndex;
    }
}
