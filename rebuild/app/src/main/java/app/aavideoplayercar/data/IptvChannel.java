package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.media3.common.*;
@Entity(tableName = "iptv_channels", foreignKeys = @ForeignKey(entity = IptvPlaylist.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE), indices = @Index("playlistId"))
public class IptvChannel {
    public static final String PREFIX = "iptv:";
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String playlistId = "";
    @NonNull public String title = "";
    @NonNull public String url = "";
    @NonNull public String groupName = "";
    @NonNull public String tvgId = "";
    public boolean live = true;
    /** 0 follows the playlist; 1 forces live; 2 forces on-demand. */
    @ColumnInfo(defaultValue = "0") public int playbackMode;
    public boolean isLive() { return playbackMode == 1 || (playbackMode != 2 && live); }
    public boolean favorite;
    public long positionMs, durationMs, lastPlayedAt;
    public MediaItem toMediaItem() {
        return new MediaItem.Builder().setMediaId(PREFIX + id).setUri(url)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(isLive() ? MediaMetadata.MEDIA_TYPE_TV_CHANNEL : MediaMetadata.MEDIA_TYPE_MOVIE).build()).build();
    }
    @Override public String toString() { return (favorite ? "★ " : "") + title + (groupName.isEmpty() ? "" : "\n" + groupName); }
}
