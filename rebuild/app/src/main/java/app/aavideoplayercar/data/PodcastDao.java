package app.aavideoplayercar.data;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;
import java.util.List;

@Dao
public abstract class PodcastDao {
    @Query("SELECT * FROM podcast_feeds ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset") public abstract List<PodcastFeed> feeds(int limit, int offset);
    @Query("SELECT COUNT(*) FROM podcast_feeds") public abstract int feedCount();
    @Query("SELECT * FROM podcast_feeds WHERE id = :id") public abstract PodcastFeed feed(String id);
    @Query("SELECT * FROM podcast_episodes WHERE feedId = :feedId ORDER BY publishedAt DESC, id LIMIT :limit OFFSET :offset")
    public abstract List<PodcastEpisode> episodes(String feedId, int limit, int offset);
    @Query("SELECT COUNT(*) FROM podcast_episodes WHERE feedId = :feedId") public abstract int episodeCount(String feedId);
    @Query("SELECT COUNT(*) FROM podcast_episodes WHERE instr(lower(title), lower(:query)) > 0") public abstract int searchCount(String query);
    @Query("SELECT * FROM podcast_episodes WHERE instr(lower(title), lower(:query)) > 0 ORDER BY publishedAt DESC, id LIMIT :limit OFFSET :offset")
    public abstract List<PodcastEpisode> search(String query, int limit, int offset);
    @Query("SELECT * FROM podcast_episodes WHERE id = :id") public abstract PodcastEpisode episode(String id);
    @Query("UPDATE podcast_episodes SET positionMs = :position, durationMs = :duration, lastPlayedAt = :time WHERE id = :id")
    public abstract void progress(String id, long position, long duration, long time);
    @Query("DELETE FROM podcast_feeds WHERE id = :id") public abstract void unsubscribe(String id);
    @Upsert protected abstract void saveFeed(PodcastFeed feed);
    @Upsert protected abstract void saveEpisode(PodcastEpisode episode);

    /** A refresh never removes archived episodes or resets listening progress. */
    @Transaction public boolean importFeed(PodcastFeed feed, List<PodcastEpisode> episodes, boolean subscribe) {
        if (!subscribe && feed(feed.id) == null) return false;
        saveFeed(feed);
        for (PodcastEpisode episode : episodes) {
            if (!episode.feedId.equals(feed.id)) throw new IllegalArgumentException("Episode belongs to another feed");
            PodcastEpisode old = episode(episode.id);
            if (old != null) {
                episode.positionMs = old.positionMs; episode.lastPlayedAt = old.lastPlayedAt;
                if (old.durationMs > 0) episode.durationMs = old.durationMs;
            }
            saveEpisode(episode);
        }
        return true;
    }
}
