package app.aavideoplayercar;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.aavideoplayercar.podcast.PodcastDirectory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PodcastDirectoryTest {
    private JSONObject show(String url) throws Exception {
        return new JSONObject().put("kind", "podcast").put("collectionName", "Science show").put("artistName", "Publisher")
            .put("feedUrl", url).put("collectionViewUrl", "https://podcasts.apple.com/us/podcast/id123");
    }
    private byte[] result(JSONArray rows) throws Exception { return new JSONObject().put("results", rows).toString().getBytes(StandardCharsets.UTF_8); }
    @Test public void queryParametersAreEncodedAndInvalidQueriesRejected() throws Exception {
        String url = PodcastDirectory.requestUrl("a & b?", "gb");
        assertTrue(url.contains("country=GB")); assertTrue(url.contains("term=a+%26+b%3F"));
        assertTrue(url.contains("entity=podcast"));
        assertThrows(IOException.class, () -> PodcastDirectory.requestUrl(" ", "US"));
        assertThrows(IOException.class, () -> PodcastDirectory.requestUrl("Science", "USA"));
    }
    @Test public void onlyValidUniquePodcastFeedsAndTrustedStoreLinksAreExposed() throws Exception {
        var rows = new JSONArray().put(show("https://publisher.example/feed.xml"))
            .put(show("https://publisher.example/feed.xml"))
            .put(show("file:///private/feed"))
            .put(show("https://user:secret@example.com/feed"))
            .put(show("https://example.com/music").put("kind", "song"))
            .put(show("https://example.com/other").put("collectionViewUrl", "https://evil.example/store"));
        var shows = PodcastDirectory.parse(result(rows));
        assertEquals(2, shows.size()); assertEquals("Science show", shows.get(0).title);
        assertTrue(shows.get(0).storeUrl.startsWith("https://podcasts.apple.com/")); assertEquals("", shows.get(1).storeUrl);
        assertThrows(UnsupportedOperationException.class, () -> shows.clear());
        assertThrows(IOException.class, () -> PodcastDirectory.parse("[]".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> PodcastDirectory.parse("broken".getBytes(StandardCharsets.UTF_8)));
        JSONArray tooMany = new JSONArray(); for (int i = 0; i < 51; i++) tooMany.put(show("https://example.com/" + i));
        assertThrows(IOException.class, () -> PodcastDirectory.parse(result(tooMany)));
    }
    @Test public void cacheRateLimitExpiryAndEvictionBoundRequests() throws Exception {
        byte[] json = result(new JSONArray().put(show("https://example.com/feed")));
        AtomicLong time = new AtomicLong(); AtomicInteger requests = new AtomicInteger();
        var engine = new PodcastDirectory.SearchEngine(time::get);
        PodcastDirectory.Fetcher fetcher = url -> { requests.incrementAndGet(); return json; };
        engine.search("Science", "US", fetcher); engine.search(" Science ", "us", fetcher);
        assertEquals(1, requests.get());
        assertThrows(IOException.class, () -> engine.search("History", "US", fetcher));
        time.addAndGet(TimeUnit.MINUTES.toNanos(11)); engine.search("Science", "US", fetcher); assertEquals(2, requests.get());
        for (int i = 0; i < 20; i++) { time.addAndGet(TimeUnit.SECONDS.toNanos(4)); engine.search("Other " + i, "US", fetcher); }
        time.addAndGet(TimeUnit.SECONDS.toNanos(4)); engine.search("Science", "US", fetcher); assertEquals(23, requests.get());
    }
    @Test public void directoryFailureIsNotCachedAndHttpResponseCanBeParsed() throws Exception {
        AtomicLong time = new AtomicLong(); var engine = new PodcastDirectory.SearchEngine(time::get);
        assertThrows(IOException.class, () -> engine.search("Science", "US", url -> { throw new IOException("Unavailable"); }));
        time.addAndGet(TimeUnit.SECONDS.toNanos(4));
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            server.add("/search", result(new JSONArray().put(show(server.url("/feed.xml")))), "application/json");
            var shows = engine.search("Science", "US", url -> new app.aavideoplayercar.network.HttpDocument().get(server.url("/search"), "application/json").bytes);
            assertEquals(1, shows.size()); assertEquals(server.url("/feed.xml"), shows.get(0).feedUrl);
        }
    }
}
