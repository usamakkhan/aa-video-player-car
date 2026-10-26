package app.aavideoplayercar;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.aavideoplayercar.iptv.*;
import app.aavideoplayercar.network.HttpDocument;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class XtreamSeriesTest {
    private void response(LocalHttpFixture server, String url, String json) {
        server.add(url.substring(server.url("").length()), json.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    private String episode(String id, int number, String extension) {
        return "{\"id\":\"" + id + "\",\"episode_num\":" + number + ",\"title\":\"Episode\",\"container_extension\":\"" + extension + "\"}";
    }
    @Test public void discoveryIsLazyAndEpisodesAreOrderedOpaqueAndStable() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), "series+user", "secret/password");
            response(server, XtreamClient.endpoint(account, "get_series"), "[{\"series_id\":7,\"name\":\"Show\",\"category_id\":2},{\"series_id\":7,\"name\":\"Duplicate\"},{\"series_id\":\"../bad\",\"name\":\"Bad\"}]");
            var shows = XtreamSeries.catalogue(new HttpDocument(), account);
            assertEquals(1, server.ranges.size()); assertEquals(1, shows.items.size()); assertEquals(2, shows.skipped);
            var show = shows.items.get(0); assertEquals("2", show.category);
            String json = "{\"episodes\":{\"10\":[" + episode("30", 1, "mp4") + "],\"2\":[" + episode("20", 10, "mkv") + "," + episode("21", 2, "mp4") + "],\"0\":[" + episode("1", 1, "mp4") + "]}}";
            response(server, XtreamSeries.detailsEndpoint(account, show.providerId), json);
            var parsed = XtreamSeries.episodes(new HttpDocument(), account, show);
            assertEquals(4, parsed.items.size()); assertEquals(0, parsed.skipped);
            assertEquals(0, parsed.items.get(0).season); assertEquals(2, parsed.items.get(1).number); assertEquals(10, parsed.items.get(3).season);
            var channel = parsed.items.get(0).channel; assertFalse(channel.isLive());
            assertEquals("xtream://" + account.playlistId + "/series/1.mp4", channel.url);
            assertEquals(server.url("/series/series%2Buser/secret%2Fpassword/1.mp4"), XtreamClient.stream(account, channel.url));
            var changed = XtreamClient.account(server.url(""), "series+user", "replacement");
            response(server, XtreamSeries.detailsEndpoint(changed, show.providerId), json.replace("mp4", "mkv"));
            var refreshed = XtreamSeries.episodes(new HttpDocument(), changed, show);
            assertEquals(channel.id, refreshed.items.get(0).channel.id);
            assertTrue(refreshed.items.get(0).channel.url.endsWith(".mkv"));
            assertThrows(java.io.IOException.class, () -> XtreamSeries.detailsEndpoint(account, "1&password=leak"));
        }
    }
    @Test public void invalidAndDuplicateEpisodesAreSkippedWithoutAcceptingProviderUris() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), "u", "p");
            response(server, XtreamClient.endpoint(account, "get_series"), "[{\"series_id\":7,\"name\":\"Show\"}]");
            var show = XtreamSeries.catalogue(new HttpDocument(), account).items.get(0);
            response(server, XtreamSeries.detailsEndpoint(account, "7"), "{\"episodes\":{\"1\":[" + episode("1", 1, "mp4") + "," + episode("1", 2, "mp4") + "," + episode("../../2", 3, "mp4") + "," + episode("4", 4, "exe") + "," + episode("5", -1, "mp4") + ",{\"id\":6,\"episode_num\":6,\"season\":2,\"title\":\"Mismatch\"}]}}");
            var result = XtreamSeries.episodes(new HttpDocument(), account, show);
            assertEquals(1, result.items.size()); assertEquals(5, result.skipped);
        }
    }
    @Test public void rejectsRedirectsInvalidShapesAndOversizedSeriesResponses() throws Exception {
        try (var server = new LocalHttpFixture()) {
            var account = XtreamClient.account(server.url(""), "u", "p");
            String url = XtreamClient.endpoint(account, "get_series");
            response(server, url, "[{\"series_id\":7,\"name\":\"Show\"}]");
            var show = XtreamSeries.catalogue(new HttpDocument(), account).items.get(0);
            String details = XtreamSeries.detailsEndpoint(account, "7");
            for (String invalid : new String[]{"{\"episodes\":[]}", "{\"episodes\":{\"bad\":[]}}", "{\"episodes\":{\"1\":null}}", "{'episodes':{}}", "{} trailing"}) {
                response(server, details, invalid);
                assertThrows(java.io.IOException.class, () -> XtreamSeries.episodes(new HttpDocument(), account, show));
            }
            String rows = (episode("1", 1, "mp4") + ",").repeat(10000) + episode("2", 2, "mp4");
            response(server, details, "{\"episodes\":{\"1\":[" + rows + "]}}");
            assertThrows(java.io.IOException.class, () -> XtreamSeries.episodes(new HttpDocument(), account, show));
            response(server, url, "[" + "{\"series_id\":1,\"name\":\"Show\"},".repeat(10000) + "{\"series_id\":2,\"name\":\"Show\"}]");
            assertThrows(java.io.IOException.class, () -> XtreamSeries.catalogue(new HttpDocument(), account));
            server.ranges.clear(); server.redirect(details.substring(server.url("").length()), server.url("/unexpected"));
            assertThrows(java.io.IOException.class, () -> XtreamSeries.episodes(new HttpDocument(), account, show));
            assertEquals(1, server.ranges.size());
            var cancelled = new HttpDocument(); cancelled.cancel();
            assertThrows(java.io.IOException.class, () -> XtreamSeries.catalogue(cancelled, account));
        }
    }
}
