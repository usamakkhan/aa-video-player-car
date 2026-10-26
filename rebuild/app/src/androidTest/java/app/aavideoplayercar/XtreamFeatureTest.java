package app.aavideoplayercar;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.iptv.*;
import app.aavideoplayercar.network.HttpDocument;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class XtreamFeatureTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String USER = "fixture+user", PASSWORD = "fixture/secret?";
    private void response(LocalHttpFixture server, XtreamAccount account, String action, String json) throws Exception {
        String url = XtreamClient.endpoint(account, action); server.add(url.substring(server.url("").length()), json.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    private void catalogue(LocalHttpFixture server, XtreamAccount account) throws Exception {
        response(server, account, "", "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":null,\"allowed_output_formats\":[\"m3u8\",\"ts\"]}}");
        response(server, account, "get_live_categories", "[{\"category_id\":1,\"category_name\":\"News\"}]");
        response(server, account, "get_vod_categories", "[{\"category_id\":2,\"category_name\":\"Drama\"}]");
        response(server, account, "get_live_streams", "[{\"stream_id\":2,\"name\":\"Live fixture\",\"category_id\":1,\"epg_channel_id\":\"station\"},{\"stream_id\":2,\"name\":\"Duplicate\"},{\"stream_id\":\"../bad\",\"name\":\"Bad\"}]");
        response(server, account, "get_vod_streams", "[{\"stream_id\":2,\"name\":\"Film fixture\",\"category_id\":2,\"container_extension\":\"mp4\"}]");
    }
    @Test public void secretsAreAuthenticatedDeviceEncryptedAndUrisEncodeCredentials() throws Exception {
        var account = XtreamClient.account("https://example.com/", USER, PASSWORD);
        assertFalse(account.credentials.contains(USER)); assertFalse(account.credentials.contains(PASSWORD));
        String plaintext = AccountSecrets.decrypt(account.playlistId, account.credentials); assertTrue(plaintext.contains(USER));
        assertNotEquals(account.credentials, AccountSecrets.encrypt(account.playlistId, plaintext));
        assertThrows(java.io.IOException.class, () -> AccountSecrets.decrypt("different-account", account.credentials));
        byte[] tampered = android.util.Base64.decode(account.credentials, android.util.Base64.NO_WRAP); tampered[tampered.length - 1] ^= 1;
        assertThrows(java.io.IOException.class, () -> AccountSecrets.decrypt(account.playlistId, android.util.Base64.encodeToString(tampered, android.util.Base64.NO_WRAP)));
        assertEquals("https://example.com/movie/fixture%2Buser/fixture%2Fsecret%3F/2.mp4", XtreamClient.stream(account, XtreamClient.PREFIX + account.playlistId + "/movie/2.mp4"));
        assertEquals("https://example.com/xmltv.php?username=fixture%2Buser&password=fixture%2Fsecret%3F", XtreamClient.guideEndpoint(account));
        assertThrows(java.io.IOException.class, () -> XtreamClient.stream(account, "xtream://other/movie/2.mp4"));
        assertThrows(java.io.IOException.class, () -> XtreamClient.stream(account, XtreamClient.PREFIX + account.playlistId + "/movie/../2.mp4"));
        assertThrows(java.io.IOException.class, () -> XtreamClient.account("https://example.com/?password=x", USER, PASSWORD));
    }
    @Test public void importsSeparateLiveAndMovieIdsAndRetainsIdentityAcrossPasswordChanges() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); catalogue(server, account);
            var parsed = XtreamClient.load(new HttpDocument(), account, "Account fixture"); assertEquals(2, parsed.channels.size()); assertEquals(2, parsed.skipped);
            assertTrue(parsed.channels.get(0).isLive()); assertFalse(parsed.channels.get(1).isLive());
            assertNotEquals(parsed.channels.get(0).id, parsed.channels.get(1).id); assertEquals("station", parsed.channels.get(0).tvgId);
            assertEquals("Live · News", parsed.channels.get(0).groupName); assertEquals("Movies · Drama", parsed.channels.get(1).groupName);
            var changed = XtreamClient.account(server.url(""), USER, "new-secret"); catalogue(server, changed);
            var refreshed = XtreamClient.load(new HttpDocument(), changed, "Renamed");
            assertEquals(parsed.playlist.id, refreshed.playlist.id); assertEquals(parsed.channels.get(1).id, refreshed.channels.get(1).id);
            assertFalse(parsed.channels.get(0).url.contains(USER)); assertFalse(parsed.channels.get(0).url.contains(PASSWORD));
        }
    }
    @Test public void nonLiveAccountsDoNotRequireLiveOutputFormatsButLiveChannelsDo() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD);
            for (String formats : new String[]{"[]", "[\"unsupported\"]"}) {
                catalogue(server, account);
                response(server, account, "", "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"allowed_output_formats\":" + formats + "}}");
                assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "Live"));
                response(server, account, "get_live_streams", "[]");
                var movies = XtreamClient.load(new HttpDocument(), account, "Movies"); assertEquals(1, movies.channels.size()); assertFalse(movies.channels.get(0).isLive());
                response(server, account, "get_vod_streams", "[]");
                assertTrue(XtreamClient.load(new HttpDocument(), account, "Series").channels.isEmpty());
            }
        }
    }
    @Test public void rejectsExpiredUnauthorizedRedirectedAndMalformedAccounts() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); catalogue(server, account);
            for (String profile : new String[]{"{\"user_info\":{\"auth\":0,\"status\":\"Active\"}}", "{\"user_info\":{\"auth\":1,\"status\":\"Expired\"}}", "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":1}}", "{\"user_info\":{}} trailing", "[".repeat(33) + "]".repeat(33)}) {
                response(server, account, "", profile); assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
            }
            server.ranges.clear(); server.redirect(XtreamClient.endpoint(account, "").substring(server.url("").length()), server.url("/unexpected"));
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV")); assertEquals(1, server.ranges.size());
            catalogue(server, account); response(server, account, "get_live_streams", "[] trailing");
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
        }
    }
    @Test public void boundsCataloguesRejectsLenientJsonAndHonorsCancellation() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); catalogue(server, account);
            for (String value : new String[]{"{/*comment*/\"user_info\":{\"auth\":1,\"status\":\"Active\"}}", "{'user_info':{'auth':1,'status':'Active'}}"}) {
                response(server, account, "", value); assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
            }
            catalogue(server, account); var cancelled = new HttpDocument(); cancelled.cancel();
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(cancelled, account, "TV"));
            var rows = new org.json.JSONArray(); for (int i = 0; i < 10001; i++) rows.put(new org.json.JSONObject().put("stream_id", i).put("name", "Channel"));
            response(server, account, "get_live_streams", rows.toString());
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
            catalogue(server, account); var categories = new org.json.JSONArray(); for (int i = 0; i < 2001; i++) categories.put(new org.json.JSONObject().put("category_id", i).put("category_name", "Category"));
            response(server, account, "get_live_categories", categories.toString());
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
            catalogue(server, account);
            server.gzip(XtreamClient.endpoint(account, "").substring(server.url("").length()), ("[" + " ".repeat(4 * 1024 * 1024) + "]").getBytes(StandardCharsets.UTF_8), "application/json");
            assertThrows(java.io.IOException.class, () -> XtreamClient.load(new HttpDocument(), account, "TV"));
        }
    }
    private View text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { var result = text(((ViewGroup)root).getChildAt(i), value); if (result != null) return result; }
        return null;
    }
    private void await(ActivityScenario<MainActivity> activity, java.util.function.Predicate<MainActivity> condition) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 12000;
        while (android.os.SystemClock.elapsedRealtime() < end) { var ready = new java.util.concurrent.atomic.AtomicBoolean(); activity.onActivity(a -> ready.set(condition.test(a))); if (ready.get()) return; Thread.sleep(50); }
        fail("Account screen timeout");
    }
    private ListView list(MainActivity a) { return a.getWindow().getDecorView().findViewWithTag("iptv.list"); }
    private String guideXml(int count) {
        StringBuilder xml = new StringBuilder("<tv>");
        var format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss", java.util.Locale.ROOT).withZone(java.time.ZoneOffset.UTC);
        for (int i = 0; i < count; i++) {
            var start = java.time.Instant.parse("2030-01-01T00:00:00Z").plusSeconds(i * 1800L);
            xml.append("<programme channel='station' start='").append(format.format(start)).append("' stop='").append(format.format(start.plusSeconds(1800)))
                .append("'><title>Account show ").append(i).append("</title></programme>");
        }
        return xml.append("</tv>").toString();
    }
    private void awaitGuide(String tag, java.util.function.Predicate<View> condition) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 12000;
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            onView(withTagValue(is(tag))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((view, error) -> { if (error != null) throw error; ready.set(condition.test(view)); });
            if (ready.get()) return; Thread.sleep(50);
        } fail("Account guide timeout");
    }
    @Test public void accountGuideKeepsSecretsOpaqueUsesRotatedCredentialsAndRetainsCacheOnFailure() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var repository = MediaRepository.get(context); String id = null;
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); id = account.playlistId; catalogue(server, account);
            var parsed = XtreamClient.load(new HttpDocument(), account, "Account guide fixture");
            repository.database.runInTransaction(() -> { repository.database.iptv().replace(parsed.playlist, parsed.channels, true); repository.database.xtream().save(account); });
            String guidePath = XtreamClient.guideEndpoint(account).substring(server.url("").length());
            server.gzip(guidePath, guideXml(51).getBytes(StandardCharsets.UTF_8), "application/xml");
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick());
                await(activity, a -> list(a).getCount() > 0);
                activity.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (((IptvPlaylist)list(a).getAdapter().getItem(i)).id.equals(account.playlistId)) { list(a).performItemClick(null, i, i); break; } });
                await(activity, a -> list(a).getCount() == 2 && list(a).getAdapter().getItem(0) instanceof IptvChannel);
                onView(withText(R.string.epg_open)).perform(click()); onView(withText("Live fixture")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                awaitGuide("epg.account", v -> v.isEnabled() && ((CheckBox)v).isChecked());
                onView(withTagValue(is("epg.url"))).check((view, error) -> { assertFalse(view.isEnabled()); assertEquals("", ((EditText)view).getText().toString()); });
                onView(withText(R.string.epg_import)).perform(click()); awaitGuide("epg.list", v -> ((ListView)v).getCount() == 50);
                assertEquals(XtreamClient.guideReference(account.playlistId), repository.database.epg().source(account.playlistId).url);
                onView(withText(R.string.radio_next)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); awaitGuide("epg.list", v -> ((ListView)v).getCount() == 1);
                activity.recreate(); awaitGuide("epg.account", v -> v.isEnabled() && ((CheckBox)v).isChecked()); awaitGuide("epg.list", v -> ((ListView)v).getCount() == 1);
                var rotated = XtreamClient.account(server.url(""), USER, "rotated-secret"); repository.database.xtream().save(rotated);
                String rotatedPath = XtreamClient.guideEndpoint(rotated).substring(server.url("").length());
                server.gzip(rotatedPath, guideXml(2).getBytes(StandardCharsets.UTF_8), "application/xml"); server.redirect(guidePath, server.url("/should-not-use-old-credentials"));
                onView(withText(R.string.epg_import)).perform(click()); awaitGuide("epg.list", v -> ((ListView)v).getCount() == 2);
                server.redirect(rotatedPath, server.url("/unexpected-redirect")); int requests = server.ranges.size();
                onView(withText(R.string.epg_import)).perform(click()); awaitGuide("epg.status", v -> context.getString(R.string.epg_account_error).contentEquals(((TextView)v).getText()));
                assertEquals(requests + 1, server.ranges.size()); assertEquals(2, repository.database.epg().count(account.playlistId, "station"));
                onView(withTagValue(is("epg.account"))).perform(click());
                server.add("/manual-guide", guideXml(3).getBytes(StandardCharsets.UTF_8), "application/xml");
                onView(withTagValue(is("epg.url"))).perform(replaceText(server.url("/manual-guide")), closeSoftKeyboard());
                onView(withText(R.string.epg_import)).perform(click()); awaitGuide("epg.list", v -> ((ListView)v).getCount() == 3);
                assertEquals(server.url("/manual-guide"), repository.database.epg().source(account.playlistId).url);
                onView(withText(R.string.epg_close)).perform(click());
                onView(withText(R.string.epg_open)).perform(click()); onView(withText("Live fixture")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                awaitGuide("epg.account", v -> v.isEnabled() && !((CheckBox)v).isChecked());
                onView(withTagValue(is("epg.url"))).check((view, error) -> assertEquals(server.url("/manual-guide"), ((EditText)view).getText().toString()));
                rotated.credentials = "unreadable ciphertext"; repository.database.xtream().save(rotated);
                onView(withTagValue(is("epg.account"))).perform(click()); onView(withText(R.string.epg_import)).perform(click());
                awaitGuide("epg.status", v -> context.getString(R.string.epg_account_error).contentEquals(((TextView)v).getText()));
                assertEquals(3, repository.database.epg().count(account.playlistId, "station")); assertEquals(server.url("/manual-guide"), repository.database.epg().source(account.playlistId).url);
                onView(withText(R.string.epg_close)).perform(click());
            }
        } finally { if (id != null) repository.database.iptv().delete(id); }
    }
    private androidx.media3.ui.PlayerView player(MainActivity a) { return a.getWindow().getDecorView().findViewWithTag("iptv.player"); }
    @Test public void seriesOnlyAccountBrowsesPagesRestoresAndPlaysCachedEpisode() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var repository = MediaRepository.get(context); String id = null;
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); id = account.playlistId; catalogue(server, account);
            response(server, account, "get_live_streams", "[]"); response(server, account, "get_vod_streams", "[]");
            response(server, account, "", "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"allowed_output_formats\":[]}}");
            var parsed = XtreamClient.load(new HttpDocument(), account, "Series account"); assertTrue(parsed.channels.isEmpty());
            repository.database.runInTransaction(() -> { repository.database.iptv().replace(parsed.playlist, parsed.channels, true); repository.database.xtream().save(account); });
            response(server, account, "get_series", "[{\"series_id\":7,\"name\":\"Fixture show\"}]");
            var rows = new org.json.JSONArray(); for (int i = 0; i < 51; i++) rows.put(new org.json.JSONObject().put("id", i + 100).put("title", "Episode " + i).put("episode_num", i).put("season", 1).put("container_extension", "mp4"));
            String details = XtreamSeries.detailsEndpoint(account, "7").substring(server.url("").length());
            var specials = new org.json.JSONArray().put(new org.json.JSONObject().put("id", 300).put("title", "Special episode").put("episode_num", 1).put("season", 0).put("container_extension", "mp4"));
            server.add(details, new org.json.JSONObject().put("episodes", new org.json.JSONObject().put("1", rows).put("0", specials)).toString().getBytes(StandardCharsets.UTF_8), "application/json");
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) {
                var bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                server.add("/series/fixture%2Buser/fixture%2Fsecret%3F/150.mp4", bytes.toByteArray(), "video/mp4");
            }
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick()); await(activity, a -> list(a).getCount() > 0);
                activity.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (((IptvPlaylist)list(a).getAdapter().getItem(i)).id.equals(account.playlistId)) { list(a).performItemClick(null, i, i); break; } });
                await(activity, a -> a.getWindow().getDecorView().findViewWithTag("iptv.series").isEnabled());
                onView(withTagValue(is("iptv.series"))).perform(click()); awaitGuide("series.refresh", View::isEnabled);
                onView(withTagValue(is("series.refresh"))).perform(click()); awaitGuide("series.list", v -> ((ListView)v).getCount() == 1);
                onView(withTagValue(is("series.search"))).perform(replaceText("no matching show"), closeSoftKeyboard()); onView(withTagValue(is("series.find"))).perform(click());
                awaitGuide("series.list", v -> ((ListView)v).getAdapter() != null && ((ListView)v).getCount() == 0);
                onView(withTagValue(is("series.search"))).perform(replaceText("fixture"), closeSoftKeyboard()); onView(withTagValue(is("series.find"))).perform(click());
                awaitGuide("series.list", v -> ((ListView)v).getCount() == 1);
                onView(withTagValue(is("series.list"))).check((v, e) -> ((ListView)v).performItemClick(null, 0, 0)); awaitGuide("series.refresh", View::isEnabled);
                onView(withTagValue(is("series.refresh"))).perform(click()); awaitGuide("series.season", v -> ((Spinner)v).getCount() == 2 && v.isEnabled());
                awaitGuide("series.list", v -> ((ListView)v).getCount() == 1 && ((IptvChannel)((ListView)v).getAdapter().getItem(0)).title.equals("Special episode"));
                onView(withTagValue(is("series.season"))).check((v, e) -> ((Spinner)v).setSelection(1)); awaitGuide("series.list", v -> ((ListView)v).getCount() == 50);
                onView(withTagValue(is("series.next"))).perform(click()); awaitGuide("series.list", v -> ((ListView)v).getCount() == 1);
                activity.recreate(); awaitGuide("series.list", v -> ((ListView)v).getCount() == 1);
                onView(withTagValue(is("series.season"))).check((v, e) -> assertEquals(1, ((Spinner)v).getSelectedItemPosition()));
                onView(withTagValue(is("series.search"))).check((v, e) -> assertEquals("fixture", ((EditText)v).getText().toString()));
                var show = repository.database.iptv().seriesPage(account.playlistId, "", 50, 0).get(0);
                var episode = repository.database.iptv().episodePage(show.id, 1, 50, 50).get(0); repository.database.iptv().progress(episode.id, 3000, 12000, 1);
                server.redirect(details, server.url("/unexpected")); onView(withTagValue(is("series.refresh"))).perform(click());
                awaitGuide("series.status", v -> context.getString(R.string.series_error).contentEquals(((TextView)v).getText()));
                assertEquals(51, repository.database.iptv().episodeCount(show.id, 1));
                onView(withTagValue(is("series.previous"))).check((v, e) -> assertTrue(v.isEnabled()));
                onView(withTagValue(is("series.list"))).check((v, e) -> ((ListView)v).performItemClick(null, 0, 0));
                await(activity, a -> player(a).getPlayer() != null && player(a).getPlayer().isPlaying() && player(a).getPlayer().getCurrentPosition() >= 3000 && player(a).getPlayer().getVideoSize().width == 160);
                activity.onActivity(a -> { player(a).getPlayer().pause(); player(a).getPlayer().clearMediaItems(); });
                activity.recreate(); await(activity, a -> a.getWindow().getDecorView().findViewWithTag("iptv.series").isEnabled());
                assertEquals(0, repository.database.iptv().count(account.playlistId, "", false));
            }
        } finally { if (id != null) repository.database.iptv().delete(id); }
    }
    @Test public void phoneSignInRefreshPlaybackAndRemovalUseEncryptedAccount() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var repository = MediaRepository.get(context); String id = null;
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), USER, PASSWORD); id = account.playlistId; catalogue(server, account);
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) {
                var bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                server.add("/movie/fixture%2Buser/fixture%2Fsecret%3F/2.mp4", bytes.toByteArray(), "video/mp4");
            }
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick());
                onView(withText(R.string.xtream_open)).perform(click());
                onView(withTagValue(is("xtream.name"))).perform(replaceText("Account fixture")); onView(withTagValue(is("xtream.server"))).perform(replaceText(server.url("")));
                onView(withTagValue(is("xtream.user"))).perform(replaceText(USER)); onView(withTagValue(is("xtream.password"))).perform(replaceText(PASSWORD), closeSoftKeyboard());
                onView(withText(R.string.xtream_import)).perform(click());
                await(activity, a -> list(a).getCount() == 2 && list(a).getAdapter().getItem(0) instanceof IptvChannel);
                var movie = repository.database.iptv().channels(account.playlistId, "Film", false, 50, 0).get(0);
                repository.database.iptv().toggleFavorite(movie.id); repository.database.iptv().progress(movie.id, 3000, 12000, 1);
                assertFalse(repository.database.xtream().account(account.playlistId).credentials.contains(PASSWORD));
                assertTrue(repository.database.iptv().channel(movie.id).url.startsWith("xtream://"));
                activity.onActivity(a -> list(a).performItemClick(null, 0, 0));
                await(activity, a -> player(a).getPlayer() != null && player(a).getPlayer().isPlaying() && player(a).getPlayer().getCurrentPosition() >= 3000 && player(a).getPlayer().getVideoSize().width == 160);
                activity.onActivity(a -> { player(a).getPlayer().pause(); player(a).getPlayer().clearMediaItems(); });
                activity.recreate(); await(activity, a -> list(a).getCount() == 2);
                activity.onActivity(a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_refresh)).performClick());
                await(activity, a -> list(a).getCount() == 2 && text(a.getWindow().getDecorView(), context.getString(R.string.iptv_refresh)).isEnabled());
                assertTrue(repository.database.iptv().channel(movie.id).favorite); assertTrue(repository.database.iptv().channel(movie.id).positionMs >= 3000);
                response(server, account, "", "{\"user_info\":{\"auth\":0}}");
                activity.onActivity(a -> text(a.getWindow().getDecorView(), context.getString(R.string.iptv_refresh)).performClick());
                await(activity, a -> text(a.getWindow().getDecorView(), context.getString(R.string.xtream_error)) != null);
                assertEquals(2, repository.database.iptv().count(account.playlistId, "", false));
            }
            repository.database.iptv().delete(account.playlistId); assertNull(repository.database.xtream().account(account.playlistId));
        } finally { if (id != null) repository.database.iptv().delete(id); }
    }
}
