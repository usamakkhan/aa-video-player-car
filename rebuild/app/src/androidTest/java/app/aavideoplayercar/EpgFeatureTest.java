package app.aavideoplayercar;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.iptv.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class EpgFeatureTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private XmltvParser.Result parse(String xml) throws Exception { return XmltvParser.parse(xml.getBytes(StandardCharsets.UTF_8), "playlist"); }
    private String programme(String start, String stop, String title) {
        return "<programme channel='station' start='" + start + "'" + (stop == null ? "" : " stop='" + stop + "'") + "><title>" + title + "</title><desc>Programme details</desc></programme>";
    }
    private String schedule(int count) {
        StringBuilder xml = new StringBuilder("<tv>");
        var format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(java.time.ZoneOffset.UTC);
        for (int i = 0; i < count; i++) {
            var start = java.time.Instant.parse("2030-01-01T00:00:00Z").plusSeconds(i * 1800L);
            xml.append(programme(format.format(start), format.format(start.plusSeconds(1800)), "Show " + i));
        }
        return xml.append("</tv>").toString();
    }
    @Test public void xmltvOffsetsPartialTimesTextDtdAndDuplicates() throws Exception {
        assertEquals(XmltvParser.timestamp("20261013090000 +0000"), XmltvParser.timestamp("20261013143000 +0530"));
        assertEquals(XmltvParser.timestamp("202610130900"), XmltvParser.timestamp("20261013020000 -0700"));
        assertEquals(XmltvParser.timestamp("202610130900"), XmltvParser.timestamp("202610131000 BST"));
        assertEquals(XmltvParser.timestamp("20260101000000"), XmltvParser.timestamp("2026"));
        String item = programme("202610130900", null, "News &amp; Weather");
        var result = parse("<!DOCTYPE tv SYSTEM 'http://127.0.0.1:1/never-fetch.dtd'><tv>" + item + item + "</tv>");
        assertEquals(1, result.programmes.size()); assertEquals(1, result.skipped);
        assertEquals("News & Weather", result.programmes.get(0).title); assertEquals(0, result.programmes.get(0).endMs);
        assertEquals("Programme details", result.programmes.get(0).description);
    }
    @Test public void rejectsUnsafeMalformedExcessiveAndInvalidScheduleData() throws Exception {
        assertThrows(java.io.IOException.class, () -> parse("<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///private'>]><tv>" + programme("20300101", null, "&x;") + "</tv>"));
        assertThrows(java.io.IOException.class, () -> parse("<tv>" + programme("20300230", null, "Invalid") + "</tv>"));
        assertThrows(java.io.IOException.class, () -> parse("<tv>" + programme("20300102000000", "20300101000000", "Reversed") + "</tv>"));
        assertThrows(java.io.IOException.class, () -> parse("<tv>" + programme("20300101", null, "&missing;") + "</tv>"));
        assertThrows(java.io.IOException.class, () -> parse("<html/>"));
        assertThrows(java.io.IOException.class, () -> parse("<tv>" + programme("20300101", null, "Truncated")));
        assertThrows(java.io.IOException.class, () -> parse(schedule(10001)));
        assertThrows(java.io.IOException.class, () -> XmltvParser.parse(new byte[4 * 1024 * 1024 + 1], "playlist"));
    }
    @Test public void guidePagesTenThousandRowsRollsBackFailureAndCascadesWithPlaylist() throws Exception {
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            var playlist = M3uParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"station\",Station\nhttps://example.com/live\n".getBytes(StandardCharsets.UTF_8), "https://example.com/list", "https://example.com/list", "TV");
            playlist.playlist.id = "playlist"; playlist.channels.get(0).playlistId = "playlist"; db.iptv().replace(playlist.playlist, playlist.channels, true);
            var source = new EpgSource(); source.playlistId = "playlist"; source.url = "https://example.com/guide";
            long start = android.os.SystemClock.elapsedRealtime(); var parsed = parse(schedule(10000));
            assertTrue(db.epg().replace(source, parsed.programmes));
            assertEquals(10000, db.epg().count("playlist", "station")); assertEquals("Show 9950", db.epg().page("playlist", "station", 50, 9950).get(0).title);
            long boundary = XmltvParser.timestamp("20300101003000");
            assertEquals(9999, db.epg().upcomingCount("playlist", "station", boundary));
            assertEquals("Show 1", db.epg().upcomingPage("playlist", "station", boundary, 50, 0).get(0).title);
            assertEquals(50, db.epg().upcomingPage("playlist", "station", boundary, 50, 9949).size());
            assertEquals(0, db.epg().upcomingCount("playlist", "station", XmltvParser.timestamp("20310101000000")));
            android.util.Log.i("EpgBenchmark", "10000_parse_import_query_ms=" + (android.os.SystemClock.elapsedRealtime() - start));
            source.url = "https://example.com/changed";
            assertThrows(android.database.sqlite.SQLiteConstraintException.class, () -> db.epg().replace(source, Arrays.asList(parsed.programmes.get(0), parsed.programmes.get(0))));
            assertEquals("https://example.com/guide", db.epg().source("playlist").url); assertEquals(10000, db.epg().count("playlist", "station"));
            db.iptv().delete("playlist"); assertNull(db.epg().source("playlist")); assertEquals(0, db.epg().count("playlist", "station")); assertFalse(db.epg().replace(source, parsed.programmes));
        } finally { db.close(); }
    }
    private View text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { var found = text(((ViewGroup)root).getChildAt(i), value); if (found != null) return found; }
        return null;
    }
    private void waitFor(java.util.concurrent.Callable<Boolean> ready) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < end) { if (ready.call()) return; Thread.sleep(50); }
        fail("Guide timeout");
    }
    private boolean rows(int count) {
        var result = new java.util.concurrent.atomic.AtomicBoolean();
        onView(withTagValue(is("epg.list"))).inRoot(isDialog()).check((view, error) -> { if (error != null) throw error; result.set(((ListView)view).getCount() == count && view.getHeight() > 100); });
        return result.get();
    }
    @Test public void guideFiltersPastAndUnknownEndsAndUpdatesNowLabelWithoutInteraction() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var repository = MediaRepository.get(context);
        var parsed = M3uParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"clock\",Clock station\nhttps://example.com/clock\n".getBytes(StandardCharsets.UTF_8), "https://example.com/clock-list", "https://example.com/clock-list", "Clock fixture");
        repository.database.iptv().replace(parsed.playlist, parsed.channels, true);
        var guide = new java.util.concurrent.atomic.AtomicReference<EpgDialog>();
        try (var activity = ActivityScenario.launch(MainActivity.class)) {
            long now = System.currentTimeMillis(); List<EpgProgramme> entries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                EpgProgramme item = new EpgProgramme(); item.id = "clock-" + i; item.playlistId = parsed.playlist.id; item.channelId = "clock"; item.title = "Clock " + i;
                item.startMs = now - 60000; item.endMs = i == 0 ? now - 1 : i == 1 ? 0 : i == 2 ? now + 15000 : now + 120000;
                if (i == 3) item.startMs = now + 60000; entries.add(item);
            }
            EpgSource source = new EpgSource(); source.playlistId = parsed.playlist.id; source.url = "https://example.com/guide"; source.updatedAt = now;
            repository.database.epg().replace(source, entries);
            activity.onActivity(a -> guide.set(new EpgDialog(a, source.playlistId, "clock", "Clock station", null)));
            waitFor(() -> rows(2));
            var label = new java.util.concurrent.atomic.AtomicReference<String>();
            onView(withTagValue(is("epg.list"))).inRoot(isDialog()).check((view, error) -> label.set(((ListView)view).getAdapter().getItem(0).toString()));
            assertTrue(label.get().startsWith(context.getString(R.string.epg_now)));
            onView(withText(R.string.epg_show_past)).perform(click()); waitFor(() -> rows(4));
            long end = android.os.SystemClock.elapsedRealtime() + 40000; boolean changed = false;
            while (android.os.SystemClock.elapsedRealtime() < end) {
                onView(withTagValue(is("epg.list"))).inRoot(isDialog()).check((view, error) -> {
                    ListView list = (ListView)view;
                    for (int i = 0; i < list.getCount(); i++) if (list.getAdapter().getItem(i).toString().contains("Clock 2")) label.set(list.getAdapter().getItem(i).toString());
                });
                if (!label.get().startsWith(context.getString(R.string.epg_now))) { changed = true; break; }
                Thread.sleep(500);
            }
            assertTrue("Now label must update without a click", changed);
            onView(withText(R.string.epg_jump_now)).perform(click()); waitFor(() -> rows(1));
            activity.onActivity(a -> guide.get().close());
            onView(withTagValue(is("epg.list"))).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { if (guide.get() != null) guide.get().close(); }); repository.database.iptv().delete(parsed.playlist.id); }
    }
    @Test public void phoneImportsPagesRestoresAndKeepsCachedGuideAfterFailure() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); String id = null; var repository = MediaRepository.get(context);
        try (LocalHttpFixture server = new LocalHttpFixture()) {
            server.gzip("/guide", schedule(51).getBytes(StandardCharsets.UTF_8), "application/xml");
            var parsed = M3uParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"station\",Guide station\nhttps://example.com/live\n".getBytes(StandardCharsets.UTF_8), server.url("/list"), server.url("/list"), "Guide fixture");
            id = parsed.playlist.id; repository.database.iptv().replace(parsed.playlist, parsed.channels, true);
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "TV / IPTV").performClick());
                waitFor(() -> { var ready = new java.util.concurrent.atomic.AtomicBoolean(); activity.onActivity(a -> {
                    ListView list = a.getWindow().getDecorView().findViewWithTag("iptv.list");
                    for (int i = 0; i < list.getCount(); i++) if (((IptvPlaylist)list.getAdapter().getItem(i)).id.equals(parsed.playlist.id)) { list.performItemClick(null, i, i); ready.set(true); break; }
                }); return ready.get(); });
                waitFor(() -> { var ready = new java.util.concurrent.atomic.AtomicBoolean(); activity.onActivity(a -> ready.set(text(a.getWindow().getDecorView(), context.getString(R.string.epg_open)).isEnabled())); return ready.get(); });
                onView(withText(R.string.epg_open)).perform(click()); onView(withText("Guide station")).inRoot(isDialog()).perform(click());
                onView(withTagValue(is("epg.url"))).perform(replaceText(server.url("/guide")), closeSoftKeyboard()); onView(withText(R.string.epg_import)).perform(click());
                waitFor(() -> rows(50)); onView(withText(R.string.radio_next)).inRoot(isDialog()).perform(click()); waitFor(() -> rows(1));
                activity.recreate(); waitFor(() -> rows(1));
                onView(withText(R.string.epg_show_past)).perform(click()); waitFor(() -> rows(50));
                activity.recreate(); onView(withTagValue(is("epg.past"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(isChecked()));
                onView(withText(R.string.epg_jump_now)).perform(click()); waitFor(() -> rows(50));
                onView(withTagValue(is("epg.past"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(isNotChecked()));
                onView(withText(R.string.radio_next)).inRoot(isDialog()).perform(click()); waitFor(() -> rows(1));
                onView(withTagValue(is("epg.url"))).perform(replaceText(server.url("/missing")), closeSoftKeyboard()); onView(withText(R.string.epg_import)).perform(click());
                waitFor(() -> { var ready = new java.util.concurrent.atomic.AtomicBoolean(); onView(withTagValue(is("epg.status"))).check((view, error) -> { if (error != null) throw error; ready.set(context.getString(R.string.epg_error).contentEquals(((TextView)view).getText())); }); return ready.get(); });
                assertEquals(51, repository.database.epg().count(parsed.playlist.id, "station")); assertEquals(server.url("/guide"), repository.database.epg().source(parsed.playlist.id).url);
                assertTrue(rows(1)); onView(withText(R.string.epg_close)).perform(click());
            }
        } finally { if (id != null) repository.database.iptv().delete(id); }
    }
}
