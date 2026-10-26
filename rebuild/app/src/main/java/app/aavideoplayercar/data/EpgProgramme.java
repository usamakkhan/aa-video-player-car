package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "epg_programmes", foreignKeys = @ForeignKey(entity = IptvPlaylist.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE), indices = @Index(value = {"playlistId", "channelId", "startMs"}))
public class EpgProgramme {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String playlistId = "";
    @NonNull public String channelId = "";
    @NonNull public String title = "";
    @NonNull public String description = "";
    public long startMs, endMs;
}
