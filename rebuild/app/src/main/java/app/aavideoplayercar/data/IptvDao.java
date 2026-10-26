package app.aavideoplayercar.data;
import androidx.room.*;
import java.util.*;
@Dao public abstract class IptvDao {
    @Query("SELECT * FROM iptv_playlists ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset") public abstract List<IptvPlaylist> playlists(int limit, int offset);
    @Query("SELECT COUNT(*) FROM iptv_playlists") public abstract int playlistCount();
    @Query("SELECT * FROM iptv_playlists WHERE id=:id") public abstract IptvPlaylist playlist(String id);
    @Query("SELECT * FROM iptv_channels WHERE id=:id") public abstract IptvChannel channel(String id);
    @Query("SELECT * FROM iptv_channels WHERE playlistId=:id AND id NOT IN (SELECT channelId FROM iptv_series_episodes) AND (:favorites=0 OR favorite=1) AND (instr(lower(title),lower(:query))>0 OR instr(lower(groupName),lower(:query))>0) ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    public abstract List<IptvChannel> channels(String id, String query, boolean favorites, int limit, int offset);
    @Query("SELECT COUNT(*) FROM iptv_channels WHERE playlistId=:id AND id NOT IN (SELECT channelId FROM iptv_series_episodes) AND (:favorites=0 OR favorite=1) AND (instr(lower(title),lower(:query))>0 OR instr(lower(groupName),lower(:query))>0)")
    public abstract int count(String id, String query, boolean favorites);
    @Query("UPDATE iptv_channels SET favorite=NOT favorite WHERE id=:id") public abstract void toggleFavorite(String id);
    @Query("UPDATE iptv_channels SET playbackMode=:mode WHERE id=:id") protected abstract void updateMode(String id, int mode);
    public void playbackMode(String id, int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid playback type");
        updateMode(id, mode);
    }
    @Query("UPDATE iptv_channels SET positionMs=:position, durationMs=:duration, lastPlayedAt=:time WHERE id=:id") public abstract void progress(String id, long position, long duration, long time);
    @Query("DELETE FROM iptv_playlists WHERE id=:id") public abstract void delete(String id);
    @Query("DELETE FROM iptv_channels WHERE playlistId=:id AND id NOT IN (SELECT channelId FROM iptv_series_episodes)") protected abstract void clearChannels(String id);
    @Upsert protected abstract void savePlaylist(IptvPlaylist playlist);
    @Insert protected abstract void insertChannels(List<IptvChannel> channels);
    @Upsert protected abstract void upsertChannel(IptvChannel channel);
    @Query("SELECT * FROM iptv_series WHERE id=:id") public abstract IptvSeries series(String id);
    @Query("SELECT * FROM iptv_series WHERE playlistId=:playlist AND instr(lower(title),lower(:query))>0 ORDER BY title COLLATE NOCASE,id LIMIT :limit OFFSET :offset")
    public abstract List<IptvSeries> seriesPage(String playlist, String query, int limit, int offset);
    @Query("SELECT COUNT(*) FROM iptv_series WHERE playlistId=:playlist AND instr(lower(title),lower(:query))>0") public abstract int seriesCount(String playlist, String query);
    @Query("SELECT DISTINCT season FROM iptv_series_episodes WHERE seriesId=:id ORDER BY season") public abstract List<Integer> seasons(String id);
    @Query("SELECT c.* FROM iptv_channels c JOIN iptv_series_episodes e ON c.id=e.channelId WHERE e.seriesId=:id AND e.season=:season ORDER BY e.number,c.id LIMIT :limit OFFSET :offset")
    public abstract List<IptvChannel> episodePage(String id, int season, int limit, int offset);
    @Query("SELECT COUNT(*) FROM iptv_series_episodes WHERE seriesId=:id AND season=:season") public abstract int episodeCount(String id, int season);
    @Query("SELECT * FROM iptv_series_episodes WHERE channelId=:id") protected abstract IptvSeriesEpisode membership(String id);
    @Query("SELECT * FROM iptv_series_episodes WHERE seriesId=:id") protected abstract List<IptvSeriesEpisode> memberships(String id);
    @Query("DELETE FROM iptv_channels WHERE id=:id") protected abstract void deleteChannel(String id);
    @Query("DELETE FROM iptv_series WHERE id=:id") protected abstract void deleteSeries(String id);
    @Upsert protected abstract void saveSeries(IptvSeries series);
    @Upsert protected abstract void saveMembership(IptvSeriesEpisode episode);

    /** Refresh only series metadata. Removed shows and their cached episodes are pruned atomically. */
    @Transaction public boolean replaceSeries(String playlistId, List<IptvSeries> rows) {
        if (playlist(playlistId) == null) return false;
        if (rows.size() > 10000) throw new IllegalArgumentException("Series limit exceeded");
        List<IptvSeries> previous = seriesPage(playlistId, "", 10000, 0);
        Set<String> retained = new HashSet<>();
        for (IptvSeries row : rows) {
            IptvSeries old = series(row.id);
            if (!playlistId.equals(row.playlistId) || row.id.isEmpty() || !retained.add(row.id) || (old != null && !old.playlistId.equals(playlistId))) throw new IllegalArgumentException("Wrong or duplicate series");
            row.updatedAt = old == null ? 0 : old.updatedAt;
            saveSeries(row);
        }
        for (IptvSeries old : previous) if (!retained.contains(old.id)) {
            for (IptvSeriesEpisode episode : memberships(old.id)) deleteChannel(episode.channelId);
            deleteSeries(old.id);
        }
        return true;
    }
    /** Episode IDs are shared with playback, while membership separates them from live/movie refresh. */
    @Transaction public boolean replaceEpisodes(String seriesId, List<IptvChannel> rows, List<IptvSeriesEpisode> members, long updatedAt) {
        IptvSeries show = series(seriesId); if (show == null) return false;
        if (rows.size() > 10000 || rows.size() != members.size()) throw new IllegalArgumentException("Invalid episode batch");
        Set<String> retained = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            IptvChannel row = rows.get(i); IptvSeriesEpisode member = members.get(i);
            IptvChannel old = channel(row.id); IptvSeriesEpisode oldMember = membership(row.id);
            if (!show.playlistId.equals(row.playlistId) || row.id.isEmpty() || row.live || !retained.add(row.id)
                || !seriesId.equals(member.seriesId) || !row.id.equals(member.channelId) || member.season < 0 || member.number < 0
                || (old != null && (oldMember == null || !seriesId.equals(oldMember.seriesId)))) throw new IllegalArgumentException("Wrong or duplicate episode");
            retainState(row, old); upsertChannel(row); saveMembership(member);
        }
        for (IptvSeriesEpisode old : memberships(seriesId)) if (!retained.contains(old.channelId)) deleteChannel(old.channelId);
        show.updatedAt = updatedAt; saveSeries(show); return true;
    }
    private static void retainState(IptvChannel channel, IptvChannel old) {
        if (old == null) return;
        channel.favorite = old.favorite; channel.playbackMode = old.playbackMode;
        if (!channel.isLive() && !old.isLive()) { channel.positionMs = old.positionMs; channel.lastPlayedAt = old.lastPlayedAt; if (old.durationMs > 0) channel.durationMs = old.durationMs; }
    }
    @Transaction public boolean replace(IptvPlaylist playlist, List<IptvChannel> channels, boolean adding) {
        if (!adding && playlist(playlist.id) == null) return false;
        Map<String, IptvChannel> previous = new HashMap<>();
        for (IptvChannel old : channels(playlist.id, "", false, 10000, 0)) previous.put(old.id, old);
        for (IptvChannel channel : channels) {
            if (!playlist.id.equals(channel.playlistId)) throw new IllegalArgumentException("Wrong playlist");
            IptvChannel old = previous.get(channel.id);
            retainState(channel, old);
        }
        savePlaylist(playlist); clearChannels(playlist.id); insertChannels(channels); return true;
    }
}
