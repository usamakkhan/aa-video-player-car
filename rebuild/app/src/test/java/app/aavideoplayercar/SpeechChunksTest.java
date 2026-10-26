package app.aavideoplayercar;
import app.aavideoplayercar.news.SpeechChunks;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechChunksTest {
    @Test public void chunksLongTextWithoutLosingWordsOrSplittingSurrogates() {
        String text = ("A long sentence with an emoji \ud83d\ude80. ").repeat(1800);
        var chunks = SpeechChunks.split(text, 3500); assertTrue(chunks.size() > 1); assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) { assertTrue(chunk.length() <= 3500); assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))); }
        assertEquals("a\ud83d\ude80b", String.join("", SpeechChunks.split("a\ud83d\ude80b", 2)));
    }
    @Test public void emptyInputAndLimitsAreHandled() {
        assertTrue(SpeechChunks.split(" \n\t", 2).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SpeechChunks.split("a", 1));
        assertThrows(IllegalArgumentException.class, () -> SpeechChunks.split("x".repeat(65001), 100));
    }
}
