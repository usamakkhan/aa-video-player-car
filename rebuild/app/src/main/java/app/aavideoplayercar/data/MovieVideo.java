package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.media3.common.*;
@Entity(tableName="movie_videos", foreignKeys=@ForeignKey(entity=MovieAddon.class,parentColumns="id",childColumns="addonId",onDelete=ForeignKey.CASCADE),indices=@Index("addonId"))
public class MovieVideo {
    public static final String PREFIX = "movie-video:";
    @PrimaryKey @NonNull public String id="";
    @NonNull public String addonId="";
    @NonNull public String title="";
    @NonNull public String source="";
    @NonNull public String providerType="";
    @NonNull public String providerVideoId="";
    @NonNull public String streamFingerprint="";
    public int streamIndex=-1;
    public long positionMs, durationMs, lastPlayedAt;
    public MediaItem item() { return new MediaItem.Builder().setMediaId(PREFIX+id).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build()).build(); }
}
