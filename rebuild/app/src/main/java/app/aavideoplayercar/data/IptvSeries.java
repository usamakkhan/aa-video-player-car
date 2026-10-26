package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "iptv_series", foreignKeys = @ForeignKey(entity = IptvPlaylist.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE), indices = @Index("playlistId"))
public class IptvSeries {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String playlistId = "";
    @NonNull public String providerId = "";
    @NonNull public String title = "";
    @NonNull public String category = "";
    public long updatedAt;
    @Override public String toString() { return title; }
}
