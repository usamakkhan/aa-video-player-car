package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "news_articles", foreignKeys = @ForeignKey(entity = NewsSource.class, parentColumns = "id", childColumns = "sourceId", onDelete = ForeignKey.CASCADE), indices = @Index(value = {"sourceId", "publishedAt"}))
public class NewsArticle {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String sourceId = "";
    @NonNull public String title = "";
    @NonNull public String body = "";
    @NonNull public String url = "";
    public long publishedAt;
    public boolean read;
    @Override public String toString() { return title; }
}
