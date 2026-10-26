package app.aavideoplayercar.playback;

import java.util.ArrayList;
import java.util.List;

/** Shared bounds for browse requests and a queue containing the selected item. */
public final class PlaybackLimits {
    public static final int MAX_ITEMS = 500;
    private PlaybackLimits() { }

    public static int pageSize(int requested) {
        if (requested < 1) throw new IllegalArgumentException("Invalid page size");
        return Math.min(requested, MAX_ITEMS);
    }

    public static int offset(int page, int requested) {
        long offset = (long) page * pageSize(requested);
        if (page < 0 || offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid page");
        return (int) offset;
    }

    public static List<String> queue(List<String> ids, String selected) {
        int selectedIndex = ids.indexOf(selected);
        int start = selectedIndex < 0 ? 0 : (selectedIndex / MAX_ITEMS) * MAX_ITEMS;
        int capacity = selectedIndex < 0 ? MAX_ITEMS - 1 : MAX_ITEMS;
        List<String> result = new ArrayList<>(ids.subList(start, Math.min(ids.size(), start + capacity)));
        if (selectedIndex < 0) result.add(selected);
        return result;
    }
}
