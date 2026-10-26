package app.aavideoplayercar;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewsDatabaseTest {
    private NewsSource source(String id) { var s = new NewsSource(); s.id = id; s.title = "News " + id; s.url = "https://example.com/" + id; return s; }
    private NewsArticle article(String id, String source, long time) { var a = new NewsArticle(); a.id = id; a.sourceId = source; a.title = "Article " + id; a.body = "Body"; a.publishedAt = time; return a; }
    @Test public void snapshotRefreshPreservesReadStatePagesAndPrunesRemovedArticles() {
        var db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();
        try {
            var dao = db.news(); var rows = new ArrayList<NewsArticle>(); for (int i = 0; i < 51; i++) rows.add(article("a" + i, "s", i));
            assertTrue(dao.replace(source("s"), rows, true)); assertEquals(51, dao.count("s", "", false));
            assertEquals("a0", dao.articles("s", "", false, 50, 50).get(0).id);
            dao.setRead("a50", true); assertEquals(50, dao.count("s", "", true)); assertEquals(1, dao.count("s", "a50", false));
            var retained = article("a50", "s", 60); retained.title = "Updated headline";
            dao.replace(source("s"), List.of(retained, article("new", "s", 61)), false);
            assertTrue(dao.article("a50").read); assertEquals("Updated headline", dao.article("a50").title); assertNull(dao.article("a0"));
            assertEquals(1, dao.count("s", "", true)); dao.setRead("a50", false); assertEquals(2, dao.count("s", "", true));
            dao.delete("s"); assertNull(dao.article("a50")); assertEquals(0, dao.sourceCount()); assertFalse(dao.replace(source("s"), rows, false));
        } finally { db.close(); }
    }
    @Test public void invalidRefreshRollsBackAndCrossSourceIdsCannotOverwriteData() {
        var db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();
        try {
            var dao = db.news(); dao.replace(source("s"), List.of(article("kept", "s", 1)), true); dao.setRead("kept", true);
            assertThrows(IllegalArgumentException.class, () -> dao.replace(source("s"), List.of(article("new", "s", 2), article("wrong", "other", 3)), false));
            assertNotNull(dao.article("kept")); assertTrue(dao.article("kept").read); assertNull(dao.article("new"));
            assertThrows(RuntimeException.class, () -> dao.replace(source("other"), List.of(article("kept", "other", 1)), true));
            assertNull(dao.source("other")); assertEquals("s", dao.article("kept").sourceId);
            assertThrows(IllegalArgumentException.class, () -> dao.replace(source("s"), List.of(article("dup", "s", 1), article("dup", "s", 2)), false));
            assertEquals(1, dao.count("s", "", false));
        } finally { db.close(); }
    }
    @Test public void cacheLimitsIncludeUtf8AcrossSourcesAndDoNotDestroyOldSnapshots() {
        var db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();
        try {
            var dao = db.news(); var large = new ArrayList<NewsArticle>();
            for (int i = 0; i < 30; i++) { var a = article("large" + i, "large", i); a.body = "\u20ac".repeat(60000); large.add(a); }
            dao.replace(source("large"), large, true);
            var overflow = new ArrayList<NewsArticle>(); for (int i = 0; i < 20; i++) { var a = article("over" + i, "over", i); a.body = "\u20ac".repeat(60000); overflow.add(a); }
            assertThrows(IllegalArgumentException.class, () -> dao.replace(source("over"), overflow, true));
            assertNull(dao.source("over")); assertEquals(30, dao.count("large", "", false));
            dao.replace(source("large"), large, false); // Replacement excludes the previous snapshot from the budget.
            dao.delete("large");
            for (int i = 0; i < NewsDao.MAX_SOURCES; i++) dao.replace(source("s" + i), List.of(), true);
            assertThrows(IllegalArgumentException.class, () -> dao.replace(source("extra"), List.of(), true));
            dao.replace(source("s0"), List.of(article("allowed", "s0", 0)), false);
            var tooMany = new ArrayList<NewsArticle>(); for (int i = 0; i <= NewsDao.MAX_ARTICLES; i++) tooMany.add(article("x" + i, "s0", i));
            assertThrows(IllegalArgumentException.class, () -> dao.replace(source("s0"), tooMany, false));
            assertNotNull(dao.article("allowed"));
        } finally { db.close(); }
    }
}
