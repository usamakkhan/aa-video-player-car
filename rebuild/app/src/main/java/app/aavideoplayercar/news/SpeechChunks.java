package app.aavideoplayercar.news;
import java.util.*;
public final class SpeechChunks {
    private SpeechChunks() {}
    public static List<String> split(String text, int limit) {
        if (limit < 2 || text.length() > 65000) throw new IllegalArgumentException("Speech text limit");
        List<String> chunks = new ArrayList<>(); int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
                for (int i = end - 1; i > start + limit / 2; i--) if (Character.isWhitespace(text.charAt(i))) { end = i + 1; break; }
            }
            String chunk = text.substring(start, end); if (!chunk.trim().isEmpty()) chunks.add(chunk); start = end;
        }
        return chunks;
    }
}
