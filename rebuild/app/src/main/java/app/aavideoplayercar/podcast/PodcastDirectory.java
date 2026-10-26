package app.aavideoplayercar.podcast;

import app.aavideoplayercar.network.HttpDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Metadata discovery only; playback always comes from the publisher's subscribed feed. */
public final class PodcastDirectory {
    public static final int LIMIT = 50;
    public static final class Show {
        public final String title, publisher, feedUrl, storeUrl;
        Show(String title, String publisher, String feedUrl, String storeUrl) {
            this.title = title; this.publisher = publisher; this.feedUrl = feedUrl; this.storeUrl = storeUrl;
        }
        @Override public String toString() { return publisher.isEmpty() ? title : title + "\n" + publisher; }
    }
    private static final class Cached {
        final List<Show> shows; final long time;
        Cached(List<Show> shows, long time) { this.shows = shows; this.time = time; }
    }
    @FunctionalInterface public interface Fetcher { byte[] get(String url) throws IOException; }
    private static final SearchEngine ENGINE = new SearchEngine(System::nanoTime);
    private PodcastDirectory() { }
    public static String requestUrl(String query, String country) throws IOException {
        String term = query.trim(), region = country.trim().toUpperCase(Locale.ROOT);
        if (term.isEmpty() || term.length() > 200 || !region.matches("[A-Z]{2}")) throw new IOException("Enter a podcast name and a two-letter country code.");
        return "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=" + LIMIT
            + "&country=" + region + "&term=" + URLEncoder.encode(term, "UTF-8");
    }
    public static List<Show> search(String query, String country, HttpDocument request) throws IOException {
        return ENGINE.search(query, country, endpoint -> request.get(endpoint, "application/json").bytes);
    }
    /** Keeps rate/cache policy separate from transport and usable with other directory adapters. */
    public static final class SearchEngine {
        private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(20, 0.75f, true);
        private final java.util.function.LongSupplier clock;
        private long lastRequest;
        private boolean requested;
        public SearchEngine(java.util.function.LongSupplier clock) { this.clock = clock; }
        public List<Show> search(String query, String country, Fetcher fetcher) throws IOException {
            String endpoint = requestUrl(query, country); long now = clock.getAsLong();
            synchronized (cache) {
                Cached cached = cache.get(endpoint);
                if (cached != null && now - cached.time < TimeUnit.MINUTES.toNanos(10)) return cached.shows;
                if (requested && now - lastRequest < TimeUnit.SECONDS.toNanos(3)) throw new IOException("Wait a few seconds before searching again.");
                requested = true; lastRequest = now;
            }
            List<Show> results = parse(fetcher.get(endpoint));
            synchronized (cache) {
                cache.put(endpoint, new Cached(results, clock.getAsLong()));
                while (cache.size() > 20) cache.remove(cache.keySet().iterator().next());
            }
            return results;
        }
    }
    public static List<Show> parse(byte[] bytes) throws IOException {
        if (bytes.length > HttpDocument.MAX_BYTES) throw new IOException("Directory response too large.");
        try {
            JSONArray rows = new JSONObject(new String(bytes, StandardCharsets.UTF_8)).getJSONArray("results");
            if (rows.length() > LIMIT) throw new IOException("Directory result limit exceeded.");
            List<Show> shows = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !"podcast".equals(row.optString("kind"))) continue;
                String title = row.optString("collectionName", row.optString("trackName", "")).trim();
                String publisher = row.optString("artistName", "").trim();
                if (title.isEmpty() || title.length() > 500 || publisher.length() > 500) continue;
                String feed;
                try { feed = HttpDocument.url(row.optString("feedUrl", "")); } catch (IOException error) { continue; }
                if (!seen.add(feed)) continue;
                String store = "";
                try {
                    String candidate = HttpDocument.url(row.optString("collectionViewUrl", ""));
                    java.net.URI uri = java.net.URI.create(candidate);
                    if ("https".equalsIgnoreCase(uri.getScheme()) && ("podcasts.apple.com".equalsIgnoreCase(uri.getHost()) || "itunes.apple.com".equalsIgnoreCase(uri.getHost()))) store = candidate;
                } catch (Exception ignored) { }
                shows.add(new Show(title, publisher, feed, store));
            }
            return Collections.unmodifiableList(shows);
        } catch (org.json.JSONException error) { throw new IOException("Invalid podcast directory response.", error); }
    }
}
