package app.aavideoplayercar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "maintenance_records")
public class MaintenanceRecord {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String type = "Service";
    public Long serviceDay;
    public Long serviceKm;
    public Long dueDay;
    public Long dueKm;
    @NonNull public String notes = "";
    public long createdAt;
    public long updatedAt;
}
