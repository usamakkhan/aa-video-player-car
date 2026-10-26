package app.aavideoplayercar.data;
import androidx.room.*;
import java.util.List;
@Dao public abstract class EpgDao {
    @Query("SELECT * FROM epg_sources WHERE playlistId=:playlist") public abstract EpgSource source(String playlist);
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE playlistId=:playlist AND channelId=:channel") public abstract int count(String playlist, String channel);
    @Query("SELECT * FROM epg_programmes WHERE playlistId=:playlist AND channelId=:channel ORDER BY startMs,id LIMIT :limit OFFSET :offset")
    public abstract List<EpgProgramme> page(String playlist, String channel, int limit, int offset);
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE playlistId=:playlist AND channelId=:channel AND (endMs>:now OR startMs>=:now)")
    public abstract int upcomingCount(String playlist, String channel, long now);
    @Query("SELECT * FROM epg_programmes WHERE playlistId=:playlist AND channelId=:channel AND (endMs>:now OR startMs>=:now) ORDER BY startMs,id LIMIT :limit OFFSET :offset")
    public abstract List<EpgProgramme> upcomingPage(String playlist, String channel, long now, int limit, int offset);
    @Query("SELECT COUNT(*) FROM iptv_playlists WHERE id=:playlist") protected abstract int exists(String playlist);
    @Query("DELETE FROM epg_programmes WHERE playlistId=:playlist") protected abstract void clear(String playlist);
    @Upsert protected abstract void save(EpgSource source);
    @Insert protected abstract void insert(List<EpgProgramme> programmes);
    @Transaction public boolean replace(EpgSource source, List<EpgProgramme> programmes) {
        if (exists(source.playlistId) == 0) return false;
        if (programmes.size() > 10000 || programmes.isEmpty()) throw new IllegalArgumentException("Invalid guide size");
        for (EpgProgramme item : programmes) if (!source.playlistId.equals(item.playlistId)) throw new IllegalArgumentException("Wrong playlist");
        save(source); clear(source.playlistId); insert(programmes); return true;
    }
}
