package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;

/** Configured transport URLs are encrypted; local identity survives credential rotation. */
@Entity(tableName = "movie_addons")
public class MovieAddon {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String endpoint = "";
    @NonNull public String providerId = "";
    @NonNull public String title = "";
    @NonNull public String version = "";
    public long updatedAt;
    @Override public String toString() { return title; }
}
