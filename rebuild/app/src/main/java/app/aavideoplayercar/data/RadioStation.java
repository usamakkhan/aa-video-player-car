package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

@Entity(tableName = "radio_stations")
public class RadioStation {
    public static final String PREFIX = "radio:";
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String url = "";
    @NonNull public String country = "";
    @NonNull public String codec = "";
    public boolean hls;
    public boolean favorite;
    public long updatedAt;

    public MediaItem toMediaItem() {
        return new MediaItem.Builder().setMediaId(PREFIX + id).setUri(url)
            .setMimeType(hls ? "application/x-mpegURL" : null)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist(country)
                .setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build()).build();
    }
    @Override public String toString() { return title + (country.isEmpty() ? "" : " - " + country); }
}
