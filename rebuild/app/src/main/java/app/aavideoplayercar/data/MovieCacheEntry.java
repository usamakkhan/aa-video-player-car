package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "movie_cache", foreignKeys = @ForeignKey(entity = MovieAddon.class, parentColumns = "id", childColumns = "addonId", onDelete = ForeignKey.CASCADE), indices = {@Index("addonId"), @Index("updatedAt")})
public class MovieCacheEntry {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String addonId = "";
    @NonNull public String payload = "";
    public int bytes;
    public long updatedAt;
}
