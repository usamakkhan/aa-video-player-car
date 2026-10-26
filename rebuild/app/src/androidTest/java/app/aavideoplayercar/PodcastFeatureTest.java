package app.aavideoplayercar;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import app.aavideoplayercar.podcast.FeedParser;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PodcastFeatureTest {
    private static final String SOURCE = "https://example.com/feed.xml";
    private FeedParser.Result parse(String xml) throws Exception { return FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8), SOURCE, SOURCE); }
    private String rss(String items) { return "<rss version='2.0'><channel><title>My podcast</title>" + items + "</channel></rss>"; }
    private String episode(String guid, String title, String url) { return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate><enclosure type='audio/mpeg' url='" + url + "'/></item>"; }
    private android.view.View findText(android.view.View view, String value) {
        if (view instanceof android.widget.TextView && value.contentEquals(((android.widget.TextView)view).getText())) return view;
        if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup)view).getChildCount(); i++) {
            android.view.View found = findText(((android.view.ViewGroup)view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private void await(androidx.test.core.app.ActivityScenario<MainActivity> scenario, java.util.function.Predicate<MainActivity> predicate) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            scenario.onActivity(a -> ready.set(predicate.test(a))); if (ready.get()) return; Thread.sleep(50);
        }
        fail("Podcast screen did not become ready");
    }
    private android.widget.ListView list(MainActivity activity) { return activity.getWindow().getDecorView().findViewWithTag("podcast.list"); }
    @Test public void rssParsesEnclosuresStableIdsDatesAndEscapedTitles() throws Exception {
        var first = parse(rss(episode("guid-1", "One &amp; two", "audio.mp3") + episode("guid-1", "Duplicate", "duplicate.mp3")));
        assertEquals("My podcast", first.feed.title); assertEquals(1, first.episodes.size());
        var item = first.episodes.get(0); assertEquals("One & two", item.title); assertEquals("https://example.com/audio.mp3", item.url);
        assertTrue(item.publishedAt > 0);
        var refresh = parse(rss(episode("guid-1", "Renamed", "new.mp3")));
        assertEquals(item.id, refresh.episodes.get(0).id);
        assertEquals(first.feed.id, refresh.feed.id);
    }
    @Test public void atomNamespacesXmlBaseAndInvalidEnclosuresAreHandled() throws Exception {
        String xml = "<feed xmlns='http://www.w3.org/2005/Atom' xml:base='https://example.com/audio/'><title>Atom show</title>"
            + "<entry><id>tag:example,1</id><title type='xhtml'><div xmlns='http://www.w3.org/1999/xhtml'>Nested title</div></title><published>2026-10-12T08:00:00Z</published><link rel='enclosure' type='audio/ogg' href='one.ogg'/></entry>"
            + "<entry><id>bad</id><link rel='enclosure' href='file:///private/file'/></entry>"
            + "<entry><id>video</id><link rel='enclosure' type='video/mp4' href='video.mp4'/></entry></feed>";
        var result = parse(xml); assertEquals("Atom show", result.feed.title); assertEquals(1, result.episodes.size());
        assertEquals("Nested title", result.episodes.get(0).title); assertEquals("https://example.com/audio/one.ogg", result.episodes.get(0).url);
    }
    @Test public void malformedXmlExternalEntitiesAndOversizedDocumentsAreRejected() {
        assertThrows(IOException.class, () -> parse("<html><title>Not a feed</title></html>"));
        assertThrows(IOException.class, () -> parse("<rss><channel>"));
        assertThrows(IOException.class, () -> parse("<!DOCTYPE rss [<!ENTITY steal SYSTEM 'file:///private/data'>]><rss><channel><title>&steal;</title></channel></rss>"));
        assertThrows(IOException.class, () -> FeedParser.parse(new byte[HttpDocument.MAX_BYTES + 1], SOURCE, SOURCE));
        assertThrows(IOException.class, () -> HttpDocument.url("https://user:password@example.com/feed"));
        assertThrows(IOException.class, () -> HttpDocument.url("file:///private/feed"));
    }
    @Test public void refreshPreservesProgressAndArchivesUnlistedEpisodesAndUnsubscribeDoesNotResurrect() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "podcast-test-" + UUID.randomUUID() + ".db";
        var original = parse(rss(episode("one", "One", "one.mp3") + episode("two", "Two", "two.mp3")));
        String id = original.episodes.get(0).id;
        try {
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).build();
            try {
                assertTrue(db.podcasts().importFeed(original.feed, original.episodes, true));
                db.podcasts().progress(id, 9000, 30000, 1234);
                var update = parse(rss(episode("one", "Updated", "new.mp3")));
                assertTrue(db.podcasts().importFeed(update.feed, update.episodes, false));
                assertEquals(2, db.podcasts().episodeCount(original.feed.id));
                assertEquals(9000, db.podcasts().episode(id).positionMs);
                assertEquals("Updated", db.podcasts().episode(id).title);
            } finally { db.close(); }
            AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, name).build();
            try {
                assertEquals(9000, reopened.podcasts().episode(id).positionMs);
                reopened.podcasts().unsubscribe(original.feed.id);
                assertNull(reopened.podcasts().episode(id));
                assertFalse(reopened.podcasts().importFeed(original.feed, original.episodes, false));
                assertEquals(0, reopened.podcasts().feedCount());
            } finally { reopened.close(); }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void v3MigrationPreservesRadioAndMediaWhileAddingPodcasts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "podcast-upgrade-" + UUID.randomUUID() + ".db";
        org.json.JSONObject schema;
        try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("app.aavideoplayercar.data.AppDatabase/3.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            schema = new org.json.JSONObject(bytes.toString("UTF-8")).getJSONObject("database");
        }
        try {
            try (android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null)) {
                var entities = schema.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    var entity = entities.getJSONObject(i); old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                    var indices = entity.optJSONArray("indices");
                    for (int j = 0; indices != null && j < indices.length(); j++) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                }
                var setup = schema.getJSONArray("setupQueries"); for (int i = 0; i < setup.length(); i++) old.execSQL(setup.getString(i));
                old.execSQL("INSERT INTO media VALUES ('kept', 'content://fixture/audio', 'Existing track', 'audio/wav', 1, 9000, 30000, 2)");
                old.execSQL("INSERT INTO radio_stations VALUES ('radio-kept','Saved station','https://example.com/live','US','MP3',0,1,1)");
                old.setVersion(3);
            }
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
            try {
                assertEquals(9000, db.media().get("kept").positionMs); assertTrue(db.radio().get("radio-kept").favorite);
                var feed = parse(rss(episode("new", "New episode", "new.mp3"))); db.podcasts().importFeed(feed.feed, feed.episodes, true);
                assertEquals(1, db.podcasts().episodeCount(feed.feed.id));
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void httpFetchHandlesRedirectsGzipFailureCancellationAndSizeLimits() throws Exception {
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            byte[] feed = rss(episode("one", "Fetched", "audio.mp3")).getBytes(StandardCharsets.UTF_8);
            server.redirect("/start", "/feed.xml"); server.gzip("/feed.xml", feed, "application/rss+xml");
            var document = new HttpDocument().get(server.url("/start"));
            assertArrayEquals(feed, document.bytes); assertEquals(server.url("/feed.xml"), document.url);
            assertEquals(1, FeedParser.parse(document.bytes, server.url("/start"), document.url).episodes.size());
            assertThrows(IOException.class, () -> new HttpDocument().get(server.url("/missing")));
            server.redirect("/loop", "/loop"); assertThrows(IOException.class, () -> new HttpDocument().get(server.url("/loop")));
            server.redirect("/unsafe", "file:///private/feed"); assertThrows(IOException.class, () -> new HttpDocument().get(server.url("/unsafe")));
            HttpDocument cancelled = new HttpDocument(); cancelled.cancel(); assertThrows(IOException.class, () -> cancelled.get(server.url("/feed.xml")));
            server.gzip("/large", new byte[HttpDocument.MAX_BYTES + 1], "application/xml");
            assertThrows(IOException.class, () -> new HttpDocument().get(server.url("/large")));
        }
    }
    @Test public void phoneSubscribesPagesRestoresAndRefreshesWithoutDuplicatingEpisodes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        var repository = MediaRepository.get(context); String feedId = null;
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < 51; i++) items.append(episode("episode-" + i, "Episode " + i, "/" + i + ".mp3"));
            byte[] xml = rss(items.toString()).getBytes(StandardCharsets.UTF_8);
            String source = server.url("/feed.xml"); server.add("/feed.xml", xml, "application/rss+xml");
            feedId = FeedParser.parse(xml, source, source).feed.id;
            try (var scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> findText(a.getWindow().getDecorView(), "Podcast").performClick());
                await(scenario, a -> findText(a.getWindow().getDecorView(), "Subscribe").isEnabled());
                scenario.onActivity(a -> {
                    android.widget.EditText field = a.getWindow().getDecorView().findViewWithTag("podcast.url");
                    field.setText("file:///bad-feed"); findText(a.getWindow().getDecorView(), "Subscribe").performClick();
                    assertNotNull(findText(a.getWindow().getDecorView(), a.getString(R.string.podcast_invalid_url)));
                    field.setText(source); findText(a.getWindow().getDecorView(), "Subscribe").performClick();
                });
                await(scenario, a -> list(a).getAdapter() != null && list(a).getCount() == 50);
                assertEquals(51, repository.database.podcasts().episodeCount(feedId));
                scenario.onActivity(a -> findText(a.getWindow().getDecorView(), "Next").performClick());
                await(scenario, a -> list(a).getAdapter() != null && list(a).getCount() == 1);
                scenario.recreate();
                await(scenario, a -> list(a).getAdapter() != null && list(a).getCount() == 1);
                scenario.onActivity(a -> {
                    assertEquals(source, ((android.widget.EditText)a.getWindow().getDecorView().findViewWithTag("podcast.url")).getText().toString());
                    a.onBackPressed();
                });
                await(scenario, a -> list(a).getAdapter() != null && list(a).getCount() > 0 && list(a).getAdapter().getItem(0) instanceof PodcastFeed);
                scenario.onActivity(a -> {
                    int index = -1;
                    for (int i = 0; i < list(a).getCount(); i++) if (((PodcastFeed)list(a).getAdapter().getItem(i)).url.equals(source)) index = i;
                    assertTrue(index >= 0); list(a).performItemClick(null, index, index);
                });
                await(scenario, a -> findText(a.getWindow().getDecorView(), "Refresh feed").isEnabled());
                server.add("/feed.xml", rss(episode("episode-0", "Renamed episode", "/new.mp3")).getBytes(StandardCharsets.UTF_8), "application/rss+xml");
                scenario.onActivity(a -> findText(a.getWindow().getDecorView(), "Refresh feed").performClick());
                await(scenario, a -> findText(a.getWindow().getDecorView(), "Refresh feed").isEnabled());
                assertEquals(51, repository.database.podcasts().episodeCount(feedId));
                assertEquals(1, repository.database.podcasts().search("Renamed episode", 50, 0).size());
            }
        } finally { if (feedId != null) repository.database.podcasts().unsubscribe(feedId); }
    }
}
