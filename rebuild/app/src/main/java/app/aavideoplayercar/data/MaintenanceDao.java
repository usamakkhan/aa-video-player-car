package app.aavideoplayercar.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Upsert;
import java.util.List;

@Dao
public abstract class MaintenanceDao {
    @Insert public abstract void insert(MaintenanceRecord record);
    @Upsert public abstract void save(MaintenanceRecord record);
    @Update public abstract int update(MaintenanceRecord record);
    @Query("SELECT * FROM maintenance_records ORDER BY serviceDay DESC, createdAt DESC, id LIMIT :limit OFFSET :offset")
    public abstract List<MaintenanceRecord> list(int limit, int offset);
    @Query("SELECT COUNT(*) FROM maintenance_records") public abstract int count();
    @Query("SELECT * FROM maintenance_records WHERE id = :id") public abstract MaintenanceRecord get(String id);
    @Query("DELETE FROM maintenance_records WHERE id = :id") public abstract int delete(String id);
    @Query("SELECT COUNT(*) FROM data_imports WHERE name = :name") public abstract int imported(String name);
    @Insert(onConflict = OnConflictStrategy.IGNORE) public abstract void markImported(DataImport marker);
    @Transaction public void importLegacyNotes(String notes) {
        if (imported("maintenance-preferences-v1") != 0) return;
        if (notes != null && !notes.isEmpty()) {
            MaintenanceRecord record = new MaintenanceRecord();
            record.id = "legacy-maintenance-notes"; record.title = "Imported maintenance notes";
            record.type = "Legacy notes"; record.notes = notes;
            record.createdAt = record.updatedAt = System.currentTimeMillis();
            insert(record);
        }
        DataImport marker = new DataImport(); marker.name = "maintenance-preferences-v1";
        markImported(marker);
    }
}
