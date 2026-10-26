package app.aavideoplayercar.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "iptv_series_episodes", foreignKeys = {
    @ForeignKey(entity = IptvSeries.class, parentColumns = "id", childColumns = "seriesId", onDelete = ForeignKey.CASCADE),
    @ForeignKey(entity = IptvChannel.class, parentColumns = "id", childColumns = "channelId", onDelete = ForeignKey.CASCADE)
}, indices = @Index(value = {"seriesId", "season", "number"}))
public class IptvSeriesEpisode {
    @PrimaryKey @NonNull public String channelId = "";
    @NonNull public String seriesId = "";
    public int season, number;
}
