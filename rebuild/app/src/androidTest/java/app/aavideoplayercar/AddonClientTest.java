package app.aavideoplayercar;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.aavideoplayercar.movies.AddonClient;
import app.aavideoplayercar.network.HttpDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AddonClientTest {
    private static final String MANIFEST = "{\"id\":\"org.fixture\",\"version\":\"1.0.0\",\"name\":\"Fixture\",\"types\":[\"movie\",\"series\"],\"idPrefixes\":[\"tt\"],\"resources\":[\"catalog\",\"meta\",{\"name\":\"stream\",\"types\":[\"movie\",\"series\"]}],\"catalogs\":[{\"id\":\"popular\",\"type\":\"movie\",\"extra\":[{\"name\":\"search\",\"isRequired\":true},{\"name\":\"skip\"}]}]}";
    private void serve(LocalHttpFixture server, String path, String json) { server.add(path, json.getBytes(StandardCharsets.UTF_8), "application/json"); }
    private interface Operation { void run() throws Exception; }
    private void rejects(Operation action) throws Exception { try { action.run(); fail("Expected rejection"); } catch (IOException expected) {} }
    @Test public void manifestCapabilitiesAndEncodedCatalogSearch() throws Exception {
        try (var server = new LocalHttpFixture()) {
            serve(server, "/configured/manifest.json", MANIFEST);
            var addon = AddonClient.manifest(new HttpDocument(), server.url("/configured/manifest.json"));
            assertTrue(addon.supports("meta", "movie", "tt123")); assertFalse(addon.supports("meta", "movie", "other"));
            assertTrue(addon.supports("stream", "series", "custom:0:1")); assertFalse(addon.supports("stream", "tv", "tt1"));
            var catalog = addon.catalogs.get(0); assertEquals("popular", catalog.name);
            String endpoint = AddonClient.endpoint(addon, "catalog", "movie", "popular", Map.of("search", "A & B/é", "skip", "50"));
            assertTrue(endpoint.endsWith("/catalog/movie/popular/search=A%20%26%20B%2F%C3%A9&skip=50.json"));
            serve(server, "/configured/catalog/movie/popular/search=hello&skip=50.json", "{\"metas\":[{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"Film\",\"poster\":\"javascript:bad\"},{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"Duplicate\"}]}");
            var titles = AddonClient.catalog(new HttpDocument(), addon, catalog, Map.of("search", "hello", "skip", "50"));
            assertEquals(1, titles.size()); assertEquals("Film", titles.get(0).name); assertEquals("", titles.get(0).poster);
            var batch = AddonClient.catalogPage(new HttpDocument(), addon, catalog, Map.of("search", "hello", "skip", "50"));
            assertEquals(1, batch.items.size()); assertEquals(2, batch.consumed);
            rejects(() -> AddonClient.catalog(new HttpDocument(), addon, catalog, Map.of()));
            rejects(() -> AddonClient.catalog(new HttpDocument(), addon, catalog, Map.of("search", "hello", "genre", "Drama")));
            rejects(() -> AddonClient.catalog(new HttpDocument(), addon, catalog, Map.of("search", "hello", "skip", "-1")));
            rejects(() -> AddonClient.endpoint(addon, "meta", "movie", "..", Map.of()));
            assertTrue(AddonClient.endpoint(addon, "meta", "series", "tt1:0:1/a?b", Map.of()).endsWith("/meta/series/tt1%3A0%3A1%2Fa%3Fb.json"));
        }
    }
    @Test public void metadataEpisodesAndStreamKindsRemainExplicit() throws Exception {
        try (var server = new LocalHttpFixture()) {
            serve(server, "/manifest.json", MANIFEST); var addon = AddonClient.manifest(new HttpDocument(), server.url("/manifest.json"));
            serve(server, "/meta/series/tt1.json", "{\"meta\":{\"id\":\"tt1\",\"type\":\"series\",\"name\":\"Show\",\"videos\":[{\"id\":\"tt1:0:1\",\"title\":\"Special\",\"season\":0,\"episode\":1,\"streams\":[]},{\"id\":\"tt1:1:1\",\"title\":\"Pilot\",\"season\":1,\"episode\":1}]}}");
            var title = AddonClient.meta(new HttpDocument(), addon, "series", "tt1");
            assertEquals(2, title.videos.size()); assertEquals(0, title.videos.get(0).season); assertTrue(title.videos.get(0).streams.isEmpty()); assertNull(title.videos.get(1).streams);
            serve(server, "/stream/movie/tt1.json", "{\"streams\":[{\"url\":\"https://example.com/video.m3u8\"},{\"infoHash\":\"abc\"},{\"ytId\":\"abc\"},{\"externalUrl\":\"https://example.com/watch\"},{\"url\":\"file:///private\"},{\"url\":\"https://example.com/protected\",\"behaviorHints\":{\"proxyHeaders\":{\"request\":{\"Authorization\":\"secret\"}}}}]}");
            var streams = AddonClient.streams(new HttpDocument(), addon, "movie", "tt1");
            assertEquals(AddonClient.StreamKind.HTTP, streams.get(0).kind); assertEquals(AddonClient.StreamKind.TORRENT, streams.get(1).kind);
            assertEquals(AddonClient.StreamKind.YOUTUBE, streams.get(2).kind); assertEquals(AddonClient.StreamKind.EXTERNAL, streams.get(3).kind);
            assertEquals(AddonClient.StreamKind.UNSUPPORTED, streams.get(4).kind); assertEquals("", streams.get(4).url);
            assertEquals(AddonClient.StreamKind.UNSUPPORTED, streams.get(5).kind); assertFalse(streams.get(5).reason.contains("secret"));
            serve(server, "/meta/series/tt1.json", "{\"meta\":{\"id\":\"wrong\",\"type\":\"series\",\"name\":\"Wrong\"}}");
            rejects(() -> AddonClient.meta(new HttpDocument(), addon, "series", "tt1"));
        }
    }
    @Test public void malformedDuplicateDeepOversizedAndCancelledResponsesFail() throws Exception {
        try (var server = new LocalHttpFixture()) {
            for (String invalid : new String[]{"{id:'bad'}", MANIFEST + "{}", MANIFEST.replace("\"version\":", "\"id\":\"duplicate\",\"version\":"), "{\"nested\":" + "[".repeat(34) + "0" + "]".repeat(34) + "}"}) {
                serve(server, "/manifest.json", invalid); rejects(() -> AddonClient.manifest(new HttpDocument(), server.url("/manifest.json")));
            }
            serve(server, "/manifest.json", MANIFEST); var addon = AddonClient.manifest(new HttpDocument(), server.url("/manifest.json"));
            serve(server, "/stream/movie/tt1.json", "{\"streams\":[" + "{},".repeat(500) + "{}]}");
            rejects(() -> AddonClient.streams(new HttpDocument(), addon, "movie", "tt1"));
            serve(server, "/manifest.json", " ".repeat(HttpDocument.MAX_BYTES + 1)); rejects(() -> AddonClient.manifest(new HttpDocument(), server.url("/manifest.json")));
            var cancelled = new HttpDocument(); cancelled.cancel(); rejects(() -> AddonClient.manifest(cancelled, server.url("/manifest.json")));
            rejects(() -> AddonClient.manifestUrl("https://name:secret@example.com/manifest.json"));
            rejects(() -> AddonClient.manifestUrl("https://example.com/manifest.json?token=secret"));
            rejects(() -> AddonClient.manifestUrl("file:///manifest.json"));
        }
    }
}
