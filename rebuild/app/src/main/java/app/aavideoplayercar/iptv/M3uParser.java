package app.aavideoplayercar.iptv;

import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import java.io.*;
import java.net.URI;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Extended M3U channel lists; HLS manifests are stream targets, never channel lists. */
public final class M3uParser {
    public static final int MAX_CHANNELS = 10000;
    private static final Pattern ATTRIBUTE = Pattern.compile("(?:^|\\s)([\\w-]+)\\s*=\\s*\"([^\"]*)\"");
    public static final class Result {
        public final IptvPlaylist playlist; public final List<IptvChannel> channels; public final int skipped;
        Result(IptvPlaylist playlist, List<IptvChannel> channels, int skipped) { this.playlist = playlist; this.channels = Collections.unmodifiableList(channels); this.skipped = skipped; }
    }
    private static String id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString(); }
    public static String sourceUrl(String value) throws IOException {
        try {
            URI uri = URI.create(value.trim());
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                if (value.length() > 8192 || uri.getRawAuthority() == null || uri.getUserInfo() != null || uri.getRawFragment() != null) throw new IOException("Invalid document URI");
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid playlist URI", invalid); }
        return HttpDocument.url(value);
    }
    public static Result parse(byte[] bytes, String source, String resolved, String title) throws IOException {
        if (bytes.length > HttpDocument.MAX_BYTES) throw new IOException("Playlist too large");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new IOException("Playlist is not UTF-8", invalid); }
        IptvPlaylist playlist = new IptvPlaylist(); playlist.url = sourceUrl(source); playlist.id = id(playlist.url);
        playlist.title = title.trim(); if (playlist.title.isEmpty() || playlist.title.length() > 200) throw new IOException("Enter a playlist name");
        playlist.updatedAt = System.currentTimeMillis(); URI base = URI.create(sourceUrl(resolved));
        List<IptvChannel> channels = new ArrayList<>(); Set<String> seen = new HashSet<>();
        boolean header = false; String pending = null, group = ""; int skipped = 0, entries = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 16384) throw new IOException("Playlist line too long");
                line = line.trim(); if (!header && line.startsWith("\ufeff")) line = line.substring(1);
                if (line.isEmpty()) continue;
                if (!header) { if (!line.equals("#EXTM3U") && !line.startsWith("#EXTM3U ")) throw new IOException("Not an extended M3U playlist"); header = true; continue; }
                if (line.startsWith("#EXT-X-")) throw new IOException("This is an HLS stream, not a channel list");
                if (line.startsWith("#EXTINF:")) { if (pending != null) skipped++; pending = line.substring(8); group = ""; continue; }
                if (line.startsWith("#EXTGRP:")) { group = line.substring(8).trim(); continue; }
                if (line.startsWith("#")) continue;
                if (++entries > MAX_CHANNELS) throw new IOException("Too many playlist entries");
                String info = pending; pending = null;
                if (info == null) { skipped++; group = ""; continue; }
                try {
                    boolean quote = false; int comma = -1;
                    for (int i = 0; i < info.length(); i++) { char c = info.charAt(i); if (c == '"') quote = !quote; if (c == ',' && !quote) { comma = i; break; } }
                    if (comma < 0) throw new IllegalArgumentException();
                    String attributes = info.substring(0, comma), label = info.substring(comma + 1).trim();
                    Map<String,String> values = new HashMap<>(); Matcher matcher = ATTRIBUTE.matcher(attributes);
                    while (matcher.find()) values.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
                    if (label.isEmpty()) label = values.getOrDefault("tvg-name", "");
                    if (label.isEmpty() || label.length() > 500) throw new IllegalArgumentException();
                    IptvChannel channel = new IptvChannel(); channel.playlistId = playlist.id;
                    channel.url = HttpDocument.url(base.resolve(line).toString()); channel.id = id(playlist.id + "\n" + channel.url);
                    channel.title = label; channel.groupName = values.getOrDefault("group-title", group); channel.tvgId = values.getOrDefault("tvg-id", "");
                    if (channel.groupName.length() > 500 || channel.tvgId.length() > 500) throw new IllegalArgumentException();
                    String duration = attributes.trim().split("\\s+", 2)[0]; double seconds = Double.parseDouble(duration);
                    if (!Double.isFinite(seconds) || seconds > Long.MAX_VALUE / 1000.0) throw new IllegalArgumentException();
                    channel.live = seconds <= 0; channel.durationMs = channel.live ? 0 : (long)(seconds * 1000);
                    if (seen.add(channel.id)) channels.add(channel); else skipped++;
                } catch (Exception invalid) { skipped++; }
                group = "";
            }
        }
        if (pending != null) skipped++;
        if (!header || channels.isEmpty()) throw new IOException("No supported channels in playlist");
        return new Result(playlist, channels, skipped);
    }
}
