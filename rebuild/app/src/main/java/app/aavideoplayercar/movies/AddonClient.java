package app.aavideoplayercar.movies;

import android.util.JsonReader;
import android.util.JsonToken;
import app.aavideoplayercar.network.HttpDocument;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** HTTP Stremio protocol adapter. Invoke off the UI thread with a per-operation cancellable client. */
public final class AddonClient {
    private AddonClient() {}
    public static final class Catalog {
        public final String id, type, name;
        public final Set<String> extras, required;
        Catalog(String id, String type, String name, Set<String> extras, Set<String> required) {
            this.id = id; this.type = type; this.name = name;
            this.extras = Collections.unmodifiableSet(extras); this.required = Collections.unmodifiableSet(required);
        }
    }
    private static final class Resource {
        String name; Set<String> types, prefixes;
        boolean matches(String resource, String type, String id) {
            if (!name.equals(resource) || !types.contains(type)) return false;
            if (prefixes == null) return true;
            for (String prefix : prefixes) if (id.startsWith(prefix)) return true;
            return false;
        }
    }
    public static final class Manifest {
        public final String url, id, name, version, description;
        public final List<Catalog> catalogs;
        private final List<Resource> resources;
        Manifest(String url, String id, String name, String version, String description, List<Catalog> catalogs, List<Resource> resources) {
            this.url = url; this.id = id; this.name = name; this.version = version; this.description = description;
            this.catalogs = Collections.unmodifiableList(catalogs); this.resources = resources;
        }
        public boolean supports(String resource, String type, String id) {
            for (Resource r : resources) if (r.matches(resource, type, id)) return true;
            return false;
        }
    }
    public static final class Title {
        public final String id, type, name, description, poster;
        public final List<Video> videos;
        Title(String id, String type, String name, String description, String poster, List<Video> videos) {
            this.id = id; this.type = type; this.name = name; this.description = description; this.poster = poster;
            this.videos = Collections.unmodifiableList(videos);
        }
    }
    public static final class Video {
        public final String id, title;
        public final int season, episode;
        /** Null means request the stream resource; empty means the provider explicitly supplied no streams. */
        public final List<Stream> streams;
        Video(String id, String title, int season, int episode, List<Stream> streams) {
            this.id = id; this.title = title; this.season = season; this.episode = episode; this.streams = streams;
        }
    }
    public enum StreamKind { HTTP, EXTERNAL, TORRENT, YOUTUBE, UNSUPPORTED }
    public static final class Stream {
        public final String name, description, url, reason;
        public final StreamKind kind;
        Stream(String name, String description, String url, StreamKind kind, String reason) {
            this.name = name; this.description = description; this.url = url; this.kind = kind; this.reason = reason;
        }
    }
    private static String string(JSONObject object, String key, int max, boolean required) throws IOException {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) { if (!required) return ""; throw new IOException("Missing add-on field"); }
        if (!(value instanceof String)) throw new IOException("Invalid add-on field");
        String text = (String)value;
        if (text.length() > max || (required && text.trim().isEmpty())) throw new IOException("Add-on field exceeds limits");
        return text;
    }
    private static Set<String> strings(JSONArray array, int max) throws IOException, JSONException {
        if (array.length() > max) throw new IOException("Too many add-on values");
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i); if (!(value instanceof String) || ((String)value).length() > 500) throw new IOException("Invalid add-on values");
            values.add((String)value);
        }
        return values;
    }
    public static String manifestUrl(String input) throws IOException {
        String url = HttpDocument.url(input); URI uri = URI.create(url);
        if (!uri.getPath().endsWith("/manifest.json") || uri.getRawQuery() != null) throw new IOException("Use an add-on manifest.json URL without a query");
        return url;
    }
    private static String encoded(String value) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 8192 || value.equals(".") || value.equals("..")) throw new IOException("Invalid add-on identifier");
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("*", "%2A");
    }
    public static String endpoint(Manifest addon, String resource, String type, String id, Map<String, String> extras) throws IOException {
        if (!Set.of("catalog", "meta", "stream").contains(resource) || !Set.of("movie", "series").contains(type)) throw new IOException("Unsupported add-on resource");
        StringBuilder url = new StringBuilder(addon.url.substring(0, addon.url.length() - "manifest.json".length()));
        url.append(resource).append('/').append(encoded(type)).append('/').append(encoded(id));
        if (!extras.isEmpty()) {
            url.append('/'); boolean first = true;
            for (var entry : new TreeMap<>(extras).entrySet()) { if (!first) url.append('&'); first = false; url.append(encoded(entry.getKey())).append('=').append(encoded(entry.getValue())); }
        }
        return HttpDocument.url(url.append(".json").toString());
    }
    public static Manifest manifest(HttpDocument client, String input) throws IOException {
        String url = manifestUrl(input);
        // Configured paths can contain secrets. Do not forward them through redirects or include them in errors.
        try { return parseManifest(json(client, url), url); }
        catch (Exception error) { throw new IOException("Add-on manifest unavailable or invalid"); }
    }
    static Manifest cachedManifest(String payload, String url) throws IOException {
        if (payload.length() > 1048576) throw new IOException("Invalid cached manifest");
        try { return parseManifest(new JSONObject(payload), manifestUrl(url)); }
        catch (Exception error) { throw new IOException("Invalid cached manifest"); }
    }
    static String manifestSnapshot(Manifest manifest) throws JSONException {
        JSONObject root = new JSONObject(); root.put("id", manifest.id).put("name", manifest.name).put("version", manifest.version).put("description", manifest.description);
        root.put("types", new JSONArray(List.of("movie", "series")));
        JSONArray resources = new JSONArray();
        for (Resource r : manifest.resources) { JSONObject item = new JSONObject().put("name", r.name).put("types", new JSONArray(r.types)); if (r.prefixes != null) item.put("idPrefixes", new JSONArray(r.prefixes)); resources.put(item); }
        root.put("resources", resources);
        JSONArray catalogs = new JSONArray();
        for (Catalog c : manifest.catalogs) { JSONObject item = new JSONObject().put("id", c.id).put("type", c.type).put("name", c.name); JSONArray extras = new JSONArray(); for (String field : c.extras) extras.put(new JSONObject().put("name", field).put("isRequired", c.required.contains(field))); item.put("extra", extras); catalogs.put(item); }
        return root.put("catalogs", catalogs).toString();
    }
    private static Manifest parseManifest(JSONObject root, String url) throws IOException {
        try {
            Set<String> types = strings(root.getJSONArray("types"), 100);
            Set<String> prefixes = root.has("idPrefixes") ? strings(root.getJSONArray("idPrefixes"), 100) : null;
            JSONArray resources = root.getJSONArray("resources"); if (resources.length() == 0 || resources.length() > 100) throw new IOException();
            List<Resource> parsed = new ArrayList<>();
            for (int i = 0; i < resources.length(); i++) {
                Resource r = new Resource(); Object raw = resources.get(i);
                if (raw instanceof String) { r.name = (String)raw; r.types = types; r.prefixes = prefixes; }
                else { JSONObject object = resources.getJSONObject(i); r.name = string(object, "name", 100, true); r.types = strings(object.getJSONArray("types"), 100); r.prefixes = object.has("idPrefixes") ? strings(object.getJSONArray("idPrefixes"), 100) : null; }
                parsed.add(r);
            }
            JSONArray catalogs = root.getJSONArray("catalogs"); if (catalogs.length() > 100) throw new IOException();
            List<Catalog> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int i = 0; i < catalogs.length(); i++) {
                JSONObject c = catalogs.getJSONObject(i); String type = string(c, "type", 100, true);
                if (!Set.of("movie", "series").contains(type)) continue;
                String id = string(c, "id", 500, true); if (!seen.add(type + "\n" + id)) throw new IOException();
                Set<String> extra = new LinkedHashSet<>(), required = new LinkedHashSet<>();
                JSONArray fields = c.optJSONArray("extra");
                if (fields != null) { if (fields.length() > 100) throw new IOException(); for (int j = 0; j < fields.length(); j++) { JSONObject f = fields.getJSONObject(j); String key = string(f, "name", 100, true); extra.add(key); if (f.optBoolean("isRequired")) required.add(key); } }
                String name = string(c, "name", 500, false); result.add(new Catalog(id, type, name.isEmpty() ? id : name, extra, required));
            }
            return new Manifest(url, string(root, "id", 500, true), string(root, "name", 500, true), string(root, "version", 100, true), string(root, "description", 16000, false), result, parsed);
        } catch (Exception error) { throw new IOException("Add-on manifest unavailable or invalid"); }
    }
    public static List<Title> catalog(HttpDocument client, Manifest addon, Catalog catalog, Map<String, String> extras) throws IOException {
        return catalogPage(client, addon, catalog, extras).items;
    }
    public static final class CatalogPage {
        public final List<Title> items;
        public final int consumed;
        CatalogPage(List<Title> items, int consumed) { this.items = Collections.unmodifiableList(items); this.consumed = consumed; }
    }
    static String catalogSnapshot(CatalogPage page) throws JSONException {
        JSONObject root = new JSONObject().put("consumed", page.consumed); JSONArray rows = new JSONArray();
        for (Title title : page.items) rows.put(titleSnapshot(title, false));
        return root.put("items", rows).toString();
    }
    static CatalogPage cachedCatalog(String payload, String type) throws IOException {
        if (payload.length() > 1048576) throw new IOException("Invalid cached catalog");
        try { JSONObject root = new JSONObject(payload); JSONArray rows = root.getJSONArray("items"); int consumed = root.getInt("consumed"); if (rows.length() > 2000 || consumed < rows.length() || consumed > 2000) throw new IOException();
            List<Title> titles = new ArrayList<>(); Set<String> ids = new HashSet<>(); for (int i=0;i<rows.length();i++) { Title title = title(rows.getJSONObject(i), false); if (!title.type.equals(type) || !ids.add(title.id)) throw new IOException(); titles.add(title); } return new CatalogPage(titles, consumed);
        } catch (Exception error) { throw new IOException("Invalid cached catalog"); }
    }
    static String metaSnapshot(Title title) throws JSONException { return titleSnapshot(title, true).toString(); }
    static Title cachedMeta(String payload, String type, String id) throws IOException {
        if (payload.length() > 1048576) throw new IOException("Invalid cached metadata");
        try { Title title = title(new JSONObject(payload), true); if (!title.id.equals(id) || !title.type.equals(type)) throw new IOException(); return title; }
        catch (Exception error) { throw new IOException("Invalid cached metadata"); }
    }
    private static JSONObject titleSnapshot(Title title, boolean detailed) throws JSONException {
        JSONObject object = new JSONObject().put("id", title.id).put("type", title.type).put("name", title.name).put("description", title.description).put("poster", title.poster);
        if (detailed) { JSONArray videos = new JSONArray(); for (Video video : title.videos) { JSONObject row = new JSONObject().put("id", video.id).put("title", video.title); if (video.season >= 0) row.put("season", video.season); if (video.episode >= 0) row.put("episode", video.episode); videos.put(row); } object.put("videos", videos); }
        return object;
    }
    public static CatalogPage catalogPage(HttpDocument client, Manifest addon, Catalog catalog, Map<String, String> extras) throws IOException {
        if (!addon.catalogs.contains(catalog) || !catalog.extras.containsAll(extras.keySet()) || !extras.keySet().containsAll(catalog.required)) throw new IOException("Unsupported or missing catalog filters");
        if (extras.containsKey("skip") && !extras.get("skip").matches("[0-9]{1,7}")) throw new IOException("Invalid catalog offset");
        try {
            JSONArray rows = json(client, endpoint(addon, "catalog", catalog.type, catalog.id, extras)).getJSONArray("metas");
            if (rows.length() > 2000) throw new IOException(); List<Title> titles = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) { check(); Title title = title(rows.getJSONObject(i), false); if (!title.type.equals(catalog.type)) throw new IOException(); if (seen.add(title.id)) titles.add(title); }
            return new CatalogPage(titles, rows.length());
        } catch (Exception error) { throw new IOException("Add-on catalog unavailable or invalid"); }
    }
    public static Title meta(HttpDocument client, Manifest addon, String type, String id) throws IOException {
        if (!addon.supports("meta", type, id)) throw new IOException("Add-on does not provide metadata for this title");
        try { Title title = title(json(client, endpoint(addon, "meta", type, id, Map.of())).getJSONObject("meta"), true); if (!title.id.equals(id) || !title.type.equals(type)) throw new IOException(); return title; }
        catch (Exception error) { throw new IOException("Add-on metadata unavailable or invalid"); }
    }
    public static List<Stream> streams(HttpDocument client, Manifest addon, String type, String videoId) throws IOException {
        if (!addon.supports("stream", type, videoId)) throw new IOException("Add-on does not provide streams for this video");
        try { return streams(json(client, endpoint(addon, "stream", type, videoId, Map.of())).getJSONArray("streams")); }
        catch (Exception error) { throw new IOException("Add-on streams unavailable or invalid"); }
    }
    private static Title title(JSONObject o, boolean detailed) throws IOException, JSONException {
        List<Video> videos = new ArrayList<>(); JSONArray rows = detailed ? o.optJSONArray("videos") : null;
        if (rows != null) { if (rows.length() > 10000) throw new IOException(); Set<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) { check(); JSONObject v = rows.getJSONObject(i); String id = string(v, "id", 500, true); if (!seen.add(id)) continue;
                videos.add(new Video(id, string(v, "title", 500, true), ordinal(v, "season"), ordinal(v, "episode"), v.has("streams") ? streams(v.getJSONArray("streams")) : null)); }
        }
        String poster = string(o, "poster", 8192, false); if (!poster.isEmpty()) { try { poster = HttpDocument.url(poster); } catch (IOException invalid) { poster = ""; } }
        return new Title(string(o, "id", 500, true), string(o, "type", 100, true), string(o, "name", 500, true), string(o, "description", 64000, false), poster, videos);
    }
    private static int ordinal(JSONObject o, String key) throws IOException {
        if (!o.has(key)) return -1; Object raw = o.opt(key);
        if (!(raw instanceof Number) || ((Number)raw).doubleValue() < 0 || ((Number)raw).doubleValue() > 1000000 || ((Number)raw).doubleValue() != ((Number)raw).intValue()) throw new IOException("Invalid episode number");
        return ((Number)raw).intValue();
    }
    private static List<Stream> streams(JSONArray rows) throws IOException, JSONException {
        if (rows.length() > 500) throw new IOException(); List<Stream> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            check(); JSONObject s = rows.getJSONObject(i); String url = string(s, "url", 8192, false), reason = "Unsupported stream source"; StreamKind kind = StreamKind.UNSUPPORTED;
            if (!url.isEmpty()) {
                try { url = HttpDocument.url(url); kind = StreamKind.HTTP; reason = ""; } catch (IOException invalid) { url = ""; }
                JSONObject hints = s.optJSONObject("behaviorHints"); if (hints != null && hints.has("proxyHeaders")) { kind = StreamKind.UNSUPPORTED; reason = "Stream requires custom headers"; }
            } else if (s.has("infoHash")) { kind = StreamKind.TORRENT; reason = "Torrent playback is not available"; }
            else if (s.has("ytId")) { kind = StreamKind.YOUTUBE; reason = "YouTube stream resolution is not available"; }
            else if (s.has("externalUrl")) { try { url = HttpDocument.url(string(s, "externalUrl", 8192, true)); kind = StreamKind.EXTERNAL; reason = "Open in browser"; } catch (IOException invalid) { url = ""; } }
            String description = string(s, "description", 16000, false); if (description.isEmpty()) description = string(s, "title", 16000, false);
            result.add(new Stream(string(s, "name", 500, false), description, url, kind, reason));
        }
        return Collections.unmodifiableList(result);
    }
    private static JSONObject json(HttpDocument client, String url) throws IOException, JSONException {
        byte[] data = client.getWithoutRedirects(url, "application/json").bytes;
        String text = new String(data, StandardCharsets.UTF_8);
        try (JsonReader reader = new JsonReader(new StringReader(text))) { reader.setLenient(false); validate(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException(); }
        return new JSONObject(text);
    }
    private static void check() throws IOException { if (Thread.currentThread().isInterrupted()) throw new IOException("Add-on request cancelled"); }
    private static void validate(JsonReader r, int depth) throws IOException {
        check(); if (depth > 32) throw new IOException("Add-on JSON too deep");
        switch (r.peek()) {
            case BEGIN_OBJECT: r.beginObject(); Set<String> names = new HashSet<>(); while (r.hasNext()) { if (!names.add(r.nextName())) throw new IOException("Duplicate JSON field"); validate(r, depth + 1); } r.endObject(); break;
            case BEGIN_ARRAY: r.beginArray(); while (r.hasNext()) validate(r, depth + 1); r.endArray(); break;
            case STRING: case NUMBER: r.nextString(); break;
            case BOOLEAN: r.nextBoolean(); break;
            case NULL: r.nextNull(); break;
            default: throw new IOException("Invalid add-on JSON");
        }
    }
}
