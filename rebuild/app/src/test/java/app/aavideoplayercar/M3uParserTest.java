package app.aavideoplayercar;
import app.aavideoplayercar.iptv.M3uParser;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
public class M3uParserTest {
    @Test public void documentSourcesRequireAbsoluteNetworkChannelsAndKeepStableIdentity() throws Exception {
        String source = "content://documents/document/playlist%3A1";
        byte[] bytes = "#EXTM3U\n#EXTINF:-1,Network\nhttps://example.com/live\n#EXTINF:-1,Relative\nrelative.m3u8\n".getBytes(StandardCharsets.UTF_8);
        var first = M3uParser.parse(bytes, source, source, "Local");
        var renamed = M3uParser.parse(bytes, source, source, "Renamed");
        assertEquals(source, first.playlist.url); assertEquals(1, first.channels.size()); assertEquals(1, first.skipped);
        assertEquals(first.channels.get(0).id, renamed.channels.get(0).id);
        assertThrows(IOException.class, () -> M3uParser.sourceUrl("file:///private/list.m3u"));
        assertThrows(IOException.class, () -> M3uParser.sourceUrl("content:///missing-authority"));
        assertThrows(IOException.class, () -> M3uParser.sourceUrl("content://documents/item#fragment"));
    }
    private M3uParser.Result parse(String value) throws Exception { return M3uParser.parse(value.getBytes(StandardCharsets.UTF_8), "https://example.com/list", "https://cdn.example.com/folder/list", "TV"); }
    @Test public void attributesQuotedCommasGroupsRelativeUrlsAndDurations() throws Exception {
        var result = parse("\ufeff#EXTM3U\r\n#EXTINF:-1 tvg-id=\"station\" group-title=\"News, local\",My, station\r\nlive.m3u8\r\n#EXTINF:30 tvg-name=\"Fallback\",\n#EXTGRP:Movies\nmovie.mp4\n");
        assertEquals(2, result.channels.size()); assertTrue(result.channels.get(0).live);
        assertEquals("News, local", result.channels.get(0).groupName); assertEquals("My, station", result.channels.get(0).title);
        assertEquals("https://cdn.example.com/folder/live.m3u8", result.channels.get(0).url);
        assertEquals("Fallback", result.channels.get(1).title); assertFalse(result.channels.get(1).live); assertEquals(30000, result.channels.get(1).durationMs);
        assertEquals("Movies", result.channels.get(1).groupName);
    }
    @Test public void invalidUrlsAndDuplicatesAreCountedAndIdentitySurvivesRename() throws Exception {
        var result = parse("#EXTM3U\n#EXTINF:-1,One\nhttps://example.com/live\n#EXTINF:-1,Duplicate\nhttps://example.com/live\n#EXTINF:-1,Private\nfile:///private/file\n#EXTINF:-1,Bad\nhttps://user:password@example.com/live\n#EXTINF:-1,Truncated\n");
        assertEquals(1, result.channels.size()); assertEquals(4, result.skipped);
        var renamed = parse("#EXTM3U\n#EXTINF:-1,Renamed\nhttps://example.com/live\n");
        assertEquals(result.channels.get(0).id, renamed.channels.get(0).id);
        assertThrows(UnsupportedOperationException.class, () -> result.channels.clear());
    }
    @Test public void rejectsHlsNonPlaylistsMalformedUtf8AndExcessiveInput() {
        assertThrows(IOException.class, () -> parse("<html>Error</html>"));
        assertThrows(IOException.class, () -> parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n"));
        assertThrows(IOException.class, () -> M3uParser.parse(new byte[]{(byte)0xff}, "https://example.com", "https://example.com", "TV"));
        assertThrows(IOException.class, () -> M3uParser.parse(new byte[4 * 1024 * 1024 + 1], "https://example.com", "https://example.com", "TV"));
        StringBuilder tooMany = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i <= 10000; i++) tooMany.append("#EXTINF:-1,C\nhttps://example.com/").append(i).append('\n');
        assertThrows(IOException.class, () -> parse(tooMany.toString()));
    }
}
