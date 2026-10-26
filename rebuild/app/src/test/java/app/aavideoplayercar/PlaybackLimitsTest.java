package app.aavideoplayercar;

import app.aavideoplayercar.playback.PlaybackLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackLimitsTest {
    private List<String> ids(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add("item-" + i);
        return result;
    }
    @Test public void selectedItemOutsideFullQueueRemainsPlayable() {
        List<String> source = ids(500);
        List<String> queue = PlaybackLimits.queue(source, "new-item");
        assertEquals(500, queue.size());
        assertEquals("new-item", queue.get(499));
        assertEquals(500, source.size());
        assertFalse(source.contains("new-item"));
    }
    @Test public void selectionBeyondFirstWindowIsIncludedOnce() {
        List<String> queue = PlaybackLimits.queue(ids(1200), "item-999");
        assertEquals(500, queue.size());
        assertEquals("item-500", queue.get(0));
        assertEquals("item-999", queue.get(499));
        assertEquals(1, java.util.Collections.frequency(queue, "item-999"));
    }
    @Test public void emptyLibraryCanPlayImportedItem() {
        assertEquals(java.util.Collections.singletonList("new-item"), PlaybackLimits.queue(ids(0), "new-item"));
    }
    @Test public void oversizedPagesRemainContiguous() {
        assertEquals(500, PlaybackLimits.pageSize(501));
        assertEquals(500, PlaybackLimits.offset(1, 501));
        assertEquals(1000, PlaybackLimits.offset(2, Integer.MAX_VALUE));
        assertEquals(150, PlaybackLimits.offset(3, 50));
    }
    @Test public void invalidPagesCannotOverflow() {
        assertThrows(IllegalArgumentException.class, () -> PlaybackLimits.offset(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> PlaybackLimits.offset(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PlaybackLimits.offset(Integer.MAX_VALUE, 500));
    }
}
