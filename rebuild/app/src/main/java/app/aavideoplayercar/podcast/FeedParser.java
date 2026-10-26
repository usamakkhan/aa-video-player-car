package app.aavideoplayercar.podcast;

import android.util.Xml;
import app.aavideoplayercar.data.PodcastFeed;
import app.aavideoplayercar.data.PodcastEpisode;
import app.aavideoplayercar.network.HttpDocument;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** RSS 2.0 and Atom audio enclosures. No HTML rendering or external entity resolution. */
public final class FeedParser {
    private static final String ATOM = "http://www.w3.org/2005/Atom", XML = "http://www.w3.org/XML/1998/namespace";
    public static final class Result {
        public final PodcastFeed feed;
        public final List<PodcastEpisode> episodes;
        Result(PodcastFeed feed, List<PodcastEpisode> episodes) { this.feed = feed; this.episodes = episodes; }
    }
    private static String identity(String text) { return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString(); }
    public static Result parse(byte[] bytes, String subscribedUrl, String documentUrl) throws IOException {
        if (bytes.length > HttpDocument.MAX_BYTES) throw new IOException("Feed exceeds the 4 MiB limit.");
        PodcastFeed feed = new PodcastFeed(); feed.url = HttpDocument.url(subscribedUrl); feed.id = identity(feed.url);
        feed.updatedAt = System.currentTimeMillis();
        List<PodcastEpisode> episodes = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        ArrayDeque<URI> bases = new ArrayDeque<>(); bases.push(URI.create(HttpDocument.url(documentUrl)));
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            parser.setInput(new ByteArrayInputStream(bytes), null);
            boolean atom = false, recognized = false, channel = false, channelSeen = false;
            PodcastEpisode episode = null; String guid = ""; int itemDepth = -1, itemCount = 0;
            for (int token = parser.nextToken(); token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Feed parsing cancelled.");
                if (token == XmlPullParser.DOCDECL) throw new IOException("Feeds with document type declarations are unsupported.");
                if (parser.getDepth() > 64) throw new IOException("Feed nesting is too deep.");
                if (token == XmlPullParser.START_TAG) {
                    int depth = parser.getDepth(); String tag = parser.getName(), ns = parser.getNamespace();
                    String xmlBase = parser.getAttributeValue(XML, "base");
                    bases.push(xmlBase == null ? bases.peek() : bases.peek().resolve(xmlBase));
                    if (depth == 1) {
                        if (recognized) throw new IOException("Multiple feed roots.");
                        atom = "feed".equals(tag) && ATOM.equals(ns);
                        recognized = atom || ("rss".equals(tag) && ns.isEmpty());
                        if (!recognized) throw new IOException("This document is not an RSS or Atom feed.");
                    }
                    if (!atom && depth == 2 && "channel".equals(tag) && ns.isEmpty()) { channel = true; channelSeen = true; }
                    boolean standard = atom ? ATOM.equals(ns) : ns.isEmpty();
                    if (standard && ((atom && depth == 2 && "entry".equals(tag)) || (!atom && channel && depth == 3 && "item".equals(tag)))) {
                        if (++itemCount > 2000) throw new IOException("Feed exceeds the 2,000-item limit.");
                        episode = new PodcastEpisode(); episode.feedId = feed.id; guid = ""; itemDepth = depth;
                    } else if (episode == null && standard && "title".equals(tag) && depth == (atom ? 2 : 3)) {
                        feed.title = text(parser, 500); bases.pop();
                    } else if (episode != null && depth == itemDepth + 1) {
                        if (standard && "title".equals(tag)) { episode.title = text(parser, 500); bases.pop(); }
                        else if (standard && ("guid".equals(tag) || "id".equals(tag))) { guid = text(parser, 8192); bases.pop(); }
                        else if (standard && ("pubDate".equals(tag) || "published".equals(tag) || ("updated".equals(tag) && episode.publishedAt == 0))) {
                            episode.publishedAt = date(text(parser, 200)); bases.pop();
                        } else if ("duration".equals(tag) && "http://www.itunes.com/dtds/podcast-1.0.dtd".equals(ns)) {
                            episode.durationMs = duration(text(parser, 100)); bases.pop();
                        } else if (standard && ((!atom && "enclosure".equals(tag)) || (atom && "link".equals(tag) && "enclosure".equals(parser.getAttributeValue(null, "rel"))))) {
                            String type = parser.getAttributeValue(null, "type");
                            if (type != null) type = type.trim().toLowerCase(java.util.Locale.ROOT);
                            String url = parser.getAttributeValue(null, atom ? "href" : "url");
                            if (episode.url.isEmpty() && url != null && (type == null || type.startsWith("audio/") || type.equals("application/octet-stream") || type.equals("application/x-mpegURL"))) {
                                try {
                                    episode.url = HttpDocument.url(bases.peek().resolve(url).toString());
                                    episode.mime = type == null || type.equals("application/octet-stream") ? "" : type;
                                } catch (Exception ignored) { /* One invalid enclosure must not hide valid episodes. */ }
                            }
                        }
                    }
                } else if (token == XmlPullParser.END_TAG) {
                    if (episode != null && parser.getDepth() == itemDepth) {
                        if (!episode.url.isEmpty()) {
                            episode.id = identity(feed.id + "\n" + (guid.isEmpty() ? episode.url : guid));
                            if (episode.title.isEmpty()) episode.title = "Untitled episode";
                            if (seen.add(episode.id)) episodes.add(episode);
                        }
                        episode = null;
                    }
                    if (!atom && parser.getDepth() == 2 && "channel".equals(parser.getName())) channel = false;
                    bases.pop();
                }
            }
            if (!recognized || bases.size() != 1 || (!atom && !channelSeen)) throw new IOException("Empty or incomplete feed.");
            if (feed.title.isEmpty()) feed.title = URI.create(feed.url).getHost();
            return new Result(feed, episodes);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Feed XML is invalid or unsupported.", error); }
    }
    private static String text(XmlPullParser parser, int max) throws Exception {
        int depth = parser.getDepth(); StringBuilder value = new StringBuilder();
        for (int token = parser.nextToken(); !(token == XmlPullParser.END_TAG && parser.getDepth() == depth); token = parser.nextToken()) {
            if (token == XmlPullParser.END_DOCUMENT || token == XmlPullParser.DOCDECL || parser.getDepth() > 64)
                throw new IOException("Invalid feed text.");
            if (token == XmlPullParser.TEXT || token == XmlPullParser.CDSECT || token == XmlPullParser.ENTITY_REF) {
                String part = parser.getText(); if (part == null) throw new IOException("Unresolved XML entity.");
                if (value.length() + part.length() > 8192) throw new IOException("Feed field is too long.");
                value.append(part);
            }
        }
        String result = value.toString().trim(); return result.substring(0, Math.min(max, result.length()));
    }
    private static long date(String value) {
        try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { }
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); } catch (Exception ignored) { return 0; }
    }
    private static long duration(String value) {
        try {
            String[] parts = value.split(":"); if (parts.length > 3) return 0;
            long seconds = 0;
            for (String part : parts) {
                if (!part.matches("[0-9]{1,7}")) return 0;
                seconds = Math.addExact(Math.multiplyExact(seconds, 60), Long.parseLong(part));
            }
            return seconds <= 604800 ? seconds * 1000 : 0;
        } catch (Exception error) { return 0; }
    }
}
