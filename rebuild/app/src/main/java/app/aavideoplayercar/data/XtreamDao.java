package app.aavideoplayercar.data;
import androidx.room.*;
@Dao public interface XtreamDao {
    @Query("SELECT * FROM xtream_accounts WHERE playlistId=:id") XtreamAccount account(String id);
    @Upsert void save(XtreamAccount account);
}
