package app.aavideoplayercar.data;

import androidx.room.*;

@Dao public abstract class MovieCacheDao {
    @Query("SELECT * FROM movie_cache WHERE id=:id AND addonId=:addonId") public abstract MovieCacheEntry get(String id, String addonId);
    @Query("SELECT COUNT(*) FROM movie_cache") protected abstract int count();
    @Query("SELECT COALESCE(SUM(bytes),0) FROM movie_cache") protected abstract long bytes();
    @Query("DELETE FROM movie_cache WHERE id=(SELECT id FROM movie_cache ORDER BY updatedAt,id LIMIT 1)") protected abstract void deleteOldest();
    @Upsert protected abstract void upsert(MovieCacheEntry entry);
    @Transaction public void save(MovieCacheEntry entry) {
        if (entry.bytes < 1 || entry.bytes > 1048576 || entry.payload.length() > 1048576 || entry.updatedAt < 0 || entry.id.length() != 64) return;
        upsert(entry);
        while (count() > 32 || bytes() > 8388608) deleteOldest();
    }
}
