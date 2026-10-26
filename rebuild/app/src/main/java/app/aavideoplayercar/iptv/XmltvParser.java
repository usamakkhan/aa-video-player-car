package app.aavideoplayercar.iptv;

import android.util.Xml;
import app.aavideoplayercar.data.EpgProgramme;
import app.aavideoplayercar.network.HttpDocument;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Bounded XMLTV schedule import. Channel IDs match M3U tvg-id exactly. */
public final class XmltvParser {
    public static final class Result {
        public final List<EpgProgramme> programmes;
        public final int skipped;
        Result(List<EpgProgramme> programmes, int skipped) { this.programmes = Collections.unmodifiableList(programmes); this.skipped = skipped; }
    }
    public static long timestamp(String input) {
        String[] parts = input.trim().split("\\s+"); String digits = parts[0];
        if (parts.length > 2 || !digits.matches("[0-9]{4}([0-9]{2}){0,5}")) throw new IllegalArgumentException("Invalid XMLTV time");
        int[] values = {0, 1, 1, 0, 0, 0}; values[0] = Integer.parseInt(digits.substring(0, 4));
        for (int i = 1; i < values.length && digits.length() >= 4 + i * 2; i++) values[i] = Integer.parseInt(digits.substring(2 + i * 2, 4 + i * 2));
        String zone = parts.length == 1 ? "Z" : parts[1];
        if (zone.equals("UTC") || zone.equals("GMT")) zone = "Z";
        if (zone.equals("BST")) zone = "+0100";
        if (!zone.equals("Z") && !zone.matches("[+-][0-9]{4}")) throw new IllegalArgumentException("Unsupported XMLTV offset");
        return LocalDateTime.of(values[0], values[1], values[2], values[3], values[4], values[5]).toInstant(ZoneOffset.of(zone)).toEpochMilli();
    }
    public static Result parse(byte[] bytes, String playlistId) throws IOException {
        if (bytes.length > HttpDocument.MAX_BYTES) throw new IOException("Guide too large");
        List<EpgProgramme> programmes = new ArrayList<>(); Set<String> seen = new HashSet<>();
        int count = 0, skipped = 0; boolean root = false, rootClosed = false; EpgProgramme current = null;
        try {
            XmlPullParser parser = Xml.newPullParser(); parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false); parser.setInput(new ByteArrayInputStream(bytes), null);
            for (int token = parser.nextToken(); token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Guide parsing cancelled");
                if (parser.getDepth() > 32) throw new IOException("Guide nesting too deep");
                // Accept the common external XMLTV DTD declaration, but never load it or allow an internal subset.
                if (token == XmlPullParser.DOCDECL && !parser.getText().trim().matches("tv\\s+SYSTEM\\s+(\"[^\"<>\\[\\]]*\"|'[^'<>\\[\\]]*')\\s*")) throw new IOException("Unsupported XML declaration");
                if (token == XmlPullParser.ENTITY_REF && parser.getText() == null) throw new IOException("Unresolved entity");
                if (token == XmlPullParser.END_TAG && parser.getDepth() == 1 && "tv".equals(parser.getName())) rootClosed = true;
                if (token == XmlPullParser.START_TAG) {
                    int depth = parser.getDepth(); String tag = parser.getName();
                    if (depth == 1) { if (root || !"tv".equals(tag) || !parser.getNamespace().isEmpty()) throw new IOException("Not XMLTV"); root = true; }
                    if (depth == 2 && "programme".equals(tag) && parser.getNamespace().isEmpty()) {
                        if (++count > 10000) throw new IOException("Too many programmes");
                        current = new EpgProgramme(); current.playlistId = playlistId;
                        try {
                            current.channelId = Objects.requireNonNull(parser.getAttributeValue(null, "channel")).trim();
                            if (current.channelId.isEmpty() || current.channelId.length() > 500) throw new IllegalArgumentException();
                            current.startMs = timestamp(Objects.requireNonNull(parser.getAttributeValue(null, "start")));
                            String end = parser.getAttributeValue(null, "stop"); current.endMs = end == null ? 0 : timestamp(end);
                            if (end != null && current.endMs <= current.startMs) throw new IllegalArgumentException();
                        } catch (RuntimeException invalid) { current = null; skipped++; }
                    } else if (depth == 3 && current != null && parser.getNamespace().isEmpty()) {
                        if ("title".equals(tag)) { String title = text(parser, 500); if (current.title.isEmpty()) current.title = title; }
                        else if ("desc".equals(tag)) { String description = text(parser, 8000); if (current.description.isEmpty()) current.description = description; }
                    }
                } else if (token == XmlPullParser.END_TAG && parser.getDepth() == 2 && "programme".equals(parser.getName()) && current != null) {
                    current.id = UUID.nameUUIDFromBytes((playlistId + "\n" + current.channelId + "\n" + current.startMs + "\n" + current.title).getBytes(StandardCharsets.UTF_8)).toString();
                    if (current.title.isEmpty() || !seen.add(current.id)) skipped++; else programmes.add(current);
                    current = null;
                }
            }
            if (!root || !rootClosed || programmes.isEmpty()) throw new IOException("No complete usable guide");
            return new Result(programmes, skipped);
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Invalid XMLTV", error); }
    }
    private static String text(XmlPullParser parser, int maximum) throws Exception {
        StringBuilder value = new StringBuilder(); int depth = parser.getDepth();
        for (int token = parser.nextToken(); !(token == XmlPullParser.END_TAG && parser.getDepth() == depth); token = parser.nextToken()) {
            if (token == XmlPullParser.END_DOCUMENT || token == XmlPullParser.START_TAG || token == XmlPullParser.DOCDECL) throw new IOException("Invalid guide text");
            if (token == XmlPullParser.TEXT || token == XmlPullParser.CDSECT || token == XmlPullParser.ENTITY_REF) {
                if (parser.getText() == null) throw new IOException("Unresolved entity");
                if (value.length() + parser.getText().length() > maximum) throw new IOException("Guide text too long"); value.append(parser.getText());
            }
        }
        return value.toString().trim();
    }
}
