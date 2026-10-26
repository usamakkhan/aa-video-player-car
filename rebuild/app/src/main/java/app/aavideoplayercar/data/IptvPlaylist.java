package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "iptv_playlists")
public class IptvPlaylist {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String url = "";
    public long updatedAt;
    @Override public String toString() { return title; }
}
