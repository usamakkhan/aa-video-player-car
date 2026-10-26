package app.aavideoplayercar.radio;

import app.aavideoplayercar.data.RadioStation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** One cancellable request; never run on the main or database worker thread. */
public final class RadioClient {
    public static final int PAGE_SIZE = 50;
    private volatile boolean cancelled;
    private boolean more;
    public boolean hasMore() { return more; }
    private volatile HttpURLConnection connection;
    public void cancel() {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }
    public List<RadioStation> search(String name, String country, int page) throws IOException {
        if (page < 0 || page > 10000 || name.length() > 200 || !country.matches("[A-Za-z]{2}|"))
            throw new IOException("Check the station name and two-letter country code.");
        String path = "/json/stations/search?hidebroken=true&order=name&limit=" + PAGE_SIZE
            + "&offset=" + ((long) page * PAGE_SIZE) + "&name=" + URLEncoder.encode(name, "UTF-8")
            + "&countrycode=" + URLEncoder.encode(country, "UTF-8");
        // A failed mirror does not prevent trying the other API endpoint.
        IOException failure = null;
        for (String host : new String[]{"de1.api.radio-browser.info", "all.api.radio-browser.info"}) {
            if (cancelled) throw new IOException("Search cancelled.");
            try {
                String response = read(new URL("https://" + host + path));
                List<RadioStation> stations = parse(response);
                try { more = new JSONArray(response).length() == PAGE_SIZE; }
                catch (JSONException error) { throw new IOException(error); }
                return stations;
            }
            catch (IOException e) { failure = e; }
        }
        throw new IOException("Radio directory unavailable. Check your connection and retry.", failure);
    }
    private String read(URL url) throws IOException {
        HttpURLConnection active = (HttpURLConnection) url.openConnection();
        connection = active;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        try {
            if (cancelled) throw new IOException("Search cancelled.");
            active.setConnectTimeout(8000); active.setReadTimeout(8000);
            active.setInstanceFollowRedirects(false);
            active.setRequestProperty("User-Agent", "AA-Video-Player-Car/0.1");
            active.setRequestProperty("Accept", "application/json");
            if (active.getResponseCode() != 200) throw new IOException("Directory request failed.");
            try (InputStream in = active.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled || System.nanoTime() > deadline || out.size() + count > 1024 * 1024) throw new IOException("Directory response cancelled or too large.");
                    out.write(buffer, 0, count);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { active.disconnect(); connection = null; }
    }
    public static boolean validStream(String value) {
        try {
            URI uri = new URI(value);
            return value.length() <= 8192 && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }
    public static List<RadioStation> parse(String json) throws IOException {
        if (json.length() > 1024 * 1024) throw new IOException("Directory response too large.");
        try {
            JSONArray rows = new JSONArray(json);
            if (rows.length() > PAGE_SIZE) throw new IOException("Directory page too large.");
            List<RadioStation> result = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String id = row.optString("stationuuid", "");
                try { if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) continue; }
                catch (IllegalArgumentException error) { continue; }
                id = id.toLowerCase(java.util.Locale.ROOT);
                String url = row.optString("url_resolved", "");
                if (!validStream(url)) url = row.optString("url", "");
                String name = row.optString("name", "").trim();
                if (!validStream(url) || name.isEmpty() || name.length() > 500 || !seen.add(id)) continue;
                RadioStation station = new RadioStation(); station.id = id; station.title = name; station.url = url;
                station.country = row.optString("countrycode", "").toUpperCase(java.util.Locale.ROOT);
                if (!station.country.matches("[A-Z]{2}|")) station.country = "";
                station.codec = row.optString("codec", "");
                if (station.codec.length() > 64) station.codec = "";
                station.hls = row.optInt("hls", 0) == 1;
                result.add(station);
            }
            return result;
        } catch (JSONException error) { throw new IOException("Invalid directory response.", error); }
    }
}
