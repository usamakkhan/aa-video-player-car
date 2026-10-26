package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "news_sources")
public class NewsSource {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String url = "";
    @NonNull public String title = "";
    public long updatedAt;
    @Override public String toString() { return title; }
}
