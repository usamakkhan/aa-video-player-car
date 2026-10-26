package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

@Entity(tableName = "media")
public class MediaEntry {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String uri = "";
    @NonNull public String title = "";
    @NonNull public String mime = "";
    public long addedAt;
    public long positionMs;
    public long durationMs;
    public long lastPlayedAt;

    public MediaItem toMediaItem() {
        return new MediaItem.Builder().setMediaId(id).setUri(uri).setMimeType(mime)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title)
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(isAudio() ? MediaMetadata.MEDIA_TYPE_MUSIC : MediaMetadata.MEDIA_TYPE_VIDEO).build())
            .build();
    }
    public boolean isAudio() { return mime.startsWith("audio/"); }
}
