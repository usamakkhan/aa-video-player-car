package app.aavideoplayercar;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.movies.AddonRegistration;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class MoviesScreenTest {
    private void catalogRows(int count, String first) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 15000;
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            try { onView(withTagValue(is("movies.catalog.list"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> {
                if (e != null) throw e; ListView list = (ListView)v; ready.set(list.isEnabled() && list.getCount() == count && (count == 0 || first.equals(list.getAdapter().getItem(0).toString())));
            }); } catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { }
            if (ready.get()) return; Thread.sleep(50);
        } fail("Catalog rows timeout: " + first);
    }
    private void selectCatalogRow(int index) { onView(withTagValue(is("movies.catalog.list"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if (e != null) throw e; ((ListView)v).performItemClick(null, index, index); }); }
    @Test public void sourceChoicesAndPausedPlayerRestoreAfterRecreation() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext(); context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var repository = MediaRepository.get(context); var dao = repository.database.movieAddons(); String id = null;
        try (var server = new LocalHttpFixture()) {
            String manifest = "{\"id\":\"org.restore.fixture\",\"name\":\"Restore fixture\",\"version\":\"1\",\"types\":[\"movie\"],\"resources\":[\"catalog\",\"meta\",\"stream\"],\"catalogs\":[{\"id\":\"all\",\"type\":\"movie\",\"name\":\"Restore catalog\"}]}";
            server.add("/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/catalog/movie/all.json", "{\"metas\":[{\"id\":\"tt-restore\",\"type\":\"movie\",\"name\":\"Restore film\"}]}".getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/meta/movie/tt-restore.json", "{\"meta\":{\"id\":\"tt-restore\",\"type\":\"movie\",\"name\":\"Restore film\"}}".getBytes(StandardCharsets.UTF_8), "application/json");
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("iptv/clip.mp4")) { server.add("/clip.mp4", input.readAllBytes(), "video/mp4"); }
            server.add("/stream/movie/tt-restore.json", ("{\"streams\":[{\"name\":\"HD\",\"url\":\""+server.url("/clip.mp4")+"\"}]}").getBytes(StandardCharsets.UTF_8), "application/json");
            id = AddonRegistration.save(dao, app.aavideoplayercar.movies.AddonClient.manifest(new app.aavideoplayercar.network.HttpDocument(), server.url("/manifest.json")), null);
            try (var scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> text(a.getWindow().getDecorView(), "Movies & Series").performClick()); await(scenario, a -> list(a).isEnabled() && list(a).getCount() > 0);
                final String selectedId = id; scenario.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (((MovieAddon)list(a).getAdapter().getItem(i)).id.equals(selectedId)) list(a).performItemClick(null, i, i); });
                onView(withText(R.string.movies_browse)).perform(click()); catalogRows(1, "Restore catalog · movie"); selectCatalogRow(0); catalogRows(1, "Restore film"); selectCatalogRow(0);
                onView(withText(R.string.movies_sources)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); dialogRows("movies.streams",1);
                scenario.recreate(); dialogRows("movies.streams",1);
                onView(withTagValue(is("movies.streams"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if(e!=null)throw e; ((ListView)v).performItemClick(null,0,0); });
                long deadline = android.os.SystemClock.elapsedRealtime()+15000; boolean paused = false;
                while (android.os.SystemClock.elapsedRealtime()<deadline) { var ready = new java.util.concurrent.atomic.AtomicBoolean(); try { onView(withTagValue(is("movies.player"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if(e!=null)throw e; var p=((androidx.media3.ui.PlayerView)v).getPlayer(); if(p!=null && p.isPlaying() && p.getVideoSize().width>0) { p.seekTo(4000); p.pause(); ready.set(true); } }); } catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { } if (ready.get()) { paused=true; break; } Thread.sleep(50); } assertTrue("Movie never reached paused state",paused);
                repository.worker.submit(() -> {}).get(10,java.util.concurrent.TimeUnit.SECONDS);
                scenario.recreate(); deadline=android.os.SystemClock.elapsedRealtime()+15000; boolean restored=false;
                while (android.os.SystemClock.elapsedRealtime()<deadline) { var ready = new java.util.concurrent.atomic.AtomicBoolean(); try { onView(withTagValue(is("movies.player"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if(e!=null)throw e; var p=((androidx.media3.ui.PlayerView)v).getPlayer(); ready.set(p!=null && !p.isPlaying() && p.getCurrentPosition()>=4000); }); } catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { } if (ready.get()) { restored=true; break; } Thread.sleep(50); } assertTrue("Paused player did not restore position",restored);
                onView(withText(R.string.news_close)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); dialogRows("movies.streams",1);
            }
        } finally { if (id != null) dao.delete(id); }
    }
    private void dialogRows(String tag,int count) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+15000;
        while(android.os.SystemClock.elapsedRealtime()<deadline) { var ready=new java.util.concurrent.atomic.AtomicBoolean(); try { onView(withTagValue(is(tag))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e)->{if(e!=null)throw e; ready.set(((ListView)v).isEnabled() && ((ListView)v).getCount()==count);}); } catch(androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { } if(ready.get())return; Thread.sleep(50); } fail("Dialog rows timeout: "+tag);
    }
    @Test public void cachedCatalogAndMetadataSurviveProviderFailure() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext(); context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var dao = MediaRepository.get(context).database.movieAddons(); String id = null;
        try (var server = new LocalHttpFixture()) {
            String manifest = "{\"id\":\"org.cache.fixture\",\"name\":\"Cache fixture\",\"version\":\"1\",\"types\":[\"movie\"],\"resources\":[\"catalog\",\"meta\"],\"catalogs\":[{\"id\":\"all\",\"type\":\"movie\",\"name\":\"Saved catalog\"}]}";
            server.add("/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/catalog/movie/all.json", "{\"metas\":[{\"id\":\"tt-cache\",\"type\":\"movie\",\"name\":\"Saved film\"}]}".getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/meta/movie/tt-cache.json", "{\"meta\":{\"id\":\"tt-cache\",\"type\":\"movie\",\"name\":\"Saved film\",\"description\":\"Saved synopsis\"}}".getBytes(StandardCharsets.UTF_8), "application/json");
            id = AddonRegistration.save(dao, app.aavideoplayercar.movies.AddonClient.manifest(new app.aavideoplayercar.network.HttpDocument(), server.url("/manifest.json")), null);
            try (var scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> text(a.getWindow().getDecorView(), "Movies & Series").performClick()); await(scenario, a -> list(a).isEnabled() && list(a).getCount() > 0);
                final String selectedId = id; scenario.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (((MovieAddon)list(a).getAdapter().getItem(i)).id.equals(selectedId)) list(a).performItemClick(null, i, i); });
                onView(withText(R.string.movies_browse)).perform(click()); catalogRows(1, "Saved catalog · movie"); selectCatalogRow(0); catalogRows(1, "Saved film"); selectCatalogRow(0);
                onView(withTagValue(is("movies.detail"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if (e != null) throw e; assertEquals("Saved synopsis", ((TextView)v).getText().toString()); });
                onView(withText(R.string.news_close)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                server.add("/manifest.json", "broken".getBytes(StandardCharsets.UTF_8), "application/json");
                server.add("/catalog/movie/all.json", "broken".getBytes(StandardCharsets.UTF_8), "application/json");
                server.add("/meta/movie/tt-cache.json", "broken".getBytes(StandardCharsets.UTF_8), "application/json");
                onView(withText(R.string.news_close)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                onView(withText(R.string.movies_browse)).perform(click()); catalogRows(1, "Saved catalog · movie"); selectCatalogRow(0); catalogRows(1, "Saved film");
                onView(withTagValue(is("movies.catalog.status"))).check((v,e) -> { if (e != null) throw e; assertTrue(((TextView)v).getText().toString().contains("saved copy")); });
                selectCatalogRow(0); onView(withTagValue(is("movies.detail"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if (e != null) throw e; assertEquals("Saved synopsis", ((TextView)v).getText().toString()); });
            }
        } finally { if (id != null) dao.delete(id); }
    }
    @Test public void browsesPagesRestoresSearchAndLoadsDescription() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext(); context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var dao = MediaRepository.get(context).database.movieAddons(); String id = null;
        try (var server = new LocalHttpFixture()) {
            String manifest = "{\"id\":\"org.catalog.fixture\",\"name\":\"Catalog fixture\",\"version\":\"1\",\"types\":[\"movie\"],\"resources\":[\"catalog\",\"meta\"],\"catalogs\":[{\"id\":\"all\",\"type\":\"movie\",\"name\":\"Fixture catalog\",\"extra\":[{\"name\":\"skip\"},{\"name\":\"search\"}]}]}";
            server.add("/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), "application/json");
            StringBuilder rows = new StringBuilder("{\"metas\":["); for (int i = 0; i < 51; i++) { if (i > 0) rows.append(','); rows.append("{\"id\":\"tt").append(i).append("\",\"type\":\"movie\",\"name\":\"Film ").append(i).append("\"}"); } rows.append("]}");
            server.add("/catalog/movie/all/skip=0.json", rows.toString().getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/catalog/movie/all/skip=51.json", "{\"metas\":[{\"id\":\"tt51\",\"type\":\"movie\",\"name\":\"Next batch\"}]}".getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/catalog/movie/all/search=hello&skip=0.json", "{\"metas\":[{\"id\":\"tt7\",\"type\":\"movie\",\"name\":\"Found film\"}]}".getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/meta/movie/tt7.json", "{\"meta\":{\"id\":\"tt7\",\"type\":\"movie\",\"name\":\"Found film\",\"description\":\"Detailed fixture synopsis\"}}".getBytes(StandardCharsets.UTF_8), "application/json");
            id = AddonRegistration.save(dao, app.aavideoplayercar.movies.AddonClient.manifest(new app.aavideoplayercar.network.HttpDocument(), server.url("/manifest.json")), null);
            try (var scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> text(a.getWindow().getDecorView(), "Movies & Series").performClick()); await(scenario, a -> list(a).isEnabled() && list(a).getCount() > 0);
                final String selectedId = id; scenario.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (((MovieAddon)list(a).getAdapter().getItem(i)).id.equals(selectedId)) list(a).performItemClick(null, i, i); });
                onView(withText(R.string.movies_browse)).perform(click()); catalogRows(1, "Fixture catalog · movie"); selectCatalogRow(0); catalogRows(50, "Film 0");
                onView(withText(R.string.radio_next)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); catalogRows(1, "Film 50");
                scenario.recreate(); catalogRows(1, "Film 50");
                onView(withText(R.string.radio_next)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); catalogRows(1, "Next batch");
                onView(withText(R.string.radio_previous)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); catalogRows(1, "Film 50");
                onView(withText(R.string.movies_filters)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                onView(withTagValue(is("movies.filter.search"))).perform(replaceText("hello"), closeSoftKeyboard()); onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); catalogRows(1, "Found film");
                scenario.recreate(); catalogRows(1, "Found film"); selectCatalogRow(0);
                long end = android.os.SystemClock.elapsedRealtime() + 12000; boolean found = false;
                while (android.os.SystemClock.elapsedRealtime() < end) { try { onView(withTagValue(is("movies.detail"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v,e) -> { if (e != null) throw e; assertEquals("Detailed fixture synopsis", ((TextView)v).getText().toString()); }); found = true; break; } catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { Thread.sleep(50); } } assertTrue(found);
            }
        } finally { if (id != null) dao.delete(id); }
    }
    private View text(View root, String label) { if (root instanceof TextView && label.contentEquals(((TextView)root).getText())) return root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View result = text(((ViewGroup)root).getChildAt(i), label); if (result != null) return result; } return null; }
    private ListView list(MainActivity a) { return a.getWindow().getDecorView().findViewWithTag("movies.list"); }
    private void await(ActivityScenario<MainActivity> scenario, java.util.function.Predicate<MainActivity> ready) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 12000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) { var ok = new java.util.concurrent.atomic.AtomicBoolean(); scenario.onActivity(a -> ok.set(ready.test(a))); if (ok.get()) return; Thread.sleep(50); } fail("Movies screen timeout");
    }
    @Test public void installsRestoresRefreshesRetainsFailureRotatesAndRemoves() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext(); context.getSharedPreferences("dashboard", 0).edit().clear().commit();
        var dao = MediaRepository.get(context).database.movieAddons(); String id = null;
        String manifest = "{\"id\":\"org.screen.fixture\",\"name\":\"Movie screen fixture\",\"version\":\"1.0.0\",\"types\":[\"movie\"],\"resources\":[\"stream\"],\"catalogs\":[]}";
        try (var server = new LocalHttpFixture(); var scenario = ActivityScenario.launch(MainActivity.class)) {
            server.add("/token/manifest.json", manifest.getBytes(StandardCharsets.UTF_8), "application/json");
            server.add("/replacement/manifest.json", manifest.replace("Movie screen fixture", "Updated movie fixture").getBytes(StandardCharsets.UTF_8), "application/json");
            scenario.onActivity(a -> text(a.getWindow().getDecorView(), "Movies & Series").performClick());
            await(scenario, a -> list(a).isEnabled());
            onView(withTagValue(is("movies.url"))).perform(replaceText(server.url("/token/manifest.json")), closeSoftKeyboard());
            onView(withText(R.string.movies_install)).perform(click());
            await(scenario, a -> text(a.getWindow().getDecorView(), context.getString(R.string.movies_selected, "Movie screen fixture", "1.0.0")) != null);
            for (var row : dao.all()) if (row.providerId.equals("org.screen.fixture")) id = row.id;
            assertNotNull(id); assertEquals(server.url("/token/manifest.json"), AddonRegistration.endpoint(dao.get(id)));
            scenario.recreate(); await(scenario, a -> text(a.getWindow().getDecorView(), context.getString(R.string.movies_selected, "Movie screen fixture", "1.0.0")) != null);
            onView(withTagValue(is("movies.url"))).check((v,e) -> assertEquals("", ((EditText)v).getText().toString()));
            onView(withText(R.string.movies_refresh)).perform(click()); await(scenario, a -> list(a).isEnabled());
            server.add("/token/manifest.json", "broken".getBytes(StandardCharsets.UTF_8), "application/json");
            onView(withText(R.string.movies_refresh)).perform(click()); await(scenario, a -> text(a.getWindow().getDecorView(), context.getString(R.string.movies_error)) != null);
            assertEquals("Movie screen fixture", dao.get(id).title);
            onView(withTagValue(is("movies.url"))).perform(replaceText(server.url("/replacement/manifest.json")), closeSoftKeyboard());
            onView(withText(R.string.movies_replace)).perform(click()); await(scenario, a -> text(a.getWindow().getDecorView(), context.getString(R.string.movies_selected, "Updated movie fixture", "1.0.0")) != null);
            assertEquals(server.url("/replacement/manifest.json"), AddonRegistration.endpoint(dao.get(id)));
            onView(withText(R.string.movies_remove)).perform(click());
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            await(scenario, a -> list(a).isEnabled()); assertNull(dao.get(id));
        } finally { if (id != null) dao.delete(id); }
    }
}
