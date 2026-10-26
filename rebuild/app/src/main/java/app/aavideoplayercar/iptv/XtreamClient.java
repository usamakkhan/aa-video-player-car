package app.aavideoplayercar.iptv;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Xtream-compatible live/movie catalogue. Secrets are resolved only on a background worker. */
public final class XtreamClient {
    public static final String PREFIX = "xtream://";
    public static final class Result {
        public final IptvPlaylist playlist; public final List<IptvChannel> channels; public final XtreamAccount account;
        public final int skipped;
        Result(IptvPlaylist playlist, List<IptvChannel> channels, XtreamAccount account, int skipped) { this.playlist = playlist; this.channels = channels; this.account = account; this.skipped = skipped; }
    }
    private static String id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString(); }
    private static String encode(String value) { return android.net.Uri.encode(value); }
    public static XtreamAccount account(String server, String username, String password) throws IOException {
        try {
            URI uri = URI.create(server.trim());
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IOException("Use the server base URL");
            String base = HttpDocument.url(server); while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (username.trim().isEmpty() || password.isEmpty() || username.length() > 200 || password.length() > 1024) throw new IOException("Invalid credentials");
            XtreamAccount account = new XtreamAccount(); account.server = base; account.playlistId = id(base + "\n" + username);
            account.credentials = AccountSecrets.encrypt(account.playlistId, new JSONArray().put(username).put(password).toString()); return account;
        } catch (Exception error) { throw new IOException("Invalid account or unavailable credential storage"); }
    }
    private static JSONArray credentials(XtreamAccount account) throws Exception { return new JSONArray(AccountSecrets.decrypt(account.playlistId, account.credentials)); }
    public static String guideReference(String playlistId) { return PREFIX + playlistId + "/xmltv"; }
    public static String guideEndpoint(XtreamAccount account) throws IOException {
        try {
            var credentials = credentials(account);
            return account.server + "/xmltv.php?username=" + encode(credentials.getString(0)) + "&password=" + encode(credentials.getString(1));
        } catch (Exception error) { throw new IOException("Account guide unavailable; sign in again"); }
    }
    public static String endpoint(XtreamAccount account, String action) throws IOException {
        try {
            if (!action.matches("(|get_live_categories|get_live_streams|get_vod_categories|get_vod_streams|get_series_categories|get_series|get_series_info)")) throw new IOException();
            var credentials = credentials(account);
            return account.server + "/player_api.php?username=" + encode(credentials.getString(0)) + "&password=" + encode(credentials.getString(1)) + (action.isEmpty() ? "" : "&action=" + action);
        } catch (Exception error) { throw new IOException("Account unavailable"); }
    }
    public static String stream(XtreamAccount account, String reference) throws IOException {
        return new StreamResolver(account).resolve(reference);
    }
    public static final class StreamResolver {
        private final XtreamAccount account;
        private final JSONArray secrets;
        public StreamResolver(XtreamAccount account) throws IOException {
            try { this.account = account; secrets = credentials(account); }
            catch (Exception error) { throw new IOException("Account unavailable"); }
        }
        public String resolve(String reference) throws IOException {
        try {
            URI uri = URI.create(reference);
            if (!"xtream".equals(uri.getScheme()) || !account.playlistId.equals(uri.getHost()) || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null || uri.getPort() != -1
                || !uri.getRawPath().matches("/(live|movie|series)/[0-9]{1,18}\\.(ts|m3u8|mp4|mkv|avi|mov|webm|m4v)")) throw new IOException();
            String[] parts = uri.getPath().split("/");
            return account.server + "/" + parts[1] + "/" + encode(secrets.getString(0)) + "/" + encode(secrets.getString(1)) + "/" + parts[2];
        } catch (Exception error) { throw new IOException("Channel account unavailable; sign in again"); }
        }
    }
    private static Object json(HttpDocument client, XtreamAccount account, String action) throws Exception {
        return jsonUrl(client, endpoint(account, action));
    }
    static Object jsonUrl(HttpDocument client, String url) throws Exception {
        byte[] bytes = client.getWithoutRedirects(url, "application/json").bytes;
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        try (var reader = new android.util.JsonReader(new java.io.StringReader(text))) {
            reader.setLenient(false); validateJson(reader, 0);
            if (reader.peek() != android.util.JsonToken.END_DOCUMENT) throw new IOException();
        }
        JSONTokener parser = new JSONTokener(text); Object value = parser.nextValue(); if (parser.nextClean() != 0) throw new IOException(); return value;
    }
    private static void validateJson(android.util.JsonReader reader, int depth) throws IOException {
        if (depth > 32 || Thread.currentThread().isInterrupted()) throw new IOException("Invalid or cancelled account response");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject(); while (reader.hasNext()) { reader.nextName(); validateJson(reader, depth + 1); } reader.endObject(); break;
            case BEGIN_ARRAY:
                reader.beginArray(); while (reader.hasNext()) validateJson(reader, depth + 1); reader.endArray(); break;
            case STRING: case NUMBER: reader.nextString(); break;
            case BOOLEAN: reader.nextBoolean(); break;
            case NULL: reader.nextNull(); break;
            default: throw new IOException("Invalid account response");
        }
    }
    public static Result load(HttpDocument client, XtreamAccount account, String title) throws IOException {
        try {
            if (title.trim().isEmpty() || title.trim().length() > 200) throw new IOException();
            JSONObject profile = (JSONObject)json(client, account, ""); JSONObject user = profile.getJSONObject("user_info");
            if (user.optInt("auth", 0) != 1 || !"Active".equalsIgnoreCase(user.optString("status"))) throw new IOException();
            String expires = user.optString("exp_date", "");
            if (!expires.isEmpty() && !expires.equals("null") && !expires.equals("0") && Long.parseLong(expires) <= System.currentTimeMillis() / 1000) throw new IOException();
            String liveExtension = "ts"; JSONArray formats = user.optJSONArray("allowed_output_formats");
            if (formats != null) { boolean ts = false, hls = false; for (int i = 0; i < formats.length(); i++) { ts |= "ts".equals(formats.optString(i)); hls |= "m3u8".equals(formats.optString(i)); } if (hls) liveExtension = "m3u8"; else if (!ts) liveExtension = ""; }
            IptvPlaylist playlist = new IptvPlaylist(); playlist.id = account.playlistId; playlist.url = PREFIX + account.playlistId; playlist.title = title.trim(); playlist.updatedAt = System.currentTimeMillis();
            List<IptvChannel> channels = new ArrayList<>(); Set<String> seen = new HashSet<>(); int skipped = 0, entries = 0;
            for (String kind : new String[]{"live", "vod"}) {
                JSONArray categories = (JSONArray)json(client, account, "get_" + kind + "_categories"); if (categories.length() > 2000) throw new IOException();
                Map<String,String> groups = new HashMap<>();
                for (int i = 0; i < categories.length(); i++) { var category = categories.getJSONObject(i); String label = category.optString("category_name").trim(); if (label.length() > 500) throw new IOException(); groups.put(category.optString("category_id"), label); }
                JSONArray rows = (JSONArray)json(client, account, "get_" + kind + "_streams"); entries += rows.length(); if (entries > 10000) throw new IOException();
                if (kind.equals("live") && rows.length() > 0 && liveExtension.isEmpty()) throw new IOException();
                for (int i = 0; i < rows.length(); i++) {
                    try {
                        JSONObject row = rows.getJSONObject(i); String stream = row.getString("stream_id"), label = row.getString("name").trim();
                        boolean live = kind.equals("live"); String extension = live ? liveExtension : row.optString("container_extension", "mp4").toLowerCase(Locale.ROOT);
                        if (!stream.matches("[0-9]{1,18}") || label.isEmpty() || label.length() > 500 || !extension.matches("ts|m3u8|mp4|mkv|avi|mov|webm|m4v")) throw new IllegalArgumentException();
                        IptvChannel channel = new IptvChannel(); channel.playlistId = playlist.id; channel.title = label; channel.live = live;
                        String path = (live ? "live" : "movie") + "/" + stream;
                        channel.id = id(playlist.id + "\n" + path); channel.url = PREFIX + playlist.id + "/" + path + "." + extension;
                        channel.groupName = (live ? "Live · " : "Movies · ") + groups.getOrDefault(row.optString("category_id"), "");
                        channel.tvgId = row.isNull("epg_channel_id") ? "" : row.optString("epg_channel_id", ""); if (channel.tvgId.length() > 500 || !seen.add(channel.id)) throw new IllegalArgumentException();
                        channels.add(channel);
                    } catch (Exception invalid) { skipped++; }
                }
            }
            if (entries > 0 && channels.isEmpty()) throw new IOException(); return new Result(playlist, channels, account, skipped);
        } catch (Exception error) { throw new IOException("Account import failed; check server, credentials, expiry and catalogue limits"); }
    }
}
