package app.aavideoplayercar.data;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;
import java.util.List;

@Dao
public abstract class RadioDao {
    @Query("SELECT * FROM radio_stations WHERE id = :id") public abstract RadioStation get(String id);
    @Query("SELECT * FROM radio_stations WHERE favorite = 1 ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    public abstract List<RadioStation> favorites(int limit, int offset);
    @Query("SELECT COUNT(*) FROM radio_stations WHERE favorite = 1") public abstract int count();
    @Query("SELECT * FROM radio_stations WHERE favorite = 1 AND instr(lower(title), lower(:query)) > 0 ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    public abstract List<RadioStation> search(String query, int limit, int offset);
    @Query("SELECT COUNT(*) FROM radio_stations WHERE favorite = 1 AND instr(lower(title), lower(:query)) > 0")
    public abstract int searchCount(String query);
    @Upsert protected abstract void upsert(RadioStation station);
    @Query("DELETE FROM radio_stations WHERE favorite = 0 AND id != :currentId AND id NOT IN (SELECT id FROM radio_stations WHERE favorite = 0 AND id != :currentId ORDER BY updatedAt DESC, id LIMIT 99)")
    protected abstract void prune(String currentId);
    @Query("DELETE FROM radio_stations WHERE id = :id") public abstract void delete(String id);

    /** Refresh metadata without accidentally clearing an existing favorite. */
    @Transaction public void save(RadioStation station, Boolean favorite) {
        RadioStation existing = get(station.id);
        station.favorite = favorite != null ? favorite : existing != null && existing.favorite;
        station.updatedAt = System.currentTimeMillis();
        upsert(station);
        prune(station.id);
    }
}
