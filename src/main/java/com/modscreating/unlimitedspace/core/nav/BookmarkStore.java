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

    /** One saved bookmark / recent entry: display name + system index. */
    public record Entry(String name, int systemIndex) {}

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
        if (systemIndex < 0) return false;
        String n = (name == null || name.isBlank()) ? defaultName(systemIndex) : name.trim();
        for (Entry e : bookmarks) {
            if (e.systemIndex() == systemIndex) {
                return false;
            }
        }
        bookmarks.add(new Entry(n, systemIndex));
        return true;
    }

    public boolean removeBookmark(int systemIndex) {
        return bookmarks.removeIf(e -> e.systemIndex() == systemIndex);
    }

    public boolean isBookmarked(int systemIndex) {
        return bookmarks.stream().anyMatch(e -> e.systemIndex() == systemIndex);
    }

    /** Record a visited/launched-to system at the front of the recents (deduplicated). */
    public void addRecent(int systemIndex) {
        if (systemIndex < 0) return;
        recent.removeIf(e -> e.systemIndex() == systemIndex);
        recent.add(0, new Entry(defaultName(systemIndex), systemIndex));
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
    }

    public void clearRecent() {
        recent.clear();
    }

    // ---- simple line serialization: "b|name|index" and "r|name|index" ----

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : bookmarks) {
            sb.append('b').append('|').append(escape(e.name())).append('|').append(e.systemIndex()).append('\n');
        }
        for (Entry e : recent) {
            sb.append('r').append('|').append(escape(e.name())).append('|').append(e.systemIndex()).append('\n');
        }
        return sb.toString();
    }

    public static BookmarkStore deserialize(String data) {
        BookmarkStore store = new BookmarkStore();
        if (data == null || data.isBlank()) return store;
        for (String line : data.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                int idx = Integer.parseInt(parts[2].trim());
                if ("b".equals(parts[0])) store.addBookmark(unescape(parts[1]), idx);
                else if ("r".equals(parts[0])) store.recent.add(new Entry(unescape(parts[1]), idx));
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
