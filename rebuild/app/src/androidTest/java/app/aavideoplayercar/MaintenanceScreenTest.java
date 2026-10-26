package app.aavideoplayercar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.MediaRepository;
import app.aavideoplayercar.data.MaintenanceRecord;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MaintenanceScreenTest {
    private View text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = text(((ViewGroup)view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private void click(MainActivity activity, String value) {
        View found = text(activity.getWindow().getDecorView(), value); assertNotNull(value, found); found.performClick();
    }
    private EditText field(MainActivity activity, String name) { return activity.getWindow().getDecorView().findViewWithTag("maintenance." + name); }
    private void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean(); scenario.onActivity(a -> ready.set(condition.test(a)));
            if (ready.get()) return; Thread.sleep(50);
        }
        fail("Maintenance screen did not become ready");
    }
    @Test public void validateRestoreDraftSaveAndEditWithoutDuplicating() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        MediaRepository repository = MediaRepository.get(context);
        String title = "UI service " + UUID.randomUUID();
        String savedId = null;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> click(a, "Vehicle Maintenance"));
            await(scenario, a -> text(a.getWindow().getDecorView(), "Add service record") != null);
            scenario.onActivity(a -> {
                click(a, "Add service record"); field(a, "title").setText(title);
                field(a, "serviceDate").setText("2026-10-12"); field(a, "serviceKm").setText("1000");
                field(a, "dueDate").setText("2027-10-12"); field(a, "dueKm").setText("999");
                click(a, "Save service record");
                assertNotNull(text(a.getWindow().getDecorView(), "Due mileage must not precede the service mileage."));
                field(a, "dueKm").setText("2000"); field(a, "notes").setText("  draft\nretained  ");
            });
            scenario.recreate();
            await(scenario, a -> field(a, "title") != null);
            scenario.onActivity(a -> {
                assertEquals(title, field(a, "title").getText().toString());
                assertEquals("  draft\nretained  ", field(a, "notes").getText().toString());
                click(a, "Save service record");
            });
            await(scenario, a -> {
                ListView list = a.getWindow().getDecorView().findViewWithTag("maintenance.records");
                return list != null && list.getAdapter() != null && list.getAdapter().getCount() > 0;
            });
            MaintenanceRecord record = repository.database.maintenance().list(1000, 0).stream().filter(r -> title.equals(r.title)).findFirst().orElseThrow();
            savedId = record.id;
            assertEquals(Long.valueOf(2000), record.dueKm); assertEquals("  draft\nretained  ", record.notes);
            scenario.onActivity(a -> {
                ListView list = a.getWindow().getDecorView().findViewWithTag("maintenance.records");
                int position = -1;
                for (int i = 0; i < list.getAdapter().getCount(); i++) if (list.getAdapter().getItem(i).toString().startsWith(title)) position = i;
                assertTrue(position >= 0); list.performItemClick(null, position, position);
                field(a, "notes").setText("Edited record"); click(a, "Save service record");
            });
            await(scenario, a -> text(a.getWindow().getDecorView(), "Add service record") != null);
            assertEquals("Edited record", repository.database.maintenance().get(savedId).notes);
            assertEquals(1, repository.database.maintenance().list(1000, 0).stream().filter(r -> title.equals(r.title)).count());
        } finally {
            if (savedId != null) repository.database.maintenance().delete(savedId);
        }
    }
}
