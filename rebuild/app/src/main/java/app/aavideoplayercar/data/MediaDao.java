package app.aavideoplayercar.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insert(MediaEntry entry);
    @Query("SELECT * FROM media WHERE id = :id") MediaEntry get(String id);
    @Query("SELECT * FROM media WHERE mime LIKE :mimePrefix || '%' ORDER BY addedAt DESC, id LIMIT :limit OFFSET :offset")
    List<MediaEntry> list(String mimePrefix, int limit, int offset);
    @Query("SELECT COUNT(*) FROM media WHERE mime LIKE :mimePrefix || '%'") int count(String mimePrefix);
    @Query("SELECT * FROM media WHERE mime LIKE 'audio/%' AND instr(lower(title), lower(:query)) > 0 ORDER BY title, id LIMIT :limit OFFSET :offset")
    List<MediaEntry> searchAudio(String query, int limit, int offset);
    @Query("SELECT COUNT(*) FROM media WHERE mime LIKE 'audio/%' AND instr(lower(title), lower(:query)) > 0") int searchCount(String query);
    @Query("UPDATE media SET positionMs = :position, durationMs = :duration, lastPlayedAt = :time WHERE id = :id")
    void progress(String id, long position, long duration, long time);
    @Query("DELETE FROM media WHERE id = :id") void remove(String id);
}
