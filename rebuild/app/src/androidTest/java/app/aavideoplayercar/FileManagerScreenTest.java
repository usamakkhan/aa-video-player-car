package app.aavideoplayercar;

import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class FileManagerScreenTest {
    private View text(View root,String label){if(root instanceof TextView && label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    @Test public void folderRouteShowsConsentStateAndSurvivesRecreation() {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences("dashboard",0).edit().clear().commit();context.getSharedPreferences("file_manager",0).edit().clear().commit();
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->text(a.getWindow().getDecorView(),"File Manager").performClick());
            onView(withTagValue(is("files.status"))).check((v,e)->{if(e!=null)throw e;assertEquals(context.getString(R.string.files_no_folder),((TextView)v).getText().toString());});
            onView(withText(R.string.files_choose)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withTagValue(is("files.status"))).check((v,e)->{if(e!=null)throw e;assertEquals(context.getString(R.string.files_no_folder),((TextView)v).getText().toString());});
        }
    }
}
