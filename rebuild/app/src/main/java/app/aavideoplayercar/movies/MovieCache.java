package app.aavideoplayercar.movies;

import app.aavideoplayercar.data.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Repository-worker cache. Keys bind results to the configured endpoint without storing its secret path. */
final class MovieCache {
    private MovieCache() {}
    static String key(String addonId, String url, String kind, String identity) throws IOException {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest((addonId + "\n" + url + "\n" + kind + "\n" + identity).getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray(); StringBuilder result = new StringBuilder(64); for (byte b : bytes) result.append(hex[(b >> 4) & 15]).append(hex[b & 15]); return result.toString();
        } catch (Exception error) { throw new IOException("Cache unavailable"); }
    }
    static String catalogIdentity(AddonClient.Catalog catalog, Map<String,String> fields, int offset) {
        StringBuilder identity = new StringBuilder(catalog.type).append('\n').append(catalog.id).append('\n').append(offset);
        for (var field : new TreeMap<>(fields).entrySet()) if (!field.getKey().equals("skip")) identity.append('\n').append(field.getKey().length()).append(':').append(field.getKey()).append(field.getValue().length()).append(':').append(field.getValue());
        return identity.toString();
    }
    static void save(MovieCacheDao dao, String addonId, String key, String payload) {
        int bytes = payload.getBytes(StandardCharsets.UTF_8).length; if (bytes > 1048576) return;
        MovieCacheEntry entry = new MovieCacheEntry(); entry.id = key; entry.addonId = addonId; entry.payload = payload; entry.bytes = bytes; entry.updatedAt = System.currentTimeMillis(); dao.save(entry);
    }
    static String get(MovieCacheDao dao, String addonId, String key) {
        MovieCacheEntry entry = dao.get(key, addonId); return entry == null ? null : entry.payload;
    }
}
