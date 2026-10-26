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
public class IptvSeriesDatabaseTest {
    private IptvSeries show(String id, String playlist) { var s = new IptvSeries(); s.id = id; s.playlistId = playlist; s.providerId = id; s.title = "Show " + id; return s; }
    private IptvChannel channel(String id, String playlist) { var c = new IptvChannel(); c.id = id; c.playlistId = playlist; c.title = id; c.live = false; c.url = "https://example.com/" + id; return c; }
    private IptvSeriesEpisode member(String id, String show, int number) { var m = new IptvSeriesEpisode(); m.channelId = id; m.seriesId = show; m.season = 1; m.number = number; return m; }
    @Test public void refreshPreservesProgressSeparatesEpisodesAndCascadesRemoval() {
        var db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();
        try {
            var dao = db.iptv(); var p = new IptvPlaylist(); p.id = "p"; p.title = "Account";
            dao.replace(p, List.of(channel("movie", "p")), true);
            dao.replaceSeries("p", List.of(show("a", "p"), show("b", "p")));
            var rows = new ArrayList<IptvChannel>(); var members = new ArrayList<IptvSeriesEpisode>();
            for (int i = 0; i < 51; i++) { rows.add(channel("ep" + i, "p")); members.add(member("ep" + i, "a", i)); }
            assertTrue(dao.replaceEpisodes("a", rows, members, 1));
            assertEquals(1, dao.count("p", "", false)); assertEquals(51, dao.episodeCount("a", 1));
            assertEquals("ep50", dao.episodePage("a", 1, 50, 50).get(0).id);
            assertEquals(List.of(1), dao.seasons("a"));
            dao.toggleFavorite("ep0"); dao.progress("ep0", 7000, 30000, 2);
            dao.replace(p, List.of(channel("movie2", "p")), false);
            assertNull(dao.channel("movie")); assertEquals(7000, dao.channel("ep0").positionMs);
            dao.replaceEpisodes("a", List.of(channel("ep0", "p")), List.of(member("ep0", "a", 2)), 3);
            assertNull(dao.channel("ep50")); assertTrue(dao.channel("ep0").favorite); assertEquals(7000, dao.channel("ep0").positionMs);
            dao.replaceSeries("p", List.of(show("a", "p"))); assertEquals(3, dao.series("a").updatedAt); assertNull(dao.series("b"));
            dao.replaceSeries("p", List.of(show("b", "p"))); assertNull(dao.channel("ep0")); assertNotNull(dao.channel("movie2"));
            dao.replaceEpisodes("b", List.of(channel("last", "p")), List.of(member("last", "b", 1)), 4);
            dao.delete("p"); assertNull(dao.channel("last")); assertNull(dao.series("b")); assertEquals(0, dao.episodeCount("b", 1));
            assertFalse(dao.replaceSeries("p", List.of(show("a", "p"))));
            assertFalse(dao.replaceEpisodes("b", List.of(), List.of(), 5));
        } finally { db.close(); }
    }
    @Test public void invalidRefreshRollsBackAndCannotStealOtherChannels() {
        var db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();
        try {
            var dao = db.iptv(); var p = new IptvPlaylist(); p.id = "p";
            dao.replace(p, List.of(channel("movie", "p")), true); dao.replaceSeries("p", List.of(show("a", "p"), show("b", "p")));
            dao.replaceEpisodes("a", List.of(channel("kept", "p")), List.of(member("kept", "a", 1)), 7);
            assertThrows(IllegalArgumentException.class, () -> dao.replaceEpisodes("b", List.of(channel("new", "p"), channel("kept", "p")), List.of(member("new", "b", 1), member("kept", "b", 2)), 9));
            assertNull(dao.channel("new")); assertEquals(1, dao.episodeCount("a", 1)); assertEquals(0, dao.series("b").updatedAt);
            assertThrows(IllegalArgumentException.class, () -> dao.replaceEpisodes("a", List.of(channel("movie", "p")), List.of(member("movie", "a", 1)), 10));
            assertEquals(7, dao.series("a").updatedAt); assertNotNull(dao.channel("movie"));
            assertThrows(IllegalArgumentException.class, () -> dao.replaceSeries("p", List.of(show("new", "p"), show("bad", "other"))));
            assertNull(dao.series("new")); assertEquals(2, dao.seriesCount("p", ""));
            assertThrows(RuntimeException.class, () -> dao.replace(p, List.of(channel("kept", "p")), false));
            assertNotNull(dao.channel("movie")); assertEquals(1, dao.episodeCount("a", 1));
        } finally { db.close(); }
    }
}
