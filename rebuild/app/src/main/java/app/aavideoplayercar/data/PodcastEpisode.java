package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

@Entity(tableName = "podcast_episodes", foreignKeys = @ForeignKey(entity = PodcastFeed.class,
    parentColumns = "id", childColumns = "feedId", onDelete = ForeignKey.CASCADE), indices = @Index("feedId"))
public class PodcastEpisode {
    public static final String PREFIX = "podcast:";
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String feedId = "";
    @NonNull public String title = "";
    @NonNull public String url = "";
    @NonNull public String mime = "";
    public long publishedAt;
    public long positionMs;
    public long durationMs;
    public long lastPlayedAt;
    public MediaItem toMediaItem() {
        return new MediaItem.Builder().setMediaId(PREFIX + id).setUri(url).setMimeType(mime.isEmpty() ? null : mime)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE).build()).build();
    }
    @Override public String toString() { return title; }
}
