package app.aavideoplayercar.data;
import androidx.room.*;
import java.util.List;

@Dao public abstract class MovieAddonDao {
    public static final int MAX_ADDONS = 50;
    @Query("SELECT * FROM movie_addons ORDER BY title COLLATE NOCASE,id LIMIT 50") public abstract List<MovieAddon> all();
    @Query("SELECT * FROM movie_addons WHERE id=:id") public abstract MovieAddon get(String id);
    @Query("SELECT COUNT(*) FROM movie_addons") public abstract int count();
    @Query("DELETE FROM movie_addons WHERE id=:id") public abstract void delete(String id);
    @Upsert protected abstract void upsert(MovieAddon addon);
    /** Refresh cannot resurrect a removed registration; caller uses the existing local ID. */
    @Transaction public boolean save(MovieAddon addon, boolean adding) {
        MovieAddon prior = get(addon.id);
        if (prior == null && !adding) return false;
        if (!addon.id.matches("[a-f0-9-]{36}") || addon.endpoint.isEmpty() || addon.endpoint.length() > 24000
            || addon.providerId.isEmpty() || addon.providerId.length() > 500 || addon.title.trim().isEmpty() || addon.title.length() > 500
            || addon.version.isEmpty() || addon.version.length() > 100 || addon.updatedAt < 0
            || (prior == null && count() >= MAX_ADDONS)) throw new IllegalArgumentException("Invalid add-on registration or registration limit reached");
        upsert(addon); return true;
    }
}
