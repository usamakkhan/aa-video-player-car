package app.aavideoplayercar;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.aavideoplayercar.data.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class NewsScreenTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private View text(View root, String value) { if (root instanceof TextView && value.contentEquals(((TextView)root).getText())) return root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { var found = text(((ViewGroup)root).getChildAt(i), value); if (found != null) return found; } return null; }
    private ListView list(MainActivity a) { return a.getWindow().getDecorView().findViewWithTag("news.list"); }
    private void await(ActivityScenario<MainActivity> activity, java.util.function.Predicate<MainActivity> condition) throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 12000; while (android.os.SystemClock.elapsedRealtime() < end) { var ready = new java.util.concurrent.atomic.AtomicBoolean(); activity.onActivity(a -> ready.set(condition.test(a))); if (ready.get()) return; Thread.sleep(50); } fail("News timeout");
    }
    private void reader() throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 12000;
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { onView(withTagValue(is("news.reader"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> { if (e != null) throw e; assertTrue(v.isShown()); }); return; }
            catch (androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.NoMatchingRootException missing) { Thread.sleep(50); }
        } fail("Reader timeout");
    }
    private String feed() { StringBuilder b = new StringBuilder("<rss><channel><title>Fixture News</title>"); for (int i = 0; i < 51; i++) b.append("<item><guid>").append(i).append("</guid><title>Headline ").append(i).append("</title><description>&lt;p&gt;Readable body&lt;/p&gt;</description><link>https://example.com/article</link></item>"); return b.append("</channel></rss>").toString(); }
    @Test public void longReaderRestoresScrollAndPauseStopsSpeechState() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var dao = MediaRepository.get(context).database.news();
        var source = new NewsSource(); source.id = "long-reader-fixture"; source.title = "Long reader fixture"; source.url = "https://example.com/long-reader";
        var article = new NewsArticle(); article.id = "long-reader-article"; article.sourceId = source.id; article.title = "Long article"; article.body = "A paragraph for the scrolling reader.\n\n".repeat(1000);
        article.publishedAt = 1792742400000L;
        dao.replace(source, java.util.List.of(article), true);
        try (var activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> text(a.getWindow().getDecorView(), "News").performClick()); await(activity, a -> list(a).getCount() > 0);
            activity.onActivity(a -> { for (int i = 0; i < list(a).getCount(); i++) if (list(a).getAdapter().getItem(i).equals(source.title)) { list(a).performItemClick(null, i, i); break; } });
            await(activity, a -> list(a).getCount() == 1 && list(a).getAdapter().getItem(0).toString().contains(article.title));
            activity.onActivity(a -> list(a).performItemClick(null, 0, 0)); reader();
            String published = context.getString(R.string.news_published, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(article.publishedAt)));
            onView(withTagValue(is("news.published"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(published, ((TextView)v).getText().toString()));
            onView(withTagValue(is("news.reader"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> ((ScrollView)v).scrollTo(0, 1600));
            onView(withTagValue(is("news.reader"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(1600, v.getScrollY()));
            activity.recreate(); reader(); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withTagValue(is("news.reader"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(1600, v.getScrollY()));
            // Verify interruption of active platform speech, not merely an idle reader.
            long end = android.os.SystemClock.elapsedRealtime() + 20000; var settled = new java.util.concurrent.atomic.AtomicBoolean();
            while (android.os.SystemClock.elapsedRealtime() < end) {
                onView(withTagValue(is("news.speech.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> settled.set(!context.getString(R.string.news_speech_loading).contentEquals(((TextView)v).getText())));
                if (settled.get()) break; Thread.sleep(50);
            } assertTrue(settled.get());
            onView(withTagValue(is("news.speech.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(context.getString(R.string.news_speech_ready), ((TextView)v).getText().toString()));
            onView(withTagValue(is("news.speak"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withTagValue(is("news.speech.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(context.getString(R.string.news_speech_active), ((TextView)v).getText().toString()));
            activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED); activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            onView(withTagValue(is("news.speech.status"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals(context.getString(R.string.news_speech_stopped), ((TextView)v).getText().toString()));
            onView(withTagValue(is("news.stop"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertFalse(v.isEnabled()));
            onView(withText(R.string.news_close)).perform(click()); await(activity, a -> list(a).getCount() == 1);
        } finally { dao.delete(source.id); }
    }
    @Test public void importsReadsRestoresRetainsCacheOnFailureAndRemovesSource() throws Exception {
        context.getSharedPreferences("dashboard", 0).edit().clear().commit(); var dao = MediaRepository.get(context).database.news(); String id = null;
        try (var server = new LocalHttpFixture()) {
            server.add("/news", feed().getBytes(StandardCharsets.UTF_8), "application/rss+xml");
            id = java.util.UUID.nameUUIDFromBytes(server.url("/news").getBytes(StandardCharsets.UTF_8)).toString();
            try (var activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(a -> text(a.getWindow().getDecorView(), "News").performClick());
                onView(withTagValue(is("news.url"))).perform(replaceText(server.url("/news")), closeSoftKeyboard()); onView(withText(R.string.news_add)).perform(click());
                await(activity, a -> list(a).getCount() == 50 && text(a.getWindow().getDecorView(), context.getString(R.string.news_refresh)).isEnabled());
                onView(withText(R.string.radio_next)).perform(click()); await(activity, a -> list(a).getCount() == 1);
                String article = dao.articles(id, "", false, 50, 50).get(0).id;
                activity.onActivity(a -> list(a).performItemClick(null, 0, 0)); reader(); assertTrue(dao.article(article).read);
                onView(withTagValue(is("news.reader"))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check((v, e) -> assertEquals("Readable body", ((TextView)((ScrollView)v).getChildAt(0)).getText().toString()));
                activity.recreate(); reader(); onView(withText(R.string.news_mark_unread)).perform(click()); await(activity, a -> list(a).getCount() == 1); assertFalse(dao.article(article).read);
                onView(withTagValue(is("news.unread"))).perform(click()); await(activity, a -> list(a).getCount() == 50);
                onView(withTagValue(is("news.query"))).perform(replaceText("Headline 50"), closeSoftKeyboard()); onView(withText(R.string.radio_search)).perform(click()); await(activity, a -> list(a).getCount() == 1);
                activity.onActivity(a -> list(a).performItemClick(null, 0, 0)); reader(); onView(withText(R.string.news_close)).perform(click());
                await(activity, a -> list(a).getAdapter() != null && list(a).getCount() == 0);
                onView(withTagValue(is("news.unread"))).perform(click()); await(activity, a -> list(a).getCount() == 1);
                onView(withText(R.string.news_refresh)).perform(click()); await(activity, a -> list(a).getCount() == 1 && text(a.getWindow().getDecorView(), context.getString(R.string.news_refresh)).isEnabled());
                assertEquals(50, dao.count(id, "", true));
                server.add("/news", "<rss><channel>broken".getBytes(StandardCharsets.UTF_8), "application/xml"); onView(withText(R.string.news_refresh)).perform(click());
                await(activity, a -> text(a.getWindow().getDecorView(), context.getString(R.string.news_error)) != null);
                assertEquals(51, dao.count(id, "", false)); activity.onActivity(a -> { assertEquals(1, list(a).getCount()); assertTrue(list(a).isEnabled()); });
                onView(withText(R.string.news_remove)).perform(click()); onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                await(activity, a -> text(a.getWindow().getDecorView(), context.getString(R.string.news_empty)) != null); assertNull(dao.source(id)); assertNull(dao.article(article));
            }
        } finally { if (id != null) dao.delete(id); }
    }
}
