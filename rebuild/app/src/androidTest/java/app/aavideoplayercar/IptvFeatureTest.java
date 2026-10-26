package app.aavideoplayercar;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.iptv.M3uParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class IptvFeatureTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String playlist(int count) {
        StringBuilder result = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < count; i++) result.append("#EXTINF:").append(i == 0 ? 30 : -1).append(" group-title=\"Group ").append(i % 2).append("\",Channel ").append(i).append("\nhttps://example.com/").append(i).append(".mp4\n");
        return result.toString();
    }
    private M3uParser.Result parse(String value) throws Exception { return M3uParser.parse(value.getBytes(StandardCharsets.UTF_8), "https://example.com/list", "https://example.com/list", "Fixture TV"); }
    @Test public void playbackOverridesSurviveRefreshAndCanReturnToPlaylistDefault() throws Exception {
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            var result = parse(playlist(2)); var dao = db.iptv(); dao.replace(result.playlist, result.channels, true);
            String id = result.channels.get(1).id;
            assertTrue(dao.channel(id).isLive()); dao.playbackMode(id, 2); assertFalse(dao.channel(id).isLive());
            dao.progress(id, 9000, 30000, 1); dao.replace(parse(playlist(2)).playlist, parse(playlist(2)).channels, false);
            assertEquals(2, dao.channel(id).playbackMode); assertEquals(9000, dao.channel(id).positionMs);
            dao.playbackMode(id, 0); assertTrue(dao.channel(id).isLive());
            String movie = result.channels.get(0).id; dao.playbackMode(movie, 1); assertTrue(dao.channel(movie).isLive());
            dao.playbackMode(movie, 0); assertFalse(dao.channel(movie).isLive());
            assertThrows(IllegalArgumentException.class, () -> dao.playbackMode(id, 3));
        } finally { db.close(); }
    }
    @Test public void largePlaylistRefreshPreservesFavoriteAndProgressAndPrunesRemovedChannels() throws Exception {
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            long start = android.os.SystemClock.elapsedRealtime(); var result = parse(playlist(10000));
            db.iptv().replace(result.playlist, result.channels, true);
            long imported = android.os.SystemClock.elapsedRealtime();
            assertEquals(10000, db.iptv().count(result.playlist.id, "", false));
            assertEquals(50, db.iptv().channels(result.playlist.id, "", false, 50, 9950).size());
            android.util.Log.i("IptvBenchmark", "10000_import_ms=" + (imported - start) + " last_page_ms=" + (android.os.SystemClock.elapsedRealtime() - imported));
            String selected = result.channels.get(0).id;
            db.iptv().toggleFavorite(selected); db.iptv().progress(selected, 9000, 30000, 123);
            var updated = parse(playlist(51).replace("Channel 0", "Renamed channel"));
            assertTrue(db.iptv().replace(updated.playlist, updated.channels, false));
            assertEquals(51, db.iptv().count(result.playlist.id, "", false));
            assertEquals(1, db.iptv().count(result.playlist.id, "", true));
            assertEquals(9000, db.iptv().channel(selected).positionMs); assertEquals("Renamed channel", db.iptv().channel(selected).title);
            assertEquals(25, db.iptv().count(result.playlist.id, "Group 1", false));
            db.iptv().delete(result.playlist.id); assertNull(db.iptv().channel(selected));
            assertFalse(db.iptv().replace(updated.playlist, updated.channels, false));
        } finally { db.close(); }
    }
    @Test public void v4MigrationPreservesPodcastProgressAndAddsIptv() throws Exception { migration(4); }
    @Test public void v5MigrationPreservesChannelStateAndUsesPlaylistMode() throws Exception { migration(5); }
    @Test public void v6MigrationPreservesChannelsAndAddsGuide() throws Exception { migration(6); }
    @Test public void v7MigrationPreservesGuideAndAddsAccounts() throws Exception { migration(7); }
    @Test public void v8MigrationPreservesAccountAndAddsSeries() throws Exception { migration(8); }
    @Test public void v9MigrationPreservesSeriesAndAddsNews() throws Exception { migration(9); }
    @Test public void v10MigrationPreservesNewsAndAddsMovieAddons() throws Exception { migration(10); }
    @Test public void v11MigrationPreservesAddonsAndAddsMovieVideos() throws Exception { migration(11); }
    @Test public void v12MigrationPreservesMovieVideoAndAddsBoundedCache() throws Exception { migration(12); }
    @Test public void v13MigrationPreservesMovieCacheAndAddsRenewalIdentity() throws Exception { migration(13); }
    private void migration(int version) throws Exception {
        String name = "iptv-upgrade-" + UUID.randomUUID() + ".db";
        try {
            org.json.JSONObject schema;
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("app.aavideoplayercar.data.AppDatabase/" + version + ".json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                schema = new org.json.JSONObject(bytes.toString("UTF-8")).getJSONObject("database");
            }
            try (var old = context.openOrCreateDatabase(name, 0, null)) {
                var entities = schema.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    var entity = entities.getJSONObject(i); old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                    var indices = entity.optJSONArray("indices");
                    for (int j = 0; indices != null && j < indices.length(); j++) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                }
                var setup = schema.getJSONArray("setupQueries"); for (int i = 0; i < setup.length(); i++) old.execSQL(setup.getString(i));
                old.execSQL("INSERT INTO podcast_feeds VALUES ('kept','https://example.com/feed','Kept show',1)");
                old.execSQL("INSERT INTO podcast_episodes VALUES ('episode','kept','Kept episode','https://example.com/episode.mp3','audio/mpeg',1,9000,30000,2)");
                if (version >= 5) {
                    old.execSQL("INSERT INTO iptv_playlists VALUES ('old','Old TV','https://example.com/old',1)");
                    old.execSQL("INSERT INTO iptv_channels (id,playlistId,title,url,groupName,tvgId,live,favorite,positionMs,durationMs,lastPlayedAt) VALUES ('old-channel','old','Old movie','https://example.com/movie','Movies','',0,1,7000,30000,2)");
                }
                old.setVersion(version);
                if (version >= 7) {
                    old.execSQL("INSERT INTO epg_sources VALUES ('old','https://example.com/guide',1)");
                    old.execSQL("INSERT INTO epg_programmes VALUES ('old-programme','old','station','Kept programme','Description',1000,2000)");
                }
                if (version >= 8) old.execSQL("INSERT INTO xtream_accounts VALUES ('old','https://example.com','ciphertext-fixture')");
                if (version >= 9) {
                    old.execSQL("INSERT INTO iptv_series VALUES ('old-show','old','7','Kept series','Category',1)");
                    old.execSQL("INSERT INTO iptv_series_episodes VALUES ('old-channel','old-show',1,2)");
                }
                if (version >= 10) {
                    old.execSQL("INSERT INTO news_sources VALUES ('news','https://example.com/news','Kept news',1)");
                    old.execSQL("INSERT INTO news_articles VALUES ('article','news','Kept article','Body','https://example.com/article',1000,1)");
                }
                if (version >= 11) old.execSQL("INSERT INTO movie_addons VALUES ('kept-addon','encrypted-fixture','org.fixture','Kept add-on','1',1)");
                if (version >= 12) old.execSQL("INSERT INTO movie_videos VALUES ('kept-video','kept-addon','Kept video','encrypted-source',9000,30000,2)");
                if (version >= 13) old.execSQL("INSERT INTO movie_cache VALUES ('" + "a".repeat(64) + "','kept-addon','saved-result',12,1)");
            }
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
            try {
                assertEquals(9000, db.podcasts().episode("episode").positionMs);
                assertEquals(version >= 7 ? 1 : 0, db.epg().count("old", "station"));
                if (version >= 8) assertEquals("ciphertext-fixture", db.xtream().account("old").credentials); else assertNull(db.xtream().account("old"));
                assertEquals(version >= 9 ? 1 : 0, db.iptv().seriesCount("old", ""));
                if (version >= 9) { assertEquals(1, db.iptv().episodeCount("old-show", 1)); assertEquals("old-channel", db.iptv().episodePage("old-show", 1, 50, 0).get(0).id); }
                assertEquals(version >= 10 ? 1 : 0, db.news().sourceCount());
                if (version >= 10) { assertTrue(db.news().article("article").read); assertEquals("Body", db.news().article("article").body); }
                assertEquals(version >= 11 ? 1 : 0, db.movieAddons().count());
                if (version >= 11) assertEquals("encrypted-fixture", db.movieAddons().get("kept-addon").endpoint);
                assertEquals(version >= 12 ? 1 : 0, db.movieVideos().count());
                if (version >= 12) { var video = db.movieVideos().get("kept-video"); assertEquals(9000, video.positionMs); assertEquals("", video.providerVideoId); assertEquals(-1, video.streamIndex); }
                if (version >= 13) assertEquals("saved-result", db.movieCache().get("a".repeat(64), "kept-addon").payload);
                if (version >= 5) {
                    var old = db.iptv().channel("old-channel"); assertEquals(0, old.playbackMode); assertFalse(old.isLive());
                    assertTrue(old.favorite); assertEquals(7000, old.positionMs); db.iptv().delete("old");
                }
                var result = parse(playlist(1)); assertTrue(db.iptv().replace(result.playlist, result.channels, true)); assertEquals(1, db.iptv().playlistCount());
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }
    private View text(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View found = text(((ViewGroup)root).getChildAt(i), text); if (found != null) return found; }
        return null;
    }
    private void await(ActivityScenario<MainActivity> activity, java.util.function.Predicate<MainActivity> ready) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < end) {
            java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(); activity.onActivity(a -> result.set(ready.test(a)));
            if (result.get()) return; Thread.sleep(50);
        } fail("IPTV screen timeout");
    }
    private ListView list(MainActivity activity) { return activity.getWindow().getDecorView().findViewWithTag("iptv.list"); }
    private androidx.media3.ui.PlayerView player(MainActivity activity) { return activity.getWindow().getDecorView().findViewWithTag("iptv.player"); }
    private boolean barsVisible(MainActivity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            var insets = activity.getWindow().getDecorView().getRootWindowInsets();
            return insets != null && insets.isVisible(android.view.WindowInsets.Type.statusBars());
        }
        return (activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
    }
    @Test public void fullscreenKeepsPlaybackAcrossRotationAndBackRestoresPlaylist() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        String id = null;
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            byte[] clip;
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) {
                var output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); clip = output.toByteArray();
            }
            server.gzip("/clip.mp4", clip, "video/mp4");
            byte[] bytes = ("#EXTM3U\n#EXTINF:12,Fullscreen movie\n" + server.url("/clip.mp4") + "\n").getBytes(StandardCharsets.UTF_8);
            server.gzip("/list", bytes, "audio/x-mpegurl");
            var parsed = M3uParser.parse(bytes, server.url("/list"), server.url("/list"), "Fullscreen test"); id = parsed.playlist.id;
            MediaRepository.get(context).database.iptv().replace(parsed.playlist, parsed.channels, true);
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick());
                await(activity, a -> list(a).getCount() > 0);
                activity.onActivity(a -> {
                    for (int i = 0; i < list(a).getCount(); i++) if (((IptvPlaylist)list(a).getAdapter().getItem(i)).id.equals(parsed.playlist.id)) {
                        list(a).performItemClick(null, i, i); break;
                    }
                });
                await(activity, a -> list(a).getCount() == 1 && list(a).getAdapter().getItem(0) instanceof IptvChannel && player(a).getPlayer() != null);
                activity.onActivity(a -> list(a).performItemClick(null, 0, 0));
                await(activity, a -> player(a).getPlayer().isPlaying() && player(a).getPlayer().getVideoSize().width == 160);
                activity.onActivity(a -> {
                    player(a).getPlayer().setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
                    a.getWindow().getDecorView().findViewWithTag("iptv.fullscreen").performClick();
                });
                await(activity, a -> !barsVisible(a) && !list(a).isShown() && !text(a.getWindow().getDecorView(), "Home").isShown()
                    && player(a).getHeight() > 300 * context.getResources().getDisplayMetrics().density && player(a).getPlayer().isPlaying());
                activity.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                await(activity, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    && player(a) != null && player(a).getPlayer() != null && player(a).getPlayer().isPlaying() && !list(a).isShown());
                activity.recreate();
                await(activity, a -> player(a).getPlayer() != null && player(a).getPlayer().isPlaying() && !list(a).isShown());
                activity.onActivity(a -> a.onBackPressed());
                await(activity, a -> barsVisible(a) && list(a).isShown() && text(a.getWindow().getDecorView(), "Home").isShown());
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("iptv.fullscreen").performClick());
                await(activity, a -> !barsVisible(a) && !list(a).isShown());
                activity.onActivity(a -> a.getWindow().getDecorView().findViewWithTag("iptv.fullscreen").performClick());
                await(activity, a -> barsVisible(a) && list(a).isShown());
                activity.onActivity(a -> {
                    assertEquals("iptv:" + parsed.channels.get(0).id, player(a).getPlayer().getCurrentMediaItem().mediaId);
                    assertTrue(player(a).getPlayer().isPlaying());
                    player(a).getPlayer().setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF);
                    player(a).getPlayer().pause(); player(a).getPlayer().clearMediaItems();
                    a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                });
                await(activity, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
            }
        } finally { if (id != null) MediaRepository.get(context).database.iptv().delete(id); }
    }
    private android.net.Uri document(byte[] bytes) throws Exception {
        var values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "aa-playlist-test-" + UUID.randomUUID() + ".m3u");
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl");
        var uri = context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        try (var output = context.getContentResolver().openOutputStream(uri, "wt")) { output.write(bytes); }
        return uri;
    }
    @Test public void documentReaderBoundsCancellationAndMissingFile() throws Exception {
        var resolver = context.getContentResolver();
        var uri = document(new byte[4 * 1024 * 1024 + 1]);
        try {
            assertThrows(java.io.IOException.class, () -> new app.aavideoplayercar.iptv.PlaylistDocument().read(resolver, uri));
            var cancelled = new app.aavideoplayercar.iptv.PlaylistDocument(); cancelled.cancel();
            assertThrows(android.os.OperationCanceledException.class, () -> cancelled.read(resolver, uri));
            assertThrows(java.io.IOException.class, () -> new app.aavideoplayercar.iptv.PlaylistDocument().read(resolver, android.net.Uri.parse("file:///private/list")));
        } finally { resolver.delete(uri, null, null); }
        assertThrows(java.io.FileNotFoundException.class, () -> new app.aavideoplayercar.iptv.PlaylistDocument().read(resolver, uri));
    }
    @Test public void documentResultImportsRefreshesAndRetainsChannelsWhenFileDisappears() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        byte[] bytes = playlist(2).getBytes(StandardCharsets.UTF_8);
        var uri = document(bytes); var resolver = context.getContentResolver();
        var parsed = M3uParser.parse(bytes, uri.toString(), uri.toString(), "Local playlist");
        var dao = MediaRepository.get(context).database.iptv();
        try (var activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> a.onActivityResult(13, android.app.Activity.RESULT_OK, new android.content.Intent().setData(uri)));
            await(activity, a -> list(a) != null && list(a).getCount() == 2);
            activity.onActivity(a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_playback_type)).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Channel 1")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(context.getResources().getStringArray(R.array.iptv_playback_types)[2])).perform(androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.iptv_save_type)).perform(androidx.test.espresso.action.ViewActions.click());
            await(activity, a -> list(a).getCount() == 2 && ((IptvChannel)list(a).getAdapter().getItem(1)).playbackMode == 2);
            dao.toggleFavorite(parsed.channels.get(0).id); dao.progress(parsed.channels.get(0).id, 5000, 30000, 1);
            try (var output = resolver.openOutputStream(uri, "wt")) { output.write(playlist(3).replace("Channel 0", "Updated channel").getBytes(StandardCharsets.UTF_8)); }
            activity.recreate(); await(activity, a -> list(a).getCount() == 2);
            activity.onActivity(a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_refresh)).performClick());
            await(activity, a -> list(a).getCount() == 3);
            assertEquals("Updated channel", dao.channel(parsed.channels.get(0).id).title);
            assertEquals(2, dao.channel(parsed.channels.get(1).id).playbackMode);
            assertTrue(dao.channel(parsed.channels.get(0).id).favorite); assertEquals(5000, dao.channel(parsed.channels.get(0).id).positionMs);
            resolver.delete(uri, null, null);
            activity.onActivity(a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_refresh)).performClick());
            await(activity, a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_document_error)) != null);
            assertEquals(3, dao.count(parsed.playlist.id, "", false));
        } finally { resolver.delete(uri, null, null); dao.delete(parsed.playlist.id); }
    }
    @Test public void phoneImportsHttpPlaylistPagesRestoresAndFiltersFavorites() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); String id = null;
        var repository = MediaRepository.get(context);
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            byte[] bytes = playlist(51).getBytes(StandardCharsets.UTF_8); server.gzip("/list", bytes, "audio/x-mpegurl");
            id = M3uParser.parse(bytes, server.url("/list"), server.url("/list"), "Fixture TV").playlist.id;
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick());
                await(activity, a -> text(a.getWindow().getDecorView(), "Import playlist").isEnabled());
                activity.onActivity(a -> {
                    ((EditText)a.getWindow().getDecorView().findViewWithTag("iptv.name")).setText("Fixture TV");
                    ((EditText)a.getWindow().getDecorView().findViewWithTag("iptv.url")).setText(server.url("/list"));
                    text(a.getWindow().getDecorView(), "Import playlist").performClick();
                });
                await(activity, a -> list(a).getCount() == 50);
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "Next").performClick());
                await(activity, a -> list(a).getCount() == 1); activity.recreate(); await(activity, a -> list(a).getCount() == 1);
                activity.onActivity(a -> list(a).getOnItemLongClickListener().onItemLongClick(list(a), null, 0, 0));
                await(activity, a -> list(a).getCount() == 1 && ((IptvChannel)list(a).getAdapter().getItem(0)).favorite);
                activity.onActivity(a -> ((CheckBox)text(a.getWindow().getDecorView(), "Favorites only")).setChecked(true));
                await(activity, a -> list(a).getCount() == 1 && !text(a.getWindow().getDecorView(), "Previous").isEnabled());
                assertEquals(1, repository.database.iptv().count(id, "", true));
            }
        } finally { if (id != null) repository.database.iptv().delete(id); }
    }
}
