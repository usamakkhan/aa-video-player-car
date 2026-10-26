package app.aavideoplayercar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable IDs recovered from dashboard-modules.csv; labels never determine routing. */
public final class ModuleRegistry {
    public enum Route { WEB, VIDEO, IMAGE, AUDIO, FILE, SPEED, SETTINGS, MAINTENANCE, RADIO, PODCAST, IPTV, NEWS, MOVIES, UNAVAILABLE }
    public static final class Module {
        public final int id;
        public final String title;
        public final Route route;
        public final String url;
        private Module(int id, String title, Route route, String url) {
            this.id = id; this.title = title; this.route = route; this.url = url;
        }
    }
    public static final List<Module> DEFAULTS = Collections.unmodifiableList(java.util.Arrays.asList(
        new Module(16, "YouTube", Route.WEB, "https://m.youtube.com"),
        new Module(5, "Web Browser", Route.WEB, "https://www.google.com"),
        new Module(6, "TV / IPTV", Route.IPTV, null),
        new Module(18, "Movies & Series", Route.MOVIES, null),
        new Module(1, "File Manager", Route.FILE, null),
        new Module(2, "Video Player", Route.VIDEO, null),
        new Module(3, "Photo Gallery", Route.IMAGE, null),
        new Module(9, "Radio", Route.RADIO, null),
        new Module(10, "Music", Route.AUDIO, null),
        new Module(11, "Speedometer", Route.SPEED, null),
        new Module(13, "Podcast", Route.PODCAST, null),
        new Module(17, "News", Route.NEWS, null),
        new Module(8, "Settings", Route.SETTINGS, null),
        new Module(12, "Screen Mirror", Route.UNAVAILABLE, null),
        new Module(14, "YouTube Kids", Route.WEB, "https://www.youtubekids.com"),
        new Module(4, "YouTube Web", Route.WEB, "https://m.youtube.com"),
        new Module(15, "YouTube Music", Route.WEB, "https://music.youtube.com"),
        new Module(19, "YouTube Classic", Route.WEB, "https://m.youtube.com"),
        new Module(20, "Vehicle Maintenance", Route.MAINTENANCE, null)
    ));
    public static Module find(int id) {
        for (Module module : DEFAULTS) if (module.id == id) return module;
        return null;
    }
    /** Tolerate stale, duplicate and malformed IDs without losing newly added modules. */
    public static List<Module> ordered(String saved) {
        List<Module> result = new ArrayList<>();
        if (saved != null) for (String token : saved.split(",")) {
            try {
                Module module = find(Integer.parseInt(token.trim()));
                if (module != null && !result.contains(module)) result.add(module);
            } catch (NumberFormatException ignored) { }
        }
        for (Module module : DEFAULTS) if (!result.contains(module)) result.add(module);
        return result;
    }
    public static String encode(List<Module> modules) {
        StringBuilder result = new StringBuilder();
        for (Module module : modules) {
            if (result.length() > 0) result.append(',');
            result.append(module.id);
        }
        return result.toString();
    }
    private ModuleRegistry() { }
}
