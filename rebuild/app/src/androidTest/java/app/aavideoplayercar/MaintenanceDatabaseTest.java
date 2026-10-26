package app.aavideoplayercar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.AppDatabase;
import app.aavideoplayercar.data.MaintenanceRecord;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MaintenanceDatabaseTest {
    @Test public void v1UpgradePreservesMediaAndImportsLegacyNotesOnlyOnce() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "migration-test-" + UUID.randomUUID() + ".db";
        JSONObject schema;
        try (java.io.InputStream stream = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("app.aavideoplayercar.data.AppDatabase/1.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int read;
            while ((read = stream.read(buffer)) != -1) bytes.write(buffer, 0, read);
            schema = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).getJSONObject("database");
        }
        try {
            try (SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null)) {
                JSONArray entities = schema.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    JSONObject entity = entities.getJSONObject(i);
                    old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                }
                JSONArray setup = schema.getJSONArray("setupQueries");
                for (int i = 0; i < setup.length(); i++) old.execSQL(setup.getString(i));
                old.execSQL("INSERT INTO media VALUES ('kept', 'content://fixture/audio', 'Existing track', 'audio/wav', 1, 9000, 30000, 2)");
                old.setVersion(1);
            }
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
            try {
                assertEquals(9000, db.media().get("kept").positionMs);
                String legacy = "  oil\nbrakes\n\nUnicode: 日本語  ";
                db.maintenance().importLegacyNotes(legacy);
                db.maintenance().importLegacyNotes("must not overwrite");
                assertEquals(1, db.maintenance().count());
                MaintenanceRecord imported = db.maintenance().get("legacy-maintenance-notes");
                assertEquals(legacy, imported.notes); assertNull(imported.serviceDay); assertNull(imported.serviceKm);
                db.maintenance().delete(imported.id);
                db.maintenance().importLegacyNotes(legacy);
                assertEquals(0, db.maintenance().count());
            } finally { db.close(); }
            AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
            try {
                assertEquals(9000, reopened.media().get("kept").positionMs);
                assertEquals(1, reopened.maintenance().imported("maintenance-preferences-v1"));
            } finally { reopened.close(); }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void createUpdateDeleteAndPagingPreserveRecordIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            MaintenanceRecord first = new MaintenanceRecord(); first.id = "first"; first.title = "Oil"; first.serviceDay = 100L; first.serviceKm = 1000L; first.dueKm = 2000L;
            MaintenanceRecord second = new MaintenanceRecord(); second.id = "second"; second.title = "Tires"; second.serviceDay = 200L;
            db.maintenance().save(first); db.maintenance().save(second);
            assertEquals("second", db.maintenance().list(1, 0).get(0).id);
            assertEquals("first", db.maintenance().list(1, 1).get(0).id);
            first.title = "Oil and filter"; first.notes = "Updated"; db.maintenance().save(first);
            assertEquals(2, db.maintenance().count()); assertEquals("Updated", db.maintenance().get("first").notes);
            assertEquals(Long.valueOf(2000), db.maintenance().get("first").dueKm);
            assertEquals(1, db.maintenance().delete("first")); assertNull(db.maintenance().get("first"));
        } finally { db.close(); }
    }
}
