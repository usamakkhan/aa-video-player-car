package app.aavideoplayercar;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.movies.*;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.network.HttpDocument;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.is;
@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class MovieTitleTest {
    private void rows(String tag,int count) throws Exception {
        long end=android.os.SystemClock.elapsedRealtime()+12000;
        while(android.os.SystemClock.elapsedRealtime()<end){var ready=new java.util.concurrent.atomic.AtomicBoolean();onView(withTagValue(is(tag))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->{if(e!=null)throw e;ready.set(((ListView)v).getCount()==count);});if(ready.get())return;Thread.sleep(50);}fail("Rows timeout");
    }
    private void clickRow(String tag,int index){onView(withTagValue(is(tag))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->{if(e!=null)throw e;((ListView)v).performItemClick(null,index,index);});}
    @Test public void seasonsPageEmbeddedSourcesExplainUnsupportedAndPlayDirectVideo() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext(); var repository=MediaRepository.get(context); String addonId=null; final TitleDialog[] dialog={null};
        try(var server=new LocalHttpFixture();var activity=ActivityScenario.launch(MainActivity.class)){
            String manifest="{\"id\":\"org.title.fixture\",\"name\":\"Title fixture\",\"version\":\"1\",\"types\":[\"series\"],\"resources\":[\"meta\"],\"catalogs\":[]}";
            server.add("/manifest.json",manifest.getBytes(StandardCharsets.UTF_8),"application/json");
            try(var in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")){server.add("/clip.mp4",in.readAllBytes(),"video/mp4");}
            StringBuilder meta=new StringBuilder("{\"meta\":{\"id\":\"show\",\"type\":\"series\",\"name\":\"Demo series\",\"videos\":[{\"id\":\"special\",\"title\":\"Special\",\"season\":0,\"episode\":1,\"streams\":[{\"infoHash\":\"abc\"},{\"url\":\"").append(server.url("/clip.mp4")).append("\"}]}");
            for(int i=1;i<=51;i++)meta.append(",{\"id\":\"episode").append(i).append("\",\"title\":\"Episode\",\"season\":1,\"episode\":").append(i).append(",\"streams\":[]}");
            meta.append("]}}");server.add("/meta/series/show.json",meta.toString().getBytes(StandardCharsets.UTF_8),"application/json");
            var addon=AddonClient.manifest(new HttpDocument(),server.url("/manifest.json"));addonId=AddonRegistration.save(repository.database.movieAddons(),addon,null);
            var title=AddonClient.meta(new HttpDocument(),addon,"series","show");final String id=addonId;
            activity.onActivity(a->dialog[0]=new TitleDialog(a,id,addon,title,null,()->{}));rows("movies.episodes",1);
            onView(withTagValue(is("movies.seasons"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->((Spinner)v).setSelection(1));rows("movies.episodes",50);
            onView(withText(R.string.radio_next)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());rows("movies.episodes",1);clickRow("movies.episodes",0);rows("movies.streams",0);
            onView(withTagValue(is("movies.stream.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->assertEquals(context.getString(R.string.movies_no_streams),((TextView)v).getText().toString()));
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withTagValue(is("movies.seasons"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->((Spinner)v).setSelection(0));rows("movies.episodes",1);clickRow("movies.episodes",0);rows("movies.streams",2);clickRow("movies.streams",0);
            onView(withTagValue(is("movies.stream.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->assertEquals(context.getString(R.string.movies_torrent),((TextView)v).getText().toString()));clickRow("movies.streams",1);
            long end=android.os.SystemClock.elapsedRealtime()+15000;boolean playing=false;
            while(android.os.SystemClock.elapsedRealtime()<end){var ready=new java.util.concurrent.atomic.AtomicBoolean();try{onView(withTagValue(is("movies.player"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->{if(e!=null)throw e;var p=((androidx.media3.ui.PlayerView)v).getPlayer();ready.set(p!=null&&p.isPlaying()&&p.getVideoSize().width>0);if(ready.get())p.pause();});}catch(androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing){}if(ready.get()){playing=true;break;}Thread.sleep(50);}assertTrue(playing);
            activity.onActivity(a->dialog[0].close());dialog[0]=null;
        }finally{if(addonId!=null)repository.database.movieAddons().delete(addonId);}
    }
}
