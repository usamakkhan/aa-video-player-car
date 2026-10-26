package app.aavideoplayercar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardSmokeTest {
    @Before public void resetDashboardPreferences() {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
            .getSharedPreferences("dashboard", Context.MODE_PRIVATE).edit().clear().commit();
    }
    private View text(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
    private void click(MainActivity activity, String label) {
        View target = text(activity.getWindow().getDecorView(), label);
        assertNotNull("Missing control: " + label, target);
        target.performClick();
    }
    @Test public void allNineteenRoutesOpenAndReturnHome() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (ModuleRegistry.Module module : ModuleRegistry.DEFAULTS) {
                scenario.onActivity(activity -> {
                    click(activity, module.title);
                    assertNotNull(text(activity.getWindow().getDecorView(), "Home"));
                    click(activity, "Home");
                    assertTrue(text(activity.getWindow().getDecorView(), "Settings") instanceof Button);
                });
            }
        }
    }
    @Test public void routeSurvivesRecreationAndPreferencesPersist() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                text(activity.getWindow().getDecorView(), "Vehicle Maintenance").performLongClick();
                assertTrue(activity.getSharedPreferences("dashboard", 0).getString("module_order", "").startsWith("20,"));
                click(activity, "Settings");
                click(activity, "3 dashboard columns");
                assertEquals(3, activity.getSharedPreferences("dashboard", 0).getInt("home_grid_columns", 0));
                click(activity, "Video Player");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(text(activity.getWindow().getDecorView(), "Choose a file"));
                activity.onBackPressed();
                assertNotNull(text(activity.getWindow().getDecorView(), "Settings"));
            });
        }
    }
}
