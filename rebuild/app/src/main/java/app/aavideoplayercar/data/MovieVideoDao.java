package app.aavideoplayercar.data;
import androidx.room.*;
@Dao public abstract class MovieVideoDao {
    @Query("SELECT * FROM movie_videos WHERE id=:id") public abstract MovieVideo get(String id);
    @Query("SELECT COUNT(*) FROM movie_videos") public abstract int count();
    @Upsert protected abstract void upsert(MovieVideo video);
    @Query("UPDATE movie_videos SET positionMs=:position,durationMs=:duration,lastPlayedAt=:time WHERE id=:id") public abstract void progress(String id,long position,long duration,long time);
    @Transaction public void save(MovieVideo video) {
        MovieVideo prior=get(video.id);
        if(video.id.isEmpty() || video.title.isEmpty() || video.title.length()>1000 || video.source.length()>24000 || video.source.isEmpty()
            || video.providerType.length()>100 || video.providerVideoId.length()>500 || video.streamFingerprint.length()>64 || video.streamIndex < -1 || video.streamIndex > 499
            || (prior==null && count()>=5000)) throw new IllegalArgumentException("Movie library limit or invalid video");
        if(prior!=null) { if(!prior.addonId.equals(video.addonId)) throw new IllegalArgumentException("Wrong add-on"); video.positionMs=prior.positionMs; video.durationMs=prior.durationMs; video.lastPlayedAt=prior.lastPlayedAt; }
        upsert(video);
    }
}
