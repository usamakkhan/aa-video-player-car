package app.aavideoplayercar.iptv;

import app.aavideoplayercar.data.IptvChannel;
import app.aavideoplayercar.data.XtreamAccount;
import app.aavideoplayercar.network.HttpDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bounded series discovery; episode requests are made only for a selected series. */
public final class XtreamSeries {
    private XtreamSeries() {}
    public static final class Show {
        public final String providerId, title, category;
        public Show(String providerId, String title, String category) { this.providerId = providerId; this.title = title; this.category = category; }
        @Override public String toString() { return title; }
    }
    public static final class Episode {
        public final int season, number;
        public final IptvChannel channel;
        Episode(int season, int number, IptvChannel channel) { this.season = season; this.number = number; this.channel = channel; }
    }
    public static final class Result<T> {
        public final List<T> items;
        public final int skipped;
        Result(List<T> items, int skipped) { this.items = Collections.unmodifiableList(items); this.skipped = skipped; }
    }
    private static String identifier(String value) throws IOException {
        if (!value.matches("[0-9]{1,18}")) throw new IOException("Invalid series identifier");
        return value;
    }
    private static String label(String value) throws IOException {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500) throw new IOException("Invalid series label");
        return trimmed;
    }
    private static int ordinal(String value) throws IOException {
        if (!value.matches("[0-9]{1,6}")) throw new IOException("Invalid season or episode number");
        return Integer.parseInt(value);
    }
    public static String detailsEndpoint(XtreamAccount account, String seriesId) throws IOException {
        return XtreamClient.endpoint(account, "get_series_info") + "&series_id=" + identifier(seriesId);
    }
    public static Result<Show> catalogue(HttpDocument client, XtreamAccount account) throws IOException {
        try {
            JSONArray rows = (JSONArray)XtreamClient.jsonUrl(client, XtreamClient.endpoint(account, "get_series"));
            if (rows.length() > 10000) throw new IOException();
            List<Show> shows = new ArrayList<>(); Set<String> seen = new HashSet<>(); int skipped = 0;
            for (int i = 0; i < rows.length(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException();
                try {
                    JSONObject row = rows.getJSONObject(i);
                    String id = identifier(row.getString("series_id")), title = label(row.getString("name"));
                    String category = row.optString("category_id", ""); if (category.length() > 500 || !seen.add(id)) throw new IOException();
                    shows.add(new Show(id, title, category));
                } catch (Exception invalid) { skipped++; }
            }
            return new Result<>(shows, skipped);
        } catch (Exception error) { throw new IOException("Series catalogue unavailable; check account and response limits"); }
    }
    public static Result<Episode> episodes(HttpDocument client, XtreamAccount account, Show show) throws IOException {
        try {
            JSONObject response = (JSONObject)XtreamClient.jsonUrl(client, detailsEndpoint(account, show.providerId));
            JSONObject seasons = response.getJSONObject("episodes"); if (seasons.length() > 1000) throw new IOException();
            List<Episode> episodes = new ArrayList<>(); Set<String> seen = new HashSet<>(); int skipped = 0, total = 0;
            Iterator<String> keys = seasons.keys();
            while (keys.hasNext()) {
                String key = keys.next(); int season = ordinal(key);
                JSONArray rows = seasons.getJSONArray(key); total += rows.length(); if (total > 10000) throw new IOException();
                for (int i = 0; i < rows.length(); i++) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException();
                    try {
                        JSONObject row = rows.getJSONObject(i);
                        String id = identifier(row.getString("id")), title = label(row.getString("title"));
                        int number = ordinal(row.getString("episode_num"));
                        if (row.has("season") && ordinal(row.getString("season")) != season) throw new IOException();
                        String extension = row.optString("container_extension", "mp4").toLowerCase(Locale.ROOT);
                        if (!extension.matches("ts|m3u8|mp4|mkv|avi|mov|webm|m4v")) throw new IOException();
                        if (!seen.add(id)) throw new IOException();
                        IptvChannel channel = new IptvChannel(); channel.playlistId = account.playlistId; channel.live = false; channel.title = title;
                        channel.id = UUID.nameUUIDFromBytes((account.playlistId + "\nseries/" + id).getBytes(StandardCharsets.UTF_8)).toString();
                        channel.url = XtreamClient.PREFIX + account.playlistId + "/series/" + id + "." + extension;
                        channel.groupName = show.title + " · S" + season + " E" + number;
                        episodes.add(new Episode(season, number, channel));
                    } catch (Exception invalid) { skipped++; }
                }
            }
            episodes.sort(Comparator.comparingInt((Episode e) -> e.season).thenComparingInt(e -> e.number).thenComparing(e -> e.channel.id));
            return new Result<>(episodes, skipped);
        } catch (Exception error) { throw new IOException("Series episodes unavailable; check account and response limits"); }
    }
}
