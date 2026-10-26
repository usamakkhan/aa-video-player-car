package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "xtream_accounts", foreignKeys = @ForeignKey(entity = IptvPlaylist.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE))
public class XtreamAccount {
    @PrimaryKey @NonNull public String playlistId = "";
    @NonNull public String server = "";
    @NonNull public String credentials = "";
}
