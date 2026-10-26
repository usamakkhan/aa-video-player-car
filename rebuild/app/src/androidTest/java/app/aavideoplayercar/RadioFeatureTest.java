package app.aavideoplayercar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.AppDatabase;
import app.aavideoplayercar.data.RadioStation;
import app.aavideoplayercar.radio.RadioClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RadioFeatureTest {
    private android.view.View findText(android.view.View view, String value) {
        if (view instanceof android.widget.TextView && value.contentEquals(((android.widget.TextView)view).getText())) return view;
        if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup)view).getChildCount(); i++) {
            android.view.View found = findText(((android.view.ViewGroup)view).getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }
    private void await(androidx.test.core.app.ActivityScenario<MainActivity> scenario, java.util.function.Predicate<MainActivity> ready) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean();
            scenario.onActivity(a -> result.set(ready.test(a)));
            if (result.get()) return;
            Thread.sleep(50);
        }
        fail("Radio UI did not become ready");
    }
    private JSONObject row(String id, String url) throws Exception {
        return new JSONObject().put("stationuuid", id).put("name", "Station").put("url_resolved", url).put("hls", 1);
    }
    @Test public void parserRejectsUnsafeMalformedAndDuplicateStations() throws Exception {
        String id = UUID.randomUUID().toString();
        JSONArray rows = new JSONArray().put(row(id, "https://example.com/live.m3u8"))
            .put(row(id, "https://example.com/duplicate"))
            .put(row(UUID.randomUUID().toString(), "file:///private/audio"))
            .put(row("bad-id", "https://example.com/live"))
            .put(row(UUID.randomUUID().toString(), "https://user:password@example.com/live"));
        var stations = RadioClient.parse(rows.toString());
        assertEquals(1, stations.size()); assertTrue(stations.get(0).hls);
        assertEquals("application/x-mpegURL", stations.get(0).toMediaItem().localConfiguration.mimeType);
        assertTrue(RadioClient.validStream("http://example.com/radio"));
        assertFalse(RadioClient.validStream("javascript:alert(1)"));
        assertFalse(RadioClient.validStream("content://station/live"));
        assertThrows(IOException.class, () -> RadioClient.parse("{broken"));
        assertThrows(IOException.class, () -> RadioClient.parse("{}"));
    }
    @Test public void oversizedResponsesAndInvalidQueriesAreRejected() throws Exception {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 51; i++) rows.put(row(UUID.randomUUID().toString(), "https://example.com/live"));
        assertThrows(IOException.class, () -> RadioClient.parse(rows.toString()));
        RadioClient client = new RadioClient(); client.cancel();
        assertThrows(IOException.class, () -> client.search("", "", 0));
        assertThrows(IOException.class, () -> new RadioClient().search("", "United States", 0));
    }
    @Test public void favoritesSurviveRefreshAndCanBeRemovedWithoutDuplicate() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "radio-persistence-" + UUID.randomUUID() + ".db";
        try {
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).build();
            RadioStation station = new RadioStation(); station.id = UUID.randomUUID().toString(); station.title = "Saved station"; station.url = "https://example.com/live";
            try {
                db.radio().save(station, true);
                RadioStation refreshed = new RadioStation(); refreshed.id = station.id; refreshed.title = "Updated station"; refreshed.url = "https://example.com/new";
                db.radio().save(refreshed, null);
                assertEquals(1, db.radio().count()); assertEquals("Updated station", db.radio().get(station.id).title);
            } finally { db.close(); }
            AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, name).build();
            try {
                assertTrue(reopened.radio().get(station.id).favorite);
                assertEquals(1, reopened.radio().search("Updated", 50, 0).size());
                reopened.radio().save(reopened.radio().get(station.id), false);
                assertEquals(0, reopened.radio().count());
            } finally { reopened.close(); }
        } finally { context.deleteDatabase(name); }
    }
    @Test public void transientCacheIsBoundedAndRetainsSelectedStationAndFavorites() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            RadioStation favorite = new RadioStation(); favorite.id = "favorite"; favorite.title = "Favorite";
            db.radio().save(favorite, true);
            for (int i = 0; i < 130; i++) {
                RadioStation station = new RadioStation(); station.id = "cache-" + i; station.title = station.id;
                db.radio().save(station, null);
                assertNotNull(db.radio().get(station.id));
            }
            try (android.database.Cursor cursor = db.query("SELECT COUNT(*) FROM radio_stations WHERE favorite = 0", null)) {
                assertTrue(cursor.moveToFirst()); assertEquals(100, cursor.getInt(0));
            }
            assertTrue(db.radio().get("favorite").favorite);
        } finally { db.close(); }
    }
    @Test public void v2MigrationPreservesMediaMaintenanceAndImportMarkers() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "radio-upgrade-" + UUID.randomUUID() + ".db";
        JSONObject schema;
        try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("app.aavideoplayercar.data.AppDatabase/2.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
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
                old.execSQL("INSERT INTO maintenance_records (id,title,type,notes,createdAt,updatedAt) VALUES ('service','Oil','Service','Keep notes',1,2)");
                old.execSQL("INSERT INTO data_imports VALUES ('maintenance-preferences-v1')");
                old.setVersion(2);
            }
            AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build();
            try {
                assertEquals(9000, db.media().get("kept").positionMs);
                assertEquals("Keep notes", db.maintenance().get("service").notes);
                assertEquals(1, db.maintenance().imported("maintenance-preferences-v1"));
                assertEquals(0, db.radio().count());
                RadioStation station = new RadioStation(); station.id = UUID.randomUUID().toString(); station.title = "New station";
                db.radio().save(station, true); assertEquals(1, db.radio().count());
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }

    @Test public void radioScreenRestoresInputValidatesAndRefreshesFavorites() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        var repository = app.aavideoplayercar.data.MediaRepository.get(context);
        RadioStation station = new RadioStation(); station.id = UUID.randomUUID().toString(); station.title = "UI radio fixture"; station.url = "https://example.com/live";
        repository.database.radio().save(station, true);
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> findText(a.getWindow().getDecorView(), "Radio").performClick());
            await(scenario, a -> {
                android.widget.ListView list = a.getWindow().getDecorView().findViewWithTag("radio.list");
                return list != null && list.getAdapter() != null && list.getCount() > 0;
            });
            scenario.onActivity(a -> {
                ((android.widget.EditText)a.getWindow().getDecorView().findViewWithTag("radio.query")).setText("Jazz");
                ((android.widget.EditText)a.getWindow().getDecorView().findViewWithTag("radio.country")).setText("United States");
            });
            scenario.recreate();
            await(scenario, a -> {
                android.view.View search = findText(a.getWindow().getDecorView(), "Search"); return search != null && search.isEnabled();
            });
            scenario.onActivity(a -> {
                assertEquals("Jazz", ((android.widget.EditText)a.getWindow().getDecorView().findViewWithTag("radio.query")).getText().toString());
                findText(a.getWindow().getDecorView(), "Search").performClick();
                assertEquals(a.getString(R.string.radio_validation), ((android.widget.TextView)a.getWindow().getDecorView().findViewWithTag("radio.status")).getText().toString());
            });
            repository.database.radio().save(station, false);
            scenario.onActivity(a -> findText(a.getWindow().getDecorView(), "Favorites").performClick());
            await(scenario, a -> {
                android.widget.ListView list = a.getWindow().getDecorView().findViewWithTag("radio.list");
                for (int i = 0; i < list.getCount(); i++) if (((RadioStation)list.getAdapter().getItem(i)).id.equals(station.id)) return false;
                return true;
            });
        } finally { repository.database.radio().delete(station.id); }
    }
}
