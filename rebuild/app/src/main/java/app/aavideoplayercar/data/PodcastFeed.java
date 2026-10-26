package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "podcast_feeds")
public class PodcastFeed {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String url = "";
    @NonNull public String title = "";
    public long updatedAt;
    @Override public String toString() { return title; }
}
