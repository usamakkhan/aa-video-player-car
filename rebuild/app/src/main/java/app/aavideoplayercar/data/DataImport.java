package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Durable marker prevents deleted imported notes from reappearing. */
@Entity(tableName = "data_imports")
public class DataImport {
    @PrimaryKey @NonNull public String name = "";
}
