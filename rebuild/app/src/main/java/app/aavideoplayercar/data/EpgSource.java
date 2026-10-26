package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "epg_sources", foreignKeys = @ForeignKey(entity = IptvPlaylist.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE))
public class EpgSource {
    @PrimaryKey @NonNull public String playlistId = "";
    @NonNull public String url = "";
    public long updatedAt;
}
