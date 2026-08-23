package com.modscreating.unlimitedspace.core.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R15: bookmark/recent store — add, remove, dedupe and save/load round-trip. */
class BookmarkStoreTest {

    @Test
    void addAndRemoveBookmarks() {
        BookmarkStore s = new BookmarkStore();
        assertTrue(s.addBookmark("Home", 4123));
        assertFalse(s.addBookmark("Dup", 4123), "duplicate system must be rejected");
        assertTrue(s.isBookmarked(4123));
        assertTrue(s.removeBookmark(4123));
        assertFalse(s.isBookmarked(4123));
        assertFalse(s.removeBookmark(999));
        assertFalse(s.addBookmark("Bad", -1));
    }

    @Test
    void blankNamesGetDefaultName() {
        BookmarkStore s = new BookmarkStore();
        s.addBookmark("   ", 7);
        assertEquals("System 7", s.bookmarks().get(0).name());
    }

    @Test
    void recentsAreDeduplicatedNewestFirstAndCapped() {
        BookmarkStore s = new BookmarkStore();
        for (int i = 0; i < 30; i++) s.addRecent(i);
        assertEquals(BookmarkStore.MAX_RECENT, s.recent().size());
        assertEquals(29, s.recent().get(0).systemIndex());
        s.addRecent(20);
        assertEquals(20, s.recent().get(0).systemIndex());
        assertEquals(1, s.recent().stream().filter(e -> e.systemIndex() == 20).count());
        s.clearRecent();
        assertTrue(s.recent().isEmpty());
    }

    @Test
    void serializeDeserializeRoundTrip() {
        BookmarkStore s = new BookmarkStore();
        s.addBookmark("Home", 4123);
        s.addBookmark("Ice | World", 12);
        s.addRecent(958);
        String data = s.serialize();

        BookmarkStore loaded = BookmarkStore.deserialize(data);
        assertEquals(2, loaded.bookmarks().size());
        assertEquals(4123, loaded.bookmarks().get(0).systemIndex());
        assertEquals("Home", loaded.bookmarks().get(0).name());
        assertEquals("Ice | World", loaded.bookmarks().get(1).name(), "pipe escaping");
        assertEquals(1, loaded.recent().size());
        assertEquals(958, loaded.recent().get(0).systemIndex());

        BookmarkStore empty = BookmarkStore.deserialize(null);
        assertEquals(0, empty.bookmarks().size() + empty.recent().size());
        assertTrue(BookmarkStore.deserialize("garbage\nb|x|notanumber").bookmarks().isEmpty());
    }
}
